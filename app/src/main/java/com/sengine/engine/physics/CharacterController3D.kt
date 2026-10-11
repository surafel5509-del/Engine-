package com.sengine.engine.physics

import com.sengine.engine.math.Vec3
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * Ultimate physics (v7) — FEATURE layer: capsule character controller.
 *
 * The controller is a *kinematic* mover: it never creates a rigid body of its own. It resolves the
 * character capsule against the world with raycasts (`PhysicsWorldApi3D.raycast`) so it works with
 * every shape the core supports and stays deterministic.
 */

/** Tunables of [CharacterController3D]. */
class CharacterControllerConfig3D {
    /** Capsule radius in metres. */
    var radius = 0.4f

    /** Total capsule height in metres (bottom of the lower hemisphere to the top of the upper one). */
    var height = 1.8f

    /** Maximum walkable slope in degrees. Steeper surfaces are treated as walls. */
    var slopeLimitDeg = 45f

    /** Maximum obstacle height (metres) the character can climb in one step. */
    var stepOffset = 0.4f

    /** Maximum distance the controller snaps back to the ground while walking. */
    var snapDistance = 0.35f

    /** Small penetration margin kept between the capsule and the world. */
    var skinWidth = 0.02f

    /** Downward acceleration in m/s². */
    var gravity = 20f

    /** Terminal downward speed in m/s. */
    var maxFallSpeed = 60f

    /** Height reached by [CharacterController3D.jump] (no argument). */
    var jumpHeight = 1.1f

    /** Collision filter used for the character probes (null = collide with everything). */
    var filter: CollisionFilter? = null

    /** Enables the automatic step-up resolution. */
    var enableStepUp = true

    /** Number of probe heights used for the horizontal sweep (odd numbers are safest). */
    var probeHeights = 3

    fun copy(): CharacterControllerConfig3D = CharacterControllerConfig3D().also {
        it.radius = radius; it.height = height; it.slopeLimitDeg = slopeLimitDeg
        it.stepOffset = stepOffset; it.snapDistance = snapDistance; it.skinWidth = skinWidth
        it.gravity = gravity; it.maxFallSpeed = maxFallSpeed; it.jumpHeight = jumpHeight
        it.filter = filter?.copy(); it.enableStepUp = enableStepUp; it.probeHeights = probeHeights
    }
}

/** Result of one [CharacterController3D.move] call. */
class CharacterMoveResult3D {
    /** Displacement actually applied this call (metres). */
    val delta = Vec3()

    /** True when the capsule rests on a walkable surface at the end of the move. */
    var grounded = false

    /** True when the horizontal motion was blocked by a wall. */
    var hitWall = false

    /** True when the upward motion was blocked by a ceiling. */
    var hitCeiling = false

    /** True when the controller climbed a step. */
    var steppedUp = false

    /** True when the controller touched the ground during this call. */
    var landed = false

    /** Angle of the ground under the capsule, in degrees (0 = flat). */
    var slopeAngle = 0f

    /** Ground normal under the capsule. */
    val groundNormal = Vec3(0f, 1f, 0f)

    /** Body the capsule is standing on (null when airborne). */
    var groundBody: RigidBodyPro3D? = null

    internal fun reset() {
        delta.zero(); grounded = false; hitWall = false; hitCeiling = false
        steppedUp = false; landed = false; slopeAngle = 0f
        groundNormal.set(0f, 1f, 0f); groundBody = null
    }

    override fun toString(): String =
        "Move(delta=$delta grounded=$grounded wall=$hitWall ceiling=$hitCeiling step=$steppedUp slope=${"%.1f".format(slopeAngle)}°)"
}

/**
 * Kinematic capsule character controller: slope limit, step offset, snap-to-ground, ground probing,
 * wall sliding, moving platforms, jump/gravity state.
 *
 * ```kotlin
 * val cc = CharacterController3D(world, CharacterControllerConfig3D(), Vec3(0f, 0.1f, 0f))
 * cc.ignore = myOwnCollider            // optional: the body that represents the character
 * val r = cc.move(Vec3(0.05f, 0f, 0f), dt)   // 3 m/s forward at 60 Hz
 * if (r.grounded && jumpPressed) cc.jump()
 * ```
 *
 * [position] is the *feet* position (the lowest point of the capsule on its centre axis); the capsule
 * centre is `position + UP * height / 2`.
 */
class CharacterController3D(
    val world: PhysicsWorldApi3D,
    val config: CharacterControllerConfig3D = CharacterControllerConfig3D(),
    startPosition: Vec3 = Vec3(),
) {

    /** Feet position (bottom of the capsule). */
    val position = Vec3(startPosition)

    /** Horizontal velocity carried over between calls (used by moving platforms and slopes). */
    val velocity = Vec3()

    /** Vertical velocity in m/s (negative = falling). */
    var verticalVelocity = 0f

    /** True when the capsule stands on a walkable surface. */
    var grounded = false
        private set

    /** Normal of the ground under the capsule. */
    val groundNormal = Vec3(0f, 1f, 0f)

    /** Ground angle in degrees. */
    var slopeAngle = 0f
        private set

    /** Body the capsule stands on (null when airborne). */
    var groundBody: RigidBodyPro3D? = null
        private set

    /** Body ignored by every probe (usually the character's own collider). */
    var ignore: RigidBodyPro3D? = null

    private val groundPoint = Vec3()
    private val result = CharacterMoveResult3D()

    /** Capsule radius (metres). */
    val radius: Float get() = config.radius

    /** Capsule total height (metres). */
    val height: Float get() = config.height

    /** Centre of the capsule in world space. */
    fun center(out: Vec3 = Vec3()): Vec3 = out.set(position.x, position.y + height * 0.5f, position.z)

    /** Ground contact point from the last probe. */
    fun groundContact(out: Vec3 = Vec3()): Vec3 = out.set(groundPoint)

    /** Result of the last [move] call (reused, do not keep a reference across calls). */
    fun lastResult(): CharacterMoveResult3D = result

    /** Teleports the capsule (also resets the vertical velocity). */
    fun teleport(p: Vec3) {
        position.set(p)
        velocity.zero()
        verticalVelocity = 0f
        grounded = false
        groundBody = null
    }

    /** Applies an upward velocity of [speed] m/s. */
    fun jump(speed: Float) {
        verticalVelocity = speed
        grounded = false
        groundBody = null
    }

    /** Jumps to the configured [CharacterControllerConfig3D.jumpHeight]. */
    fun jump() = jump(sqrt(2f * config.gravity * config.jumpHeight))

    /** True when the capsule is moving upwards. */
    val isJumping: Boolean get() = verticalVelocity > 0f
}


    private val lastWallNormal = Vec3(0f, 0f, 1f)

    /**
     * Moves the capsule by [desiredDelta] (world metres; the horizontal part is normally
     * `speed * dt`), integrating gravity over [dt] and resolving the world with raycasts.
     *
     * @return the reused [CharacterMoveResult3D] describing what actually happened.
     */
    fun move(desiredDelta: Vec3, dt: Float): CharacterMoveResult3D {
        val r = result
        r.reset()
        if (dt <= 0f) return r
        val startX = position.x; val startY = position.y; val startZ = position.z

        probeGround()
        if (grounded && verticalVelocity <= 0f) verticalVelocity = -STICK_SPEED
        else verticalVelocity = max(verticalVelocity - config.gravity * dt, -config.maxFallSpeed)

        // Moving platforms carry the character (linear + angular velocity at the contact point).
        val platform = Vec3()
        val gb = groundBody
        if (grounded && gb != null && !gb.isStatic && gb !== ignore) {
            platform.set(gb.getVelocityAt(groundPoint)).scale(dt)
        }

        val horiz = Vec3(desiredDelta.x + platform.x, 0f, desiredDelta.z + platform.z)
        if (grounded) {
            val d = horiz.dot(groundNormal)
            horiz.addScaled(groundNormal, -d)
        }
        velocity.set(horiz.x / dt, 0f, horiz.z / dt)
        resolveHorizontal(horiz, r)
        applyVertical(verticalVelocity * dt + platform.y, r)
        if (grounded) snapToGround()

        r.delta.set(position.x - startX, position.y - startY, position.z - startZ)
        r.grounded = grounded
        r.groundNormal.set(groundNormal)
        r.groundBody = groundBody
        r.slopeAngle = slopeAngle
        return r
    }

    /** Probes the ground under the capsule and refreshes [grounded]/[groundNormal]/[slopeAngle]. */
    fun probeGround(): Boolean {
        val originY = position.y + config.stepOffset + config.skinWidth
        val maxDist = config.stepOffset + config.snapDistance + config.skinWidth
        val hit = world.raycast(
            Vec3(position.x, originY, position.z),
            Vec3(position.x, originY - maxDist, position.z),
            config.filter, ignore
        )
        if (!hit.hit) {
            grounded = false; groundBody = null
            groundNormal.set(0f, 1f, 0f); slopeAngle = 0f
            return false
        }
        val ang = angleDeg(hit.normal)
        groundNormal.set(hit.normal)
        slopeAngle = ang
        groundPoint.set(hit.point)
        groundBody = hit.body
        grounded = ang <= config.slopeLimitDeg && verticalVelocity <= 0f
        return grounded
    }

    /** Casts [raysPerHeight]x3 rays and returns the closest allowed travel (<= [dist]). */
    private fun probeHorizontal(dir: Vec3, dist: Float): Float {
        var best = dist
        val side = Vec3(dir.z, 0f, -dir.x)
        val n = config.probeHeights.coerceAtLeast(1)
        val lo = config.radius * 0.5f
        val hi = max(lo, height - config.radius * 0.5f)
        val reach = dist + config.skinWidth
        for (i in 0 until n) {
            val h = if (n == 1) (lo + hi) * 0.5f else lo + (hi - lo) * i / (n - 1)
            for (o in 0 until 3) {
                val off = when (o) {
                    0 -> -config.radius * 0.98f
                    1 -> 0f
                    else -> config.radius * 0.98f
                }
                val ox = position.x + side.x * off
                val oz = position.z + side.z * off
                val oy = position.y + h
                val hit = world.raycast(
                    Vec3(ox, oy, oz),
                    Vec3(ox + dir.x * reach, oy, oz + dir.z * reach),
                    config.filter, ignore
                )
                if (hit.hit) {
                    val d = (hit.distance - config.skinWidth).coerceAtLeast(0f)
                    if (d < best) {
                        best = d
                        lastWallNormal.set(hit.normal)
                    }
                }
            }
        }
        return best
    }

    /** Moves horizontally, resolving walls (with step-up) and sliding along them. */
    private fun resolveHorizontal(horiz: Vec3, r: CharacterMoveResult3D): Vec3 {
        val dist = Vec3(horiz.x, 0f, horiz.z).length()
        if (dist < 1e-6f) return Vec3()
        val dir = Vec3(horiz.x / dist, 0f, horiz.z / dist)
        val travel = probeHorizontal(dir, dist)
        if (travel >= dist - 1e-4f) {
            position.addScaled(dir, dist)
            return Vec3(dir.x * dist, 0f, dir.z * dist)
        }
        r.hitWall = true
        if (grounded && config.enableStepUp && config.stepOffset > 0f) {
            val stepped = tryStep(dir, dist, r)
            if (stepped != null) return stepped
        }
        // Slide along the wall: remove the velocity component that pushes into it.
        val n = Vec3(lastWallNormal.x, 0f, lastWallNormal.z)
        val nl = n.length()
        if (nl < 1e-5f) return Vec3()
        n.scale(1f / nl)
        val slide = Vec3(dir.x * dist, 0f, dir.z * dist)
        val into = slide.dot(n)
        if (into < 0f) slide.addScaled(n, -into)
        val len = slide.length()
        if (len < 1e-6f) return Vec3()
        val sdir = Vec3(slide.x / len, 0f, slide.z / len)
        val st = probeHorizontal(sdir, len)
        position.addScaled(sdir, st)
        return Vec3(sdir.x * st, 0f, sdir.z * st)
    }


    /**
     * Attempts the classic "lift, advance, drop" step resolution. Returns the applied delta or null
     * when the obstacle is too tall/steep (the caller then falls back to wall sliding).
     */
    private fun tryStep(dir: Vec3, dist: Float, r: CharacterMoveResult3D): Vec3? {
        val sx = position.x; val sy = position.y; val sz = position.z
        position.y += config.stepOffset
        val travel = probeHorizontal(dir, dist)
        if (travel < 0.01f) {
            position.set(sx, sy, sz)
            return null
        }
        position.x += dir.x * travel
        position.z += dir.z * travel
        val originY = position.y + config.skinWidth
        val maxDrop = config.stepOffset + config.snapDistance + config.skinWidth
        val hit = world.raycast(
            Vec3(position.x, originY, position.z),
            Vec3(position.x, originY - maxDrop, position.z),
            config.filter, ignore
        )
        if (!hit.hit) {
            position.set(sx, sy, sz)
            return null
        }
        val rise = hit.point.y - sy
        val ang = angleDeg(hit.normal)
        if (rise < -config.snapDistance || rise > config.stepOffset + config.skinWidth || ang > config.slopeLimitDeg) {
            position.set(sx, sy, sz)
            return null
        }
        position.y = hit.point.y
        groundNormal.set(hit.normal)
        slopeAngle = ang
        groundBody = hit.body
        groundPoint.set(hit.point)
        grounded = true
        r.steppedUp = true
        return Vec3(position.x - sx, position.y - sy, position.z - sz)
    }

    /** Integrates the vertical motion, clamping against ceilings and floors. */
    private fun applyVertical(dv: Float, r: CharacterMoveResult3D) {
        if (dv > 1e-6f) {
            val originY = position.y + height
            val hit = world.raycast(
                Vec3(position.x, originY, position.z),
                Vec3(position.x, originY + dv + config.skinWidth, position.z),
                config.filter, ignore
            )
            if (hit.hit) {
                position.y += max(hit.distance - config.skinWidth, 0f)
                verticalVelocity = 0f
                r.hitCeiling = true
            } else {
                position.y += dv
            }
        } else if (dv < -1e-6f) {
            val originY = position.y + config.skinWidth
            val maxDist = -dv + config.snapDistance
            val hit = world.raycast(
                Vec3(position.x, originY, position.z),
                Vec3(position.x, originY - maxDist, position.z),
                config.filter, ignore
            )
            if (!hit.hit) {
                position.y += dv
                grounded = false
                return
            }
            val ang = angleDeg(hit.normal)
            val d = max(hit.distance - config.skinWidth, 0f)
            if (d <= -dv && ang <= config.slopeLimitDeg) {
                position.y -= d
                verticalVelocity = 0f
                grounded = true
                groundNormal.set(hit.normal)
                slopeAngle = ang
                groundBody = hit.body
                groundPoint.set(hit.point)
                r.landed = true
            } else {
                position.y += dv
                if (ang > config.slopeLimitDeg) grounded = false
            }
        }
    }

    /** Snaps the capsule back down to the walkable surface (prevents "floating" down slopes). */
    private fun snapToGround() {
        if (verticalVelocity > 0.01f) {
            grounded = false
            groundBody = null
            return
        }
        val originY = position.y + config.stepOffset + config.skinWidth
        val maxDist = config.stepOffset + config.snapDistance + config.skinWidth
        val hit = world.raycast(
            Vec3(position.x, originY, position.z),
            Vec3(position.x, originY - maxDist, position.z),
            config.filter, ignore
        )
        if (!hit.hit) {
            grounded = false
            groundBody = null
            return
        }
        val ang = angleDeg(hit.normal)
        if (ang > config.slopeLimitDeg) {
            grounded = false
            groundBody = null
            return
        }
        position.y = hit.point.y
        grounded = true
        groundNormal.set(hit.normal)
        slopeAngle = ang
        groundBody = hit.body
        groundPoint.set(hit.point)
    }

    /** Angle between [n] and world up, in degrees. */
    private fun angleDeg(n: Vec3): Float {
        val len = n.length()
        if (len < 1e-6f) return 0f
        val c = (n.y / len).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(c.toDouble())).toFloat()
    }

    override fun toString(): String =
        "Character($position grounded=$grounded slope=${"%.1f".format(slopeAngle)}° v=$verticalVelocity)"

    private companion object {
        /** Small downward velocity applied while grounded so the capsule stays in contact. */
        const val STICK_SPEED = 1f
    }
}

