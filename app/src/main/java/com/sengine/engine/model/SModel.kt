package com.sengine.engine.model

import com.sengine.engine.math.Mat4
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * S Engine native model (.smodel): a hierarchy of editable polygon parts with per-face colours
 * and keyframed part animation clips. Created by the Model Editor, rendered by MeshRenderer.
 */
class SPart(
    var name: String = "Part",
    var parent: Int = -1,
    var color: Int = 0xFFB0B8C8.toInt(),
    val pos: FloatArray = floatArrayOf(0f, 0f, 0f),
    val rot: FloatArray = floatArrayOf(0f, 0f, 0f),
    val scale: FloatArray = floatArrayOf(1f, 1f, 1f),
    val verts: ArrayList<FloatArray> = ArrayList(),
    val faces: ArrayList<IntArray> = ArrayList(),
    /** Per-face colour (0 = use part colour). Kept the same length as [faces]. */
    val faceColors: ArrayList<Int> = ArrayList(),
    var smooth: Boolean = false,
    var visible: Boolean = true,
) {
    fun copy(): SPart = SPart(name, parent, color, pos.copyOf(), rot.copyOf(), scale.copyOf(),
        ArrayList(verts.map { it.copyOf() }), ArrayList(faces.map { it.copyOf() }), ArrayList(faceColors), smooth, visible)

    fun fixColors() { while (faceColors.size < faces.size) faceColors.add(0); while (faceColors.size > faces.size) faceColors.removeAt(faceColors.size - 1) }

    fun toJson(): JSONObject {
        fixColors()
        val v = JSONArray(); verts.forEach { v.put(r(it[0])); v.put(r(it[1])); v.put(r(it[2])) }
        val f = JSONArray(); faces.forEach { fa -> f.put(JSONArray().also { a -> fa.forEach { a.put(it) } }) }
        val fc = JSONArray(); faceColors.forEach { fc.put(it) }
        return JSONObject().put("name", name).put("parent", parent).put("color", color)
            .put("pos", arr(pos)).put("rot", arr(rot)).put("scale", arr(scale))
            .put("v", v).put("f", f).put("fc", fc).put("smooth", smooth).put("visible", visible)
    }

    companion object {
        private fun r(x: Float) = Math.round(x * 10000.0) / 10000.0
        fun arr(a: FloatArray) = JSONArray().also { j -> a.forEach { j.put(r(it)) } }
        fun farr(j: JSONArray?, def: FloatArray): FloatArray = if (j == null) def else FloatArray(def.size) { j.optDouble(it, def[it].toDouble()).toFloat() }
        fun fromJson(o: JSONObject): SPart {
            val p = SPart(o.optString("name", "Part"), o.optInt("parent", -1), o.optInt("color", 0xFFB0B8C8.toInt()),
                farr(o.optJSONArray("pos"), floatArrayOf(0f, 0f, 0f)), farr(o.optJSONArray("rot"), floatArrayOf(0f, 0f, 0f)),
                farr(o.optJSONArray("scale"), floatArrayOf(1f, 1f, 1f)))
            val v = o.optJSONArray("v") ?: JSONArray()
            var i = 0
            while (i + 2 < v.length()) { p.verts.add(floatArrayOf(v.getDouble(i).toFloat(), v.getDouble(i + 1).toFloat(), v.getDouble(i + 2).toFloat())); i += 3 }
            val f = o.optJSONArray("f") ?: JSONArray()
            for (k in 0 until f.length()) { val a = f.getJSONArray(k); p.faces.add(IntArray(a.length()) { a.getInt(it) }) }
            val fc = o.optJSONArray("fc")
            if (fc != null) for (k in 0 until fc.length()) p.faceColors.add(fc.optInt(k))
            p.fixColors()
            p.smooth = o.optBoolean("smooth", false); p.visible = o.optBoolean("visible", true)
            return p
        }
    }
}

class SKey(var t: Float, val pos: FloatArray, val rot: FloatArray, val scale: FloatArray) {
    fun toJson(): JSONObject = JSONObject().put("t", t.toDouble()).put("pos", SPart.arr(pos)).put("rot", SPart.arr(rot)).put("scale", SPart.arr(scale))
    companion object {
        fun fromJson(o: JSONObject) = SKey(o.optDouble("t", 0.0).toFloat(), SPart.farr(o.optJSONArray("pos"), floatArrayOf(0f, 0f, 0f)),
            SPart.farr(o.optJSONArray("rot"), floatArrayOf(0f, 0f, 0f)), SPart.farr(o.optJSONArray("scale"), floatArrayOf(1f, 1f, 1f)))
    }
}

class STrack(var part: String, val keys: MutableList<SKey> = ArrayList())

class SClip(var name: String = "Idle", var length: Float = 1f, var loop: Boolean = true, val tracks: MutableList<STrack> = ArrayList()) {
    fun track(part: String): STrack = tracks.firstOrNull { it.part == part } ?: STrack(part).also { tracks.add(it) }
    fun toJson(): JSONObject = JSONObject().put("name", name).put("length", length.toDouble()).put("loop", loop)
        .put("tracks", JSONArray().also { a -> tracks.forEach { t -> a.put(JSONObject().put("part", t.part).put("keys", JSONArray().also { k -> t.keys.sortedBy { it.t }.forEach { k.put(it.toJson()) } })) } })
    companion object {
        fun fromJson(o: JSONObject): SClip {
            val c = SClip(o.optString("name", "Clip"), o.optDouble("length", 1.0).toFloat(), o.optBoolean("loop", true))
            val ts = o.optJSONArray("tracks") ?: JSONArray()
            for (i in 0 until ts.length()) {
                val t = ts.getJSONObject(i); val tr = STrack(t.optString("part"))
                val ks = t.optJSONArray("keys") ?: JSONArray()
                for (k in 0 until ks.length()) tr.keys.add(SKey.fromJson(ks.getJSONObject(k)))
                tr.keys.sortBy { it.t }
                c.tracks.add(tr)
            }
            return c
        }
    }
}

class SModel(val parts: MutableList<SPart> = ArrayList(), val clips: MutableList<SClip> = ArrayList()) {

    fun copy(): SModel = fromJson(toJson())

    fun toJson(): JSONObject = JSONObject().put("format", "smodel").put("version", 1)
        .put("parts", JSONArray().also { a -> parts.forEach { a.put(it.toJson()) } })
        .put("clips", JSONArray().also { a -> clips.forEach { a.put(it.toJson()) } })

    fun clip(name: String): SClip? = clips.firstOrNull { it.name.equals(name, true) }

    /** Local TRS of a part at [time] in [clip] (falls back to the rest pose). */
    fun pose(i: Int, clip: SClip?, time: Float, pos: FloatArray, rot: FloatArray, scale: FloatArray) {
        val p = parts[i]
        p.pos.copyInto(pos); p.rot.copyInto(rot); p.scale.copyInto(scale)
        val tr = clip?.tracks?.firstOrNull { it.part == p.name } ?: return
        if (tr.keys.isEmpty()) return
        var t = time
        if (clip.length > 0f) t = if (clip.loop) ((time % clip.length) + clip.length) % clip.length else time.coerceIn(0f, clip.length)
        val ks = tr.keys
        if (t <= ks.first().t || ks.size == 1) { ks.first().let { it.pos.copyInto(pos); it.rot.copyInto(rot); it.scale.copyInto(scale) }; return }
        if (t >= ks.last().t) {
            // loop back towards the first key for smooth cycles
            val a = ks.last(); val b = ks.first()
            val span = clip.length - a.t
            if (clip.loop && span > 1e-4f) lerpKey(a, b, (t - a.t) / span, pos, rot, scale)
            else { a.pos.copyInto(pos); a.rot.copyInto(rot); a.scale.copyInto(scale) }
            return
        }
        for (k in 0 until ks.size - 1) {
            val a = ks[k]; val b = ks[k + 1]
            if (t >= a.t && t <= b.t) { lerpKey(a, b, if (b.t - a.t > 1e-5f) (t - a.t) / (b.t - a.t) else 0f, pos, rot, scale); return }
        }
    }

    private fun lerpKey(a: SKey, b: SKey, f0: Float, pos: FloatArray, rot: FloatArray, scale: FloatArray) {
        val f = f0 * f0 * (3f - 2f * f0) // ease in-out
        for (k in 0 until 3) {
            pos[k] = a.pos[k] + (b.pos[k] - a.pos[k]) * f
            rot[k] = a.rot[k] + (b.rot[k] - a.rot[k]) * f
            scale[k] = a.scale[k] + (b.scale[k] - a.scale[k]) * f
        }
    }

    /** World (model-space) matrices of all parts for the given clip/time. */
    fun matrices(clip: SClip?, time: Float, out: Array<FloatArray>) {
        val pos = FloatArray(3); val rot = FloatArray(3); val sc = FloatArray(3)
        val local = FloatArray(16)
        val done = BooleanArray(parts.size)
        fun solve(i: Int, depth: Int) {
            if (done[i]) return
            pose(i, clip, time, pos, rot, sc)
            Mat4.trs(local, pos[0], pos[1], pos[2], rot[0], rot[1], rot[2], sc[0], sc[1], sc[2])
            val par = parts[i].parent
            if (par in parts.indices && par != i && depth < 32) {
                val lc = local.copyOf()
                solve(par, depth + 1)
                Mat4.mul(out[i], out[par], lc)
            } else local.copyInto(out[i])
            done[i] = true
        }
        for (i in parts.indices) solve(i, 0)
    }

    /**
     * Triangulated vertex data of a part grouped by colour: list of (colour, pos3/normal3/uv2 array).
     * Flat shading unless the part is marked smooth.
     */
    fun buildPart(i: Int): List<Pair<Int, FloatArray>> {
        val p = parts[i]
        p.fixColors()
        val groups = LinkedHashMap<Int, ArrayList<Float>>()
        val smoothN = if (p.smooth) vertexNormals(p) else null
        for ((fi, f) in p.faces.withIndex()) {
            if (f.size < 3 || f.any { it !in p.verts.indices }) continue
            val color = p.faceColors.getOrElse(fi) { 0 }.let { if (it == 0) p.color else it }
            val out = groups.getOrPut(color) { ArrayList() }
            val n = faceNormal(p, f)
            val ax = dominant(n)
            for (k in 1 until f.size - 1) for (vi in intArrayOf(f[0], f[k], f[k + 1])) {
                val v = p.verts[vi]
                val nn = smoothN?.get(vi) ?: n
                out.add(v[0]); out.add(v[1]); out.add(v[2]); out.add(nn[0]); out.add(nn[1]); out.add(nn[2])
                when (ax) { 0 -> { out.add(v[2]); out.add(-v[1]) }; 1 -> { out.add(v[0]); out.add(v[2]) }; else -> { out.add(v[0]); out.add(-v[1]) } }
            }
        }
        return groups.map { it.key to it.value.toFloatArray() }
    }

    fun bounds(): Pair<FloatArray, FloatArray> {
        val mats = Array(parts.size) { FloatArray(16) }
        matrices(null, 0f, mats)
        val mn = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE); val mx = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for ((i, p) in parts.withIndex()) for (v in p.verts) {
            val w = Mat4.point(mats[i], v[0], v[1], v[2])
            for (k in 0 until 3) { mn[k] = minOf(mn[k], w[k]); mx[k] = maxOf(mx[k], w[k]) }
        }
        if (mn[0] > mx[0]) { mn.fill(-0.5f); mx.fill(0.5f) }
        return mn to mx
    }

    /** Wavefront OBJ export of the rest pose (one object per part, with a .mtl-free colour comment). */
    fun toObj(): String {
        val sb = StringBuilder("# Exported from S Engine Model Editor\n")
        val mats = Array(parts.size) { FloatArray(16) }
        matrices(null, 0f, mats)
        var base = 1
        for ((i, p) in parts.withIndex()) {
            sb.append("o ").append(p.name.replace(' ', '_')).append('\n')
            sb.append(String.format("# color #%08X\n", p.color))
            for (v in p.verts) { val w = Mat4.point(mats[i], v[0], v[1], v[2]); sb.append(String.format(java.util.Locale.US, "v %.5f %.5f %.5f\n", w[0], w[1], w[2])) }
            for (f in p.faces) { sb.append('f'); f.forEach { sb.append(' ').append(it + base) }; sb.append('\n') }
            base += p.verts.size
        }
        return sb.toString()
    }

    companion object {
        fun fromJson(o: JSONObject): SModel {
            val m = SModel()
            val ps = o.optJSONArray("parts") ?: JSONArray()
            for (i in 0 until ps.length()) m.parts.add(SPart.fromJson(ps.getJSONObject(i)))
            val cs = o.optJSONArray("clips") ?: JSONArray()
            for (i in 0 until cs.length()) m.clips.add(SClip.fromJson(cs.getJSONObject(i)))
            return m
        }

        fun parse(text: String): SModel = fromJson(JSONObject(text))

        fun faceNormal(p: SPart, f: IntArray): FloatArray {
            // Newell's method (robust for n-gons)
            var nx = 0f; var ny = 0f; var nz = 0f
            for (k in f.indices) {
                val a = p.verts[f[k]]; val b = p.verts[f[(k + 1) % f.size]]
                nx += (a[1] - b[1]) * (a[2] + b[2]); ny += (a[2] - b[2]) * (a[0] + b[0]); nz += (a[0] - b[0]) * (a[1] + b[1])
            }
            val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-9f)
            return floatArrayOf(nx / l, ny / l, nz / l)
        }

        fun faceCenter(p: SPart, f: IntArray): FloatArray {
            val c = FloatArray(3); for (i in f) for (k in 0 until 3) c[k] += p.verts[i][k]
            for (k in 0 until 3) c[k] /= f.size.coerceAtLeast(1); return c
        }

        private fun dominant(n: FloatArray): Int {
            val ax = kotlin.math.abs(n[0]); val ay = kotlin.math.abs(n[1]); val az = kotlin.math.abs(n[2])
            return if (ax >= ay && ax >= az) 0 else if (ay >= az) 1 else 2
        }

        private fun vertexNormals(p: SPart): Array<FloatArray> {
            val acc = Array(p.verts.size) { FloatArray(3) }
            for (f in p.faces) { if (f.size < 3 || f.any { it !in p.verts.indices }) continue; val n = faceNormal(p, f); for (i in f) for (k in 0 until 3) acc[i][k] += n[k] }
            for (a in acc) { val l = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]).coerceAtLeast(1e-9f); for (k in 0 until 3) a[k] /= l }
            return acc
        }

        val PRIMITIVES = listOf("Cube", "Plane", "Cylinder", "Cone", "Sphere", "Torus", "Wedge", "Tube")

        /** Editable primitive part (quads where possible). */
        fun primitive(kind: Int, segments: Int = 16): SPart {
            val p = SPart(PRIMITIVES.getOrElse(kind) { "Part" })
            val v = p.verts; val f = p.faces
            when (kind) {
                1 -> { v.addAll(listOf(floatArrayOf(-0.5f, 0f, -0.5f), floatArrayOf(0.5f, 0f, -0.5f), floatArrayOf(0.5f, 0f, 0.5f), floatArrayOf(-0.5f, 0f, 0.5f))); f.add(intArrayOf(3, 2, 1, 0)) }
                2, 3, 7 -> {
                    val n = segments.coerceIn(3, 64)
                    val top = if (kind == 3) 0f else 0.5f
                    val inner = if (kind == 7) 0.3f else -1f
                    for (i in 0 until n) { val a = 2 * PI * i / n; v.add(floatArrayOf((cos(a) * 0.5).toFloat(), -0.5f, (sin(a) * 0.5).toFloat())) }
                    for (i in 0 until n) { val a = 2 * PI * i / n; v.add(floatArrayOf((cos(a) * top).toFloat(), 0.5f, (sin(a) * top).toFloat())) }
                    if (kind == 7) {
                        for (i in 0 until n) { val a = 2 * PI * i / n; v.add(floatArrayOf((cos(a) * inner).toFloat(), -0.5f, (sin(a) * inner).toFloat())) }
                        for (i in 0 until n) { val a = 2 * PI * i / n; v.add(floatArrayOf((cos(a) * inner).toFloat(), 0.5f, (sin(a) * inner).toFloat())) }
                    }
                    for (i in 0 until n) { val j = (i + 1) % n; f.add(intArrayOf(i, n + i, n + j, j)) }
                    if (kind == 7) {
                        for (i in 0 until n) { val j = (i + 1) % n; f.add(intArrayOf(2 * n + j, 3 * n + j, 3 * n + i, 2 * n + i)) }
                        for (i in 0 until n) { val j = (i + 1) % n; f.add(intArrayOf(n + i, 3 * n + i, 3 * n + j, n + j)) }
                        for (i in 0 until n) { val j = (i + 1) % n; f.add(intArrayOf(j, 2 * n + j, 2 * n + i, i)) }
                    } else {
                        f.add(IntArray(n) { it }) // bottom
                        if (kind == 2) f.add(IntArray(n) { 2 * n - 1 - it }) // top
                    }
                }
                4 -> {
                    val rings = (segments / 2).coerceIn(4, 32); val segs = segments.coerceIn(6, 64)
                    v.add(floatArrayOf(0f, -0.5f, 0f))
                    for (r in 1 until rings) {
                        val phi = PI * r / rings
                        for (s in 0 until segs) { val th = 2 * PI * s / segs; v.add(floatArrayOf((sin(phi) * cos(th) * 0.5).toFloat(), (-cos(phi) * 0.5).toFloat(), (sin(phi) * sin(th) * 0.5).toFloat())) }
                    }
                    v.add(floatArrayOf(0f, 0.5f, 0f))
                    val topI = v.size - 1
                    for (s in 0 until segs) f.add(intArrayOf(0, 1 + s, 1 + (s + 1) % segs))
                    for (r in 0 until rings - 2) for (s in 0 until segs) {
                        val a = 1 + r * segs + s; val b = 1 + r * segs + (s + 1) % segs
                        f.add(intArrayOf(a, a + segs, b + segs, b))
                    }
                    val last = 1 + (rings - 2) * segs
                    for (s in 0 until segs) f.add(intArrayOf(last + s, topI, last + (s + 1) % segs))
                    p.smooth = true
                }
                5 -> {
                    val segs = segments.coerceIn(6, 48); val sides = (segments / 2).coerceIn(4, 24)
                    for (i in 0 until segs) for (j in 0 until sides) {
                        val u = 2 * PI * i / segs; val w = 2 * PI * j / sides
                        val r = 0.35 + 0.15 * cos(w)
                        v.add(floatArrayOf((r * cos(u)).toFloat(), (0.15 * sin(w)).toFloat(), (r * sin(u)).toFloat()))
                    }
                    for (i in 0 until segs) for (j in 0 until sides) {
                        val a = i * sides + j; val b = ((i + 1) % segs) * sides + j
                        val c = ((i + 1) % segs) * sides + (j + 1) % sides; val d = i * sides + (j + 1) % sides
                        f.add(intArrayOf(a, d, c, b))
                    }
                    p.smooth = true
                }
                6 -> {
                    v.addAll(listOf(floatArrayOf(-0.5f, -0.5f, -0.5f), floatArrayOf(0.5f, -0.5f, -0.5f), floatArrayOf(0.5f, -0.5f, 0.5f), floatArrayOf(-0.5f, -0.5f, 0.5f),
                        floatArrayOf(-0.5f, 0.5f, -0.5f), floatArrayOf(0.5f, 0.5f, -0.5f)))
                    f.add(intArrayOf(0, 1, 2, 3)); f.add(intArrayOf(0, 4, 5, 1)); f.add(intArrayOf(3, 2, 5, 4)); f.add(intArrayOf(0, 3, 4)); f.add(intArrayOf(1, 5, 2))
                }
                else -> {
                    for (z in 0..1) for (y in 0..1) for (x in 0..1) v.add(floatArrayOf(x - 0.5f, y - 0.5f, z - 0.5f))
                    // indices: x + 2y + 4z
                    f.add(intArrayOf(4, 5, 7, 6)); f.add(intArrayOf(1, 0, 2, 3)); f.add(intArrayOf(5, 1, 3, 7))
                    f.add(intArrayOf(0, 4, 6, 2)); f.add(intArrayOf(2, 6, 7, 3)); f.add(intArrayOf(0, 1, 5, 4))
                }
            }
            p.fixColors()
            return p
        }
    }
}
