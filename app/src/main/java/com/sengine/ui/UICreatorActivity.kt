package com.sengine.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sengine.engine.core.Component
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SpriteRenderer
import com.sengine.engine.core.TextRenderer
import com.sengine.engine.core.UIButton
import com.sengine.engine.core.UIPanel
import com.sengine.engine.core.UIProgress
import com.sengine.project.Project
import com.sengine.project.ProjectManager

/**
 * UI Creator: visual WYSIWYG editor for a scene's game UI (panels, buttons, text, bars) with drag & drop,
 * snapping, anchors, a layer list, ready-made screens (menu, HUD, pause, game over) and a property panel.
 */
class UICreatorActivity : AppCompatActivity() {
    private lateinit var project: Project
    private lateinit var scene: Scene
    private var selected: GameObject? = null
    private lateinit var stage: Stage
    private lateinit var layerList: LinearLayout
    private lateinit var props: LinearLayout
    private lateinit var title: TextView
    private var snap = true
    private var dirty = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra("project") ?: run { finish(); return }
        project = ProjectManager.open(this, name)
        scene = project.loadScene(intent.getStringExtra("scene") ?: project.startScene)
        Scene.uiHalfW = 5f * 16f / 9f
        val root = vbox().apply { setBackgroundColor(C.BG) }
        val bar = hbox().apply { setBackgroundColor(C.HEADER); setPadding(dp(6), dp(4), dp(8), dp(4)); gravity = Gravity.CENTER_VERTICAL }
        bar.addView(iconButton("back", "Back", sizeDp = 36) { close() })
        title = label("", 14f, C.TEXT, true).apply { setPadding(dp(8), 0, 0, 0) }
        bar.addView(vbox().apply { addView(label("UI Creator", 11f, C.DIM)); addView(title) }, lp(0, WRAP, 1f))
        bar.addView(iconTextButton("scene", "Scene", C.PANEL2) { pickScene() }, lp(WRAP, WRAP).margins(dp(4), 0, 0, 0))
        val snapBtn = iconTextButton("magnet", "Snap", C.SEL) {}
        snapBtn.setOnClickListener { snap = !snap; snapBtn.setButtonColor(if (snap) C.SEL else C.PANEL2) }
        bar.addView(snapBtn, lp(WRAP, WRAP).margins(dp(4), 0, 0, 0))
        bar.addView(iconTextButton("gift", "Screens", C.PANEL2) { presets() }, lp(WRAP, WRAP).margins(dp(4), 0, 0, 0))
        bar.addView(iconTextButton("save", "Save", C.ACCENT, 0xFF000000.toInt()) { save() }, lp(WRAP, WRAP).margins(dp(4), 0, 0, 0))
        root.addView(bar, lp(MATCH, WRAP))

        val body = hbox()
        val left = vbox().apply { setPadding(dp(8), dp(8), dp(8), dp(8)) }
        left.addView(sectionHeader("plus", "Add"))
        for ((ic, t) in listOf("frame" to "Panel", "cursor" to "Button", "text" to "Text", "chart" to "Progress bar", "image" to "Image")) {
            left.addView(iconTextButton(ic, t, C.PANEL2) { add(t) }, lp(MATCH, WRAP).margins(0, dp(2), 0, dp(2)))
        }
        left.addView(sectionHeader("layers", "Layers"), lp(MATCH, WRAP).margins(0, dp(10), 0, dp(4)))
        layerList = vbox()
        left.addView(layerList)
        body.addView(ScrollView(this).apply { addView(left); setBackgroundColor(C.PANEL) }, lp(dp(180), MATCH))
        stage = Stage(this)
        body.addView(stage, lp(0, MATCH, 1f))
        props = vbox().apply { setPadding(dp(10), dp(8), dp(10), dp(8)) }
        body.addView(ScrollView(this).apply { addView(props); setBackgroundColor(C.PANEL) }, lp(dp(260), MATCH))
        root.addView(body, lp(MATCH, 0, 1f))
        setContentView(root)
        refresh()
    }

    override fun onBackPressed() { close() }

    private fun close() {
        if (!dirty) { finish(); return }
        MaterialAlertDialogBuilder(this).setTitle("Save UI changes?").setPositiveButton("Save") { _, _ -> save(); finish() }
            .setNegativeButton("Discard") { _, _ -> finish() }.setNeutralButton("Cancel", null).show()
    }

    private fun isUi(go: GameObject) = go.components.any { it is UIPanel || it is UIButton || it is UIProgress } ||
        go.getAny<TextRenderer>()?.screenSpace == true || go.getAny<SpriteRenderer>()?.screenSpace == true

    private fun uiObjects() = scene.objects.filter { isUi(it) && !it.destroyed }

    private fun refresh() {
        title.text = "${project.name} / ${scene.name}  ·  ${uiObjects().size} UI elements${if (dirty) "  •" else ""}"
        layerList.removeAllViews()
        for (go in uiObjects().sortedByDescending { it.order }) {
            val row = hbox().apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(4), dp(2), dp(4))
                background = round(if (go === selected) C.SEL else C.PANEL2, dp(6).toFloat())
                setOnClickListener { select(go) }
            }
            val kind = when { go.getAny<UIButton>() != null -> "cursor"; go.getAny<UIPanel>() != null -> "frame"; go.getAny<UIProgress>() != null -> "chart"; go.getAny<TextRenderer>() != null -> "text"; else -> "image" }
            row.addView(android.widget.ImageView(this).apply { setImageDrawable(Icons.drawable(this@UICreatorActivity, kind, C.DIM, 14)) }, lp(dp(18), dp(18)))
            val depth = generateSequence(go.parent) { it.parent }.count()
            row.addView(label("  ".repeat(depth) + go.name, 12f, if (go.active) C.TEXT else C.DIM), lp(0, WRAP, 1f).margins(dp(4), 0, 0, 0))
            row.addView(iconButton(if (go.active) "eye" else "eye_off", "Visible", sizeDp = 26) { go.active = !go.active; dirty = true; refresh() })
            layerList.addView(row, lp(MATCH, WRAP).margins(0, dp(1), 0, dp(1)))
        }
        renderProps()
        stage.invalidate()
    }

    private fun select(go: GameObject?) { selected = go; refresh() }

    private fun renderProps() {
        props.removeAllViews()
        val go = selected ?: run { props.addView(label("Tap an element on the canvas or in Layers to edit it. Drag to move; snap keeps things aligned to a 0.25 grid.", 12f, C.DIM)); return }
        props.addView(sectionHeader("sliders", go.name))
        val nameF = field(go.name)
        nameF.setOnFocusChangeListener { _, f -> if (!f && nameF.text.isNotBlank()) { go.name = nameF.text.toString(); dirty = true; refresh() } }
        props.addView(nameF, lp(MATCH, WRAP))
        val pos = hbox()
        val xf = field(fmt(go.x), numeric = true); val yf = field(fmt(go.y), numeric = true)
        xf.setOnFocusChangeListener { _, f -> if (!f) xf.text.toString().toFloatOrNull()?.let { go.x = it; changed() } }
        yf.setOnFocusChangeListener { _, f -> if (!f) yf.text.toString().toFloatOrNull()?.let { go.y = it; changed() } }
        pos.addView(label("X", 12f, C.DIM)); pos.addView(xf, lp(0, WRAP, 1f)); pos.addView(label(" Y", 12f, C.DIM)); pos.addView(yf, lp(0, WRAP, 1f))
        props.addView(pos, lp(MATCH, WRAP).margins(0, dp(4), 0, 0))
        val actions = hbox()
        actions.addView(iconButton("copy", "Duplicate", sizeDp = 34) { val c = scene.duplicate(go); c.x += 0.3f; c.y -= 0.3f; dirty = true; select(c) })
        actions.addView(iconButton("forward", "Bring forward", sizeDp = 34) { go.order += 1; changed() }, lp(WRAP, WRAP).margins(dp(4), 0, 0, 0))
        actions.addView(iconButton("back", "Send backward", sizeDp = 34) { go.order -= 1; changed() }, lp(WRAP, WRAP).margins(dp(4), 0, 0, 0))
        actions.addView(iconButton("trash", "Delete", sizeDp = 34) {
            val gone = scene.objects.filter { it === go || go.isAncestorOf(it) }.toSet(); scene.objects.removeAll(gone); dirty = true; select(null)
        }, lp(WRAP, WRAP).margins(dp(4), 0, 0, 0))
        props.addView(actions, lp(MATCH, WRAP).margins(0, dp(6), 0, 0))
        for (c in go.components) if (c is UIPanel || c is UIButton || c is UIProgress || c is TextRenderer || c is SpriteRenderer) PropForm.build(this, c, props) { changed(false) }
    }

    private fun changed(rebuild: Boolean = true) { dirty = true; if (rebuild) refresh() else stage.invalidate() }

    private fun uniqueName(base: String) = scene.uniqueName(base)

    private fun add(kind: String) {
        val go = scene.create(uniqueName(kind.replace(" ", "")))
        go.order = 200 + uiObjects().size
        when (kind) {
            "Panel" -> go.add(UIPanel()).also { it.width = 6f; it.height = 4f; it.color = 0xDD111111.toInt(); it.corner = 0.3f; it.borderColor = 0xFF444444.toInt(); it.border = 0.04f }
            "Button" -> go.add(UIButton()).also { it.text = "Button"; it.width = 3.2f; it.height = 0.9f; it.color = 0xFFFFFFFF.toInt(); it.textColor = 0xFF000000.toInt(); it.corner = 0.25f }
            "Text" -> go.add(TextRenderer()).also { it.text = "Text"; it.size = 0.5f; it.screenSpace = true; it.bold = true }
            "Progress bar" -> go.add(UIProgress()).also { it.width = 4f; it.height = 0.35f; it.value = 0.7f }
            else -> { go.add(SpriteRenderer()).also { it.screenSpace = true }; go.scaleX = 1.5f; go.scaleY = 1.5f }
        }
        dirty = true; select(go)
    }

    private fun presets() {
        val names = arrayOf("Main menu (title + Play/Help/Quit)", "HUD (score, health bar, pause)", "Pause menu", "Game over screen", "Dialog box", "Mobile shop row")
        MaterialAlertDialogBuilder(this).setTitle("Insert ready-made screen").setItems(names) { _, i -> UIScreens.insert(scene, i); dirty = true; refresh() }.show()
    }

    private fun pickScene() {
        val list = project.listScenes()
        MaterialAlertDialogBuilder(this).setTitle("Edit UI of scene").setItems(list.toTypedArray()) { _, i ->
            if (dirty) save()
            scene = project.loadScene(list[i]); selected = null; dirty = false; refresh()
        }.show()
    }

    private fun save() { project.saveScene(scene); dirty = false; Toast.makeText(this, "UI saved to ${scene.name}", Toast.LENGTH_SHORT).show(); refresh() }

    /** Rect of an element in canvas units (cx, cy, w, h). */
    private fun bounds(go: GameObject): FloatArray {
        val w = go.computeWorld()
        val sx = w.scaleX; val sy = w.scaleY
        val (bw, bh) = go.getAny<UIButton>()?.let { it.width to it.height } ?: go.getAny<UIPanel>()?.let { it.width to it.height }
            ?: go.getAny<UIProgress>()?.let { it.width to it.height }
            ?: go.getAny<TextRenderer>()?.let { t -> (t.text.lines().maxOfOrNull { it.length } ?: 1) * t.size * 0.55f to t.text.lines().size * t.size * 1.2f }
            ?: (1f to 1f)
        return floatArrayOf(w.tx, w.ty, bw * sx, bh * sy)
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class Stage(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        private var scale = 1f; private var cx = 0f; private var cy = 0f
        private var dragOff = floatArrayOf(0f, 0f)
        private var dragging: GameObject? = null

        private fun layout() {
            val hw = Scene.uiHalfW
            scale = minOf(width * 0.94f / (hw * 2), height * 0.94f / 10f)
            cx = width / 2f; cy = height / 2f
        }
        private fun sx(x: Float) = cx + x * scale
        private fun sy(y: Float) = cy - y * scale

        override fun onDraw(c: Canvas) {
            layout()
            c.drawColor(0xFF050505.toInt())
            val hw = Scene.uiHalfW
            p.style = Paint.Style.FILL; p.color = 0xFF1A1A1A.toInt()
            c.drawRect(sx(-hw), sy(5f), sx(hw), sy(-5f), p)
            // grid
            p.color = 0x14FFFFFF; p.strokeWidth = 1f
            var g = -hw; while (g <= hw) { c.drawLine(sx(g), sy(5f), sx(g), sy(-5f), p); g += 1f }
            g = -5f; while (g <= 5f) { c.drawLine(sx(-hw), sy(g), sx(hw), sy(g), p); g += 1f }
            p.color = 0x33FFFFFF; c.drawLine(sx(0f), sy(5f), sx(0f), sy(-5f), p); c.drawLine(sx(-hw), sy(0f), sx(hw), sy(0f), p)
            for (go in uiObjects().filter { it.isActiveInHierarchy() || it === selected || generateSequence(it) { x -> x.parent }.any { a -> a === selected } }.sortedBy { it.order }) drawEl(c, go)
            selected?.let { s ->
                val b = bounds(s)
                p.style = Paint.Style.STROKE; p.color = C.ACCENT; p.strokeWidth = 3f
                p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 6f), 0f)
                c.drawRect(sx(b[0] - b[2] / 2), sy(b[1] + b[3] / 2), sx(b[0] + b[2] / 2), sy(b[1] - b[3] / 2), p)
                p.pathEffect = null
            }
            p.style = Paint.Style.STROKE; p.color = C.BORDER; p.strokeWidth = 2f
            c.drawRect(sx(-hw), sy(5f), sx(hw), sy(-5f), p)
        }

        private fun drawEl(c: Canvas, go: GameObject) {
            val b = bounds(go)
            val r = RectF(sx(b[0] - b[2] / 2), sy(b[1] + b[3] / 2), sx(b[0] + b[2] / 2), sy(b[1] - b[3] / 2))
            p.style = Paint.Style.FILL
            go.getAny<UIPanel>()?.let { u ->
                p.color = u.color; c.drawRoundRect(r, u.corner * scale, u.corner * scale, p)
                if (u.border > 0f) { p.style = Paint.Style.STROKE; p.strokeWidth = u.border * scale; p.color = u.borderColor; c.drawRoundRect(r, u.corner * scale, u.corner * scale, p) }
            }
            go.getAny<UIProgress>()?.let { u ->
                p.color = u.backColor; c.drawRoundRect(r, u.corner * scale, u.corner * scale, p)
                p.color = u.fillColor; c.drawRoundRect(RectF(r.left, r.top, r.left + r.width() * u.value, r.bottom), u.corner * scale, u.corner * scale, p)
            }
            go.getAny<UIButton>()?.let { u ->
                p.color = u.color; c.drawRoundRect(r, u.corner * scale, u.corner * scale, p)
                tp.color = u.textColor; tp.textSize = u.textSize * scale
                c.drawText(u.text, r.centerX(), r.centerY() + tp.textSize * 0.35f, tp)
            }
            go.getAny<TextRenderer>()?.let { t ->
                tp.color = t.color; tp.textSize = t.size * scale
                tp.textAlign = when (t.align) { 0 -> Paint.Align.LEFT; 2 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
                val x = when (t.align) { 0 -> r.left; 2 -> r.right; else -> r.centerX() }
                t.text.lines().forEachIndexed { i, line -> c.drawText(line, x, r.top + tp.textSize * (1f + i * 1.2f), tp) }
                tp.textAlign = Paint.Align.CENTER
            }
            go.getAny<SpriteRenderer>()?.takeIf { it.screenSpace && go.getAny<UIPanel>() == null }?.let { s ->
                p.color = s.color; c.drawRect(r, p)
                tp.color = 0xFF000000.toInt(); tp.textSize = 10f * resources.displayMetrics.density
                if (s.texture.isNotBlank()) c.drawText(s.texture, r.centerX(), r.centerY(), tp)
            }
        }

        private fun hit(x: Float, y: Float): GameObject? = uiObjects().filter { it.isActiveInHierarchy() }.sortedByDescending { it.order }.firstOrNull { go ->
            val b = bounds(go); x in (b[0] - b[2] / 2)..(b[0] + b[2] / 2) && y in (b[1] - b[3] / 2)..(b[1] + b[3] / 2)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val ux = (e.x - cx) / scale; val uy = (cy - e.y) / scale
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val h = hit(ux, uy)
                    // prefer children of the selected element's hierarchy when tapping overlapping elements
                    dragging = h
                    if (h !== selected) select(h)
                    h?.let { dragOff = floatArrayOf(it.x - ux, it.y - uy) }
                }
                MotionEvent.ACTION_MOVE -> dragging?.let { go ->
                    var nx = ux + dragOff[0]; var ny = uy + dragOff[1]
                    if (snap) { nx = Math.round(nx * 4) / 4f; ny = Math.round(ny * 4) / 4f }
                    if (nx != go.x || ny != go.y) { go.x = nx; go.y = ny; dirty = true; invalidate() }
                }
                MotionEvent.ACTION_UP -> { if (dragging != null) refresh(); dragging = null }
            }
            return true
        }
    }
}

/** Ready-made UI screens inserted by the UI Creator (and usable by templates / the agent). */
object UIScreens {
    fun insert(s: Scene, kind: Int) {
        fun obj(name: String, x: Float, y: Float, parent: GameObject? = null) = s.create(s.uniqueName(name), parent).also { it.x = x; it.y = y; it.order = 210 }
        fun panel(name: String, w: Float, h: Float, x: Float = 0f, y: Float = 0f, parent: GameObject? = null, color: Int = 0xE6101010.toInt()) =
            obj(name, x, y, parent).also { it.add(UIPanel()).apply { width = w; height = h; this.color = color; corner = 0.35f; borderColor = 0xFF3A3A3A.toInt(); border = 0.04f } }
        fun text(name: String, t: String, x: Float, y: Float, size: Float, parent: GameObject? = null, color: Int = 0xFFFFFFFF.toInt(), anchor: Int = 0, align: Int = 1) =
            obj(name, x, y, parent).also { it.order = 230; it.add(TextRenderer()).apply { text = t; this.size = size; this.color = color; bold = true; screenSpace = true; this.anchor = anchor; this.align = align } }
        fun btn(name: String, t: String, x: Float, y: Float, action: String, parent: GameObject? = null, primary: Boolean = false, w: Float = 3.6f, anchor: Int = 0) =
            obj(name, x, y, parent).also { it.order = 225; it.add(UIButton()).apply {
                text = t; width = w; height = 0.9f; corner = 0.3f; this.action = action; this.anchor = anchor
                color = if (primary) 0xFFFFFFFF.toInt() else 0xFF242424.toInt(); textColor = if (primary) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                pressedColor = if (primary) 0xFFBDBDBD.toInt() else 0xFF3A3A3A.toInt()
            } }
        when (kind) {
            0 -> {
                val p = panel("MenuPanel", 7f, 7.4f)
                text("MenuTitle", "MY GAME", 0f, 2.6f, 0.9f, p)
                text("MenuSubtitle", "Tap Play to start", 0f, 1.8f, 0.3f, p, 0xFF9E9E9E.toInt())
                btn("PlayButton", "PLAY", 0f, 0.6f, "scene:Main", p, true)
                btn("HelpButton", "HOW TO PLAY", 0f, -0.5f, "show:HelpPanel", p)
                btn("QuitButton", "QUIT", 0f, -1.6f, "quit", p)
            }
            1 -> {
                text("ScoreText", "SCORE 0", -0.4f, -0.5f, 0.45f, anchor = 6, align = 2)
                obj("HealthBar", 2.6f, -0.45f).also { it.order = 220; it.add(UIProgress()).apply { width = 4f; height = 0.32f; value = 1f; fillColor = 0xFF22C55E.toInt(); backColor = 0x88000000.toInt(); corner = 0.16f; anchor = 5 } }
                text("HealthLabel", "HP", 0.35f, -0.47f, 0.3f, anchor = 5, align = 0)
                btn("PauseButton", "II", -0.7f, -1.3f, "pause;show:PausePanel", w = 0.9f, anchor = 6)
            }
            2 -> {
                val p = panel("PausePanel", 6f, 5.6f)
                text("PauseTitle", "PAUSED", 0f, 1.8f, 0.7f, p)
                btn("ResumeButton", "RESUME", 0f, 0.5f, "resume;hide:PausePanel", p, true)
                btn("RestartButton", "RESTART", 0f, -0.6f, "resume;reload", p)
                btn("MenuButton", "MAIN MENU", 0f, -1.7f, "resume;scene:Menu", p)
                p.active = false
            }
            3 -> {
                val p = panel("GameOverPanel", 7f, 6f)
                text("GameOverTitle", "GAME OVER", 0f, 2f, 0.8f, p, 0xFFFF5A5A.toInt())
                text("FinalScore", "Score 0", 0f, 1f, 0.4f, p)
                btn("RetryButton", "TRY AGAIN", 0f, -0.3f, "reload", p, true)
                btn("QuitToMenu", "MENU", 0f, -1.4f, "scene:Menu", p)
                p.active = false
            }
            4 -> {
                val p = panel("DialogPanel", 12f, 2.6f, 0f, -3.4f)
                text("DialogName", "Guide", -5.4f, 0.8f, 0.35f, p, 0xFFF5D06B.toInt(), align = 0)
                text("DialogText", "Welcome, traveller! Tap to continue.", -5.4f, 0.1f, 0.32f, p, align = 0)
                btn("DialogNext", "NEXT ›", 4.6f, -0.8f, "hide:DialogPanel", p, true, 2.2f)
            }
            else -> {
                val p = panel("ShopRow", 10f, 1.6f, 0f, 0f)
                for (i in 0 until 3) btn("ShopItem${i + 1}", listOf("Speed 100", "Shield 250", "Laser 500")[i], -3.3f + i * 3.3f, 0f, "call:buy", p, i == 0, 3f)
            }
        }
    }
}
