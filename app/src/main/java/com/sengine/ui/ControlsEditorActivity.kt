package com.sengine.ui

import android.content.pm.ActivityInfo
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sengine.engine.Input
import com.sengine.engine.controls.ControlDef
import com.sengine.engine.controls.ControlLayout
import com.sengine.project.Project
import com.sengine.project.ProjectManager

/**
 * On-screen Game Controls Editor: start from a preset, drag controls into place, add joysticks,
 * D-pads, buttons, steering and look pads, set ids/labels/icons/colours/sizes, and test live.
 * Saved to the project's controls.json and used by the player and exported games
 * (scripts read them with input.button("id") / input.stickX("id") and can switch layouts with input.setControls()).
 */
class ControlsEditorActivity : AppCompatActivity() {
    private lateinit var project: Project
    private lateinit var controls: GameControlsView
    private lateinit var panel: LinearLayout
    private lateinit var testInfo: TextView
    private lateinit var modeBtn: TextView
    private val input = Input()
    private var layout = ControlLayout.default()
    private var saved = ""
    private var testing = false
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        val ICONS = listOf("", "rocket", "bolt", "target", "fire", "refresh", "cube", "hammer", "stop", "shield", "star", "heart", "play", "pause", "gear", "menu", "car", "gamepad", "wand", "sparkle", "eye", "key", "lock", "trophy", "music", "camera", "plus", "close", "check", "back", "forward", "home")
        val COLORS = intArrayOf(0xFF5B7CFF.toInt(), 0xFF34D399.toInt(), 0xFFFF5C6C.toInt(), 0xFFFF9F43.toInt(), 0xFFA78BFA.toInt(), 0xFF22D3EE.toInt(), 0xFFFBBF24.toInt(), 0xFFE8EAF6.toInt(), 0xFF455A64.toInt())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = ProjectManager.open(this, intent.getStringExtra("project")!!)
        requestedOrientation = if (project.orientation == 1) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        layout = project.loadControls().copy()
        saved = layout.toJson().toString()

        val root = FrameLayout(this).apply { setBackgroundColor(0xFF0B0E1A.toInt()) }
        root.addView(GamePreview(), FrameLayout.LayoutParams(MATCH, MATCH))
        controls = GameControlsView(this) { input }.apply {
            editing = true
            this.layout = this@ControlsEditorActivity.layout
            onSelect = { refreshPanel() }
            onChanged = { refreshPanel() }
        }
        root.addView(controls, FrameLayout.LayoutParams(MATCH, MATCH))

        val bar = hbox().apply { background = round(0xE6111427.toInt(), dp(14).toFloat()); setPadding(dp(6), dp(4), dp(6), dp(4)) }
        bar.addView(iconButton("back", "Back", sizeDp = 36) { onBackPressedDispatcher.onBackPressed() })
        bar.addView(label("Controls Editor", 14f, C.TEXT, true).apply { setPadding(dp(8), 0, dp(10), 0) })
        bar.addView(iconTextButton("layers", "Presets") { presetMenu() }, lp(WRAP, WRAP).margins(dp(2), 0, dp(2), 0))
        bar.addView(iconTextButton("plus", "Add") { addMenu(it) }, lp(WRAP, WRAP).margins(dp(2), 0, dp(2), 0))
        modeBtn = iconTextButton("play", "Test") { toggleTest() }
        bar.addView(modeBtn, lp(WRAP, WRAP).margins(dp(2), 0, dp(2), 0))
        bar.addView(iconTextButton("save", "Save", C.ACCENT, 0xFFFFFFFF.toInt()) { save() }, lp(WRAP, WRAP).margins(dp(2), 0, 0, 0))
        root.addView(bar, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(8) })

        panel = vbox().apply { background = round(0xEE161A2D.toInt(), dp(14).toFloat(), dp(1), C.BORDER); setPadding(dp(12), dp(10), dp(12), dp(10)) }
        root.addView(ScrollView(this).apply { addView(panel) }, FrameLayout.LayoutParams(dp(250), WRAP, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(8) })
        testInfo = label("", 12f, C.ACCENT2).apply { background = round(0xCC0B0E1A.toInt(), dp(10).toFloat()); setPadding(dp(10), dp(6), dp(10), dp(6)); visibility = View.GONE }
        root.addView(testInfo, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply { topMargin = dp(64); leftMargin = dp(12) })
        setContentView(root)
        refreshPanel()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (layout.toJson().toString() == saved) { finish(); return }
                MaterialAlertDialogBuilder(this@ControlsEditorActivity).setTitle("Save controls?")
                    .setPositiveButton("Save") { _, _ -> save(); finish() }
                    .setNegativeButton("Discard") { _, _ -> finish() }
                    .setNeutralButton("Cancel", null).show()
            }
        })
    }

    /** Stylised game backdrop so the controls can be positioned against a "screen". */
    private inner class GamePreview : View(this@ControlsEditorActivity) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            p.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, 0xFF1B2450.toInt(), 0xFF0B0E1A.toInt(), android.graphics.Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p); p.shader = null
            p.color = 0xFF1F7A4D.toInt(); c.drawRect(0f, h * 0.72f, w, h, p)
            p.color = 0x22FFFFFF
            for (i in 1 until 10) c.drawRect(w * i / 10f, 0f, w * i / 10f + 1, h, p)
            for (i in 1 until 6) c.drawRect(0f, h * i / 6f, w, h * i / 6f + 1, p)
            p.color = 0xFF5B7CFF.toInt(); c.drawRoundRect(w * 0.47f, h * 0.6f, w * 0.53f, h * 0.72f, 12f, 12f, p)
            p.color = 0x55FFFFFF; p.textSize = h * 0.035f; p.textAlign = Paint.Align.CENTER
            c.drawText("Game preview — drag controls to position them", w / 2, h * 0.5f, p)
        }
    }

    private val selected: ControlDef? get() = layout.controls.getOrNull(controls.selected)

    private fun refreshPanel() {
        panel.removeAllViews()
        val c = selected
        if (testing) { panel.visibility = View.GONE; return }
        panel.visibility = View.VISIBLE
        if (c == null) {
            panel.addView(sectionHeader("gamepad", "Layout: ${layout.name}"))
            panel.addView(label("${layout.controls.size} controls. Tap a control to edit it, drag to move. Use Presets for ready-made layouts (Classic, Platformer, Twin-Stick, Action, Racing, First Person).", 12f, C.DIM))
            panel.addView(label("\nScripts:\ninput.button(\"A\")\ninput.buttonDown(\"Fire\")\ninput.stickX(\"aim\")\ninput.axisX / input.axisY (\"move\")\ninput.lookX / input.lookY\ninput.setControls(\"Racing\")", 11f, C.TEXT).apply { typeface = android.graphics.Typeface.MONOSPACE })
            return
        }
        panel.addView(sectionHeader(when (c.type) { "joystick" -> "joystick"; "dpad" -> "dpad"; "steer" -> "car"; "look" -> "eye"; else -> "gamepad" }, c.type.replaceFirstChar { it.uppercase() }))
        fun row(title: String, v: View) { panel.addView(label(title, 11f, C.DIM).apply { setPadding(0, dp(6), 0, dp(2)) }); panel.addView(v, lp(MATCH, WRAP)) }
        val idF = field(c.id)
        idF.setOnFocusChangeListener { _, has -> if (!has) { c.id = idF.text.toString().trim().ifBlank { c.id }; controls.invalidate() } }
        idF.setOnEditorActionListener { _, _, _ -> c.id = idF.text.toString().trim().ifBlank { c.id }; controls.invalidate(); false }
        row(if (c.type == "joystick") "Stick id (\"move\" drives input.axisX/Y)" else "Input id (used by scripts)", idF)
        if (c.type == "button") {
            val lbF = field(c.label)
            lbF.setOnFocusChangeListener { _, has -> if (!has) { c.label = lbF.text.toString(); controls.invalidate() } }
            lbF.setOnEditorActionListener { _, _, _ -> c.label = lbF.text.toString(); controls.invalidate(); false }
            row("Label", lbF)
            row("Icon", button(if (c.icon.isEmpty()) "None" else c.icon) { chooseIcon(c) }.apply {
                if (c.icon.isNotEmpty()) { setCompoundDrawables(Icons.drawable(this@ControlsEditorActivity, c.icon, C.TEXT, 18), null, null, null); compoundDrawablePadding = dp(6) }
            })
        }
        row("Size", slider((c.size * 100).toInt(), 5, 100) { c.size = it / 100f; controls.invalidate() })
        row("Opacity", slider((c.opacity * 100).toInt(), 10, 100) { c.opacity = it / 100f; controls.invalidate() })
        val colors = hbox()
        for (col in COLORS) colors.addView(View(this).apply {
            background = round(col, dp(12).toFloat(), if (col == c.color) dp(2) else 0, 0xFFFFFFFF.toInt())
            setOnClickListener { c.color = col; controls.invalidate(); refreshPanel() }
        }, lp(dp(22), dp(22)).margins(0, 0, dp(3), 0))
        row("Colour", colors)
        val pos = hbox()
        pos.addView(label("x ${fmt(c.x)}  y ${fmt(c.y)}", 11f, C.DIM), lp(0, WRAP, 1f))
        pos.addView(button("Mirror") { c.x = 1f - c.x; controls.invalidate(); refreshPanel() }.apply { textSize = 11f })
        row("Position", pos)
        val actions = hbox().apply { setPadding(0, dp(10), 0, 0) }
        actions.addView(iconTextButton("copy", "Duplicate") {
            val d = c.copy(); d.id = uniqueId(c.id); d.x = (c.x - 0.12f).coerceIn(0.05f, 0.95f)
            layout.controls += d; controls.selected = layout.controls.size - 1; controls.invalidate(); refreshPanel()
        }, lp(0, WRAP, 1f).margins(0, 0, dp(3), 0))
        actions.addView(iconTextButton("trash", "Delete", C.RED, 0xFFFFFFFF.toInt()) {
            layout.controls.remove(c); controls.selected = -1; controls.invalidate(); refreshPanel()
        }, lp(0, WRAP, 1f).margins(dp(3), 0, 0, 0))
        panel.addView(actions, lp(MATCH, WRAP))
    }

    private fun slider(v: Int, minV: Int, maxV: Int, onChange: (Int) -> Unit) = SeekBar(this).apply {
        max = maxV - minV; progress = (v - minV).coerceIn(0, maxV - minV)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onChange(p + minV) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun uniqueId(base: String): String {
        if (layout.controls.none { it.id == base }) return base
        var i = 2; while (layout.controls.any { it.id == "$base$i" }) i++; return "$base$i"
    }

    private fun chooseIcon(c: ControlDef) {
        MaterialAlertDialogBuilder(this).setTitle("Button icon").setItems(ICONS.map { if (it.isEmpty()) "None (use label)" else it }.toTypedArray()) { _, w ->
            c.icon = ICONS[w]; controls.invalidate(); refreshPanel()
        }.show()
    }

    private fun presetMenu() {
        val ps = ControlLayout.presets
        MaterialAlertDialogBuilder(this).setTitle("Controller presets").setItems(ps.map { "${it.name}  (${it.controls.size})" }.toTypedArray()) { _, w ->
            layout = ps[w].copy(); controls.layout = layout; controls.selected = -1; refreshPanel()
            toast("Applied ${ps[w].name}")
        }.show()
    }

    private fun addMenu(anchor: View) {
        val pm = android.widget.PopupMenu(this, anchor)
        listOf("Button", "Joystick", "D-Pad", "Steering arrows", "Look / camera pad", "Aim stick").forEach { pm.menu.add(it) }
        pm.setOnMenuItemClickListener { item ->
            val d = when (item.title.toString()) {
                "Button" -> ControlDef("button", uniqueId("Btn"), "B", 0.8f, 0.5f, 0.15f, COLORS[layout.controls.size % COLORS.size], "")
                "Joystick" -> ControlDef("joystick", uniqueId("move"), "", 0.15f, 0.72f, 0.32f, COLORS[0])
                "D-Pad" -> ControlDef("dpad", uniqueId("move"), "", 0.15f, 0.72f, 0.32f, COLORS[0])
                "Steering arrows" -> ControlDef("steer", uniqueId("steer"), "", 0.16f, 0.8f, 0.2f, COLORS[0])
                "Look / camera pad" -> ControlDef("look", uniqueId("look"), "", 0.7f, 0.45f, 0.9f, 0x00FFFFFF)
                else -> ControlDef("joystick", uniqueId("aim"), "", 0.85f, 0.72f, 0.32f, COLORS[2], "target")
            }
            layout.controls += d; controls.selected = layout.controls.size - 1; controls.invalidate(); refreshPanel()
            true
        }
        pm.show()
    }

    private val testTick = object : Runnable {
        override fun run() {
            if (!testing) return
            val pressed = input.rawButtons.filter { it.value }.keys.joinToString(", ").ifEmpty { "–" }
            val sticks = input.rawSticks.entries.joinToString("  ") { "${it.key}(${fmt(Math.round(it.value[0] * 100) / 100f)}, ${fmt(Math.round(it.value[1] * 100) / 100f)})" }
            testInfo.text = "TEST MODE\nmove: (${fmt(Math.round(input.joyX * 100) / 100f)}, ${fmt(Math.round(input.joyY * 100) / 100f)})  A=${input.rawA} B=${input.rawB}\nbuttons: $pressed\n$sticks\nlook: ${Math.round(input.rawLookX)}, ${Math.round(input.rawLookY)}"
            handler.postDelayed(this, 60)
        }
    }

    private fun toggleTest() {
        testing = !testing
        controls.editing = !testing
        controls.selected = -1
        controls.layout = layout
        modeBtn.text = if (testing) "Edit" else "Test"
        modeBtn.setCompoundDrawables(Icons.drawable(this, if (testing) "wrench" else "play", C.TEXT, 18), null, null, null)
        testInfo.visibility = if (testing) View.VISIBLE else View.GONE
        refreshPanel()
        if (testing) handler.post(testTick)
    }

    private fun save() {
        project.saveControls(layout)
        saved = layout.toJson().toString()
        toast("Controls saved — used by Play and exported games")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
