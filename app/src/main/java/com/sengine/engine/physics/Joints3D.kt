package com.sengine.engine.physics

import com.sengine.engine.math.Quat
import com.sengine.engine.math.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * Ultimate physics (v7) — FEATURE layer: joints.
 *
 * Every joint is a sequential-impulse constraint solved against exactly the public contract in
 * `ProPhysicsApi.kt` (`Joint3D`, `RigidBodyPro3D`, `BodyDesc3D`). Nothing here reaches into the
 * core solver internals: joints read/write body motion state and read `invMass` / `invInertiaWorld`.
 *
 * Solving protocol (as documented on [Joint3D]):
 *  * [Joint3D.prepare] is called once per fixed step: it recomputes the Jacobians, the effective
 *    masses, the Baumgarte/soft bias, and warm-starts from the previous step's impulses.
 *  * [Joint3D.solve] performs exactly ONE sequential-impulse iteration; the world calls it
 *    `iterations` times. Impulses are accumulated across iterations, which is what makes
 *    warm starting and breaking thresholds meaningful.
 */

/** 3x3 row-major matrix used for the joint effective-mass blocks. */
internal class JointMat3 {
    val m = FloatArray(9)

    fun setZero(): JointMat3 {
        for (i in 0 until 9) m[i] = 0f
        return this
    }

    fun setIdentity(): JointMat3 {
        setZero(); m[0] = 1f; m[4] = 1f; m[8] = 1f
        return this
    }

    fun setDiagonal(s: Float): JointMat3 {
        setZero(); m[0] = s; m[4] = s; m[8] = s
        return this
    }

    fun setSkew(v: Vec3): JointMat3 {
        m[0] = 0f; m[1] = -v.z; m[2] = v.y
        m[3] = v.z; m[4] = 0f; m[5] = -v.x
        m[6] = -v.y; m[7] = v.x; m[8] = 0f
        return this
    }

    fun setFrom(arr: FloatArray, offset: Int = 0): JointMat3 {
        for (i in 0 until 9) m[i] = arr[offset + i]
        return this
    }

    fun addScaled(o: JointMat3, s: Float): JointMat3 {
        for (i in 0 until 9) m[i] += o.m[i] * s
        return this
    }

    /** this * o written into [out]. */
    fun mul(o: JointMat3, out: JointMat3 = JointMat3()): JointMat3 {
        val a = m; val b = o.m; val r = out.m
        for (i in 0 until 3) for (j in 0 until 3) {
            r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j]
        }
        return out
    }

    fun mulVec(v: Vec3, out: Vec3 = Vec3()): Vec3 = out.set(
        m[0] * v.x + m[1] * v.y + m[2] * v.z,
        m[3] * v.x + m[4] * v.y + m[5] * v.z,
        m[6] * v.x + m[7] * v.y + m[8] * v.z
    )

    /** Inverts this matrix into [out]; returns false when singular. */
    fun invert(out: JointMat3 = JointMat3()): Boolean {
        val det = m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6])
        if (abs(det) < 1e-12f || !det.isFinite()) {
            out.setZero()
            return false
        }
        val id = 1f / det
        out.m[0] = (m[4] * m[8] - m[5] * m[7]) * id
        out.m[1] = (m[2] * m[7] - m[1] * m[8]) * id
        out.m[2] = (m[1] * m[5] - m[2] * m[4]) * id
        out.m[3] = (m[5] * m[6] - m[3] * m[8]) * id
        out.m[4] = (m[0] * m[8] - m[2] * m[6]) * id
        out.m[5] = (m[2] * m[3] - m[0] * m[5]) * id
        out.m[6] = (m[3] * m[7] - m[4] * m[6]) * id
        out.m[7] = (m[1] * m[6] - m[0] * m[7]) * id
        out.m[8] = (m[0] * m[4] - m[1] * m[3]) * id
        return true
    }
}


/**
 * Stateless maths shared by every joint. All functions take explicit scratch/output parameters so
 * a joint can own its temporaries (safe when islands are solved on different JobSystem workers).
 */
internal object JointMath3D {

    /** Applies a linear impulse [p] at offset [r] (world, relative to the body centre). */
    fun applyPointImpulse(body: RigidBodyPro3D?, p: Vec3, r: Vec3) {
        if (body == null || body.isStatic || body.invMass <= 0f) return
        body.linearVelocity.addScaled(p, body.invMass)
        body.angularVelocity.add(RigidBodyPro3D.transformInertia(r.cross(p), body.invInertiaWorld))
    }

    /** Applies a pure angular impulse [p]. */
    fun applyAngularImpulse(body: RigidBodyPro3D?, p: Vec3) {
        if (body == null || body.isStatic) return
        body.angularVelocity.add(RigidBodyPro3D.transformInertia(p, body.invInertiaWorld))
    }

    /** 3x3 point-constraint mass: `K = (invMa + invMb) I - S(ra) Ia S(ra) - S(rb) Ib S(rb)`. */
    fun pointMass(a: RigidBodyPro3D, ra: Vec3, b: RigidBodyPro3D?, rb: Vec3, out: JointMat3): JointMat3 {
        out.setDiagonal(a.invMass + (b?.invMass ?: 0f))
        addRotationalTerm(out, a, ra, -1f)
        if (b != null) addRotationalTerm(out, b, rb, -1f)
        return out
    }

    private fun addRotationalTerm(out: JointMat3, body: RigidBodyPro3D, r: Vec3, s: Float) {
        if (body.isStatic) return
        val sk = JointMat3().setSkew(r)
        val inv = JointMat3().setFrom(body.invInertiaWorld)
        out.addScaled(sk.mul(inv).mul(sk), s)
    }

    /** 3x3 angular-constraint mass: `K = Ia + Ib`. */
    fun angularMass(a: RigidBodyPro3D, b: RigidBodyPro3D?, out: JointMat3): JointMat3 {
        out.setFrom(a.invInertiaWorld)
        if (b != null) out.addScaled(JointMat3().setFrom(b.invInertiaWorld), 1f)
        return out
    }

    /** Scalar effective mass `n^T (Ia + Ib) n` for an angular row along the unit [n]. */
    fun angularScalarMass(a: RigidBodyPro3D, b: RigidBodyPro3D?, n: Vec3): Float {
        var k = if (a.isStatic) 0f else n.dot(RigidBodyPro3D.transformInertia(n, a.invInertiaWorld))
        if (b != null && !b.isStatic) k += n.dot(RigidBodyPro3D.transformInertia(n, b.invInertiaWorld))
        return k
    }

    /** Scalar effective mass for a linear row along the unit [n] through the offsets [ra]/[rb]. */
    fun linearScalarMass(a: RigidBodyPro3D, ra: Vec3, b: RigidBodyPro3D?, rb: Vec3, n: Vec3): Float {
        var k = a.invMass + (b?.invMass ?: 0f)
        if (!a.isStatic) {
            val raxn = ra.cross(n)
            k += raxn.dot(RigidBodyPro3D.transformInertia(raxn, a.invInertiaWorld))
        }
        if (b != null && !b.isStatic) {
            val rbxn = rb.cross(n)
            k += rbxn.dot(RigidBodyPro3D.transformInertia(rbxn, b.invInertiaWorld))
        }
        return k
    }

    /** Rotation vector `axis * angle` of [q] (world space). */
    fun rotVec(q: Quat): Vec3 {
        val n = q.normalized()
        val s = sqrt(n.x * n.x + n.y * n.y + n.z * n.z)
        if (s < 1e-7f) return Vec3(0f, 0f, 0f)
        val angle = 2f * atan2(s, n.w)
        return Vec3(n.x, n.y, n.z).scale(angle / s)
    }

    /** Twist angle of [q] about the unit [axis] (swing/twist decomposition). */
    fun twistAbout(q: Quat, axis: Vec3): Float {
        val n = q.normalized()
        val d = n.x * axis.x + n.y * axis.y + n.z * axis.z
        val px = axis.x * d; val py = axis.y * d; val pz = axis.z * d
        val len = sqrt(px * px + py * py + pz * pz)
        if (len < 1e-7f) return 0f
        return 2f * atan2(if (d < 0f) -len else len, n.w)
    }

    /** Builds an orthonormal basis ([t1], [t2]) perpendicular to the unit [axis]. */
    fun perpendicular(axis: Vec3, t1: Vec3, t2: Vec3) {
        val ref = if (abs(axis.y) < 0.9f) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)
        t1.set(axis.cross(ref))
        if (t1.lengthSq() < 1e-10f) t1.set(1f, 0f, 0f) else t1.normalize()
        t2.set(axis.cross(t1)).normalize()
    }

    /** Solves `mat * x = rhs` into [out] using the caller-owned [scratch]; false when singular. */
    fun solve(mat: JointMat3, rhs: Vec3, out: Vec3, scratch: JointMat3): Boolean {
        if (!mat.invert(scratch)) {
            out.zero()
            return false
        }
        scratch.mulVec(rhs, out)
        return true
    }
}


/**
 * Creates a static "world frame" body used as the second body of a joint that is anchored to the
 * world (i.e. constructed with `bodyB == null`). It has infinite mass/inertia so the sequential
 * impulse code can treat every joint uniformly.
 */
internal fun worldFrameBody(anchor: Vec3): RigidBodyPro3D {
    val d = BodyDesc3D()
    d.static()
    d.position = Vec3(anchor)
    val b = RigidBodyPro3D(-1, d)
    b.position.set(anchor)
    b.isStatic = true
    b.isDynamic = false
    b.isKinematic = false
    b.invMass = 0f
    return b
}

/**
 * Common base of the feature-layer joints: keeps the Baumgarte factor, the inverse timestep, the
 * accumulated impulse magnitudes used for breaking thresholds and the break callback.
 */
abstract class ProJoint3D(bodyA: RigidBodyPro3D, bodyB: RigidBodyPro3D?) : Joint3D(bodyA, bodyB) {
    /** Positional error feedback factor (Baumgarte). 0.2 keeps stacks firm without jitter. */
    var baumgarte = 0.2f

    /** Magnitude of the angular impulse accumulated over the last step (N·m·s). */
    var accumulatedTorqueImpulse = 0f
        protected set

    /** Invoked once when the joint exceeds its breaking threshold. */
    var onBreak: ((ProJoint3D) -> Unit)? = null

    protected var invDt = 0f

    /** Recomputes 1/dt for the current step. */
    protected fun updateTimestep(dt: Float) {
        invDt = if (dt > 1e-6f) 1f / dt else 0f
    }

    /** Records the impulse magnitudes and flips [broken] when a threshold is exceeded. */
    protected fun checkBreak(forceImpulse: Float, torqueImpulse: Float) {
        accumulatedImpulse = forceImpulse
        accumulatedTorqueImpulse = torqueImpulse
        if (breakForce < Float.MAX_VALUE && forceImpulse > breakForce) breakJoint()
        if (breakTorque < Float.MAX_VALUE && torqueImpulse > breakTorque) breakJoint()
    }

    protected fun breakJoint() {
        if (broken) return
        broken = true
        onBreak?.invoke(this)
    }
}



/**
 * A joint with a single scalar degree of freedom (hinge angle / slider position). Used by
 * [GearJoint3D] to couple two such joints.
 */
interface JointCoordinate3D {
    /** Current value of the joint coordinate (radians for hinges, metres for sliders). */
    val coordinate: Float
    /** Rate of change of [coordinate] (rad/s or m/s). */
    val coordinateVelocity: Float
    /** Effective mass of the coordinate row. */
    fun coordinateMass(): Float
    /**
     * Applies a coordinate-space impulse: increases [coordinateVelocity] by
     * `lambda * coordinateMass()` (and feeds the warm-start accumulator).
     */
    fun applyCoordinateImpulse(lambda: Float)
}

/**
 * Welds two bodies together (all six degrees of freedom locked) at [worldAnchor].
 *
 * Pass `bodyB = null` to anchor [bodyA] to the world (a static world frame is created at the
 * anchor).
 */
class FixedJoint3D(bodyA: RigidBodyPro3D, bodyB: RigidBodyPro3D?, worldAnchor: Vec3) :
    ProJoint3D(bodyA, bodyB ?: worldFrameBody(worldAnchor)) {

    override val kind: String = "Fixed"

    private val localAnchorA = Vec3()
    private val localAnchorB = Vec3()
    private val refRot: Quat

    private val ra = Vec3(); private val rb = Vec3()
    private val cErr = Vec3(); private val bias = Vec3()
    private val angBias = Vec3()
    private val pointImpulse = Vec3(); private val angImpulse = Vec3()
    private val rhs = Vec3(); private val lambda = Vec3()
    private val km = JointMat3(); private val ka = JointMat3(); private val scratch = JointMat3()

    init {
        val b = bodyB!!
        localAnchorA.set(bodyA.rotation.rotateInverse(worldAnchor - bodyA.position))
        localAnchorB.set(b.rotation.rotateInverse(worldAnchor - b.position))
        refRot = bodyA.rotation.conjugate().mul(b.rotation).normalize()
    }

    /** Relative orientation the joint holds (B relative to A). */
    fun referenceRotation(): Quat = refRot.copy()

    override fun prepare(dt: Float) {
        updateTimestep(dt)
        if (!enabled || broken) return
        val b = bodyB!!
        ra.set(bodyA.rotation.rotate(localAnchorA))
        rb.set(b.rotation.rotate(localAnchorB))
        JointMath3D.pointMass(bodyA, ra, b, rb, km)
        cErr.set(bodyA.position + ra).sub(b.position + rb)
        bias.set(cErr).scale(baumgarte * invDt)
        val qErr = bodyA.rotation.mul(refRot).mul(b.rotation.conjugate())
        angBias.set(JointMath3D.rotVec(qErr)).scale(baumgarte * invDt)
        JointMath3D.angularMass(bodyA, b, ka)
        JointMath3D.applyPointImpulse(bodyA, pointImpulse, ra)
        JointMath3D.applyPointImpulse(b, -pointImpulse, rb)
        JointMath3D.applyAngularImpulse(bodyA, angImpulse)
        JointMath3D.applyAngularImpulse(b, -angImpulse)
    }

    override fun solve(iterations: Int) {
        if (!enabled || broken) return
        val b = bodyB!!
        val vrel = (bodyA.linearVelocity + bodyA.angularVelocity.cross(ra)) -
            (b.linearVelocity + b.angularVelocity.cross(rb))
        rhs.set(vrel).add(bias).scale(-1f)
        if (JointMath3D.solve(km, rhs, lambda, scratch)) {
            pointImpulse.add(lambda)
            JointMath3D.applyPointImpulse(bodyA, lambda, ra)
            JointMath3D.applyPointImpulse(b, -lambda, rb)
        }
        val wrel = bodyA.angularVelocity - b.angularVelocity
        rhs.set(wrel).add(angBias).scale(-1f)
        if (JointMath3D.solve(ka, rhs, lambda, scratch)) {
            angImpulse.add(lambda)
            JointMath3D.applyAngularImpulse(bodyA, lambda)
            JointMath3D.applyAngularImpulse(b, -lambda)
        }
        checkBreak(pointImpulse.length(), angImpulse.length())
    }

    override fun describe(): String =
        "Fixed(anchorA=$localAnchorA, impulse=${"%.3f".format(pointImpulse.length())})"
}

/**
 * Ball-and-socket (spherical) joint: the two anchor points coincide, all relative rotation is free.
 * Pass `bodyB = null` to anchor [bodyA] to the world.
 */
class BallSocketJoint3D(bodyA: RigidBodyPro3D, bodyB: RigidBodyPro3D?, worldAnchor: Vec3) :
    ProJoint3D(bodyA, bodyB ?: worldFrameBody(worldAnchor)) {

    override val kind: String = "BallSocket"

    private val localAnchorA = Vec3()
    private val localAnchorB = Vec3()
    private val ra = Vec3(); private val rb = Vec3()
    private val cErr = Vec3(); private val bias = Vec3()
    private val impulse = Vec3()
    private val rhs = Vec3(); private val lambda = Vec3()
    private val km = JointMat3(); private val scratch = JointMat3()

    init {
        val b = bodyB!!
        localAnchorA.set(bodyA.rotation.rotateInverse(worldAnchor - bodyA.position))
        localAnchorB.set(b.rotation.rotateInverse(worldAnchor - b.position))
    }

    /** Current world position of the socket (from body A). */
    fun worldAnchor(out: Vec3 = Vec3()): Vec3 = out.set(bodyA.position).add(bodyA.rotation.rotate(localAnchorA))

    /** Current separation of the two anchor points in metres (debug/telemetry). */
    fun anchorError(): Float =
        (bodyA.position + bodyA.rotation.rotate(localAnchorA))
            .distanceTo(bodyB!!.position + bodyB!!.rotation.rotate(localAnchorB))

    override fun prepare(dt: Float) {
        updateTimestep(dt)
        if (!enabled || broken) return
        val b = bodyB!!
        ra.set(bodyA.rotation.rotate(localAnchorA))
        rb.set(b.rotation.rotate(localAnchorB))
        JointMath3D.pointMass(bodyA, ra, b, rb, km)
        cErr.set(bodyA.position + ra).sub(b.position + rb)
        bias.set(cErr).scale(baumgarte * invDt)
        JointMath3D.applyPointImpulse(bodyA, impulse, ra)
        JointMath3D.applyPointImpulse(b, -impulse, rb)
    }

    override fun solve(iterations: Int) {
        if (!enabled || broken) return
        val b = bodyB!!
        val vrel = (bodyA.linearVelocity + bodyA.angularVelocity.cross(ra)) -
            (b.linearVelocity + b.angularVelocity.cross(rb))
        rhs.set(vrel).add(bias).scale(-1f)
        if (!JointMath3D.solve(km, rhs, lambda, scratch)) return
        impulse.add(lambda)
        JointMath3D.applyPointImpulse(bodyA, lambda, ra)
        JointMath3D.applyPointImpulse(b, -lambda, rb)
        checkBreak(impulse.length(), 0f)
    }

    override fun describe(): String = "BallSocket(error=${"%.4f".format(anchorError())})"
}


/**
 * Revolute (hinge) joint: locks the two anchor points and keeps the two hinge axes aligned while
 * leaving rotation about [worldAxis] free. Supports an angle range ([JointLimits]) and a
 * velocity [JointMotor] with a force limit.
 *
 * Pass `bodyB = null` to hinge [bodyA] to the world.
 */
class HingeJoint3D(
    bodyA: RigidBodyPro3D,
    bodyB: RigidBodyPro3D?,
    worldAnchor: Vec3,
    worldAxis: Vec3,
    val limits: JointLimits = JointLimits(),
    val motor: JointMotor = JointMotor(),
) : ProJoint3D(bodyA, bodyB ?: worldFrameBody(worldAnchor)), JointCoordinate3D {

    override val kind: String = "Hinge"

    private val localAnchorA = Vec3()
    private val localAnchorB = Vec3()
    private val axisA = Vec3()
    private val axisB = Vec3()

    private val ra = Vec3(); private val rb = Vec3()
    private val axis = Vec3(0f, 1f, 0f)
    private val t1 = Vec3(1f, 0f, 0f); private val t2 = Vec3(0f, 0f, 1f)
    private val cErr = Vec3(); private val bias = Vec3(); private val e = Vec3()
    private val pointImpulse = Vec3(); private val angImpulse = Vec3()
    private val rhs = Vec3(); private val lambda = Vec3()
    private val tmp = Vec3(); private val tmp2 = Vec3()
    private val km = JointMat3(); private val scratch = JointMat3()

    private var stepDt = 0f
    private var alignMass1 = 1f; private var alignMass2 = 1f
    private var axialMass = 1f
    private var motorImpulse = 0f; private var limitImpulse = 0f
    private var motorBias = 0f; private var limitBias = 0f
    private var limitMode = 0

    /** Relative rotation angle about the hinge axis in radians (0 at construction). */
    var angle = 0f
        private set

    init {
        val b = bodyB!!
        localAnchorA.set(bodyA.rotation.rotateInverse(worldAnchor - bodyA.position))
        localAnchorB.set(b.rotation.rotateInverse(worldAnchor - b.position))
        val n = worldAxis.normalizedOr(Vec3.UP)
        axisA.set(bodyA.rotation.rotateInverse(n))
        axisB.set(b.rotation.rotateInverse(n))
    }

    /** World-space hinge axis (derived from body A). */
    fun worldAxis(out: Vec3 = Vec3()): Vec3 = out.set(bodyA.rotation.rotate(axisA))

    override fun prepare(dt: Float) {
        stepDt = dt
        updateTimestep(dt)
        if (!enabled || broken) return
        val b = bodyB!!
        ra.set(bodyA.rotation.rotate(localAnchorA))
        rb.set(b.rotation.rotate(localAnchorB))
        JointMath3D.pointMass(bodyA, ra, b, rb, km)
        cErr.set(bodyA.position + ra).sub(b.position + rb)
        bias.set(cErr).scale(baumgarte * invDt)

        val aA = bodyA.rotation.rotate(axisA)
        val aB = b.rotation.rotate(axisB)
        axis.set(aA)
        JointMath3D.perpendicular(axis, t1, t2)
        e.crossSet(aA, aB)
        alignMass1 = JointMath3D.angularScalarMass(bodyA, b, t1)
        alignMass2 = JointMath3D.angularScalarMass(bodyA, b, t2)
        axialMass = JointMath3D.angularScalarMass(bodyA, b, axis)
        if (axialMass < 1e-9f) axialMass = 1e-9f

        // d(theta)/dt = (wB - wA).axis, so the coordinate rate is -J with J = (wA - wB).axis.
        angle -= (bodyA.angularVelocity - b.angularVelocity).dot(axis) * dt

        motorBias = if (motor.enabled) motor.targetVelocity else 0f
        limitMode = 0
        limitBias = 0f
        if (limits.isLimited) {
            if (angle < limits.lower) {
                limitMode = -1; limitBias = baumgarte * invDt * (limits.lower - angle)
            } else if (angle > limits.upper) {
                limitMode = 1; limitBias = baumgarte * invDt * (limits.upper - angle)
            }
        }
        if (limitMode == 0) limitImpulse = 0f

        JointMath3D.applyPointImpulse(bodyA, pointImpulse, ra)
        JointMath3D.applyPointImpulse(b, -pointImpulse, rb)
        JointMath3D.applyAngularImpulse(bodyA, angImpulse)
        JointMath3D.applyAngularImpulse(b, -angImpulse)
        if (motor.enabled && motorImpulse != 0f) applyAxial(motorImpulse)
        if (limitMode != 0 && limitImpulse != 0f) applyAxial(limitImpulse)
    }


    override fun solve(iterations: Int) {
        if (!enabled || broken) return
        val b = bodyB!!
        // 1) socket (3 rows)
        val vrel = (bodyA.linearVelocity + bodyA.angularVelocity.cross(ra)) -
            (b.linearVelocity + b.angularVelocity.cross(rb))
        rhs.set(vrel).add(bias).scale(-1f)
        if (JointMath3D.solve(km, rhs, lambda, scratch)) {
            pointImpulse.add(lambda)
            JointMath3D.applyPointImpulse(bodyA, lambda, ra)
            JointMath3D.applyPointImpulse(b, -lambda, rb)
        }
        // 2) axis alignment (2 rows perpendicular to the hinge axis)
        var wrel = bodyA.angularVelocity - b.angularVelocity
        val l1 = -(wrel.dot(t1) + baumgarte * invDt * e.dot(t1)) / alignMass1
        val l2 = -(wrel.dot(t2) + baumgarte * invDt * e.dot(t2)) / alignMass2
        tmp.set(t1).scale(l1).addScaled(t2, l2)
        angImpulse.add(tmp)
        JointMath3D.applyAngularImpulse(bodyA, tmp)
        JointMath3D.applyAngularImpulse(b, tmp2.set(tmp).scale(-1f))
        // 3) motor (axial, bilateral, force limited)
        if (motor.enabled) {
            wrel = bodyA.angularVelocity - b.angularVelocity
            val l = -(wrel.dot(axis) + motorBias) / axialMass
            val old = motorImpulse
            val maxImp = if (motor.maxForce > 0f) motor.maxForce * stepDt else Float.MAX_VALUE
            motorImpulse = (old + l).coerceIn(-maxImp, maxImp)
            applyAxial(motorImpulse - old)
        } else {
            motorImpulse = 0f
        }
        // 4) angular limit (axial, unilateral)
        if (limitMode != 0) {
            wrel = bodyA.angularVelocity - b.angularVelocity
            val l = -(wrel.dot(axis) + limitBias) / axialMass
            val old = limitImpulse
            val next = if (limitMode < 0) min(old + l, 0f) else max(old + l, 0f)
            limitImpulse = next
            applyAxial(next - old)
        }
        checkBreak(pointImpulse.length(), angImpulse.length() + abs(motorImpulse) + abs(limitImpulse))
    }

    /** Applies an axial (about [axis]) angular impulse pair: `+lambda` on A, `-lambda` on B. */
    private fun applyAxial(lambda: Float) {
        if (lambda == 0f) return
        JointMath3D.applyAngularImpulse(bodyA, axis.scale(lambda))
        JointMath3D.applyAngularImpulse(bodyB, axis.scale(-lambda))
    }

    override val coordinate: Float get() = angle

    override val coordinateVelocity: Float
        get() = -(bodyA.angularVelocity - bodyB!!.angularVelocity).dot(axis)

    override fun coordinateMass(): Float = axialMass

    override fun applyCoordinateImpulse(lambda: Float) {
        applyAxial(-lambda)
        angImpulse.addScaled(axis, -lambda)
    }

    /** Total axial impulse applied this step (motor + limit + external gear coupling). */
    fun axialImpulse(): Float = motorImpulse + limitImpulse

    override fun describe(): String =
        "Hinge(angle=${"%.4f".format(angle)}, $limits, $motor)"
}


/**
 * Prismatic (slider) joint: the two bodies may translate along [worldAxis] and nothing else — the
 * two perpendicular translation axes and all three rotation axes are locked. Supports a travel range
 * ([JointLimits], measured relative to the rest position) and an axial velocity [JointMotor].
 *
 * Pass `bodyB = null` to slide [bodyA] along a world axis.
 */
class SliderJoint3D(
    bodyA: RigidBodyPro3D,
    bodyB: RigidBodyPro3D?,
    worldAnchor: Vec3,
    worldAxis: Vec3,
    val limits: JointLimits = JointLimits(),
    val motor: JointMotor = JointMotor(),
) : ProJoint3D(bodyA, bodyB ?: worldFrameBody(worldAnchor)), JointCoordinate3D {

    override val kind: String = "Slider"

    private val localAnchorA = Vec3()
    private val localAnchorB = Vec3()
    private val axisA = Vec3()
    private val axisB = Vec3()
    private val refRot: Quat

    private val ra = Vec3(); private val rb = Vec3()
    private val axis = Vec3(0f, 1f, 0f)
    private val t1 = Vec3(1f, 0f, 0f); private val t2 = Vec3(0f, 0f, 1f)
    private val cErr = Vec3(); private val bias = Vec3()
    private val e = Vec3(); private val angBias = Vec3()
    private val perpImpulse = Vec3(); private val angImpulse = Vec3()
    private val rhs = Vec3(); private val lambda = Vec3(); private val tmp = Vec3(); private val tmp2 = Vec3()
    private val km = JointMat3(); private val ka = JointMat3(); private val scratch = JointMat3()

    private var stepDt = 0f
    private var mass1 = 1f; private var mass2 = 1f; private var axialMass = 1f
    private var motorImpulse = 0f; private var limitImpulse = 0f
    private var motorBias = 0f; private var limitBias = 0f
    private var limitMode = 0
    private var restOffset = 0f

    /** Travel along the slide axis relative to the rest position, in metres. */
    var position = 0f
        private set

    init {
        val b = bodyB!!
        localAnchorA.set(bodyA.rotation.rotateInverse(worldAnchor - bodyA.position))
        localAnchorB.set(b.rotation.rotateInverse(worldAnchor - b.position))
        val n = worldAxis.normalizedOr(Vec3.UP)
        axisA.set(bodyA.rotation.rotateInverse(n))
        axisB.set(b.rotation.rotateInverse(n))
        refRot = bodyA.rotation.conjugate().mul(b.rotation).normalize()
        val d = (b.position + b.rotation.rotate(localAnchorB)) - (bodyA.position + bodyA.rotation.rotate(localAnchorA))
        restOffset = d.dot(n)
    }

    /** World-space slide axis (derived from body A). */
    fun worldAxis(out: Vec3 = Vec3()): Vec3 = out.set(bodyA.rotation.rotate(axisA))

    override fun prepare(dt: Float) {
        stepDt = dt
        updateTimestep(dt)
        if (!enabled || broken) return
        val b = bodyB!!
        ra.set(bodyA.rotation.rotate(localAnchorA))
        rb.set(b.rotation.rotate(localAnchorB))
        axis.set(bodyA.rotation.rotate(axisA))
        JointMath3D.perpendicular(axis, t1, t2)
        cErr.set(bodyA.position + ra).sub(b.position + rb)
        bias.set(cErr).scale(baumgarte * invDt)
        mass1 = JointMath3D.linearScalarMass(bodyA, ra, b, rb, t1)
        mass2 = JointMath3D.linearScalarMass(bodyA, ra, b, rb, t2)
        axialMass = JointMath3D.linearScalarMass(bodyA, ra, b, rb, axis)
        if (mass1 < 1e-9f) mass1 = 1e-9f
        if (mass2 < 1e-9f) mass2 = 1e-9f
        if (axialMass < 1e-9f) axialMass = 1e-9f

        // locked relative orientation
        val qErr = bodyA.rotation.mul(refRot).mul(b.rotation.conjugate())
        angBias.set(JointMath3D.rotVec(qErr)).scale(baumgarte * invDt)
        JointMath3D.angularMass(bodyA, b, ka)

        // axial coordinate (0 = rest)
        position = ((b.position + rb) - (bodyA.position + ra)).dot(axis) - restOffset

        motorBias = if (motor.enabled) motor.targetVelocity else 0f
        limitMode = 0
        limitBias = 0f
        if (limits.isLimited) {
            if (position < limits.lower) {
                limitMode = -1; limitBias = baumgarte * invDt * (limits.lower - position)
            } else if (position > limits.upper) {
                limitMode = 1; limitBias = baumgarte * invDt * (limits.upper - position)
            }
        }
        if (limitMode == 0) limitImpulse = 0f

        JointMath3D.applyPointImpulse(bodyA, perpImpulse, ra)
        JointMath3D.applyPointImpulse(b, -perpImpulse, rb)
        JointMath3D.applyAngularImpulse(bodyA, angImpulse)
        JointMath3D.applyAngularImpulse(b, -angImpulse)
        if (motor.enabled && motorImpulse != 0f) applyAxial(motorImpulse)
        if (limitMode != 0 && limitImpulse != 0f) applyAxial(limitImpulse)
    }


    override fun solve(iterations: Int) {
        if (!enabled || broken) return
        val b = bodyB!!
        // 1) lock the two translation axes perpendicular to the slide axis
        var vrel = (bodyA.linearVelocity + bodyA.angularVelocity.cross(ra)) -
            (b.linearVelocity + b.angularVelocity.cross(rb))
        val l1 = -(vrel.dot(t1) + baumgarte * invDt * cErr.dot(t1)) / mass1
        val l2 = -(vrel.dot(t2) + baumgarte * invDt * cErr.dot(t2)) / mass2
        tmp.set(t1).scale(l1).addScaled(t2, l2)
        perpImpulse.add(tmp)
        JointMath3D.applyPointImpulse(bodyA, tmp, ra)
        JointMath3D.applyPointImpulse(b, tmp2.set(tmp).scale(-1f), rb)
        // 2) lock the relative orientation
        val wrel = bodyA.angularVelocity - b.angularVelocity
        rhs.set(wrel).add(angBias).scale(-1f)
        if (JointMath3D.solve(ka, rhs, lambda, scratch)) {
            angImpulse.add(lambda)
            JointMath3D.applyAngularImpulse(bodyA, lambda)
            JointMath3D.applyAngularImpulse(b, tmp2.set(lambda).scale(-1f))
        }
        // 3) axial motor (bilateral, force limited)
        if (motor.enabled) {
            vrel = (bodyA.linearVelocity + bodyA.angularVelocity.cross(ra)) -
                (b.linearVelocity + b.angularVelocity.cross(rb))
            val l = -(vrel.dot(axis) + motorBias) / axialMass
            val old = motorImpulse
            val maxImp = if (motor.maxForce > 0f) motor.maxForce * stepDt else Float.MAX_VALUE
            motorImpulse = (old + l).coerceIn(-maxImp, maxImp)
            applyAxial(motorImpulse - old)
        } else {
            motorImpulse = 0f
        }
        // 4) travel limit (unilateral)
        if (limitMode != 0) {
            vrel = (bodyA.linearVelocity + bodyA.angularVelocity.cross(ra)) -
                (b.linearVelocity + b.angularVelocity.cross(rb))
            val l = -(vrel.dot(axis) + limitBias) / axialMass
            val old = limitImpulse
            val next = if (limitMode < 0) min(old + l, 0f) else max(old + l, 0f)
            limitImpulse = next
            applyAxial(next - old)
        }
        checkBreak(perpImpulse.length(), angImpulse.length() + abs(motorImpulse) + abs(limitImpulse))
    }

    /** Applies an axial linear impulse pair: `+lambda` on A, `-lambda` on B. */
    private fun applyAxial(lambda: Float) {
        if (lambda == 0f) return
        val p = axis.scale(lambda)
        JointMath3D.applyPointImpulse(bodyA, p, ra)
        JointMath3D.applyPointImpulse(bodyB, p.scale(-1f), rb)
    }

    override val coordinate: Float get() = position

    override val coordinateVelocity: Float
        get() = ((bodyB!!.linearVelocity + bodyB!!.angularVelocity.cross(rb)) -
            (bodyA.linearVelocity + bodyA.angularVelocity.cross(ra))).dot(axis)

    override fun coordinateMass(): Float = axialMass

    override fun applyCoordinateImpulse(lambda: Float) {
        applyAxial(-lambda)
        perpImpulse.addScaled(axis, -lambda)
    }

    override fun describe(): String =
        "Slider(pos=${"%.4f".format(position)}, $limits, $motor)"
}

/**
 * Cone-twist joint: a ball socket plus a swing cone limit about the primary axis and a twist limit
 * about the same axis. Both limits are unilateral (they only push back when exceeded), so the joint
 * is free inside the cone.
 *
 * @param coneAngle maximum swing angle in radians, in `(0, PI/2]`.
 * @param twistLimits allowed twist range in radians relative to the construction pose.
 */
class ConeTwistJoint3D(
    bodyA: RigidBodyPro3D,
    bodyB: RigidBodyPro3D?,
    worldAnchor: Vec3,
    worldAxis: Vec3,
    var coneAngle: Float = (Math.PI / 4.0).toFloat(),
    val twistLimits: JointLimits = JointLimits(),
) : ProJoint3D(bodyA, bodyB ?: worldFrameBody(worldAnchor)) {

    override val kind: String = "ConeTwist"

    private val localAnchorA = Vec3()
    private val localAnchorB = Vec3()
    private val axisA = Vec3()
    private val axisB = Vec3()

    private val ra = Vec3(); private val rb = Vec3()
    private val aW = Vec3(0f, 1f, 0f)
    private val cErr = Vec3(); private val bias = Vec3()
    private val e = Vec3(); private val dir = Vec3()
    private val pointImpulse = Vec3()
    private val swingImpulse = Vec3()
    private val rhs = Vec3(); private val lambda = Vec3(); private val tmp2 = Vec3()
    private val km = JointMat3(); private val scratch = JointMat3()

    private var stepDt = 0f
    private var swingMass = 1f
    private var swingBias = 0f
    private var swingActive = false
    private var swingLambda = 0f
    private var twistMass = 1f
    private var twistBias = 0f
    private var twistMode = 0
    private var twistImpulse = 0f
    private var refTwist = 0f

    /** Current swing angle (radians) between the two primary axes. */
    var swingAngle = 0f
        private set

    /** Current twist angle (radians) relative to the construction pose. */
    var twistAngle = 0f
        private set

    init {
        val b = bodyB!!
        localAnchorA.set(bodyA.rotation.rotateInverse(worldAnchor - bodyA.position))
        localAnchorB.set(b.rotation.rotateInverse(worldAnchor - b.position))
        val n = worldAxis.normalizedOr(Vec3.UP)
        axisA.set(bodyA.rotation.rotateInverse(n))
        axisB.set(b.rotation.rotateInverse(n))
        val qRel = bodyA.rotation.conjugate().mul(b.rotation).normalize()
        refTwist = JointMath3D.twistAbout(qRel, axisA)
    }

    override fun prepare(dt: Float) {
        stepDt = dt
        updateTimestep(dt)
        if (!enabled || broken) return
        val b = bodyB!!
        ra.set(bodyA.rotation.rotate(localAnchorA))
        rb.set(b.rotation.rotate(localAnchorB))
        JointMath3D.pointMass(bodyA, ra, b, rb, km)
        cErr.set(bodyA.position + ra).sub(b.position + rb)
        bias.set(cErr).scale(baumgarte * invDt)

        val aA = bodyA.rotation.rotate(axisA)
        val aB = b.rotation.rotate(axisB)
        aW.set(aA)
        e.crossSet(aA, aB)
        val eLen = e.length()
        swingAngle = Math.toDegrees(atan2(eLen.toDouble(), aA.dot(aB).toDouble()).coerceAtLeast(0.0)).toFloat()
        val limit = (Math.PI / 2.0).toFloat().coerceAtMost(coneAngle).coerceAtLeast(0f)
        swingActive = eLen > 1e-6f && eLen > kotlin.math.sin(limit)
        if (swingActive) {
            dir.set(e).scale(1f / eLen)
            swingMass = JointMath3D.angularScalarMass(bodyA, b, dir)
            if (swingMass < 1e-9f) swingMass = 1e-9f
            swingBias = baumgarte * invDt * (eLen - kotlin.math.sin(limit))
        } else {
            swingImpulse.zero()
            swingLambda = 0f
        }

        val qRel = bodyA.rotation.conjugate().mul(b.rotation).normalize()
        twistAngle = JointMath3D.twistAbout(qRel, axisA) - refTwist
        twistMass = JointMath3D.angularScalarMass(bodyA, b, aW)
        if (twistMass < 1e-9f) twistMass = 1e-9f
        twistMode = 0
        twistBias = 0f
        if (twistLimits.isLimited) {
            if (twistAngle < twistLimits.lower) {
                twistMode = -1; twistBias = baumgarte * invDt * (twistLimits.lower - twistAngle)
            } else if (twistAngle > twistLimits.upper) {
                twistMode = 1; twistBias = baumgarte * invDt * (twistLimits.upper - twistAngle)
            }
        }
        if (twistMode == 0) twistImpulse = 0f

        JointMath3D.applyPointImpulse(bodyA, pointImpulse, ra)
        JointMath3D.applyPointImpulse(b, -pointImpulse, rb)
        if (swingActive) applySwing(swingImpulse)
        if (twistMode != 0 && twistImpulse != 0f) applyTwist(twistImpulse)
    }

    override fun solve(iterations: Int) {
        if (!enabled || broken) return
        val b = bodyB!!
        val vrel = (bodyA.linearVelocity + bodyA.angularVelocity.cross(ra)) -
            (b.linearVelocity + b.angularVelocity.cross(rb))
        rhs.set(vrel).add(bias).scale(-1f)
        if (JointMath3D.solve(km, rhs, lambda, scratch)) {
            pointImpulse.add(lambda)
            JointMath3D.applyPointImpulse(bodyA, lambda, ra)
            JointMath3D.applyPointImpulse(b, -lambda, rb)
        }
        if (swingActive) {
            // Unilateral row: only a negative (corrective) impulse is allowed, so the joint is free
            // inside the cone.
            val wrel = bodyA.angularVelocity - b.angularVelocity
            val l = -(wrel.dot(dir) + swingBias) / swingMass
            val old = swingLambda
            val next = min(old + l, 0f)
            swingLambda = next
            applySwing(dir.scale(next - old))
            swingImpulse.set(dir).scale(next)
        }
        if (twistMode != 0) {
            val wrel = bodyA.angularVelocity - b.angularVelocity
            val l = -(wrel.dot(aW) + twistBias) / twistMass
            val old = twistImpulse
            val next = if (twistMode < 0) min(old + l, 0f) else max(old + l, 0f)
            twistImpulse = next
            applyTwist(next - old)
        }
        checkBreak(pointImpulse.length(), swingImpulse.length() + abs(twistImpulse))
    }

    /** Applies an angular impulse `lambda * dir` on A and the opposite on B. */
    private fun applySwing(lambda: Vec3) {
        if (lambda.lengthSq() == 0f) return
        JointMath3D.applyAngularImpulse(bodyA, lambda)
        JointMath3D.applyAngularImpulse(bodyB, tmp2.set(lambda).scale(-1f))
    }

    /** Applies an angular impulse `lambda * aW` on A and the opposite on B. */
    private fun applyTwist(lambda: Float) {
        if (lambda == 0f) return
        JointMath3D.applyAngularImpulse(bodyA, aW.scale(lambda))
        JointMath3D.applyAngularImpulse(bodyB, aW.scale(-lambda))
    }

    override fun describe(): String =
        "ConeTwist(swing=${"%.3f".format(swingAngle)}° cone=${"%.3f".format(Math.toDegrees(coneAngle.toDouble()))}° " +
            "twist=${"%.3f".format(twistAngle)}, $twistLimits)"
}

/**
 * Distance joint: keeps the two anchor points at [restLength]. When [rope] is true the constraint is
 * unilateral (it only pulls, never pushes), which is what makes a rope/chain behave.
 *
 * Pass `bodyB = null` to tether [bodyA] to a fixed world point.
 */
class DistanceJoint3D(
    bodyA: RigidBodyPro3D,
    bodyB: RigidBodyPro3D?,
    worldAnchorA: Vec3,
    worldAnchorB: Vec3,
    distance: Float,
    val rope: Boolean = false,
) : ProJoint3D(bodyA, bodyB ?: worldFrameBody(worldAnchorB)) {

    override val kind: String = "Distance"

    /** Target distance in metres. */
    var restLength: Float = distance

    private val localAnchorA = Vec3()
    private val localAnchorB = Vec3()
    private val ra = Vec3(); private val rb = Vec3()
    private val n = Vec3(1f, 0f, 0f)
    private val impulseVec = Vec3()
    private val rhs = Vec3(); private val lambda = Vec3()
    private var impulse = 0f
    private var mass = 1f
    private var bias = 0f

    /** Current anchor separation in metres. */
    var length = 0f
        private set

    init {
        val b = bodyB!!
        localAnchorA.set(bodyA.rotation.rotateInverse(worldAnchorA - bodyA.position))
        localAnchorB.set(b.rotation.rotateInverse(worldAnchorB - b.position))
    }

    override fun prepare(dt: Float) {
        updateTimestep(dt)
        if (!enabled || broken) return
        val b = bodyB!!
        ra.set(bodyA.rotation.rotate(localAnchorA))
        rb.set(b.rotation.rotate(localAnchorB))
        val d = (b.position + rb) - (bodyA.position + ra)
        length = d.length()
        n.set(d).normalizedOr(Vec3.UP)
        mass = JointMath3D.linearScalarMass(bodyA, ra, b, rb, n)
        if (mass < 1e-9f) mass = 1e-9f
        // J = (vA - vB).n = -d(length)/dt, so the target J is +beta/dt * (length - rest).
        bias = -baumgarte * invDt * (length - restLength)
        JointMath3D.applyPointImpulse(bodyA, impulseVec, ra)
        JointMath3D.applyPointImpulse(b, tmpNeg(impulseVec), rb)
    }

    override fun solve(iterations: Int) {
        if (!enabled || broken) return
        val b = bodyB!!
        val vrel = (bodyA.linearVelocity + bodyA.angularVelocity.cross(ra)) -
            (b.linearVelocity + b.angularVelocity.cross(rb))
        val l = -(vrel.dot(n) + bias) / mass
        val old = impulse
        impulse = if (rope) max(old + l, 0f) else old + l
        val applied = impulse - old
        if (applied != 0f) {
            impulseVec.addScaled(n, applied)
            JointMath3D.applyPointImpulse(bodyA, n.scale(applied), ra)
            JointMath3D.applyPointImpulse(b, n.scale(-applied), rb)
        }
        checkBreak(abs(impulse), 0f)
    }

    private fun tmpNeg(v: Vec3): Vec3 = Vec3(-v.x, -v.y, -v.z)

    override fun describe(): String =
        "Distance(len=${"%.4f".format(length)} rest=${"%.4f".format(restLength)}${if (rope) " rope" else ""})"
}


/**
 * Soft spring-damper distance joint, implemented as a *soft* sequential-impulse constraint
 * (Catto's soft-constraint formulation): stiffness and damping are given through a natural
 * [frequencyHz] and a [dampingRatio], which keeps the spring stable at any timestep and any mass
 * ratio. The bodies settle at [restLength] under a constant load plus the static sag `F/k`.
 *
 * Pass `bodyB = null` to hang [bodyA] from a fixed world point.
 */
class SpringJoint3D(
    bodyA: RigidBodyPro3D,
    bodyB: RigidBodyPro3D?,
    worldAnchorA: Vec3,
    worldAnchorB: Vec3,
    restLength: Float,
    var frequencyHz: Float = 2f,
    var dampingRatio: Float = 1f,
) : ProJoint3D(bodyA, bodyB ?: worldFrameBody(worldAnchorB)) {

    override val kind: String = "Spring"

    /** Target length in metres. */
    var restLength: Float = restLength

    /** Maximum force in N (0 = unlimited). */
    var maxForce = 0f

    private val localAnchorA = Vec3()
    private val localAnchorB = Vec3()
    private val ra = Vec3(); private val rb = Vec3()
    private val n = Vec3(1f, 0f, 0f)
    private val impulseVec = Vec3()
    private var impulse = 0f
    private var softMass = 0f
    private var gamma = 0f
    private var bias = 0f
    private var maxImpulse = Float.MAX_VALUE

    /** Current anchor separation in metres. */
    var length = 0f
        private set

    /** Current spring force magnitude in N (spring + damping). */
    var force = 0f
        private set

    init {
        val b = bodyB!!
        localAnchorA.set(bodyA.rotation.rotateInverse(worldAnchorA - bodyA.position))
        localAnchorB.set(b.rotation.rotateInverse(worldAnchorB - b.position))
    }

    override fun prepare(dt: Float) {
        updateTimestep(dt)
        if (!enabled || broken) return
        val b = bodyB!!
        ra.set(bodyA.rotation.rotate(localAnchorA))
        rb.set(b.rotation.rotate(localAnchorB))
        val d = (b.position + rb) - (bodyA.position + ra)
        length = d.length()
        n.set(d).normalizedOr(Vec3.UP)
        val k = JointMath3D.linearScalarMass(bodyA, ra, b, rb, n)
        val mEff = if (k > 1e-9f) 1f / k else 0f
        val omega = 2f * Math.PI.toFloat() * frequencyHz.coerceAtLeast(0.001f)
        val springK = mEff * omega * omega
        val springD = 2f * dampingRatio.coerceAtLeast(0f) * mEff * omega
        gamma = if (dt > 1e-6f) 1f / (dt * (springD + dt * springK)).coerceAtLeast(1e-9f) else 0f
        val softBeta = dt * springK * gamma
        softMass = if (k + gamma > 1e-9f) 1f / (k + gamma) else 0f
        bias = -softBeta * (length - restLength)
        force = springK * (length - restLength)
        maxImpulse = if (maxForce > 0f) maxForce * dt else Float.MAX_VALUE
        JointMath3D.applyPointImpulse(bodyA, impulseVec, ra)
        JointMath3D.applyPointImpulse(b, Vec3(-impulseVec.x, -impulseVec.y, -impulseVec.z), rb)
    }

    override fun solve(iterations: Int) {
        if (!enabled || broken || softMass <= 0f) return
        val b = bodyB!!
        val vrel = (bodyA.linearVelocity + bodyA.angularVelocity.cross(ra)) -
            (b.linearVelocity + b.angularVelocity.cross(rb))
        val j = vrel.dot(n)
        var l = -softMass * (j + bias + gamma * impulse)
        val old = impulse
        impulse = (old + l).coerceIn(-maxImpulse, maxImpulse)
        l = impulse - old
        if (l != 0f) {
            impulseVec.addScaled(n, l)
            JointMath3D.applyPointImpulse(bodyA, n.scale(l), ra)
            JointMath3D.applyPointImpulse(b, n.scale(-l), rb)
        }
        checkBreak(abs(impulse), 0f)
    }

    override fun describe(): String =
        "Spring(len=${"%.4f".format(length)} rest=${"%.4f".format(restLength)} f=${frequencyHz}Hz z=$dampingRatio)"
}

/**
 * Couples two single-DOF joints (hinge or slider) with a constant [ratio]: `coordA = ratio * coordB`.
 * Typical use is a gear train between two hinges or a rack-and-pinion between a hinge and a slider.
 *
 * The two coupled joints must be solved by the world as well (add all three joints to the world) and
 * should not run their own motor at the same time.
 */
class GearJoint3D(val jointA: Joint3D, val jointB: Joint3D, var ratio: Float = 1f) :
    ProJoint3D(jointA.bodyA, jointA.bodyB) {

    override val kind: String = "Gear"

    private val coordA: JointCoordinate3D =
        jointA as? JointCoordinate3D ?: error("GearJoint3D requires a hinge/slider as jointA")
    private val coordB: JointCoordinate3D =
        jointB as? JointCoordinate3D ?: error("GearJoint3D requires a hinge/slider as jointB")

    private var impulse = 0f
    private var mass = 1f
    private var bias = 0f

    /** Current constraint error `coordA - ratio * coordB`. */
    var error = 0f
        private set

    override fun prepare(dt: Float) {
        updateTimestep(dt)
        if (!enabled || broken) return
        error = coordA.coordinate - ratio * coordB.coordinate
        mass = coordA.coordinateMass() + ratio * ratio * coordB.coordinateMass()
        if (mass < 1e-9f) mass = 1e-9f
        bias = baumgarte * invDt * error
        coordA.applyCoordinateImpulse(impulse)
        coordB.applyCoordinateImpulse(-ratio * impulse)
    }

    override fun solve(iterations: Int) {
        if (!enabled || broken) return
        val j = coordA.coordinateVelocity - ratio * coordB.coordinateVelocity
        val l = -(j + bias) / mass
        impulse += l
        coordA.applyCoordinateImpulse(l)
        coordB.applyCoordinateImpulse(-ratio * l)
        checkBreak(abs(impulse), 0f)
    }

    override fun describe(): String = "Gear(ratio=$ratio, error=${"%.4f".format(error)})"
}


/**
 * Wheel joint: a suspension strut. The two bodies may translate along [worldAxis] (suspension
 * travel, with an optional unilateral travel range) and rotate about [worldAxis] (the wheel spins);
 * the two perpendicular translation axes and the two perpendicular rotation axes are locked.
 *
 * The suspension itself is a soft spring-damper ([frequencyHz]/[dampingRatio]) pulling the travel
 * back to 0, and [motor] can drive the wheel's rotation.
 */
class WheelJoint3D(
    bodyA: RigidBodyPro3D,
    bodyB: RigidBodyPro3D?,
    worldAnchor: Vec3,
    worldAxis: Vec3,
    val limits: JointLimits = JointLimits(-0.25f, 0.25f),
    val motor: JointMotor = JointMotor(),
    var frequencyHz: Float = 4f,
    var dampingRatio: Float = 0.7f,
) : ProJoint3D(bodyA, bodyB ?: worldFrameBody(worldAnchor)), JointCoordinate3D {

    override val kind: String = "Wheel"

    private val localAnchorA = Vec3()
    private val localAnchorB = Vec3()
    private val axisA = Vec3()
    private val axisB = Vec3()

    private val ra = Vec3(); private val rb = Vec3()
    private val axis = Vec3(0f, 1f, 0f)
    private val t1 = Vec3(1f, 0f, 0f); private val t2 = Vec3(0f, 0f, 1f)
    private val cErr = Vec3(); private val bias = Vec3()
    private val e = Vec3()
    private val perpImpulse = Vec3(); private val angImpulse = Vec3()
    private val springImpulse = Vec3()
    private val rhs = Vec3(); private val lambda = Vec3(); private val tmp2 = Vec3()
    private val km = JointMat3(); private val scratch = JointMat3()

    private var stepDt = 0f
    private var mass1 = 1f; private var mass2 = 1f
    private var alignMass1 = 1f; private var alignMass2 = 1f
    private var axialMass = 1f; private var angAxialMass = 1f
    private var springSoftMass = 0f; private var springGamma = 0f; private var springBias = 0f
    private var springLambda = 0f
    private var limitImpulse = 0f; private var limitBias = 0f; private var limitMode = 0
    private var motorImpulse = 0f
    private var restOffset = 0f

    /** Suspension travel along the axis relative to the rest position, in metres. */
    var suspension = 0f
        private set

    /** Relative wheel rotation about the axis, in radians. */
    var angle = 0f
        private set

    init {
        val b = bodyB!!
        localAnchorA.set(bodyA.rotation.rotateInverse(worldAnchor - bodyA.position))
        localAnchorB.set(b.rotation.rotateInverse(worldAnchor - b.position))
        val n = worldAxis.normalizedOr(Vec3.UP)
        axisA.set(bodyA.rotation.rotateInverse(n))
        axisB.set(b.rotation.rotateInverse(n))
        val d = (b.position + b.rotation.rotate(localAnchorB)) - (bodyA.position + bodyA.rotation.rotate(localAnchorA))
        restOffset = d.dot(n)
    }

    /** World-space wheel/suspension axis (derived from body A). */
    fun worldAxis(out: Vec3 = Vec3()): Vec3 = out.set(bodyA.rotation.rotate(axisA))

    override fun prepare(dt: Float) {
        stepDt = dt
        updateTimestep(dt)
        if (!enabled || broken) return
        val b = bodyB!!
        ra.set(bodyA.rotation.rotate(localAnchorA))
        rb.set(b.rotation.rotate(localAnchorB))
        axis.set(bodyA.rotation.rotate(axisA))
        JointMath3D.perpendicular(axis, t1, t2)
        cErr.set(bodyA.position + ra).sub(b.position + rb)
        bias.set(cErr).scale(baumgarte * invDt)
        mass1 = JointMath3D.linearScalarMass(bodyA, ra, b, rb, t1).coerceAtLeast(1e-9f)
        mass2 = JointMath3D.linearScalarMass(bodyA, ra, b, rb, t2).coerceAtLeast(1e-9f)
        axialMass = JointMath3D.linearScalarMass(bodyA, ra, b, rb, axis).coerceAtLeast(1e-9f)
        angAxialMass = JointMath3D.angularScalarMass(bodyA, b, axis).coerceAtLeast(1e-9f)

        val aB = b.rotation.rotate(axisB)
        e.crossSet(axis, aB)
        alignMass1 = JointMath3D.angularScalarMass(bodyA, b, t1).coerceAtLeast(1e-9f)
        alignMass2 = JointMath3D.angularScalarMass(bodyA, b, t2).coerceAtLeast(1e-9f)

        suspension = ((b.position + rb) - (bodyA.position + ra)).dot(axis) - restOffset
        angle -= (bodyA.angularVelocity - b.angularVelocity).dot(axis) * dt

        // soft suspension spring pulling the travel back to 0
        val mEff = 1f / axialMass
        val omega = 2f * Math.PI.toFloat() * frequencyHz.coerceAtLeast(0.001f)
        val springK = mEff * omega * omega
        val springD = 2f * dampingRatio.coerceAtLeast(0f) * mEff * omega
        springGamma = if (dt > 1e-6f) 1f / (dt * (springD + dt * springK)).coerceAtLeast(1e-9f) else 0f
        val softBeta = dt * springK * springGamma
        springSoftMass = 1f / (axialMass + springGamma)
        springBias = -softBeta * suspension

        limitMode = 0
        limitBias = 0f
        if (limits.isLimited) {
            if (suspension < limits.lower) {
                limitMode = -1; limitBias = baumgarte * invDt * (limits.lower - suspension)
            } else if (suspension > limits.upper) {
                limitMode = 1; limitBias = baumgarte * invDt * (limits.upper - suspension)
            }
        }
        if (limitMode == 0) limitImpulse = 0f

        JointMath3D.applyPointImpulse(bodyA, perpImpulse, ra)
        JointMath3D.applyPointImpulse(b, tmp2.set(perpImpulse).scale(-1f), rb)
        JointMath3D.applyAngularImpulse(bodyA, angImpulse)
        JointMath3D.applyAngularImpulse(b, tmp2.set(angImpulse).scale(-1f))
        JointMath3D.applyPointImpulse(bodyA, springImpulse, ra)
        JointMath3D.applyPointImpulse(b, tmp2.set(springImpulse).scale(-1f), rb)
        if (limitMode != 0 && limitImpulse != 0f) applyAxial(limitImpulse)
        if (motor.enabled && motorImpulse != 0f) applyAngularAxial(motorImpulse)
    }


    override fun solve(iterations: Int) {
        if (!enabled || broken) return
        val b = bodyB!!
        // 1) lock the perpendicular translation axes
        var vrel = (bodyA.linearVelocity + bodyA.angularVelocity.cross(ra)) -
            (b.linearVelocity + b.angularVelocity.cross(rb))
        val l1 = -(vrel.dot(t1) + baumgarte * invDt * cErr.dot(t1)) / mass1
        val l2 = -(vrel.dot(t2) + baumgarte * invDt * cErr.dot(t2)) / mass2
        val p = Vec3().set(t1).scale(l1).addScaled(t2, l2)
        perpImpulse.add(p)
        JointMath3D.applyPointImpulse(bodyA, p, ra)
        JointMath3D.applyPointImpulse(b, tmp2.set(p).scale(-1f), rb)
        // 2) keep the two axes aligned
        val wrel = bodyA.angularVelocity - b.angularVelocity
        val a1 = -(wrel.dot(t1) + baumgarte * invDt * e.dot(t1)) / alignMass1
        val a2 = -(wrel.dot(t2) + baumgarte * invDt * e.dot(t2)) / alignMass2
        val pa = Vec3().set(t1).scale(a1).addScaled(t2, a2)
        angImpulse.add(pa)
        JointMath3D.applyAngularImpulse(bodyA, pa)
        JointMath3D.applyAngularImpulse(b, tmp2.set(pa).scale(-1f))
        // 3) soft suspension spring along the axis
        vrel = (bodyA.linearVelocity + bodyA.angularVelocity.cross(ra)) -
            (b.linearVelocity + b.angularVelocity.cross(rb))
        val j = vrel.dot(axis)
        val old = springLambda
        springLambda = old - springSoftMass * (j + springBias + springGamma * old)
        val sl = springLambda - old
        if (sl != 0f) {
            springImpulse.addScaled(axis, sl)
            applyAxial(sl)
        }
        // 4) hard travel limits (unilateral)
        if (limitMode != 0) {
            vrel = (bodyA.linearVelocity + bodyA.angularVelocity.cross(ra)) -
                (b.linearVelocity + b.angularVelocity.cross(rb))
            val l = -(vrel.dot(axis) + limitBias) / axialMass
            val prev = limitImpulse
            val next = if (limitMode < 0) min(prev + l, 0f) else max(prev + l, 0f)
            limitImpulse = next
            applyAxial(next - prev)
        }
        // 5) wheel motor about the axis (bilateral, force limited)
        if (motor.enabled) {
            val w = (bodyA.angularVelocity - b.angularVelocity).dot(axis)
            val l = -(w + motor.targetVelocity) / angAxialMass
            val prev = motorImpulse
            val maxImp = if (motor.maxForce > 0f) motor.maxForce * stepDt else Float.MAX_VALUE
            motorImpulse = (prev + l).coerceIn(-maxImp, maxImp)
            applyAngularAxial(motorImpulse - prev)
        } else {
            motorImpulse = 0f
        }
        checkBreak(
            perpImpulse.length() + abs(springLambda),
            angImpulse.length() + abs(motorImpulse) + abs(limitImpulse)
        )
    }

    /** Applies an axial linear impulse pair: `+lambda` on A, `-lambda` on B. */
    private fun applyAxial(lambda: Float) {
        if (lambda == 0f) return
        JointMath3D.applyPointImpulse(bodyA, axis.scale(lambda), ra)
        JointMath3D.applyPointImpulse(bodyB, axis.scale(-lambda), rb)
    }

    /** Applies an axial angular impulse pair: `+lambda` on A, `-lambda` on B. */
    private fun applyAngularAxial(lambda: Float) {
        if (lambda == 0f) return
        JointMath3D.applyAngularImpulse(bodyA, axis.scale(lambda))
        JointMath3D.applyAngularImpulse(bodyB, axis.scale(-lambda))
    }

    override val coordinate: Float get() = angle

    override val coordinateVelocity: Float
        get() = -(bodyA.angularVelocity - bodyB!!.angularVelocity).dot(axis)

    override fun coordinateMass(): Float = angAxialMass

    override fun applyCoordinateImpulse(lambda: Float) {
        applyAngularAxial(-lambda)
        angImpulse.addScaled(axis, -lambda)
    }

    override fun describe(): String =
        "Wheel(travel=${"%.4f".format(suspension)}, angle=${"%.3f".format(angle)}, $limits, $motor)"
}

/**
 * Wraps any [Joint3D] and disables it once the accumulated impulse exceeds [breakForce] /
 * [breakTorque] (inherited from [Joint3D]). The wrapper must be the joint registered in the world.
 */
class BreakableJoint3D(val inner: Joint3D) : Joint3D(inner.bodyA, inner.bodyB) {

    override val kind: String = "Breakable(${inner.kind})"

    /** Invoked once with the wrapper when the joint snaps. */
    var onBreak: ((BreakableJoint3D) -> Unit)? = null

    init {
        breakForce = inner.breakForce
        breakTorque = inner.breakTorque
    }

    /** The joint this wrapper breaks (delegated). */
    fun target(): Joint3D = inner

    override fun prepare(dt: Float) {
        if (!enabled || broken) return
        inner.prepare(dt)
    }

    override fun solve(iterations: Int) {
        if (broken || !enabled) return
        inner.solve(iterations)
        val torque = (inner as? ProJoint3D)?.accumulatedTorqueImpulse ?: 0f
        if (breakForce < Float.MAX_VALUE && inner.accumulatedImpulse > breakForce) snap()
        else if (breakTorque < Float.MAX_VALUE && torque > breakTorque) snap()
        accumulatedImpulse = inner.accumulatedImpulse
    }

    private fun snap() {
        if (broken) return
        broken = true
        inner.broken = true
        inner.enabled = false
        enabled = false
        onBreak?.invoke(this)
    }

    override fun wakeBodies() = inner.wakeBodies()

    override fun describe(): String = "Breakable(${inner.describe()})"
}

