package com.sengine.agent

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Saved model profiles + the active one. Stored privately in the app's files dir (never exported with games). */
class AgentConfig(private val file: File) {
    val profiles = ArrayList<ModelProfile>()
    var activeId: String = ""
    var maxSteps = 60

    init { load() }

    fun load() {
        profiles.clear()
        if (file.exists()) try {
            val o = JSONObject(file.readText())
            val arr = o.optJSONArray("profiles") ?: JSONArray()
            for (i in 0 until arr.length()) profiles += ModelProfile.fromJson(arr.getJSONObject(i))
            activeId = o.optString("active")
            maxSteps = o.optInt("maxSteps", 60)
        } catch (_: Exception) {}
        if (profiles.none { it.kind == ProviderKind.LOCAL }) profiles.add(0, ModelProfile("offline", "Offline Planner (no key)", ProviderKind.LOCAL, "", "builtin"))
        if (profiles.none { it.id == activeId }) activeId = profiles.first().id
    }

    fun save() {
        file.parentFile?.mkdirs()
        file.writeText(JSONObject().put("active", activeId).put("maxSteps", maxSteps)
            .put("profiles", JSONArray().also { a -> profiles.forEach { a.put(it.toJson()) } }).toString(2))
    }

    val active: ModelProfile get() = profiles.firstOrNull { it.id == activeId } ?: profiles.first()

    fun modelFor(p: ModelProfile = active): ChatModel = if (p.kind == ProviderKind.LOCAL) LocalPlanner() else LlmClient(p)

    /** Checks a profile is usable before running; returns an error message or null. */
    fun validate(p: ModelProfile): String? = when {
        p.kind == ProviderKind.LOCAL -> null
        p.kind.needsKey && p.apiKey.isBlank() -> "Add your ${p.kind.title} API key in Agent Settings."
        p.baseUrl.isBlank() || !p.baseUrl.startsWith("http") -> "Base URL must start with http(s)://"
        p.model.isBlank() -> "Model name is empty."
        else -> null
    }
}
