package com.sengine.engine.physics

import com.sengine.engine.math.Aabb3
import com.sengine.engine.math.Quat
import com.sengine.engine.math.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Result of a GJK query (boolean intersection or closest distance between two convex shapes). */
class GjkResult {
    var intersects = false
    var distance = 0f
    var iterations = 0
    val pointA = Vec3()
    val pointB = Vec3()
    /** Unit direction from A to B. */
    val normal = Vec3(0f, 1f, 0f)

    fun reset() {
        intersects = false; distance = 0f; iterations = 0
        pointA.zero(); pointB.zero(); normal.set(0f, 1f, 0f)
    }
}

/** Result of an EPA query: minimum translation depth and direction (A -> B). */
class EpaResult {
    var ok = false
    var depth = 0f
    var iterations = 0
    val normal = Vec3(0f, 1f, 0f)
    val pointA = Vec3()
    val pointB = Vec3()

    fun reset() { ok = false; depth = 0f; iterations = 0; normal.set(0f, 1f, 0f); pointA.zero(); pointB.zero() }
}

/**
 * Narrowphase: GJK distance/boolean, EPA penetration, SAT box/box manifolds, analytic
 * sphere/capsule/plane paths and BVH-accelerated mesh tests. One instance is single-threaded and
 * reuses scratch buffers, so the world keeps one per worker for parallel narrowphase.
 */
class Narrowphase3D {

    // ---- GJK simplex (Minkowski points + witness points on each shape) ----
    private val simplexW = Array(4) { Vec3() }
    private val simplexA = Array(4) { Vec3() }
    private val simplexB = Array(4) { Vec3() }
    private val tmpW = Vec3(); private val tmpA = Vec3(); private val tmpB = Vec3()
    private val tmpDir = Vec3(); private val tmp0 = Vec3(); private val tmp1 = Vec3(); private val tmp2 = Vec3()

    // ---- closest point on the current simplex ----
    private val cpClosest = Vec3()
    private var cpCount = 0
    private val cpIdx = IntArray(4)
    private val cpBary = FloatArray(4)
    private var cpInside = false
    private val cw = Array(4) { Vec3() }

    /** Minkowski support: `supportA(dir) - supportB(-dir)`. */
    private fun supportMinkowski(
        shapeA: Shape3D, posA: Vec3, rotA: Quat,
        shapeB: Shape3D, posB: Vec3, rotB: Quat,
        dir: Vec3, w: Vec3, pa: Vec3, pb: Vec3
    ) {
        supportWorld(shapeA, posA, rotA, dir, pa)
        tmpDir.set(-dir.x, -dir.y, -dir.z)
        supportWorld(shapeB, posB, rotB, tmpDir, pb)
        w.set(pa.x - pb.x, pa.y - pb.y, pa.z - pb.z)
    }

    /** World-space support point of a shape. */
    private fun supportWorld(shape: Shape3D, pos: Vec3, rot: Quat, dir: Vec3, out: Vec3): Vec3 {
        val local = rot.rotateInverse(dir)
        val sup = shape.supportLocal(local, tmp0)
        val w = rot.rotate(sup)
        return out.set(pos.x + w.x, pos.y + w.y, pos.z + w.z)
    }

    /**
     * GJK: fills [out] with the intersection flag or the closest distance/witness points.
     * Returns true when the shapes intersect.
     */
    fun gjk(
        shapeA: Shape3D, posA: Vec3, rotA: Quat,
        shapeB: Shape3D, posB: Vec3, rotB: Quat,
        out: GjkResult, maxIterations: Int = 48
    ): Boolean {
        out.reset()
        var dx = posB.x - posA.x; var dy = posB.y - posA.y; var dz = posB.z - posA.z
        if (dx * dx + dy * dy + dz * dz < 1e-12f) { dx = 1f; dy = 0f; dz = 0f }
        tmpDir.set(dx, dy, dz)
        supportMinkowski(shapeA, posA, rotA, shapeB, posB, rotB, tmpDir, simplexW[0], simplexA[0], simplexB[0])
        var count = 1
        cpClosest.set(simplexW[0])
        var lastDistSq = Float.MAX_VALUE
        var iter = 0
        while (iter < maxIterations) {
            iter++
            val d2 = cpClosest.lengthSq()
            if (d2 < 1e-12f) { out.intersects = true; break }
            if (d2 >= lastDistSq - 1e-12f * d2) break
            lastDistSq = d2
            tmpDir.set(-cpClosest.x, -cpClosest.y, -cpClosest.z)
            supportMinkowski(shapeA, posA, rotA, shapeB, posB, rotB, tmpDir, tmpW, tmpA, tmpB)
            if (tmpDir.dot(tmpW) < 0f) break // separated
            var dup = false
            for (i in 0 until count) if (simplexW[i].distanceSqTo(tmpW) < 1e-12f) { dup = true; break }
            if (dup) break
            simplexW[count].set(tmpW); simplexA[count].set(tmpA); simplexB[count].set(tmpB)
            count++
            closestPointOnSimplex(count)
            if (cpInside) { out.intersects = true; break }
            // reduce the simplex to the closest feature
            for (i in 0 until cpCount) {
                cw[i].set(simplexW[cpIdx[i]])
                simplexA[i].set(simplexA[cpIdx[i]])
                simplexB[i].set(simplexB[cpIdx[i]])
            }
            for (i in 0 until cpCount) simplexW[i].set(cw[i])
            count = cpCount
            cpClosest.set(cpClosest.x, cpClosest.y, cpClosest.z)
        }
        out.iterations = iter
        if (out.intersects) return true
        // distance + witness points from the current (reduced) simplex
        val pa = tmpA.zero(); val pb = tmpB.zero()
        for (i in 0 until count) {
            pa.addScaled(simplexA[i], cpBary[i])
            pb.addScaled(simplexB[i], cpBary[i])
        }
        out.pointA.set(pa); out.pointB.set(pb)
        val diff = tmp0.set(pb.x - pa.x, pb.y - pa.y, pb.z - pa.z)
        out.distance = diff.length()
        out.normal.set(diff.x, diff.y, diff.z)
        if (out.distance > 1e-9f) out.normal.scale(1f / out.distance)
        return false
    }

    /** Keeps only the vertices of the current simplex needed for the closest feature. */
    private fun closestPointOnSimplex(count: Int) {
        cpInside = false
        when (count) {
            1 -> { cpClosest.set(simplexW[0]); cpCount = 1; cpIdx[0] = 0; cpBary[0] = 1f }
            2 -> closestOnSegment()
            3 -> closestOnTriangle(0, 1, 2)
            else -> closestOnTetra()
        }
    }

    private fun closestOnSegment() {
        val a = simplexW[0]; val b = simplexW[1]
        val abx = b.x - a.x; val aby = b.y - a.y; val abz = b.z - a.z
        val denom = abx * abx + aby * aby + abz * abz
        val t = if (denom < 1e-20f) 0f else -(a.x * abx + a.y * aby + a.z * abz) / denom
        when {
            t <= 0f -> { cpClosest.set(a); cpCount = 1; cpIdx[0] = 0; cpBary[0] = 1f }
            t >= 1f -> { cpClosest.set(b); cpCount = 1; cpIdx[0] = 1; cpBary[0] = 1f }
            else -> {
                cpClosest.set(a.x + abx * t, a.y + aby * t, a.z + abz * t)
                cpCount = 2; cpIdx[0] = 0; cpIdx[1] = 1; cpBary[0] = 1f - t; cpBary[1] = t
            }
        }
    }

    private fun closestOnTriangle(i0: Int, i1: Int, i2: Int) {
        val a = simplexW[i0]; val b = simplexW[i1]; val c = simplexW[i2]
        val abx = b.x - a.x; val aby = b.y - a.y; val abz = b.z - a.z
        val acx = c.x - a.x; val acy = c.y - a.y; val acz = c.z - a.z
        val apx = -a.x; val apy = -a.y; val apz = -a.z
        val d1 = abx * apx + aby * apy + abz * apz
        val d2 = acx * apx + acy * apy + acz * apz
        if (d1 <= 0f && d2 <= 0f) { vertex(a, i0); return }
        val bpx = -b.x; val bpy = -b.y; val bpz = -b.z
        val d3 = abx * bpx + aby * bpy + abz * bpz
        val d4 = acx * bpx + acy * bpy + acz * bpz
        if (d3 >= 0f && d4 <= d3) { vertex(b, i1); return }
        val vc = d1 * d4 - d3 * d2
        if (vc <= 0f && d1 >= 0f && d3 <= 0f) {
            val v = d1 / (d1 - d3)
            cpClosest.set(a.x + abx * v, a.y + aby * v, a.z + abz * v)
            cpCount = 2; cpIdx[0] = i0; cpIdx[1] = i1; cpBary[0] = 1f - v; cpBary[1] = v
            return
        }
        val cpx = -c.x; val cpy = -c.y; val cpz = -c.z
        val d5 = abx * cpx + aby * cpy + abz * cpz
        val d6 = acx * cpx + acy * cpy + acz * cpz
        if (d6 >= 0f && d5 <= d6) { vertex(c, i2); return }
        val vb = d5 * d2 - d1 * d6
        if (vb <= 0f && d2 >= 0f && d6 <= 0f) {
            val w = d2 / (d2 - d6)
            cpClosest.set(a.x + acx * w, a.y + acy * w, a.z + acz * w)
            cpCount = 2; cpIdx[0] = i0; cpIdx[1] = i2; cpBary[0] = 1f - w; cpBary[1] = w
            return
        }
        val va = d3 * d6 - d5 * d4
        if (va <= 0f && (d4 - d3) >= 0f && (d5 - d6) >= 0f) {
            val w = (d4 - d3) / ((d4 - d3) + (d5 - d6))
            cpClosest.set(b.x + (c.x - b.x) * w, b.y + (c.y - b.y) * w, b.z + (c.z - b.z) * w)
            cpCount = 2; cpIdx[0] = i1; cpIdx[1] = i2; cpBary[0] = 1f - w; cpBary[1] = w
            return
        }
        val denom = 1f / (va + vb + vc)
        val v = vb * denom; val w = vc * denom
        cpClosest.set(a.x + abx * v + acx * w, a.y + aby * v + acy * w, a.z + abz * v + acz * w)
        cpCount = 3; cpIdx[0] = i0; cpIdx[1] = i1; cpIdx[2] = i2
        cpBary[0] = 1f - v - w; cpBary[1] = v; cpBary[2] = w
    }

    private fun vertex(p: Vec3, i: Int) {
        cpClosest.set(p); cpCount = 1; cpIdx[0] = i; cpBary[0] = 1f
    }

    private fun closestOnTetra() {
        val w = simplexW
        if (behind(w[0], w[1], w[2], w[3]) && behind(w[0], w[1], w[3], w[2]) &&
            behind(w[0], w[2], w[3], w[1]) && behind(w[1], w[2], w[3], w[0])) {
            cpInside = true; cpClosest.set(0f, 0f, 0f); cpCount = 0; return
        }
        val faces = arrayOf(intArrayOf(0, 1, 2), intArrayOf(0, 1, 3), intArrayOf(0, 2, 3), intArrayOf(1, 2, 3))
        var best = Float.MAX_VALUE
        val bClosest = Vec3(); var bCount = 0
        val bIdx = IntArray(4); val bBary = FloatArray(4)
        for (f in faces) {
            closestOnTriangle(f[0], f[1], f[2])
            val d = cpClosest.lengthSq()
            if (d < best) {
                best = d; bClosest.set(cpClosest); bCount = cpCount
                for (i in 0 until 4) { bIdx[i] = cpIdx[i]; bBary[i] = cpBary[i] }
            }
        }
        cpClosest.set(bClosest); cpCount = bCount
        for (i in 0 until 4) { cpIdx[i] = bIdx[i]; cpBary[i] = bBary[i] }
    }

    /** True when the origin is on the inner side of triangle (p0,p1,p2) whose outward side faces [opposite]. */
    private fun behind(p0: Vec3, p1: Vec3, p2: Vec3, opposite: Vec3): Boolean {
        val nx = (p1.y - p0.y) * (p2.z - p0.z) - (p1.z - p0.z) * (p2.y - p0.y)
        val ny = (p1.z - p0.z) * (p2.x - p0.x) - (p1.x - p0.x) * (p2.z - p0.z)
        val nz = (p1.x - p0.x) * (p2.y - p0.y) - (p1.y - p0.y) * (p2.x - p0.x)
        val side = nx * (opposite.x - p0.x) + ny * (opposite.y - p0.y) + nz * (opposite.z - p0.z)
        val s = if (side > 0f) -1f else 1f
        return s * (nx * (0f - p0.x) + ny * (0f - p0.y) + nz * (0f - p0.z)) <= 1e-9f
    }


    private class EpaFace(var a: Int, var b: Int, var c: Int) {
        val normal = Vec3()
        var dist = 0f
    }

    /**
     * EPA expansion of the current GJK simplex (which must contain the origin). Fills [out] with the
     * minimum penetration depth and the normal from A to B, plus witness points on each shape.
     * Call after [gjk] returned true.
     */
    fun epa(
        shapeA: Shape3D, posA: Vec3, rotA: Quat,
        shapeB: Shape3D, posB: Vec3, rotB: Quat,
        out: EpaResult, maxIterations: Int = 64
    ): Boolean {
        out.reset()
        val verts = ArrayList<Vec3>(64)
        val va = ArrayList<Vec3>(64)
        val vb = ArrayList<Vec3>(64)
        for (i in 0 until 4) { verts.add(simplexW[i].copy()); va.add(simplexA[i].copy()); vb.add(simplexB[i].copy()) }
        val centroid = Vec3()
        for (v in verts) centroid.add(v); centroid.scale(0.25f)
        val faces = ArrayList<EpaFace>(64)
        addFace(faces, verts, centroid, 0, 1, 2)
        addFace(faces, verts, centroid, 0, 1, 3)
        addFace(faces, verts, centroid, 0, 2, 3)
        addFace(faces, verts, centroid, 1, 2, 3)

        var iter = 0
        var lastFace = -1
        while (iter < maxIterations) {
            iter++
            var minDist = Float.MAX_VALUE; var minFace = -1
            for (i in faces.indices) { val f = faces[i]; if (f.dist < minDist) { minDist = f.dist; minFace = i } }
            if (minFace < 0) break
            lastFace = minFace
            val f = faces[minFace]
            supportMinkowski(shapeA, posA, rotA, shapeB, posB, rotB, f.normal, tmpW, tmpA, tmpB)
            val d = tmpW.dot(f.normal)
            if (d - f.dist < 1e-5f) break
            // horizon of faces visible from tmpW
            val visible = ArrayList<Int>()
            for (i in faces.indices) {
                val ff = faces[i]
                if (ff.normal.dot(tmpW) - ff.dist > 1e-6f) visible.add(i)
            }
            if (visible.isEmpty()) break
            val edgeSet = HashSet<Long>()
            for (i in visible) {
                val ff = faces[i]
                edgeSet.add(edgeKey(ff.a, ff.b)); edgeSet.add(edgeKey(ff.b, ff.c)); edgeSet.add(edgeKey(ff.c, ff.a))
            }
            val horizon = ArrayList<IntArray>()
            for (i in visible) {
                val ff = faces[i]
                if (!edgeSet.contains(edgeKey(ff.b, ff.a))) horizon.add(intArrayOf(ff.a, ff.b))
                if (!edgeSet.contains(edgeKey(ff.c, ff.b))) horizon.add(intArrayOf(ff.b, ff.c))
                if (!edgeSet.contains(edgeKey(ff.a, ff.c))) horizon.add(intArrayOf(ff.c, ff.a))
            }
            // remove visible faces (mark dist = MAX and skip)
            for (i in visible.sortedDescending()) faces.removeAt(i)
            verts.add(tmpW.copy()); va.add(tmpA.copy()); vb.add(tmpB.copy())
            val ni = verts.size - 1
            for (h in horizon) addFace(faces, verts, centroid, h[0], h[1], ni)
        }
        if (faces.isEmpty()) return false
        var minDist = Float.MAX_VALUE; var minFace = -1
        for (i in faces.indices) { val f = faces[i]; if (f.dist < minDist) { minDist = f.dist; minFace = i } }
        if (minFace < 0) return false
        val f = faces[minFace]
        out.ok = true
        out.iterations = iter
        out.depth = max(0f, f.dist)
        out.normal.set(f.normal)
        // witness points from the barycentric of the origin's projection onto the face
        simplexW[0].set(verts[f.a]); simplexW[1].set(verts[f.b]); simplexW[2].set(verts[f.c])
        closestOnTriangle(0, 1, 2)
        val pa = Vec3(); val pb = Vec3()
        for (i in 0 until cpCount) {
            val vi = when (cpIdx[i]) { 0 -> f.a; 1 -> f.b; else -> f.c }
            pa.addScaled(va[vi], cpBary[i])
            pb.addScaled(vb[vi], cpBary[i])
        }
        out.pointA.set(pa); out.pointB.set(pb)
        return true
    }

    private fun addFace(faces: ArrayList<EpaFace>, verts: ArrayList<Vec3>, centroid: Vec3, a: Int, b: Int, c: Int) {
        val face = EpaFace(a, b, c)
        val pa = verts[a]; val pb = verts[b]; val pc = verts[c]
        face.normal.set((pb - pa).cross(pc - pa))
        val len = face.normal.length()
        if (len < 1e-12f) return
        face.normal.scale(1f / len)
        if (face.normal.dot(pa - centroid) < 0f) {
            val t = face.b; face.b = face.c; face.c = t
            face.normal.set((verts[face.b] - pa).cross(verts[face.c] - pa))
            face.normal.scale(1f / face.normal.length())
        }
        face.dist = face.normal.dot(pa)
        faces.add(face)
    }

    private fun edgeKey(a: Int, b: Int): Long = (a.toLong() shl 32) or (b.toLong() and 0xFFFFFFFFL)


    // ------------------------------------------------------------------ manifold entry points

    /** Builds the contact manifold between two bodies. Returns true when they touch. */
    fun collide(a: RigidBodyPro3D, b: RigidBodyPro3D, out: Manifold3D): Boolean {
        out.clear()
        out.bodyA = a
        out.bodyB = b
        return collideShapes(a.shape, a.position, a.rotation, b.shape, b.position, b.rotation, out)
    }

    /** Dispatches on shape kinds; compounds recurse per child. */
    fun collideShapes(
        shapeA: Shape3D, posA: Vec3, rotA: Quat,
        shapeB: Shape3D, posB: Vec3, rotB: Quat, out: Manifold3D
    ): Boolean {
        if (shapeA is CompoundShape3D) {
            var touched = false
            val cp = Vec3(); val cr = Quat()
            for (c in shapeA.children) {
                childTransform(posA, rotA, c, cp, cr)
                if (collideShapes(c.shape, cp, cr, shapeB, posB, rotB, out)) touched = true
                if (out.points.size >= 4) break
            }
            return touched
        }
        if (shapeB is CompoundShape3D) {
            var touched = false
            val cp = Vec3(); val cr = Quat()
            for (c in shapeB.children) {
                childTransform(posB, rotB, c, cp, cr)
                if (collideShapes(shapeA, posA, rotA, c.shape, cp, cr, out)) touched = true
                if (out.points.size >= 4) break
            }
            return touched
        }
        val ka = shapeA.kind; val kb = shapeB.kind
        if (ka == ShapeKind.PLANE && kb == ShapeKind.PLANE) return false
        if (ka == ShapeKind.PLANE) return planeVsShape(shapeA as PlaneShape3D, posA, rotA, shapeB, posB, rotB, out, planeIsA = true)
        if (kb == ShapeKind.PLANE) return planeVsShape(shapeB as PlaneShape3D, posB, rotB, shapeA, posA, rotA, out, planeIsA = false)
        val aMesh = ka == ShapeKind.TRIANGLE_MESH || ka == ShapeKind.HEIGHTFIELD
        val bMesh = kb == ShapeKind.TRIANGLE_MESH || kb == ShapeKind.HEIGHTFIELD
        if (aMesh && bMesh) return meshMesh(shapeA, posA, rotA, shapeB, posB, rotB, out)
        if (aMesh) return meshVsShape(shapeA, posA, rotA, shapeB, posB, rotB, out, meshIsA = true)
        if (bMesh) return meshVsShape(shapeB, posB, rotB, shapeA, posA, rotA, out, meshIsA = false)

        if (ka == ShapeKind.SPHERE && kb == ShapeKind.SPHERE) return sphereSphere(posA, shapeA as SphereShape3D, posB, shapeB as SphereShape3D, out)
        if (ka == ShapeKind.SPHERE && kb == ShapeKind.CAPSULE) return sphereCapsule(posA, shapeA as SphereShape3D, posB, rotB, shapeB as CapsuleShape3D, out, sphereIsA = true)
        if (ka == ShapeKind.CAPSULE && kb == ShapeKind.SPHERE) return sphereCapsule(posB, shapeB as SphereShape3D, posA, rotA, shapeA as CapsuleShape3D, out, sphereIsA = false)
        if (ka == ShapeKind.CAPSULE && kb == ShapeKind.CAPSULE) return capsuleCapsule(posA, rotA, shapeA as CapsuleShape3D, posB, rotB, shapeB as CapsuleShape3D, out)
        if (ka == ShapeKind.BOX && kb == ShapeKind.BOX) return boxBox(posA, rotA, shapeA as BoxShape3D, posB, rotB, shapeB as BoxShape3D, out)
        return convexConvex(shapeA, posA, rotA, shapeB, posB, rotB, out)
    }

    private fun childTransform(pos: Vec3, rot: Quat, c: CompoundShape3D.Child, outPos: Vec3, outRot: Quat) {
        val rp = rot.rotate(c.position)
        outPos.set(pos.x + rp.x, pos.y + rp.y, pos.z + rp.z)
        outRot.set(rot.mul(c.rotation)).normalize()
    }

    private fun addPoint(out: Manifold3D, p: Vec3, n: Vec3, depth: Float, fid: Int) {
        if (out.points.size >= 4 || depth <= 0f) return
        val cp = ContactPoint3D()
        cp.position.set(p); cp.normal.set(n); cp.depth = depth; cp.featureId = fid
        out.points.add(cp)
    }

    /** Stable id from a local-space contact position (quantised to 1 cm). */
    fun featureIdFromPoint(p: Vec3): Int {
        val qx = Math.round(p.x * 100f); val qy = Math.round(p.y * 100f); val qz = Math.round(p.z * 100f)
        var h = 17
        h = h * 31 + qx; h = h * 31 + qy; h = h * 31 + qz
        return h
    }


    // ------------------------------------------------------------------ analytic paths

    private fun planeVsShape(
        plane: PlaneShape3D, planePos: Vec3, planeRot: Quat,
        shape: Shape3D, pos: Vec3, rot: Quat, out: Manifold3D, planeIsA: Boolean
    ): Boolean {
        val nW = planeRot.rotate(plane.normal)
        val dW = nW.dot(planePos) + plane.offset
        val sign = if (planeIsA) 1f else -1f
        val n = Vec3(nW.x * sign, nW.y * sign, nW.z * sign)
        when (shape.kind) {
            ShapeKind.SPHERE -> {
                val s = shape as SphereShape3D
                val dist = nW.dot(pos) - dW
                val depth = s.radius - dist
                if (depth <= 0f) return false
                val p = Vec3(pos.x - nW.x * s.radius, pos.y - nW.y * s.radius, pos.z - nW.z * s.radius)
                addPoint(out, p, n, depth, 0)
                return true
            }
            ShapeKind.BOX -> {
                val s = shape as BoxShape3D
                val corners = boxCorners(pos, rot, s)
                val hits = ArrayList<Int>()
                for (i in 0 until 8) if (nW.dot(corners[i]) - dW < 0f) hits.add(i)
                if (hits.isEmpty()) return false
                hits.sortByDescending { nW.dot(corners[it]) }
                for (i in 0 until min(4, hits.size)) {
                    val ci = hits[i]
                    val dist = nW.dot(corners[ci]) - dW
                    addPoint(out, corners[ci], n, -dist, ci + 1)
                }
                return true
            }
            ShapeKind.CAPSULE -> {
                val s = shape as CapsuleShape3D
                val p0 = capEnd(pos, rot, -s.halfHeight)
                val p1 = capEnd(pos, rot, s.halfHeight)
                var touched = false
                for (idx in 0..1) {
                    val e = if (idx == 0) p0 else p1
                    val dist = nW.dot(e) - dW
                    val depth = s.radius - dist
                    if (depth > 0f) {
                        val p = Vec3(e.x - nW.x * s.radius, e.y - nW.y * s.radius, e.z - nW.z * s.radius)
                        addPoint(out, p, n, depth, idx + 1); touched = true
                    }
                }
                return touched
            }
            else -> {
                val sup = supportWorld(shape, pos, rot, Vec3(-nW.x, -nW.y, -nW.z), tmp0)
                val dist = nW.dot(sup) - dW
                if (dist >= 0f) return false
                addPoint(out, sup.copy(), n, -dist, featureIdFromPoint(shapeLocal(shape, pos, rot, sup)))
                return true
            }
        }
    }

    private fun boxCorners(pos: Vec3, rot: Quat, box: BoxShape3D): Array<Vec3> {
        val hx = box.halfExtents.x; val hy = box.halfExtents.y; val hz = box.halfExtents.z
        return Array(8) { i ->
            val lx = if (i and 1 == 0) -hx else hx
            val ly = if (i and 2 == 0) -hy else hy
            val lz = if (i and 4 == 0) -hz else hz
            val r = rot.rotate(Vec3(lx, ly, lz))
            Vec3(pos.x + r.x, pos.y + r.y, pos.z + r.z)
        }
    }

    private fun shapeLocal(shape: Shape3D, pos: Vec3, rot: Quat, worldPoint: Vec3): Vec3 =
        rot.rotateInverse(Vec3(worldPoint.x - pos.x, worldPoint.y - pos.y, worldPoint.z - pos.z))

    private fun sphereSphere(posA: Vec3, a: SphereShape3D, posB: Vec3, b: SphereShape3D, out: Manifold3D): Boolean {
        val dx = posB.x - posA.x; val dy = posB.y - posA.y; val dz = posB.z - posA.z
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        if (dist > a.radius + b.radius) return false
        val n = if (dist > 1e-6f) Vec3(dx / dist, dy / dist, dz / dist) else Vec3(0f, 1f, 0f)
        val depth = a.radius + b.radius - dist
        val p = Vec3(posA.x + n.x * (a.radius - depth * 0.5f), posA.y + n.y * (a.radius - depth * 0.5f), posA.z + n.z * (a.radius - depth * 0.5f))
        addPoint(out, p, n, depth, 0)
        return true
    }


    private fun sphereCapsule(
        spherePos: Vec3, sphere: SphereShape3D, capPos: Vec3, capRot: Quat, cap: CapsuleShape3D,
        out: Manifold3D, sphereIsA: Boolean
    ): Boolean {
        val p0 = capEnd(capPos, capRot, -cap.halfHeight)
        val p1 = capEnd(capPos, capRot, cap.halfHeight)
        val q = closestPointOnSegment(spherePos, p0, p1, tmp1)
        val dx = q.x - spherePos.x; val dy = q.y - spherePos.y; val dz = q.z - spherePos.z
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        if (dist > sphere.radius + cap.radius) return false
        val n = if (dist > 1e-6f) Vec3(dx / dist, dy / dist, dz / dist) else Vec3(0f, 1f, 0f)
        val depth = sphere.radius + cap.radius - dist
        val p = Vec3(spherePos.x + n.x * (sphere.radius - depth * 0.5f), spherePos.y + n.y * (sphere.radius - depth * 0.5f), spherePos.z + n.z * (sphere.radius - depth * 0.5f))
        val nn = if (sphereIsA) n else Vec3(-n.x, -n.y, -n.z)
        addPoint(out, p, nn, depth, 0)
        return true
    }

    private fun capsuleCapsule(posA: Vec3, rotA: Quat, a: CapsuleShape3D, posB: Vec3, rotB: Quat, b: CapsuleShape3D, out: Manifold3D): Boolean {
        val a0 = capEnd(posA, rotA, -a.halfHeight); val a1 = capEnd(posA, rotA, a.halfHeight)
        val b0 = capEnd(posB, rotB, -b.halfHeight); val b1 = capEnd(posB, rotB, b.halfHeight)
        val pa = Vec3(); val pb = Vec3()
        closestPointsOnSegments(a0, a1, b0, b1, pa, pb)
        val dx = pb.x - pa.x; val dy = pb.y - pa.y; val dz = pb.z - pa.z
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        if (dist > a.radius + b.radius) return false
        val n = if (dist > 1e-6f) Vec3(dx / dist, dy / dist, dz / dist) else Vec3(0f, 1f, 0f)
        val depth = a.radius + b.radius - dist
        val p = Vec3(pa.x + n.x * (a.radius - depth * 0.5f), pa.y + n.y * (a.radius - depth * 0.5f), pa.z + n.z * (a.radius - depth * 0.5f))
        addPoint(out, p, n, depth, featureIdFromPoint(shapeLocal(a, posA, rotA, pa)))
        return true
    }

    private fun capEnd(pos: Vec3, rot: Quat, y: Float): Vec3 {
        val r = rot.rotate(Vec3(0f, y, 0f))
        return Vec3(pos.x + r.x, pos.y + r.y, pos.z + r.z)
    }

    private fun closestPointOnSegment(p: Vec3, a: Vec3, b: Vec3, out: Vec3): Vec3 {
        val abx = b.x - a.x; val aby = b.y - a.y; val abz = b.z - a.z
        val denom = abx * abx + aby * aby + abz * abz
        val t = if (denom < 1e-20f) 0f else ((p.x - a.x) * abx + (p.y - a.y) * aby + (p.z - a.z) * abz) / denom
        val tc = t.coerceIn(0f, 1f)
        return out.set(a.x + abx * tc, a.y + aby * tc, a.z + abz * tc)
    }

    /** Closest points between two segments [a0,a1] and [b0,b1] (Ericson). */
    private fun closestPointsOnSegments(a0: Vec3, a1: Vec3, b0: Vec3, b1: Vec3, outA: Vec3, outB: Vec3) {
        val d1x = a1.x - a0.x; val d1y = a1.y - a0.y; val d1z = a1.z - a0.z
        val d2x = b1.x - b0.x; val d2y = b1.y - b0.y; val d2z = b1.z - b0.z
        val rx = a0.x - b0.x; val ry = a0.y - b0.y; val rz = a0.z - b0.z
        val a = d1x * d1x + d1y * d1y + d1z * d1z
        val e = d2x * d2x + d2y * d2y + d2z * d2z
        val f = d2x * rx + d2y * ry + d2z * rz
        var s: Float; var t: Float
        if (a <= 1e-12f && e <= 1e-12f) { s = 0f; t = 0f }
        else if (a <= 1e-12f) { s = 0f; t = (f / e).coerceIn(0f, 1f) }
        else {
            val c = d1x * rx + d1y * ry + d1z * rz
            if (e <= 1e-12f) { t = 0f; s = (-c / a).coerceIn(0f, 1f) }
            else {
                val b = d1x * d2x + d1y * d2y + d1z * d2z
                val denom = a * e - b * b
                s = if (denom > 1e-12f) ((b * f - c * e) / denom).coerceIn(0f, 1f) else 0f
                t = (b * s + f) / e
                if (t < 0f) { t = 0f; s = (-c / a).coerceIn(0f, 1f) }
                else if (t > 1f) { t = 1f; s = ((b - c) / a).coerceIn(0f, 1f) }
            }
        }
        outA.set(a0.x + d1x * s, a0.y + d1y * s, a0.z + d1z * s)
        outB.set(b0.x + d2x * t, b0.y + d2y * t, b0.z + d2z * t)
    }


    // ------------------------------------------------------------------ box / box (SAT + clipping)

    private fun axesOf(rot: Quat): Array<Vec3> {
        val m = rot.toMat3()
        return arrayOf(Vec3(m[0], m[3], m[6]), Vec3(m[1], m[4], m[7]), Vec3(m[2], m[5], m[8]))
    }

    private fun boxBox(cA: Vec3, rotA: Quat, boxA: BoxShape3D, cB: Vec3, rotB: Quat, boxB: BoxShape3D, out: Manifold3D): Boolean {
        val uA = axesOf(rotA); val uB = axesOf(rotB)
        val hA = floatArrayOf(boxA.halfExtents.x, boxA.halfExtents.y, boxA.halfExtents.z)
        val hB = floatArrayOf(boxB.halfExtents.x, boxB.halfExtents.y, boxB.halfExtents.z)
        val absR = FloatArray(9)
        for (i in 0 until 3) for (j in 0 until 3) absR[i * 3 + j] = abs(uA[i].dot(uB[j])) + 1e-6f
        val t = Vec3(cB.x - cA.x, cB.y - cA.y, cB.z - cA.z)
        val tA = floatArrayOf(t.dot(uA[0]), t.dot(uA[1]), t.dot(uA[2]))
        val tB = floatArrayOf(t.dot(uB[0]), t.dot(uB[1]), t.dot(uB[2]))

        var bestPen = Float.MAX_VALUE
        var faceAxis = -1
        var axisVec = Vec3(0f, 1f, 0f)
        for (i in 0 until 3) {
            val ra = hA[i]
            val rb = hB[0] * absR[i * 3] + hB[1] * absR[i * 3 + 1] + hB[2] * absR[i * 3 + 2]
            val pen = ra + rb - abs(tA[i])
            if (pen <= 0f) return false
            if (pen < bestPen) { bestPen = pen; faceAxis = i; axisVec.set(if (tA[i] >= 0f) uA[i] else Vec3(-uA[i].x, -uA[i].y, -uA[i].z)) }
        }
        for (j in 0 until 3) {
            val ra = hA[0] * absR[j] + hA[1] * absR[3 + j] + hA[2] * absR[6 + j]
            val rb = hB[j]
            val pen = ra + rb - abs(tB[j])
            if (pen <= 0f) return false
            if (pen < bestPen) { bestPen = pen; faceAxis = 3 + j; axisVec.set(if (tB[j] >= 0f) uB[j] else Vec3(-uB[j].x, -uB[j].y, -uB[j].z)) }
        }
        for (i in 0 until 3) for (j in 0 until 3) {
            val axis = uA[i].cross(uB[j])
            val len = axis.length()
            if (len < 1e-6f) continue
            axis.scale(1f / len)
            val ra = hA[(i + 1) % 3] * absR[((i + 2) % 3) * 3 + j] + hA[(i + 2) % 3] * absR[((i + 1) % 3) * 3 + j]
            val rb = hB[(j + 1) % 3] * absR[i * 3 + (j + 2) % 3] + hB[(j + 2) % 3] * absR[i * 3 + (j + 1) % 3]
            val pen = ra + rb - abs(t.dot(axis))
            if (pen <= 0f) return false
            if (pen < bestPen) {
                bestPen = pen; faceAxis = -1
                axisVec.set(if (t.dot(axis) >= 0f) axis else Vec3(-axis.x, -axis.y, -axis.z))
            }
        }
        val n = axisVec.normalizedOr(Vec3.UP)

        if (faceAxis < 0) {
            val pa = supportWorld(boxA, cA, rotA, Vec3(-n.x, -n.y, -n.z), tmp0)
            val pb = supportWorld(boxB, cB, rotB, n, tmp1)
            val p = Vec3((pa.x + pb.x) * 0.5f, (pa.y + pb.y) * 0.5f, (pa.z + pb.z) * 0.5f)
            addPoint(out, p, n, bestPen, featureIdFromPoint(shapeLocal(boxB, cB, rotB, p)))
            return true
        }

        val refA = faceAxis < 3
        val refIndex = if (refA) faceAxis else faceAxis - 3
        val refC = if (refA) cA else cB
        val refAxes = if (refA) uA else uB
        val refHalf = if (refA) hA else hB
        val incC = if (refA) cB else cA
        val incAxes = if (refA) uB else uA
        val incHalf = if (refA) hB else hA
        val refNormal = if (refA) n else Vec3(-n.x, -n.y, -n.z)

        var incIndex = 0; var bestDot = Float.MAX_VALUE
        for (k in 0 until 3) { val d = incAxes[k].dot(refNormal); if (d < bestDot) { bestDot = d; incIndex = k } }
        val refFaceCenter = Vec3(refC.x + refNormal.x * refHalf[refIndex], refC.y + refNormal.y * refHalf[refIndex], refC.z + refNormal.z * refHalf[refIndex])
        val refPlaneOffset = refNormal.dot(refFaceCenter)
        val i0 = (incIndex + 1) % 3; val i1 = (incIndex + 2) % 3
        val incNormal = incAxes[incIndex]
        val incFaceCenter = Vec3(incC.x + incNormal.x * incHalf[incIndex], incC.y + incNormal.y * incHalf[incIndex], incC.z + incNormal.z * incHalf[incIndex])
        val quad = ArrayList<Vec3>(8)
        val sxs = intArrayOf(1, -1, -1, 1); val sys = intArrayOf(1, 1, -1, -1)
        for (ci in 0 until 4) {
            val sx = sxs[ci]; val sy = sys[ci]

            quad.add(Vec3(
                incFaceCenter.x + incAxes[i0].x * incHalf[i0] * sx + incAxes[i1].x * incHalf[i1] * sy,
                incFaceCenter.y + incAxes[i0].y * incHalf[i0] * sx + incAxes[i1].y * incHalf[i1] * sy,
                incFaceCenter.z + incAxes[i0].z * incHalf[i0] * sx + incAxes[i1].z * incHalf[i1] * sy
            ))
        }
        clipBoxFace(quad, refAxes, refIndex, refHalf, refFaceCenter, refNormal, refPlaneOffset, n, boxB, cB, rotB, bestPen, out)
        return true
    }


    private fun clipBoxFace(
        quad: MutableList<Vec3>, refAxes: Array<Vec3>, refIndex: Int, refHalf: FloatArray,
        refFaceCenter: Vec3, refNormal: Vec3, refPlaneOffset: Float,
        n: Vec3, boxB: BoxShape3D, cB: Vec3, rotB: Quat, bestPen: Float, out: Manifold3D
    ) {
        val t0 = refAxes[(refIndex + 1) % 3]; val t1 = refAxes[(refIndex + 2) % 3]
        val ht0 = refHalf[(refIndex + 1) % 3]; val ht1 = refHalf[(refIndex + 2) % 3]
        clipHalfSpace(quad, t0, t0.dot(refFaceCenter) + ht0)
        clipHalfSpace(quad, Vec3(-t0.x, -t0.y, -t0.z), -t0.dot(refFaceCenter) + ht0)
        clipHalfSpace(quad, t1, t1.dot(refFaceCenter) + ht1)
        clipHalfSpace(quad, Vec3(-t1.x, -t1.y, -t1.z), -t1.dot(refFaceCenter) + ht1)

        var any = false
        for (v in quad) {
            val sep = refNormal.dot(v) - refPlaneOffset
            val depth = -sep
            if (depth > -1e-4f) {
                val p = Vec3(v.x - refNormal.x * sep, v.y - refNormal.y * sep, v.z - refNormal.z * sep)
                addPoint(out, p, n, max(1e-5f, depth), featureIdFromPoint(shapeLocal(boxB, cB, rotB, p)))
                any = true
                if (out.points.size >= 4) break
            }
        }
        if (!any) {
            val p = Vec3(refFaceCenter.x, refFaceCenter.y, refFaceCenter.z)
            addPoint(out, p, n, bestPen, featureIdFromPoint(shapeLocal(boxB, cB, rotB, p)))
        }
    }

    /** Sutherland–Hodgman clip of [poly] against the half-space `dot(n, p) <= offset`. */
    private fun clipHalfSpace(poly: MutableList<Vec3>, n: Vec3, offset: Float) {
        if (poly.isEmpty()) return
        val res = ArrayList<Vec3>(poly.size + 2)
        val size = poly.size
        for (i in 0 until size) {
            val cur = poly[i]; val nxt = poly[(i + 1) % size]
            val dc = n.dot(cur) - offset; val dn = n.dot(nxt) - offset
            if (dc <= 0f) res.add(cur)
            if ((dc < 0f && dn > 0f) || (dc > 0f && dn < 0f)) {
                val t = dc / (dc - dn)
                res.add(Vec3(cur.x + (nxt.x - cur.x) * t, cur.y + (nxt.y - cur.y) * t, cur.z + (nxt.z - cur.z) * t))
            }
        }
        poly.clear(); poly.addAll(res)
    }

    // ------------------------------------------------------------------ convex / convex (GJK + EPA)

    private fun convexConvex(
        shapeA: Shape3D, posA: Vec3, rotA: Quat,
        shapeB: Shape3D, posB: Vec3, rotB: Quat, out: Manifold3D
    ): Boolean {
        val g = GjkResult()
        if (!gjk(shapeA, posA, rotA, shapeB, posB, rotB, g)) return false
        val e = EpaResult()
        if (!epa(shapeA, posA, rotA, shapeB, posB, rotB, e)) return false
        if (e.depth <= 0f) return false
        val p = Vec3((e.pointA.x + e.pointB.x) * 0.5f, (e.pointA.y + e.pointB.y) * 0.5f, (e.pointA.z + e.pointB.z) * 0.5f)
        addPoint(out, p, e.normal, e.depth, featureIdFromPoint(shapeLocal(shapeB, posB, rotB, p)))
        return true
    }


    // ------------------------------------------------------------------ mesh collisions (BVH + triangles)

    /** Minimal convex shape wrapping a triangle for the GJK/EPA path. */
    private class PointCloudShape(private val pts: Array<Vec3>) : Shape3D {
        override val kind: ShapeKind get() = ShapeKind.CONVEX_HULL
        override fun localAabb(): Aabb3 = Aabb3.fromPoints(pts.toList())
        override fun supportLocal(dir: Vec3, out: Vec3): Vec3 {
            var best = -Float.MAX_VALUE; var bi = 0
            for (i in pts.indices) { val d = pts[i].dot(dir); if (d > best) { best = d; bi = i } }
            return out.set(pts[bi])
        }
        override fun volume(): Float = 0f
        override fun localInertia(mass: Float, out: Vec3): Vec3 = out.set(0f, 0f, 0f)
        override fun boundingRadius(): Float { var r = 0f; for (p in pts) r = max(r, p.length()); return r }
    }

    private fun meshBvh(shape: Shape3D): Bvh3D? = when (shape) {
        is TriangleMeshShape3D -> shape.bvh
        is HeightfieldShape3D -> shape.bvh
        else -> null
    }

    private fun worldAabbOf(shape: Shape3D, pos: Vec3, rot: Quat, out: Aabb3): Aabb3 {
        val la = shape.localAabb()
        out.reset()
        for (i in 0 until 8) {
            val lx = if (i and 1 == 0) la.min.x else la.max.x
            val ly = if (i and 2 == 0) la.min.y else la.max.y
            val lz = if (i and 4 == 0) la.min.z else la.max.z
            val r = rot.rotate(Vec3(lx, ly, lz))
            out.expand(pos.x + r.x, pos.y + r.y, pos.z + r.z)
        }
        return out
    }

    private val triA = Vec3(); private val triB = Vec3(); private val triC = Vec3()
    private val triQ = Vec3(); private val triR = Vec3(); private val triS = Vec3()

    private fun meshVsShape(
        meshShape: Shape3D, meshPos: Vec3, meshRot: Quat,
        shape: Shape3D, pos: Vec3, rot: Quat, out: Manifold3D, meshIsA: Boolean
    ): Boolean {
        val bvh = meshBvh(meshShape) ?: return false
        val wa = worldAabbOf(shape, pos, rot, Aabb3())
        val la = Aabb3()
        for (i in 0 until 8) {
            val wx = if (i and 1 == 0) wa.min.x else wa.max.x
            val wy = if (i and 2 == 0) wa.min.y else wa.max.y
            val wz = if (i and 4 == 0) wa.min.z else wa.max.z
            la.expand(meshRot.rotateInverse(Vec3(wx - meshPos.x, wy - meshPos.y, wz - meshPos.z)))
        }
        val candidates = ArrayList<Int>()
        bvh.queryAabb(la, candidates)
        candidates.sort()
        var touched = false
        for (t in candidates) {
            bvh.triangle(t, triA, triB, triC)
            worldTri(meshPos, meshRot, triA, triB, triC, triQ, triR, triS)
            if (shapeVsTriangle(shape, pos, rot, triQ, triR, triS, t, out, meshIsA)) touched = true
            if (out.points.size >= 4) break
        }
        return touched
    }

    private fun worldTri(meshPos: Vec3, meshRot: Quat, a: Vec3, b: Vec3, c: Vec3, oa: Vec3, ob: Vec3, oc: Vec3) {
        val ra = meshRot.rotate(a); oa.set(meshPos.x + ra.x, meshPos.y + ra.y, meshPos.z + ra.z)
        val rb = meshRot.rotate(b); ob.set(meshPos.x + rb.x, meshPos.y + rb.y, meshPos.z + rb.z)
        val rc = meshRot.rotate(c); oc.set(meshPos.x + rc.x, meshPos.y + rc.y, meshPos.z + rc.z)
    }


    /** Contact between a convex shape and one world-space triangle. */
    private fun shapeVsTriangle(shape: Shape3D, pos: Vec3, rot: Quat, a: Vec3, b: Vec3, c: Vec3, tri: Int, out: Manifold3D, meshIsA: Boolean): Boolean {
        val sign = if (meshIsA) 1f else -1f
        // EPA normal points shape -> triangle, so flip when the mesh is body A
        val signConv = if (meshIsA) -1f else 1f
        when (shape.kind) {
            ShapeKind.SPHERE -> {
                val s = shape as SphereShape3D
                Bvh3D.closestPointTriangle(pos, a, b, c, tmp1)
                val dx = pos.x - tmp1.x; val dy = pos.y - tmp1.y; val dz = pos.z - tmp1.z
                val dist = sqrt(dx * dx + dy * dy + dz * dz)
                if (dist > s.radius) return false
                val depth = s.radius - dist
                val n = if (dist > 1e-6f) Vec3(dx / dist, dy / dist, dz / dist) else triangleNormal(a, b, c)
                val p = Vec3(pos.x - n.x * s.radius, pos.y - n.y * s.radius, pos.z - n.z * s.radius)
                addPoint(out, p, Vec3(n.x * sign, n.y * sign, n.z * sign), depth, tri + 1)
                return true
            }
            ShapeKind.CAPSULE -> {
                val s = shape as CapsuleShape3D
                val e0 = capEnd(pos, rot, -s.halfHeight); val e1 = capEnd(pos, rot, s.halfHeight)
                val mid = Vec3((e0.x + e1.x) * 0.5f, (e0.y + e1.y) * 0.5f, (e0.z + e1.z) * 0.5f)
                Bvh3D.closestPointTriangle(mid, a, b, c, tmp1)
                val q = closestPointOnSegment(tmp1, e0, e1, tmp2)
                val dx = q.x - tmp1.x; val dy = q.y - tmp1.y; val dz = q.z - tmp1.z
                val dist = sqrt(dx * dx + dy * dy + dz * dz)
                if (dist > s.radius) return false
                val depth = s.radius - dist
                val n = if (dist > 1e-6f) Vec3(dx / dist, dy / dist, dz / dist) else triangleNormal(a, b, c)
                val p = Vec3(q.x - n.x * s.radius, q.y - n.y * s.radius, q.z - n.z * s.radius)
                addPoint(out, p, Vec3(n.x * sign, n.y * sign, n.z * sign), depth, tri + 1)
                return true
            }
            else -> {
                val triShape = PointCloudShape(arrayOf(a.copy(), b.copy(), c.copy()))
                val g = GjkResult()
                if (!gjk(shape, pos, rot, triShape, Vec3.ZERO, Quat(), g)) return false
                val e = EpaResult()
                if (!epa(shape, pos, rot, triShape, Vec3.ZERO, Quat(), e)) return false
                if (e.depth <= 0f) return false
                val p = Vec3((e.pointA.x + e.pointB.x) * 0.5f, (e.pointA.y + e.pointB.y) * 0.5f, (e.pointA.z + e.pointB.z) * 0.5f)
                addPoint(out, p, Vec3(e.normal.x * signConv, e.normal.y * signConv, e.normal.z * signConv), e.depth, tri + 1)
                return true
            }
        }
    }

    private fun triangleNormal(a: Vec3, b: Vec3, c: Vec3): Vec3 = (b - a).cross(c - a).normalizedOr(Vec3.UP)


    // ------------------------------------------------------------------ mesh / mesh (triangle SAT)

    private fun meshMesh(
        shapeA: Shape3D, posA: Vec3, rotA: Quat, shapeB: Shape3D, posB: Vec3, rotB: Quat, out: Manifold3D
    ): Boolean {
        val bvhA = meshBvh(shapeA) ?: return false
        val bvhB = meshBvh(shapeB) ?: return false
        val wa = worldAabbOf(shapeA, posA, rotA, Aabb3())
        val la = Aabb3()
        for (i in 0 until 8) {
            val wx = if (i and 1 == 0) wa.min.x else wa.max.x
            val wy = if (i and 2 == 0) wa.min.y else wa.max.y
            val wz = if (i and 4 == 0) wa.min.z else wa.max.z
            la.expand(rotB.rotateInverse(Vec3(wx - posB.x, wy - posB.y, wz - posB.z)))
        }
        val candB = ArrayList<Int>()
        bvhB.queryAabb(la, candB)
        candB.sort()
        if (candB.isEmpty()) return false
        val a0 = Vec3(); val a1 = Vec3(); val a2 = Vec3()
        val b0 = Vec3(); val b1 = Vec3(); val b2 = Vec3()
        val ta = Aabb3()
        var touched = false
        val candA = ArrayList<Int>()
        for (tb in candB) {
            bvhB.triangle(tb, b0, b1, b2)
            worldTri(posB, rotB, b0, b1, b2, triQ, triR, triS)
            ta.reset(); ta.expand(triQ); ta.expand(triR); ta.expand(triS)
            val laA = Aabb3()
            for (i in 0 until 8) {
                val wx = if (i and 1 == 0) ta.min.x else ta.max.x
                val wy = if (i and 2 == 0) ta.min.y else ta.max.y
                val wz = if (i and 4 == 0) ta.min.z else ta.max.z
                laA.expand(rotA.rotateInverse(Vec3(wx - posA.x, wy - posA.y, wz - posA.z)))
            }
            candA.clear()
            bvhA.queryAabb(laA, candA)
            candA.sort()
            for (tai in candA) {
                bvhA.triangle(tai, a0, a1, a2)
                worldTri(posA, rotA, a0, a1, a2, triA, triB, triC)
                if (triTriContact(triA, triB, triC, triQ, triR, triS, out, tai)) touched = true
                if (out.points.size >= 4) return true
            }
        }
        return touched
    }

    /** Triangle/triangle SAT: tests both face normals and the 9 edge cross products. */
    private fun triTriContact(a0: Vec3, a1: Vec3, a2: Vec3, b0: Vec3, b1: Vec3, b2: Vec3, out: Manifold3D, tri: Int): Boolean {
        val axes = ArrayList<Vec3>(12)
        val na = (a1 - a0).cross(a2 - a0)
        val nb = (b1 - b0).cross(b2 - b0)
        if (na.lengthSq() > 1e-12f) axes.add(na.normalized())
        if (nb.lengthSq() > 1e-12f) axes.add(nb.normalized())
        val ea = arrayOf(a1 - a0, a2 - a1, a0 - a2)
        val eb = arrayOf(b1 - b0, b2 - b1, b0 - b2)
        for (i in 0 until 3) for (j in 0 until 3) {
            val x = ea[i].cross(eb[j])
            if (x.lengthSq() > 1e-12f) axes.add(x.normalized())
        }
        val ca = Vec3((a0.x + a1.x + a2.x) / 3f, (a0.y + a1.y + a2.y) / 3f, (a0.z + a1.z + a2.z) / 3f)
        val cb = Vec3((b0.x + b1.x + b2.x) / 3f, (b0.y + b1.y + b2.y) / 3f, (b0.z + b1.z + b2.z) / 3f)
        val dir = Vec3(cb.x - ca.x, cb.y - ca.y, cb.z - ca.z)
        var bestOverlap = Float.MAX_VALUE
        val bestAxis = Vec3(0f, 1f, 0f)
        for (axis in axes) {
            val pa = projectTri(a0, a1, a2, axis)
            val pb = projectTri(b0, b1, b2, axis)
            val overlap = min(pa[1], pb[1]) - max(pa[0], pb[0])
            if (overlap <= 0f) return false
            if (overlap < bestOverlap) {
                bestOverlap = overlap
                bestAxis.set(if (dir.dot(axis) >= 0f) axis else Vec3(-axis.x, -axis.y, -axis.z))
            }
        }
        val p = Vec3((ca.x + cb.x) * 0.5f, (ca.y + cb.y) * 0.5f, (ca.z + cb.z) * 0.5f)
        addPoint(out, p, bestAxis, bestOverlap, tri + 1)
        return true
    }

    private fun projectTri(a: Vec3, b: Vec3, c: Vec3, axis: Vec3): FloatArray {
        val da = a.dot(axis); val db = b.dot(axis); val dc = c.dot(axis)
        return floatArrayOf(min(da, min(db, dc)), max(da, max(db, dc)))
    }
}

