package com.sengine.engine.math

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Axis aligned bounding box (min/max corners). Broadphase, culling and asset code all use this. */
class Aabb3(var min: Vec3 = Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE), var max: Vec3 = Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)) {

    constructor(minX: Float, minY: Float, minZ: Float, maxX: Float, maxY: Float, maxZ: Float) : this(Vec3(minX, minY, minZ), Vec3(maxX, maxY, maxZ))

    val isValid: Boolean get() = min.x <= max.x && min.y <= max.y && min.z <= max.z

    fun set(minX: Float, minY: Float, minZ: Float, maxX: Float, maxY: Float, maxZ: Float): Aabb3 { min.set(minX, minY, minZ); max.set(maxX, maxY, maxZ); return this }
    fun set(o: Aabb3): Aabb3 { min.set(o.min); max.set(o.max); return this }
    fun reset(): Aabb3 = set(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
    fun copy(): Aabb3 = Aabb3(min.copy(), max.copy())

    fun center(out: Vec3 = Vec3()): Vec3 = out.set((min.x + max.x) * 0.5f, (min.y + max.y) * 0.5f, (min.z + max.z) * 0.5f)
    fun extents(out: Vec3 = Vec3()): Vec3 = out.set((max.x - min.x) * 0.5f, (max.y - min.y) * 0.5f, (max.z - min.z) * 0.5f)
    fun size(out: Vec3 = Vec3()): Vec3 = out.set(max.x - min.x, max.y - min.y, max.z - min.z)
    fun radius(): Float = extents().length()

    fun expand(p: Vec3): Aabb3 {
        min.x = min(min.x, p.x); min.y = min(min.y, p.y); min.z = min(min.z, p.z)
        max.x = max(max.x, p.x); max.y = max(max.y, p.y); max.z = max(max.z, p.z)
        return this
    }

    fun expand(x: Float, y: Float, z: Float): Aabb3 = expand(Vec3(x, y, z))
    fun expand(o: Aabb3): Aabb3 {
        if (!o.isValid) return this
        min.x = min(min.x, o.min.x); min.y = min(min.y, o.min.y); min.z = min(min.z, o.min.z)
        max.x = max(max.x, o.max.x); max.y = max(max.y, o.max.y); max.z = max(max.z, o.max.z)
        return this
    }

    fun grow(amount: Float): Aabb3 {
        min.x -= amount; min.y -= amount; min.z -= amount
        max.x += amount; max.y += amount; max.z += amount
        return this
    }

    fun contains(p: Vec3): Boolean = p.x >= min.x && p.x <= max.x && p.y >= min.y && p.y <= max.y && p.z >= min.z && p.z <= max.z
    fun contains(a: Aabb3): Boolean = a.min.x >= min.x && a.max.x <= max.x && a.min.y >= min.y && a.max.y <= max.y && a.min.z >= min.z && a.max.z <= max.z

    fun intersects(o: Aabb3): Boolean =
        min.x <= o.max.x && max.x >= o.min.x && min.y <= o.max.y && max.y >= o.min.y && min.z <= o.max.z && max.z >= o.min.z

    /** Separating-axis test between this box and an oriented box (rotation [rm] row-major 3x3, [bHalf] extents, [bPos] centre). */
    fun intersectsObb(bPos: Vec3, bHalf: Vec3, rm: FloatArray, margin: Float = 0f): Boolean {
        val aHalf = extents()
        val aPos = center()
        val t = Vec3(
            bPos.x - aPos.x, bPos.y - aPos.y, bPos.z - aPos.z
        )
        val absR = FloatArray(9)
        for (i in 0 until 3) for (j in 0 until 3) absR[i * 3 + j] = abs(rm[i * 3 + j]) + 1e-6f
        val ax = floatArrayOf(aHalf.x, aHalf.y, aHalf.z)
        val bx = floatArrayOf(bHalf.x, bHalf.y, bHalf.z)
        // A axes
        for (i in 0 until 3) {
            val ra = ax[i]
            val rb = bx[0] * absR[i * 3] + bx[1] * absR[i * 3 + 1] + bx[2] * absR[i * 3 + 2]
            if (abs(t.toFloatArray()[i]) > ra + rb + margin) return false
        }
        // B axes
        val tArr = t.toFloatArray()
        for (j in 0 until 3) {
            val ra = ax[0] * absR[j] + ax[1] * absR[3 + j] + ax[2] * absR[6 + j]
            val rb = bx[j]
            val tj = abs(tArr[0] * rm[j] + tArr[1] * rm[3 + j] + tArr[2] * rm[6 + j])
            if (tj > ra + rb + margin) return false
        }
        return true
    }

    fun surfaceArea(): Float {
        if (!isValid) return 0f
        val d = size()
        return 2f * (d.x * d.y + d.y * d.z + d.z * d.x)
    }

    fun volume(): Float { val d = size(); return if (isValid) d.x * d.y * d.z else 0f }

    /** World-space AABB of this box transformed by a column-major 4x4 matrix. */
    fun transformedBy(m: FloatArray, out: Aabb3 = Aabb3()): Aabb3 {
        out.reset()
        for (i in 0 until 8) {
            val x = if (i and 1 == 0) min.x else max.x
            val y = if (i and 2 == 0) min.y else max.y
            val z = if (i and 4 == 0) min.z else max.z
            out.expand(
                m[0] * x + m[4] * y + m[8] * z + m[12],
                m[1] * x + m[5] * y + m[9] * z + m[13],
                m[2] * x + m[6] * y + m[10] * z + m[14]
            )
        }
        return out
    }

    /** Grows the box by the given world-space offset (swept AABB for CCD broadphase). */
    fun sweep(dx: Float, dy: Float, dz: Float): Aabb3 {
        val o = copy()
        if (dx > 0f) o.max.x += dx else o.min.x += dx
        if (dy > 0f) o.max.y += dy else o.min.y += dy
        if (dz > 0f) o.max.z += dz else o.min.z += dz
        return o
    }

    override fun toString(): String = "Aabb3[$min .. $max]"

    companion object {
        fun fromCenterExtents(center: Vec3, extents: Vec3) = Aabb3(
            center.x - extents.x, center.y - extents.y, center.z - extents.z,
            center.x + extents.x, center.y + extents.y, center.z + extents.z
        )

        fun fromCenterRadius(center: Vec3, radius: Float) = Aabb3(
            center.x - radius, center.y - radius, center.z - radius,
            center.x + radius, center.y + radius, center.z + radius
        )

        fun fromPoints(points: List<Vec3>): Aabb3 {
            val b = Aabb3()
            for (p in points) b.expand(p)
            return b
        }

        fun union(a: Aabb3, b: Aabb3): Aabb3 = a.copy().expand(b)

        fun empty() = Aabb3()
    }
}

/** Ray with normalised (or arbitrary) direction. */
class Ray3(var origin: Vec3 = Vec3(), var dir: Vec3 = Vec3.FORWARD) {
    fun pointAt(t: Float, out: Vec3 = Vec3()): Vec3 = out.set(origin.x + dir.x * t, origin.y + dir.y * t, origin.z + dir.z * t)
    fun normalized(): Ray3 = Ray3(origin.copy(), dir.normalized())
    override fun toString(): String = "Ray3[$origin -> $dir]"
}

/** Plane in the form dot(normal, p) + d = 0. */
class Plane3(var nx: Float = 0f, var ny: Float = 1f, var nz: Float = 0f, var d: Float = 0f) {

    fun set(nx: Float, ny: Float, nz: Float, d: Float): Plane3 {
        this.nx = nx; this.ny = ny; this.nz = nz; this.d = d
        return this
    }

    fun set(o: Plane3): Plane3 = set(o.nx, o.ny, o.nz, o.d)
    fun setPointNormal(point: Vec3, normal: Vec3): Plane3 {
        val n = normal.normalizedOr(Vec3.UP)
        nx = n.x; ny = n.y; nz = n.z
        d = -(n.x * point.x + n.y * point.y + n.z * point.z)
        return this
    }

    fun distance(x: Float, y: Float, z: Float): Float = nx * x + ny * y + nz * z + d
    fun distance(p: Vec3): Float = distance(p.x, p.y, p.z)

    fun normalize(): Plane3 {
        val l = sqrt(nx * nx + ny * ny + nz * nz)
        if (l > 1e-12f) { val i = 1f / l; nx *= i; ny *= i; nz *= i; d *= i }
        return this
    }

    override fun toString(): String = "Plane3(n=($nx, $ny, $nz), d=$d)"
}

/** View frustum extracted from a projection*view matrix (Gribb/Hartmann, column-major input). */
class Frustum3 {
    val planes = Array(6) { Plane3() }
    var left = 0; var right = 1; var bottom = 2; var top = 3; var near = 4; var far = 5

    fun setFromMatrix(m: FloatArray): Frustum3 {
        val r0x = m[0]; val r0y = m[4]; val r0z = m[8]; val r0w = m[12]
        val r1x = m[1]; val r1y = m[5]; val r1z = m[9]; val r1w = m[13]
        val r2x = m[2]; val r2y = m[6]; val r2z = m[10]; val r2w = m[14]
        val r3x = m[3]; val r3y = m[7]; val r3z = m[11]; val r3w = m[15]
        planes[left].set(r3x + r0x, r3y + r0y, r3z + r0z, r3w + r0w)
        planes[right].set(r3x - r0x, r3y - r0y, r3z - r0z, r3w - r0w)
        planes[bottom].set(r3x + r1x, r3y + r1y, r3z + r1z, r3w + r1w)
        planes[top].set(r3x - r1x, r3y - r1y, r3z - r1z, r3w - r1w)
        planes[near].set(r3x + r2x, r3y + r2y, r3z + r2z, r3w + r2w)
        planes[far].set(r3x - r2x, r3y - r2y, r3z - r2z, r3w - r2w)
        for (p in planes) p.normalize()
        return this
    }

    fun testPoint(x: Float, y: Float, z: Float): Boolean {
        for (p in planes) if (p.distance(x, y, z) < 0f) return false
        return true
    }

    /** -1 = outside, 0 = intersecting, 1 = fully inside. */
    fun classifySphere(c: Vec3, r: Float): Int {
        var result = 1
        for (p in planes) {
            val d = p.distance(c)
            if (d < -r) return -1
            if (d < r) result = 0
        }
        return result
    }

    fun testSphere(c: Vec3, r: Float): Boolean = classifySphere(c, r) >= 0

    /** -1 = outside, 0 = intersecting, 1 = fully inside. */
    fun classifyAabb(b: Aabb3): Int {
        var result = 1
        for (p in planes) {
            val px = if (p.nx >= 0f) b.max.x else b.min.x
            val py = if (p.ny >= 0f) b.max.y else b.min.y
            val pz = if (p.nz >= 0f) b.max.z else b.min.z
            if (p.distance(px, py, pz) < 0f) return -1
            val nx = if (p.nx >= 0f) b.min.x else b.max.x
            val ny = if (p.ny >= 0f) b.min.y else b.max.y
            val nz = if (p.nz >= 0f) b.min.z else b.max.z
            if (p.distance(nx, ny, nz) < 0f) result = 0
        }
        return result
    }

    fun testAabb(b: Aabb3): Boolean = classifyAabb(b) >= 0
}
