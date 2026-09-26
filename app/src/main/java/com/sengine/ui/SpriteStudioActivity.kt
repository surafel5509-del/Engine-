package com.sengine.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sengine.engine.texture.PixelDoc
import com.sengine.engine.texture.PngEncoder
import com.sengine.project.Project
import com.sengine.project.ProjectManager
import org.json.JSONObject

/**
 * Sprite Studio: professional pixel-art editor with layers, frames, onion skin, mirror drawing,
 * palettes and export to PNG sprite sheets + .anim clips used by the Animator.
 */
class SpriteStudioActivity : AppCompatActivity() {
    private var project: Project? = null
    private val doc = PixelDoc(32, 32)
    private var fileName = "NewSprite"
    private lateinit var canvasView: PixelCanvas
    private lateinit var framesBox: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var swatch: View
    private lateinit var paletteBox: LinearLayout
    private lateinit var previewView: PreviewView
    private val toolButtons = HashMap<Tool, android.widget.ImageView>()
    private var tool = Tool.PENCIL
    private var color = 0xFF000000.toInt()
    private var brush = 1
    private var onion = true
    private var palette = "PICO-8"
    private val recent = ArrayList<Int>()

    enum class Tool(val icon: String, val title: String) {
        PENCIL("pencil", "Pencil"), ERASER("eraser", "Eraser"), FILL("bucket", "Fill"), LINE("edge", "Line"),
        RECT("frame", "Rectangle"), RECT_FILL("layers", "Filled rect"), ELLIPSE("sphere", "Ellipse"), PICKER("pipette", "Color picker"), MOVE("move", "Move pixels")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = intent.getStringExtra("project")?.let { try { ProjectManager.open(this, it) } catch (_: Exception) { null } }
        intent.getStringExtra("file")?.let { open(it) }
        val root = vbox().apply { setBackgroundColor(C.BG) }

        // top bar
        val bar = hbox().apply { setBackgroundColor(C.HEADER); setPadding(dp(6), dp(4), dp(8), dp(4)); gravity = Gravity.CENTER_VERTICAL }
        bar.addView(iconButton("back", "Back", sizeDp = 36) { finish() })
        titleText = label("", 14f, C.TEXT, true).apply { setPadding(dp(8), 0, dp(8), 0) }
        bar.addView(vbox().apply { addView(label("Sprite Studio", 11f, C.DIM)); addView(titleText) })
        bar.addView(View(this), lp(0, 1, 1f))
        bar.addView(iconButton("undo", "Undo", sizeDp = 36) { doc.undo(); refreshAll() })
        bar.addView(iconButton("redo", "Redo", sizeDp = 36) { doc.redo(); refreshAll() })
        bar.addView(iconTextButton("plus", "New", C.PANEL2) { newDialog() }, lp(WRAP, WRAP).margins(dp(6), 0, 0, 0))
        bar.addView(iconTextButton("folder_open", "Open", C.PANEL2) { openDialog() }, lp(WRAP, WRAP).margins(dp(6), 0, 0, 0))
        bar.addView(iconTextButton("wand", "Effects", C.PANEL2) { effectsDialog() }, lp(WRAP, WRAP).margins(dp(6), 0, 0, 0))
        bar.addView(iconTextButton("save", "Save", C.ACCENT, 0xFF000000.toInt()) { save() }, lp(WRAP, WRAP).margins(dp(6), 0, 0, 0))
        root.addView(bar, lp(MATCH, WRAP))

        val body = hbox()
        // tools column
        val tools = vbox().apply { setPadding(dp(6), dp(6), dp(6), dp(6)); gravity = Gravity.CENTER_HORIZONTAL }
        for (t in Tool.values()) {
            val b = iconButton(t.icon, t.title, sizeDp = 40) { tool = t; refreshTools() }
            toolButtons[t] = b
            tools.addView(b, lp(WRAP, WRAP).margins(0, dp(2), 0, dp(2)))
        }
        tools.addView(label("Size", 10f, C.DIM).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP).margins(0, dp(6), 0, 0))
        val brushLabel = label("1px", 12f, C.TEXT, true).apply { gravity = Gravity.CENTER }
        tools.addView(brushLabel, lp(MATCH, WRAP))
        tools.addView(iconButton("plus", "Bigger brush", sizeDp = 30) { brush = (brush % 4) + 1; brushLabel.text = "${brush}px" })
        val mirrorBtn = iconButton("mirror", "Mirror X", sizeDp = 36) {}
        mirrorBtn.setOnClickListener { doc.mirrorX = !doc.mirrorX; mirrorBtn.setBg(if (doc.mirrorX) C.SEL else C.PANEL2) }
        tools.addView(mirrorBtn, lp(WRAP, WRAP).margins(0, dp(6), 0, 0))
        val onionBtn = iconButton("layers", "Onion skin", sizeDp = 36) {}
        onionBtn.setBg(C.SEL)
        onionBtn.setOnClickListener { onion = !onion; onionBtn.setBg(if (onion) C.SEL else C.PANEL2); canvasView.invalidate() }
        tools.addView(onionBtn, lp(WRAP, WRAP).margins(0, dp(4), 0, 0))
        body.addView(ScrollView(this).apply { addView(tools); setBackgroundColor(C.PANEL) }, lp(dp(58), MATCH))

        // canvas
        canvasView = PixelCanvas(this)
        body.addView(canvasView, lp(0, MATCH, 1f))

        // right panel
        val right = vbox().apply { setPadding(dp(10), dp(8), dp(10), dp(8)) }
        right.addView(sectionHeader("palette", "Color"))
        swatch = View(this).apply { setOnClickListener { ColorPickerDialog.show(this@SpriteStudioActivity, color) { pick(it) } } }
        right.addView(swatch, lp(MATCH, dp(38)).margins(0, dp(4), 0, dp(6)))
        val palRow = hbox().apply { gravity = Gravity.CENTER_VERTICAL }
        val palName = label(palette, 12f, C.TEXT)
        palRow.addView(palName, lp(0, WRAP, 1f))
        palRow.addView(iconButton("more", "Palettes", sizeDp = 28) {
            val names = PixelDoc.PALETTES.keys.toTypedArray()
            MaterialAlertDialogBuilder(this).setTitle("Palette").setItems(names) { _, i -> palette = names[i]; palName.text = palette; renderPalette() }.show()
        })
        right.addView(palRow)
        paletteBox = vbox()
        right.addView(paletteBox)
        right.addView(sectionHeader("film", "Preview"), lp(MATCH, WRAP).margins(0, dp(10), 0, dp(4)))
        previewView = PreviewView(this)
        right.addView(previewView, lp(MATCH, dp(120)))
        val fpsRow = hbox().apply { gravity = Gravity.CENTER_VERTICAL }
        fpsRow.addView(label("FPS", 12f, C.DIM), lp(0, WRAP, 1f))
        val fpsField = field(doc.fps.toInt().toString(), numeric = true)
        fpsField.setOnFocusChangeListener { _, f -> if (!f) doc.fps = fpsField.text.toString().toFloatOrNull()?.coerceIn(1f, 60f) ?: 10f }
        fpsRow.addView(fpsField, lp(dp(64), WRAP))
        right.addView(fpsRow, lp(MATCH, WRAP).margins(0, dp(6), 0, 0))
        right.addView(iconTextButton("layers", "Layers…", C.PANEL2) { layersDialog() }, lp(MATCH, WRAP).margins(0, dp(8), 0, 0))
        body.addView(ScrollView(this).apply { addView(right); setBackgroundColor(C.PANEL) }, lp(dp(210), MATCH))
        root.addView(body, lp(MATCH, 0, 1f))

        // frames strip
        val strip = hbox().apply { setBackgroundColor(C.HEADER); setPadding(dp(8), dp(6), dp(8), dp(6)); gravity = Gravity.CENTER_VERTICAL }
        strip.addView(iconButton("plus", "New frame", sizeDp = 34) { doc.checkpoint(); doc.addFrame(false); refreshAll() })
        strip.addView(iconButton("copy", "Duplicate frame", sizeDp = 34) { doc.checkpoint(); doc.addFrame(true); refreshAll() }, lp(WRAP, WRAP).margins(dp(4), 0, 0, 0))
        strip.addView(iconButton("trash", "Delete frame", sizeDp = 34) { doc.checkpoint(); doc.deleteFrame(); refreshAll() }, lp(WRAP, WRAP).margins(dp(4), 0, 0, 0))
        strip.addView(iconButton("back", "Move left", sizeDp = 34) { doc.moveFrame(-1); refreshAll() }, lp(WRAP, WRAP).margins(dp(4), 0, 0, 0))
        strip.addView(iconButton("forward", "Move right", sizeDp = 34) { doc.moveFrame(1); refreshAll() }, lp(WRAP, WRAP).margins(dp(4), 0, dp(8), 0))
        framesBox = hbox()
        strip.addView(HorizontalScrollView(this).apply { addView(framesBox); isHorizontalScrollBarEnabled = false }, lp(0, WRAP, 1f))
        root.addView(strip, lp(MATCH, dp(76)))
        setContentView(root)
        pick(color); renderPalette(); refreshAll(); refreshTools()
    }

    private fun pick(c: Int) {
        color = c
        swatch.background = round(c, dp(8).toFloat(), 1, C.BORDER)
        recent.remove(c); recent.add(0, c); while (recent.size > 8) recent.removeAt(recent.size - 1)
        if (::paletteBox.isInitialized) renderPalette()
    }

    private fun renderPalette() {
        paletteBox.removeAllViews()
        val cols = PixelDoc.PALETTES[palette] ?: return
        val all = (listOf(0) + cols + recent).distinct()
        all.chunked(6).forEach { rowCols ->
            val row = hbox()
            rowCols.forEach { c ->
                row.addView(View(this).apply {
                    background = if (c == 0) round(0xFF333333.toInt(), dp(4).toFloat(), 1, C.RED) else round(c, dp(4).toFloat(), if (c == color) 2 else 0, C.ACCENT)
                    setOnClickListener { pick(c) }
                }, lp(0, dp(26), 1f).margins(dp(1), dp(1), dp(1), dp(1)))
            }
            repeat(6 - rowCols.size) { row.addView(View(this), lp(0, dp(26), 1f)) }
            paletteBox.addView(row, lp(MATCH, WRAP))
        }
    }

    private fun refreshTools() { toolButtons.forEach { (t, b) -> b.setBg(if (t == tool) C.ACCENT else C.PANEL2); b.setIconTint(t.icon, if (t == tool) 0xFF000000.toInt() else C.TEXT) } }

    private fun refreshAll() {
        titleText.text = "$fileName  ·  ${doc.width}×${doc.height}  ·  frame ${doc.frame + 1}/${doc.frames.size}  ·  layer ${doc.layer + 1}"
        framesBox.removeAllViews()
        for (i in doc.frames.indices) {
            val bmp = bitmapOf(doc.composite(i), doc.width, doc.height)
            val v = android.widget.ImageView(this).apply {
                setImageBitmap(bmp); scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                background = round(if (i == doc.frame) C.SEL else C.PANEL2, dp(6).toFloat(), if (i == doc.frame) 2 else 0, C.ACCENT)
                setPadding(dp(4), dp(4), dp(4), dp(4))
                setOnClickListener { doc.frame = i; refreshAll() }
            }
            framesBox.addView(v, lp(dp(58), dp(58)).margins(0, 0, dp(6), 0))
        }
        canvasView.invalidate()
    }

    private fun bitmapOf(px: IntArray, w: Int, h: Int): Bitmap = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)

    private fun newDialog() {
        val sizes = arrayOf("16 × 16", "24 × 24", "32 × 32", "48 × 48", "64 × 64", "128 × 128", "32 × 48 (character)", "64 × 32 (wide)")
        val dims = listOf(16 to 16, 24 to 24, 32 to 32, 48 to 48, 64 to 64, 128 to 128, 32 to 48, 64 to 32)
        MaterialAlertDialogBuilder(this).setTitle("New sprite").setItems(sizes) { _, i ->
            doc.checkpoint()
            doc.load(JSONObject().put("w", dims[i].first).put("h", dims[i].second))
            fileName = project?.uniqueAssetName("Sprite.png")?.removeSuffix(".png") ?: "Sprite"
            refreshAll()
        }.show()
    }

    private fun openDialog() {
        val p = project ?: run { Toast.makeText(this, "Open the studio from a project to load files", Toast.LENGTH_SHORT).show(); return }
        val files = p.listAssets().filter { it.endsWith(".spx") || it.endsWith(".png") }.sorted()
        if (files.isEmpty()) { Toast.makeText(this, "No sprites yet", Toast.LENGTH_SHORT).show(); return }
        MaterialAlertDialogBuilder(this).setTitle("Open sprite").setItems(files.toTypedArray()) { _, i -> open(files[i]); refreshAll() }.show()
    }

    private fun open(name: String) {
        val p = project ?: return
        try {
            if (name.endsWith(".spx")) { doc.load(JSONObject(p.readAsset(name) ?: return)); fileName = name.removeSuffix(".spx"); return }
            val anim = p.readAsset(name.removeSuffix(".png") + ".anim")?.let { try { JSONObject(it).optInt("columns", 1) } catch (_: Exception) { 1 } } ?: 1
            val bytes = p.assetFile(name).readBytes()
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val (w, h, px) = if (bmp != null) Triple(bmp.width, bmp.height, IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) })
                else PngEncoder.decode(bytes) ?: return
            if (w > 512 || h > 512) { Toast.makeText(this, "Image too large for pixel editing (max 512)", Toast.LENGTH_SHORT).show(); return }
            doc.importSheet(w, h, px, anim)
            fileName = name.removeSuffix(".png")
        } catch (e: Exception) { Toast.makeText(this, "Could not open: ${e.message}", Toast.LENGTH_SHORT).show() }
    }

    private fun save() {
        val p = project ?: run { Toast.makeText(this, "No project open", Toast.LENGTH_SHORT).show(); return }
        val nameField = field(fileName)
        MaterialAlertDialogBuilder(this).setTitle("Save sprite").setView(vbox().apply { setPadding(dp(20), dp(8), dp(20), 0); addView(nameField) })
            .setPositiveButton("Save") { _, _ ->
                fileName = nameField.text.toString().trim().ifBlank { "Sprite" }.replace(Regex("[^A-Za-z0-9 _\\-]"), "")
                p.writeAsset("$fileName.spx", doc.toJson().toString())
                p.assetFile("$fileName.png").writeBytes(doc.sheetPng())
                if (doc.frames.size > 1) p.writeAsset("$fileName.anim", doc.animClip("$fileName.png").toJson().toString(2))
                Toast.makeText(this, "Saved $fileName.png" + if (doc.frames.size > 1) " + $fileName.anim (${doc.frames.size} frames)" else "", Toast.LENGTH_LONG).show()
                refreshAll()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun effectsDialog() {
        val items = arrayOf("Flip horizontal", "Flip vertical", "Rotate 90°", "Outline (current color)", "Drop shadow", "Shift left", "Shift right", "Shift up", "Shift down",
            "Replace color under… (pick first)", "Clear layer", "Resize canvas ×2", "Resize canvas ÷2")
        MaterialAlertDialogBuilder(this).setTitle("Effects").setItems(items) { _, i ->
            doc.checkpoint()
            when (i) {
                0 -> doc.flipH(); 1 -> doc.flipV(); 2 -> doc.rotate90(); 3 -> doc.outline(color); 4 -> doc.dropShadow()
                5 -> doc.shift(-1, 0); 6 -> doc.shift(1, 0); 7 -> doc.shift(0, -1); 8 -> doc.shift(0, 1)
                9 -> recent.getOrNull(1)?.let { doc.replaceColor(it, color) }
                10 -> doc.clear()
                11 -> if (doc.width <= 256) scale2x()
                12 -> if (doc.width >= 16) doc.resize(doc.width / 2, doc.height / 2)
            }
            refreshAll()
        }.show()
    }

    private fun scale2x() {
        val w = doc.width; val h = doc.height
        val copies = doc.frames.map { f -> f.layers.map { it.px.copyOf() } }
        doc.resize(w * 2, h * 2)
        doc.frames.forEachIndexed { fi, f -> f.layers.forEachIndexed { li, l -> val src = copies[fi][li]; for (y in 0 until h * 2) for (x in 0 until w * 2) l.px[y * w * 2 + x] = src[(y / 2) * w + x / 2] } }
    }

    private fun layersDialog() {
        val box = vbox().apply { setPadding(dp(16), dp(8), dp(16), 0) }
        lateinit var dlg: androidx.appcompat.app.AlertDialog
        fun render() {
            box.removeAllViews()
            val layers = doc.frames[doc.frame].layers
            for (i in layers.indices.reversed()) {
                val l = layers[i]
                val row = hbox().apply { gravity = Gravity.CENTER_VERTICAL; background = round(if (i == doc.layer) C.SEL else C.PANEL2, dp(8).toFloat()); setPadding(dp(8), dp(4), dp(4), dp(4)) }
                row.addView(label(l.name, 13f, C.TEXT, i == doc.layer), lp(0, WRAP, 1f))
                row.addView(iconButton(if (l.visible) "eye" else "eye_off", "Visible", sizeDp = 32) { doc.frames.forEach { f -> f.layers.getOrNull(i)?.visible = !l.visible }; render(); canvasView.invalidate() })
                row.setOnClickListener { doc.layer = i; render(); refreshAll() }
                box.addView(row, lp(MATCH, WRAP).margins(0, dp(2), 0, dp(2)))
            }
            val actions = hbox()
            actions.addView(button("+ Layer") { doc.checkpoint(); doc.addLayer(); render(); refreshAll() }, lp(0, WRAP, 1f).margins(0, dp(6), dp(4), 0))
            actions.addView(button("Merge down") { doc.checkpoint(); doc.mergeDown(); render(); refreshAll() }, lp(0, WRAP, 1f).margins(0, dp(6), dp(4), 0))
            actions.addView(button("Delete") { doc.checkpoint(); doc.deleteLayer(); render(); refreshAll() }, lp(0, WRAP, 1f).margins(0, dp(6), 0, 0))
            box.addView(actions)
        }
        render()
        dlg = MaterialAlertDialogBuilder(this).setTitle("Layers").setView(box).setPositiveButton("Done", null).show()
    }

    /** Zoomable, pannable pixel canvas with checkerboard, onion skin and grid. */
    @SuppressLint("ClickableViewAccessibility")
    inner class PixelCanvas(ctx: Context) : View(ctx) {
        private val paint = Paint().apply { isFilterBitmap = false }
        private val grid = Paint().apply { color = 0x33FFFFFF; strokeWidth = 1f }
        private var zoom = 0f
        private var ox = 0f; private var oy = 0f
        private var startX = -1; private var startY = -1
        private var lastX = -1; private var lastY = -1
        private var snapshot: IntArray? = null
        private var pinchDist = 0f; private var pinchMidX = 0f; private var pinchMidY = 0f; private var multi = false
        private fun fit() { zoom = minOf(width * 0.9f / doc.width, height * 0.9f / doc.height).coerceAtLeast(1f); ox = (width - doc.width * zoom) / 2; oy = (height - doc.height * zoom) / 2 }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { fit() }

        override fun onDraw(c: Canvas) {
            if (zoom <= 0f) fit()
            c.drawColor(C.BG)
            val dst = RectF(ox, oy, ox + doc.width * zoom, oy + doc.height * zoom)
            // checkerboard tiled
            val tile = zoom * 2
            val pb = Paint().apply { color = 0xFF262626.toInt() }
            var yy = 0
            while (yy * tile < dst.height()) { var xx = 0; while (xx * tile < dst.width()) { if ((xx + yy) % 2 == 0) c.drawRect(ox + xx * tile, oy + yy * tile, minOf(dst.right, ox + (xx + 1) * tile), minOf(dst.bottom, oy + (yy + 1) * tile), pb); xx++ }; yy++ }
            if (onion && doc.frame > 0) {
                paint.alpha = 70; c.drawBitmap(bitmapOf(doc.composite(doc.frame - 1), doc.width, doc.height), null, dst, paint); paint.alpha = 255
            }
            c.drawBitmap(bitmapOf(doc.composite(), doc.width, doc.height), null, dst, paint)
            if (zoom >= 6f) {
                for (x in 0..doc.width) c.drawLine(ox + x * zoom, oy, ox + x * zoom, dst.bottom, grid)
                for (y in 0..doc.height) c.drawLine(ox, oy + y * zoom, dst.right, oy + y * zoom, grid)
            }
            val border = Paint().apply { style = Paint.Style.STROKE; color = C.BORDER; strokeWidth = 2f }
            c.drawRect(dst, border)
        }

        private fun cell(e: MotionEvent): Pair<Int, Int> = ((e.x - ox) / zoom).toInt() to ((e.y - oy) / zoom).toInt()

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.pointerCount >= 2) {
                val dx = e.getX(0) - e.getX(1); val dy = e.getY(0) - e.getY(1)
                val d = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                val mx = (e.getX(0) + e.getX(1)) / 2; val my = (e.getY(0) + e.getY(1)) / 2
                if (!multi || e.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    // cancel the stroke that started with the first finger
                    snapshot?.let { s -> s.copyInto(doc.cur) }
                    if (!multi) { multi = true }
                    pinchDist = d; pinchMidX = mx; pinchMidY = my
                } else if (pinchDist > 0f) {
                    val nz = (zoom * d / pinchDist).coerceIn(1f, 80f)
                    ox = mx - (pinchMidX - ox) * nz / zoom; oy = my - (pinchMidY - oy) * nz / zoom
                    zoom = nz; pinchDist = d; pinchMidX = mx; pinchMidY = my
                }
                invalidate(); return true
            }
            if (multi) { if (e.actionMasked == MotionEvent.ACTION_UP) { multi = false; snapshot = null }; return true }
            val (x, y) = cell(e)
            val drawColor = if (tool == Tool.ERASER) 0 else color
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (tool == Tool.PICKER) { val c = doc.composite()[(y.coerceIn(0, doc.height - 1)) * doc.width + x.coerceIn(0, doc.width - 1)]; if (c != 0) pick(c); tool = Tool.PENCIL; refreshTools(); return true }
                    doc.checkpoint()
                    snapshot = doc.cur.copyOf()
                    startX = x; startY = y; lastX = x; lastY = y
                    when (tool) {
                        Tool.PENCIL, Tool.ERASER -> doc.set(x, y, drawColor, brush)
                        Tool.FILL -> doc.fill(x, y, drawColor)
                        else -> {}
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (x == lastX && y == lastY) return true
                    when (tool) {
                        Tool.PENCIL, Tool.ERASER -> doc.line(lastX, lastY, x, y, drawColor, brush)
                        Tool.LINE, Tool.RECT, Tool.RECT_FILL, Tool.ELLIPSE -> {
                            snapshot?.copyInto(doc.cur)
                            when (tool) {
                                Tool.LINE -> doc.line(startX, startY, x, y, drawColor, brush)
                                Tool.RECT -> doc.rect(startX, startY, x, y, drawColor, false)
                                Tool.RECT_FILL -> doc.rect(startX, startY, x, y, drawColor, true)
                                else -> doc.ellipse(startX, startY, x, y, drawColor, false)
                            }
                        }
                        Tool.MOVE -> { doc.shift(x - lastX, y - lastY) }
                        else -> {}
                    }
                    lastX = x; lastY = y
                }
                MotionEvent.ACTION_UP -> { snapshot = null; refreshAll() }
            }
            invalidate()
            return true
        }
    }

    /** Looping animation preview of all frames. */
    inner class PreviewView(ctx: Context) : View(ctx) {
        private val h = Handler(Looper.getMainLooper())
        private var f = 0
        private val paint = Paint().apply { isFilterBitmap = false }
        private val tick = object : Runnable { override fun run() { f = (f + 1) % doc.frames.size.coerceAtLeast(1); invalidate(); h.postDelayed(this, (1000 / doc.fps.coerceIn(1f, 60f)).toLong()) } }
        override fun onAttachedToWindow() { super.onAttachedToWindow(); h.post(tick) }
        override fun onDetachedFromWindow() { h.removeCallbacks(tick); super.onDetachedFromWindow() }
        override fun onDraw(c: Canvas) {
            c.drawColor(0xFF1A1A1A.toInt())
            val fi = f.coerceIn(0, doc.frames.size - 1)
            val s = minOf(width / doc.width.toFloat(), height / doc.height.toFloat())
            val w = doc.width * s; val hh = doc.height * s
            c.drawBitmap(bitmapOf(doc.composite(fi), doc.width, doc.height), null, RectF((width - w) / 2, (height - hh) / 2, (width + w) / 2, (height + hh) / 2), paint)
        }
    }
}
