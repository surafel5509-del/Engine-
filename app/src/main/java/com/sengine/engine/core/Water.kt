package com.sengine.engine.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Water volume with buoyancy, drag, currents, rolling waves, spring-simulated ripples and splashes.
 *
 * Mode 0 (2D side view): a [width] × [height] rectangle centred on the object (scaled by the object's scale);
 *   its surface is the top edge. Dynamic Rigidbody2D bodies float according to [density].
 * Mode 1 (3D volume): a [width] × [depth] area in the XZ plane whose surface is the object's world Y.
 *   Rigidbody3D bodies below the surface float; the surface is drawn as an animated low-poly sheet.
 *
 * Scripts: `self.getProp("Water", "Wave Height")`, and bodies can read how submerged they are with
 * `obj.getProp("Rigidbody2D", "Submerged")` (0…1).
 */
class Water : Component() {
    override val type = "Water"
    var mode = 0
    var width = 12f
    var height = 4f
    var depth = 12f
    var color = 0x991E88E5.toInt()
    var deepColor = 0xCC0D2F6B.toInt()
    var surfaceColor = 0xFFE1F5FE.toInt()
    var density = 1.6f
    var drag = 2.2f
    var flowX = 0f
    var flowZ = 0f
    var waveHeight = 0.12f
    var waveSpeed = 1.6f
    var waveLength = 3f
    var ripples = true
    var splashes = true
    var detail = 48

    // ---------------------------------------------------------------- runtime
    var time = 0f
    var heights = FloatArray(0)
    var velocities = FloatArray(0)
    val drops = ArrayList<Drop>()
    class Drop(var x: Float, var y: Float, var z: Float, var vx: Float, var vy: Float, var vz: Float, var age: Float, val life: Float, val size: Float)

    /** World-space bounds, refreshed by the physics step (2D: x0..x1 / bottom..top; 3D: x0..x1, z0..z1, top). */
    var x0 = 0f; var x1 = 0f; var bottom = 0f; var top = 0f; var z0 = 0f; var z1 = 0f

    override fun props() = listOf(
        Prop.Choice("Mode", MODES, { mode }, { mode = it }),
        Prop.F("Width", { width }, { width = it.coerceAtLeast(0.1f) }),
        Prop.F("Height", { height }, { height = it.coerceAtLeast(0.1f) }),
        Prop.F("Depth (3D)", { depth }, { depth = it.coerceAtLeast(0.1f) }),
        Prop.Color("Color", { color }, { color = it }),
        Prop.Color("Deep Color", { deepColor }, { deepColor = it }),
        Prop.Color("Surface Color", { surfaceColor }, { surfaceColor = it }),
        Prop.F("Density", { density }, { density = it.coerceAtLeast(0f) }),
        Prop.F("Drag", { drag }, { drag = it.coerceAtLeast(0f) }),
        Prop.F("Flow X", { flowX }, { flowX = it }),
        Prop.F("Flow Z", { flowZ }, { flowZ = it }),
        Prop.F("Wave Height", { waveHeight }, { waveHeight = it.coerceAtLeast(0f) }, 0.02f),
        Prop.F("Wave Speed", { waveSpeed }, { waveSpeed = it }),
        Prop.F("Wave Length", { waveLength }, { waveLength = it.coerceAtLeast(0.2f) }),
        Prop.B("Ripples", { ripples }, { ripples = it }),
        Prop.B("Splashes", { splashes }, { splashes = it }),
        Prop.I("Detail", { detail }, { detail = it.coerceIn(4, 128) }),
    )

    override fun resetRuntime() {
        time = 0f; heights = FloatArray(0); velocities = FloatArray(0); drops.clear()
    }

    private fun ensureColumns() {
        val n = detail.coerceIn(4, 128) + 1
        if (heights.size != n) { heights = FloatArray(n); velocities = FloatArray(n) }
    }

    /** Rolling wave offset at world x (and z in 3D). */
    fun wave(x: Float, z: Float = 0f): Float {
        if (waveHeight <= 0f) return 0f
        val k = (2 * PI / waveLength).toFloat()
        return waveHeight * (sin(x * k + time * waveSpeed * 2f) * 0.65f + sin(x * k * 0.53f - time * waveSpeed * 1.3f + z * k * 0.8f) * 0.35f)
    }

    private fun column(x: Float): Int {
        if (heights.isEmpty() || x1 <= x0) return -1
        val t = (x - x0) / (x1 - x0)
        if (t < 0f || t > 1f) return -1
        return (t * (heights.size - 1)).toInt().coerceIn(0, heights.size - 1)
    }

    /** Surface height at world x (includes waves and ripples). */
    fun surfaceAt(x: Float, z: Float = 0f): Float {
        val c = column(x)
        return top + wave(x, z) + (if (c >= 0 && mode == 0) heights[c] else 0f)
    }

    fun contains(x: Float, y: Float, z: Float = 0f): Boolean =
        if (mode == 0) x in x0..x1 && y in bottom..surfaceAt(x)
        else x in x0..x1 && z in z0..z1 && y <= surfaceAt(x, z) && y >= top - 60f

    /** Disturbs the surface at x and throws droplets. strength ≈ impact speed. */
    fun splash(x: Float, strength: Float, z: Float = 0f) {
        val s = abs(strength).coerceAtMost(14f)
        if (ripples && mode == 0) {
            ensureColumns()
            val c = column(x)
            if (c >= 0) {
                for (d in -2..2) {
                    val i = c + d
                    if (i in velocities.indices) velocities[i] -= s * 0.35f * (1f - abs(d) * 0.3f)
                }
            }
        }
        if (splashes) {
            val n = (s * 2.2f).toInt().coerceIn(2, 26)
            val y = surfaceAt(x, z)
            for (i in 0 until n) {
                if (drops.size > 220) break
                val a = (PI / 2 + (Random.nextFloat() - 0.5f) * 2.1f).toFloat()
                val sp = s * (0.25f + Random.nextFloat() * 0.35f)
                drops.add(Drop(x + (Random.nextFloat() - 0.5f) * 0.4f, y, z + (Random.nextFloat() - 0.5f) * 0.4f,
                    kotlin.math.cos(a) * sp, sin(a) * sp, (Random.nextFloat() - 0.5f) * sp * 0.6f,
                    0f, 0.5f + Random.nextFloat() * 0.5f, 0.06f + Random.nextFloat() * 0.08f))
            }
        }
    }

    /** Advances waves, ripple springs and droplets. Called once per physics step. */
    fun tick(dt: Float, gravity: Float) {
        time += dt
        if (mode == 0 && ripples) {
            ensureColumns()
            val n = heights.size
            val k = 38f; val damp = 3.2f; val spread = 0.22f
            for (i in 0 until n) {
                velocities[i] += (-k * heights[i] - damp * velocities[i]) * dt
                heights[i] += velocities[i] * dt
            }
            for (pass in 0 until 2) for (i in 0 until n) {
                if (i > 0) velocities[i - 1] += spread * (heights[i] - heights[i - 1]) * dt * 30f
                if (i < n - 1) velocities[i + 1] += spread * (heights[i] - heights[i + 1]) * dt * 30f
            }
        }
        val it = drops.iterator()
        while (it.hasNext()) {
            val d = it.next()
            d.age += dt
            d.vy += gravity * dt
            d.x += d.vx * dt; d.y += d.vy * dt; d.z += d.vz * dt
            if (d.age > d.life || (d.vy < 0 && d.y < surfaceAt(d.x, d.z) - 0.05f)) it.remove()
        }
    }

    companion object {
        val MODES = listOf("2D Side View", "3D Volume")
    }
}
