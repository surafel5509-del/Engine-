package com.sengine.engine.core

import org.json.JSONArray
import org.json.JSONObject

class Scene(var name: String) {
    companion object {
        /** Half width of the UI canvas (its half height is 5); follows the game view aspect. */
        @Volatile @JvmStatic var uiHalfW = 5f * 16f / 9f
    }

    val objects = mutableListOf<GameObject>()
    var gravityX = 0f
    var gravityY = -9.81f
    var gravityZ = 0f
    /** Vertical gravity used by 3D physics. */
    var gravity3D = -9.81f
    var ambient = 0xFF4A505A.toInt()
    var fog = false
    var fogColor = 0xFF9DB4CF.toInt()
    var fogStart = 20f
    var fogEnd = 80f
    var nextId = 1L

    // -------------------------------------------------------------- fast hierarchy (v7 Pro)
    // Reused containers so a frame produces no garbage while walking the hierarchy.
    private val childBuckets = HashMap<GameObject, MutableList<GameObject>>()
    private val rootList = ArrayList<GameObject>()
    private val orderStack = ArrayList<GameObject>()
    private val tmp3 = FloatArray(16)

    fun create(name: String, parent: GameObject? = null): GameObject {
        val go = GameObject(nextId++, uniqueName(name))
        go.parent = parent
        objects.add(go)
        drawListDirty = true
        return go
    }

    fun uniqueName(base: String): String {
        if (objects.none { it.name == base }) return base
        val stem = base.replace(Regex(" \\(\\d+\\)$"), "")
        var i = 1
        while (objects.any { it.name == "$stem ($i)" }) i++
        return "$stem ($i)"
    }

    fun findById(id: Long) = objects.firstOrNull { it.id == id }

    /** Finds by name, preferring active objects (so level variants / templates with the same name don't shadow live ones).
     *  Results are memoised for the current frame; scripts that look up the same target every frame stop scanning the scene. */
    fun find(name: String): GameObject? {
        if (nameCacheStamp != frameStamp) { nameCache.clear(); nameCacheStamp = frameStamp }
        if (nameCache.containsKey(name)) {
            val cached = nameCache[name]
            if (cached == null) return null
            if (!cached.destroyed && cached.name == name) return cached
            nameCache.remove(name)
        }
        var fallback: GameObject? = null
        for (o in objects) {
            if (o.name != name || o.destroyed) continue
            if (o.isActiveInHierarchy()) { nameCache[name] = o; return o }
            if (fallback == null) fallback = o
        }
        nameCache[name] = fallback
        return fallback
    }

    fun childrenOf(go: GameObject?) = objects.filter { it.parent === go }

    /** Depth-first hierarchy listing with depth (single-pass bucketing; safe for editor UI). */
    fun hierarchy(): List<Pair<GameObject, Int>> {
        val out = ArrayList<Pair<GameObject, Int>>()
        childBuckets.clear(); rootList.clear()
        for (go in objects) {
            val p = go.parent
            if (p == null || p.destroyed) rootList.add(go)
            else childBuckets.getOrPut(p) { ArrayList() }.add(go)
        }
        fun walk(p: GameObject?, depth: Int) {
            val kids = if (p == null) rootList else childBuckets[p] ?: return
            for (c in kids) { out.add(c to depth); walk(c, depth + 1) }
        }
        walk(null, 0)
        childBuckets.clear(); rootList.clear()
        return out
    }

    fun remove(go: GameObject) {
        for (c in childrenOf(go)) remove(c)
        go.destroyed = true
        objects.remove(go)
        drawListDirty = true
    }

    private val tmp3 = FloatArray(16)

    /** Frame stamp bumped by [updateTransforms]; used to scope per-frame memo caches. */
    @Volatile var frameStamp = 0L
        private set
    private val nameCache = HashMap<String, GameObject?>()
    private var nameCacheStamp = -1L

    fun updateTransforms() {
        frameStamp++
        // single pass: bucket children by parent (O(n)), then a depth-first walk (O(n))
        // replaces the old scan-per-parent recursion which was O(n * parents).
        childBuckets.clear(); rootList.clear()
        for (go in objects) {
            val p = go.parent
            if (p == null || p.destroyed) rootList.add(go)
            else childBuckets.getOrPut(p) { ArrayList() }.add(go)
        }
        orderStack.clear(); orderStack.addAll(rootList)
        var cursor = 0
        while (cursor < orderStack.size) {
            val go = orderStack[cursor++]
            val p = go.parent
            if (p == null || p.destroyed) {
                go.localMatrix(go.world); go.applyAnchor(go.world)
                go.localMatrix3(go.world3)
            } else {
                go.world.setMul(p.world, go.localMatrix())
                com.sengine.engine.math.Mat4.mul(go.world3, p.world3, go.localMatrix3(tmp3))
            }
            childBuckets[go]?.let { orderStack.addAll(it) }
        }
    }

    // -------------------------------------------------------------- draw order + component index (v7 Pro)
    /** Reusable, per-frame ordered object list: active only, sorted by (order, creation). [stamp] guards staleness. */
    fun drawList(): List<GameObject> {
        if (drawStamp != frameStamp || drawListDirty) {
            drawListBuf.clear()
            for (go in objects) if (go.isActiveInHierarchy()) { go.scratchIndex = drawListBuf.size; drawListBuf.add(go) }
            drawListBuf.sortWith(orderComparator)
            drawStamp = frameStamp
            drawListDirty = false
        }
        return drawListBuf
    }
    private val drawListBuf = ArrayList<GameObject>()
    private var drawStamp = -1L
    private var drawListDirty = false
    private val orderComparator = compareBy<GameObject>({ it.order }, { it.scratchIndex })
    /** Editor mutations (create/delete/reorder/parent/active) call this so the draw list rebuilds. */
    fun invalidateDrawList() { drawListDirty = true }

    /** Typed component index rebuilt once per play frame; replaces ~15 full-scene scans with one. */
    val index = RuntimeIndex()

    class RuntimeIndex {
        val sprites = ArrayList<SpriteRenderer>()
        val texts = ArrayList<TextRenderer>()
        val animators = ArrayList<Animator>()
        val particles = ArrayList<ParticleEmitter>()
        val cameras2 = ArrayList<Camera2D>()
        val cameras3 = ArrayList<Camera3D>()
        val lights = ArrayList<Light>()
        val rigidbodies2 = ArrayList<Rigidbody2D>()
        val colliders2 = ArrayList<Collider2D>()
        val rigidbodies3 = ArrayList<Rigidbody3D>()
        val colliders3 = ArrayList<Collider3D>()
        val meshes = ArrayList<MeshRenderer>()
        val audioSources = ArrayList<AudioSource>()
        val scripts = ArrayList<ScriptComponent>()
        val voxelWorlds = ArrayList<VoxelWorld>()
        val waters = ArrayList<Water>()
        val landscapes = ArrayList<Landscape>()

        fun clear() {
            sprites.clear(); texts.clear(); animators.clear(); particles.clear()
            cameras2.clear(); cameras3.clear(); lights.clear()
            rigidbodies2.clear(); colliders2.clear(); rigidbodies3.clear(); colliders3.clear()
            meshes.clear(); audioSources.clear(); scripts.clear()
            voxelWorlds.clear(); waters.clear(); landscapes.clear()
        }
    }

    fun rebuildIndex() {
        index.clear()
        for (go in objects) {
            if (go.destroyed) continue
            val comps = go.components
            for (i in comps.indices) {
                val c = comps[i]
                if (!c.enabled) continue // matches GameObject.get<T>() semantics
                when (c) {
                    is SpriteRenderer -> index.sprites.add(c)
                    is TextRenderer -> index.texts.add(c)
                    is Animator -> index.animators.add(c)
                    is ParticleEmitter -> index.particles.add(c)
                    is Camera2D -> index.cameras2.add(c)
                    is Camera3D -> index.cameras3.add(c)
                    is Light -> index.lights.add(c)
                    is Rigidbody2D -> index.rigidbodies2.add(c)
                    is Collider2D -> index.colliders2.add(c)
                    is Rigidbody3D -> index.rigidbodies3.add(c)
                    is Collider3D -> index.colliders3.add(c)
                    is MeshRenderer -> index.meshes.add(c)
                    is AudioSource -> index.audioSources.add(c)
                    is ScriptComponent -> index.scripts.add(c)
                    is VoxelWorld -> index.voxelWorlds.add(c)
                    is Water -> index.waters.add(c)
                    is Landscape -> index.landscapes.add(c)
                }
            }
        }
    }

    /** Deep copy of an object (and its children) placed next to the original. */
    fun duplicate(src: GameObject, newParent: GameObject? = src.parent): GameObject {
        val json = SceneSerializer.objectToJson(src)
        val copy = create(src.name, newParent)
        SceneSerializer.applyObjectJson(copy, json)
        copy.parent = newParent
        for (child in childrenOf(src).toList()) {
            if (child !== copy) duplicate(child, copy)
        }
        return copy
    }

    fun moveInOrder(go: GameObject, delta: Int) {
        val siblings = objects.filter { it.parent === go.parent }
        val i = siblings.indexOf(go)
        val j = (i + delta).coerceIn(0, siblings.size - 1)
        if (i == j) return
        val other = siblings[j]
        val a = objects.indexOf(go)
        val b = objects.indexOf(other)
        objects[a] = other
        objects[b] = go
        drawListDirty = true
    }
}

object SceneSerializer {
    fun objectToJson(go: GameObject): JSONObject {
        val o = JSONObject()
        o.put("id", go.id)
        o.put("name", go.name)
        o.put("tag", go.tag)
        o.put("active", go.active)
        o.put("order", go.order)
        o.put("x", go.x.toDouble()); o.put("y", go.y.toDouble())
        o.put("rotation", go.rotation.toDouble())
        o.put("scaleX", go.scaleX.toDouble()); o.put("scaleY", go.scaleY.toDouble())
        if (go.z != 0f) o.put("z", go.z.toDouble())
        if (go.rotX != 0f) o.put("rotX", go.rotX.toDouble())
        if (go.rotY != 0f) o.put("rotY", go.rotY.toDouble())
        if (go.scaleZ != 1f) o.put("scaleZ", go.scaleZ.toDouble())
        go.parent?.let { o.put("parent", it.id) }
        val comps = JSONArray()
        for (c in go.components) comps.put(c.toJson())
        o.put("components", comps)
        return o
    }

    /** Applies everything except id and parent. */
    fun applyObjectJson(go: GameObject, o: JSONObject) {
        go.tag = o.optString("tag", "Untagged")
        go.active = o.optBoolean("active", true)
        go.order = o.optInt("order", 0)
        go.x = o.optDouble("x", 0.0).toFloat()
        go.y = o.optDouble("y", 0.0).toFloat()
        go.rotation = o.optDouble("rotation", 0.0).toFloat()
        go.scaleX = o.optDouble("scaleX", 1.0).toFloat()
        go.scaleY = o.optDouble("scaleY", 1.0).toFloat()
        go.z = o.optDouble("z", 0.0).toFloat()
        go.rotX = o.optDouble("rotX", 0.0).toFloat()
        go.rotY = o.optDouble("rotY", 0.0).toFloat()
        go.scaleZ = o.optDouble("scaleZ", 1.0).toFloat()
        go.components.clear()
        val comps = o.optJSONArray("components") ?: JSONArray()
        for (i in 0 until comps.length()) {
            val cj = comps.getJSONObject(i)
            val c = ComponentRegistry.create(cj.optString("type")) ?: continue
            c.fromJson(cj)
            go.add(c)
        }
    }

    fun toJson(scene: Scene): JSONObject {
        val o = JSONObject()
        o.put("name", scene.name)
        o.put("gravityX", scene.gravityX.toDouble())
        o.put("gravityY", scene.gravityY.toDouble())
        o.put("gravityZ", scene.gravityZ.toDouble())
        o.put("gravity3D", scene.gravity3D.toDouble())
        o.put("ambient", String.format("#%08X", scene.ambient))
        o.put("fogColor", String.format("#%08X", scene.fogColor))
        o.put("fogStart", scene.fogStart.toDouble()); o.put("fogEnd", scene.fogEnd.toDouble())
        o.put("fog", scene.fog)
        o.put("nextId", scene.nextId)
        val arr = JSONArray()
        for (go in scene.objects) arr.put(objectToJson(go))
        o.put("objects", arr)
        return o
    }

    fun fromJson(o: JSONObject): Scene {
        val s = Scene(o.optString("name", "Main"))
        s.gravityX = o.optDouble("gravityX", 0.0).toFloat()
        s.gravityY = o.optDouble("gravityY", -9.81).toFloat()
        s.gravityZ = o.optDouble("gravityZ", 0.0).toFloat()
        s.gravity3D = o.optDouble("gravity3D", -9.81).toFloat()
        try { s.ambient = Component.parseColor(o.optString("ambient", "#FF4A505A")) } catch (_: Exception) {}
        try { s.fogColor = Component.parseColor(o.optString("fogColor", "#FF9DB4CF")) } catch (_: Exception) {}
        s.fogStart = o.optDouble("fogStart", 20.0).toFloat()
        s.fogEnd = o.optDouble("fogEnd", 80.0).toFloat()
        s.fog = o.optBoolean("fog", false)
        val arr = o.optJSONArray("objects") ?: JSONArray()
        val parents = HashMap<GameObject, Long>()
        var maxId = 0L
        for (i in 0 until arr.length()) {
            val oj = arr.getJSONObject(i)
            val go = GameObject(oj.optLong("id"), oj.optString("name", "GameObject"))
            applyObjectJson(go, oj)
            if (oj.has("parent")) parents[go] = oj.getLong("parent")
            s.objects.add(go)
            maxId = maxOf(maxId, go.id)
        }
        for ((go, pid) in parents) go.parent = s.findById(pid)
        s.nextId = maxOf(o.optLong("nextId", 1L), maxId + 1)
        return s
    }
}
