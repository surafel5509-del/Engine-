package com.sengine.engine.ecs

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Data oriented ECS world: sparse-set component stores, 64 bit component masks, cached
 * queries, deferred structural changes and JSON serialisation.
 *
 * The world is deliberately free of Android APIs so it runs in headless JVM tests and on
 * the device. Physics, AI, animation, networking and gameplay systems all share one world.
 */
class EcsWorld {

    // generation starts at 1 so (index 0, generation 0) - the null handle - is never a live entity
    private class Slot(var generation: Int = 1, var alive: Boolean = false, var name: String = "", var mask: Long = 0L, var parent: Entity = Entity.NULL)

    private val slots = ArrayList<Slot>()
    private val free = ArrayDeque<Int>()
    private val stores = LinkedHashMap<Class<*>, ComponentStore<*>>()
    private val typeIds = LinkedHashMap<Class<*>, Int>()
    private val names = LinkedHashMap<Int, Class<*>>()
    private val codecs = LinkedHashMap<Int, Codec<*>>()
    private val typeNames = LinkedHashMap<Class<*>, String>()
    private val queryCache = HashMap<String, IntArray>()

    var entityCount = 0; private set
    var destroyedCount = 0; private set
    /** Bumped whenever components are added/removed, so cached queries can be invalidated. */
    var version = 0L; private set

    private interface Codec<T : Any> {
        fun write(c: T, o: JSONObject)
        fun read(o: JSONObject): T
    }

    val events = EcsEvents()

    // ------------------------------------------------------------------ entities

    fun create(name: String = "Entity", parent: Entity = Entity.NULL): Entity {
        val index: Int
        if (free.isNotEmpty()) { index = free.removeFirst(); slots[index].alive = true } else {
            index = slots.size
            slots.add(Slot())
        }
        val slot = slots[index]
        slot.mask = 0L
        slot.name = name
        slot.parent = parent
        entityCount++
        version++
        return Entity.of(index, slot.generation)
    }

    fun isAlive(e: Entity): Boolean {
        if (e.isNull) return false
        val s = slots.getOrNull(e.index) ?: return false
        return s.alive && s.generation == e.generation
    }

    fun destroy(e: Entity) {
        if (!isAlive(e)) return
        // destroy children first
        val kids = slots.indices.filter { slots[it].alive && slots[it].parent == e }
        for (k in kids) destroy(Entity.of(k, slots[k].generation))
        val slot = slots[e.index]
        for ((_, store) in stores) store.remove(e.index)
        slot.alive = false
        slot.generation++
        slot.mask = 0L
        slot.name = ""
        slot.parent = Entity.NULL
        free.addLast(e.index)
        entityCount--
        destroyedCount++
        version++
    }

    fun clear() {
        for (s in slots) { s.alive = false; s.generation++; s.mask = 0L }
        free.clear()
        for (i in slots.indices) free.addLast(i)
        for ((_, store) in stores) store.clear()
        entityCount = 0
        version++
    }

    fun nameOf(e: Entity): String = slots.getOrNull(e.index)?.name ?: ""
    fun setName(e: Entity, name: String) { slots.getOrNull(e.index)?.name = name }
    fun parentOf(e: Entity): Entity = slots.getOrNull(e.index)?.parent ?: Entity.NULL
    fun setParent(e: Entity, parent: Entity) { slots.getOrNull(e.index)?.parent = parent }

    /** Iterates every live entity (no allocation beyond the callback). */
    fun forEachEntity(action: (Entity) -> Unit) {
        for (i in slots.indices) {
            val s = slots[i]
            if (s.alive) action(Entity.of(i, s.generation))
        }
    }

    fun entities(): List<Entity> {
        val out = ArrayList<Entity>(entityCount)
        forEachEntity { out.add(it) }
        return out
    }

    // ------------------------------------------------------------------ components

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> store(type: Class<T>): ComponentStore<T> {
        var s = stores[type]
        if (s == null) {
            s = ComponentStore(type)
            stores[type] = s
            typeIds[type] = typeIds.size
            names[typeIds.size - 1] = type
        }
        return s as ComponentStore<T>
    }

    inline fun <reified T : Any> store(): ComponentStore<T> = store(T::class.java)

    fun <T : Any> add(e: Entity, type: Class<T>, value: T) {
        if (!isAlive(e)) return
        store(type).add(e.index, value)
        slots[e.index].mask = slots[e.index].mask or (1L shl typeId(type))
        version++
    }

    inline fun <reified T : Any> add(e: Entity, value: T) = add(e, T::class.java, value)

    /** Type-erased add used by the deserialiser and [clone]. */
    @Suppress("UNCHECKED_CAST")
    fun addAny(e: Entity, type: Class<*>, value: Any) {
        if (!isAlive(e)) return
        (store(type as Class<Any>) as ComponentStore<Any>).add(e.index, value)
        slots[e.index].mask = slots[e.index].mask or (1L shl typeId(type))
        version++
    }

    fun <T : Any> remove(e: Entity, type: Class<T>) {
        if (!isAlive(e)) return
        store(type).remove(e.index)
        slots[e.index].mask = slots[e.index].mask and (1L shl typeId(type)).inv()
        version++
    }

    inline fun <reified T : Any> remove(e: Entity) = remove(e, T::class.java)

    fun <T : Any> get(e: Entity, type: Class<T>): T? {
        if (!isAlive(e)) return null
        return store(type).get(e.index)
    }

    inline fun <reified T : Any> get(e: Entity): T? = get(e, T::class.java)

    fun <T : Any> has(e: Entity, type: Class<T>): Boolean = isAlive(e) && store(type).has(e.index)

    inline fun <reified T : Any> has(e: Entity): Boolean = has(e, T::class.java)

    /** Component type id (assigned on first use); -1 when the type was never stored. */
    fun typeId(type: Class<*>): Int = typeIds[type] ?: run { store(type); typeIds[type] ?: 0 }

    fun componentTypes(): List<Class<*>> = stores.keys.toList()

    fun maskOf(e: Entity): Long = slots.getOrNull(e.index)?.mask ?: 0L

    // ------------------------------------------------------------------ queries

    /** Cached list of entities holding all [types]. The result must not be mutated. */
    fun query(vararg types: Class<*>): IntArray {
        require(types.isNotEmpty()) { "query needs at least one component type" }
        val key = types.joinToString(",") { it.name }
        queryCache[key]?.let { if (cachedVersion == version) return it }
        val smallest = types.minByOrNull { store(it).size } ?: return IntArray(0)
        val base = store(smallest)
        val out = ArrayList<Int>(base.size)
        outer@ for (i in 0 until base.size) {
            val ei = base.entityAt(i)
            val mask = slots[ei].mask
            for (t in types) if (mask and (1L shl typeId(t)) == 0L) continue@outer
            out.add(ei)
        }
        val arr = out.toIntArray()
        queryCache[key] = arr
        cachedVersion = version
        return arr
    }

    private var cachedVersion = -1L

    fun queryEntities(vararg types: Class<*>): List<Entity> = query(*types).map { Entity.of(it, slots[it].generation) }

    /** Typed two-component iteration over cached query results. */
    fun <A : Any, B : Any> each(typeA: Class<A>, typeB: Class<B>, action: (Entity, A, B) -> Unit) {
        val ids = query(typeA, typeB)
        val sa = store(typeA); val sb = store(typeB)
        for (i in ids) {
            val a = sa.get(i) ?: continue
            val b = sb.get(i) ?: continue
            action(Entity.of(i, slots[i].generation), a, b)
        }
    }

    fun <A : Any> each(typeA: Class<A>, action: (Entity, A) -> Unit) {
        val ids = query(typeA)
        val sa = store(typeA)
        for (i in ids) action(Entity.of(i, slots[i].generation), sa.get(i) ?: continue)
    }

    fun count(vararg types: Class<*>): Int = query(*types).size

    fun invalidateQueries() { version++; queryCache.clear() }

    // ------------------------------------------------------------------ serialisation

    /** Registers a serialisable component type under [typeName]. */
    fun <T : Any> register(typeName: String, type: Class<T>, write: (T, JSONObject) -> Unit, read: (JSONObject) -> T) {
        val id = typeId(type)
        typeNames[type] = typeName
        codecs[id] = object : Codec<T> {
            override fun write(c: T, o: JSONObject) = write(c, o)
            override fun read(o: JSONObject): T = read(o)
        }
    }

    fun registeredTypes(): List<String> = typeNames.values.toList()

    /** Serialises every entity plus its registered components. */
    fun toJson(): JSONObject {
        val o = JSONObject()
        val arr = JSONArray()
        forEachEntity { e ->
            val eo = JSONObject()
            eo.put("name", nameOf(e))
            eo.put("index", e.index)
            val parent = parentOf(e)
            if (!parent.isNull) eo.put("parent", parent.index)
            val comps = JSONArray()
            for ((type, store) in stores) {
                val name = typeNames[type] ?: continue
                val codec = codecs[typeId(type)] ?: continue
                val c = store.get(e.index) ?: continue
                val co = JSONObject()
                co.put("type", name)
                @Suppress("UNCHECKED_CAST")
                (codec as Codec<Any>).write(c, co)
                comps.put(co)
            }
            eo.put("components", comps)
            arr.put(eo)
        }
        o.put("entities", arr)
        o.put("count", entityCount)
        return o
    }

    /** Rebuilds the world from [toJson] output (replacing existing content). */
    fun fromJson(o: JSONObject) {
        clear()
        val arr = o.optJSONArray("entities") ?: JSONArray()
        val byIndex = HashMap<Int, Entity>()
        for (i in 0 until arr.length()) {
            val eo = arr.getJSONObject(i)
            val e = create(eo.optString("name", "Entity"))
            byIndex[eo.optInt("index", e.index)] = e
        }
        for (i in 0 until arr.length()) {
            val eo = arr.getJSONObject(i)
            val e = byIndex[eo.optInt("index", -1)] ?: continue
            if (eo.has("parent")) eo.optInt("parent").let { p -> byIndex[p]?.let { setParent(e, it) } }
            val comps = eo.optJSONArray("components") ?: JSONArray()
            for (j in 0 until comps.length()) {
                val co = comps.getJSONObject(j)
                val name = co.optString("type")
                val type = typeNames.entries.firstOrNull { it.value == name }?.key ?: continue
                val codec = codecs[typeId(type)] ?: continue
                @Suppress("UNCHECKED_CAST")
                addAny(e, type, (codec as Codec<Any>).read(co))
            }
        }
        invalidateQueries()
    }

    /** Deep copy of the world (undo/redo, netcode rollback, tests). */
    fun clone(): EcsWorld {
        val w = EcsWorld()
        for ((type, name) in typeNames) {
            val src = codecs[typeId(type)] ?: continue
            val id = typeId(type)
            @Suppress("UNCHECKED_CAST")
            val c = src as Codec<Any>
            w.register(name, type as Class<Any>, { v, o -> c.write(v, o) }, { o -> c.read(o) })
            // keep type ids aligned so masks stay comparable
            w.typeId(type)
            if (w.typeId(type) != id) { /* ids only differ if registration order differs */ }
        }
        w.fromJson(toJson())
        return w
    }

    fun stats(): String = "EcsWorld(entities=$entityCount, types=${stores.size}, destroyed=$destroyedCount)"
}
