package com.sengine.engine.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v7 Blender-style sculpting + modifiers for S Parts (pure Kotlin, works everywhere):
 * pull / inflate / smooth / flatten / pinch brushes with radius falloff, an Array modifier
 * and six new primitives (icosphere, torus knot, spring, wedge, diamond, pipe, stairs).
 */
object ModelSculpt {

    val BRUSHES = listOf("Pull", "Inflate", "Smooth", "Flatten", "Pinch")

    private fun normals(p: SPart): Array<FloatArray> {
        val acc = Array(p.verts.size) { FloatArray(3) }
        for (f in p.faces) {
            if (f.size < 3 || f.any { it !in p.verts.indices }) continue
            val n = SModel.faceNormal(p, f)
            for (i in f) for (k in 0 until 3) acc[i][k] += n[k]
        }
        for (a in acc) {
            val l = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
            if (l > 1e-6f) for (k in 0 until 3) a[k] /= l
        }
        return acc
    }

    private fun center(p: SPart, verts: Set<Int>): FloatArray {
        val c = FloatArray(3); var n = 0
        for (i in verts) if (i in p.verts.indices) { for (k in 0 until 3) c[k] += p.verts[i][k]; n++ }
        if (n > 0) for (k in 0 until 3) c[k] /= n
        return c
    }

    /**
     * Applies a sculpt brush around [center] (world-ish part space) with smooth falloff.
     * Returns the number of vertices moved.
     */
    fun brush(p: SPart, center: FloatArray, radius: Float, mode: Int, strength: Float, vertices: Set<Int>? = null): Int {
        if (p.verts.isEmpty()) return 0
        val ns = normals(p)
        val c = center
        val inside = (vertices ?: p.verts.indices.toSet()).filter { i ->
            val v = p.verts[i]; val dx = v[0] - c[0]; val dy = v[1] - c[1]; val dz = v[2] - c[2]
            dx * dx + dy * dy + dz * dz <= radius * radius
        }.toSet()
        if (inside.isEmpty()) return 0
        val s = strength.coerceIn(-2f, 2f)
        var moved = 0
        when (BRUSHES.getOrNull(mode)) {
            "Pull" -> {
                val n = FloatArray(3) { k -> inside.sumOf { ns[it][k].toDouble() }.toFloat() / inside.size }
                val l = sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]).coerceAtLeast(1e-6f)
                for (i in inside) {
                    val v = p.verts[i]; val d = falloff(v, c, radius)
                    v[0] += n[0] / l * s * d; v[1] += n[1] / l * s * d; v[2] += n[2] / l * s * d; moved++
                }
            }
            "Inflate" -> for (i in inside) {
                val v = p.verts[i]; val d = falloff(v, c, radius) * s
                val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).coerceAtLeast(1e-6f)
                v[0] += v[0] / l * d; v[1] += v[1] / l * d; v[2] += v[2] / l * d; moved++
            }
            "Smooth" -> {
                val avg = HashMap<Int, FloatArray>()
                for (i in inside) avg[i] = neighborAvg(p, i)
                for (i in inside) {
                    val a = avg[i] ?: continue; val v = p.verts[i]; val d = falloff(v, c, radius) * s * 0.5f
                    v[0] += (a[0] - v[0]) * d; v[1] += (a[1] - v[1]) * d; v[2] += (a[2] - v[2]) * d; moved++
                }
            }
            "Flatten" -> {
                val n = FloatArray(3) { k -> inside.sumOf { ns[it][k].toDouble() }.toFloat() / inside.size }
                val plane = FloatArray(3) { k -> inside.sumOf { p.verts[it][k].toDouble() }.toFloat() / inside.size }
                for (i in inside) {
                    val v = p.verts[i]
                    val dist = (v[0] - plane[0]) * n[0] + (v[1] - plane[1]) * n[1] + (v[2] - plane[2]) * n[2]
                    val d = falloff(v, c, radius) * s
                    v[0] -= n[0] * dist * d; v[1] -= n[1] * dist * d; v[2] -= n[2] * dist * d; moved++
                }
            }
            "Pinch" -> for (i in inside) {
                val v = p.verts[i]; val d = falloff(v, c, radius) * s * 0.5f
                v[0] += (c[0] - v[0]) * d; v[1] += (c[1] - v[1]) * d; v[2] += (c[2] - v[2]) * d; moved++
            }
        }
        return moved
    }

    private fun falloff(v: FloatArray, c: FloatArray, r: Float): Float {
        val d = sqrt((v[0] - c[0]) * (v[0] - c[0]) + (v[1] - c[1]) * (v[1] - c[1]) + (v[2] - c[2]) * (v[2] - c[2]))
        val t = (1f - d / r).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t) // smoothstep
    }

    private fun neighborAvg(p: SPart, vi: Int): FloatArray {
        val c = FloatArray(3); var n = 0
        for (f in p.faces) if (vi in f) for (j in f) if (j != vi && j in p.verts.indices) { for (k in 0 until 3) c[k] += p.verts[j][k]; n++ }
        if (n == 0) return p.verts[vi]
        for (k in 0 until 3) c[k] /= n
        return c
    }

    /** Array modifier: duplicates all faces [count] times with a growing offset, welding seams. Returns the new vertex count. */
    fun array(p: SPart, count: Int, dx: Float, dy: Float, dz: Float): Int {
        if (count < 2) return p.verts.size
        val base = ArrayList(p.verts.map { it.copyOf() })
        val baseFaces = ArrayList(p.faces.map { it.copyOf() })
        val baseColors = ArrayList(p.faceColors); val baseGroups = ArrayList(p.groups); val baseUvs = ArrayList(p.uvs.map { it?.copyOf() })
        for (c in 1 until count) {
            val map = HashMap<Int, Int>()
            for ((i, v) in base.withIndex()) {
                p.verts.add(floatArrayOf(v[0] + dx * c, v[1] + dy * c, v[2] + dz * c))
                map[i] = p.verts.size - 1
            }
            for ((fi, f) in baseFaces.withIndex()) {
                p.faces.add(IntArray(f.size) { map[f[it]] ?: f[it] })
                p.faceColors.add(baseColors.getOrElse(fi) { 0 }); p.groups.add(baseGroups.getOrElse(fi) { 0 }); p.uvs.add(baseUvs.getOrNull(fi)?.copyOf())
            }
        }
        p.fixColors()
        return p.verts.size
    }

    /** Mirrors the part's geometry across an axis (0=X,1=Y,2=Z), flipping winding. */
    fun mirrorPart(p: SPart, axis: Int) {
        val n0 = p.verts.size
        val map = HashMap<Int, Int>()
        for (i in 0 until n0) {
            val v = p.verts[i]; val m = v.copyOf(); m[axis] = -m[axis]; p.verts.add(m); map[i] = p.verts.size - 1
        }
        val faces = ArrayList(p.faces.map { it.copyOf() })
        for (f in faces) p.faces.add(IntArray(f.size) { map[f[f.size - 1 - it]] ?: 0 })
        for (fi in faces.indices) {
            val c = p.faceColors.getOrElse(fi) { 0 }; val g = p.groups.getOrElse(fi) { 0 }; val u = p.uvs.getOrNull(fi)
            p.faceColors.add(c); p.groups.add(g)
            p.uvs.add(u?.let { FloatArray(it.size) { k -> if (k % 2 == 0) 1f - it[k] else it[k] } })
        }
        p.fixColors()
    }

    // =================================================================== new primitives
    /** Icosphere (subdivided icosahedron). */
    fun icosphere(subdiv: Int = 1, radius: Float = 1f): SPart {
        val t = (1f + sqrt(5f)) / 2f
        val raw = listOf(
            floatArrayOf(-1f, t, 0f), floatArrayOf(1f, t, 0f), floatArrayOf(-1f, -t, 0f), floatArrayOf(1f, -t, 0f),
            floatArrayOf(0f, -1f, t), floatArrayOf(0f, 1f, t), floatArrayOf(0f, -1f, -t), floatArrayOf(0f, 1f, -t),
            floatArrayOf(t, 0f, -1f), floatArrayOf(t, 0f, 1f), floatArrayOf(-t, 0f, -1f), floatArrayOf(-t, 0f, 1f))
        var verts = raw.map { it.let { v -> val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); floatArrayOf(v[0] / l, v[1] / l, v[2] / l) } }
        var faces = listOf(
            intArrayOf(0, 11, 5), intArrayOf(0, 5, 1), intArrayOf(0, 1, 7), intArrayOf(0, 7, 10), intArrayOf(0, 10, 11),
            intArrayOf(1, 5, 9), intArrayOf(5, 11, 4), intArrayOf(11, 10, 2), intArrayOf(10, 7, 6), intArrayOf(7, 1, 8),
            intArrayOf(3, 9, 4), intArrayOf(3, 4, 2), intArrayOf(3, 2, 6), intArrayOf(3, 6, 8), intArrayOf(3, 8, 9),
            intArrayOf(4, 9, 5), intArrayOf(2, 4, 11), intArrayOf(6, 2, 10), intArrayOf(8, 6, 7), intArrayOf(9, 8, 1))
        repeat(subdiv.coerceIn(0, 3)) {
            val mid = HashMap<Long, Int>()
            fun midpoint(a: Int, b: Int): Int {
                val key = if (a < b) (a.toLong() shl 32) or b.toLong() else (b.toLong() shl 32) or a.toLong()
                mid[key]?.let { return it }
                val va = verts[a]; val vb = verts[b]
                val m = floatArrayOf((va[0] + vb[0]) / 2, (va[1] + vb[1]) / 2, (va[2] + vb[2]) / 2)
                val l = sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2])
                verts = verts + floatArrayOf(m[0] / l, m[1] / l, m[2] / l)
                mid[key] = verts.size - 1
                return verts.size - 1
            }
            val nf = ArrayList<IntArray>()
            for (f in faces) {
                val a = midpoint(f[0], f[1]); val b = midpoint(f[1], f[2]); val c = midpoint(f[2], f[0])
                nf.add(intArrayOf(f[0], a, c)); nf.add(intArrayOf(f[1], b, a)); nf.add(intArrayOf(f[2], c, b)); nf.add(intArrayOf(a, b, c))
            }
            faces = nf
        }
        val p = SPart("Icosphere")
        for (v in verts) p.verts.add(floatArrayOf(v[0] * radius, v[1] * radius, v[2] * radius))
        for (f in faces) { p.faces.add(f); p.faceColors.add(0); p.groups.add(0); p.uvs.add(null) }
        p.smooth = true
        return p
    }

    /** (p,q) torus knot — great for rings, ropes and decorations. */
    fun torusKnot(pq: Int = 0, radius: Float = 1f, tube: Float = 0.3f, segs: Int = 64, sides: Int = 10): SPart {
        val p2 = if (pq == 0) 2 else 3; val q2 = if (pq == 0) 3 else 2
        val p = SPart(if (pq == 0) "Knot" else "Knot 3-2")
        fun knot(t: Float): FloatArray {
            val u = t * 2f * PI.toFloat() * (p2.toFloat() / q2)
            val r = radius * (2f + cos(q2.toFloat() * u)) / 3f
            return floatArrayOf(r * cos(p2 * u), r * sin(p2 * u), radius * sin(q2.toFloat() * u) / 1.5f)
        }
        fun norm(t: Float): FloatArray {
            val e = 0.001f
            val a = knot(t - e); val b = knot(t + e)
            val d = floatArrayOf(b[0] - a[0], b[1] - a[1], b[2] - a[2])
            val l = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]).coerceAtLeast(1e-6f)
            return floatArrayOf(d[0] / l, d[1] / l, d[2] / l)
        }
        val ring = ArrayList<IntArray>()
        for (i in 0 until segs) {
            val t = i.toFloat() / segs
            val c = knot(t); val d = norm(t)
            val up = floatArrayOf(0f, 1f, 0f)
            var sx = d[1] * up[2] - d[2] * up[1]; var sy = d[2] * up[0] - d[0] * up[2]; var sz = d[0] * up[1] - d[1] * up[0]
            val sl = sqrt(sx * sx + sy * sy + sz * sz).coerceAtLeast(1e-6f)
            sx /= sl; sy /= sl; sz /= sl
            val ux = d[1] * sz - d[2] * sy; val uy = d[2] * sx - d[0] * sz; val uz = d[0] * sy - d[1] * sx
            val ringV = IntArray(sides)
            for (j in 0 until sides) {
                val a = 2f * PI.toFloat() * j / sides
                val nx = sx * cos(a) + ux * sin(a); val ny = sy * cos(a) + uy * sin(a); val nz = sz * cos(a) + uz * sin(a)
                p.verts.add(floatArrayOf(c[0] + nx * tube, c[1] + ny * tube, c[2] + nz * tube))
                ringV[j] = p.verts.size - 1
            }
            ring.add(ringV)
        }
        for (i in 0 until segs) {
            val a = ring[i]; val b2 = ring[(i + 1) % segs]
            for (j in 0 until sides) {
                val j2 = (j + 1) % sides
                p.faces.add(intArrayOf(a[j], b2[j], b2[j2], a[j2])); p.faceColors.add(0); p.groups.add(0); p.uvs.add(null)
            }
        }
        p.smooth = true
        return p
    }

    /** Spring / coil (helix tube). */
    fun spring(coils: Int = 3, radius: Float = 0.7f, height: Float = 2f, tube: Float = 0.15f, segs: Int = 72, sides: Int = 8): SPart {
        val p = SPart("Spring")
        fun center(t: Float) = floatArrayOf(radius * cos(2f * PI.toFloat() * coils * t), height * t - height / 2, radius * sin(2f * PI.toFloat() * coils * t))
        val ring = ArrayList<IntArray>()
        for (i in 0..segs) {
            val t = i.toFloat() / segs
            val c = center(t)
            val e = 0.001f
            val a = center((t - e).coerceAtLeast(0f)); val b2 = center((t + e).coerceAtMost(1f))
            val d = floatArrayOf(b2[0] - a[0], b2[1] - a[1], b2[2] - a[2])
            val dl = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]).coerceAtLeast(1e-6f)
            val up = floatArrayOf(0f, 1f, 0f)
            var sx = d[1] * up[2] - d[2] * up[1]; var sy = d[2] * up[0] - d[0] * up[2]; var sz = d[0] * up[1] - d[1] * up[0]
            val sl = sqrt(sx * sx + sy * sy + sz * sz).coerceAtLeast(1e-6f)
            sx /= sl; sy /= sl; sz /= sl
            val ux = d[1] * sz - d[2] * sy; val uy = d[2] * sx - d[0] * sz; val uz = d[0] * sy - d[1] * sx
            val ringV = IntArray(sides)
            for (j in 0 until sides) {
                val a2 = 2f * PI.toFloat() * j / sides
                val nx = sx * cos(a2) + ux * sin(a2); val ny = sy * cos(a2) + uy * sin(a2); val nz = sz * cos(a2) + uz * sin(a2)
                p.verts.add(floatArrayOf(c[0] + nx * tube, c[1] + ny * tube, c[2] + nz * tube))
                ringV[j] = p.verts.size - 1
            }
            ring.add(ringV)
        }
        for (i in 0 until ring.size - 1) {
            val a = ring[i]; val b2 = ring[i + 1]
            for (j in 0 until sides) {
                val j2 = (j + 1) % sides
                p.faces.add(intArrayOf(a[j], b2[j], b2[j2], a[j2])); p.faceColors.add(0); p.groups.add(0); p.uvs.add(null)
            }
        }
        p.smooth = true
        return p
    }

    /** Wedge / ramp. */
    fun wedge(): SPart {
        val p = SPart("Wedge")
        for (v in arrayOf(floatArrayOf(-0.5f, -0.5f, -0.5f), floatArrayOf(0.5f, -0.5f, -0.5f), floatArrayOf(0.5f, -0.5f, 0.5f),
            floatArrayOf(-0.5f, -0.5f, 0.5f), floatArrayOf(-0.5f, 0.5f, -0.5f), floatArrayOf(0.5f, 0.5f, -0.5f))) p.verts.add(v)
        val fs = arrayOf(intArrayOf(0, 3, 2, 1), intArrayOf(0, 1, 5, 4), intArrayOf(1, 2, 5), intArrayOf(2, 3, 4, 5), intArrayOf(3, 0, 4))
        for (f in fs) { p.faces.add(f); p.faceColors.add(0); p.groups.add(0); p.uvs.add(null) }
        return p
    }

    /** Cut diamond / gem. */
    fun diamond(): SPart {
        val p = SPart("Diamond")
        for (i in 0 until 8) {
            val a = PI.toFloat() * i / 4 + PI.toFloat() / 8
            p.verts.add(floatArrayOf(cos(a) * 0.6f, if (i % 2 == 0) 0.25f else 0.05f, sin(a) * 0.6f))
        }
        p.verts.add(floatArrayOf(0f, 0.7f, 0f)); p.verts.add(floatArrayOf(0f, -0.9f, 0f))
        for (i in 0 until 8) {
            val j = (i + 1) % 8
            p.faces.add(intArrayOf(i, j, 9)); p.faceColors.add(0); p.groups.add(0); p.uvs.add(null)
            p.faces.add(intArrayOf(j, i, 8)); p.faceColors.add(0); p.groups.add(0); p.uvs.add(null)
        }
        return p
    }

    /** Hollow pipe / tube along Y. */
    fun pipe(inner: Float = 0.55f, segs: Int = 24): SPart {
        val p = SPart("Pipe")
        fun ring(r: Float, y: Float): IntArray {
            val out = IntArray(segs)
            for (i in 0 until segs) {
                val a = 2f * PI.toFloat() * i / segs
                p.verts.add(floatArrayOf(r * cos(a), y, r * sin(a)))
                out[i] = p.verts.size - 1
            }
            return out
        }
        val o0 = ring(1f, -0.5f); val o1 = ring(1f, 0.5f); val i0 = ring(inner, -0.5f); val i1 = ring(inner, 0.5f)
        for (i in 0 until segs) {
            val j = (i + 1) % segs
            p.faces.add(intArrayOf(o0[i], o0[j], o1[j], o1[i])); p.faceColors.add(0); p.groups.add(0); p.uvs.add(null)
            p.faces.add(intArrayOf(i1[j], i1[i], i0[i], i0[j])); p.faceColors.add(0); p.groups.add(0); p.uvs.add(null)
            p.faces.add(intArrayOf(o1[j], o1[i], i1[i], i1[j])); p.faceColors.add(0); p.groups.add(0); p.uvs.add(null)
            p.faces.add(intArrayOf(i0[j], i0[i], o0[i], o0[j])); p.faceColors.add(0); p.groups.add(0); p.uvs.add(null)
        }
        return p
    }

    /** Staircase with [steps] steps. */
    fun stairs(steps: Int = 6): SPart {
        val n = steps.coerceIn(2, 24)
        val p = SPart("Stairs")
        for (i in 0 until n) {
            val x0 = -0.5f + i.toFloat() / n; val x1 = -0.5f + (i + 1).toFloat() / n
            val y0 = i.toFloat() / n - 0.5f; val y1 = (i + 1).toFloat() / n - 0.5f
            val b = p.verts.size
            p.verts.add(floatArrayOf(x0, -0.5f, -0.5f)); p.verts.add(floatArrayOf(x1, -0.5f, -0.5f))
            p.verts.add(floatArrayOf(x1, y1, -0.5f)); p.verts.add(floatArrayOf(x0, y1, -0.5f))
            p.verts.add(floatArrayOf(x0, -0.5f, 0.5f)); p.verts.add(floatArrayOf(x1, -0.5f, 0.5f))
            p.verts.add(floatArrayOf(x1, y1, 0.5f)); p.verts.add(floatArrayOf(x0, y1, 0.5f))
            val fs = arrayOf(intArrayOf(b, b + 1, b + 2, b + 3), intArrayOf(b + 4, b + 7, b + 6, b + 5), intArrayOf(b, b + 4, b + 5, b + 1),
                intArrayOf(b + 1, b + 5, b + 6, b + 2), intArrayOf(b + 2, b + 6, b + 7, b + 3), intArrayOf(b + 3, b + 7, b + 4, b))
            for (f in fs) { p.faces.add(f); p.faceColors.add(0); p.groups.add(0); p.uvs.add(null) }
        }
        return p
    }

    /** Names for the Model Editor "Add" menu. */
    val PRIMITIVES = listOf("Icosphere", "Torus Knot", "Spring", "Wedge", "Diamond", "Pipe", "Stairs")
    fun primitiveByName(name: String): SPart? = when (name) {
        "Icosphere" -> icosphere(1)
        "Torus Knot" -> torusKnot(0)
        "Spring" -> spring()
        "Wedge" -> wedge()
        "Diamond" -> diamond()
        "Pipe" -> pipe()
        "Stairs" -> stairs()
        else -> null
    }
}
