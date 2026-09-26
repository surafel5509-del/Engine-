package com.sengine.engine.texture

import com.sengine.engine.anim.AnimationClip
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Multi-frame, multi-layer pixel-art document used by the Sprite Studio.
 * Frames × layers of ARGB pixels, drawing tools, undo/redo, and export to PNG sprite sheets + .anim clips.
 */
class PixelDoc(var width: Int = 32, var height: Int = 32) {
    class Layer(var name: String, val px: IntArray, var visible: Boolean = true, var opacity: Float = 1f)
    class Frame(val layers: MutableList<Layer>, var durationMs: Int = 100)

    val frames = ArrayList<Frame>()
    var frame = 0
    var layer = 0
    var fps = 10f
    var loop = true
    private val undo = ArrayDeque<String>()
    private val redo = ArrayDeque<String>()

    init { frames += newFrame() }

    private fun newFrame() = Frame(mutableListOf(Layer("Layer 1", IntArray(width * height))))
    val cur: IntArray get() = frames[frame].layers[layer].px

    // ------------------------------------------------------------------ undo
    fun checkpoint() { undo.addLast(toJson().toString()); if (undo.size > 60) undo.removeFirst(); redo.clear() }
    fun canUndo() = undo.isNotEmpty()
    fun canRedo() = redo.isNotEmpty()
    fun undo() { val s = undo.removeLastOrNull() ?: return; redo.addLast(toJson().toString()); load(JSONObject(s)) }
    fun redo() { val s = redo.removeLastOrNull() ?: return; undo.addLast(toJson().toString()); load(JSONObject(s)) }

    // ------------------------------------------------------------------ drawing
    var mirrorX = false
    var mirrorY = false

    fun inside(x: Int, y: Int) = x in 0 until width && y in 0 until height
    fun get(x: Int, y: Int): Int = if (inside(x, y)) cur[y * width + x] else 0

    fun set(x: Int, y: Int, c: Int, brush: Int = 1) {
        val r0 = -(brush - 1) / 2; val r1 = brush / 2
        for (dy in r0..r1) for (dx in r0..r1) {
            plot(x + dx, y + dy, c)
            if (mirrorX) plot(width - 1 - (x + dx), y + dy, c)
            if (mirrorY) plot(x + dx, height - 1 - (y + dy), c)
            if (mirrorX && mirrorY) plot(width - 1 - (x + dx), height - 1 - (y + dy), c)
        }
    }

    private fun plot(x: Int, y: Int, c: Int) { if (inside(x, y)) cur[y * width + x] = c }

    fun line(x0: Int, y0: Int, x1: Int, y1: Int, c: Int, brush: Int = 1) {
        var x = x0; var y = y0
        val dx = abs(x1 - x0); val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            set(x, y, c, brush)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    fun rect(x0: Int, y0: Int, x1: Int, y1: Int, c: Int, fill: Boolean) {
        val ax = min(x0, x1); val bx = max(x0, x1); val ay = min(y0, y1); val by = max(y0, y1)
        for (y in ay..by) for (x in ax..bx) if (fill || x == ax || x == bx || y == ay || y == by) set(x, y, c)
    }

    fun ellipse(x0: Int, y0: Int, x1: Int, y1: Int, c: Int, fill: Boolean) {
        val ax = min(x0, x1); val bx = max(x0, x1); val ay = min(y0, y1); val by = max(y0, y1)
        val cx = (ax + bx) / 2f; val cy = (ay + by) / 2f
        val rx = max(0.5f, (bx - ax) / 2f); val ry = max(0.5f, (by - ay) / 2f)
        for (y in ay..by) for (x in ax..bx) {
            val nx = (x - cx) / rx; val ny = (y - cy) / ry
            val d = nx * nx + ny * ny
            if (d <= 1.05f) {
                if (fill) set(x, y, c) else {
                    val inner = ((x - cx) / (rx - 1f).coerceAtLeast(0.3f)).let { it * it } + ((y - cy) / (ry - 1f).coerceAtLeast(0.3f)).let { it * it }
                    if (inner > 1f) set(x, y, c)
                }
            }
        }
    }

    fun fill(x: Int, y: Int, c: Int) {
        if (!inside(x, y)) return
        val target = get(x, y)
        if (target == c) return
        val stack = ArrayDeque<Int>(); stack.addLast(y * width + x)
        val px = cur
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            if (px[i] != target) continue
            px[i] = c
            val ix = i % width; val iy = i / width
            if (ix > 0) stack.addLast(i - 1); if (ix < width - 1) stack.addLast(i + 1)
            if (iy > 0) stack.addLast(i - width); if (iy < height - 1) stack.addLast(i + width)
        }
    }

    /** Replace every pixel of one colour on the layer (global fill). */
    fun replaceColor(from: Int, to: Int) { val px = cur; for (i in px.indices) if (px[i] == from) px[i] = to }

    fun flipH() { val px = cur; for (y in 0 until height) for (x in 0 until width / 2) { val a = y * width + x; val b = y * width + width - 1 - x; val t = px[a]; px[a] = px[b]; px[b] = t } }
    fun flipV() { val px = cur; for (y in 0 until height / 2) for (x in 0 until width) { val a = y * width + x; val b = (height - 1 - y) * width + x; val t = px[a]; px[a] = px[b]; px[b] = t } }
    fun shift(dx: Int, dy: Int) {
        val px = cur; val copy = px.copyOf()
        for (y in 0 until height) for (x in 0 until width) px[Math.floorMod(y + dy, height) * width + Math.floorMod(x + dx, width)] = copy[y * width + x]
    }
    fun rotate90() {
        if (width != height) return
        val px = cur; val copy = px.copyOf()
        for (y in 0 until height) for (x in 0 until width) px[x * width + (width - 1 - y)] = copy[y * width + x]
    }
    fun clear() { cur.fill(0) }

    /** Adds a 1px outline of [c] around opaque pixels. */
    fun outline(c: Int) {
        val px = cur; val copy = px.copyOf()
        for (y in 0 until height) for (x in 0 until width) {
            if ((copy[y * width + x] ushr 24) != 0) continue
            var near = false
            for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val nx = x + dx; val ny = y + dy
                if (inside(nx, ny) && (copy[ny * width + nx] ushr 24) > 0) near = true
            }
            if (near) px[y * width + x] = c
        }
    }

    /** Adds a darker drop shadow one pixel down-right. */
    fun dropShadow() {
        val px = cur; val copy = px.copyOf()
        for (y in height - 2 downTo 0) for (x in width - 2 downTo 0) {
            val s = copy[y * width + x]
            if ((s ushr 24) != 0 && (copy[(y + 1) * width + x + 1] ushr 24) == 0) px[(y + 1) * width + x + 1] = 0x66000000
        }
    }

    // ------------------------------------------------------------------ frames & layers
    fun addFrame(duplicate: Boolean) {
        val f = if (duplicate) Frame(frames[frame].layers.map { Layer(it.name, it.px.copyOf(), it.visible, it.opacity) }.toMutableList(), frames[frame].durationMs)
        else Frame(frames[frame].layers.map { Layer(it.name, IntArray(width * height), it.visible, it.opacity) }.toMutableList())
        frames.add(frame + 1, f); frame++
    }
    fun deleteFrame() { if (frames.size > 1) { frames.removeAt(frame); frame = frame.coerceAtMost(frames.size - 1) } }
    fun moveFrame(d: Int) { val j = frame + d; if (j in frames.indices) { val t = frames[frame]; frames[frame] = frames[j]; frames[j] = t; frame = j } }

    fun addLayer() {
        for (f in frames) f.layers.add(layer + 1, Layer("Layer ${f.layers.size + 1}", IntArray(width * height)))
        layer++
    }
    fun deleteLayer() { if (frames[0].layers.size > 1) { for (f in frames) f.layers.removeAt(layer); layer = layer.coerceAtMost(frames[0].layers.size - 1) } }
    fun mergeDown() {
        if (layer == 0) return
        for (f in frames) { val top = f.layers[layer]; val bot = f.layers[layer - 1]; for (i in bot.px.indices) bot.px[i] = over(top.px[i], bot.px[i], top.opacity); f.layers.removeAt(layer) }
        layer--
    }

    /** Composited pixels of a frame (all visible layers). */
    fun composite(fi: Int = frame): IntArray {
        val out = IntArray(width * height)
        for (l in frames[fi].layers) if (l.visible) for (i in out.indices) out[i] = over(l.px[i], out[i], l.opacity)
        return out
    }

    fun resize(w: Int, h: Int) {
        for (f in frames) for (l in f.layers) {
            val old = l.px.copyOf(); val nw = IntArray(w * h)
            for (y in 0 until min(h, height)) for (x in 0 until min(w, width)) nw[y * w + x] = old[y * width + x]
            f.layers[f.layers.indexOf(l)] = Layer(l.name, nw, l.visible, l.opacity)
        }
        width = w; height = h
    }

    // ------------------------------------------------------------------ export
    /** Horizontal sprite sheet of all frames (columns = frames). */
    fun sheet(): IntArray {
        val w = width * frames.size
        val out = IntArray(w * height)
        for (fi in frames.indices) { val c = composite(fi); for (y in 0 until height) System.arraycopy(c, y * width, out, y * w + fi * width, width) }
        return out
    }
    fun sheetPng(): ByteArray = PngEncoder.encode(width * frames.size, height, sheet())
    fun framePng(fi: Int = frame): ByteArray = PngEncoder.encode(width, height, composite(fi))
    fun animClip(texture: String) = AnimationClip(texture, frames.size, 1, frames.indices.toMutableList(), fps, loop)

    /** Imports a horizontal sheet (or single image when cols = 1). */
    fun importSheet(w: Int, h: Int, px: IntArray, cols: Int) {
        val fw = w / cols.coerceAtLeast(1)
        width = fw; height = h; frames.clear()
        for (c in 0 until cols) {
            val p = IntArray(fw * h)
            for (y in 0 until h) System.arraycopy(px, y * w + c * fw, p, y * fw, fw)
            frames += Frame(mutableListOf(Layer("Layer 1", p)))
        }
        frame = 0; layer = 0
    }

    // ------------------------------------------------------------------ persistence (.spx)
    fun toJson(): JSONObject = JSONObject().put("format", "spx").put("w", width).put("h", height).put("fps", fps.toDouble()).put("loop", loop)
        .put("frame", frame).put("layer", layer)
        .put("frames", JSONArray().also { fa -> frames.forEach { f ->
            fa.put(JSONObject().put("ms", f.durationMs).put("layers", JSONArray().also { la -> f.layers.forEach { l ->
                la.put(JSONObject().put("name", l.name).put("visible", l.visible).put("opacity", l.opacity.toDouble()).put("px", encode(l.px)))
            } }))
        } })

    fun load(o: JSONObject) {
        width = o.optInt("w", 32); height = o.optInt("h", 32); fps = o.optDouble("fps", 10.0).toFloat(); loop = o.optBoolean("loop", true)
        frames.clear()
        val fa = o.optJSONArray("frames") ?: JSONArray()
        for (i in 0 until fa.length()) {
            val fo = fa.getJSONObject(i); val la = fo.optJSONArray("layers") ?: JSONArray()
            val layers = ArrayList<Layer>()
            for (j in 0 until la.length()) { val lo = la.getJSONObject(j); layers += Layer(lo.optString("name", "Layer"), decode(lo.optString("px"), width * height), lo.optBoolean("visible", true), lo.optDouble("opacity", 1.0).toFloat()) }
            if (layers.isEmpty()) layers += Layer("Layer 1", IntArray(width * height))
            frames += Frame(layers, fo.optInt("ms", 100))
        }
        if (frames.isEmpty()) frames += newFrame()
        frame = o.optInt("frame", 0).coerceIn(0, frames.size - 1); layer = o.optInt("layer", 0).coerceIn(0, frames[0].layers.size - 1)
    }

    companion object {
        /** Classic palettes for pixel art. */
        val PALETTES: Map<String, List<Int>> = linkedMapOf(
            "PICO-8" to listOf(0xFF000000, 0xFF1D2B53, 0xFF7E2553, 0xFF008751, 0xFFAB5236, 0xFF5F574F, 0xFFC2C3C7, 0xFFFFF1E8, 0xFFFF004D, 0xFFFFA300, 0xFFFFEC27, 0xFF00E436, 0xFF29ADFF, 0xFF83769C, 0xFFFF77A8, 0xFFFFCCAA).map { it.toInt() },
            "Game Boy" to listOf(0xFF0F380F, 0xFF306230, 0xFF8BAC0F, 0xFF9BBC0F).map { it.toInt() },
            "Grayscale" to (0..15).map { (0xFF000000 or ((it * 17L) shl 16) or ((it * 17L) shl 8) or (it * 17L)).toInt() },
            "Endesga" to listOf(0xFFBE4A2F, 0xFFD77643, 0xFFEAD4AA, 0xFFE4A672, 0xFFB86F50, 0xFF733E39, 0xFF3E2731, 0xFFA22633, 0xFFE43B44, 0xFFF77622, 0xFFFEAE34, 0xFFFEE761, 0xFF63C74D, 0xFF3E8948, 0xFF265C42, 0xFF193C3E,
                0xFF124E89, 0xFF0099DB, 0xFF2CE8F5, 0xFFFFFFFF, 0xFFC0CBDC, 0xFF8B9BB4, 0xFF5A6988, 0xFF3A4466, 0xFF262B44, 0xFF181425, 0xFFFF0044, 0xFF68386C, 0xFFB55088, 0xFFF6757A, 0xFFE8B796, 0xFFC28569).map { it.toInt() },
            "Skin & Hair" to listOf(0xFFFFE0C4, 0xFFF1C27D, 0xFFE0AC69, 0xFFC68642, 0xFF8D5524, 0xFF4A2C17, 0xFF2B1B0E, 0xFFF4D03F, 0xFFB03A2E, 0xFF1B1B1B).map { it.toInt() },
        )

        fun over(src: Int, dst: Int, opacity: Float = 1f): Int {
            val sa = ((src ushr 24) * opacity).toInt()
            if (sa == 0) return dst
            if (sa == 255) return src
            val da = dst ushr 24
            val oa = sa + da * (255 - sa) / 255
            if (oa == 0) return 0
            fun ch(s: Int): Int { val sc = (src shr s) and 0xFF; val dc = (dst shr s) and 0xFF; return (sc * sa + dc * da * (255 - sa) / 255) / oa }
            return (oa shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        private fun encode(px: IntArray): String {
            val b = java.io.ByteArrayOutputStream()
            java.util.zip.DeflaterOutputStream(b).use { d -> val bb = java.nio.ByteBuffer.allocate(px.size * 4); bb.asIntBuffer().put(px); d.write(bb.array()) }
            return java.util.Base64.getEncoder().encodeToString(b.toByteArray())
        }
        private fun decode(s: String, n: Int): IntArray {
            if (s.isBlank()) return IntArray(n)
            return try {
                val raw = java.util.zip.InflaterInputStream(java.util.Base64.getDecoder().decode(s).inputStream()).readBytes()
                val out = IntArray(n); java.nio.ByteBuffer.wrap(raw).asIntBuffer().get(out, 0, minOf(n, raw.size / 4)); out
            } catch (_: Exception) { IntArray(n) }
        }
    }
}
