package com.sengine.engine.physics

import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.DistanceJoint2D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.RevoluteJoint2D
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Scene
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Lightweight impulse-based 2D physics: axis-aligned boxes and circles,
 * gravity, restitution, friction, triggers and collision callbacks.
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

    private class Body(
        val go: GameObject,
        val rb: Rigidbody2D?,
        val col: Collider2D,
        var cx: Float = 0f, var cy: Float = 0f,
        var hw: Float = 0f, var hh: Float = 0f, var r: Float = 0f
    ) {
        val isCircle get() = col.shape == 1
        val invMass: Float
            get() = if (rb == null || rb.bodyType != 0) 0f else 1f / rb.mass
    }

    private var prevContacts = HashSet<Long>()
    private var prevTriggers = HashSet<Long>()

    fun reset() {
        revPins.clear()
        accumulator = 0f
        prevContacts = HashSet(); prevTriggers = HashSet()
    }

    fun step(scene: Scene, dt: Float) {
        accumulator += min(dt, 0.25f)
        var steps = 0
        while (accumulator >= fixedDt && steps < 5) {
            fixedStep(scene, fixedDt)
            accumulator -= fixedDt
            steps++
        }
        if (steps == 5) accumulator = 0f
    }

    private fun fixedStep(scene: Scene, dt: Float) {
        val waters = WaterPhysics.volumes(scene, 0)
        for (w in waters) w.tick(dt, scene.gravityY.coerceAtMost(-4f))
        // integrate
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val rb = go.get<Rigidbody2D>() ?: continue
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

        // gather bodies
        val bodies = ArrayList<Body>()
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val col = go.get<Collider2D>() ?: continue
            val b = Body(go, go.get(), col)
            refresh(b)
            bodies.add(b)
        }

        val contacts = HashSet<Long>()
        val triggers = HashSet<Long>()
        for (i in 0 until bodies.size) {
            for (j in i + 1 until bodies.size) {
                val a = bodies[i]
                val b = bodies[j]
                if (a.invMass == 0f && b.invMass == 0f && !a.col.isTrigger && !b.col.isTrigger) continue
                val m = collide(a, b) ?: continue
                // v7 one-way platforms: only solid when approached from above (normal along +Y for the dynamic body)
                if (a.invMass == 0f && b.invMass != 0f && a.col.oneWay && m.ny < 0.5f) continue
                if (b.invMass == 0f && a.invMass != 0f && b.col.oneWay && m.ny > -0.5f) continue
                val key = pairKey(a.go.id, b.go.id)
                if (a.col.isTrigger || b.col.isTrigger) {
                    triggers.add(key)
                    if (key !in prevTriggers) listener?.onTriggerEnter(a.go, b.go)
                    continue
                }
                contacts.add(key)
                resolve(a, b, m)
                if (key !in prevContacts) listener?.onCollisionEnter(a.go, b.go)
            }
        }
        for (k in prevTriggers) if (k !in triggers) {
            val a = scene.findById(k shr 32)
            val b = scene.findById(k and 0xFFFFFFFFL)
            if (a != null && b != null) listener?.onTriggerExit(a, b)
        }
        prevContacts = contacts
        prevTriggers = triggers
        solveJoints2D(scene, dt)
    }

    // ================================================================================ joints (v7)
    private val revPins = HashMap<Int, FloatArray>()

    private fun bodyOf(scene: Scene, go: GameObject): Body? {
        val col = go.get<Collider2D>() ?: return null
        val b = Body(go, go.get(), col); refresh(b); return b
    }

    /** Distance joints (ropes, pendulums) and revolute joints (wheels, swings, ragdoll pivots). */
    private fun solveJoints2D(scene: Scene, dt: Float) {
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val dj = go.get<DistanceJoint2D>()
            if (dj != null && dj.enabled) {
                val other = scene.find(dj.target)
                if (other != null) {
                    val a = go.computeWorld(); val b = other.computeWorld()
                    val dx = b.tx - a.tx; val dy = b.ty - a.ty
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1e-5f)
                    val err = dist - dj.distance
                    if (abs(err) > 1e-4f) {
                        val nx = dx / dist; val ny = dy / dist
                        val k = dj.stiffness * 60f * dt
                        val ra = go.get<Rigidbody2D>(); val rb = other.get<Rigidbody2D>()
                        val wa = ra != null && ra.bodyType == 0; val wb = rb != null && rb.bodyType == 0
                        val split = (if (wa) 1f else 0f) + (if (wb) 1f else 0f)
                        if (split > 0f) {
                            val ca = err / dist * k * (if (wa) 1f / split else 0f)
                            val cb = err / dist * k * (if (wb) 1f / split else 0f)
                            if (wa) moveWorld(go, nx * ca, ny * ca)
                            if (wb) moveWorld(other, -nx * cb, -ny * cb)
                            if (wa && ra != null) { ra.vx += nx * err * 4f * k; ra.vy += ny * err * 4f * k }
                            if (wb && rb != null) { rb.vx -= nx * err * 4f * k; rb.vy -= ny * err * 4f * k }
                        }
                    }
                }
            }
            val rj = go.get<RevoluteJoint2D>()
            if (rj != null && rj.enabled) {
                val rb = go.get<Rigidbody2D>()
                if (rb != null && rb.bodyType == 0) {
                    val key = System.identityHashCode(rj)
                    val a = go.computeWorld()
                    val px = a.tx + (a.a * rj.pivotX + a.c * rj.pivotY)
                    val py = a.ty + (a.b * rj.pivotX + a.d * rj.pivotY)
                    val anchor = revPins.getOrPut(key) { floatArrayOf(px, py) }
                    val other = scene.find(rj.target)
                    // keep the pivot at its anchor: remove radial velocity so the body orbits freely
                    val dx = a.tx - anchor[0]; val dy = a.ty - anchor[1]
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (dist > 1e-4f) {
                        val nx = dx / dist; val ny = dy / dist
                        val vr = rb.vx * nx + rb.vy * ny
                        rb.vx -= nx * vr; rb.vy -= ny * vr
                        // spring back if drifted
                        rb.vx -= nx * dist * 30f * dt; rb.vy -= ny * dist * 30f * dt
                    }
                    if (rj.motorSpeed != 0f) go.rotation += rj.motorSpeed * dt
                    if (other != null) {
                        val orb = other.get<Rigidbody2D>()
                        if (orb != null && orb.bodyType == 0 && rj.motorSpeed != 0f && rj.maxTorque > 0f) {
                            // drive the connected body tangentially (belt / wheel on axle)
                            val ox = other.computeWorld()
                            val tdx = ox.tx - a.tx; val tdy = ox.ty - a.ty
                            val tl = kotlin.math.sqrt(tdx * tdx + tdy * tdy).coerceAtLeast(1e-5f)
                            val tx = -tdy / tl; val ty = tdx / tl
                            val sgn = if (rj.motorSpeed > 0f) 1f else -1f
                            orb.vx += tx * rj.motorSpeed * 0.03f * rj.maxTorque * sgn * dt * 60f * 0.02f
                            orb.vy += ty * rj.motorSpeed * 0.03f * rj.maxTorque * sgn * dt * 60f * 0.02f
                        }
                    }
                }
            }
        }
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
        val ia = a.invMass
        val ib = b.invMass
        val sum = ia + ib
        if (sum == 0f) return
        // positional correction
        val corr = max(m.depth - 0.001f, 0f) / sum * 0.9f
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
