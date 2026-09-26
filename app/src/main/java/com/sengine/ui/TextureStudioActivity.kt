package com.sengine.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sengine.engine.texture.TextureGen
import com.sengine.project.Project
import com.sengine.project.ProjectManager
import java.util.concurrent.Executors

/** Texture Studio: 30 procedural, seamlessly tiling material generators with live tiled preview and normal maps. */
class TextureStudioActivity : AppCompatActivity() {
    private var project: Project? = null
    private val params = TextureGen.Params("Bricks", 128)
    private var tiles = 2
    private var normal = false
    private var bitmap: Bitmap? = null
    private lateinit var preview: View
    private lateinit var info: TextView
    private lateinit var colorA: View
    private lateinit var colorB: View
    private val styleButtons = HashMap<String, TextView>()
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = intent.getStringExtra("project")?.let { try { ProjectManager.open(this, it) } catch (_: Exception) { null } }
        TextureGen.defaults(params.style).let { params.colorA = it.first; params.colorB = it.second }
        val root = vbox().apply { setBackgroundColor(C.BG) }
        val bar = hbox().apply { setBackgroundColor(C.HEADER); setPadding(dp(6), dp(4), dp(8), dp(4)); gravity = Gravity.CENTER_VERTICAL }
        bar.addView(iconButton("back", "Back", sizeDp = 36) { finish() })
        bar.addView(label("Texture Studio", 16f, C.TEXT, true).apply { setPadding(dp(10), 0, 0, 0) }, lp(0, WRAP, 1f))
        bar.addView(iconTextButton("refresh", "Randomize", C.PANEL2) { params.seed = (Math.random() * 99999).toInt(); regen() }, lp(WRAP, WRAP).margins(0, 0, dp(6), 0))
        bar.addView(iconTextButton("save", "Save PNG", C.ACCENT, 0xFF000000.toInt()) { save() })
        root.addView(bar, lp(MATCH, WRAP))

        val body = hbox()
        // styles grid
        val grid = GridLayout(this).apply { columnCount = 2; setPadding(dp(6), dp(6), dp(6), dp(6)) }
        for (st in TextureGen.STYLES) {
            val b = label(st, 12f, C.TEXT).apply {
                gravity = Gravity.CENTER; setPadding(dp(6), dp(9), dp(6), dp(9))
                setOnClickListener { params.style = st; TextureGen.defaults(st).let { d -> params.colorA = d.first; params.colorB = d.second }; syncColors(); regen() }
            }
            styleButtons[st] = b
            grid.addView(b, GridLayout.LayoutParams().apply { width = dp(92); setMargins(dp(2), dp(2), dp(2), dp(2)) })
        }
        body.addView(ScrollView(this).apply { addView(grid); setBackgroundColor(C.PANEL) }, lp(dp(206), MATCH))

        preview = object : View(this) {
            val p = Paint().apply { isFilterBitmap = true }
            override fun onDraw(c: Canvas) {
                c.drawColor(C.BG)
                val b = bitmap ?: return
                val s = minOf(width, height) * 0.92f
                val cell = s / tiles
                val x0 = (width - s) / 2; val y0 = (height - s) / 2
                for (j in 0 until tiles) for (i in 0 until tiles) c.drawBitmap(b, null, RectF(x0 + i * cell, y0 + j * cell, x0 + (i + 1) * cell, y0 + (j + 1) * cell), p)
            }
        }
        body.addView(preview, lp(0, MATCH, 1f))

        val side = vbox().apply { setPadding(dp(12), dp(10), dp(12), dp(10)) }
        info = label("", 12f, C.DIM)
        side.addView(info)
        side.addView(sectionHeader("palette", "Colors"), lp(MATCH, WRAP).margins(0, dp(8), 0, dp(4)))
        val cRow = hbox()
        colorA = View(this).apply { setOnClickListener { ColorPickerDialog.show(this@TextureStudioActivity, params.colorA) { params.colorA = it; syncColors(); regen() } } }
        colorB = View(this).apply { setOnClickListener { ColorPickerDialog.show(this@TextureStudioActivity, params.colorB) { params.colorB = it; syncColors(); regen() } } }
        cRow.addView(colorA, lp(0, dp(40), 1f).margins(0, 0, dp(6), 0)); cRow.addView(colorB, lp(0, dp(40), 1f))
        side.addView(cRow)
        side.addView(button("Swap colors") { val t = params.colorA; params.colorA = params.colorB; params.colorB = t; syncColors(); regen() }, lp(MATCH, WRAP).margins(0, dp(6), 0, 0))
        fun slider(title: String, max: Int, value: Int, on: (Int) -> Unit) {
            val t = label("$title: $value", 12f, C.TEXT)
            side.addView(t, lp(MATCH, WRAP).margins(0, dp(10), 0, 0))
            side.addView(SeekBar(this).apply {
                this.max = max; progress = value
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) { t.text = "$title: $v"; if (u) on(v) }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) { regen() }
                })
            })
        }
        slider("Scale", 16, params.scale.toInt()) { params.scale = it.coerceAtLeast(1).toFloat() }
        slider("Roughness", 100, (params.roughness * 100).toInt()) { params.roughness = it / 100f }
        slider("Contrast", 200, (params.contrast * 100).toInt()) { params.contrast = it.coerceAtLeast(10) / 100f }
        slider("Preview tiles", 4, tiles) { tiles = it.coerceAtLeast(1); preview.invalidate() }
        side.addView(sectionHeader("grid", "Size"), lp(MATCH, WRAP).margins(0, dp(10), 0, dp(4)))
        val sizes = hbox()
        for (s in listOf(64, 128, 256, 512)) sizes.addView(button("$s") { params.size = s; regen() }, lp(0, WRAP, 1f).margins(dp(1), 0, dp(1), 0))
        side.addView(sizes)
        val nBtn = button("Normal map: off") {}
        nBtn.setOnClickListener { normal = !normal; nBtn.text = "Normal map: ${if (normal) "on" else "off"}"; regen() }
        side.addView(nBtn, lp(MATCH, WRAP).margins(0, dp(10), 0, 0))
        body.addView(ScrollView(this).apply { addView(side); setBackgroundColor(C.PANEL) }, lp(dp(230), MATCH))
        root.addView(body, lp(MATCH, 0, 1f))
        setContentView(root)
        syncColors(); regen()
    }

    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }

    private fun syncColors() {
        colorA.background = round(params.colorA, dp(8).toFloat(), 1, C.BORDER)
        colorB.background = round(params.colorB, dp(8).toFloat(), 1, C.BORDER)
        styleButtons.forEach { (s, b) -> b.background = round(if (s == params.style) C.ACCENT else C.PANEL2, dp(8).toFloat()); b.setTextColor(if (s == params.style) 0xFF000000.toInt() else C.TEXT) }
    }

    private fun regen() {
        val g = ++generation
        val snap = TextureGen.Params(params.style, params.size, params.colorA, params.colorB, params.scale, params.seed, params.contrast, params.roughness)
        val wantNormal = normal
        info.text = "Generating ${snap.style}…"
        worker.execute {
            val t0 = System.currentTimeMillis()
            var px = TextureGen.generate(snap)
            if (wantNormal) px = TextureGen.normalMap(px, snap.size)
            val bmp = Bitmap.createBitmap(px, snap.size, snap.size, Bitmap.Config.ARGB_8888)
            val ms = System.currentTimeMillis() - t0
            runOnUiThread { if (g == generation) { bitmap = bmp; info.text = "${snap.style} · ${snap.size}px · seed ${snap.seed} · ${ms}ms · seamless"; preview.invalidate() } }
        }
    }

    private fun save() {
        val p = project ?: run { Toast.makeText(this, "Open from a project to save textures", Toast.LENGTH_SHORT).show(); return }
        val nameField = field(p.uniqueAssetName("${params.style}.png").removeSuffix(".png"))
        MaterialAlertDialogBuilder(this).setTitle("Save texture").setView(vbox().apply { setPadding(dp(20), dp(8), dp(20), 0); addView(nameField) })
            .setPositiveButton("Save") { _, _ ->
                val n = nameField.text.toString().trim().ifBlank { params.style }
                worker.execute {
                    val px = TextureGen.generate(params)
                    p.assetFile("$n.png").writeBytes(com.sengine.engine.texture.PngEncoder.encode(params.size, params.size, px))
                    if (normal) p.assetFile("${n}_normal.png").writeBytes(com.sengine.engine.texture.PngEncoder.encode(params.size, params.size, TextureGen.normalMap(px, params.size)))
                    runOnUiThread { Toast.makeText(this, "Saved $n.png" + if (normal) " + ${n}_normal.png" else "", Toast.LENGTH_SHORT).show() }
                }
            }.setNegativeButton("Cancel", null).show()
    }
}
