package com.sengine.engine.ecs

import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Scene
import com.sengine.engine.math.Quat
import com.sengine.engine.math.Vec3

/** Mirror of a GameObject's 3D transform inside the ECS world. */
class TransformEcs : EcsComponent {
    var position = Vec3()
    var rotation = Quat()
    var scale = Vec3(1f, 1f, 1f)
    var parent = Entity.NULL
    var worldMatrix = FloatArray(16)
    fun set(p: Vec3, r: Quat, s: Vec3): TransformEcs { position.set(p); rotation.set(r); scale.set(s); return this }
    override fun toString(): String = "TransformEcs($position, $rotation, $scale)"
}

/** Links an entity to the scene object it mirrors. */
class SceneLinkEcs : EcsComponent {
    var objectId = 0L
    var name = ""
    var tag = "Untagged"
    var active = true
    override fun toString(): String = "SceneLinkEcs(#$objectId $name)"
}

/** Per-entity tags used by the AI / gameplay layers. */
class TagsEcs : EcsComponent {
    val tags = ArrayList<String>(2)
    fun has(t: String) = tags.contains(t)
}

/**
 * Keeps the ECS world in sync with the classic `Scene` graph: one entity per active GameObject,
 * with a [TransformEcs] mirror. Systems written against ECS (AI, physics, networking, gameplay)
 * can therefore operate on existing scenes, and the bridge writes transforms back when asked.
 */
class SceneEcsBridge(val world: EcsWorld) {
    private val entityByObject = HashMap<Long, Entity>()
    var lastSyncEntities = 0; private set
    var lastCreated = 0; private set
    var lastDestroyed = 0; private set

    init {
        world.register("TransformEcs", TransformEcs::class.java,
            { c, o ->
                o.put("px", c.position.x.toDouble()); o.put("py", c.position.y.toDouble()); o.put("pz", c.position.z.toDouble())
                o.put("qx", c.rotation.x.toDouble()); o.put("qy", c.rotation.y.toDouble()); o.put("qz", c.rotation.z.toDouble()); o.put("qw", c.rotation.w.toDouble())
                o.put("sx", c.scale.x.toDouble()); o.put("sy", c.scale.y.toDouble()); o.put("sz", c.scale.z.toDouble())
            },
            { o ->
                TransformEcs().set(
                    Vec3(o.optDouble("px", 0.0).toFloat(), o.optDouble("py", 0.0).toFloat(), o.optDouble("pz", 0.0).toFloat()),
                    Quat(o.optDouble("qx", 0.0).toFloat(), o.optDouble("qy", 0.0).toFloat(), o.optDouble("qz", 0.0).toFloat(), o.optDouble("qw", 1.0).toFloat()),
                    Vec3(o.optDouble("sx", 1.0).toFloat(), o.optDouble("sy", 1.0).toFloat(), o.optDouble("sz", 1.0).toFloat())
                )
            })
        world.register("SceneLinkEcs", SceneLinkEcs::class.java,
            { c, o -> o.put("objectId", c.objectId); o.put("name", c.name); o.put("tag", c.tag); o.put("active", c.active) },
            { o -> SceneLinkEcs().also { it.objectId = o.optLong("objectId"); it.name = o.optString("name"); it.tag = o.optString("tag", "Untagged"); it.active = o.optBoolean("active", true) } })
        world.register("TagsEcs", TagsEcs::class.java,
            { c, o -> o.put("tags", org.json.JSONArray(c.tags)) },
            { o -> TagsEcs().also { t -> o.optJSONArray("tags")?.let { a -> for (i in 0 until a.length()) t.tags.add(a.getString(i)) } } })
    }

    fun entityFor(go: GameObject): Entity? = entityByObject[go.id]
    fun objectIdFor(e: Entity): Long = world.get(e, SceneLinkEcs::class.java)?.objectId ?: 0L
    fun entityCount(): Int = entityByObject.size

    /** Creates/updates entities for every live scene object. Returns the number of entities. */
    fun syncFromScene(scene: Scene, createMissing: Boolean = true): Int {
        var created = 0
        var alive = 0
        for (go in scene.objects) {
            if (go.destroyed) continue
            var e = entityByObject[go.id]
            if (e == null || !world.isAlive(e)) {
                if (!createMissing) continue
                e = world.create(go.name, go.parent?.let { entityByObject[it.id] } ?: Entity.NULL)
                world.add(e, TransformEcs::class.java, TransformEcs())
                world.add(e, SceneLinkEcs::class.java, SceneLinkEcs().also { it.objectId = go.id; it.name = go.name })
                world.add(e, TagsEcs::class.java, TagsEcs().also { if (go.tag.isNotEmpty() && go.tag != "Untagged") it.tags.add(go.tag) })
                entityByObject[go.id] = e
                created++
            }
            val t = world.get(e, TransformEcs::class.java) ?: continue
            t.position.set(go.x, go.y, go.z)
            t.rotation.set(Quat.fromEulerDeg(go.rotY, go.rotX, go.rotation))
            t.scale.set(go.scaleX, go.scaleY, go.scaleZ)
            t.parent = go.parent?.let { entityByObject[it.id] } ?: Entity.NULL
            go.world3.copyInto(t.worldMatrix)
            world.get(e, SceneLinkEcs::class.java)?.let { it.name = go.name; it.tag = go.tag; it.active = go.active }
            alive++
        }
        // remove entities whose object disappeared
        val gone = entityByObject.entries.filter { (id, e) -> scene.findById(id) == null || !world.isAlive(e) }
        for ((id, e) in gone) { world.destroy(e); entityByObject.remove(id) }
        lastCreated = created
        lastDestroyed = gone.size
        lastSyncEntities = alive
        world.invalidateQueries()
        return alive
    }

    /** Writes ECS transform changes back into the scene objects. */
    fun syncToScene(scene: Scene): Int {
        var n = 0
        for ((id, e) in entityByObject) {
            val go = scene.findById(id) ?: continue
            val t = world.get(e, TransformEcs::class.java) ?: continue
            if (go.x == t.position.x && go.y == t.position.y && go.z == t.position.z &&
                go.rotation == t.rotation.toEulerDeg()[2] && go.rotX == t.rotation.toEulerDeg()[1] && go.rotY == t.rotation.toEulerDeg()[0]) continue
            val euler = t.rotation.toEulerDeg()
            go.x = t.position.x; go.y = t.position.y; go.z = t.position.z
            go.rotY = euler[0]; go.rotX = euler[1]; go.rotation = euler[2]
            go.scaleX = t.scale.x; go.scaleY = t.scale.y; go.scaleZ = t.scale.z
            n++
        }
        return n
    }

    fun clear() { entityByObject.clear() }
    fun describe(): String = "SceneEcsBridge(entities=${entityByObject.size}, world=${world.stats()})"
}
