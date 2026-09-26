package com.sengine.engine.physics

import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.Collider3D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.Water
import com.sengine.engine.math.Mat4
import kotlin.math.abs
import kotlin.math.max

/** Buoyancy, drag, currents and splashes for [Water] volumes (2D and 3D). */
object WaterPhysics {

    fun volumes(scene: Scene, mode: Int): List<Water> {
        var out: ArrayList<Water>? = null
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val w = go.get<Water>() ?: continue
            if (w.mode != mode) continue
            if (mode == 0) {
                val t = go.computeWorld()
                val hw = w.width * abs(t.scaleX) / 2; val hh = w.height * abs(t.scaleY) / 2
                w.x0 = t.tx - hw; w.x1 = t.tx + hw; w.bottom = t.ty - hh; w.top = t.ty + hh
            } else {
                val m = go.computeWorld3()
                val hw = w.width * Mat4.scaleOf(m, 0) / 2; val hd = w.depth * Mat4.scaleOf(m, 2) / 2
                w.x0 = m[12] - hw; w.x1 = m[12] + hw; w.z0 = m[14] - hd; w.z1 = m[14] + hd; w.top = m[13]
            }
            if (out == null) out = ArrayList()
            out.add(w)
        }
        return out ?: emptyList()
    }

    /** Applies water forces to a 2D body before integration. */
    fun apply2D(waters: List<Water>, go: GameObject, rb: Rigidbody2D, gx: Float, gy: Float, dt: Float) {
        val before = rb.submerged
        rb.submerged = 0f
        if (waters.isEmpty()) return
        val t = go.computeWorld()
        val col = go.get<Collider2D>()
        val half = if (col == null) 0.5f * abs(t.scaleY) else if (col.shape == 1) col.radius * max(abs(t.scaleX), abs(t.scaleY)) else col.height * abs(t.scaleY) / 2
        val cx = t.tx; val cy = t.ty
        for (w in waters) {
            if (cx < w.x0 || cx > w.x1 || cy - half > w.top + w.waveHeight + 0.5f || cy + half < w.bottom) continue
            val surface = w.surfaceAt(cx)
            val frac = ((surface - (cy - half)) / (2 * half).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
            if (frac <= 0f) continue
            rb.submerged = max(rb.submerged, frac)
            // buoyancy opposes gravity; density 1 = neutral when fully submerged
            rb.vx -= gx * rb.gravityScale * w.density * frac * dt
            rb.vy -= gy * rb.gravityScale * w.density * frac * dt
            val k = max(0f, 1f - w.drag * frac * dt)
            rb.vx = rb.vx * k + w.flowX * frac * dt * 2f
            rb.vy *= k
            if (before <= 0.05f && abs(rb.vy) > 1.2f) w.splash(cx, rb.vy)
            // gentle bobbing with the waves at the surface
            if (frac < 1f) rb.vy += (w.wave(cx) - 0f) * 0.8f * dt
        }
    }

    fun apply3D(waters: List<Water>, go: GameObject, rb: Rigidbody3D, g: Float, dt: Float) {
        val before = rb.submerged
        rb.submerged = 0f
        if (waters.isEmpty()) return
        val m = go.computeWorld3()
        val col = go.get<Collider3D>()
        val sy = Mat4.scaleOf(m, 1)
        val half = if (col == null) 0.5f * sy else if (col.shape == 1) col.radius * sy else col.sizeY * sy / 2
        val cx = m[12]; val cy = m[13]; val cz = m[14]
        for (w in waters) {
            if (cx < w.x0 || cx > w.x1 || cz < w.z0 || cz > w.z1 || cy - half > w.top + w.waveHeight + 0.5f) continue
            val surface = w.surfaceAt(cx, cz)
            val frac = ((surface - (cy - half)) / (2 * half).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
            if (frac <= 0f) continue
            rb.submerged = max(rb.submerged, frac)
            rb.vy -= g * rb.gravityScale * w.density * frac * dt
            val k = max(0f, 1f - w.drag * frac * dt)
            rb.vx = rb.vx * k + w.flowX * frac * dt * 2f
            rb.vz = rb.vz * k + w.flowZ * frac * dt * 2f
            rb.vy *= k
            if (before <= 0.05f && abs(rb.vy) > 1.5f) w.splash(cx, rb.vy, cz)
        }
    }
}
