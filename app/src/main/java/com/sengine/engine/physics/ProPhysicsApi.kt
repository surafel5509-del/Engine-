package com.sengine.engine.physics

import com.sengine.engine.math.Aabb3
import com.sengine.engine.math.Quat
import com.sengine.engine.math.Vec3

/*
 * Shared contract of the Ultimate physics engine (v7).
 *
 *  * `phys-core` implements shapes, broadphase, narrowphase (GJK/EPA/SAT), the sequential-impulse
 *    solver, sleeping, CCD, the world (`ProPhysicsWorld`) and queries.
 *  * `phys-features` implements joints, the character controller, the raycast vehicle, ragdolls,
 *    destruction and soft bodies on top of exactly this contract.
 *
 * Both sides depend on the types below and on `com.sengine.engine.math` / `jobs` / `ecs`.
 */

/** Collision filtering: two bodies interact when `(a.mask and b.layer) != 0 && (b.mask and a.layer) != 0`. */
class CollisionFilter(var layer: Int = 1, var mask: Int = -1, var group: Int = 0) {
    fun collidesWith(o: CollisionFilter): Boolean =
        (mask and o.layer) != 0 && (o.mask and layer) != 0 && (group == 0 || group != o.group)

    fun copy(): CollisionFilter = CollisionFilter(layer, mask, group)
    override fun toString(): String = "Filter(layer=$layer, mask=$mask, group=$group)"
}

enum class ShapeKind { SPHERE, BOX, CAPSULE, CYLINDER, CONVEX_HULL, TRIANGLE_MESH, PLANE, HEIGHTFIELD, COMPOUND }

enum class BodyType { DYNAMIC, KINEMATIC, STATIC }

/**
 * Convex (or implicitly convex) collision shape. All shapes are defined in local space and centred
 * on the origin except [PlaneShape3D] (infinite plane, normal + offset) and [HeightfieldShape3D].
 */
interface Shape3D {
    val kind: ShapeKind
    /** Local-space bounds. Infinite shapes return [Aabb3.empty] plus `isInfinite`. */
    fun localAabb(): Aabb3
    val isInfinite: Boolean get() = false
    /** Farthest local-space point along [dir] (unit). GJK's support function. */
    fun supportLocal(dir: Vec3, out: Vec3 = Vec3()): Vec3
    fun volume(): Float
    /** Diagonal of the inertia tensor for a body of [mass] kg. */
    fun localInertia(mass: Float, out: Vec3 = Vec3()): Vec3
    fun boundingRadius(): Float
}

/** One contact point of a manifold (world space). */
class ContactPoint3D {
    var position = Vec3()
    var normal = Vec3(0f, 1f, 0f)
    var depth = 0f
    /** Accumulated impulses for warm starting (normal, tangent1, tangent2). */
    var normalImpulse = 0f
    var tangentImpulse1 = 0f
    var tangentImpulse2 = 0f
    /** Stable feature id used to match contacts between frames. */
    var featureId = 0
    override fun toString(): String = "Contact($position, n=$normal, d=$depth)"
}

/** Contact manifold between two bodies. */
class Manifold3D {
    var bodyA: RigidBodyPro3D? = null
    var bodyB: RigidBodyPro3D? = null
    var points = ArrayList<ContactPoint3D>(4)
    var friction = 0.5f
    var restitution = 0f
    var isTrigger = false
    var mixedRestitution = 0f
    val pointCount: Int get() = points.size
    fun clear() { points.clear() }
    override fun toString(): String = "Manifold(${bodyA?.id} vs ${bodyB?.id}, points=$pointCount)"
}

/** Result of a ray / shape query. */
class RayHit3D {
    var hit = false
    var body: RigidBodyPro3D? = null
    var distance = 0f
    var point = Vec3()
    var normal = Vec3(0f, 1f, 0f)
    var fraction = 0f
    var subShape = 0
    var triangle = -1
    override fun toString(): String = if (hit) "RayHit(d=$distance, p=$point, n=$normal, body=${body?.id})" else "RayHit(miss)"
}

/** Description used to create a body. */
class BodyDesc3D {
    var shape: Shape3D? = null
    var type: BodyType = BodyType.DYNAMIC
    var mass = 1f
    var friction = 0.5f
    var restitution = 0f
    var rollingFriction = 0.02f
    var linearDamping = 0.02f
    var angularDamping = 0.05f
    var gravityScale = 1f
    var ccdEnabled = false
    var isTrigger = false
    var collisionDetectionRange = 0f
    var allowSleep = true
    var filter = CollisionFilter()
    var position = Vec3()
    var rotation = Quat()
    var linearVelocity = Vec3()
    var angularVelocity = Vec3()
    var userData: Any? = null
    var tag: String = ""
    var group: String = ""

    fun static() { type = BodyType.STATIC }
    fun kinematic() { type = BodyType.KINEMATIC }
    fun dynamic(m: Float) { type = BodyType.DYNAMIC; mass = m }
}

/** Live rigid body. Motion state is authoritative; write through the helper methods. */
class RigidBodyPro3D(val id: Int, val desc: BodyDesc3D) {
    val shape: Shape3D get() = desc.shape ?: error("body $id has no shape")
    var position = Vec3(desc.position)
    var rotation = Quat(desc.rotation)
    var linearVelocity = Vec3(desc.linearVelocity)
    var angularVelocity = Vec3(desc.angularVelocity)
    var force = Vec3()
    var torque = Vec3()
    var invMass = 0f
    var invInertiaLocal = Vec3()
    var invInertiaWorld = FloatArray(9)
    var awake = true
    var sleepTimer = 0f
    var islandId = -1
    var isStatic = false
    var isKinematic = false
    var isDynamic = false
    var aabb = Aabb3()
    var worldTag: String = ""
    var userData: Any? = null
    var collisionCount = 0
    var isGrounded = false
    var linearSpeedSq = 0f
    var angularSpeedSq = 0f
    /** Set by the character controller / kinematic movers to flag sweep-moved bodies. */
    var isMovedKinematic = false

    fun wakeUp() { awake = true; sleepTimer = 0f }
    fun sleep() { awake = false; linearVelocity.zero(); angularVelocity.zero() }
    fun applyForce(f: Vec3, worldPoint: Vec3? = null) {
        force.add(f)
        if (worldPoint != null) torque.add(worldPoint.minus(position).cross(f))
    }
    fun applyImpulse(impulse: Vec3, worldPoint: Vec3? = null) {
        if (isStatic) return
        linearVelocity.addScaled(impulse, invMass)
        if (worldPoint != null) applyAngularImpulse(worldPoint.minus(position).cross(impulse))
        wakeUp()
    }
    fun applyAngularImpulse(impulse: Vec3) {
        if (isStatic) return
        angularVelocity.add(transformInertia(impulse, invInertiaWorld))
        wakeUp()
    }
    fun getVelocityAt(worldPoint: Vec3): Vec3 = linearVelocity + angularVelocity.cross(worldPoint.minus(position))
    fun localToWorld(p: Vec3): Vec3 = position + rotation.rotate(p)
    fun worldToLocal(p: Vec3): Vec3 = rotation.rotateInverse(p - position)
    fun kineticEnergy(): Float = 0.5f * (1f / invMass.coerceAtLeast(1e-9f)) * linearSpeedSq
    override fun toString(): String = "Body#$id(${desc.tag.ifEmpty { desc.shape?.kind?.name ?: "?" }}, $position, v=$linearVelocity)"

    companion object {
        /** out = I^-1 * v using a row-major 3x3 inverse inertia matrix. */
        fun transformInertia(v: Vec3, invInertia: FloatArray, out: Vec3 = Vec3()): Vec3 = out.set(
            invInertia[0] * v.x + invInertia[1] * v.y + invInertia[2] * v.z,
            invInertia[3] * v.x + invInertia[4] * v.y + invInertia[5] * v.z,
            invInertia[6] * v.x + invInertia[7] * v.y + invInertia[8] * v.z
        )
    }
}

/** Base class of every constraint. [prepare] is called once per step, [solve] once per iteration. */
abstract class Joint3D(val bodyA: RigidBodyPro3D, val bodyB: RigidBodyPro3D?) {
    var enabled = true
    var broken = false
    /** Breaking impulse thresholds (Float.MAX_VALUE disables breaking). */
    var breakForce = Float.MAX_VALUE
    var breakTorque = Float.MAX_VALUE
    var accumulatedImpulse = 0f
    abstract val kind: String

    open fun prepare(dt: Float) {}
    abstract fun solve(iterations: Int)
    open fun wakeBodies() { bodyA.wakeUp(); bodyB?.wakeUp() }
    open fun describe(): String = kind
    override fun toString(): String = describe()
}

/** Common motor/limit description shared by hinge, slider and cone-twist joints. */
class JointLimits(var lower: Float = -Float.MAX_VALUE, var upper: Float = Float.MAX_VALUE) {
    val isLimited: Boolean get() = lower > -Float.MAX_VALUE || upper < Float.MAX_VALUE
    fun clamp(v: Float): Float = v.coerceIn(lower, upper)
    override fun toString(): String = "Limits($lower..$upper)"
}

class JointMotor(var targetVelocity: Float = 0f, var maxForce: Float = 0f, var enabled: Boolean = false) {
    override fun toString(): String = "Motor(v=$targetVelocity, f=$maxForce, on=$enabled)"
}

/** Optional callbacks; implement to react to contacts and triggers. */
interface ContactListener3D {
    fun onContactBegan(m: Manifold3D) {}
    fun onContactStayed(m: Manifold3D) {}
    fun onContactEnded(a: RigidBodyPro3D, b: RigidBodyPro3D) {}
    fun onTriggerEnter(trigger: RigidBodyPro3D, other: RigidBodyPro3D) {}
    fun onTriggerExit(trigger: RigidBodyPro3D, other: RigidBodyPro3D) {}
}

/** Counters exposed to the profiler / editor. */
class PhysicsStats3D {
    var bodyCount = 0
    var awakeCount = 0
    var staticCount = 0
    var jointCount = 0
    var broadphasePairs = 0
    var narrowphaseTests = 0
    var contactCount = 0
    var islandCount = 0
    var solverIterations = 0
    var stepMs = 0f
    var broadphaseMs = 0f
    var narrowphaseMs = 0f
    var solverMs = 0f
    var ccdSweeps = 0
    var sleepingBodies = 0
    var fixedSteps = 0
    override fun toString(): String =
        "Physics(bodies=$bodyCount awake=$awakeCount pairs=$broadphasePairs contacts=$contactCount islands=$islandCount step=${"%.2f".format(stepMs)}ms)"
}

/**
 * Everything the feature layer (joints, characters, vehicles, ragdolls, destruction, soft bodies)
 * and the lead's scene integration may call on the world.
 */
interface PhysicsWorldApi3D {
    val gravity: Vec3
    var iterations: Int
    var subSteps: Int
    var fixedDeltaTime: Float
    var contactListener: ContactListener3D?
    val bodies: List<RigidBodyPro3D>
    val joints: List<Joint3D>
    val manifolds: List<Manifold3D>
    val stats: PhysicsStats3D

    fun addBody(desc: BodyDesc3D): RigidBodyPro3D
    fun removeBody(body: RigidBodyPro3D)
    fun addJoint(joint: Joint3D): Joint3D
    fun removeJoint(joint: Joint3D)
    /** Advances the simulation; accumulates [dt] internally and runs fixed steps. */
    fun step(dt: Float)
    /** Runs exactly one fixed step (used by netcode/tests). */
    fun stepFixed(dt: Float = fixedDeltaTime)
    fun clear()

    fun raycast(from: Vec3, to: Vec3, filter: CollisionFilter? = null, ignore: RigidBodyPro3D? = null): RayHit3D
    fun raycastDirection(from: Vec3, direction: Vec3, maxDistance: Float, filter: CollisionFilter? = null, ignore: RigidBodyPro3D? = null): RayHit3D
    fun raycastAll(from: Vec3, to: Vec3, filter: CollisionFilter? = null, ignore: RigidBodyPro3D? = null, out: MutableList<RayHit3D> = ArrayList()): MutableList<RayHit3D>
    fun overlapSphere(center: Vec3, radius: Float, filter: CollisionFilter? = null, out: MutableList<RigidBodyPro3D> = ArrayList()): MutableList<RigidBodyPro3D>
    fun overlapAabb(aabb: Aabb3, filter: CollisionFilter? = null, out: MutableList<RigidBodyPro3D> = ArrayList()): MutableList<RigidBodyPro3D>
    fun shapeCast(shape: Shape3D, from: Vec3, direction: Vec3, maxDistance: Float, filter: CollisionFilter? = null, ignore: RigidBodyPro3D? = null): RayHit3D
    fun closestPoint(point: Vec3, maxDistance: Float, filter: CollisionFilter? = null, ignore: RigidBodyPro3D? = null): Vec3?
    /** Applies an explosion impulse to every dynamic body inside [radius]. */
    fun applyExplosion(center: Vec3, radius: Float, force: Float, upwardModifier: Float = 0f)
}
