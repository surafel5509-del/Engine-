package com.sengine.engine.ecs

/** Deferred structural changes: collected while systems iterate, applied at a safe point. */
class CommandBuffer(private val world: EcsWorld) {
    private val ops = ArrayList<() -> Unit>()
    var lastFlushCount = 0; private set

    /**
     * Entities are created immediately (cheap, safe during iteration); component changes are queued
     * and applied by [flush], which is where the structural change actually happens.
     */
    fun create(name: String = "Entity", parent: Entity = Entity.NULL): Entity = world.create(name, parent)

    fun destroy(e: Entity) { ops.add { world.destroy(e) } }
    fun <T : Any> add(e: Entity, type: Class<T>, value: T) { ops.add { world.add(e, type, value) } }
    inline fun <reified T : Any> add(e: Entity, value: T) = add(e, T::class.java, value)
    fun <T : Any> remove(e: Entity, type: Class<T>) { ops.add { world.remove(e, type) } }
    inline fun <reified T : Any> remove(e: Entity) = remove(e, T::class.java)
    fun run(action: () -> Unit) { ops.add(action) }

    val size: Int get() = ops.size

    fun flush() {
        if (ops.isEmpty()) return
        val batch = ops.toList()
        ops.clear()
        lastFlushCount = batch.size
        for (op in batch) op()
        world.invalidateQueries()
    }

    fun clear() { ops.clear() }

}

/** A system operating on the ECS world. */
interface EcsSystem {
    val name: String get() = this::class.java.simpleName
    var enabled: Boolean
    /** Higher runs first inside a stage. */
    val order: Int get() = 0
    fun update(world: EcsWorld, dt: Float) {}
    fun onAdded(world: EcsWorld) {}
    fun onRemoved(world: EcsWorld) {}
}

/** Convenience base class with sensible defaults. */
abstract class BaseSystem(override val order: Int = 0) : EcsSystem {
    override var enabled: Boolean = true
}

/** Runs systems in stages: FixedUpdate -> Update -> LateUpdate -> Render, ordered by [EcsSystem.order]. */
class SystemScheduler(private val world: EcsWorld) {

    enum class Stage { FIXED_UPDATE, UPDATE, LATE_UPDATE, RENDER }

    private val stages = LinkedHashMap<Stage, MutableList<EcsSystem>>()
    var lastStageMs = LinkedHashMap<Stage, Float>()
    val commands = CommandBuffer(world)

    init { for (s in Stage.values()) stages[s] = ArrayList() }

    fun add(system: EcsSystem, stage: Stage = Stage.UPDATE): SystemScheduler {
        stages.getValue(stage).add(system)
        stages.getValue(stage).sortByDescending { it.order }
        system.onAdded(world)
        return this
    }

    fun remove(system: EcsSystem) {
        for (list in stages.values) if (list.remove(system)) system.onRemoved(world)
    }

    fun systems(stage: Stage): List<EcsSystem> = stages.getValue(stage)
    fun count(): Int = stages.values.sumOf { it.size }

    fun run(stage: Stage, dt: Float) {
        val t0 = System.nanoTime()
        for (s in stages.getValue(stage)) {
            if (!s.enabled) continue
            s.update(world, dt)
        }
        commands.flush()
        lastStageMs[stage] = (System.nanoTime() - t0) / 1e6f
    }

    fun describe(): String = stages.entries.joinToString("; ") { (stage, list) -> "$stage=[${list.joinToString { it.name }}]" }
}

/** Typed event bus with deferred dispatch (safe to subscribe/unsubscribe while emitting). */
class EcsEvents {
    private val listeners = LinkedHashMap<String, MutableList<Pair<Any, (Any?) -> Unit>>>()
    private val queue = ArrayList<Pair<String, Any?>>()
    var emitted = 0; private set

    fun <T : Any> on(channel: String, owner: Any, handler: (T) -> Unit) {
        listeners.getOrPut(channel) { ArrayList() }.add(owner to @Suppress("UNCHECKED_CAST") (handler as (Any?) -> Unit))
    }

    fun off(channel: String, owner: Any) {
        listeners[channel]?.removeAll { it.first === owner }
    }

    fun offAll(owner: Any) { for (l in listeners.values) l.removeAll { it.first === owner } }

    fun emit(channel: String, payload: Any? = null) {
        queue.add(channel to payload)
        emitted++
    }

    /** Delivers queued events immediately. */
    fun dispatch(): Int {
        if (queue.isEmpty()) return 0
        val batch = queue.toList()
        queue.clear()
        for ((channel, payload) in batch) {
            val ls = listeners[channel]?.toList() ?: continue
            for ((_, h) in ls) h(payload)
        }
        return batch.size
    }

    fun listenerCount(channel: String): Int = listeners[channel]?.size ?: 0
    fun channels(): List<String> = listeners.keys.toList()
}
