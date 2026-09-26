package com.sengine.engine.physics

import com.sengine.engine.core.Collider3D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.VoxelWorld
import com.sengine.engine.math.Mat4
import com.sengine.engine.voxel.Blocks
import com.sengine.engine.voxel.VoxelData
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Impulse-based 3D physics: axis-aligned boxes and spheres, sweep-and-prune broadphase,
 * iterative contact solver with friction and restitution, sleeping, triggers, and
 * collision against voxel worlds. Runs at a fixed 60 Hz step.
 */
class PhysicsWorld3D {
    var listener: PhysicsWorld.Listener? = null
    var iterations = 4
    private var accumulator = 0f
    private val fixedDt = 1f / 60f
    private var prevContacts = HashSet<Long>()
    private var prevTriggers = HashSet<Long>()
    /** Stats for the profiler. */
    var bodyCount = 0; private set
    var pairTests = 0; private set
    var contactCount = 0; private set

    private class Body(val go: GameObject, val rb: Rigidbody3D?, val col: Collider3D) {
        var cx = 0f; var cy = 0f; var cz = 0f
        var hx = 0f; var hy = 0f; var hz = 0f; var r = 0f
        val sphere get() = col.shape == 1
        val dynamic get() = rb != null && rb.bodyType == 0
        val invMass get() = if (rb == null || rb.bodyType != 0) 0f else 1f / rb.mass
        val sleeping get() = rb != null && rb.sleepTime > SLEEP_AFTER
        fun minX() = if (sphere) cx - r else cx - hx
        fun maxX() = if (sphere) cx + r else cx + hx
    }

    private class Contact(val a: Body, val b: Body, var nx: Float, var ny: Float, var nz: Float, var depth: Float)
    private class M(val nx: Float, val ny: Float, val nz: Float, val depth: Float)

    fun reset() { accumulator = 0f; prevContacts = HashSet(); prevTriggers = HashSet() }

    fun step(scene: Scene, dt: Float) {
        val waters = WaterPhysics.volumes(scene, 1)
        for (w in waters) w.tick(dt, scene.gravity3D.coerceAtMost(-4f))
        currentWaters = waters
        if (scene.objects.none { it.getAny<Collider3D>() != null || it.getAny<Rigidbody3D>() != null }) return
        accumulator += min(dt, 0.25f)
        var n = 0
        while (accumulator >= fixedDt && n < 5) { fixed(scene, fixedDt); accumulator -= fixedDt; n++ }
        if (n == 5) accumulator = 0f
    }

    private fun move(go: GameObject, dx: Float, dy: Float, dz: Float) {
        if (go.parent == null) { go.x += dx; go.y += dy; go.z += dz } else {
            val w = go.computeWorld3()
            go.setWorldPosition3(w[12] + dx, w[13] + dy, w[14] + dz)
        }
    }

    private fun voxelOf(scene: Scene): Pair<GameObject, VoxelData>? {
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val v = go.get<VoxelWorld>() ?: continue
            val d = v.data ?: continue
            return go to d
        }
        return null
    }

    private var currentWaters: List<com.sengine.engine.core.Water> = emptyList()

    private fun fixed(scene: Scene, dt: Float) {
        // integrate
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val rb = go.get<Rigidbody3D>() ?: continue
            rb.grounded = false
            when (rb.bodyType) {
                0 -> {
                    if (rb.sleepTime > SLEEP_AFTER) { rb.vx = 0f; rb.vy = 0f; rb.vz = 0f; rb.grounded = true; continue }
                    rb.vy += scene.gravity3D * rb.gravityScale * dt
                    if (currentWaters.isNotEmpty()) WaterPhysics.apply3D(currentWaters, go, rb, scene.gravity3D, dt)
                    if (rb.drag > 0f) { val k = max(0f, 1f - rb.drag * dt); rb.vx *= k; rb.vy *= k; rb.vz *= k }
                    move(go, rb.vx * dt, rb.vy * dt, rb.vz * dt)
                }
                1 -> move(go, rb.vx * dt, rb.vy * dt, rb.vz * dt)
                else -> {}
            }
        }
        val bodies = ArrayList<Body>()
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val col = go.get<Collider3D>() ?: continue
            val b = Body(go, go.get(), col); refresh(b); bodies.add(b)
        }
        bodyCount = bodies.size
        // voxel terrain
        val vox = voxelOf(scene)
        if (vox != null) for (b in bodies) if (b.dynamic && !b.col.isTrigger) collideVoxels(b, vox.first, vox.second)

        // broadphase: sweep and prune on X
        bodies.sortBy { it.minX() }
        val contacts = ArrayList<Contact>()
        val contactKeys = HashSet<Long>()
        val triggers = HashSet<Long>()
        var tests = 0
        for (i in bodies.indices) {
            val a = bodies[i]
            val aMax = a.maxX()
            for (j in i + 1 until bodies.size) {
                val b = bodies[j]
                if (b.minX() > aMax) break
                val trig = a.col.isTrigger || b.col.isTrigger
                if (!trig && a.invMass == 0f && b.invMass == 0f) continue
                if (!trig && (a.sleeping || a.invMass == 0f) && (b.sleeping || b.invMass == 0f)) continue
                tests++
                val m = collide(a, b) ?: continue
                val key = key(a.go.id, b.go.id)
                if (trig) {
                    triggers.add(key)
                    if (key !in prevTriggers) listener?.onTriggerEnter(a.go, b.go)
                    continue
                }
                contacts.add(Contact(a, b, m.nx, m.ny, m.nz, m.depth))
                if (contactKeys.add(key) && key !in prevContacts) listener?.onCollisionEnter(a.go, b.go)
            }
        }
        pairTests = tests; contactCount = contacts.size
        // wake sleeping bodies touched by moving ones
        for (c in contacts) {
            val am = c.a.rb?.let { speed2(it) > WAKE_SPEED2 } ?: false
            val bm = c.b.rb?.let { speed2(it) > WAKE_SPEED2 } ?: false
            if (am) c.b.rb?.sleepTime = 0f
            if (bm) c.a.rb?.sleepTime = 0f
        }
        // iterative solver
        for (it in 0 until iterations) {
            for (c in contacts) {
                if (it > 0) { refresh(c.a); refresh(c.b); val m = collide(c.a, c.b) ?: continue; c.nx = m.nx; c.ny = m.ny; c.nz = m.nz; c.depth = m.depth }
                resolve(c.a, c.b, M(c.nx, c.ny, c.nz, c.depth), it == 0)
            }
        }
        for (k in prevTriggers) if (k !in triggers) {
            val a = scene.findById(k shr 32); val b = scene.findById(k and 0xFFFFFFFFL)
            if (a != null && b != null) listener?.onTriggerExit(a, b)
        }
        prevContacts = contactKeys; prevTriggers = triggers
        // sleeping bookkeeping
        for (b in bodies) {
            val rb = b.rb ?: continue
            if (rb.bodyType != 0) continue
            if (speed2(rb) < SLEEP_SPEED2 && rb.grounded) rb.sleepTime += dt else rb.sleepTime = 0f
        }
    }

    private fun speed2(rb: Rigidbody3D) = rb.vx * rb.vx + rb.vy * rb.vy + rb.vz * rb.vz

    /** Wake a body (e.g. after a script changes its velocity). */
    fun wake(rb: Rigidbody3D) { rb.sleepTime = 0f }

    private fun collideVoxels(b: Body, vgo: GameObject, v: VoxelData) {
        val rb = b.rb ?: return
        val w = vgo.world3
        val ox = w[12]; val oy = w[13]; val oz = w[14]
        for (pass in 0 until 3) {
            refresh(b)
            val hx = if (b.sphere) b.r else b.hx; val hy = if (b.sphere) b.r else b.hy; val hz = if (b.sphere) b.r else b.hz
            val minX = floor(b.cx - hx - ox).toInt(); val maxX = ceil(b.cx + hx - ox).toInt() - 1
            val minY = floor(b.cy - hy - oy).toInt(); val maxY = ceil(b.cy + hy - oy).toInt() - 1
            val minZ = floor(b.cz - hz - oz).toInt(); val maxZ = ceil(b.cz + hz - oz).toInt() - 1
            var best: M? = null
            for (y in minY..maxY) for (z in minZ..maxZ) for (x in minX..maxX) {
                if (!Blocks.solid(v.get(x, y, z))) continue
                val bx = ox + x + 0.5f; val by = oy + y + 0.5f; val bz = oz + z + 0.5f
                val dx = b.cx - bx; val px = hx + 0.5f - abs(dx); if (px <= 0f) continue
                val dy = b.cy - by; val py = hy + 0.5f - abs(dy); if (py <= 0f) continue
                val dz = b.cz - bz; val pz = hz + 0.5f - abs(dz); if (pz <= 0f) continue
                // prefer pushing out along a face whose neighbour is free
                val m = when {
                    py <= px && py <= pz && !Blocks.solid(v.get(x, y + (if (dy > 0) 1 else -1), z)) -> M(0f, if (dy > 0) 1f else -1f, 0f, py)
                    px <= pz && !Blocks.solid(v.get(x + (if (dx > 0) 1 else -1), y, z)) -> M(if (dx > 0) 1f else -1f, 0f, 0f, px)
                    !Blocks.solid(v.get(x, y, z + (if (dz > 0) 1 else -1))) -> M(0f, 0f, if (dz > 0) 1f else -1f, pz)
                    else -> M(0f, 1f, 0f, py)
                }
                if (best == null || m.depth < best.depth || (m.ny > 0.5f && best.ny <= 0.5f && m.depth < 0.6f)) best = m
            }
            val m = best ?: return
            move(b.go, m.nx * m.depth, m.ny * m.depth, m.nz * m.depth)
            val vn = rb.vx * m.nx + rb.vy * m.ny + rb.vz * m.nz
            if (vn < 0f) { rb.vx -= vn * m.nx; rb.vy -= vn * m.ny; rb.vz -= vn * m.nz }
            if (m.ny > 0.5f) {
                rb.grounded = true
                val k = max(0f, 1f - rb.friction * 0.35f)
                rb.vx *= k; rb.vz *= k
            }
        }
    }

    private fun key(a: Long, b: Long): Long = (min(a, b) shl 32) or (max(a, b) and 0xFFFFFFFFL)

    private fun refresh(b: Body) {
        val w = b.go.computeWorld3()
        val p = Mat4.point(w, b.col.centerX, b.col.centerY, b.col.centerZ)
        b.cx = p[0]; b.cy = p[1]; b.cz = p[2]
        val sx = Mat4.scaleOf(w, 0); val sy = Mat4.scaleOf(w, 1); val sz = Mat4.scaleOf(w, 2)
        b.hx = b.col.sizeX * sx / 2; b.hy = b.col.sizeY * sy / 2; b.hz = b.col.sizeZ * sz / 2
        b.r = b.col.radius * max(sx, max(sy, sz))
    }

    private fun collide(a: Body, b: Body): M? = when {
        !a.sphere && !b.sphere -> boxBox(a, b)
        a.sphere && b.sphere -> sphereSphere(a, b)
        !a.sphere && b.sphere -> boxSphere(a, b)
        else -> boxSphere(b, a)?.let { M(-it.nx, -it.ny, -it.nz, it.depth) }
    }

    private fun boxBox(a: Body, b: Body): M? {
        val dx = b.cx - a.cx; val ox = a.hx + b.hx - abs(dx); if (ox <= 0f) return null
        val dy = b.cy - a.cy; val oy = a.hy + b.hy - abs(dy); if (oy <= 0f) return null
        val dz = b.cz - a.cz; val oz = a.hz + b.hz - abs(dz); if (oz <= 0f) return null
        return when {
            ox <= oy && ox <= oz -> M(if (dx < 0) -1f else 1f, 0f, 0f, ox)
            oy <= oz -> M(0f, if (dy < 0) -1f else 1f, 0f, oy)
            else -> M(0f, 0f, if (dz < 0) -1f else 1f, oz)
        }
    }

    private fun sphereSphere(a: Body, b: Body): M? {
        val dx = b.cx - a.cx; val dy = b.cy - a.cy; val dz = b.cz - a.cz
        val rs = a.r + b.r
        val d2 = dx * dx + dy * dy + dz * dz
        if (d2 >= rs * rs) return null
        val d = sqrt(d2)
        return if (d < 1e-5f) M(0f, 1f, 0f, rs) else M(dx / d, dy / d, dz / d, rs - d)
    }

    private fun boxSphere(box: Body, s: Body): M? {
        val px = s.cx.coerceIn(box.cx - box.hx, box.cx + box.hx)
        val py = s.cy.coerceIn(box.cy - box.hy, box.cy + box.hy)
        val pz = s.cz.coerceIn(box.cz - box.hz, box.cz + box.hz)
        val dx = s.cx - px; val dy = s.cy - py; val dz = s.cz - pz
        val d2 = dx * dx + dy * dy + dz * dz
        if (d2 > s.r * s.r) return null
        if (d2 < 1e-8f) {
            val ox = box.hx - abs(s.cx - box.cx); val oy = box.hy - abs(s.cy - box.cy); val oz = box.hz - abs(s.cz - box.cz)
            return when {
                ox <= oy && ox <= oz -> M(if (s.cx < box.cx) -1f else 1f, 0f, 0f, ox + s.r)
                oy <= oz -> M(0f, if (s.cy < box.cy) -1f else 1f, 0f, oy + s.r)
                else -> M(0f, 0f, if (s.cz < box.cz) -1f else 1f, oz + s.r)
            }
        }
        val d = sqrt(d2)
        return M(dx / d, dy / d, dz / d, s.r - d)
    }

    private fun vel(b: Body): FloatArray {
        val rb = b.rb
        return if (rb == null || rb.bodyType == 2) floatArrayOf(0f, 0f, 0f) else floatArrayOf(rb.vx, rb.vy, rb.vz)
    }

    private fun resolve(a: Body, b: Body, m: M, first: Boolean) {
        val ia = if (a.sleeping) 0f else a.invMass; val ib = if (b.sleeping) 0f else b.invMass
        val sum = ia + ib
        if (sum == 0f) return
        val corr = max(m.depth - 0.002f, 0f) / sum * (if (first) 0.8f else 0.4f)
        if (ia > 0f) move(a.go, -m.nx * corr * ia, -m.ny * corr * ia, -m.nz * corr * ia)
        if (ib > 0f) move(b.go, m.nx * corr * ib, m.ny * corr * ib, m.nz * corr * ib)
        if (m.ny < -0.5f) a.rb?.grounded = true
        if (m.ny > 0.5f) b.rb?.grounded = true
        val va = vel(a); val vb = vel(b)
        val rx = vb[0] - va[0]; val ry = vb[1] - va[1]; val rz = vb[2] - va[2]
        val vn = rx * m.nx + ry * m.ny + rz * m.nz
        if (vn > 0f) return
        val e = if (vn > -1f) 0f else max(a.rb?.bounciness ?: 0f, b.rb?.bounciness ?: 0f)
        val j = -(1f + e) * vn / sum
        impulse(a, ia, -j * m.nx, -j * m.ny, -j * m.nz)
        impulse(b, ib, j * m.nx, j * m.ny, j * m.nz)
        var tx = rx - vn * m.nx; var ty = ry - vn * m.ny; var tz = rz - vn * m.nz
        val tl = sqrt(tx * tx + ty * ty + tz * tz)
        if (tl < 1e-5f) return
        tx /= tl; ty /= tl; tz /= tl
        val mu = sqrt((a.rb?.friction ?: 0.5f) * (b.rb?.friction ?: 0.5f))
        val jt = (-(rx * tx + ry * ty + rz * tz) / sum).coerceIn(-j * mu, j * mu)
        impulse(a, ia, -jt * tx, -jt * ty, -jt * tz)
        impulse(b, ib, jt * tx, jt * ty, jt * tz)
    }

    private fun impulse(b: Body, inv: Float, x: Float, y: Float, z: Float) {
        if (inv == 0f) return
        val rb = b.rb ?: return
        rb.vx += x * inv; rb.vy += y * inv; rb.vz += z * inv
    }

    class RayHit(val go: GameObject?, val x: Float, val y: Float, val z: Float, val nx: Float, val ny: Float, val nz: Float, val distance: Float,
                 val blockX: Int = 0, val blockY: Int = 0, val blockZ: Int = 0, val block: Int = 0)

    /** Ray cast against colliders; returns the hit object or null. */
    fun raycast(scene: Scene, ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, maxDist: Float): GameObject? =
        raycastHit(scene, ox, oy, oz, dx, dy, dz, maxDist, false)?.go

    /** Full ray cast against colliders and (optionally) voxel terrain. Direction must be normalized. */
    fun raycastHit(scene: Scene, ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, maxDist: Float, voxels: Boolean = true, ignore: GameObject? = null): RayHit? {
        var best: RayHit? = null
        var bestT = maxDist
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            if (ignore != null && (go === ignore || ignore.isAncestorOf(go))) continue
            val col = go.get<Collider3D>() ?: continue
            if (col.isTrigger) continue
            val b = Body(go, null, col); refresh(b)
            val t = if (b.sphere) raySphere(ox, oy, oz, dx, dy, dz, b) else rayBox(ox, oy, oz, dx, dy, dz, b)
            if (t != null && t >= 0f && t < bestT) {
                bestT = t
                val hx = ox + dx * t; val hy = oy + dy * t; val hz = oz + dz * t
                var nx: Float; var ny: Float; var nz: Float
                if (b.sphere) { nx = (hx - b.cx) / b.r; ny = (hy - b.cy) / b.r; nz = (hz - b.cz) / b.r } else {
                    val ex = (hx - b.cx) / b.hx; val ey = (hy - b.cy) / b.hy; val ez = (hz - b.cz) / b.hz
                    nx = 0f; ny = 0f; nz = 0f
                    if (abs(ex) >= abs(ey) && abs(ex) >= abs(ez)) nx = if (ex > 0) 1f else -1f
                    else if (abs(ey) >= abs(ez)) ny = if (ey > 0) 1f else -1f else nz = if (ez > 0) 1f else -1f
                }
                best = RayHit(go, hx, hy, hz, nx, ny, nz, t)
            }
        }
        if (voxels) voxelOf(scene)?.let { (vgo, v) ->
            val w = vgo.world3
            val h = v.raycast(ox - w[12], oy - w[13], oz - w[14], dx, dy, dz, bestT)
            if (h != null && h.distance < bestT) {
                best = RayHit(vgo, ox + dx * h.distance, oy + dy * h.distance, oz + dz * h.distance, h.nx.toFloat(), h.ny.toFloat(), h.nz.toFloat(), h.distance, h.x, h.y, h.z, h.block)
            }
        }
        return best
    }

    private fun raySphere(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, b: Body): Float? {
        val lx = ox - b.cx; val ly = oy - b.cy; val lz = oz - b.cz
        val bb = lx * dx + ly * dy + lz * dz
        val c = lx * lx + ly * ly + lz * lz - b.r * b.r
        val disc = bb * bb - c
        if (disc < 0f) return null
        return -bb - sqrt(disc)
    }

    private fun rayBox(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, b: Body): Float? =
        rayAabb(ox, oy, oz, dx, dy, dz, b.cx - b.hx, b.cy - b.hy, b.cz - b.hz, b.cx + b.hx, b.cy + b.hy, b.cz + b.hz)

    companion object {
        const val SLEEP_AFTER = 0.8f
        const val SLEEP_SPEED2 = 0.02f
        const val WAKE_SPEED2 = 0.3f

        fun rayAabb(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float,
                    minX: Float, minY: Float, minZ: Float, maxX: Float, maxY: Float, maxZ: Float): Float? {
            var tmin = -Float.MAX_VALUE; var tmax = Float.MAX_VALUE
            val o = floatArrayOf(ox, oy, oz); val d = floatArrayOf(dx, dy, dz)
            val mn = floatArrayOf(minX, minY, minZ); val mx = floatArrayOf(maxX, maxY, maxZ)
            for (i in 0 until 3) {
                if (abs(d[i]) < 1e-8f) { if (o[i] < mn[i] || o[i] > mx[i]) return null } else {
                    var t1 = (mn[i] - o[i]) / d[i]; var t2 = (mx[i] - o[i]) / d[i]
                    if (t1 > t2) { val t = t1; t1 = t2; t2 = t }
                    tmin = max(tmin, t1); tmax = min(tmax, t2)
                    if (tmin > tmax) return null
                }
            }
            if (tmax < 0f) return null
            return if (tmin >= 0f) tmin else tmax
        }
    }
}
