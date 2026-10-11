package com.sengine.engine.physics

import com.sengine.engine.math.Aabb3
import com.sengine.engine.math.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Hit produced by [Bvh3D.raycast]. */
class BvhRayHit {
    var hit = false
    var triangle = -1
    var t = 0f
    var u = 0f
    var v = 0f
    val point = Vec3()
    val normal = Vec3(0f, 1f, 0f)

    fun reset() {
        hit = false; triangle = -1; t = 0f; u = 0f; v = 0f
        point.zero(); normal.set(0f, 1f, 0f)
    }
}

/**
 * Bounding volume hierarchy over a triangle soup, used by [TriangleMeshShape3D] and
 * [HeightfieldShape3D] for raycasts, overlap queries and triangle-level narrowphase.
 *
 * Nodes are stored in a flat array; leaves hold a contiguous range of the `order` permutation so
 * traversal is cache friendly. Splits use the surface-area heuristic when [build] is called with
 * `useSah = true` and fall back to a median split otherwise. [refit] rebuilds the internal bounds
 * after vertices move; [rebuild] re-runs the full build.
 */
class Bvh3D {

    private class Node {
        val min = Vec3()
        val max = Vec3()
        var left = -1
        var right = -1
        var start = 0
        var count = 0
        val isLeaf: Boolean get() = left < 0
    }

    private val nodes = ArrayList<Node>()
    private var order = IntArray(0)
    private var primMin = emptyArray<Vec3>()
    private var primMax = emptyArray<Vec3>()
    private var primCentroid = emptyArray<Vec3>()

    var vertices: Array<Vec3> = emptyArray()
        private set
    /** Triangle indices, three per triangle. */
    var indices: IntArray = IntArray(0)
        private set
    var triangleCount = 0
        private set
    var root = -1
        private set
    /** Leaf threshold; small leaves give faster traversal, large leaves a shallower tree. */
    var leafSize = 4
    /** Number of nodes in the tree (root + internals + leaves). */
    val nodeCount: Int get() = nodes.size

    /** Builds the hierarchy over [verts] and [tris]. Deterministic for identical input. */
    fun build(verts: Array<Vec3>, tris: IntArray, useSah: Boolean = true) {
        vertices = verts
        indices = tris
        triangleCount = tris.size / 3
        nodes.clear()
        order = IntArray(triangleCount) { it }
        primMin = Array(triangleCount) { Vec3() }
        primMax = Array(triangleCount) { Vec3() }
        primCentroid = Array(triangleCount) { Vec3() }
        for (t in 0 until triangleCount) computePrimBounds(t)
        root = if (triangleCount == 0) -1 else buildNode(0, triangleCount, useSah)
    }

    /** Re-runs the full build with the current [vertices] (topology unchanged). */
    fun rebuild(useSah: Boolean = true) = build(vertices, indices, useSah)

    private fun computePrimBounds(t: Int) {
        val a = vertices[indices[t * 3]]
        val b = vertices[indices[t * 3 + 1]]
        val c = vertices[indices[t * 3 + 2]]
        val mn = primMin[t]; val mx = primMax[t]
        mn.set(min(a.x, min(b.x, c.x)), min(a.y, min(b.y, c.y)), min(a.z, min(b.z, c.z)))
        mx.set(max(a.x, max(b.x, c.x)), max(a.y, max(b.y, c.y)), max(a.z, max(b.z, c.z)))
        primCentroid[t].set((mn.x + mx.x) * 0.5f, (mn.y + mx.y) * 0.5f, (mn.z + mx.z) * 0.5f)
    }

    /** Recomputes node bounds bottom-up (cheap when geometry moved a little). */
    fun refit() {
        if (root < 0) return
        for (t in 0 until triangleCount) computePrimBounds(t)
        refitNode(root)
    }

    private fun refitNode(n: Int) {
        val node = nodes[n]
        if (node.isLeaf) {
            node.min.set(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
            node.max.set(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            for (i in node.start until node.start + node.count) {
                val t = order[i]
                node.min.x = min(node.min.x, primMin[t].x); node.min.y = min(node.min.y, primMin[t].y); node.min.z = min(node.min.z, primMin[t].z)
                node.max.x = max(node.max.x, primMax[t].x); node.max.y = max(node.max.y, primMax[t].y); node.max.z = max(node.max.z, primMax[t].z)
            }
        } else {
            refitNode(node.left)
            refitNode(node.right)
            val l = nodes[node.left]; val r = nodes[node.right]
            node.min.set(min(l.min.x, r.min.x), min(l.min.y, r.min.y), min(l.min.z, r.min.z))
            node.max.set(max(l.max.x, r.max.x), max(l.max.y, r.max.y), max(l.max.z, r.max.z))
        }
    }

    private fun buildNode(lo: Int, hi: Int, useSah: Boolean): Int {

        val node = Node()
        val index = nodes.size
        nodes.add(node)
        node.start = lo
        node.count = hi - lo
        node.min.set(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        node.max.set(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        val cMin = Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        val cMax = Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (i in lo until hi) {
            val t = order[i]
            node.min.x = min(node.min.x, primMin[t].x); node.min.y = min(node.min.y, primMin[t].y); node.min.z = min(node.min.z, primMin[t].z)
            node.max.x = max(node.max.x, primMax[t].x); node.max.y = max(node.max.y, primMax[t].y); node.max.z = max(node.max.z, primMax[t].z)
            cMin.x = min(cMin.x, primCentroid[t].x); cMin.y = min(cMin.y, primCentroid[t].y); cMin.z = min(cMin.z, primCentroid[t].z)
            cMax.x = max(cMax.x, primCentroid[t].x); cMax.y = max(cMax.y, primCentroid[t].y); cMax.z = max(cMax.z, primCentroid[t].z)
        }
        if (hi - lo <= leafSize) { node.left = -1; node.right = -1; return index }
        val ex = cMax.x - cMin.x; val ey = cMax.y - cMin.y; val ez = cMax.z - cMin.z
        val axis = if (ex >= ey && ex >= ez) 0 else if (ey >= ez) 1 else 2
        val mid = if (useSah) sahSplit(lo, hi, axis) else medianSplit(lo, hi, axis)
        if (mid <= lo || mid >= hi) { node.left = -1; node.right = -1; return index }
        val l = buildNode(lo, mid, useSah)
        val r = buildNode(mid, hi, useSah)
        node.left = l; node.right = r
        return index
    }

    private fun centroidAxis(t: Int, axis: Int): Float = when (axis) {
        0 -> primCentroid[t].x; 1 -> primCentroid[t].y; else -> primCentroid[t].z
    }

    private fun medianSplit(lo: Int, hi: Int, axis: Int): Int {
        val sub = Array(hi - lo) { order[lo + it] }
        java.util.Arrays.sort(sub) { a, b ->
            val c = centroidAxis(a, axis).compareTo(centroidAxis(b, axis))
            if (c != 0) c else a - b
        }
        for (i in sub.indices) order[lo + i] = sub[i]
        return (lo + hi) ushr 1
    }

    private fun areaOf(mn: Vec3, mx: Vec3): Float {
        if (mn.x > mx.x) return 0f
        val dx = mx.x - mn.x; val dy = mx.y - mn.y; val dz = mx.z - mn.z
        return 2f * (dx * dy + dy * dz + dz * dx)
    }

    private fun sahSplit(lo: Int, hi: Int, axis: Int): Int {
        var cMin = Float.MAX_VALUE; var cMax = -Float.MAX_VALUE
        for (i in lo until hi) { val c = centroidAxis(order[i], axis); cMin = min(cMin, c); cMax = max(cMax, c) }
        if (cMax - cMin < 1e-9f) return medianSplit(lo, hi, axis)
        val bins = 12
        val scale = bins / (cMax - cMin)
        val counts = IntArray(bins)
        val binMin = Array(bins) { Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE) }
        val binMax = Array(bins) { Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE) }
        for (i in lo until hi) {
            val t = order[i]
            var b = ((centroidAxis(t, axis) - cMin) * scale).toInt()
            if (b < 0) b = 0
            if (b >= bins) b = bins - 1
            counts[b]++
            val mn = binMin[b]; val mx = binMax[b]
            mn.x = min(mn.x, primMin[t].x); mn.y = min(mn.y, primMin[t].y); mn.z = min(mn.z, primMin[t].z)
            mx.x = max(mx.x, primMax[t].x); mx.y = max(mx.y, primMax[t].y); mx.z = max(mx.z, primMax[t].z)
        }
        val leftArea = FloatArray(bins); val rightArea = FloatArray(bins)
        val leftCount = IntArray(bins); val rightCount = IntArray(bins)
        val accMin = Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        val accMax = Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        var acc = 0
        for (b in 0 until bins) {
            if (counts[b] > 0) {
                accMin.x = min(accMin.x, binMin[b].x); accMin.y = min(accMin.y, binMin[b].y); accMin.z = min(accMin.z, binMin[b].z)
                accMax.x = max(accMax.x, binMax[b].x); accMax.y = max(accMax.y, binMax[b].y); accMax.z = max(accMax.z, binMax[b].z)
            }
            acc += counts[b]; leftCount[b] = acc; leftArea[b] = areaOf(accMin, accMax)
        }
        accMin.set(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        accMax.set(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        acc = 0
        for (b in bins - 1 downTo 0) {
            if (counts[b] > 0) {
                accMin.x = min(accMin.x, binMin[b].x); accMin.y = min(accMin.y, binMin[b].y); accMin.z = min(accMin.z, binMin[b].z)
                accMax.x = max(accMax.x, binMax[b].x); accMax.y = max(accMax.y, binMax[b].y); accMax.z = max(accMax.z, binMax[b].z)
            }
            acc += counts[b]; rightCount[b] = acc; rightArea[b] = areaOf(accMin, accMax)
        }
        var bestCost = Float.MAX_VALUE; var bestBin = -1
        for (b in 0 until bins - 1) {
            if (leftCount[b] == 0 || rightCount[b + 1] == 0) continue
            val cost = leftArea[b] * leftCount[b] + rightArea[b + 1] * rightCount[b + 1]
            if (cost < bestCost) { bestCost = cost; bestBin = b }
        }
        if (bestBin < 0) return medianSplit(lo, hi, axis)
        val splitCoord = cMin + (bestBin + 1) * (cMax - cMin) / bins
        var i = lo; var j = hi - 1
        while (i <= j) {
            if (centroidAxis(order[i], axis) < splitCoord) i++ else { val tmp = order[i]; order[i] = order[j]; order[j] = tmp; j-- }
        }
        val mid = i
        if (mid <= lo || mid >= hi) return medianSplit(lo, hi, axis)
        return mid
    }


    /** Triangle vertex positions. */
    fun triangle(t: Int, a: Vec3, b: Vec3, c: Vec3) {
        a.set(vertices[indices[t * 3]])
        b.set(vertices[indices[t * 3 + 1]])
        c.set(vertices[indices[t * 3 + 2]])
    }

    /** Triangle AABB into [out]. */
    fun triangleAabb(t: Int, out: Aabb3) { out.min.set(primMin[t]); out.max.set(primMax[t]) }

    private val stack = IntArray(256)

    /**
     * Closest ray/triangle hit in `[0, maxT]`. [out] is filled with the hit (triangle index,
     * point, geometric normal). Returns true when something was hit.
     */
    fun raycast(origin: Vec3, dir: Vec3, maxT: Float, out: BvhRayHit): Boolean {
        out.reset()
        if (root < 0) return false
        var best = maxT
        var sp = 0
        stack[sp++] = root
        val a = Vec3(); val b = Vec3(); val c = Vec3()
        while (sp > 0) {
            val n = nodes[stack[--sp]]
            if (!slab(n, origin, dir, best)) continue
            if (n.isLeaf) {
                for (i in n.start until n.start + n.count) {
                    val t = order[i]
                    triangle(t, a, b, c)
                    val hit = rayTriangle(origin, dir, a, b, c, best)
                    if (hit >= 0f) {
                        best = hit
                        out.hit = true; out.triangle = t; out.t = hit
                        out.point.set(origin.x + dir.x * hit, origin.y + dir.y * hit, origin.z + dir.z * hit)
                        out.normal.set((b - a).cross(c - a)).normalize()
                    }
                }
            } else if (sp + 2 <= stack.size) { stack[sp++] = n.left; stack[sp++] = n.right }
        }
        return out.hit
    }

    /** Collects every triangle whose AABB overlaps [aabb] into [out]. */
    fun queryAabb(aabb: Aabb3, out: MutableList<Int>) {
        if (root < 0) return
        var sp = 0
        stack[sp++] = root
        while (sp > 0) {
            val n = nodes[stack[--sp]]
            if (n.max.x < aabb.min.x || n.min.x > aabb.max.x) continue
            if (n.max.y < aabb.min.y || n.min.y > aabb.max.y) continue
            if (n.max.z < aabb.min.z || n.min.z > aabb.max.z) continue
            if (n.isLeaf) {
                for (i in n.start until n.start + n.count) {
                    val t = order[i]
                    if (primMax[t].x < aabb.min.x || primMin[t].x > aabb.max.x) continue
                    if (primMax[t].y < aabb.min.y || primMin[t].y > aabb.max.y) continue
                    if (primMax[t].z < aabb.min.z || primMin[t].z > aabb.max.z) continue
                    out.add(t)
                }
            } else if (sp + 2 <= stack.size) { stack[sp++] = n.left; stack[sp++] = n.right }
        }
    }

    /** Collects triangles whose AABB overlaps the sphere ([center],[radius]) into [out]. */
    fun querySphere(center: Vec3, radius: Float, out: MutableList<Int>) {
        val r2 = radius * radius
        if (root < 0) return
        var sp = 0
        stack[sp++] = root
        while (sp > 0) {
            val n = nodes[stack[--sp]]
            if (aabbSphereDist2(n.min, n.max, center) > r2) continue
            if (n.isLeaf) {
                for (i in n.start until n.start + n.count) {
                    val t = order[i]
                    if (aabbSphereDist2(primMin[t], primMax[t], center) <= r2) out.add(t)
                }
            } else if (sp + 2 <= stack.size) { stack[sp++] = n.left; stack[sp++] = n.right }
        }
    }

    /**
     * Nearest point on any triangle within [maxDistance] of [p]. Writes the point into [outPoint]
     * and returns the squared distance, or a negative value when nothing is within range.
     */
    fun closestPoint(p: Vec3, maxDistance: Float, outPoint: Vec3): Float {
        if (root < 0) return -1f
        var best2 = maxDistance * maxDistance
        var found = false
        val a = Vec3(); val b = Vec3(); val c = Vec3(); val tmp = Vec3()
        var sp = 0
        stack[sp++] = root
        while (sp > 0) {
            val n = nodes[stack[--sp]]
            if (aabbSphereDist2(n.min, n.max, p) > best2) continue
            if (n.isLeaf) {
                for (i in n.start until n.start + n.count) {
                    val t = order[i]
                    triangle(t, a, b, c)
                    val d2 = closestPointTriangle(p, a, b, c, tmp)
                    if (d2 < best2) { best2 = d2; outPoint.set(tmp); found = true }
                }
            } else if (sp + 2 <= stack.size) { stack[sp++] = n.left; stack[sp++] = n.right }
        }
        return if (found) best2 else -1f
    }


    /** Visits every triangle whose AABB overlaps [aabb]. */
    fun forEachTriangleInAabb(aabb: Aabb3, action: (Int) -> Unit) {
        if (root < 0) return
        var sp = 0
        stack[sp++] = root
        while (sp > 0) {
            val n = nodes[stack[--sp]]
            if (n.max.x < aabb.min.x || n.min.x > aabb.max.x) continue
            if (n.max.y < aabb.min.y || n.min.y > aabb.max.y) continue
            if (n.max.z < aabb.min.z || n.min.z > aabb.max.z) continue
            if (n.isLeaf) {
                for (i in n.start until n.start + n.count) {
                    val t = order[i]
                    if (primMax[t].x < aabb.min.x || primMin[t].x > aabb.max.x) continue
                    if (primMax[t].y < aabb.min.y || primMin[t].y > aabb.max.y) continue
                    if (primMax[t].z < aabb.min.z || primMin[t].z > aabb.max.z) continue
                    action(t)
                }
            } else if (sp + 2 <= stack.size) { stack[sp++] = n.left; stack[sp++] = n.right }
        }
    }

    fun worldAabb(out: Aabb3): Aabb3 {
        if (root < 0) return out.reset()
        val n = nodes[root]
        return out.set(n.min.x, n.min.y, n.min.z, n.max.x, n.max.y, n.max.z)
    }

    private fun slab(n: Node, o: Vec3, d: Vec3, maxT: Float): Boolean {
        var tmin = 0f; var tmax = maxT
        if (abs(d.x) < 1e-9f) { if (o.x < n.min.x || o.x > n.max.x) return false } else {
            var t1 = (n.min.x - o.x) / d.x; var t2 = (n.max.x - o.x) / d.x
            if (t1 > t2) { val t = t1; t1 = t2; t2 = t }
            if (t1 > tmin) tmin = t1; if (t2 < tmax) tmax = t2
            if (tmin > tmax) return false
        }
        if (abs(d.y) < 1e-9f) { if (o.y < n.min.y || o.y > n.max.y) return false } else {
            var t1 = (n.min.y - o.y) / d.y; var t2 = (n.max.y - o.y) / d.y
            if (t1 > t2) { val t = t1; t1 = t2; t2 = t }
            if (t1 > tmin) tmin = t1; if (t2 < tmax) tmax = t2
            if (tmin > tmax) return false
        }
        if (abs(d.z) < 1e-9f) { if (o.z < n.min.z || o.z > n.max.z) return false } else {
            var t1 = (n.min.z - o.z) / d.z; var t2 = (n.max.z - o.z) / d.z
            if (t1 > t2) { val t = t1; t1 = t2; t2 = t }
            if (t1 > tmin) tmin = t1; if (t2 < tmax) tmax = t2
            if (tmin > tmax) return false
        }
        return true
    }


    companion object {
        private const val EPS = 1e-7f

        /** Möller–Trumbore. Returns t >= 0 on hit, -1 otherwise. */
        fun rayTriangle(o: Vec3, d: Vec3, a: Vec3, b: Vec3, c: Vec3, maxT: Float): Float {
            val e1x = b.x - a.x; val e1y = b.y - a.y; val e1z = b.z - a.z
            val e2x = c.x - a.x; val e2y = c.y - a.y; val e2z = c.z - a.z
            val px = d.y * e2z - d.z * e2y
            val py = d.z * e2x - d.x * e2z
            val pz = d.x * e2y - d.y * e2x
            val det = e1x * px + e1y * py + e1z * pz
            if (det > -EPS && det < EPS) return -1f
            val inv = 1f / det
            val tx = o.x - a.x; val ty = o.y - a.y; val tz = o.z - a.z
            val u = (tx * px + ty * py + tz * pz) * inv
            if (u < -EPS || u > 1f + EPS) return -1f
            val qx = ty * e1z - tz * e1y
            val qy = tz * e1x - tx * e1z
            val qz = tx * e1y - ty * e1x
            val v = (d.x * qx + d.y * qy + d.z * qz) * inv
            if (v < -EPS || u + v > 1f + EPS) return -1f
            val t = (e2x * qx + e2y * qy + e2z * qz) * inv
            if (t < 0f || t > maxT) return -1f
            return t
        }

        /** Squared distance from [p] to the closest point on triangle abc (writes [out]). */
        fun closestPointTriangle(p: Vec3, a: Vec3, b: Vec3, c: Vec3, out: Vec3): Float {
            val abx = b.x - a.x; val aby = b.y - a.y; val abz = b.z - a.z
            val acx = c.x - a.x; val acy = c.y - a.y; val acz = c.z - a.z
            val apx = p.x - a.x; val apy = p.y - a.y; val apz = p.z - a.z
            val d1 = abx * apx + aby * apy + abz * apz
            val d2 = acx * apx + acy * apy + acz * apz
            if (d1 <= 0f && d2 <= 0f) { out.set(a); return dist2(p, a) }
            val bpx = p.x - b.x; val bpy = p.y - b.y; val bpz = p.z - b.z
            val d3 = abx * bpx + aby * bpy + abz * bpz
            val d4 = acx * bpx + acy * bpy + acz * bpz
            if (d3 >= 0f && d4 <= d3) { out.set(b); return dist2(p, b) }
            val vc = d1 * d4 - d3 * d2
            if (vc <= 0f && d1 >= 0f && d3 <= 0f) {
                val v = d1 / (d1 - d3)
                out.set(a.x + abx * v, a.y + aby * v, a.z + abz * v); return dist2(p, out)
            }
            val cpx = p.x - c.x; val cpy = p.y - c.y; val cpz = p.z - c.z
            val d5 = abx * cpx + aby * cpy + abz * cpz
            val d6 = acx * cpx + acy * cpy + acz * cpz
            if (d6 >= 0f && d5 <= d6) { out.set(c); return dist2(p, c) }
            val vb = d5 * d2 - d1 * d6
            if (vb <= 0f && d2 >= 0f && d6 <= 0f) {
                val w = d2 / (d2 - d6)
                out.set(a.x + acx * w, a.y + acy * w, a.z + acz * w); return dist2(p, out)
            }
            val va = d3 * d6 - d5 * d4
            if (va <= 0f && (d4 - d3) >= 0f && (d5 - d6) >= 0f) {
                val w = (d4 - d3) / ((d4 - d3) + (d5 - d6))
                out.set(b.x + (c.x - b.x) * w, b.y + (c.y - b.y) * w, b.z + (c.z - b.z) * w); return dist2(p, out)
            }
            val denom = 1f / (va + vb + vc)
            val v = vb * denom; val w = vc * denom
            out.set(a.x + abx * v + acx * w, a.y + aby * v + acy * w, a.z + abz * v + acz * w)
            return dist2(p, out)
        }

        private fun dist2(a: Vec3, b: Vec3): Float {
            val dx = a.x - b.x; val dy = a.y - b.y; val dz = a.z - b.z
            return dx * dx + dy * dy + dz * dz
        }

        fun aabbSphereDist2(mn: Vec3, mx: Vec3, c: Vec3): Float {
            var dx = 0f; var dy = 0f; var dz = 0f
            if (c.x < mn.x) dx = mn.x - c.x else if (c.x > mx.x) dx = c.x - mx.x
            if (c.y < mn.y) dy = mn.y - c.y else if (c.y > mx.y) dy = c.y - mx.y
            if (c.z < mn.z) dz = mn.z - c.z else if (c.z > mx.z) dz = c.z - mx.z
            return dx * dx + dy * dy + dz * dz
        }
    }
}

