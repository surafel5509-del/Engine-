package com.sengine.engine.controls

import org.json.JSONArray
import org.json.JSONObject

/**
 * A virtual on-screen control. Positions are the control centre as fractions of the screen
 * (0..1, y down); size is a fraction of the smaller screen dimension.
 * Types: joystick, button, dpad, look (drag pad), steer (left/right arrows pair).
 */
class ControlDef(
    var type: String,
    var id: String,
    var label: String = "",
    var x: Float = 0.5f,
    var y: Float = 0.5f,
    var size: Float = 0.2f,
    var color: Int = 0xFF4C8DFF.toInt(),
    var icon: String = "",
    var opacity: Float = 0.55f,
) {
    fun toJson(): JSONObject = JSONObject().put("type", type).put("id", id).put("label", label).put("x", x.toDouble()).put("y", y.toDouble())
        .put("size", size.toDouble()).put("color", String.format("#%08X", color)).put("icon", icon).put("opacity", opacity.toDouble())

    fun copy() = ControlDef(type, id, label, x, y, size, color, icon, opacity)

    companion object {
        fun fromJson(o: JSONObject) = ControlDef(
            o.optString("type", "button"), o.optString("id", "A"), o.optString("label", ""),
            o.optDouble("x", 0.5).toFloat(), o.optDouble("y", 0.5).toFloat(), o.optDouble("size", 0.2).toFloat(),
            try { o.optString("color", "#FF4C8DFF").removePrefix("#").let { if (it.length == 6) "FF$it" else it }.toLong(16).toInt() } catch (_: Exception) { 0xFF4C8DFF.toInt() },
            o.optString("icon", ""), o.optDouble("opacity", 0.55).toFloat(),
        )
    }
}

class ControlLayout(var name: String = "Custom", val controls: MutableList<ControlDef> = ArrayList()) {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("controls", JSONArray().also { a -> controls.forEach { a.put(it.toJson()) } })
    fun copy() = ControlLayout(name, controls.map { it.copy() }.toMutableList())

    companion object {
        const val FILE = "controls.json"
        val TYPES = listOf("button", "joystick", "dpad", "look", "steer")

        fun fromJson(o: JSONObject): ControlLayout {
            val l = ControlLayout(o.optString("name", "Custom"))
            val a = o.optJSONArray("controls") ?: JSONArray()
            for (i in 0 until a.length()) l.controls.add(ControlDef.fromJson(a.getJSONObject(i)))
            return l
        }

        private const val BLUE = 0xFF4C8DFF.toInt()
        private const val GREEN = 0xFF3FB950.toInt()
        private const val RED = 0xFFF85149.toInt()
        private const val ORANGE = 0xFFFFA14A.toInt()
        private const val PURPLE = 0xFFA371F7.toInt()

        val presets: List<ControlLayout> get() = listOf(
            ControlLayout("Classic (Joystick + A/B)", mutableListOf(
                ControlDef("joystick", "move", "", 0.14f, 0.74f, 0.34f, BLUE),
                ControlDef("button", "A", "A", 0.9f, 0.7f, 0.17f, GREEN),
                ControlDef("button", "B", "B", 0.78f, 0.84f, 0.15f, RED),
            )),
            ControlLayout("Platformer (D-Pad)", mutableListOf(
                ControlDef("dpad", "move", "", 0.14f, 0.74f, 0.34f, BLUE),
                ControlDef("button", "A", "", 0.9f, 0.72f, 0.18f, GREEN, "rocket"),
                ControlDef("button", "B", "", 0.77f, 0.84f, 0.15f, RED, "bolt"),
            )),
            ControlLayout("Twin-Stick Shooter", mutableListOf(
                ControlDef("joystick", "move", "", 0.14f, 0.74f, 0.34f, BLUE),
                ControlDef("joystick", "aim", "", 0.86f, 0.74f, 0.34f, RED, "target"),
                ControlDef("button", "Reload", "", 0.66f, 0.86f, 0.12f, ORANGE, "refresh"),
                ControlDef("button", "Special", "", 0.66f, 0.66f, 0.12f, PURPLE, "bolt"),
            )),
            ControlLayout("Action (Fire + Jump)", mutableListOf(
                ControlDef("joystick", "move", "", 0.14f, 0.74f, 0.34f, BLUE),
                ControlDef("button", "Fire", "", 0.9f, 0.7f, 0.19f, RED, "target"),
                ControlDef("button", "A", "", 0.77f, 0.86f, 0.14f, GREEN, "rocket"),
                ControlDef("button", "Reload", "", 0.77f, 0.6f, 0.12f, ORANGE, "refresh"),
                ControlDef("button", "Switch", "", 0.9f, 0.46f, 0.11f, PURPLE, "cube"),
            )),
            ControlLayout("Racing", mutableListOf(
                ControlDef("steer", "steer", "", 0.16f, 0.8f, 0.2f, BLUE),
                ControlDef("button", "Gas", "", 0.9f, 0.74f, 0.2f, GREEN, "bolt"),
                ControlDef("button", "Brake", "", 0.75f, 0.84f, 0.16f, RED, "stop"),
                ControlDef("button", "Nitro", "", 0.9f, 0.46f, 0.12f, ORANGE, "fire"),
            )),
            ControlLayout("First Person / Builder", mutableListOf(
                ControlDef("joystick", "move", "", 0.14f, 0.74f, 0.32f, BLUE),
                ControlDef("look", "look", "", 0.7f, 0.45f, 0.9f, 0x00FFFFFF),
                ControlDef("button", "A", "", 0.92f, 0.78f, 0.15f, GREEN, "rocket"),
                ControlDef("button", "Break", "", 0.8f, 0.9f, 0.13f, RED, "hammer"),
                ControlDef("button", "Place", "", 0.8f, 0.66f, 0.13f, BLUE, "cube"),
            )),
            ControlLayout("FPS Shooter", mutableListOf(
                ControlDef("joystick", "move", "", 0.13f, 0.74f, 0.3f, BLUE),
                ControlDef("look", "look", "", 0.62f, 0.45f, 0.75f, 0x00FFFFFF),
                ControlDef("button", "Fire", "", 0.88f, 0.66f, 0.18f, RED, "target"),
                ControlDef("button", "Aim", "", 0.74f, 0.8f, 0.13f, BLUE, "search"),
                ControlDef("button", "A", "", 0.93f, 0.88f, 0.12f, GREEN, "rocket"),
                ControlDef("button", "Reload", "", 0.8f, 0.46f, 0.11f, ORANGE, "refresh"),
                ControlDef("button", "Grenade", "", 0.93f, 0.4f, 0.11f, PURPLE, "fire"),
                ControlDef("button", "Switch", "", 0.62f, 0.9f, 0.11f, PURPLE, "cube"),
            )),
            ControlLayout("Touch Only", mutableListOf()),
        )

        fun default(): ControlLayout = presets[0]
    }
}
