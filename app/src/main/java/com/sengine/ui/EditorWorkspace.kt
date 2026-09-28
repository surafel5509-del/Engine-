package com.sengine.ui

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import org.json.JSONObject

/** Persistent, touch-friendly editor layout model. Kept separate from scene/project data. */
data class EditorWorkspace(
    var preset: String = "3D",
    var leftWidth: Int = 210,
    var rightWidth: Int = 290,
    var bottomHeight: Int = 112,
    var leftVisible: Boolean = true,
    var rightVisible: Boolean = true,
    var bottomVisible: Boolean = true,
    var autoHide: Boolean = false,
    var leftTab: String = "Hierarchy",
    var rightTab: String = "Inspector",
    var bottomTab: String = "Console",
    var viewportTab: String = "Scene",
    var localSpace: Boolean = false,
    var grid: Boolean = true,
    var snap: Boolean = false,
) {
    fun save(context: Context) {
        val o = JSONObject().apply {
            put("preset", preset); put("leftWidth", leftWidth); put("rightWidth", rightWidth); put("bottomHeight", bottomHeight)
            put("leftVisible", leftVisible); put("rightVisible", rightVisible); put("bottomVisible", bottomVisible); put("autoHide", autoHide)
            put("leftTab", leftTab); put("rightTab", rightTab); put("bottomTab", bottomTab); put("viewportTab", viewportTab)
            put("localSpace", localSpace); put("grid", grid); put("snap", snap)
        }
        context.getSharedPreferences("editor_workspace", Context.MODE_PRIVATE).edit().putString("layout", o.toString()).apply()
    }

    fun applyPreset(name: String) {
        preset = name
        leftVisible = true; rightVisible = true; bottomVisible = true
        when (name) {
            "2D" -> { leftTab = "Hierarchy"; rightTab = "Inspector"; bottomTab = "Timeline"; viewportTab = "2D" }
            "3D" -> { leftTab = "Hierarchy"; rightTab = "Inspector"; bottomTab = "Assets"; viewportTab = "3D" }
            "Level Design" -> { leftTab = "Hierarchy"; rightTab = "Inspector"; bottomTab = "Assets"; viewportTab = "Scene" }
            "Animation" -> { leftTab = "Animation"; rightTab = "Tools"; bottomTab = "Timeline"; viewportTab = "Scene"; bottomHeight = 190 }
            "Shader" -> { leftTab = "Project"; rightTab = "Inspector"; bottomTab = "Shader Graph"; viewportTab = "Shader Preview"; bottomHeight = 200 }
            "Debug" -> { leftTab = "Hierarchy"; rightTab = "Tools"; bottomTab = "Debug"; viewportTab = "Game"; bottomHeight = 170 }
        }
    }

    companion object {
        fun load(context: Context): EditorWorkspace {
            val raw = context.getSharedPreferences("editor_workspace", Context.MODE_PRIVATE).getString("layout", null) ?: return EditorWorkspace()
            return try {
                val o = JSONObject(raw); EditorWorkspace(
                    o.optString("preset", "3D"), o.optInt("leftWidth", 210), o.optInt("rightWidth", 290), o.optInt("bottomHeight", 112),
                    o.optBoolean("leftVisible", true), o.optBoolean("rightVisible", true), o.optBoolean("bottomVisible", true), o.optBoolean("autoHide", false),
                    o.optString("leftTab", "Hierarchy"), o.optString("rightTab", "Inspector"), o.optString("bottomTab", "Console"), o.optString("viewportTab", "Scene"),
                    o.optBoolean("localSpace", false), o.optBoolean("grid", true), o.optBoolean("snap", false)
                )
            } catch (_: Exception) { EditorWorkspace() }
        }
    }
}

/** Makes a thin divider resize the view before it. Values are persisted by the caller. */
fun resizeHandle(context: Context, vertical: Boolean, onDelta: (Int) -> Unit, onFinished: () -> Unit): View {
    var last = 0f
    return View(context).apply {
        setBackgroundColor(C.BORDER)
        isClickable = true
        setOnTouchListener { v, e ->
            val p = if (vertical) e.rawX else e.rawY
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { last = p; v.setBackgroundColor(C.ACCENT); true }
                MotionEvent.ACTION_MOVE -> { val d = (p - last).toInt(); last = p; onDelta(d); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.setBackgroundColor(C.BORDER); onFinished(); true }
                else -> false
            }
        }
        layoutParams = LinearLayout.LayoutParams(if (vertical) context.dp(5) else LinearLayout.LayoutParams.MATCH_PARENT, if (vertical) LinearLayout.LayoutParams.MATCH_PARENT else context.dp(5))
    }
}
