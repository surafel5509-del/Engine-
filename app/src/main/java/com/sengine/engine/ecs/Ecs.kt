package com.sengine.engine.ecs

import org.json.JSONArray
import org.json.JSONObject

/**
 * Compact entity handle: 32 bit index + 32 bit generation packed into a Long.
 * A stale handle (entity destroyed and the slot reused) is detected by comparing generations.
 */
@JvmInline
value class Entity(val raw: Long) {
    val index: Int get() = (raw and 0xFFFFFFFFL).toInt()
    val generation: Int get() = (raw ushr 32).toInt()
    val isNull: Boolean get() = raw == 0L
    override fun toString(): String = "Entity($index, gen=$generation)"

    companion object {
        val NULL = Entity(0L)
        fun of(index: Int, generation: Int): Entity = Entity((generation.toLong() shl 32) or (index.toLong() and 0xFFFFFFFFL))
    }
}

/** Base marker for components stored in an [EcsWorld]. Plain data classes keep them cheap. */
interface EcsComponent

/** Dense component storage: parallel arrays of entities and components plus a sparse index map. */
class ComponentStore<T : Any>(val type: Class<T>) {
    private val entities = ArrayList<Int>()
    private val items = ArrayList<T>()
    private var sparse = IntArray(256) { -1 }
    val size: Int get() = entities.size

    fun entityAt(i: Int): Int = entities[i]
    fun itemAt(i: Int): T = items[i]

    private fun ensure(index: Int) {
        if (index < sparse.size) return
        var n = sparse.size
        while (n <= index) n *= 2
        val old = sparse.size
        sparse = sparse.copyOf(n)
        java.util.Arrays.fill(sparse, old, n, -1)
    }

    fun add(entityIndex: Int, value: T) {
        ensure(entityIndex)
        val at = sparse[entityIndex]
        if (at >= 0) { items[at] = value; return }
        sparse[entityIndex] = entities.size
        entities.add(entityIndex)
        items.add(value)
    }

    fun remove(entityIndex: Int) {
        if (entityIndex >= sparse.size) return
        val at = sparse[entityIndex]
        if (at < 0) return
        val last = entities.size - 1
        if (at != last) {
            val movedEntity = entities[last]
            entities[at] = movedEntity
            items[at] = items[last]
            sparse[movedEntity] = at
        }
        entities.removeAt(last)
        items.removeAt(last)
        sparse[entityIndex] = -1
    }

    fun has(entityIndex: Int): Boolean = entityIndex < sparse.size && sparse[entityIndex] >= 0
    fun get(entityIndex: Int): T? { if (entityIndex >= sparse.size) return null; val at = sparse[entityIndex]; return if (at < 0) null else items[at] }
    fun clear() { entities.clear(); items.clear(); java.util.Arrays.fill(sparse, -1) }
    fun entitiesSnapshot(): IntArray = entities.toIntArray()
}
