package com.sengine.engine

import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Scene
import com.sengine.engine.core.UIButton
import com.sengine.engine.core.UIPanel
import com.sengine.engine.core.UIProgress
import org.json.JSONObject
import java.io.File

/** Hooks into the host activity (vibration, quitting, links). */
interface Platform {
    fun vibrate(ms: Int) {}
    fun quit() {}
    fun openUrl(url: String) {}
    fun toast(text: String) {}
}

/** Persistent key/value storage for games (high scores, unlocked levels, settings). */
class Storage(private val file: File) {
    private var data: JSONObject? = null

    private fun obj(): JSONObject {
        data?.let { return it }
        val o = try { if (file.exists()) JSONObject(file.readText()) else JSONObject() } catch (_: Exception) { JSONObject() }
        data = o
        return o
    }

    fun get(key: String): Any? = obj().opt(key)
    fun has(key: String) = obj().has(key)
    fun set(key: String, value: Any?) {
        if (value == null) obj().remove(key) else obj().put(key, value)
        save()
    }
    fun remove(key: String) { obj().remove(key); save() }
    fun clear() { data = JSONObject(); save() }
    fun keys(): List<String> = obj().keys().asSequence().toList()

    private fun save() {
        try { file.parentFile?.mkdirs(); file.writeText(obj().toString()) } catch (_: Exception) {}
    }
}

/**
 * In-game UI: hit-tests UIButtons from pointer events, runs button actions and publishes the
 * interactive screen rectangles so the on-screen controller lets UI touches through.
 *
 * Button actions (separate several with ';'):
 *   scene:Name  load a scene        call:fn     call fn(buttonName) on all scripts
 *   show:Obj / hide:Obj / toggle:Obj            activate or deactivate an object
 *   pause / resume   set time.scale to 0 / 1    reload / quit / url:https://...
 */
class UISystem(private val engine: Engine) {

    private class Hit(val go: GameObject, val l: Float, val t: Float, val r: Float, val b: Float, val button: UIButton?)
    private val hits = ArrayList<Hit>()

    fun process() {
        val scene = engine.scene
        val input = engine.input
        val w = engine.gameView.widthPx.toFloat().coerceAtLeast(1f)
        val h = engine.gameView.heightPx.toFloat().coerceAtLeast(1f)
        val hw = Scene.uiHalfW
        hits.clear()
        val objs = scene.objects.withIndex().filter { it.value.isActiveInHierarchy() && !it.value.destroyed }
            .sortedWith(compareBy({ it.value.order }, { it.index })).map { it.value }
        for (go in objs) {
            val btn = go.get<UIButton>()?.takeIf { it.enabled }
            val panel = go.get<UIPanel>()?.takeIf { it.enabled && com.sengine.engine.render.GL.a(it.color) > 0.05f }
            val (bw, bh) = when {
                btn != null -> btn.width to btn.height
                panel != null -> panel.width to panel.height
                else -> continue
            }
            val m = go.world
            val ex = bw * m.scaleX / 2f; val ey = bh * m.scaleY / 2f
            val l = (m.tx - ex + hw) / (2 * hw) * w; val r = (m.tx + ex + hw) / (2 * hw) * w
            val t = (5f - (m.ty + ey)) / 10f * h; val b = (5f - (m.ty - ey)) / 10f * h
            hits.add(Hit(go, l, t, r, b, btn))
        }
        // topmost first
        hits.reverse()
        val rects = FloatArray(hits.size * 4)
        hits.forEachIndexed { i, it -> rects[i * 4] = it.l; rects[i * 4 + 1] = it.t; rects[i * 4 + 2] = it.r; rects[i * 4 + 3] = it.b }
        input.uiRects = rects

        while (true) {
            val p = input.pointers.poll() ?: break
            val top = hits.firstOrNull { p.x >= it.l && p.x <= it.r && p.y >= it.t && p.y <= it.b }
            when (p.action) {
                0 -> {
                    if (top != null) input.uiCaptured = true
                    val b = top?.button
                    if (b != null && b.interactable) { b.pressed = true; b.pointer = p.id }
                }
                1 -> for (hit in hits) { val b = hit.button ?: continue; if (b.pointer == p.id) b.pressed = top === hit }
                else -> for (hit in hits) {
                    val b = hit.button ?: continue
                    if (b.pointer != p.id) continue
                    val inside = top === hit
                    b.pressed = false; b.pointer = -1
                    if (inside && b.interactable) click(hit.go, b)
                }
            }
        }
    }

    /** Simulates a click (also used by tests and by scripts via ui.click(name)). */
    fun click(go: GameObject, b: UIButton) {
        b.clicks++
        if (b.sound.isNotBlank()) engine.audio.play(b.sound) else engine.audio.play("ui_click.wav", 0.7f)
        engine.scripts.broadcast("onUIClick", go.name)
        for (raw in b.action.split(';')) {
            val a = raw.trim()
            if (a.isEmpty()) continue
            val arg = a.substringAfter(':', "").trim()
            when (a.substringBefore(':').trim().lowercase()) {
                "scene", "load" -> engine.requestLoadScene(arg)
                "call" -> engine.scripts.broadcast(arg, go.name)
                "show" -> engine.scene.find(arg)?.active = true
                "hide" -> engine.scene.find(arg)?.active = false
                "toggle" -> engine.scene.find(arg)?.let { it.active = !it.active }
                "pause" -> engine.timeScale = 0f
                "resume" -> engine.timeScale = 1f
                "reload" -> engine.requestLoadScene(engine.scene.name)
                "quit" -> engine.platform?.quit()
                "url" -> engine.platform?.openUrl(a.substringAfter(':'))
                else -> engine.log(1, "Unknown button action '$a' on ${go.name}")
            }
        }
    }

    fun clickByName(name: String): Boolean {
        val go = engine.scene.objects.firstOrNull { it.name == name && it.isActiveInHierarchy() } ?: return false
        val b = go.get<UIButton>() ?: return false
        click(go, b)
        return true
    }

    @Suppress("unused")
    private fun progressOf(go: GameObject) = go.get<UIProgress>()
}
