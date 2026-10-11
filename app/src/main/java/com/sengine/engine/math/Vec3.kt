package com.sengine.engine.math

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mutable 3D vector used by the Ultimate physics, animation, AI, rendering and networking
 * subsystems. Arithmetic operators allocate a new vector; the `addScaled`, `set*` and
 * `crossSet` helpers write in place so hot loops stay allocation free.
 */
class Vec3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {

    constructor(v: Vec3) : this(v.x, v.y, v.z)

    fun set(x: Float, y: Float, z: Float): Vec3 { this.x = x; this.y = y; this.z = z; return this }
    fun set(v: Vec3): Vec3 = set(v.x, v.y, v.z)
    fun copy(): Vec3 = Vec3(x, y, z)

    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun times(o: Vec3) = Vec3(x * o.x, y * o.y, z * o.z)
    operator fun div(s: Float) = Vec3(x / s, y / s, z / s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    fun add(o: Vec3): Vec3 { x += o.x; y += o.y; z += o.z; return this }
    fun add(ax: Float, ay: Float, az: Float): Vec3 { x += ax; y += ay; z += az; return this }
    fun sub(o: Vec3): Vec3 { x -= o.x; y -= o.y; z -= o.z; return this }
    fun scale(s: Float): Vec3 { x *= s; y *= s; z *= s; return this }
    fun addScaled(o: Vec3, s: Float): Vec3 { x += o.x * s; y += o.y * s; z += o.z * s; return this }
    fun addScaled(ax: Float, ay: Float, az: Float, s: Float): Vec3 { x += ax * s; y += ay * s; z += az * s; return this }
    fun zero(): Vec3 { x = 0f; y = 0f; z = 0f; return this }

    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z
    fun dot(ox: Float, oy: Float, oz: Float): Float = x * ox + y * oy + z * oz
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun cross(ox: Float, oy: Float, oz: Float) = Vec3(y * oz - z * oy, z * ox - x * oz, x * oy - y * ox)

    /** this = a x b (in place, no allocation). */
    fun crossSet(a: Vec3, b: Vec3): Vec3 {
        val nx = a.y * b.z - a.z * b.y
        val ny = a.z * b.x - a.x * b.z
        val nz = a.x * b.y - a.y * b.x
        return set(nx, ny, nz)
    }

    fun lengthSq(): Float = x * x + y * y + z * z
    fun length(): Float = sqrt(lengthSq())
    fun distanceTo(o: Vec3): Float = sqrt(distanceSqTo(o))
    fun distanceSqTo(o: Vec3): Float { val dx = x - o.x; val dy = y - o.y; val dz = z - o.z; return dx * dx + dy * dy + dz * dz }

    fun normalize(): Vec3 {
        val l = length()
        if (l > 1e-20f) scale(1f / l) else set(0f, 0f, 0f)
        return this
    }

    fun normalized(): Vec3 = copy().normalize()

    /** Safe normalize that keeps [fallback] for degenerate vectors. */
    fun normalizedOr(fallback: Vec3): Vec3 {
        val l = length()
        return if (l > 1e-9f) Vec3(x / l, y / l, z / l) else fallback.copy()
    }

    fun lerp(o: Vec3, t: Float): Vec3 = Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t)
    fun lerpTo(o: Vec3, t: Float): Vec3 = set(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t)
    fun clampLength(maxLen: Float): Vec3 {
        val l = length()
        return if (l > maxLen && l > 1e-9f) scale(maxLen / l) else this
    }

    fun min(o: Vec3) = Vec3(min(x, o.x), min(y, o.y), min(z, o.z))
    fun max(o: Vec3) = Vec3(max(x, o.x), max(y, o.y), max(z, o.z))
    fun abs() = Vec3(abs(x), abs(y), abs(z))

    /** Angle between two vectors in degrees. */
    fun angleTo(o: Vec3): Float {
        val l = length() * o.length()
        if (l < 1e-9f) return 0f
        return Math.toDegrees(acos((dot(o) / l).coerceIn(-1f, 1f).toDouble())).toFloat()
    }

    fun isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

    /** Rotates this vector around a unit [axis] by [angleRad] (Rodrigues). */
    fun rotateAxis(axis: Vec3, angleRad: Float): Vec3 {
        val c = cos(angleRad); val s = sin(angleRad)
        val k = axis.dot(this) * (1f - c)
        val cr = axis.cross(this)
        return Vec3(x * c + cr.x * s + axis.x * k, y * c + cr.y * s + axis.y * k, z * c + cr.z * s + axis.z * k)
    }

    fun toFloatArray(out: FloatArray = FloatArray(3)): FloatArray { out[0] = x; out[1] = y; out[2] = z; return out }

    override fun equals(other: Any?): Boolean = other is Vec3 && other.x == x && other.y == y && other.z == z
    override fun hashCode(): Int = (x.toRawBits() * 31 + y.toRawBits()) * 31 + z.toRawBits()
    override fun toString(): String = "(%.4f, %.4f, %.4f)".format(x, y, z)

    companion object {
        fun of(x: Float, y: Float, z: Float) = Vec3(x, y, z)
        fun splat(v: Float) = Vec3(v, v, v)
        fun fromArray(a: FloatArray, offset: Int = 0) = Vec3(a[offset], a[offset + 1], a[offset + 2])

        val ZERO: Vec3 get() = Vec3(0f, 0f, 0f)
        val ONE: Vec3 get() = Vec3(1f, 1f, 1f)
        val UP: Vec3 get() = Vec3(0f, 1f, 0f)
        val DOWN: Vec3 get() = Vec3(0f, -1f, 0f)
        val RIGHT: Vec3 get() = Vec3(1f, 0f, 0f)
        val LEFT: Vec3 get() = Vec3(-1f, 0f, 0f)
        val FORWARD: Vec3 get() = Vec3(0f, 0f, -1f)
        val BACK: Vec3 get() = Vec3(0f, 0f, 1f)

        /** Direction from spherical angles (yaw/pitch in degrees), [UP]-relative. */
        fun fromYawPitch(yawDeg: Float, pitchDeg: Float): Vec3 {
            val y = Math.toRadians(yawDeg.toDouble()); val p = Math.toRadians(pitchDeg.toDouble())
            return Vec3((cos(p) * sin(y)).toFloat(), (-sin(p)).toFloat(), (-cos(p) * cos(y)).toFloat())
        }

        /** Yaw of a direction in degrees (0 = -Z, +90 = +X). */
        fun toYaw(v: Vec3): Float = Math.toDegrees(kotlin.math.atan2(v.x.toDouble(), -v.z.toDouble())).toFloat()

        /** Pitch of a direction in degrees (+ up). */
        fun toPitch(v: Vec3): Float {
            val l = sqrt(v.x * v.x + v.z * v.z)
            return Math.toDegrees(kotlin.math.atan2(-v.y.toDouble(), l.toDouble())).toFloat()
        }

        fun lerp(a: Vec3, b: Vec3, t: Float) = a.lerp(b, t)
        fun cross(a: Vec3, b: Vec3) = a.cross(b)
        fun dot(a: Vec3, b: Vec3) = a.dot(b)
        fun distance(a: Vec3, b: Vec3) = a.distanceTo(b)
        fun min(a: Vec3, b: Vec3) = a.min(b)
        fun max(a: Vec3, b: Vec3) = a.max(b)
        fun normalize(v: Vec3) = v.normalized()
    }
}
