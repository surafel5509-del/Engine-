package com.sengine.engine.physics

import com.sengine.engine.math.Aabb3
import com.sengine.engine.math.Quat
import com.sengine.engine.math.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val PI_F = 3.14159265358979f

/**
 * Closed-form mass properties of a closed, outward-oriented triangle soup (used by
 * [ConvexHullShape3D] and [TriangleMeshShape3D]). Tetrahedra are formed with the origin as the
 * apex and the signed volumes/moments summed with the divergence theorem, so the result is exact
 * for a well-formed mesh regardless of where the origin sits.
 */
internal object MassProps3D {
    /** Accumulates volume, centre of mass and the full second-moment tensor. */
    class Accumulator {
        var volume = 0f
        private var fx = 0f; private var fy = 0f; private var fz = 0f
        private var mxx = 0f; private var myy = 0f; private var mzz = 0f
        private var mxy = 0f; private var mxz = 0f; private var myz = 0f

        fun add(a: Vec3, b: Vec3, c: Vec3) {
            val v = a.dot(b.cross(c)) / 6f
            volume += v
            val sx = a.x + b.x + c.x
            val sy = a.y + b.y + c.y
            val sz = a.z + b.z + c.z
            fx += v * 0.25f * sx; fy += v * 0.25f * sy; fz += v * 0.25f * sz
            val sxx = a.x * a.x + b.x * b.x + c.x * c.x
            val syy = a.y * a.y + b.y * b.y + c.y * c.y
            val szz = a.z * a.z + b.z * b.z + c.z * c.z
            val sxy = a.x * a.y + b.x * b.y + c.x * c.y
            val sxz = a.x * a.z + b.x * b.z + c.x * c.z
            val syz = a.y * a.z + b.y * b.z + c.y * c.z
            mxx += v / 20f * (sxx + sx * sx)
            myy += v / 20f * (syy + sy * sy)
            mzz += v / 20f * (szz + sz * sz)
            mxy += v / 20f * (sxy + sx * sy)
            mxz += v / 20f * (sxz + sx * sz)
            myz += v / 20f * (syz + sy * sz)
        }

        /** Unit-density diagonal inertia (about the centre of mass) into [out]; returns the volume. */
        fun finish(out: Vec3): Float {
            if (abs(volume) < 1e-12f) { out.set(0f, 0f, 0f); return 0f }
            val cx = fx / volume; val cy = fy / volume; val cz = fz / volume
            // inertia about the origin from the second moments (unit density)
            val ixx0 = myy + mzz
            val iyy0 = mxx + mzz
            val izz0 = mxx + myy
            out.set(
                ixx0 - volume * (cy * cy + cz * cz),
                iyy0 - volume * (cx * cx + cz * cz),
                izz0 - volume * (cx * cx + cy * cy)
            )
            return volume
        }

        /** Centre of mass into [out]. */
        fun centerOfMass(out: Vec3): Vec3 {
            if (abs(volume) < 1e-12f) return out.set(0f, 0f, 0f)
            return out.set(fx / volume, fy / volume, fz / volume)
        }
    }
}

/** Sphere centred on the origin. */
class SphereShape3D(val radius: Float) : Shape3D {
    override val kind: ShapeKind get() = ShapeKind.SPHERE
    override fun localAabb(): Aabb3 = Aabb3.fromCenterRadius(Vec3(), radius)
    override fun supportLocal(dir: Vec3, out: Vec3): Vec3 {
        val l = dir.length()
        if (l < 1e-9f) return out.set(radius, 0f, 0f)
        val s = radius / l
        return out.set(dir.x * s, dir.y * s, dir.z * s)
    }
    override fun volume(): Float = (4f / 3f) * PI_F * radius * radius * radius
    override fun localInertia(mass: Float, out: Vec3): Vec3 {
        val i = 0.4f * mass * radius * radius
        return out.set(i, i, i)
    }
    override fun boundingRadius(): Float = radius
    override fun toString(): String = "Sphere(r=$radius)"
}

/** Axis-aligned box (oriented box in world space once rotated) described by its half extents. */
class BoxShape3D(val halfExtents: Vec3) : Shape3D {

    constructor(hx: Float, hy: Float, hz: Float) : this(Vec3(hx, hy, hz))
    constructor(halfSize: Float) : this(halfSize, halfSize, halfSize)

    override val kind: ShapeKind get() = ShapeKind.BOX
    override fun localAabb(): Aabb3 =
        Aabb3(-halfExtents.x, -halfExtents.y, -halfExtents.z, halfExtents.x, halfExtents.y, halfExtents.z)

    override fun supportLocal(dir: Vec3, out: Vec3): Vec3 = out.set(
        if (dir.x >= 0f) halfExtents.x else -halfExtents.x,
        if (dir.y >= 0f) halfExtents.y else -halfExtents.y,
        if (dir.z >= 0f) halfExtents.z else -halfExtents.z
    )

    override fun volume(): Float = 8f * halfExtents.x * halfExtents.y * halfExtents.z
    override fun localInertia(mass: Float, out: Vec3): Vec3 {
        val k = mass / 3f
        val hx = halfExtents.x; val hy = halfExtents.y; val hz = halfExtents.z
        return out.set(k * (hy * hy + hz * hz), k * (hx * hx + hz * hz), k * (hx * hx + hy * hy))
    }
    override fun boundingRadius(): Float = halfExtents.length()
    override fun toString(): String = "Box($halfExtents)"
}

/**
 * Capsule (swept sphere) whose core segment runs along the local Y axis from
 * `(0,-halfHeight,0)` to `(0,+halfHeight,0)`, with hemispherical caps of [radius].
 */
class CapsuleShape3D(val radius: Float, val halfHeight: Float) : Shape3D {
    override val kind: ShapeKind get() = ShapeKind.CAPSULE
    override fun localAabb(): Aabb3 =
        Aabb3(-radius, -halfHeight - radius, -radius, radius, halfHeight + radius, radius)

    override fun supportLocal(dir: Vec3, out: Vec3): Vec3 {
        val l = dir.length()
        val y = if (dir.y >= 0f) halfHeight else -halfHeight
        if (l < 1e-9f) return out.set(0f, y + radius, 0f)
        val s = radius / l
        return out.set(dir.x * s, y + dir.y * s, dir.z * s)
    }

    override fun volume(): Float = PI_F * radius * radius * (2f * halfHeight) + (4f / 3f) * PI_F * radius * radius * radius
    override fun localInertia(mass: Float, out: Vec3): Vec3 {
        val r = radius; val hh = halfHeight
        val volCyl = PI_F * r * r * (2f * hh)
        val volSph = (4f / 3f) * PI_F * r * r * r
        val total = volCyl + volSph
        if (total < 1e-12f) return out.set(0f, 0f, 0f)
        val rho = mass / total
        val mc = rho * volCyl
        val ms = rho * volSph
        val iy = mc * 0.5f * r * r + ms * 0.4f * r * r
        val hemiPerp = 0.259375f * r * r // (2/5 - 9/64) r^2 about the hemisphere's own com
        val d = hh + 0.375f * r
        val ix = mc * (hh * hh / 3f + r * r / 4f) + ms * hemiPerp + ms * d * d
        return out.set(ix, iy, ix)
    }
    override fun boundingRadius(): Float = halfHeight + radius
    override fun toString(): String = "Capsule(r=$radius, hh=$halfHeight)"
}

/** Solid cylinder whose axis runs along the local Y axis. */
class CylinderShape3D(val radius: Float, val halfHeight: Float) : Shape3D {
    override val kind: ShapeKind get() = ShapeKind.CYLINDER
    override fun localAabb(): Aabb3 = Aabb3(-radius, -halfHeight, -radius, radius, halfHeight, radius)

    override fun supportLocal(dir: Vec3, out: Vec3): Vec3 {
        val y = if (dir.y >= 0f) halfHeight else -halfHeight
        val len = sqrt(dir.x * dir.x + dir.z * dir.z)
        if (len < 1e-9f) return out.set(0f, y, 0f)
        val s = radius / len
        return out.set(dir.x * s, y, dir.z * s)
    }

    override fun volume(): Float = PI_F * radius * radius * (2f * halfHeight)
    override fun localInertia(mass: Float, out: Vec3): Vec3 {
        val r = radius; val hh = halfHeight
        val iy = 0.5f * mass * r * r
        val ix = mass * (hh * hh / 3f + r * r / 4f)
        return out.set(ix, iy, ix)
    }
    override fun boundingRadius(): Float = sqrt(radius * radius + halfHeight * halfHeight)
    override fun toString(): String = "Cylinder(r=$radius, hh=$halfHeight)"
}


/** Convex hull produced by the incremental builder: vertices, triangle indices and face normals. */
internal class HullData(val vertices: Array<Vec3>, val indices: IntArray, val normals: Array<Vec3>)

/**
 * Incremental (gift-wrapping style) 3D convex hull. Starts from an extreme tetrahedron and adds
 * the remaining points one at a time, removing the visible faces and stitching the horizon.
 * Deterministic for a given input order. Returns `null` for degenerate (flat/collinear) clouds.
 */
internal object Hull3D {

    private class Face(var a: Int, var b: Int, var c: Int) {
        val normal = Vec3()
        var offset = 0f
        var deleted = false
        fun update(pts: Array<Vec3>) {
            normal.set((pts[b] - pts[a]).cross(pts[c] - pts[a])).normalize()
            offset = normal.dot(pts[a])
        }
        fun dist(p: Vec3): Float = normal.dot(p) - offset
    }

    fun build(input: List<Vec3>, tolerance: Float = 1e-6f): HullData? {
        val pts = dedupe(input, tolerance)
        if (pts.size < 4) return null
        var i0 = 0
        for (i in 1 until pts.size) if (pts[i].x < pts[i0].x || (pts[i].x == pts[i0].x && pts[i].y < pts[i0].y)) i0 = i
        var i1 = -1; var best = tolerance
        for (i in 0 until pts.size) { val d = pts[i].distanceTo(pts[i0]); if (d > best) { best = d; i1 = i } }
        if (i1 < 0) return null
        val e = pts[i1] - pts[i0]
        var i2 = -1; best = tolerance
        for (i in 0 until pts.size) {
            val d = e.cross(pts[i] - pts[i0]).length() / e.length()
            if (d > best) { best = d; i2 = i }
        }
        if (i2 < 0) return null
        val n = (pts[i1] - pts[i0]).cross(pts[i2] - pts[i0]).normalizedOr(Vec3.UP)
        var i3 = -1; best = tolerance
        for (i in 0 until pts.size) {
            val d = abs(n.dot(pts[i] - pts[i0]))
            if (d > best) { best = d; i3 = i }
        }
        if (i3 < 0) return null

        val interior = Vec3(
            (pts[i0].x + pts[i1].x + pts[i2].x + pts[i3].x) * 0.25f,
            (pts[i0].y + pts[i1].y + pts[i2].y + pts[i3].y) * 0.25f,
            (pts[i0].z + pts[i1].z + pts[i2].z + pts[i3].z) * 0.25f
        )
        val faces = ArrayList<Face>(64)
        addFace(faces, pts, i0, i1, i2, interior)
        addFace(faces, pts, i0, i1, i3, interior)
        addFace(faces, pts, i0, i2, i3, interior)
        addFace(faces, pts, i1, i2, i3, interior)

        val used = BooleanArray(pts.size)
        used[i0] = true; used[i1] = true; used[i2] = true; used[i3] = true
        val eps = max(tolerance, hullScale(pts) * 1e-6f)

        for (p in 0 until pts.size) {
            if (used[p]) continue
            val pt = pts[p]
            var visible = false
            for (f in faces) if (!f.deleted && f.dist(pt) > eps) { visible = true; break }
            if (!visible) continue
            used[p] = true
            val edgeSet = HashSet<Long>()
            for (f in faces) {
                if (f.deleted) continue
                if (f.dist(pt) > eps) {
                    edgeSet.add(key(f.a, f.b)); edgeSet.add(key(f.b, f.c)); edgeSet.add(key(f.c, f.a))
                }
            }
            val horizon = ArrayList<IntArray>()
            for (f in faces) {
                if (f.deleted) continue
                if (f.dist(pt) > eps) {
                    if (!edgeSet.contains(key(f.b, f.a))) horizon.add(intArrayOf(f.a, f.b))
                    if (!edgeSet.contains(key(f.c, f.b))) horizon.add(intArrayOf(f.b, f.c))
                    if (!edgeSet.contains(key(f.a, f.c))) horizon.add(intArrayOf(f.c, f.a))
                }
            }
            val kept = ArrayList<Face>(faces.size)
            for (f in faces) if (f.deleted || f.dist(pt) <= eps) kept.add(f)
            faces.clear(); faces.addAll(kept)
            for (h in horizon) addFace(faces, pts, h[0], h[1], p, interior)
        }

        val usedIdx = ArrayList<Int>()
        val remap = HashMap<Int, Int>()
        val outIndices = ArrayList<Int>(faces.size * 3)
        for (f in faces) {
            if (f.deleted) continue
            for (v in intArrayOf(f.a, f.b, f.c)) {
                val m = remap.getOrPut(v) { usedIdx.add(v); usedIdx.size - 1 }
                outIndices.add(m)
            }
        }
        if (outIndices.size < 12) return null
        val verts = Array(usedIdx.size) { pts[usedIdx[it]].copy() }
        val idx = outIndices.toIntArray()
        val normals = Array(idx.size / 3) { Vec3() }
        for (t in normals.indices) {
            val a = verts[idx[t * 3]]; val b = verts[idx[t * 3 + 1]]; val c = verts[idx[t * 3 + 2]]
            normals[t].set((b - a).cross(c - a)).normalize()
        }
        return HullData(verts, idx, normals)
    }


    private fun addFace(faces: ArrayList<Face>, pts: Array<Vec3>, a: Int, b: Int, c: Int, interior: Vec3) {
        val f = Face(a, b, c)
        f.update(pts)
        if (f.normal.dot(interior - pts[a]) > 0f) { // interior must sit behind the face
            f.b = c; f.c = b
            f.update(pts)
        }
        faces.add(f)
    }

    private fun key(a: Int, b: Int): Long = (a.toLong() shl 32) or (b.toLong() and 0xFFFFFFFFL)

    private fun dedupe(input: List<Vec3>, tol: Float): Array<Vec3> {
        val out = ArrayList<Vec3>(input.size)
        val t2 = tol * tol
        for (p in input) {
            var dup = false
            for (q in out) if (q.distanceSqTo(p) <= t2) { dup = true; break }
            if (!dup) out.add(p.copy())
        }
        return out.toTypedArray()
    }

    private fun hullScale(pts: Array<Vec3>): Float {
        val mn = Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        val mx = Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (p in pts) {
            mn.x = min(mn.x, p.x); mn.y = min(mn.y, p.y); mn.z = min(mn.z, p.z)
            mx.x = max(mx.x, p.x); mx.y = max(mx.y, p.y); mx.z = max(mx.z, p.z)
        }
        return max(mx.x - mn.x, max(mx.y - mn.y, mx.z - mn.z))
    }
}

/**
 * Convex hull of a point cloud. The hull is recentred on its centre of mass so the shape origin
 * matches the body origin, and mass properties are computed exactly from the hull faces.
 * Degenerate clouds (fewer than four non-coplanar points) fall back to the AABB box.
 */
class ConvexHullShape3D(points: List<Vec3>) : Shape3D {

    /** Hull vertices in local space (centred on the centre of mass). */
    val vertices: Array<Vec3>
    /** Triangle indices, three per face. */
    val indices: IntArray
    /** Per-face outward normals. */
    val faceNormals: Array<Vec3>

    private val box = Aabb3()
    private val boundRadius: Float
    private val vol: Float
    private val unitInertia = Vec3()

    init {
        val hull = Hull3D.build(points)
        if (hull == null) {
            val b = Aabb3.fromPoints(points)
            val c = b.center()
            val e = b.extents()
            val vs = arrayListOf<Vec3>()
            for (i in 0 until 8) vs.add(Vec3(
                if (i and 1 == 0) c.x - e.x else c.x + e.x,
                if (i and 2 == 0) c.y - e.y else c.y + e.y,
                if (i and 4 == 0) c.z - e.z else c.z + e.z
            ))
            val fb = Hull3D.build(vs)!!
            vertices = fb.vertices; indices = fb.indices; faceNormals = fb.normals
        } else {
            vertices = hull.vertices; indices = hull.indices; faceNormals = hull.normals
        }
        // recentre on the centre of mass
        val acc0 = MassProps3D.Accumulator()
        for (t in 0 until indices.size / 3) acc0.add(vertices[indices[t * 3]], vertices[indices[t * 3 + 1]], vertices[indices[t * 3 + 2]])
        val com = acc0.centerOfMass(Vec3())
        for (v in vertices) v.sub(com)
        val acc = MassProps3D.Accumulator()
        for (t in 0 until indices.size / 3) acc.add(vertices[indices[t * 3]], vertices[indices[t * 3 + 1]], vertices[indices[t * 3 + 2]])
        vol = abs(acc.finish(unitInertia))
        var r = 0f
        box.reset()
        for (v in vertices) { box.expand(v); r = max(r, v.length()) }
        boundRadius = r
    }

    override val kind: ShapeKind get() = ShapeKind.CONVEX_HULL
    override fun localAabb(): Aabb3 = box.copy()

    override fun supportLocal(dir: Vec3, out: Vec3): Vec3 {
        var bestDot = -Float.MAX_VALUE
        var bx = 0f; var by = 0f; var bz = 0f
        for (v in vertices) {
            val d = v.x * dir.x + v.y * dir.y + v.z * dir.z
            if (d > bestDot) { bestDot = d; bx = v.x; by = v.y; bz = v.z }
        }
        return out.set(bx, by, bz)
    }

    override fun volume(): Float = vol
    override fun localInertia(mass: Float, out: Vec3): Vec3 {
        if (vol < 1e-12f) return out.set(0f, 0f, 0f)
        val s = mass / vol
        return out.set(unitInertia.x * s, unitInertia.y * s, unitInertia.z * s)
    }
    override fun boundingRadius(): Float = boundRadius
    override fun toString(): String = "ConvexHull(v=${vertices.size}, f=${indices.size / 3}, vol=$vol)"
}


/**
 * Static triangle mesh with a BVH for raycasts, overlap queries and triangle-level narrowphase.
 * The mesh is meant to be used on static bodies; it is not recentred, so the local frame matches
 * the authored geometry.
 */
class TriangleMeshShape3D(val vertices: Array<Vec3>, val indices: IntArray) : Shape3D {

    /** Acceleration structure over the triangles. */
    val bvh = Bvh3D()

    private val box = Aabb3()
    private val boundRadius: Float
    private val vol: Float
    private val unitInertia = Vec3()

    init {
        bvh.build(vertices, indices)
        box.reset()
        var r = 0f
        for (v in vertices) { box.expand(v); r = max(r, v.length()) }
        boundRadius = r
        val acc = MassProps3D.Accumulator()
        for (t in 0 until indices.size / 3) acc.add(vertices[indices[t * 3]], vertices[indices[t * 3 + 1]], vertices[indices[t * 3 + 2]])
        vol = abs(acc.finish(unitInertia))
    }

    val triangleCount: Int get() = indices.size / 3

    override val kind: ShapeKind get() = ShapeKind.TRIANGLE_MESH
    override fun localAabb(): Aabb3 = box.copy()

    override fun supportLocal(dir: Vec3, out: Vec3): Vec3 {
        var bestDot = -Float.MAX_VALUE
        var bx = 0f; var by = 0f; var bz = 0f
        for (v in vertices) {
            val d = v.x * dir.x + v.y * dir.y + v.z * dir.z
            if (d > bestDot) { bestDot = d; bx = v.x; by = v.y; bz = v.z }
        }
        return out.set(bx, by, bz)
    }

    override fun volume(): Float = vol
    override fun localInertia(mass: Float, out: Vec3): Vec3 {
        if (vol < 1e-12f) return out.set(0f, 0f, 0f)
        val s = mass / vol
        return out.set(unitInertia.x * s, unitInertia.y * s, unitInertia.z * s)
    }
    override fun boundingRadius(): Float = boundRadius
    override fun toString(): String = "TriangleMesh(v=${vertices.size}, tris=$triangleCount)"
}

/**
 * Infinite plane `dot(normal, x) = offset` (local frame). Planes are resolved analytically in the
 * narrowphase, so [supportLocal] only provides a far-away point for the convex routines.
 */
class PlaneShape3D(normal: Vec3, val offset: Float = 0f) : Shape3D {

    /** Unit plane normal. */
    val normal: Vec3 = normal.normalizedOr(Vec3.UP)

    override val kind: ShapeKind get() = ShapeKind.PLANE
    override val isInfinite: Boolean get() = true
    override fun localAabb(): Aabb3 = Aabb3.empty()

    override fun supportLocal(dir: Vec3, out: Vec3): Vec3 {
        val big = 1e6f
        val dn = dir.dot(normal)
        return if (abs(dn) > 1e-6f) {
            val s = if (dn > 0f) big else -big
            out.set(normal.x * (s + offset), normal.y * (s + offset), normal.z * (s + offset))
        } else {
            val t = dir.cross(normal).normalizedOr(Vec3.RIGHT).cross(normal)
            out.set(t.x * big + normal.x * offset, t.y * big + normal.y * offset, t.z * big + normal.z * offset)
        }
    }

    override fun volume(): Float = 0f
    override fun localInertia(mass: Float, out: Vec3): Vec3 = out.set(0f, 0f, 0f)
    override fun boundingRadius(): Float = 1e9f
    /** Signed distance from a local-space point to the plane (positive on the normal side). */
    fun distance(p: Vec3): Float = normal.dot(p) - offset
    override fun toString(): String = "Plane(n=$normal, offset=$offset)"
}


/**
 * Regular heightfield on the local XZ plane. [heights] holds `width * depth` samples in row-major
 * order (`j * width + i`), with `i` along X and `j` along Z. A BVH over the generated triangles
 * accelerates raycasts and collisions. Meant for static bodies.
 */
class HeightfieldShape3D(
    val width: Int,
    val depth: Int,
    val scaleX: Float,
    val scaleZ: Float,
    val heights: FloatArray
) : Shape3D {

    init {
        require(width >= 2 && depth >= 2) { "heightfield needs at least a 2x2 grid" }
        require(heights.size >= width * depth) { "heights must hold width*depth samples" }
    }

    /** Generated grid vertices. */
    val vertices: Array<Vec3>
    /** Generated triangle indices. */
    val indices: IntArray
    /** BVH over the generated triangles. */
    val bvh = Bvh3D()

    private val box = Aabb3()
    private val boundRadius: Float
    private val halfW = (width - 1) * 0.5f
    private val halfD = (depth - 1) * 0.5f

    init {
        vertices = Array(width * depth) { k ->
            val i = k % width; val j = k / width
            Vec3((i - halfW) * scaleX, heights[k], (j - halfD) * scaleZ)
        }
        val tris = ArrayList<Int>((width - 1) * (depth - 1) * 6)
        for (j in 0 until depth - 1) for (i in 0 until width - 1) {
            val v00 = j * width + i
            val v10 = j * width + i + 1
            val v01 = (j + 1) * width + i
            val v11 = (j + 1) * width + i + 1
            tris.add(v00); tris.add(v01); tris.add(v10)
            tris.add(v10); tris.add(v01); tris.add(v11)
        }
        indices = tris.toIntArray()
        bvh.build(vertices, indices)
        box.reset()
        var r = 0f
        for (v in vertices) { box.expand(v); r = max(r, v.length()) }
        boundRadius = r
    }

    val triangleCount: Int get() = indices.size / 3

    override val kind: ShapeKind get() = ShapeKind.HEIGHTFIELD
    override fun localAabb(): Aabb3 = box.copy()

    override fun supportLocal(dir: Vec3, out: Vec3): Vec3 {
        var bestDot = -Float.MAX_VALUE
        var bx = 0f; var by = 0f; var bz = 0f
        for (v in vertices) {
            val d = v.x * dir.x + v.y * dir.y + v.z * dir.z
            if (d > bestDot) { bestDot = d; bx = v.x; by = v.y; bz = v.z }
        }
        return out.set(bx, by, bz)
    }

    override fun volume(): Float = 0f
    override fun localInertia(mass: Float, out: Vec3): Vec3 = out.set(0f, 0f, 0f)
    override fun boundingRadius(): Float = boundRadius

    /** Raw sample value at grid coordinate (i, j). */
    fun sample(i: Int, j: Int): Float = heights[j.coerceIn(0, depth - 1) * width + i.coerceIn(0, width - 1)]

    /** Bilinearly interpolated height at local (x, z); clamped to the field extent. */
    fun heightAt(x: Float, z: Float): Float {
        val fi = (x / scaleX + halfW).coerceIn(0f, (width - 1).toFloat())
        val fj = (z / scaleZ + halfD).coerceIn(0f, (depth - 1).toFloat())
        val i0 = fi.toInt().coerceAtMost(width - 2)
        val j0 = fj.toInt().coerceAtMost(depth - 2)
        val tx = fi - i0; val tz = fj - j0
        val h00 = sample(i0, j0); val h10 = sample(i0 + 1, j0)
        val h01 = sample(i0, j0 + 1); val h11 = sample(i0 + 1, j0 + 1)
        val a = h00 + (h10 - h00) * tx
        val b = h01 + (h11 - h01) * tx
        return a + (b - a) * tz
    }

    /** Surface normal at local (x, z) via central differences. */
    fun normalAt(x: Float, z: Float, out: Vec3 = Vec3()): Vec3 {
        val e = max(scaleX, scaleZ) * 0.5f
        val hl = heightAt(x - e, z); val hr = heightAt(x + e, z)
        val hd = heightAt(x, z - e); val hu = heightAt(x, z + e)
        return out.set(hl - hr, 2f * e, hd - hu).normalize()
    }

    override fun toString(): String = "Heightfield(${width}x$depth)"
}


/**
 * Compound shape: a list of child shapes each with a local position/rotation. Used for multi-part
 * bodies (a hammer, a vehicle chassis, ...). Children should be placed so that the compound origin
 * is the centre of mass; mass properties are aggregated with the parallel-axis theorem.
 */
class CompoundShape3D(val children: List<Child>) : Shape3D {

    /** One child shape placed in the compound's local frame. */
    class Child(val shape: Shape3D, val position: Vec3 = Vec3(), val rotation: Quat = Quat())

    private val box = Aabb3()
    private val boundRadius: Float
    private val vol: Float
    private val unitInertia = Vec3()

    init {
        box.reset()
        var r = 0f
        var totalVol = 0f
        for (c in children) totalVol += abs(c.shape.volume())
        val tensor = FloatArray(9)
        val com = Vec3()
        for (c in children) {
            val cv = abs(c.shape.volume())
            val m = if (totalVol > 1e-9f) cv else 1f
            com.addScaled(c.position, m)
        }
        if (totalVol > 1e-9f) com.scale(1f / totalVol)
        var totalMass = 0f
        val rm = FloatArray(9)
        for (c in children) {
            val cv = abs(c.shape.volume())
            val m = if (totalVol > 1e-9f) cv else 1f
            totalMass += m
            // transform the child's local AABB to the compound frame
            val la = c.shape.localAabb()
            for (k in 0 until 8) {
                val lx = if (k and 1 == 0) la.min.x else la.max.x
                val ly = if (k and 2 == 0) la.min.y else la.max.y
                val lz = if (k and 4 == 0) la.min.z else la.max.z
                val rp = c.rotation.rotate(Vec3(lx, ly, lz))
                box.expand(rp.x + c.position.x, rp.y + c.position.y, rp.z + c.position.z)
            }
            r = max(r, c.position.length() + c.shape.boundingRadius())
            // child inertia about its own frame
            val diag = c.shape.localInertia(m)
            c.rotation.toMat3(rm)
            for (i in 0 until 3) for (j in 0 until 3) {
                var sum = 0f
                for (k in 0 until 3) sum += rm[i * 3 + k] * diagArr(diag, k) * rm[j * 3 + k]
                tensor[i * 3 + j] += sum
            }
            // parallel axis about the compound origin
            val px = c.position.x; val py = c.position.y; val pz = c.position.z
            val p2 = px * px + py * py + pz * pz
            tensor[0] += m * (p2 - px * px)
            tensor[4] += m * (p2 - py * py)
            tensor[8] += m * (p2 - pz * pz)
            tensor[1] -= m * px * py; tensor[3] -= m * px * py
            tensor[2] -= m * px * pz; tensor[6] -= m * px * pz
            tensor[5] -= m * py * pz; tensor[7] -= m * py * pz
        }
        vol = totalVol
        // shift the tensor to the centre of mass
        val cx = com.x; val cy = com.y; val cz = com.z
        val c2 = cx * cx + cy * cy + cz * cz
        unitInertia.set(
            tensor[0] - totalMass * (c2 - cx * cx),
            tensor[4] - totalMass * (c2 - cy * cy),
            tensor[8] - totalMass * (c2 - cz * cz)
        )
        boundRadius = r
    }

    private fun diagArr(v: Vec3, i: Int): Float = when (i) { 0 -> v.x; 1 -> v.y; else -> v.z }

    override val kind: ShapeKind get() = ShapeKind.COMPOUND
    override fun localAabb(): Aabb3 = box.copy()

    override fun supportLocal(dir: Vec3, out: Vec3): Vec3 {
        var best = -Float.MAX_VALUE
        val localDir = Vec3(); val cs = Vec3(); val ws = Vec3()
        var bx = 0f; var by = 0f; var bz = 0f
        for (c in children) {
            localDir.set(c.rotation.rotateInverse(dir))
            c.shape.supportLocal(localDir, cs)
            val rp = c.rotation.rotate(cs)
            ws.set(rp.x + c.position.x, rp.y + c.position.y, rp.z + c.position.z)
            val d = ws.dot(dir)
            if (d > best) { best = d; bx = ws.x; by = ws.y; bz = ws.z }
        }
        return out.set(bx, by, bz)
    }

    override fun volume(): Float = vol
    override fun localInertia(mass: Float, out: Vec3): Vec3 {
        if (vol < 1e-12f) return out.set(0f, 0f, 0f)
        val s = mass / vol
        return out.set(unitInertia.x * s, unitInertia.y * s, unitInertia.z * s)
    }
    override fun boundingRadius(): Float = boundRadius
    override fun toString(): String = "Compound(children=${children.size}, vol=$vol)"
}


/**
 * Factory for the shape hierarchy. Handy entry points for gameplay code and tests; all shapes are
 * centred on their local origin except [plane] and [heightfield].
 */
object Shapes3D {

    /** Sphere of [radius]. */
    fun sphere(radius: Float): SphereShape3D = SphereShape3D(radius)

    /** Box with half extents [hx],[hy],[hz]. */
    fun box(hx: Float, hy: Float, hz: Float): BoxShape3D = BoxShape3D(hx, hy, hz)

    /** Box from half extents. */
    fun box(halfExtents: Vec3): BoxShape3D = BoxShape3D(halfExtents)

    /** Box from full size. */
    fun boxFromSize(sx: Float, sy: Float, sz: Float): BoxShape3D = BoxShape3D(sx * 0.5f, sy * 0.5f, sz * 0.5f)

    /** Cube with the given full edge length. */
    fun cube(size: Float): BoxShape3D = BoxShape3D(size * 0.5f)

    /** Capsule with the given cap [radius] and cylinder half-length [halfHeight]. */
    fun capsule(radius: Float, halfHeight: Float): CapsuleShape3D = CapsuleShape3D(radius, halfHeight)

    /** Capsule whose total height (caps included) is [totalHeight]. */
    fun capsuleFromHeight(radius: Float, totalHeight: Float): CapsuleShape3D =
        CapsuleShape3D(radius, max(0f, totalHeight * 0.5f - radius))

    /** Cylinder with the given [radius] and cylinder [height] (caps excluded). */
    fun cylinder(radius: Float, height: Float): CylinderShape3D = CylinderShape3D(radius, height * 0.5f)

    /** Cylinder with the given [radius] and cylinder half-length [halfHeight]. */
    fun cylinderHalf(radius: Float, halfHeight: Float): CylinderShape3D = CylinderShape3D(radius, halfHeight)

    /** Convex hull of a point cloud (recentred on its centre of mass). */
    fun hull(points: List<Vec3>): ConvexHullShape3D = ConvexHullShape3D(points)

    /** Convex hull of an AABB's corners. */
    fun hullOfBox(halfExtents: Vec3): ConvexHullShape3D {
        val pts = ArrayList<Vec3>(8)
        for (i in 0 until 8) pts.add(Vec3(
            if (i and 1 == 0) -halfExtents.x else halfExtents.x,
            if (i and 2 == 0) -halfExtents.y else halfExtents.y,
            if (i and 4 == 0) -halfExtents.z else halfExtents.z
        ))
        return ConvexHullShape3D(pts)
    }

    /** Static triangle mesh (three indices per triangle). */
    fun mesh(vertices: Array<Vec3>, indices: IntArray): TriangleMeshShape3D = TriangleMeshShape3D(vertices, indices)

    /** Infinite plane `dot(normal, x) = offset`. */
    fun plane(normal: Vec3, offset: Float = 0f): PlaneShape3D = PlaneShape3D(normal, offset)

    /** Ground plane through the origin facing +Y. */
    fun groundPlane(): PlaneShape3D = PlaneShape3D(Vec3.UP, 0f)

    /** Heightfield (`heights` is row-major, `width*depth` samples). */
    fun heightfield(width: Int, depth: Int, scaleX: Float, scaleZ: Float, heights: FloatArray): HeightfieldShape3D =
        HeightfieldShape3D(width, depth, scaleX, scaleZ, heights)

    /** Compound of placed children. */
    fun compound(children: List<CompoundShape3D.Child>): CompoundShape3D = CompoundShape3D(children)

    /** Compound of shapes all placed at the origin. */
    fun compound(vararg shapes: Shape3D): CompoundShape3D =
        CompoundShape3D(shapes.map { CompoundShape3D.Child(it) })
}
