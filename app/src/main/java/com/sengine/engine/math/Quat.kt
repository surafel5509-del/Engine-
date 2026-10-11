package com.sengine.engine.math

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Unit quaternion (x, y, z, w). Used by the Ultimate physics (rotational integration),
 * skeletal animation (joint poses, slerp blending, IK) and the renderer (normals, cameras).
 */
class Quat(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f, var w: Float = 1f) {

    constructor(v: Vec3, w: Float) : this(v.x, v.y, v.z, w)
    constructor(q: Quat) : this(q.x, q.y, q.z, q.w)

    fun set(x: Float, y: Float, z: Float, w: Float): Quat { this.x = x; this.y = y; this.z = z; this.w = w; return this }
    fun set(q: Quat): Quat = set(q.x, q.y, q.z, q.w)
    fun copy(): Quat = Quat(x, y, z, w)
    fun identity(): Quat = set(0f, 0f, 0f, 1f)

    /** Hamilton product: this * o. */
    fun mul(o: Quat): Quat = Quat(
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
        w * o.w - x * o.x - y * o.y - z * o.z
    )

    /** this = a * b in place. */
    fun mulSet(a: Quat, b: Quat): Quat = set(a.mul(b))

    operator fun times(o: Quat) = mul(o)
    operator fun times(s: Float) = Quat(x * s, y * s, z * s, w * s)

    fun dot(o: Quat): Float = x * o.x + y * o.y + z * o.z + w * o.w
    fun length(): Float = sqrt(x * x + y * y + z * z + w * w)

    fun normalize(): Quat {
        val l = length()
        if (l > 1e-20f) { val i = 1f / l; x *= i; y *= i; z *= i; w *= i } else identity()
        return this
    }

    fun normalized(): Quat = copy().normalize()
    fun conjugate() = Quat(-x, -y, -z, w)
    fun inverse(): Quat = conjugate().normalize()

    /** Rotates [v] by this quaternion: q * v * q^-1 (optimised). */
    fun rotate(v: Vec3): Vec3 {
        val tx = 2f * (y * v.z - z * v.y)
        val ty = 2f * (z * v.x - x * v.z)
        val tz = 2f * (x * v.y - y * v.x)
        return Vec3(
            v.x + w * tx + (y * tz - z * ty),
            v.y + w * ty + (z * tx - x * tz),
            v.z + w * tz + (x * ty - y * tx)
        )
    }

    /** Inverse rotation (world -> local). */
    fun rotateInverse(v: Vec3): Vec3 = conjugate().rotate(v)

    fun isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite() && w.isFinite()

    fun toAxisAngle(): FloatArray {
        val q = normalized()
        val ang = 2f * acos(q.w.coerceIn(-1f, 1f))
        val s = sqrt(1f - q.w * q.w)
        return if (s < 1e-6f) floatArrayOf(1f, 0f, 0f, ang) else floatArrayOf(q.x / s, q.y / s, q.z / s, ang)
    }

    /** Row-major 3x3 rotation matrix (length 9). */
    fun toMat3(out: FloatArray = FloatArray(9)): FloatArray {
        val xx = x * x; val yy = y * y; val zz = z * z
        val xy = x * y; val xz = x * z; val yz = y * z
        val wx = w * x; val wy = w * y; val wz = w * z
        out[0] = 1f - 2f * (yy + zz); out[1] = 2f * (xy - wz); out[2] = 2f * (xz + wy)
        out[3] = 2f * (xy + wz); out[4] = 1f - 2f * (xx + zz); out[5] = 2f * (yz - wx)
        out[6] = 2f * (xz - wy); out[7] = 2f * (yz + wx); out[8] = 1f - 2f * (xx + yy)
        return out
    }

    /** Column-major 4x4 rotation matrix (GL layout). */
    fun toMat4(out: FloatArray = FloatArray(16)): FloatArray {
        val m = toMat3(FloatArray(9))
        Mat4.identity(out)
        out[0] = m[0]; out[1] = m[3]; out[2] = m[6]
        out[4] = m[1]; out[5] = m[4]; out[6] = m[7]
        out[8] = m[2]; out[9] = m[5]; out[10] = m[8]
        return out
    }

    override fun equals(other: Any?): Boolean = other is Quat && other.x == x && other.y == y && other.z == z && other.w == w
    override fun hashCode(): Int = ((x.toRawBits() * 31 + y.toRawBits()) * 31 + z.toRawBits()) * 31 + w.toRawBits()
    override fun toString(): String = "(%.4f, %.4f, %.4f, %.4f)".format(x, y, z, w)

    /**
     * Yaw/pitch/roll in degrees, inverse of [fromEulerDeg] (rotation order Ry * Rx * Rz, matching
     * [Mat4.trs]). Falls back to yaw-only extraction near gimbal lock.
     */
    fun toEulerDeg(): FloatArray {
        val m = toMat3()
        val sx = (-m[5]).coerceIn(-1f, 1f)
        val pitch = kotlin.math.asin(sx)
        val yaw: Float
        val roll: Float
        if (kotlin.math.abs(m[5]) < 0.99999f) {
            yaw = kotlin.math.atan2(m[2], m[8])
            roll = kotlin.math.atan2(m[3], m[4])
        } else {
            yaw = kotlin.math.atan2(-m[1], m[0])
            roll = 0f
        }
        return floatArrayOf(
            Math.toDegrees(yaw.toDouble()).toFloat(),
            Math.toDegrees(pitch.toDouble()).toFloat(),
            Math.toDegrees(roll.toDouble()).toFloat()
        )
    }

    companion object {
        fun identity() = Quat(0f, 0f, 0f, 1f)

        /** Rotation of [angleRad] around a (unit) [axis]. */
        fun fromAxisAngle(axis: Vec3, angleRad: Float): Quat {
            val h = angleRad * 0.5f
            val s = sin(h)
            val a = axis.normalizedOr(Vec3.UP)
            return Quat(a.x * s, a.y * s, a.z * s, cos(h))
        }

        fun fromAxisAngle(ax: Float, ay: Float, az: Float, angleRad: Float): Quat =
            fromAxisAngle(Vec3(ax, ay, az), angleRad)

        /** yaw (Y) * pitch (X) * roll (Z), angles in degrees - matches [Mat4.trs]. */
        fun fromEulerDeg(yawDeg: Float, pitchDeg: Float, rollDeg: Float): Quat {
            val qy = fromAxisAngle(0f, 1f, 0f, Math.toRadians(yawDeg.toDouble()).toFloat())
            val qx = fromAxisAngle(1f, 0f, 0f, Math.toRadians(pitchDeg.toDouble()).toFloat())
            val qz = fromAxisAngle(0f, 0f, 1f, Math.toRadians(rollDeg.toDouble()).toFloat())
            return qy.mul(qx).mul(qz).normalize()
        }

        /** Shortest-arc rotation taking [from] to [to]. */
        fun fromTo(from: Vec3, to: Vec3): Quat {
            val a = from.normalizedOr(Vec3.FORWARD)
            val b = to.normalizedOr(Vec3.FORWARD)
            val d = a.dot(b).coerceIn(-1f, 1f)
            if (d > 0.999999f) return identity()
            if (d < -0.999999f) {
                var axis = Vec3.RIGHT.cross(a)
                if (axis.lengthSq() < 1e-8f) axis = Vec3.UP.cross(a)
                return fromAxisAngle(axis, Math.PI.toFloat())
            }
            val c = a.cross(b)
            return Quat(c.x, c.y, c.z, 1f + d).normalize()
        }

        /** Row-major 3x3 rotation matrix -> quaternion. */
        fun fromMat3(m: FloatArray, offset: Int = 0): Quat {
            val m00 = m[offset]; val m01 = m[offset + 1]; val m02 = m[offset + 2]
            val m10 = m[offset + 3]; val m11 = m[offset + 4]; val m12 = m[offset + 5]
            val m20 = m[offset + 6]; val m21 = m[offset + 7]; val m22 = m[offset + 8]
            val tr = m00 + m11 + m22
            return if (tr > 0f) {
                val s = sqrt(tr + 1f) * 2f
                Quat((m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, 0.25f * s)
            } else if (m00 > m11 && m00 > m22) {
                val s = sqrt(1f + m00 - m11 - m22) * 2f
                Quat(0.25f * s, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s)
            } else if (m11 > m22) {
                val s = sqrt(1f + m11 - m00 - m22) * 2f
                Quat((m01 + m10) / s, 0.25f * s, (m12 + m21) / s, (m02 - m20) / s)
            } else {
                val s = sqrt(1f + m22 - m00 - m11) * 2f
                Quat((m02 + m20) / s, (m12 + m21) / s, 0.25f * s, (m10 - m01) / s)
            }.normalize()
        }

        /** Column-major 4x4 (GL) -> quaternion. */
        fun fromMat4(m: FloatArray): Quat {
            val r = floatArrayOf(m[0], m[4], m[8], m[1], m[5], m[9], m[2], m[6], m[10])
            return fromMat3(r)
        }

        /** Spherical interpolation; falls back to normalised lerp for very close quaternions. */
        fun slerp(a: Quat, b: Quat, t: Float): Quat {
            var cosTheta = a.dot(b)
            var bx = b.x; var by = b.y; var bz = b.z; var bw = b.w
            // Only flip hemispheres for a clearly negative dot: a dot within 1e-6 of zero means the
            // quaternions are orthogonal (a 180 degree rotation apart) where flipping would make the
            // interpolation take the negative path and produce a jittering sign near that case.
            if (cosTheta < -1e-6f) { cosTheta = -cosTheta; bx = -bx; by = -by; bz = -bz; bw = -bw }
            if (cosTheta > 0.9995f) {
                return Quat(
                    a.x + (bx - a.x) * t, a.y + (by - a.y) * t,
                    a.z + (bz - a.z) * t, a.w + (bw - a.w) * t
                ).normalize()
            }
            val theta = acos(cosTheta.coerceIn(-1f, 1f))
            val sinTheta = sin(theta)
            val wa = sin((1f - t) * theta) / sinTheta
            val wb = sin(t * theta) / sinTheta
            return Quat(a.x * wa + bx * wb, a.y * wa + by * wb, a.z * wa + bz * wb, a.w * wa + bw * wb).normalize()
        }

        fun nlerp(a: Quat, b: Quat, t: Float): Quat {
            val d = if (a.dot(b) < 0f) -1f else 1f
            return Quat(
                a.x + (b.x * d - a.x) * t, a.y + (b.y * d - a.y) * t,
                a.z + (b.z * d - a.z) * t, a.w + (b.w * d - a.w) * t
            ).normalize()
        }

        /** Integrates angular velocity [w] (rad/s, world space) over [dt] into [q]. */
        fun integrate(q: Quat, w: Vec3, dt: Float): Quat {
            val halfDt = dt * 0.5f
            val dq = Quat(w.x * halfDt, w.y * halfDt, w.z * halfDt, 0f).mul(q)
            return Quat(q.x + dq.x, q.y + dq.y, q.z + dq.z, q.w + dq.w).normalize()
        }
    }
}
