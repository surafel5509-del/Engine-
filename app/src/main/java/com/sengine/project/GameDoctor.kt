package com.sengine.project

import com.sengine.engine.core.AssetKind
import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Camera3D
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.Collider3D
import com.sengine.engine.core.Prop
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.UIButton
import com.sengine.engine.core.UIPanel
import com.sengine.engine.core.UIProgress
import java.io.File

/**
 * Game Doctor: static health check of a project. Finds broken asset references, script syntax
 * errors, scenes without cameras, rigidbodies without colliders, buttons pointing at missing
 * scenes, oversized textures and more — most issues come with a one-tap automatic fix.
 */
object GameDoctor {
    class Issue(
        val severity: Int, // 0 info, 1 warning, 2 error
        val title: String,
        val detail: String,
        val scene: String? = null,
        val fixLabel: String? = null,
        val fix: (() -> Unit)? = null,
    )

    fun check(project: Project): List<Issue> {
        val out = ArrayList<Issue>()
        val scenes = project.listScenes()
        val assets = project.listAssets().toSet()
        if (scenes.isEmpty()) out += Issue(2, "No scenes", "The project has no scenes. Create one in the editor (Scenes menu).")
        if (!project.sceneExists(project.startScene) && scenes.isNotEmpty()) {
            val first = scenes.first()
            out += Issue(2, "Start scene missing", "Start scene '${project.startScene}' doesn't exist — the game can't start.", null,
                "Use '$first'") { project.startScene = first; project.saveMeta() }
        }

        // ---------------------------------------------------------------- scripts
        val jsFiles = assets.filter { it.endsWith(".js") }
        for (js in jsFiles) {
            val src = project.readAsset(js) ?: continue
            syntaxError(src, js)?.let { out += Issue(2, "Script error in $js", it) }
            for (m in Regex("scene\\.load\\(\\s*['\"]([^'\"]+)['\"]").findAll(src)) {
                val target = m.groupValues[1]
                if (target !in scenes) out += Issue(1, "$js loads missing scene", "scene.load(\"$target\") — no scene named '$target'.")
            }
            for (m in Regex("audio\\.(?:play|loop|playMusic)\\(\\s*['\"]([^'\"]+)['\"]").findAll(src)) {
                val a = m.groupValues[1]
                if (a !in assets && a != "ui_click.wav") out += Issue(1, "$js plays missing sound", "'$a' isn't in Assets. Add it from the Asset Store or import it.")
            }
        }

        // ---------------------------------------------------------------- scenes
        var totalObjects = 0
        for (name in scenes) {
            val scene = try { project.loadScene(name) } catch (e: Exception) {
                out += Issue(2, "Scene '$name' can't be loaded", e.message ?: e.javaClass.simpleName, name); continue
            }
            totalObjects += scene.objects.size
            val hasCam2 = scene.objects.any { it.getAny<Camera2D>() != null }
            val hasCam3 = scene.objects.any { it.getAny<Camera3D>() != null }
            val hasUI = scene.objects.any { it.getAny<UIButton>() != null || it.getAny<UIPanel>() != null || it.getAny<UIProgress>() != null }
            if (!hasCam2 && !hasCam3) {
                out += Issue(if (hasUI) 0 else 1, "Scene '$name' has no camera",
                    if (hasUI) "Only UI will be visible (fine for menus)." else "Nothing in the world will be rendered.", name, "Add camera") {
                    val s = project.loadScene(name); s.create("Main Camera").add(Camera2D()); project.saveScene(s)
                }
            }
            for (go in scene.objects) {
                for (c in go.components) for (p in c.props()) if (p is Prop.Asset) {
                    val v = p.get()
                    if (v.isNotBlank() && v !in assets && !project.assetFile(v).exists() && v != "ui_click.wav") {
                        val id = go.id; val comp = c.type; val prop = p.name
                        out += Issue(2, "Missing ${p.kind.name.lowercase()} '$v'", "${go.name} → $comp.$prop in scene '$name'.", name, "Clear reference") {
                            val s = project.loadScene(name)
                            s.objects.firstOrNull { it.id == id }?.components?.firstOrNull { it.type == comp }?.props()
                                ?.firstOrNull { it.name == prop }?.let { (it as? Prop.Asset)?.set?.invoke("") }
                            project.saveScene(s)
                        }
                    }
                }
                if (go.getAny<Rigidbody2D>() != null && go.getAny<Collider2D>() == null) {
                    val id = go.id
                    out += Issue(1, "${go.name}: Rigidbody2D without collider", "It will fall through everything in scene '$name'.", name, "Add Collider2D") {
                        val s = project.loadScene(name); s.objects.firstOrNull { it.id == id }?.add(Collider2D()); project.saveScene(s)
                    }
                }
                if (go.getAny<Rigidbody3D>() != null && go.getAny<Collider3D>() == null) {
                    val id = go.id
                    out += Issue(1, "${go.name}: Rigidbody3D without collider", "It won't collide in scene '$name'.", name, "Add Collider3D") {
                        val s = project.loadScene(name); s.objects.firstOrNull { it.id == id }?.add(Collider3D()); project.saveScene(s)
                    }
                }
                go.getAny<UIButton>()?.let { b ->
                    for (a in b.action.split(';').map { it.trim() }) {
                        val kind = a.substringBefore(':').lowercase(); val arg = a.substringAfter(':', "").trim()
                        if ((kind == "scene" || kind == "load") && arg !in scenes) out += Issue(2, "Button '${go.name}' opens missing scene", "Action '$a' in scene '$name'.", name)
                        if ((kind == "show" || kind == "hide" || kind == "toggle") && scene.find(arg) == null) out += Issue(1, "Button '${go.name}' targets missing object", "Action '$a' — no object '$arg' in scene '$name'.", name)
                    }
                }
            }
            if (scene.objects.size > 1500) out += Issue(1, "Scene '$name' is heavy", "${scene.objects.size} objects — consider spawning at runtime or using voxels/tiling.", name)
        }

        // ---------------------------------------------------------------- assets
        for (a in assets) {
            val f = project.assetFile(a)
            if (AssetKind.of(a) == AssetKind.TEXTURE && a.lowercase().endsWith(".png")) {
                val (w, h) = pngSize(f)
                if (w > 2048 || h > 2048) out += Issue(1, "Large texture $a", "${w}×$h px uses a lot of GPU memory. 1024 px or less is recommended on mobile.")
            }
            if (AssetKind.of(a) == AssetKind.SOUND && f.length() > 4_000_000) out += Issue(0, "Large sound $a", "${f.length() / 1_048_576} MB — long tracks are streamed with audio.playMusic().")
        }
        if (out.none { it.severity >= 1 }) out += Issue(0, "All good!", "Checked ${scenes.size} scenes, $totalObjects objects, ${jsFiles.size} scripts and ${assets.size} assets.")
        return out.sortedByDescending { it.severity }
    }

    /** Returns a readable syntax error message or null when the script compiles. */
    fun syntaxError(src: String, name: String): String? {
        val cx = org.mozilla.javascript.Context.enter()
        return try {
            cx.optimizationLevel = -1
            cx.languageVersion = org.mozilla.javascript.Context.VERSION_ES6
            cx.compileString(src, name, 1, null)
            null
        } catch (e: org.mozilla.javascript.EvaluatorException) {
            "Line ${e.lineNumber()}: ${e.details()}" + (e.lineSource()?.let { "\n  ${it.trim()}" } ?: "")
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        } finally {
            org.mozilla.javascript.Context.exit()
        }
    }

    private fun pngSize(f: File): Pair<Int, Int> = try {
        f.inputStream().use { s ->
            val b = ByteArray(24); if (s.read(b) < 24) return 0 to 0
            fun i(o: Int) = ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)
            i(16) to i(20)
        }
    } catch (_: Exception) { 0 to 0 }
}
