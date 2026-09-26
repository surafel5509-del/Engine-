package com.sengine.agent

import com.sengine.engine.Engine
import com.sengine.engine.audio.Song
import com.sengine.engine.core.Component
import com.sengine.engine.core.ComponentRegistry
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Prop
import com.sengine.engine.core.Scene
import com.sengine.engine.core.TextRenderer
import com.sengine.engine.core.UIButton
import com.sengine.engine.model.ModelPresets
import com.sengine.engine.model.SModel
import com.sengine.engine.texture.PngEncoder
import com.sengine.engine.texture.TextureGen
import com.sengine.project.AssetLibrary
import com.sengine.project.GameDoctor
import com.sengine.project.Project
import com.sengine.project.Templates
import org.json.JSONArray
import org.json.JSONObject

/** Host services the agent needs but which depend on Android (project folder, APK build). */
interface AgentHost {
    fun createProject(name: String, template: Templates.Template): Project
    fun openProject(name: String): Project?
    /** Starts / queues an APK build; returns a human readable status. */
    fun buildApk(project: Project): String = "Build queued — open Build to sign and export the APK."
}

class ToolResult(val ok: Boolean, val text: String)

/**
 * Executes agent tool calls against the S Engine project model.
 * Every mutation is written to disk immediately, so the editor always sees the agent's work.
 */
class AgentTools(private val host: AgentHost) {
    var project: Project? = null
        private set
    val plan = ArrayList<PlanTask>()
    var finished = false
    var finalSummary = ""
    var buildRequested = false
    private val scenes = HashMap<String, Scene>()

    class PlanTask(val id: Int, val title: String, var done: Boolean = false, var note: String = "")

    class Tool(val name: String, val args: String, val help: String)

    companion object {
        val TOOLS = listOf(
            Tool("plan", "{tasks:[string]}", "Save the full feature/task checklist (call first, may be called again to replace it)."),
            Tool("task_done", "{id:int, note?:string}", "Mark a plan task finished."),
            Tool("create_project", "{name, template?:string, description?:string, orientation?:\"landscape\"|\"portrait\"}", "Create a new project from a template (see TEMPLATES) and make it current."),
            Tool("open_project", "{name}", "Open an existing project."),
            Tool("list_files", "{kind?:\"scripts\"|\"textures\"|\"sounds\"|\"models\"|\"songs\"|\"all\"}", "List project assets and scenes."),
            Tool("read_file", "{path}", "Read a text asset (script, .anim, .song, .smodel, json)."),
            Tool("write_file", "{path, content}", "Create/overwrite a text asset, e.g. a JavaScript behaviour script. Scripts are syntax-checked."),
            Tool("delete_file", "{path}", "Delete an asset."),
            Tool("new_scene", "{name, kind?:\"2d\"|\"3d\", gravity?:number, background?:color, start?:bool}", "Create an empty scene with a camera (2D) or camera+sun light (3D)."),
            Tool("inspect_scene", "{scene}", "List objects, components and key props of a scene."),
            Tool("add_object", "{scene, name, x?,y?,z?, rotation?, rotX?, rotY?, sx?,sy?,sz?, tag?, parent?, active?, order?, components:{Type:{Prop:value}}}", "Add a game object. Components use COMPONENTS names/props. Inactive objects are spawn templates."),
            Tool("set_props", "{scene, object, component?, props:{Prop:value}}", "Change component props (or transform fields x,y,z,rotation,sx,sy,sz,tag,active,order when component is omitted)."),
            Tool("add_component", "{scene, object, type, props?:{}}", "Attach another component."),
            Tool("delete_object", "{scene, object}", "Remove an object and its children."),
            Tool("scene_settings", "{scene, gravityY?, gravity3D?, ambient?, fog?:bool, fogColor?, start?:bool}", "Edit scene-wide settings; start=true makes it the start scene."),
            Tool("list_store", "{query?:string, category?:string}", "Search the built-in asset store (textures, sprites, sounds, music, models, scripts, packs)."),
            Tool("install_asset", "{title}", "Install a store item into the project by exact title."),
            Tool("generate_texture", "{name, style, colorA?, colorB?, size?, scale?, seed?}", "Create a seamless PNG texture. Styles: " + TextureGen.STYLES.joinToString(", ")),
            Tool("generate_sprite", "{name, shape:\"circle\"|\"box\"|\"ship\"|\"tank\"|\"character\"|\"coin\"|\"star\", color, size?}", "Create a simple game sprite PNG with transparency."),
            Tool("compose_song", "{name, style, seed?}", "Compose a multi-track .song. Styles: " + Song.STYLES.joinToString(", ")),
            Tool("create_model", "{name, preset?:string, parts?:[{shape, color, pos:[x,y,z], scale:[x,y,z], rot?:[x,y,z]}]}", "Create a 3D .smodel from a preset (" + ModelPresets.NAMES.joinToString(", ") + ") or primitive parts (" + SModel.PRIMITIVES.joinToString(", ") + ")."),
            Tool("set_controls", "{preset}", "Choose the on-screen touch controls preset (" + com.sengine.engine.controls.ControlLayout.presets.joinToString(", ") { it.name } + ")."),
            Tool("run_doctor", "{}", "Check the project for errors: missing assets, script syntax, broken references."),
            Tool("play_test", "{scene?, seconds?:number, press?:[button ids]}", "Run the game headless with auto-input and report errors, logs and HUD texts."),
            Tool("build_apk", "{}", "Request an installable APK build of the finished game."),
            Tool("finish", "{summary}", "Declare the game finished with a short summary for the user."),
        )

        fun toolReference(): String = TOOLS.joinToString("\n") { "- ${it.name} ${it.args}: ${it.help}" }

        /** Auto-generated component reference so the model always sees the real prop names. */
        fun componentReference(): String {
            val sb = StringBuilder()
            for ((type, make) in ComponentRegistry.types) {
                val c = make()
                sb.append("- ").append(type).append(": ")
                sb.append(c.props().joinToString(", ") { p ->
                    when (p) {
                        is Prop.F -> "${p.name}(num)"
                        is Prop.I -> "${p.name}(int)"
                        is Prop.B -> "${p.name}(bool)"
                        is Prop.S -> "${p.name}(text)"
                        is Prop.Color -> "${p.name}(#AARRGGBB)"
                        is Prop.Choice -> "${p.name}(${p.options.joinToString("|")})"
                        is Prop.Asset -> "${p.name}(${p.kind.name.lowercase()} file)"
                    }
                })
                sb.append('\n')
            }
            return sb.toString()
        }

        fun templateReference(): String = Templates.all.joinToString("\n") { "- ${it.name}: ${it.description}" }
    }

    fun planText(): String = if (plan.isEmpty()) "(no plan yet)" else plan.joinToString("\n") { "${if (it.done) "[x]" else "[ ]"} ${it.id}. ${it.title}" }

    fun execute(tool: String, args: JSONObject): ToolResult = try {
        run(tool, args)
    } catch (e: Exception) {
        ToolResult(false, "Error in $tool: ${e.message ?: e.javaClass.simpleName}")
    }

    private fun need(): Project = project ?: throw IllegalStateException("No project yet — call create_project first")

    private fun scene(name: String?): Scene {
        val p = need()
        val n = if (name.isNullOrBlank()) p.startScene else name
        return scenes[n] ?: run {
            if (!p.sceneExists(n)) throw IllegalArgumentException("Scene '$n' not found. Scenes: ${p.listScenes()}")
            p.loadScene(n).also { scenes[n] = it }
        }
    }

    private fun save(s: Scene) { need().saveScene(s) }

    private fun find(s: Scene, name: String): GameObject =
        s.objects.firstOrNull { it.name == name } ?: s.objects.firstOrNull { it.name.equals(name, true) }
        ?: throw IllegalArgumentException("Object '$name' not in scene ${s.name}")

    private fun run(tool: String, a: JSONObject): ToolResult {
        when (tool) {
            "plan" -> {
                val t = a.optJSONArray("tasks") ?: JSONArray()
                plan.clear()
                for (i in 0 until t.length()) plan += PlanTask(i + 1, t.optString(i))
                return ToolResult(true, "Plan saved with ${plan.size} tasks.\n" + planText())
            }
            "task_done" -> {
                val id = a.optInt("id")
                val task = plan.firstOrNull { it.id == id } ?: return ToolResult(false, "No task $id")
                task.done = true; task.note = a.optString("note")
                val left = plan.count { !it.done }
                return ToolResult(true, "Task $id done. $left task(s) left.")
            }
            "create_project" -> {
                val raw = a.optString("name", "AI Game").ifBlank { "AI Game" }
                val base = raw.replace(Regex("[^A-Za-z0-9 _\\-]"), "").trim().take(40).ifBlank { "AI Game" }
                var name = base; var i = 2
                while (host.openProject(name) != null) name = "$base $i".also { i++ }
                val tName = a.optString("template", "Empty")
                val tpl = Templates.all.firstOrNull { it.name.equals(tName, true) }
                    ?: Templates.all.firstOrNull { it.name.startsWith(tName, true) || it.name.substringBefore(" (").equals(tName, true) }
                    ?: Templates.all.firstOrNull { it.name.contains(tName, true) || tName.contains(it.name.substringBefore(" ("), true) } ?: Templates.all[0]
                val p = host.createProject(name, tpl)
                p.description = a.optString("description", p.description)
                if (a.optString("orientation") == "portrait") p.orientation = 1
                p.saveMeta()
                project = p; scenes.clear()
                return ToolResult(true, "Created project '$name' from template '${tpl.name}'. Scenes: ${p.listScenes()}. Start scene: ${p.startScene}. Assets: ${p.listAssets().size} files.")
            }
            "open_project" -> {
                val p = host.openProject(a.optString("name")) ?: return ToolResult(false, "Project not found")
                project = p; scenes.clear()
                return ToolResult(true, "Opened ${p.name}. Scenes: ${p.listScenes()}")
            }
            "list_files" -> {
                val p = need()
                val kind = a.optString("kind", "all")
                val files = p.listAssets().filter {
                    when (kind) {
                        "scripts" -> it.endsWith(".js") || it.endsWith(".bp")
                        "textures" -> it.endsWith(".png")
                        "sounds" -> it.endsWith(".wav")
                        "models" -> it.endsWith(".smodel") || it.endsWith(".obj")
                        "songs" -> it.endsWith(".song")
                        else -> true
                    }
                }
                return ToolResult(true, "Scenes: ${p.listScenes()} (start: ${p.startScene})\nAssets (${files.size}): ${files.joinToString(", ")}")
            }
            "read_file" -> {
                val path = a.getString("path")
                val t = need().readAsset(path) ?: return ToolResult(false, "File not found: $path")
                return ToolResult(true, if (t.length > 12000) t.take(12000) + "\n…(truncated)" else t)
            }
            "write_file" -> {
                val path = a.getString("path").trim().removePrefix("assets/")
                val content = a.getString("content")
                if (path.contains("..")) return ToolResult(false, "Invalid path")
                need().writeAsset(path, content)
                if (path.endsWith(".js")) {
                    val err = GameDoctor.syntaxError(content, path)
                    if (err != null) return ToolResult(false, "Saved $path but it has a SYNTAX ERROR: $err — fix it (Rhino: use var, function(){}, no arrow functions/classes/template strings).")
                }
                return ToolResult(true, "Saved $path (${content.length} chars)")
            }
            "delete_file" -> {
                val f = need().assetFile(a.getString("path"))
                return ToolResult(f.delete(), if (f.exists()) "Could not delete" else "Deleted")
            }
            "new_scene" -> {
                val p = need()
                val name = a.getString("name")
                val s = Scene(name)
                val threeD = a.optString("kind", "2d").equals("3d", true)
                val bg = a.optString("background", if (threeD) "#FF87CEEB" else "#FF101010")
                if (threeD) {
                    s.gravity3D = a.optDouble("gravity", -9.81).toFloat()
                    val cam = s.create("Main Camera"); cam.y = 6f; cam.z = -10f; cam.rotX = 20f
                    cam.add(ComponentRegistry.create("Camera3D")!!)
                    val sun = s.create("Sun"); sun.rotX = 50f; sun.rotY = 30f; sun.y = 10f
                    sun.add(ComponentRegistry.create("Light")!!)
                } else {
                    s.gravityY = a.optDouble("gravity", -9.81).toFloat()
                    val cam = s.create("Main Camera")
                    val c = ComponentRegistry.create("Camera")!!
                    setProps(c, JSONObject().put("Background", bg))
                    cam.add(c)
                }
                p.saveScene(s); scenes[name] = s
                if (a.optBoolean("start", false)) { p.startScene = name; p.saveMeta() }
                return ToolResult(true, "Scene '$name' created (${if (threeD) "3D" else "2D"}). Objects: ${s.objects.joinToString { it.name }}")
            }
            "inspect_scene" -> return ToolResult(true, describe(scene(a.optString("scene"))))
            "add_object" -> {
                val s = scene(a.optString("scene"))
                val parent = a.optString("parent").takeIf { it.isNotBlank() }?.let { find(s, it) }
                val go = s.create(a.optString("name", "Object"), parent)
                applyTransform(go, a)
                val comps = a.optJSONObject("components")
                val warn = ArrayList<String>()
                if (comps != null) for (type in comps.keys()) {
                    val c = ComponentRegistry.create(normalizeType(type)) ?: run { warn += "unknown component $type"; null } ?: continue
                    warn += setProps(c, comps.optJSONObject(type) ?: JSONObject())
                    go.add(c)
                }
                save(s)
                return ToolResult(true, "Added '${go.name}' to ${s.name} with [${go.components.joinToString { it.type }}]" + if (warn.isNotEmpty()) ". Warnings: " + warn.joinToString("; ") else "")
            }
            "set_props" -> {
                val s = scene(a.optString("scene"))
                val go = find(s, a.getString("object"))
                val props = a.optJSONObject("props") ?: JSONObject()
                val type = a.optString("component")
                val warn = if (type.isBlank()) { applyTransform(go, props); emptyList() } else {
                    val c = go.components.firstOrNull { it.type.equals(normalizeType(type), true) }
                        ?: return ToolResult(false, "${go.name} has no $type (has ${go.components.joinToString { it.type }})")
                    setProps(c, props)
                }
                save(s)
                return ToolResult(true, "Updated ${go.name}" + if (warn.isNotEmpty()) ". Warnings: " + warn.joinToString("; ") else "")
            }
            "add_component" -> {
                val s = scene(a.optString("scene"))
                val go = find(s, a.getString("object"))
                val c = ComponentRegistry.create(normalizeType(a.getString("type"))) ?: return ToolResult(false, "Unknown component type. Valid: ${ComponentRegistry.types.keys}")
                val warn = setProps(c, a.optJSONObject("props") ?: JSONObject())
                go.add(c); save(s)
                return ToolResult(true, "Added ${c.type} to ${go.name}" + if (warn.isNotEmpty()) ". Warnings: " + warn.joinToString("; ") else "")
            }
            "delete_object" -> {
                val s = scene(a.optString("scene"))
                val go = find(s, a.getString("object"))
                val gone = s.objects.filter { it === go || go.isAncestorOf(it) }
                s.objects.removeAll(gone.toSet()); save(s)
                return ToolResult(true, "Deleted ${gone.size} object(s)")
            }
            "scene_settings" -> {
                val s = scene(a.optString("scene"))
                if (a.has("gravityY")) s.gravityY = a.getDouble("gravityY").toFloat()
                if (a.has("gravity3D")) s.gravity3D = a.getDouble("gravity3D").toFloat()
                if (a.has("ambient")) s.ambient = TextureGen.parseColor(a.getString("ambient"), s.ambient)
                if (a.has("fog")) s.fog = a.getBoolean("fog")
                if (a.has("fogColor")) s.fogColor = TextureGen.parseColor(a.getString("fogColor"), s.fogColor)
                save(s)
                if (a.optBoolean("start", false)) { need().startScene = s.name; need().saveMeta() }
                return ToolResult(true, "Scene settings saved")
            }
            "list_store" -> {
                val q = a.optString("query").lowercase()
                val cat = a.optString("category")
                val items = AssetLibrary.items.filter {
                    (cat.isBlank() || it.category.equals(cat, true)) &&
                        (q.isBlank() || q.split(' ').any { w -> w.isNotBlank() && (it.title.lowercase().contains(w) || it.description.lowercase().contains(w)) })
                }.take(40)
                return ToolResult(true, items.joinToString("\n") { "- ${it.title} [${it.category}] files: ${it.files.joinToString()}" }.ifBlank { "No match. Categories: ${AssetLibrary.categories}" })
            }
            "install_asset" -> {
                val title = a.getString("title")
                val item = AssetLibrary.items.firstOrNull { it.title.equals(title, true) }
                    ?: AssetLibrary.items.firstOrNull { it.title.contains(title, true) }
                    ?: return ToolResult(false, "No store item '$title'. Use list_store.")
                try { item.install(need()) } catch (e: Throwable) { return ToolResult(false, "Install failed: ${e.message}") }
                return ToolResult(true, "Installed '${item.title}': ${item.files.joinToString()}")
            }
            "generate_texture" -> {
                val style = TextureGen.STYLES.firstOrNull { it.equals(a.optString("style"), true) } ?: "Noise"
                val d = TextureGen.defaults(style)
                val prm = TextureGen.Params(style, a.optInt("size", 128).coerceIn(16, 512),
                    TextureGen.parseColor(a.optString("colorA"), d.first), TextureGen.parseColor(a.optString("colorB"), d.second),
                    a.optDouble("scale", 4.0).toFloat(), a.optInt("seed", 1))
                val name = pngName(a.optString("name", style))
                need().assetFile(name).also { it.parentFile?.mkdirs() }.writeBytes(TextureGen.png(prm))
                return ToolResult(true, "Texture $name (${prm.size}px, $style) created. Use it as SpriteRenderer Texture or MeshRenderer Texture.")
            }
            "generate_sprite" -> {
                val size = a.optInt("size", 64).coerceIn(16, 256)
                val color = TextureGen.parseColor(a.optString("color"), 0xFFFFFFFF.toInt())
                val px = SpriteShapes.draw(a.optString("shape", "circle"), size, color)
                val name = pngName(a.optString("name", "Sprite"))
                need().assetFile(name).writeBytes(PngEncoder.encode(size, size, px))
                return ToolResult(true, "Sprite $name created")
            }
            "compose_song" -> {
                val st = a.optString("style")
                val idx = Song.STYLES.indexOfFirst { it.equals(st, true) }.let { if (it < 0) Song.STYLES.indexOfFirst { s -> s.contains(st, true) || st.contains(s.split(' ')[0], true) } else it }.coerceAtLeast(0)
                val song = Song.compose(idx, a.optInt("seed", 7))
                val name = a.optString("name", song.name).let { if (it.endsWith(".song")) it else "$it.song" }
                song.name = name.removeSuffix(".song")
                need().writeAsset(name, song.toJson().toString())
                return ToolResult(true, "Song $name composed (${Song.STYLES[idx]}, ${song.bpm} bpm, ${song.tracks.size} tracks). Play it from a script: audio.playMusic(\"$name\", 0.6)")
            }
            "create_model" -> {
                val name = a.optString("name", "Model").let { if (it.endsWith(".smodel")) it else "$it.smodel" }
                val preset = a.optString("preset")
                val m: SModel = if (preset.isNotBlank()) {
                    val i = ModelPresets.NAMES.indexOfFirst { it.contains(preset, true) || preset.contains(it.split(' ')[0], true) }.coerceAtLeast(0)
                    ModelPresets.build(i)
                } else {
                    val model = SModel()
                    val parts = a.optJSONArray("parts") ?: JSONArray()
                    for (i in 0 until parts.length()) {
                        val o = parts.getJSONObject(i)
                        val kind = SModel.PRIMITIVES.indexOfFirst { it.equals(o.optString("shape", "Cube"), true) }.coerceAtLeast(0)
                        val part = SModel.primitive(kind)
                        part.name = o.optString("name", "${SModel.PRIMITIVES[kind]}$i")
                        part.color = TextureGen.parseColor(o.optString("color"), part.color)
                        fun arr(k: String, into: FloatArray) { val v = o.optJSONArray(k) ?: return; for (j in 0 until minOf(3, v.length())) into[j] = v.optDouble(j).toFloat() }
                        arr("pos", part.pos); arr("scale", part.scale); arr("rot", part.rot)
                        model.parts += part
                    }
                    model
                }
                need().writeAsset(name, m.toJson().toString())
                return ToolResult(true, "Model $name created (${m.parts.size} parts, clips: ${m.clips.joinToString { it.name }.ifBlank { "none" }}). Use MeshRenderer Mesh=Custom, Model=$name")
            }
            "set_controls" -> {
                val want = a.optString("preset")
                val l = com.sengine.engine.controls.ControlLayout.presets.firstOrNull { it.name.equals(want, true) }
                    ?: com.sengine.engine.controls.ControlLayout.presets.firstOrNull { it.name.contains(want, true) }
                    ?: return ToolResult(false, "Unknown preset")
                need().saveControls(l.copy())
                return ToolResult(true, "Controls set to ${l.name}: ${l.controls.joinToString { it.type + ":" + it.id }}")
            }
            "run_doctor" -> {
                val p = need()
                val issues = GameDoctor.check(p).filter { it.severity >= 1 }
                val syntax = p.listAssets().filter { it.endsWith(".js") }.mapNotNull { f -> GameDoctor.syntaxError(p.readAsset(f) ?: "", f)?.let { "$f: $it" } }
                val sb = StringBuilder()
                issues.forEach { sb.append(if (it.severity >= 2) "ERROR " else "warn ").append(it.title).append(" — ").append(it.detail).append('\n') }
                syntax.forEach { sb.append("ERROR syntax ").append(it).append('\n') }
                val errors = issues.count { it.severity >= 2 } + syntax.size
                return ToolResult(errors == 0, if (sb.isEmpty()) "Doctor: no problems found." else "Doctor found $errors error(s):\n$sb")
            }
            "play_test" -> return playTest(a)
            "build_apk" -> { buildRequested = true; return ToolResult(true, host.buildApk(need())) }
            "finish" -> { finished = true; finalSummary = a.optString("summary"); return ToolResult(true, "Finished.") }
        }
        return ToolResult(false, "Unknown tool '$tool'. Tools: ${TOOLS.joinToString { it.name }}")
    }

    private fun pngName(n: String): String { val b = n.trim().ifBlank { "Texture" }; return if (b.endsWith(".png")) b else "$b.png" }

    private fun normalizeType(t: String): String = when (t.lowercase()) {
        "sprite", "spriterenderer" -> "SpriteRenderer"; "text", "textrenderer", "label" -> "TextRenderer"
        "camera", "camera2d" -> "Camera"; "rigidbody", "rigidbody2d", "body" -> "Rigidbody2D"; "collider", "collider2d" -> "Collider2D"
        "script", "scriptcomponent" -> "Script"; "particles", "particleemitter" -> "ParticleEmitter"; "mesh", "meshrenderer" -> "MeshRenderer"
        else -> ComponentRegistry.types.keys.firstOrNull { it.equals(t, true) } ?: t
    }

    private fun applyTransform(go: GameObject, a: JSONObject) {
        fun f(k: String, cur: Float) = if (a.has(k)) a.optDouble(k, cur.toDouble()).toFloat() else cur
        go.x = f("x", go.x); go.y = f("y", go.y); go.z = f("z", go.z)
        go.rotation = f("rotation", go.rotation); go.rotX = f("rotX", go.rotX); go.rotY = f("rotY", go.rotY)
        go.scaleX = f("sx", go.scaleX); go.scaleY = f("sy", go.scaleY); go.scaleZ = f("sz", go.scaleZ)
        if (a.has("scale")) { val s = a.optDouble("scale", 1.0).toFloat(); go.scaleX = s; go.scaleY = s; go.scaleZ = s }
        if (a.has("tag")) go.tag = a.getString("tag")
        if (a.has("active")) go.active = a.getBoolean("active")
        if (a.has("order")) go.order = a.getInt("order")
    }

    /** Sets props by (case-insensitive) label; returns warnings for unknown names. */
    fun setProps(c: Component, props: JSONObject): List<String> {
        val warn = ArrayList<String>()
        val all = c.props()
        for (k in props.keys()) {
            val p = all.firstOrNull { it.name.equals(k, true) } ?: all.firstOrNull { it.name.replace(" ", "").equals(k.replace(" ", "").replace("_", ""), true) }
                ?: all.firstOrNull { k.length >= 4 && it.name.replace(" ", "").startsWith(k.replace(" ", "").replace("_", ""), true) }
            if (p == null) { warn += "${c.type} has no prop '$k' (props: ${all.joinToString { it.name }})"; continue }
            val v = props.get(k)
            try {
                when (p) {
                    is Prop.F -> p.set((v as? Number)?.toFloat() ?: v.toString().toFloat())
                    is Prop.I -> p.set((v as? Number)?.toInt() ?: v.toString().toDouble().toInt())
                    is Prop.B -> p.set(v as? Boolean ?: v.toString().toBoolean())
                    is Prop.S -> p.set(v.toString())
                    is Prop.Asset -> p.set(v.toString())
                    is Prop.Color -> p.set(if (v is Number) v.toLong().toInt() else TextureGen.parseColor(v.toString(), p.get()))
                    is Prop.Choice -> p.set(if (v is Number) v.toInt().coerceIn(0, p.options.size - 1) else p.options.indexOfFirst { it.equals(v.toString(), true) }.let { i -> if (i < 0) { warn += "${p.name} options: ${p.options}"; p.get() } else i })
                }
            } catch (e: Exception) { warn += "bad value for ${p.name}: $v" }
        }
        return warn
    }

    private fun describe(s: Scene): String {
        val sb = StringBuilder("Scene ${s.name} — ${s.objects.size} objects (gravity2D=${s.gravityY}, gravity3D=${s.gravity3D})\n")
        for (go in s.objects.take(150)) {
            sb.append(if (go.active) "" else "(template) ").append(go.name)
            go.parent?.let { sb.append(" <").append(it.name).append('>') }
            sb.append(" tag=").append(go.tag).append(" pos=(").append(fmt(go.x)).append(',').append(fmt(go.y)).append(',').append(fmt(go.z)).append(") scale=(")
                .append(fmt(go.scaleX)).append(',').append(fmt(go.scaleY)).append(") [")
            sb.append(go.components.joinToString { c ->
                val key = c.props().take(3).joinToString(" ") { p -> when (p) { is Prop.S -> "${p.name}=${p.get().take(24)}"; is Prop.Asset -> "${p.name}=${p.get()}"; else -> "" } }.trim()
                if (key.isEmpty()) c.type else "${c.type}{$key}"
            })
            sb.append("]\n")
        }
        if (s.objects.size > 150) sb.append("…").append(s.objects.size - 150).append(" more\n")
        return sb.toString()
    }

    private fun fmt(f: Float) = if (f == f.toInt().toFloat()) f.toInt().toString() else String.format("%.2f", f)

    /** Headless play-test: runs the scene for N seconds with scripted input and reports problems. */
    fun playTest(a: JSONObject): ToolResult {
        val p = need()
        val sceneName = a.optString("scene").ifBlank { p.startScene }
        if (!p.sceneExists(sceneName)) return ToolResult(false, "Scene $sceneName not found")
        val secs = a.optDouble("seconds", 8.0).coerceIn(1.0, 60.0)
        val press = a.optJSONArray("press")?.let { arr -> (0 until arr.length()).map { arr.optString(it) } } ?: emptyList()
        val e = Engine(p, p.loadScene(sceneName))
        e.gameView.widthPx = 1600; e.gameView.heightPx = 900
        val errors = LinkedHashSet<String>(); val warns = LinkedHashSet<String>(); val logs = ArrayList<String>()
        e.listeners.add(object : Engine.Listener {
            override fun onLog(level: Int, message: String) {
                when { level >= 2 -> errors += message; level == 1 -> warns += message; else -> if (logs.size < 20) logs += message }
            }
        })
        e.play()
        val frames = (secs * 60).toInt()
        val t0 = System.currentTimeMillis()
        var scenesSeen = LinkedHashSet<String>()
        for (i in 0 until frames) {
            // wander + periodically press buttons to exercise gameplay code
            e.input.joyX = kotlin.math.sin(i / 50f); e.input.joyY = kotlin.math.cos(i / 70f) * 0.5f
            val on = (i / 20) % 2 == 0
            e.input.rawButtons["A"] = on; e.input.rawButtons["Fire"] = on; e.input.rawButtons["Gas"] = true
            for (b in press) e.input.rawButtons[b] = on
            e.input.rawSticks["aim"] = floatArrayOf(kotlin.math.cos(i / 30f), kotlin.math.sin(i / 30f))
            synchronized(e.lock) { e.tick(1f / 60f) }
            scenesSeen += e.scene.name
            if (errors.size > 15) break
        }
        val ms = System.currentTimeMillis() - t0
        val s = e.scene
        val active = s.objects.count { it.isActiveInHierarchy() && !it.destroyed }
        val hud = s.objects.filter { it.isActiveInHierarchy() }.mapNotNull { go -> go.get<TextRenderer>()?.text?.takeIf { t -> t.isNotBlank() }?.let { "${go.name}='${it.take(40)}'" } }.take(15)
        val buttons = s.objects.filter { it.isActiveInHierarchy() && it.get<UIButton>() != null }.map { it.name }.take(15)
        try { synchronized(e.lock) { e.stop(); e.tick(0f) } } catch (_: Exception) {}
        val sb = StringBuilder()
        sb.append("Play-test of '$sceneName' for ${"%.1f".format(secs)}s (${ms}ms real, ~${if (ms > 0) (frames * 1000L / ms) else 0} sim fps). Scenes visited: $scenesSeen. Active objects at end: $active.\n")
        sb.append("Errors (${errors.size}): ").append(if (errors.isEmpty()) "none" else errors.take(10).joinToString("\n  ")).append('\n')
        if (warns.isNotEmpty()) sb.append("Warnings: ").append(warns.take(8).joinToString("\n  ")).append('\n')
        if (logs.isNotEmpty()) sb.append("Logs: ").append(logs.take(10).joinToString(" | ")).append('\n')
        sb.append("HUD: ").append(hud.joinToString(", ").ifBlank { "(no visible text)" }).append('\n')
        sb.append("Visible buttons: ").append(buttons.joinToString(", ").ifBlank { "none" })
        return ToolResult(errors.isEmpty(), sb.toString())
    }
}

/** Tiny vector-ish sprite painter used by generate_sprite (pure Kotlin, anti-aliased edges). */
object SpriteShapes {
    val SHAPES = listOf("circle", "box", "ship", "tank", "character", "coin", "star", "bullet", "heart", "tree")

    fun draw(shape: String, n: Int, color: Int): IntArray {
        val px = IntArray(n * n)
        val dark = TextureGen.shade(color, 0.55f); val light = TextureGen.mix(color, 0xFFFFFFFF.toInt(), 0.35f)
        fun put(x: Int, y: Int, c: Int) { if (x in 0 until n && y in 0 until n) px[y * n + x] = c }
        fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, c: Int, shadeIt: Boolean = true) {
            for (y in 0 until n) for (x in 0 until n) {
                val dx = (x + 0.5f - cx) / rx; val dy = (y + 0.5f - cy) / ry
                val d = dx * dx + dy * dy
                if (d <= 1f) put(x, y, if (!shadeIt) c else if (d > 0.8f) dark else if (dx < -0.2f && dy < -0.2f) light else c)
            }
        }
        fun rect(x0: Float, y0: Float, x1: Float, y1: Float, c: Int, border: Boolean = true) {
            for (y in (y0).toInt() until (y1).toInt()) for (x in (x0).toInt() until (x1).toInt()) {
                val edge = border && (x == x0.toInt() || y == y0.toInt() || x == x1.toInt() - 1 || y == y1.toInt() - 1)
                put(x, y, if (edge) dark else c)
            }
        }
        fun poly(pts: List<Pair<Float, Float>>, c: Int) {
            for (y in 0 until n) for (x in 0 until n) {
                var inside = false; var j = pts.size - 1
                val fx = x + 0.5f; val fy = y + 0.5f
                for (i in pts.indices) {
                    val (xi, yi) = pts[i]; val (xj, yj) = pts[j]
                    if ((yi > fy) != (yj > fy) && fx < (xj - xi) * (fy - yi) / (yj - yi) + xi) inside = !inside
                    j = i
                }
                if (inside) put(x, y, c)
            }
        }
        val f = n.toFloat()
        when (shape.lowercase()) {
            "box" -> rect(f * 0.08f, f * 0.08f, f * 0.92f, f * 0.92f, color)
            "ship" -> { poly(listOf(f * 0.5f to f * 0.05f, f * 0.9f to f * 0.85f, f * 0.5f to f * 0.68f, f * 0.1f to f * 0.85f), color); ellipse(f * 0.5f, f * 0.42f, f * 0.1f, f * 0.14f, 0xFF7FD8FF.toInt()) }
            "tank" -> { rect(f * 0.12f, f * 0.1f, f * 0.3f, f * 0.9f, dark); rect(f * 0.7f, f * 0.1f, f * 0.88f, f * 0.9f, dark); rect(f * 0.26f, f * 0.2f, f * 0.74f, f * 0.82f, color); ellipse(f * 0.5f, f * 0.52f, f * 0.16f, f * 0.16f, light, false); rect(f * 0.46f, f * 0.02f, f * 0.54f, f * 0.5f, dark, false) }
            "character" -> { ellipse(f * 0.5f, f * 0.25f, f * 0.16f, f * 0.16f, 0xFFF1C27D.toInt()); rect(f * 0.32f, f * 0.4f, f * 0.68f, f * 0.72f, color); rect(f * 0.34f, f * 0.72f, f * 0.47f, f * 0.95f, dark); rect(f * 0.53f, f * 0.72f, f * 0.66f, f * 0.95f, dark) }
            "coin" -> { ellipse(f / 2, f / 2, f * 0.42f, f * 0.42f, color); ellipse(f / 2, f / 2, f * 0.26f, f * 0.26f, light, false) }
            "star" -> { val pts = (0 until 10).map { i -> val r = if (i % 2 == 0) f * 0.46f else f * 0.2f; val a = -Math.PI / 2 + i * Math.PI / 5; (f / 2 + r * Math.cos(a)).toFloat() to (f / 2 + r * Math.sin(a)).toFloat() }; poly(pts, color) }
            "bullet" -> ellipse(f / 2, f / 2, f * 0.18f, f * 0.42f, color)
            "heart" -> { ellipse(f * 0.34f, f * 0.36f, f * 0.2f, f * 0.2f, color, false); ellipse(f * 0.66f, f * 0.36f, f * 0.2f, f * 0.2f, color, false); poly(listOf(f * 0.15f to f * 0.42f, f * 0.85f to f * 0.42f, f * 0.5f to f * 0.9f), color) }
            "tree" -> { rect(f * 0.43f, f * 0.6f, f * 0.57f, f * 0.95f, 0xFF6D4C41.toInt()); ellipse(f / 2, f * 0.38f, f * 0.34f, f * 0.32f, color) }
            else -> ellipse(f / 2, f / 2, f * 0.45f, f * 0.45f, color)
        }
        return px
    }
}
