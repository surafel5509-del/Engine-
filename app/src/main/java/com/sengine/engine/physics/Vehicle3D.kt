package com.sengine.engine.physics

import com.sengine.engine.math.Quat
import com.sengine.engine.math.Vec3
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Ultimate physics (v7) — FEATURE layer: raycast vehicle.
 *
 * The chassis is a normal rigid body; the wheels are *virtual*: each wheel casts a ray along its
 * suspension direction and applies a suspension force, a longitudinal drive/brake force and a
 * lateral tyre force to the chassis. This is the classic "raycast vehicle" model used by
 * Bullet/Unity and it needs nothing from the core solver beyond `raycast` and `applyForce`.
 */

/** Tunables of [Vehicle3D] (engine, gearbox, tyres, suspension, aerodynamics). */
class VehicleConfig3D {
    /** Chassis mass in kg (used for the engine/differential maths; the body's own mass is authoritative). */
    var chassisMass = 800f

    /** Peak engine torque in N·m. */
    var maxEngineTorque = 3200f

    /** Rev limiter. */
    var maxRpm = 6500f

    /** Idle engine speed. */
    var idleRpm = 900f

    /** Forward gear ratios (first = lowest gear). */
    var gearRatios = floatArrayOf(3.6f, 2.3f, 1.6f, 1.2f, 0.95f, 0.8f)

    /** Reverse gear ratio. */
    var reverseRatio = 3.4f

    /** Final drive ratio. */
    var finalDrive = 3.4f

    /** 0 = front wheel drive, 1 = rear wheel drive, 2 = all wheel drive. */
    var drive = 2

    /** 0 = open differential, 1 = fully locked (limited-slip blend). */
    var differentialLock = 0.4f

    /** Automatic gearbox upshift / downshift thresholds. */
    var shiftUpRpm = 5600f
    var shiftDownRpm = 2000f

    /** Maximum steer angle of the front wheels in degrees. */
    var maxSteeringAngleDeg = 32f

    /** Steering input smoothing speed (1/s). */
    var steeringSpeed = 4f

    /** Maximum braking torque per wheel in N·m. */
    var maxBrakeTorque = 4500f

    /** Anti-roll bar stiffness in N per metre of travel difference. */
    var antiRollBar = 6000f

    /** Aerodynamic drag coefficient `0.5 * rho * Cd * A` (N per (m/s)²). */
    var airDrag = 0.45f

    /** Rolling resistance as a fraction of the wheel load. */
    var rollingResistance = 0.015f

    /** Suspension rest length in metres (chassis connection point to wheel centre). */
    var suspensionRestLength = 0.35f

    /** Suspension spring stiffness in N/m. */
    var suspensionStiffness = 35000f

    /** Suspension damper in N·s/m. */
    var suspensionDamping = 2400f

    /** Maximum suspension compression in metres. */
    var suspensionMaxTravel = 0.3f

    /** Wheel radius in metres. */
    var wheelRadius = 0.35f

    /** Tyre friction coefficient (peak of the friction curve). */
    var wheelFrictionSlip = 1.5f

    /** Lateral slip stiffness scaling of the Pacejka curve. */
    var lateralStiffness = 9f

    /** Shape factor of the Pacejka curve (1.5 .. 2.0 is realistic). */
    var pacejkaShape = 1.9f

    /** Collision filter used for the wheel rays (null = everything). */
    var wheelFilter: CollisionFilter? = null

    /** Height of the chassis collision box (metres) — kept small so the wheels carry the body. */
    var chassisHalfHeight = 0.3f
}

/**
 * One virtual wheel of a [Vehicle3D].
 *
 * @param connectionPoint attachment point in chassis-local space (top of the suspension strut).
 * @param direction suspension direction in chassis-local space (usually straight down).
 * @param axle wheel axle direction in chassis-local space (usually +-X).
 * @param steering true for the steered (front) wheels.
 * @param driven true for the wheels that receive engine torque.
 */
class Wheel3D(
    val connectionPoint: Vec3,
    val direction: Vec3 = Vec3(0f, -1f, 0f),
    val axle: Vec3 = Vec3(-1f, 0f, 0f),
    var radius: Float = 0.35f,
    var suspensionRestLength: Float = 0.35f,
    var steering: Boolean = false,
    var driven: Boolean = false,
) {
    /** True when the wheel touches the ground this step. */
    var contact = false
        internal set

    /** World contact point (valid when [contact]). */
    val contactPoint = Vec3()

    /** World contact normal (valid when [contact]). */
    val contactNormal = Vec3(0f, 1f, 0f)

    /** Current suspension length (connection point to wheel centre). */
    var suspensionLength = suspensionRestLength
        internal set

    /** Suspension force applied this step, in N. */
    var suspensionForce = 0f
        internal set

    /** Accumulated wheel rotation angle in radians (for rendering). */
    var rotation = 0f
        internal set

    /** Current steering angle in radians. */
    var steerAngle = 0f
        internal set

    /** Longitudinal force applied this step, in N. */
    var forwardForce = 0f
        internal set

    /** Lateral (cornering) force applied this step, in N. */
    var lateralForce = 0f
        internal set

    /** Slip angle in radians (atan(lateral / forward)). */
    var slipAngle = 0f
        internal set

    /** How much of the available friction was used (1 = at the limit of adhesion). */
    var skidInfo = 1f
        internal set

    /** Body the wheel is resting on (null when airborne). */
    var groundBody: RigidBodyPro3D? = null
        internal set

    /** World position of the wheel centre. */
    val worldPosition = Vec3()

    /** Longitudinal force requested by the engine/gearbox this step, in N. */
    var driveForce = 0f

/**
 * Raycast vehicle: a rigid chassis plus any number of [Wheel3D] struts.
 *
 * ```kotlin
 * val car = Vehicle3D(world, chassisBody, VehicleConfig3D())
 * car.addWheel(Wheel3D(Vec3(-0.8f, -0.3f, 1.2f), steering = true, driven = true))
 * car.addWheel(Wheel3D(Vec3( 0.8f, -0.3f, 1.2f), steering = true, driven = true))
 * car.addWheel(Wheel3D(Vec3(-0.8f, -0.3f, -1.2f)).also { it.rear = true })
 * car.addWheel(Wheel3D(Vec3( 0.8f, -0.3f, -1.2f)).also { it.rear = true })
 * car.throttle = 1f; car.update(dt)
 * ```
 *
 * Call [update] once per fixed step (after the world step is fine; the forces are integrated by the
 * core solver on the next step).
 */
class Vehicle3D(
    val world: PhysicsWorldApi3D,
    val chassis: RigidBodyPro3D,
    val config: VehicleConfig3D = VehicleConfig3D(),
) {

    /** The vehicle's wheels, in insertion order (deterministic). */
    val wheels = ArrayList<Wheel3D>()

    /** Throttle input `0..1`. */
    var throttle = 0f

    /** Brake input `0..1`. */
    var brake = 0f

    /** Handbrake input `0..1` (applied to the wheels flagged [Wheel3D.rear]). */
    var handbrake = 0f

    /** Steering input `-1..1` (positive = right). */
    var steering = 0f
        private set

    /** Smoothed steering angle actually applied to the steered wheels, in radians. */
    var steeringAngle = 0f
        private set

    /** Current engine speed in rpm. */
    var engineRpm = 0f
        private set

    /** Torque produced by the engine this step, in N·m. */
    var engineTorque = 0f
        private set

    /** Current gear (1..n, 0 = neutral, -1 = reverse). */
    var gear = 1
        private set

    /** Signed forward speed in m/s. */
    var speed = 0f
        private set

    /** Total travelled distance in metres. */
    var odometer = 0f
        private set

    /** True when no wheel touches the ground. */
    var airborne = true
        private set

    private val fwdTmp = Vec3(0f, 0f, 1f)

    init {
        engineRpm = config.idleRpm
    }

    /** Number of wheels. */
    val wheelCount: Int get() = wheels.size

    /** Adds a wheel; its radius/rest length default to the config unless [useConfigDefaults] is false. */
    fun addWheel(wheel: Wheel3D, useConfigDefaults: Boolean = true): Wheel3D {
        if (useConfigDefaults) {
            wheel.radius = config.wheelRadius
            wheel.suspensionRestLength = config.suspensionRestLength
        }
        wheel.suspensionLength = wheel.suspensionRestLength
        wheels.add(wheel)
        return wheel
    }

    /** Sets the steering input in `-1..1`. */
    fun setSteering(value: Float) {
        steering = value.coerceIn(-1f, 1f)
    }

    /** Resets the gearbox/odometer state (keeps the inputs). */
    fun reset() {
        engineRpm = config.idleRpm
        engineTorque = 0f
        gear = 1
        speed = 0f
        odometer = 0f
        steeringAngle = 0f
        for (w in wheels) {
            w.contact = false; w.suspensionLength = w.suspensionRestLength
            w.suspensionForce = 0f; w.driveForce = 0f; w.forwardSpeed = 0f
        }
    }

    /** Average forward axis of the wheels (falls back to the chassis +Z axis). */
    fun forwardAxis(out: Vec3 = Vec3()): Vec3 {
        out.zero()
        for (w in wheels) out.add(w.forwardWorld)
        if (out.lengthSq() < 1e-8f) return out.set(chassis.rotation.rotate(Vec3(0f, 0f, 1f))).normalize()
        return out.normalize()
    }

    /** World transform (position + axle/up/forward axes) of every wheel, for the renderer. */
    fun wheelTransforms(out: MutableList<FloatArray> = ArrayList()): MutableList<FloatArray> {
        out.clear()
        for (w in wheels) {
            val a = w.axleWorld
            val u = w.suspDirection
            val f = w.forwardWorld
            out.add(
                floatArrayOf(
                    w.worldPosition.x, w.worldPosition.y, w.worldPosition.z,
                    a.x, a.y, a.z, u.x, u.y, u.z, f.x, f.y, f.z, w.rotation, w.radius
                )
            )
        }
        return out
    }

        internal set

    /** Forward speed at the contact point in m/s (negative = reversing). */
    var forwardSpeed = 0f
        internal set

    /** World suspension direction (chassis-local [direction] rotated into world space). */
    val suspDirection = Vec3(0f, -1f, 0f)
        internal set

    /** World axle direction (after steering is applied). */
    val axleWorld = Vec3(-1f, 0f, 0f)
        internal set

    /** World forward direction (perpendicular to [axleWorld] and [suspDirection]). */
    val forwardWorld = Vec3(0f, 0f, 1f)
        internal set

    /** True for the rear wheels (they also receive the handbrake). */
    var rear = false

    override fun toString(): String =
        "Wheel(contact=$contact len=${"%.3f".format(suspensionLength)} force=${"%.0f".format(suspensionForce)}N slip=${"%.1f".format(Math.toDegrees(slipAngle.toDouble()))}°)"
}

    /**
     * Advances the vehicle by [dt] seconds: steering smoothing, engine/gearbox, one suspension ray
     * per wheel, suspension/damping forces, Pacejka lateral tyre forces, drive/brake forces, the
     * friction circle, the anti-roll bars and the aero drag.
     */
    fun update(dt: Float) {
        if (dt <= 0f) return
        val mass = if (chassis.invMass > 0f) 1f / chassis.invMass else config.chassisMass
        val targetSteer = steering * Math.toRadians(config.maxSteeringAngleDeg.toDouble()).toFloat()
        steeringAngle += (targetSteer - steeringAngle) * min(1f, config.steeringSpeed * dt)

        updateEngine(dt)

        var contacts = 0
        for (w in wheels) {
            val conn = chassis.localToWorld(w.connectionPoint)
            val susp = w.suspDirection.set(chassis.rotation.rotate(w.direction)).normalize()
            val axleBase = chassis.rotation.rotate(w.axle).normalize()
            w.steerAngle = if (w.steering) steeringAngle else 0f
            val axle = if (w.steerAngle != 0f) Quat.fromAxisAngle(susp, w.steerAngle).rotate(axleBase).normalize() else axleBase
            w.axleWorld.set(axle)
            w.forwardWorld.set(axle).cross(susp).normalize()

            val maxLen = w.suspensionRestLength + w.radius + config.suspensionMaxTravel
            val hit = world.raycast(conn, Vec3(conn.x + susp.x * maxLen, conn.y + susp.y * maxLen, conn.z + susp.z * maxLen), config.wheelFilter, chassis)
            if (!hit.hit) {
                w.contact = false
                w.groundBody = null
                w.suspensionForce = 0f
                w.suspensionLength = w.suspensionRestLength + config.suspensionMaxTravel
                w.worldPosition.set(conn).addScaled(susp, w.suspensionLength)
                w.forwardSpeed = 0f
                continue
            }
            w.contact = true
            contacts++
            w.contactPoint.set(hit.point)
            w.contactNormal.set(hit.normal)
            w.groundBody = hit.body
            w.suspensionLength = (hit.distance - w.radius)
                .coerceIn(0f, w.suspensionRestLength + config.suspensionMaxTravel)
            w.worldPosition.set(conn).addScaled(susp, w.suspensionLength)

            val compression = (w.suspensionRestLength - w.suspensionLength).coerceAtLeast(0f)
            val contactVel = chassis.getVelocityAt(w.contactPoint)
            val suspVel = contactVel.dot(susp)
            var force = config.suspensionStiffness * compression + config.suspensionDamping * suspVel
            if (force < 0f) force = 0f
            w.suspensionForce = force
            chassis.applyForce(susp.scale(-force), w.contactPoint)

            // --- tyre forces -------------------------------------------------------------
            val fwd = w.forwardWorld
            val lat = w.axleWorld
            val fwdVel = contactVel.dot(fwd)
            val latVel = contactVel.dot(lat)
            w.forwardSpeed = fwdVel
            w.slipAngle = atan2(latVel, abs(fwdVel) + 0.5f)
            val maxFriction = config.wheelFrictionSlip * force
            var fy = -pacejka(w.slipAngle * config.lateralStiffness, config.pacejkaShape) * maxFriction
            var fx = w.driveForce
            // braking (plus handbrake on the rear wheels)
            val brakeInput = max(brake, if (w.rear) handbrake else 0f)
            if (brakeInput > 0.001f && abs(fwdVel) > 0.05f) {
                val want = config.maxBrakeTorque * brakeInput / w.radius
                val stop = abs(fwdVel) * mass / max(1, wheels.size) / dt
                fx -= sign(fwdVel) * min(want, stop)
            }
            // rolling resistance always opposes motion
            if (abs(fwdVel) > 0.05f) fx -= sign(fwdVel) * config.rollingResistance * force
            val mag = sqrt(fx * fx + fy * fy)
            w.skidInfo = if (mag > 1e-4f) (maxFriction / mag).coerceIn(0f, 1f) else 1f
            if (mag > maxFriction && mag > 1e-6f) {
                val s = maxFriction / mag
                fx *= s; fy *= s
            }
            w.forwardForce = fx
            w.lateralForce = fy
            chassis.applyForce(
                Vec3(fwd.x * fx + lat.x * fy, fwd.y * fx + lat.y * fy, fwd.z * fx + lat.z * fy),
                w.contactPoint
            )
            w.rotation += fwdVel / max(w.radius, 1e-3f) * dt
        }

        applyAntiRollBars()
        airborne = contacts == 0

        val fwd = forwardAxis()
        val v = chassis.linearVelocity
        speed = v.dot(fwd)
        odometer += abs(speed) * dt
        // aerodynamic drag (quadratic, opposing the motion)
        val dragMag = config.airDrag * v.lengthSq()
        if (dragMag > 1e-4f) chassis.applyForce(v.normalized().scale(-dragMag))
    }

    /** Anti-roll bar between wheel pairs (left/right). */
    private fun applyAntiRollBars() {
        if (config.antiRollBar <= 0f || wheels.size < 4) return
        applyAntiRoll(0, 1)
        applyAntiRoll(2, 3)
    }

    private fun applyAntiRoll(i: Int, j: Int) {
        if (i >= wheels.size || j >= wheels.size) return
        val a = wheels[i]; val b = wheels[j]
        if (!a.contact || !b.contact) return
        val travelA = a.suspensionRestLength - a.suspensionLength
        val travelB = b.suspensionRestLength - b.suspensionLength
        val f = (travelA - travelB) * config.antiRollBar
        if (f == 0f) return
        chassis.applyForce(a.suspDirection.scale(f), a.contactPoint)
        chassis.applyForce(b.suspDirection.scale(-f), b.contactPoint)
    }



    /**
     * Engine + automatic gearbox + limited-slip differential. Uses the wheel speeds measured on the
     * previous step (a one-frame lag, exactly like production raycast vehicles).
     */
    private fun updateEngine(dt: Float) {
        val driven = wheels.filter { it.driven }
        var wheelRpm = 0f
        if (driven.isNotEmpty()) {
            var n = 0
            var sum = 0f
            for (w in driven) if (w.contact) { sum += abs(w.forwardSpeed); n++ }
            if (n > 0) wheelRpm = (sum / n) / (TWO_PI * max(config.wheelRadius, 0.01f)) * 60f
        }
        val ratio = gearRatio()
        val target = (wheelRpm * abs(ratio) * config.finalDrive).coerceIn(config.idleRpm, config.maxRpm)
        engineRpm = (engineRpm + (target - engineRpm) * min(1f, 8f * dt)).coerceIn(config.idleRpm, config.maxRpm)

        if (gear > 0 && engineRpm > config.shiftUpRpm && gear < config.gearRatios.size) gear++
        else if (gear > 1 && engineRpm < config.shiftDownRpm) gear--
        if (gear >= 0) {
            if (speed < 0.3f && throttle <= 0.01f && brake > 0.5f) gear = -1
        } else if (throttle > 0.5f && brake <= 0.01f) {
            gear = 1
        }

        engineTorque = if (gear == 0) 0f else torqueAt(engineRpm) * throttle
        val axleTorque = engineTorque * ratio * config.finalDrive
        val base = if (driven.isNotEmpty()) axleTorque / max(config.wheelRadius, 0.01f) / driven.size else 0f
        for (w in wheels) w.driveForce = 0f
        if (base != 0f) {
            var avg = 0f
            for (w in driven) avg += abs(w.forwardSpeed)
            avg /= driven.size
            val weights = FloatArray(driven.size)
            var total = 0f
            for (i in driven.indices) {
                val slip = avg - abs(driven[i].forwardSpeed)
                weights[i] = (1f + config.differentialLock * (slip / (avg + 0.5f))).coerceIn(0.2f, 3f)
                total += weights[i]
            }
            for (i in driven.indices) driven[i].driveForce = base * weights[i] * driven.size / max(total, 1e-3f)
        }
        // engine braking with the throttle released
        if (throttle <= 0.01f) {
            val engineBrake = 90f * abs(ratio)
            for (w in driven) if (w.contact && abs(w.forwardSpeed) > 0.05f) {
                w.driveForce -= sign(w.forwardSpeed) * engineBrake
            }
        }
    }

    /** Torque curve in N·m: quadratic, peaking at 55 % of the usable rev range. */
    fun torqueAt(rpm: Float): Float {
        val x = ((rpm - config.idleRpm) / (config.maxRpm - config.idleRpm)).coerceIn(0f, 1f)
        val peak = 0.55f
        val f = 1f - ((x - peak) * (x - peak)) / (peak * peak)
        return config.maxEngineTorque * f.coerceIn(0.25f, 1f)
    }

    /** Simplified Pacejka magic formula returning a normalised force in `-1..1`. */
    private fun pacejka(slip: Float, shape: Float): Float {
        val bx = slip.coerceIn(-25f, 25f)
        val c = shape.coerceIn(1f, 2.5f)
        val inner = bx - 0.97f * (bx - atan(bx))
        return sin(c * atan(inner))
    }

    private fun gearRatio(): Float = when {
        gear == 0 -> 0f
        gear < 0 -> config.reverseRatio
        else -> config.gearRatios[(gear - 1).coerceIn(0, config.gearRatios.size - 1)]
    }

    override fun toString(): String =
        "Vehicle(wheels=$wheelCount rpm=${"%.0f".format(engineRpm)} gear=$gear speed=${"%.1f".format(speed)}m/s " +
            "odometer=${"%.1f".format(odometer)}m airborne=$airborne)"

    private companion object {
        const val TWO_PI = 6.2831855f
    }
}
