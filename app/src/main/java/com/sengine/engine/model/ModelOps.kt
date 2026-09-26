package com.sengine.engine.model

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mesh editing operations for the Model Editor (pure Kotlin, unit tested):
 * extrude, inset, Catmull-Clark subdivision, mirror, smooth, merge, flip, delete, transforms.
 * All operations work on part-local vertex coordinates and keep faces CCW = front.
 */
object ModelOps {

    private fun edgeKey(a: Int, b: Int): Long = if (a < b) (a.toLong() shl 32) or b.toLong() else (b.toLong() shl 32) or a.toLong()

    /** Region extrude: selected faces move along their averaged normals; side walls are created on the region boundary. */
    fun extrude(p: SPart, faces: Set<Int>, distance: Float): Set<Int> {
        if (faces.isEmpty()) return faces
        p.fixColors()
        val sel = faces.filter { it in p.faces.indices }
        // averaged normal per vertex over the selected faces
        val vn = HashMap<Int, FloatArray>()
        for (fi in sel) {
            val n = SModel.faceNormal(p, p.faces[fi])
            for (v in p.faces[fi]) { val a = vn.getOrPut(v) { FloatArray(3) }; for (k in 0 until 3) a[k] += n[k] }
        }
        val remap = HashMap<Int, Int>()
        for ((v, n) in vn) {
            val l = sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]).coerceAtLeast(1e-6f)
            val o = p.verts[v]
            remap[v] = p.verts.size
            p.verts.add(floatArrayOf(o[0] + n[0] / l * distance, o[1] + n[1] / l * distance, o[2] + n[2] / l * distance))
        }
        // boundary edges: directed edges of selected faces whose undirected edge is used once in the selection
        val count = HashMap<Long, Int>()
        for (fi in sel) { val f = p.faces[fi]; for (k in f.indices) { val key = edgeKey(f[k], f[(k + 1) % f.size]); count[key] = (count[key] ?: 0) + 1 } }
        val newFaces = ArrayList<IntArray>(); val newColors = ArrayList<Int>()
        for (fi in sel) {
            val f = p.faces[fi]
            for (k in f.indices) {
                val a = f[k]; val b = f[(k + 1) % f.size]
                if (count[edgeKey(a, b)] == 1) { newFaces.add(intArrayOf(a, b, remap[b]!!, remap[a]!!)); newColors.add(p.faceColors[fi]) }
            }
            p.faces[fi] = IntArray(f.size) { remap[f[it]]!! }
        }
        p.faces.addAll(newFaces); p.faceColors.addAll(newColors)
        return sel.toSet()
    }

    /** Insets each selected face (amount 0..1 towards its centre) creating a ring of quads. */
    fun inset(p: SPart, faces: Set<Int>, amount: Float): Set<Int> {
        p.fixColors()
        val t = amount.coerceIn(0.01f, 0.95f)
        for (fi in faces.filter { it in p.faces.indices }) {
            val f = p.faces[fi]
            val c = SModel.faceCenter(p, f)
            val inner = IntArray(f.size) {
                val o = p.verts[f[it]]
                p.verts.add(floatArrayOf(o[0] + (c[0] - o[0]) * t, o[1] + (c[1] - o[1]) * t, o[2] + (c[2] - o[2]) * t))
                p.verts.size - 1
            }
            for (k in f.indices) {
                val k2 = (k + 1) % f.size
                p.faces.add(intArrayOf(f[k], f[k2], inner[k2], inner[k])); p.faceColors.add(p.faceColors[fi])
            }
            p.faces[fi] = inner
        }
        return faces
    }

    /**
     * Subdivides every face into quads. With [smooth] this is Catmull-Clark subdivision
     * (boundary edges and vertices use the standard boundary rules), otherwise a flat split.
     */
    fun subdivide(p: SPart, smooth: Boolean) {
        p.fixColors()
        val oldV = p.verts.map { it.copyOf() }
        val faces = p.faces.map { it.copyOf() }
        val colors = p.faceColors.toList()
        val nv = oldV.size
        val facePts = faces.map { f -> FloatArray(3).also { c -> for (i in f) for (k in 0 until 3) c[k] += oldV[i][k]; val inv = 1f / f.size.coerceAtLeast(1); for (k in 0 until 3) c[k] = c[k] * inv } }
        // edge -> adjacent faces
        val edgeFaces = LinkedHashMap<Long, MutableList<Int>>()
        faces.forEachIndexed { fi, f -> for (k in f.indices) edgeFaces.getOrPut(edgeKey(f[k], f[(k + 1) % f.size])) { ArrayList(2) }.add(fi) }
        val edgeIndex = HashMap<Long, Int>()
        val out = ArrayList<FloatArray>(nv + edgeFaces.size + faces.size)
        // vertex points
        if (smooth) {
            val vFaces = Array(nv) { ArrayList<Int>() }
            val vEdges = Array(nv) { ArrayList<Long>() }
            faces.forEachIndexed { fi, f -> for (v in f) vFaces[v].add(fi) }
            for (e in edgeFaces.keys) { vEdges[(e ushr 32).toInt()].add(e); vEdges[(e and 0xFFFFFFFFL).toInt()].add(e) }
            for (v in 0 until nv) {
                val P = oldV[v]
                val boundary = vEdges[v].filter { edgeFaces[it]!!.size == 1 }
                if (boundary.isNotEmpty()) {
                    if (boundary.size == 2) {
                        val q = FloatArray(3)
                        for (e in boundary) { val o = other(e, v); for (k in 0 until 3) q[k] += oldV[o][k] }
                        out.add(FloatArray(3) { (P[it] * 6f + q[it]) / 8f })
                    } else out.add(P.copyOf())
                    continue
                }
                val n = vFaces[v].size
                if (n < 3) { out.add(P.copyOf()); continue }
                val F = FloatArray(3); for (fi in vFaces[v]) for (k in 0 until 3) F[k] += facePts[fi][k] / n
                val R = FloatArray(3); val m = vEdges[v].size
                for (e in vEdges[v]) { val o = other(e, v); for (k in 0 until 3) R[k] += (P[k] + oldV[o][k]) / 2f / m }
                out.add(FloatArray(3) { (F[it] + 2f * R[it] + (n - 3f) * P[it]) / n })
            }
        } else oldV.forEach { out.add(it.copyOf()) }
        // edge points
        for ((e, fs) in edgeFaces) {
            val a = (e ushr 32).toInt(); val b = (e and 0xFFFFFFFFL).toInt()
            val pnt = if (smooth && fs.size == 2) FloatArray(3) { (oldV[a][it] + oldV[b][it] + facePts[fs[0]][it] + facePts[fs[1]][it]) / 4f }
            else FloatArray(3) { (oldV[a][it] + oldV[b][it]) / 2f }
            edgeIndex[e] = out.size; out.add(pnt)
        }
        // face points + new quads
        val newFaces = ArrayList<IntArray>(); val newColors = ArrayList<Int>()
        faces.forEachIndexed { fi, f ->
            val c = out.size; out.add(facePts[fi])
            for (k in f.indices) {
                val prev = f[(k - 1 + f.size) % f.size]; val cur = f[k]; val next = f[(k + 1) % f.size]
                newFaces.add(intArrayOf(cur, edgeIndex[edgeKey(cur, next)]!!, c, edgeIndex[edgeKey(prev, cur)]!!))
                newColors.add(colors.getOrElse(fi) { 0 })
            }
        }
        p.verts.clear(); p.verts.addAll(out)
        p.faces.clear(); p.faces.addAll(newFaces)
        p.faceColors.clear(); p.faceColors.addAll(newColors)
        if (smooth) p.smooth = true
    }

    private fun other(e: Long, v: Int): Int { val a = (e ushr 32).toInt(); val b = (e and 0xFFFFFFFFL).toInt(); return if (a == v) b else a }

    /** Mirrors the part geometry across a local axis plane (0=X,1=Y,2=Z), welding vertices on the plane. */
    fun mirror(p: SPart, axis: Int, eps: Float = 1e-3f) {
        p.fixColors()
        val n = p.verts.size
        val map = IntArray(n)
        for (i in 0 until n) {
            val v = p.verts[i]
            if (kotlin.math.abs(v[axis]) < eps) { v[axis] = 0f; map[i] = i }
            else { map[i] = p.verts.size; p.verts.add(v.copyOf().also { it[axis] = -it[axis] }) }
        }
        val fc = p.faces.size
        for (fi in 0 until fc) {
            val f = p.faces[fi]
            p.faces.add(IntArray(f.size) { map[f[f.size - 1 - it]] })
            p.faceColors.add(p.faceColors[fi])
        }
    }

    /** Laplacian smoothing of the given vertices (all when empty). */
    fun smooth(p: SPart, verts: Set<Int>, strength: Float = 0.5f, iterations: Int = 1) {
        val nb = Array(p.verts.size) { HashSet<Int>() }
        for (f in p.faces) for (k in f.indices) { val a = f[k]; val b = f[(k + 1) % f.size]; if (a in nb.indices && b in nb.indices) { nb[a].add(b); nb[b].add(a) } }
        val target = if (verts.isEmpty()) p.verts.indices.toSet() else verts
        repeat(iterations) {
            val snapshot = p.verts.map { it.copyOf() }
            for (v in target) {
                if (v !in nb.indices || nb[v].isEmpty()) continue
                val avg = FloatArray(3); for (o in nb[v]) for (k in 0 until 3) avg[k] += snapshot[o][k] / nb[v].size
                for (k in 0 until 3) p.verts[v][k] = snapshot[v][k] + (avg[k] - snapshot[v][k]) * strength
            }
        }
    }

    fun flip(p: SPart, faces: Set<Int>) { for (fi in faces) if (fi in p.faces.indices) p.faces[fi] = p.faces[fi].reversedArray() }

    /** Deletes faces and removes vertices that are no longer used. */
    fun deleteFaces(p: SPart, faces: Set<Int>) {
        p.fixColors()
        val keep = p.faces.indices.filter { it !in faces }
        val nf = keep.map { p.faces[it] }; val nc = keep.map { p.faceColors[it] }
        p.faces.clear(); p.faces.addAll(nf); p.faceColors.clear(); p.faceColors.addAll(nc)
        compact(p)
    }

    /** Deletes vertices and every face that uses them. */
    fun deleteVerts(p: SPart, verts: Set<Int>) {
        deleteFaces(p, p.faces.indices.filter { fi -> p.faces[fi].any { it in verts } }.toSet())
    }

    /** Removes unused vertices and re-indexes faces. */
    fun compact(p: SPart) {
        val used = BooleanArray(p.verts.size)
        for (f in p.faces) for (v in f) if (v in used.indices) used[v] = true
        val map = IntArray(p.verts.size) { -1 }
        val nv = ArrayList<FloatArray>()
        for (i in p.verts.indices) if (used[i]) { map[i] = nv.size; nv.add(p.verts[i]) }
        p.verts.clear(); p.verts.addAll(nv)
        for (fi in p.faces.indices) p.faces[fi] = p.faces[fi].map { map[it] }.toIntArray()
    }

    /** Merges vertices closer than [dist]; degenerate faces are removed. Returns the number of merged vertices. */
    fun mergeByDistance(p: SPart, dist: Float = 1e-3f): Int {
        val n = p.verts.size
        val map = IntArray(n) { it }
        var merged = 0
        for (i in 0 until n) {
            if (map[i] != i) continue
            for (j in i + 1 until n) {
                if (map[j] != j) continue
                val a = p.verts[i]; val b = p.verts[j]
                val dx = a[0] - b[0]; val dy = a[1] - b[1]; val dz = a[2] - b[2]
                if (dx * dx + dy * dy + dz * dz <= dist * dist) { map[j] = i; merged++ }
            }
        }
        if (merged == 0) return 0
        p.fixColors()
        val nf = ArrayList<IntArray>(); val nc = ArrayList<Int>()
        p.faces.forEachIndexed { fi, f ->
            val g = ArrayList<Int>()
            for (v in f) { val m = map[v]; if (g.isEmpty() || g.last() != m) g.add(m) }
            if (g.size > 1 && g.first() == g.last()) g.removeAt(g.size - 1)
            if (g.toSet().size >= 3) { nf.add(g.toIntArray()); nc.add(p.faceColors[fi]) }
        }
        p.faces.clear(); p.faces.addAll(nf); p.faceColors.clear(); p.faceColors.addAll(nc)
        compact(p)
        return merged
    }

    /** Vertices used by the given faces. */
    fun vertsOf(p: SPart, faces: Set<Int>): Set<Int> = faces.filter { it in p.faces.indices }.flatMap { p.faces[it].toList() }.toSet()

    fun centroid(p: SPart, verts: Set<Int>): FloatArray {
        val c = FloatArray(3); if (verts.isEmpty()) return c
        for (v in verts) for (k in 0 until 3) c[k] += p.verts[v][k]
        val inv = 1f / verts.size; for (k in 0 until 3) c[k] = c[k] * inv
        return c
    }

    fun translate(p: SPart, verts: Set<Int>, dx: Float, dy: Float, dz: Float) {
        for (v in verts) if (v in p.verts.indices) { p.verts[v][0] += dx; p.verts[v][1] += dy; p.verts[v][2] += dz }
    }

    fun scale(p: SPart, verts: Set<Int>, sx: Float, sy: Float, sz: Float, pivot: FloatArray = centroid(p, verts)) {
        for (v in verts) if (v in p.verts.indices) {
            val a = p.verts[v]
            a[0] = pivot[0] + (a[0] - pivot[0]) * sx; a[1] = pivot[1] + (a[1] - pivot[1]) * sy; a[2] = pivot[2] + (a[2] - pivot[2]) * sz
        }
    }

    /** Rotates vertices by [deg] degrees around a local axis through the pivot. */
    fun rotate(p: SPart, verts: Set<Int>, axis: Int, deg: Float, pivot: FloatArray = centroid(p, verts)) {
        val r = Math.toRadians(deg.toDouble()); val c = cos(r).toFloat(); val s = sin(r).toFloat()
        val (i, j) = when (axis) { 0 -> 1 to 2; 1 -> 2 to 0; else -> 0 to 1 }
        for (v in verts) if (v in p.verts.indices) {
            val a = p.verts[v]
            val x = a[i] - pivot[i]; val y = a[j] - pivot[j]
            a[i] = pivot[i] + x * c - y * s; a[j] = pivot[j] + x * s + y * c
        }
    }

    /** Applies the part's scale into its vertices (keeps the look, resets scale to 1). */
    fun applyScale(p: SPart) {
        for (v in p.verts) { v[0] *= p.scale[0]; v[1] *= p.scale[1]; v[2] *= p.scale[2] }
        p.scale[0] = 1f; p.scale[1] = 1f; p.scale[2] = 1f
    }

    /** Parses an OBJ file into a single editable part (quads/ngons preserved). */
    fun fromObj(text: String, name: String = "Imported"): SPart {
        val p = SPart(name)
        for (line in text.lineSequence()) {
            val t = line.trim()
            if (t.startsWith("v ")) {
                val a = t.substring(2).trim().split(Regex("\\s+")).mapNotNull { it.toFloatOrNull() }
                if (a.size >= 3) p.verts.add(floatArrayOf(a[0], a[1], a[2]))
            } else if (t.startsWith("f ")) {
                val idx = t.substring(2).trim().split(Regex("\\s+")).mapNotNull { it.substringBefore('/').toIntOrNull() }
                    .map { if (it < 0) p.verts.size + it else it - 1 }
                if (idx.size >= 3 && idx.all { it in p.verts.indices }) p.faces.add(idx.toIntArray())
            }
        }
        p.fixColors()
        return p
    }

    /** Counts triangles after fan triangulation (for stats). */
    fun triCount(m: SModel): Int = m.parts.sumOf { p -> p.faces.sumOf { (it.size - 2).coerceAtLeast(0) } }
}
