package com.sengine.engine.physics

import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Scene
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Impulse-based 2D physics: axis-aligned boxes and circles, gravity, restitution,
 * friction, triggers and collision callbacks.
 *
 * v7 Pro: bodies are pooled (no per-step allocation), broadphase is a uniform
 * spatial hash grid (O(n) neighbourhood queries instead of O(n²) all-pairs) and
 * the solver exposes iteration count, pair/contact stats for the profiler.
 */
class PhysicsWorld {

    interface Listener {
        fun onCollisionEnter(a: GameObject, b: GameObject)
        fun onTriggerEnter(a: GameObject, b: GameObject)
        fun onTriggerExit(a: GameObject, b: GameObject)
    }

    var listener: Listener? = null
    private var accumulator = 0f
    private val fixedDt = 1f / 60f
    /** Physics sub-steps allowed per frame; excess accumulated time is dropped. */
    var maxSteps = 5

    /** Solver iterations per step (1 = classic behaviour, raise for taller stacks). */
    var iterations = 1
    /** Broadphase cell size override; 0 = automatic from body sizes. */
    var broadphaseCell = 0f
    /** Broadphase diagnostics for the profiler. */
    var bodyCount = 0; private set
    var pairTests = 0; private set
    var contactCount = 0; private set

    private class Body {
        lateinit var go: GameObject
        var rb: Rigidbody2D? = null
        lateinit var col: Collider2D
        var cx = 0f; var cy = 0f
        var hw = 0f; var hh = 0f; var r = 0f
        var slot = 0
        val isCircle get() = col.shape == 1
        val invMass: Float
            get() = if (rb == null || rb.bodyType != 0) 0f else 1f / rb.mass
        val minX get() = if (isCircle) cx - r else cx - hw
        val maxX get() = if (isCircle) cx + r else cx + hw
        val minY get() = if (isCircle) cy - r else cy - hh
        val maxY get() = if (isCircle) cy + r else cy + hh
    }

    private var prevContacts = HashSet<Long>()
    private var prevTriggers = HashSet<Long>()

    // pooled state
    private val bodies = ArrayList<Body>()
    private val bodyPool = ArrayDeque<Body>()

    // spatial hash broadphase (reused every step)
    private val grid = HashMap<Long, ArrayList<Body>>()
    private val gridBinPool = ArrayDeque<ArrayList<Body>>()
    private val bigBodies = ArrayList<Body>()
    private val candidatePairs = HashSet<Long>()
    private var autoCell = 2f

    fun reset() {
        accumulator = 0f
        prevContacts = HashSet(); prevTriggers = HashSet()
        releaseBodies()
    }

    private fun releaseBodies() {
        for (b in bodies) bodyPool.addLast(b)
        bodies.clear()
        for (bin in grid.values) { bin.clear(); gridBinPool.addLast(bin) }
        grid.clear()
        bigBodies.clear()
        candidatePairs.clear()
    }

    fun step(scene: Scene, dt: Float) {
        accumulator += min(dt, 0.25f)
        var steps = 0
        val cap = maxSteps.coerceIn(1, 10)
        while (accumulator >= fixedDt && steps < cap) {
            fixedStep(scene, fixedDt)
            accumulator -= fixedDt
            steps++
        }
        if (steps == cap) accumulator = 0f
    }

    private fun fixedStep(scene: Scene, dt: Float) {
        val waters = WaterPhysics.volumes(scene, 0)
        for (w in waters) w.tick(dt, scene.gravityY.coerceAtMost(-4f))
        // integrate
        val idx = scene.index
        val rbs = idx.rigidbodies2
        for (i in rbs.indices) {
            val rb = rbs[i]
            val go = rb.gameObject ?: continue
            if (!go.isActiveInHierarchy()) continue
            rb.grounded = false
            when (rb.bodyType) {
                0 -> {
                    if (waters.isNotEmpty()) WaterPhysics.apply2D(waters, go, rb, scene.gravityX, scene.gravityY, dt)
                    rb.vx += scene.gravityX * rb.gravityScale * dt
                    rb.vy += scene.gravityY * rb.gravityScale * dt
                    if (rb.drag > 0f) {
                        val k = max(0f, 1f - rb.drag * dt)
                        rb.vx *= k; rb.vy *= k
                    }
                    moveWorld(go, rb.vx * dt, rb.vy * dt)
                }
                1 -> moveWorld(go, rb.vx * dt, rb.vy * dt)
                else -> {}
            }
        }

        // gather bodies (pooled)
        var n = 0
        val cols = idx.colliders2
        for (i in cols.indices) {
            val col = cols[i]
            val go = col.gameObject ?: continue
            if (!go.isActiveInHierarchy()) continue
            val b = obtainBody(go, go.get(), col, n) ?: continue
            refresh(b); n++
        }
        trimBodies(n)
        bodyCount = n

        // ---- broadphase: uniform spatial hash with an oversized-body fast path
        candidatePairs.clear()
        bigBodies.clear()
        for (bin in grid.values) { bin.clear(); gridBinPool.addLast(bin) }
        grid.clear()
        pairTests = 0
        if (n > 1) {
            var extent = 0f
            for (i in 0 until n) {
                val b = bodies[i]
                extent += if (b.isCircle) b.r else max(b.hw, b.hh)
            }
            val avg = extent / n
            autoCell = if (broadphaseCell > 0f) broadphaseCell else (avg * 4f).coerceIn(0.5f, 24f)
            val inv = 1f / autoCell
            for (i in 0 until n) {
                val b = bodies[i]
                val x0 = floorDiv(b.minX * inv); val x1 = floorDiv(b.maxX * inv)
                val y0 = floorDiv(b.minY * inv); val y1 = floorDiv(b.maxY * inv)
                if ((x1 - x0 + 1) * (y1 - y0 + 1) > 16) { bigBodies.add(b); continue }
                var cx = x0
                while (cx <= x1) {
                    var cy = y0
                    while (cy <= y1) {
                        grid.getOrPut(cellKey(cx, cy)) { gridBinPool.removeLastOrNull() ?: ArrayList(8) }.add(b)
                        cy++
                    }
                    cx++
                }
            }
            for ((_, bin) in grid) {
                for (i in 0 until bin.size) for (j in i + 1 until bin.size) addCandidate(bin[i], bin[j])
            }
            for (big in bigBodies) for (i in 0 until n) if (bodies[i] !== big) addCandidate(big, bodies[i])
        }

        val contacts = HashSet<Long>()
        val triggers = HashSet<Long>()
        for (key in candidatePairs) {
            val a = bodies[((key shr 20) and 0xFFFFFL).toInt()]
            val b = bodies[(key and 0xFFFFFL).toInt()]
            if (a.invMass == 0f && b.invMass == 0f && !a.col.isTrigger && !b.col.isTrigger) continue
            pairTests++
            val m = collide(a, b) ?: continue
            val objKey = pairKey(a.go.id, b.go.id)
            if (a.col.isTrigger || b.col.isTrigger) {
                triggers.add(objKey)
                if (objKey !in prevTriggers) listener?.onTriggerEnter(a.go, b.go)
                continue
            }
            contacts.add(objKey)
            resolve(a, b, m)
            if (objKey !in prevContacts) listener?.onCollisionEnter(a.go, b.go)
        }
        for (k in prevTriggers) if (k !in triggers) {
            val a = scene.findById(k shr 32)
            val b = scene.findById(k and 0xFFFFFFFFL)
            if (a != null && b != null) listener?.onTriggerExit(a, b)
        }
        prevContacts = contacts
        prevTriggers = triggers
        contactCount = contacts.size
    }

    private fun obtainBody(go: GameObject, rb: Rigidbody2D?, col: Collider2D, at: Int): Body? {
        val b: Body
        if (at < bodies.size) {
            b = bodies[at]
        } else {
            b = bodyPool.removeLastOrNull() ?: Body()
            bodies.add(b)
        }
        b.go = go; b.rb = rb; b.col = col; b.slot = at
        return b
    }

    private fun trimBodies(n: Int) {
        while (bodies.size > n) bodyPool.addLast(bodies.removeAt(bodies.size - 1))
    }

    private fun addCandidate(a: Body, b: Body) {
        if (a.slot == b.slot) return
        val lo: Int; val hi: Int
        if (a.slot < b.slot) { lo = a.slot; hi = b.slot } else { lo = b.slot; hi = a.slot }
        if (hi >= 0xFFFFF) return // body count overflow guard (1M bodies)
        candidatePairs.add((lo.toLong() shl 20) or hi.toLong())
    }

    private fun cellKey(cx: Int, cy: Int): Long = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)

    private fun floorDiv(v: Float): Int {
        val i = v.toInt()
        return if (v < 0f && i.toFloat() != v) i - 1 else i
    }

    private fun pairKey(a: Long, b: Long): Long {
        val lo = min(a, b); val hi = max(a, b)
        return (lo shl 32) or (hi and 0xFFFFFFFFL)
    }

    private fun moveWorld(go: GameObject, dx: Float, dy: Float) {
        if (go.parent == null) {
            go.x += dx; go.y += dy
        } else {
            val w = go.computeWorld()
            go.setWorldPosition(w.tx + dx, w.ty + dy)
        }
    }

    private fun refresh(b: Body) {
        val w = b.go.computeWorld()
        val sx = w.scaleX
        val sy = w.scaleY
        b.cx = w.mapX(b.col.offsetX, b.col.offsetY)
        b.cy = w.mapY(b.col.offsetX, b.col.offsetY)
        b.hw = b.col.width * sx * 0.5f
        b.hh = b.col.height * sy * 0.5f
        b.r = b.col.radius * max(sx, sy)
    }

    private class Manifold(val nx: Float, val ny: Float, val depth: Float)

    /** Normal points from a to b. */
    private fun collide(a: Body, b: Body): Manifold? {
        return when {
            !a.isCircle && !b.isCircle -> boxBox(a, b)
            a.isCircle && b.isCircle -> circleCircle(a, b)
            !a.isCircle && b.isCircle -> boxCircle(a, b)
            else -> boxCircle(b, a)?.let { Manifold(-it.nx, -it.ny, it.depth) }
        }
    }

    private fun boxBox(a: Body, b: Body): Manifold? {
        val dx = b.cx - a.cx
        val ox = a.hw + b.hw - abs(dx)
        if (ox <= 0f) return null
        val dy = b.cy - a.cy
        val oy = a.hh + b.hh - abs(dy)
        if (oy <= 0f) return null
        return if (ox < oy) Manifold(if (dx < 0) -1f else 1f, 0f, ox)
        else Manifold(0f, if (dy < 0) -1f else 1f, oy)
    }

    private fun circleCircle(a: Body, b: Body): Manifold? {
        val dx = b.cx - a.cx
        val dy = b.cy - a.cy
        val rs = a.r + b.r
        val d2 = dx * dx + dy * dy
        if (d2 >= rs * rs) return null
        val d = sqrt(d2)
        return if (d < 1e-5f) Manifold(0f, 1f, rs) else Manifold(dx / d, dy / d, rs - d)
    }

    private fun boxCircle(box: Body, c: Body): Manifold? {
        val px = (c.cx).coerceIn(box.cx - box.hw, box.cx + box.hw)
        val py = (c.cy).coerceIn(box.cy - box.hh, box.cy + box.hh)
        var dx = c.cx - px
        var dy = c.cy - py
        val d2 = dx * dx + dy * dy
        if (d2 > c.r * c.r) return null
        if (d2 < 1e-8f) {
            // centre inside box: push out along smallest axis
            val ox = box.hw - abs(c.cx - box.cx)
            val oy = box.hh - abs(c.cy - box.cy)
            return if (ox < oy) Manifold(if (c.cx < box.cx) -1f else 1f, 0f, ox + c.r)
            else Manifold(0f, if (c.cy < box.cy) -1f else 1f, oy + c.r)
        }
        val d = sqrt(d2)
        dx /= d; dy /= d
        return Manifold(dx, dy, c.r - d)
    }

    private fun resolve(a: Body, b: Body, m: Manifold) {
        for (pass in 0 until iterations) {
            resolveOnce(a, b, m, pass > 0)
        }
    }

    private fun resolveOnce(a: Body, b: Body, m: Manifold, relax: Boolean) {
        val ia = a.invMass
        val ib = b.invMass
        val sum = ia + ib
        if (sum == 0f) return
        // positional correction
        val corr = max(m.depth - 0.001f, 0f) / sum * (if (relax) 0.45f else 0.9f)
        if (ia > 0f) moveWorld(a.go, -m.nx * corr * ia, -m.ny * corr * ia)
        if (ib > 0f) moveWorld(b.go, m.nx * corr * ib, m.ny * corr * ib)

        // grounded flags (normal a->b pointing down means a is on top of b)
        if (m.ny < -0.5f) a.rb?.grounded = true
        if (m.ny > 0.5f) b.rb?.grounded = true

        val avx = a.rb?.takeIf { it.bodyType != 2 }?.vx ?: 0f
        val avy = a.rb?.takeIf { it.bodyType != 2 }?.vy ?: 0f
        val bvx = b.rb?.takeIf { it.bodyType != 2 }?.vx ?: 0f
        val bvy = b.rb?.takeIf { it.bodyType != 2 }?.vy ?: 0f
        val rvx = bvx - avx
        val rvy = bvy - avy
        val vn = rvx * m.nx + rvy * m.ny
        if (vn > 0f) return
        val e = max(a.rb?.bounciness ?: 0f, b.rb?.bounciness ?: 0f)
        val j = -(1f + e) * vn / sum
        applyImpulse(a, -j * m.nx, -j * m.ny)
        applyImpulse(b, j * m.nx, j * m.ny)

        // friction
        val tx = -m.ny
        val ty = m.nx
        val vt = rvx * tx + rvy * ty
        val mu = sqrt((a.rb?.friction ?: 0.4f) * (b.rb?.friction ?: 0.4f))
        var jt = -vt / sum
        val maxF = j * mu
        jt = jt.coerceIn(-maxF, maxF)
        applyImpulse(a, -jt * tx, -jt * ty)
        applyImpulse(b, jt * tx, jt * ty)
    }

    private fun applyImpulse(b: Body, ix: Float, iy: Float) {
        val inv = b.invMass
        if (inv == 0f) return
        val rb = b.rb ?: return
        rb.vx += ix * inv
        rb.vy += iy * inv
    }

    /** Returns first active object whose collider contains the world point. */
    fun overlapPoint(scene: Scene, x: Float, y: Float): GameObject? {
        for (go in scene.objects.asReversed()) {
            if (!go.isActiveInHierarchy()) continue
            val col = go.get<Collider2D>() ?: continue
            val b = Body(go, null, col)
            refresh(b)
            val hit = if (b.isCircle) {
                val dx = x - b.cx; val dy = y - b.cy; dx * dx + dy * dy <= b.r * b.r
            } else abs(x - b.cx) <= b.hw && abs(y - b.cy) <= b.hh
            if (hit) return go
        }
        return null
    }

    class RayHit2D(val go: GameObject, val x: Float, val y: Float, val nx: Float, val ny: Float, val distance: Float)

    /** Casts a ray (unit direction) against 2D colliders (triggers ignored unless [triggers]); nearest hit or null. */
    fun raycast(scene: Scene, ox: Float, oy: Float, dx: Float, dy: Float, maxDist: Float, ignore: GameObject? = null, triggers: Boolean = false, tag: String = ""): RayHit2D? {
        var best: RayHit2D? = null
        var bestT = maxDist
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy() || go === ignore || go.destroyed) continue
            val col = go.get<Collider2D>() ?: continue
            if (col.isTrigger && !triggers) continue
            if (tag.isNotEmpty() && go.tag != tag) continue
            val b = Body(go, null, col)
            refresh(b)
            if (b.isCircle) {
                val fx = ox - b.cx; val fy = oy - b.cy
                val bq = fx * dx + fy * dy
                val c = fx * fx + fy * fy - b.r * b.r
                val disc = bq * bq - c
                if (disc < 0f) continue
                var t = -bq - kotlin.math.sqrt(disc)
                if (t < 0f) t = if (c < 0f) 0f else continue
                if (t < bestT) {
                    bestT = t
                    val hx = ox + dx * t; val hy = oy + dy * t
                    val l = kotlin.math.sqrt((hx - b.cx) * (hx - b.cx) + (hy - b.cy) * (hy - b.cy)).coerceAtLeast(1e-6f)
                    best = RayHit2D(go, hx, hy, (hx - b.cx) / l, (hy - b.cy) / l, t)
                }
            } else {
                var tmin = 0f; var tmax = bestT
                var nx = 0f; var ny = 0f
                var ok = true
                for (axis in 0..1) {
                    val o = if (axis == 0) ox else oy; val d = if (axis == 0) dx else dy
                    val lo = if (axis == 0) b.cx - b.hw else b.cy - b.hh; val hi = if (axis == 0) b.cx + b.hw else b.cy + b.hh
                    if (abs(d) < 1e-8f) { if (o < lo || o > hi) { ok = false; break }; continue }
                    var t1 = (lo - o) / d; var t2 = (hi - o) / d
                    var sgn = -1f
                    if (t1 > t2) { val tt = t1; t1 = t2; t2 = tt; sgn = 1f }
                    if (t1 > tmin) { tmin = t1; if (axis == 0) { nx = sgn; ny = 0f } else { nx = 0f; ny = sgn } }
                    if (t2 < tmax) tmax = t2
                    if (tmin > tmax) { ok = false; break }
                }
                if (ok && tmin < bestT) { bestT = tmin; best = RayHit2D(go, ox + dx * tmin, oy + dy * tmin, nx, ny, tmin) }
            }
        }
        return best
    }
}
