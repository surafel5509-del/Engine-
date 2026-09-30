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
        val ids = ArrayList<Int>()
        fun walk(go: GameObject) {
            ids.add(go.id)
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

    fun load(project: Project, file: String): JSONObject? = try {
        val t = project.readAsset(file) ?: return null
        val o = JSONObject(t)
        if (o.optString("format") != FORMAT) null else o
    } catch (_: Exception) { null }

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
        val rootId = json.optInt("rootId", objects.getJSONObject(0).optInt("id"))
        val created = HashMap<Int, GameObject>()
        val rootPos = FloatArray(3)
        var first = true
        // parents always precede children in capture order, so a single pass rebuilds the tree
        for (i in 0 until objects.length()) {
            val o = objects.getJSONObject(i)
            val oldId = o.optInt("id", i)
            val oldParent = if (o.has("parent")) o.optInt("parent") else -1
            val parentGo = if (oldId == rootId) null else created[oldParent]
            val go = scene.create(scene.uniqueName(o.optString("name", "Object")), parentGo)
            SceneSerializer.applyObjectJson(go, o)
            if (first) {
                rootPos[0] = go.x; rootPos[1] = go.y; rootPos[2] = go.z
                go.x = x; go.y = y; go.z = z
                first = false
            } else {
                go.x += x - rootPos[0]; go.y += y - rootPos[1]; go.z += z - rootPos[2]
            }
            created[oldId] = go
        }
        return created[rootId] ?: objects.getJSONObject(0).optInt("id", -1).let { created[it] }
    }
}
