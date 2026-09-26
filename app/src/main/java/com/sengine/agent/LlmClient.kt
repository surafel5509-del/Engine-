package com.sengine.agent

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One chat turn. role = "user" | "assistant". */
class ChatMessage(val role: String, val content: String)

/** Anything that can answer a conversation — a real LLM or the offline planner. */
interface ChatModel {
    val label: String
    fun complete(system: String, messages: List<ChatMessage>): String
}

/** Supported API families. Most hosted models speak the OpenAI chat format, so one adapter covers many vendors. */
enum class ProviderKind(val title: String, val defaultBase: String, val defaultModel: String, val needsKey: Boolean = true) {
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    ANTHROPIC("Anthropic Claude", "https://api.anthropic.com/v1", "claude-3-5-sonnet-latest"),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-1.5-flash"),
    OPENROUTER("OpenRouter (any model)", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
    GROQ("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    MISTRAL("Mistral", "https://api.mistral.ai/v1", "mistral-large-latest"),
    XAI("xAI Grok", "https://api.x.ai/v1", "grok-2-latest"),
    TOGETHER("Together AI", "https://api.together.xyz/v1", "meta-llama/Llama-3.3-70B-Instruct-Turbo"),
    OLLAMA("Ollama / LM Studio (local)", "http://192.168.1.10:11434/v1", "llama3.1", needsKey = false),
    CUSTOM("Custom OpenAI-compatible", "https://example.com/v1", "model-name", needsKey = false),
    LOCAL("S Engine Offline Planner", "", "builtin", needsKey = false);

    val openAiCompatible get() = this != ANTHROPIC && this != GEMINI && this != LOCAL
}

/** A saved model configuration (the user can keep several and switch between them). */
class ModelProfile(
    var id: String = java.util.UUID.randomUUID().toString().take(8),
    var name: String = "My model",
    var kind: ProviderKind = ProviderKind.OPENAI,
    var baseUrl: String = kind.defaultBase,
    var model: String = kind.defaultModel,
    var apiKey: String = "",
    var temperature: Float = 0.4f,
    var maxTokens: Int = 4096,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("kind", kind.name).put("baseUrl", baseUrl)
        .put("model", model).put("apiKey", apiKey).put("temperature", temperature.toDouble()).put("maxTokens", maxTokens)

    companion object {
        fun fromJson(o: JSONObject) = ModelProfile(
            o.optString("id"), o.optString("name", "Model"),
            try { ProviderKind.valueOf(o.optString("kind", "OPENAI")) } catch (_: Exception) { ProviderKind.CUSTOM },
            o.optString("baseUrl"), o.optString("model"), o.optString("apiKey"),
            o.optDouble("temperature", 0.4).toFloat(), o.optInt("maxTokens", 4096),
        )
    }
}

/** HTTP client for the three API dialects (OpenAI-compatible, Anthropic Messages, Gemini generateContent). */
class LlmClient(private val p: ModelProfile, private val timeoutMs: Int = 180_000) : ChatModel {
    override val label get() = "${p.kind.title} · ${p.model}"

    override fun complete(system: String, messages: List<ChatMessage>): String = when (p.kind) {
        ProviderKind.ANTHROPIC -> anthropic(system, messages)
        ProviderKind.GEMINI -> gemini(system, messages)
        ProviderKind.LOCAL -> throw IllegalStateException("Offline planner is not an HTTP model")
        else -> openAi(system, messages)
    }

    private fun base() = p.baseUrl.trim().trimEnd('/')

    private fun openAi(system: String, messages: List<ChatMessage>): String {
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        messages.forEach { msgs.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val body = JSONObject().put("model", p.model).put("messages", msgs).put("temperature", p.temperature.toDouble()).put("max_tokens", p.maxTokens)
        val headers = HashMap<String, String>()
        if (p.apiKey.isNotBlank()) headers["Authorization"] = "Bearer ${p.apiKey.trim()}"
        if (p.kind == ProviderKind.OPENROUTER) { headers["HTTP-Referer"] = "https://sengine.app"; headers["X-Title"] = "S Engine" }
        val res = post("${base()}/chat/completions", body, headers)
        return res.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "")
    }

    private fun anthropic(system: String, messages: List<ChatMessage>): String {
        val msgs = JSONArray()
        messages.forEach { msgs.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val body = JSONObject().put("model", p.model).put("system", system).put("messages", msgs)
            .put("max_tokens", p.maxTokens).put("temperature", p.temperature.toDouble())
        val res = post("${base()}/messages", body, mapOf("x-api-key" to p.apiKey.trim(), "anthropic-version" to "2023-06-01"))
        val content = res.getJSONArray("content")
        val sb = StringBuilder()
        for (i in 0 until content.length()) { val c = content.getJSONObject(i); if (c.optString("type") == "text") sb.append(c.optString("text")) }
        return sb.toString()
    }

    private fun gemini(system: String, messages: List<ChatMessage>): String {
        val contents = JSONArray()
        messages.forEach {
            contents.put(JSONObject().put("role", if (it.role == "assistant") "model" else "user")
                .put("parts", JSONArray().put(JSONObject().put("text", it.content))))
        }
        val body = JSONObject().put("contents", contents)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("generationConfig", JSONObject().put("temperature", p.temperature.toDouble()).put("maxOutputTokens", p.maxTokens))
        val url = "${base()}/models/${p.model}:generateContent?key=${java.net.URLEncoder.encode(p.apiKey.trim(), "UTF-8")}"
        val res = post(url, body, emptyMap())
        val parts = res.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        return sb.toString()
    }

    private fun post(url: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 30_000; c.readTimeout = timeoutMs
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = try { JSONObject(text).let { o -> o.optJSONObject("error")?.optString("message") ?: o.optString("message", text) } } catch (_: Exception) { text }
                throw RuntimeException("HTTP $code: ${msg.take(400)}")
            }
            return JSONObject(text)
        } finally { c.disconnect() }
    }
}
