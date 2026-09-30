package com.sengine.project

import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SceneSerializer
import org.json.JSONArray
import org.json.JSONObject

/**
 * Unity-style prefabs: capture any GameObject subtree in the editor as a reusable `.prefab`
 * asset, then instantiate it into any scene (ids remapped, parents rebuilt, names uniquified).
 */
object PrefabIO {
    const val FORMAT = "prefab"

    /** Serialises [root] and all descendants (breadth-first: parents always precede children). */
    fun capture(scene: Scene, root: GameObject): JSONObject {
        val arr = JSONArray()
        fun walk(go: GameObject) {
            arr.put(SceneSerializer.objectToJson(go))
            for (c in scene.childrenOf(go)) walk(c)
        }
        walk(root)
        return JSONObject()
            .put("format", FORMAT)
            .put("name", root.name)
            .put("rootId", root.id)
            .put("objects", arr)
    }

    fun save(project: Project, name: String, json: JSONObject): String {
        val file = project.uniqueAssetName("$name.prefab")
        project.writeAsset(file, json.toString(2))
        return file
    }

    fun load(project: Project, file: String): JSONObject? {
        return try {
            val t = project.readAsset(file) ?: return null
            val o = JSONObject(t)
            if (o.optString("format") != FORMAT) null else o
        } catch (_: Exception) { null }
    }

    fun listPrefabs(project: Project): List<String> =
        project.listAssets().filter { it.endsWith(".prefab") }

    /**
     * Instantiates a captured prefab at (x, y, z). Returns the new root object, or null if the
     * asset is malformed. Positions shift by (x,y,z) minus the captured root position so children
     * keep their local layout; every name goes through the scene's uniquifier.
     */
    fun instantiate(scene: Scene, json: JSONObject, x: Float, y: Float, z: Float): GameObject? {
        val objects = json.optJSONArray("objects") ?: return null
        if (objects.length() == 0) return null
        val rootId = json.optLong("rootId", objects.getJSONObject(0).optLong("id"))
        val created = HashMap<Long, GameObject>()
        // parents always precede children in capture order, so a single pass rebuilds the tree;
        // captured transforms are parent-local, so only the root is moved to the spawn point
        for (i in 0 until objects.length()) {
            val o = objects.getJSONObject(i)
            val oldId = o.optLong("id", i.toLong())
            val oldParent = if (o.has("parent")) o.optLong("parent") else -1L
            val parentGo = if (oldId == rootId) null else created[oldParent]
            val go = scene.create(scene.uniqueName(o.optString("name", "Object")), parentGo)
            SceneSerializer.applyObjectJson(go, o)
            if (oldId == rootId || (i == 0 && !created.containsKey(rootId))) { go.x = x; go.y = y; go.z = z }
            created[oldId] = go
        }
        return created[rootId] ?: created[objects.getJSONObject(0).optLong("id", -1L)]
    }
}
