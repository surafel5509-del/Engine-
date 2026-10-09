package com.sengine.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sengine.engine.core.AssetKind
import com.sengine.engine.core.TileLayer
import com.sengine.engine.core.Tilemap
import com.sengine.engine.texture.PngEncoder
import com.sengine.project.Project
import com.sengine.project.ProjectManager
import org.json.JSONObject
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Touch-first tilemap editor for `.tmap` assets: paint / erase / fill / rectangle / pick tiles on a
 * pan-and-zoom canvas, manage layers, slice any texture as a tileset and generate a starter tileset
 * offline. The scene editor renders the same asset live through the `TileMap` component.
 */
class TilemapEditorActivity : AppCompatActivity() {

    private lateinit var project: Project
    private var asset: String? = null
    private var doc = Tilemap()
    private var saved = ""
    private var atlas: Bitmap? = null

    private var layerIndex = 0
    private var activeTile = 0
    private var tool = BRUSH

    private lateinit var titleText: TextView
    private lateinit var infoText: TextView
    private lateinit var canvas: MapCanvas
    private lateinit var palette: LinearLayout
    private val toolButtons = HashMap<Int, ImageView>()
    private val undo = ArrayDeque<Array<IntArray>>()
    private val redo = ArrayDeque<Array<IntArray>>()

    private companion object {
        const val BRUSH = 0
        const val ERASE = 1
        const val RECT = 2
        const val PICK = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = ProjectManager.open(this, intent.getStringExtra("project") ?: run { finish(); return })
        asset = intent.getStringExtra("asset")

        val root = vbox().apply { setBackgroundColor(C.BG) }

        // ---- header
        val bar = hbox().apply { setBackgroundColor(C.HEADER); setPadding(dp(6), dp(4), dp(6), dp(4)) }
        bar.addView(iconButton("back", "Back") { onBackPressedDispatcher.onBackPressed() })
        titleText = label("", 15f, C.TEXT, true).apply { setPadding(dp(10), 0, dp(10), 0); isSingleLine = true }
        bar.addView(titleText, lp(0, WRAP, 1f))
        bar.addView(button("Open…") { openDialog() }.apply { textSize = 12f }, lp(WRAP, WRAP).margins(dp(3), 0, dp(3), 0))
        bar.addView(button("New") { newDialog() }.apply { textSize = 12f }, lp(WRAP, WRAP).margins(dp(3), 0, dp(3), 0))
        bar.addView(button("Save", C.ACCENT, 0xFF000000.toInt()) { save() }.apply { textSize = 12f }, lp(WRAP, WRAP).margins(dp(3), 0, 0, 0))
        root.addView(bar, lp(MATCH, WRAP))

        // ---- tools
        val tools = hbox().apply { setBackgroundColor(C.PANEL); setPadding(dp(6), dp(4), dp(6), dp(4)) }
        fun toolBtn(id: Int, icon: String, name: String) {
            val b = iconButton(icon, name, C.TEXT, C.PANEL2, 36) { setTool(id) }
            toolButtons[id] = b
            tools.addView(b, lp(dp(36), dp(36)).margins(dp(2), 0, dp(2), 0))
        }
        toolBtn(BRUSH, "brush", "Paint tiles")
        toolBtn(ERASE, "eraser", "Erase tiles")
        toolBtn(RECT, "collider", "Rectangle fill")
        toolBtn(PICK, "pipette", "Pick tile under finger")
        tools.addView(View(this).apply { setBackgroundColor(C.BORDER) }, lp(dp(1), dp(22)).margins(dp(6), 0, dp(6), 0))
        tools.addView(iconButton("bucket", "Fill current layer with the selected tile") { fillLayer() }, lp(dp(36), dp(36)).margins(dp(2), 0, dp(2), 0))
        tools.addView(iconButton("trash", "Clear current layer") { clearLayer() }, lp(dp(36), dp(36)).margins(dp(2), 0, dp(2), 0))
        tools.addView(View(this).apply { setBackgroundColor(C.BORDER) }, lp(dp(1), dp(22)).margins(dp(6), 0, dp(6), 0))
        tools.addView(iconButton("undo", "Undo") { undoStroke() }, lp(dp(36), dp(36)).margins(dp(2), 0, dp(2), 0))
        tools.addView(iconButton("redo", "Redo") { redoStroke() }, lp(dp(36), dp(36)).margins(dp(2), 0, dp(2), 0))
        tools.addView(iconButton("grid", "Toggle grid") { canvas.showGrid = !canvas.showGrid; canvas.invalidate(); refreshInfo() }, lp(dp(36), dp(36)).margins(dp(2), 0, dp(2), 0))
        tools.addView(iconButton("layers", "Layers") { layersDialog() }, lp(dp(36), dp(36)).margins(dp(2), 0, dp(2), 0))
        tools.addView(iconButton("frame", "Resize map") { resizeDialog() }, lp(dp(36), dp(36)).margins(dp(2), 0, dp(2), 0))
        tools.addView(iconButton("image", "Tileset texture") { chooseTileset() }, lp(dp(36), dp(36)).margins(dp(2), 0, dp(2), 0))
        tools.addView(iconButton("palette", "Generate starter tileset") { makeStarterTileset() }, lp(dp(36), dp(36)).margins(dp(2), 0, dp(2), 0))
        tools.addView(iconButton("target", "Fit map") { canvas.fit(); canvas.invalidate() }, lp(dp(36), dp(36)).margins(dp(2), 0, dp(2), 0))
        root.addView(HorizontalScrollView(this).apply { addView(tools); isHorizontalScrollBarEnabled = false }, lp(MATCH, WRAP))

        // ---- canvas + palette
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        canvas = MapCanvas(this)
        canvas.onStroke = { cells, erase -> paintCells(cells, erase) }
        canvas.onPicked = { tile -> pickTile(tile) }
        body.addView(canvas, lp(MATCH, 0, 1f))
        val pal = hbox().apply { setBackgroundColor(C.PANEL); setPadding(dp(6), dp(4), dp(6), dp(4)) }
        palette = hbox()
        pal.addView(HorizontalScrollView(this).apply { addView(palette); isHorizontalScrollBarEnabled = false }, lp(0, WRAP, 1f))
        infoText = label("", 11f, C.DIM).apply { setPadding(dp(8), 0, 0, 0) }
        val palWrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        palWrap.addView(pal, lp(MATCH, WRAP))
        palWrap.addView(infoText, lp(MATCH, WRAP))
        body.addView(palWrap, lp(MATCH, WRAP))
        root.addView(body, lp(MATCH, 0, 1f))
        setContentView(root)

        setTool(BRUSH)

        val a = asset
        if (a != null && AssetKind.of(a) == AssetKind.TILEMAP) load(a) else {
            val first = project.listAssets(AssetKind.TILEMAP).firstOrNull()
            if (first != null) load(first) else newDoc("Level1")
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (doc.toJson().toString() == saved || asset == null && doc.isEmpty()) { finish(); return }
                MaterialAlertDialogBuilder(this@TilemapEditorActivity)
                    .setTitle("Unsaved changes").setMessage("Save this tilemap?")
                    .setPositiveButton("Save") { _, _ -> if (save()) finish() }
                    .setNegativeButton("Discard") { _, _ -> finish() }
                    .setNeutralButton("Cancel", null).show()
            }
        })
    }

    // ================================================================== document

    private fun load(name: String) {
        asset = name
        doc = try { Tilemap.fromJson(JSONObject(project.readAsset(name) ?: "{}")) } catch (_: Exception) { Tilemap.starter() }
        saved = doc.toJson().toString()
        layerIndex = 0
        activeTile = 0
        undo.clear(); redo.clear()
        loadAtlas()
        refreshAll()
    }

    private fun newDoc(base: String) {
        val name = project.uniqueAssetName("$base.${Tilemap.EXT}")
        val tileset = project.listAssets(AssetKind.TEXTURE).firstOrNull() ?: ""
        doc = Tilemap.starter(tileset)
        project.writeAsset(name, doc.toJson().toString(2))
        load(name)
    }

    private fun save(): Boolean {
        var name = asset
        if (name == null) {
            name = project.uniqueAssetName("Level.${Tilemap.EXT}")
            asset = name
        }
        project.writeAsset(name, doc.toJson().toString(2))
        saved = doc.toJson().toString()
        titleText.text = "Tilemap: $name"
        toast("Saved $name")
        return true
    }

    private fun loadAtlas() {
        atlas = if (doc.tileset.isBlank()) null
        else try { BitmapFactory.decodeFile(project.assetFile(doc.tileset).absolutePath) } catch (_: Throwable) { null }
    }

    private fun atlasCols() = ((atlas?.width ?: 0) / doc.tileW.coerceAtLeast(1)).coerceAtLeast(1)
    private fun atlasRows() = ((atlas?.height ?: 0) / doc.tileH.coerceAtLeast(1)).coerceAtLeast(1)

    private fun currentLayer(): TileLayer? = doc.layers.getOrNull(layerIndex)

    private fun refreshAll() {
        canvas.set(doc, atlas)
        canvas.invalidate()
        rebuildPalette()
        refreshInfo()
    }

    private fun refreshInfo() {
        val l = currentLayer()
        titleText.text = "Tilemap: " + (asset ?: "(unsaved)")
        val cells = l?.cells?.count { it >= 0 } ?: 0
        infoText.text = "${doc.cols}×${doc.rows}  •  layer ${layerIndex + 1}/${doc.layers.size}" +
            (if (l != null) " '${l.name}'" + (if (l.solid) " solid" else "") else "") +
            "  •  $cells tiles  •  ${atlas?.let { "${it.width}×${it.height} px" } ?: "no tileset"}"
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ================================================================== painting

    private fun snapshot() {
        undo.addLast(doc.layers.map { it.cells.copyOf() }.toTypedArray())
        if (undo.size > 40) undo.removeFirst()
        redo.clear()
    }

    private fun applySnapshot(s: Array<IntArray>) {
        doc.layers.forEachIndexed { i, l -> if (i < s.size) l.cells = s[i].copyOf() }
        canvas.invalidate(); refreshInfo()
    }

    private fun undoStroke() {
        val s = undo.removeLastOrNull() ?: return
        redo.addLast(doc.layers.map { it.cells.copyOf() }.toTypedArray())
        applySnapshot(s)
    }

    private fun redoStroke() {
        val s = redo.removeLastOrNull() ?: return
        undo.addLast(doc.layers.map { it.cells.copyOf() }.toTypedArray())
        applySnapshot(s)
    }

    /** Applies one stroke of [cells] (as `[col, row]`) to the current layer. */
    private fun paintCells(cells: List<IntArray>, erase: Boolean) {
        val l = currentLayer() ?: return
        if (erase) for (c in cells) l.set(c[0], c[1], -1)
        else for (c in cells) {
            val t = activeTile
            if (t >= 0 && t < atlasCols() * atlasRows()) l.set(c[0], c[1], t)
        }
        canvas.invalidate()
        refreshInfo()
    }

    private fun fillLayer() {
        val l = currentLayer() ?: return
        snapshot()
        l.fill(activeTile.coerceAtLeast(-1))
        canvas.invalidate(); refreshInfo()
    }

    private fun clearLayer() {
        val l = currentLayer() ?: return
        snapshot()
        l.clear()
        canvas.invalidate(); refreshInfo()
    }

    private fun pickTile(tile: Int) {
        activeTile = tile
        setTool(BRUSH)
        rebuildPalette()
        refreshInfo()
    }

    private fun setTool(t: Int) {
        tool = t
        canvas.tool = t
        for ((id, b) in toolButtons) b.setBg(if (id == t) C.ACCENT2 else C.PANEL2)
    }

    // ================================================================== palette

    private fun rebuildPalette() {
        palette.removeAllViews()
        val b = atlas
        val erase = label("Erase", 12f, C.TEXT).apply {
            gravity = android.view.Gravity.CENTER
            background = round(if (activeTile < 0) C.ACCENT2 else C.PANEL2, dp(8).toFloat(), 1, C.BORDER)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { pickTile(-1) }
        }
        palette.addView(erase, lp(WRAP, WRAP).margins(dp(3), 0, dp(3), 0))
        if (b == null) {
            palette.addView(label("No tileset — pick a texture with the  icon or generate one", 11f, C.DIM), lp(WRAP, WRAP).margins(dp(6), 0, 0, 0))
            return
        }
        val cols = atlasCols(); val rows = atlasRows()
        for (i in 0 until cols * rows) {
            val col = i % cols; val row = i / cols
            val cw = min(doc.tileW, b.width - col * doc.tileW)
            val ch = min(doc.tileH, b.height - row * doc.tileH)
            if (cw <= 0 || ch <= 0) continue
            val thumb = Bitmap.createBitmap(b, col * doc.tileW, row * doc.tileH, cw, ch)
            val iv = ImageView(this).apply {
                setImageBitmap(thumb)
                scaleType = ImageView.ScaleType.FIT_XY
                background = round(if (activeTile == i) C.ACCENT2 else C.PANEL2, dp(6).toFloat(), if (activeTile == i) dp(2) else 1, C.BORDER)
                setPadding(dp(2), dp(2), dp(2), dp(2))
                contentDescription = "Tile $i"
                setOnClickListener { pickTile(i) }
            }
            palette.addView(iv, lp(dp(46), dp(46)).margins(dp(3), 0, dp(3), 0))
        }
    }

    // ================================================================== dialogs

    private fun openDialog() {
        val items = project.listAssets(AssetKind.TILEMAP)
        if (items.isEmpty()) { toast("No tilemaps yet — tap New"); return }
        MaterialAlertDialogBuilder(this).setTitle("Open tilemap")
            .setItems(items.toTypedArray()) { _, i -> if (confirmDiscard()) load(items[i]) }.show()
    }

    private fun newDialog() {
        if (!confirmDiscard()) return
        val f = field("Level1")
        MaterialAlertDialogBuilder(this).setTitle("New tilemap")
            .setMessage("A blank map with Background and Ground layers.")
            .setView(LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(f, lp(MATCH, WRAP)) })
            .setPositiveButton("Create") { _, _ ->
                val n = f.text.toString().trim().replace(Regex("[^A-Za-z0-9_\\-]"), "").ifBlank { "Level1" }
                newDoc(n)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun confirmDiscard(): Boolean {
        if (doc.toJson().toString() == saved) return true
        MaterialAlertDialogBuilder(this)
            .setTitle("Unsaved changes").setMessage("Save first?")
            .setPositiveButton("Save") { _, _ -> save() }
            .setNegativeButton("Discard", null).show()
        return false
    }

    private fun chooseTileset() {
        val items = project.listAssets(AssetKind.TEXTURE)
        if (items.isEmpty()) { toast("No textures — import an image or generate a starter tileset"); return }
        MaterialAlertDialogBuilder(this).setTitle("Tileset texture")
            .setItems(items.toTypedArray()) { _, i ->
                doc.tileset = items[i]
                guessTileSize()
                loadAtlas()
                refreshAll()
            }.show()
    }

    /** Picks a tile size that divides the texture sensibly (square tiles, 8/16/32/64 px). */
    private fun guessTileSize() {
        val b = atlas ?: try { BitmapFactory.decodeFile(project.assetFile(doc.tileset).absolutePath) } catch (_: Throwable) { null } ?: return
        for (size in intArrayOf(8, 16, 32, 64)) {
            if (b.width % size == 0 && b.height % size == 0) { doc.tileW = size; doc.tileH = size; return }
        }
    }

    private fun resizeDialog() {
        val cf = field(doc.cols.toString(), numeric = true)
        val rf = field(doc.rows.toString(), numeric = true)
        val box = vbox().apply { setPadding(dp(20), dp(8), dp(20), 0) }
        box.addView(label("Columns / rows (cells are kept from the bottom-left)", 11f, C.DIM))
        val row = hbox()
        row.addView(cf, lp(0, WRAP, 1f)); row.addView(label("  ×  ", 13f, C.DIM)); row.addView(rf, lp(0, WRAP, 1f))
        box.addView(row, lp(MATCH, WRAP))
        MaterialAlertDialogBuilder(this).setTitle("Resize map").setView(box)
            .setPositiveButton("Resize") { _, _ ->
                snapshot()
                doc.setSize(cf.text.toString().toIntOrNull() ?: doc.cols, rf.text.toString().toIntOrNull() ?: doc.rows)
                canvas.fit(); refreshAll()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun layersDialog() {
        val labels = doc.layers.mapIndexed { i, l ->
            "${i + 1}. ${l.name}" + (if (l.visible) "" else "  (hidden)") + (if (l.solid) "  solid" else "") +
                "  ·  ${l.cells.count { it >= 0 }} tiles"
        }.toMutableList()
        labels.add("+ Add layer")
        MaterialAlertDialogBuilder(this).setTitle("Layers")
            .setItems(labels.toTypedArray()) { _, i ->
                if (i >= doc.layers.size) {
                    snapshot()
                    doc.layers.add(TileLayer("Layer ${doc.layers.size + 1}", doc.cols, doc.rows))
                    layerIndex = doc.layers.size - 1
                    refreshAll()
                } else {
                    layerIndex = i
                    layerOptionsDialog()
                }
            }.setNeutralButton("Close", null).show()
    }

    private fun layerOptionsDialog() {
        val l = currentLayer() ?: return
        val entries = listOf(
            if (l.visible) "Hide layer" else "Show layer",
            if (l.solid) "Solid off (no collision)" else "Solid on (collision)",
            "Rename…",
            "Set opacity…",
            "Clear layer",
            "Delete layer",
        )
        MaterialAlertDialogBuilder(this).setTitle("Layer ${layerIndex + 1}: ${l.name}")
            .setItems(entries.toTypedArray()) { _, i ->
                when (i) {
                    0 -> { l.visible = !l.visible; refreshAll() }
                    1 -> { l.solid = !l.solid; refreshInfo() }
                    2 -> renameLayer(l)
                    3 -> opacityDialog(l)
                    4 -> clearLayer()
                    5 -> if (doc.layers.size <= 1) toast("A map needs at least one layer") else {
                        snapshot(); doc.layers.removeAt(layerIndex); layerIndex = 0; refreshAll()
                    }
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun renameLayer(l: TileLayer) {
        val f = field(l.name)
        MaterialAlertDialogBuilder(this).setTitle("Rename layer")
            .setView(LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(f, lp(MATCH, WRAP)) })
            .setPositiveButton("OK") { _, _ -> l.name = f.text.toString().ifBlank { l.name }; refreshInfo() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun opacityDialog(l: TileLayer) {
        val f = field(fmt(l.opacity), numeric = true)
        MaterialAlertDialogBuilder(this).setTitle("Layer opacity (0–1)")
            .setView(LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(f, lp(MATCH, WRAP)) })
            .setPositiveButton("OK") { _, _ -> l.opacity = (f.text.toString().toFloatOrNull() ?: 1f).coerceIn(0f, 1f); canvas.invalidate() }
            .setNegativeButton("Cancel", null).show()
    }

    /** Writes a ready-to-use 8×4 tileset PNG so tilemaps can be painted completely offline. */
    private fun makeStarterTileset() {
        val tw = 16; val cols = 8; val rows = 4
        val w = tw * cols; val h = tw * rows
        val px = IntArray(w * h)
        for (t in 0 until cols * rows) {
            val tx = (t % cols) * tw; val ty = (t / cols) * tw
            for (y in 0 until tw) for (x in 0 until tw) px[(ty + y) * w + tx + x] = tilePixel(t, x, y)
        }
        val name = project.uniqueAssetName("Tileset.png")
        try {
            project.assetsDir.mkdirs()
            project.assetFile(name).writeBytes(PngEncoder.encode(w, h, px))
        } catch (e: Exception) { toast("Could not write tileset: ${e.message}"); return }
        doc.tileset = name
        doc.tileW = tw; doc.tileH = tw
        loadAtlas()
        refreshAll()
        toast("Created $name — paint away!")
    }

    /** Deterministic pixel for the generated tileset: solids, liquids, bricks, plank and metal. */
    private fun tilePixel(tile: Int, x: Int, y: Int): Int {
        val noise = ((x * 7 + y * 13 + tile * 31) % 5)
        fun shade(c: Int, k: Int): Int {
            fun ch(s: Int) = (((c shr s) and 0xFF) + k).coerceIn(0, 255)
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
        val edge = x == 0 || y == 0 || x == 15 || y == 15
        return when (tile) {
            0 -> shade(0xFF4C9A2A.toInt(), if (edge) -30 else noise)              // grass
            1 -> shade(0xFF7B5A34.toInt(), if (edge) -25 else noise)              // dirt
            2 -> shade(0xFF8A8F96.toInt(), if (edge) -30 else noise)              // stone
            3 -> shade(0xFFDCC27A.toInt(), if (edge) -25 else noise)              // sand
            4 -> {                                                                // water
                val wave = if ((x + y * 2) % 8 < 2) 18 else 0
                shade(0xFF2F6BD6.toInt(), wave)
            }
            5 -> if ((y % 8) == 0) shade(0xFF9E4A3A.toInt(), -30) else shade(0xFFB85B45.toInt(), noise) // brick
            6 -> if ((x % 8) == 0) shade(0xFF8A6134.toInt(), -30) else shade(0xFFA9763F.toInt(), noise) // plank
            7 -> if ((x + y) % 6 == 0) shade(0xFFB9C3CC.toInt(), 25) else shade(0xFF9AA5AE.toInt(), noise) // metal
            8 -> shade(0xFF3F5D2E.toInt(), if (edge) -25 else noise)              // dark grass
            9 -> if ((x * y) % 11 == 0) shade(0xFFFF7C4D.toInt(), 20) else shade(0xFFC24A22.toInt(), noise) // lava
            10 -> shade(0xFF536DFE.toInt(), if ((x + y) % 5 == 0) 30 else 0)      // crystal
            11 -> shade(0xFF5B4636.toInt(), if (edge) -35 else noise)             // dark wood
            12 -> if (edge) shade(0xFFF5D76E.toInt(), -40) else shade(0xFFF5D76E.toInt(), noise) // gold
            13 -> shade(0xFF37474F.toInt(), if (edge) -20 else noise)             // cobble dark
            14 -> shade(0xFF7E57C2.toInt(), if ((x + y) % 4 == 0) 22 else 0)       // magic
            else -> shade(0xFFE0E0E0.toInt(), -noise)                             // light stone
        }
    }

    // ================================================================== canvas

    @SuppressLint("ViewConstructor")
    inner class MapCanvas(ctx: Context) : View(ctx) {
        var tool = BRUSH
        var showGrid = true
        /** Receives `[col, row]` pairs for a stroke, and whether the stroke erases. */
        var onStroke: ((List<IntArray>, Boolean) -> Unit)? = null
        var onPicked: ((Int) -> Unit)? = null

        private var map: Tilemap? = null
        private var bmp: Bitmap? = null
        private val paint = Paint().apply { isFilterBitmap = false }
        private val line = Paint().apply { color = 0x33FFFFFF; strokeWidth = 1f; style = Paint.Style.STROKE }
        private val gridLine = Paint().apply { color = 0x30FFFFFF; strokeWidth = 1f }
        private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF9AA0A6.toInt(); textSize = 12f * resources.displayMetrics.density
            textAlign = Paint.Align.CENTER
        }
        private val src = Rect()
        private val dst = RectF()

        private var ppt = 32f
        private var panX = 0f
        private var panY = 0f
        private var fitted = false
        private var pinching = false
        private var pinchDist = 0f
        private var lastX = 0f
        private var lastY = 0f
        private var stroking = false
        private var strokingErase = false
        private var strokeCells = ArrayList<IntArray>()
        private var rectStart: IntArray? = null

        fun set(d: Tilemap, b: Bitmap?) {
            map = d; bmp = b
            fitted = false
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            fitted = false
        }

        /** Frames the whole map in the view; ignored until the view has a size. */
        fun fit() {
            val m = map ?: return
            if (m.cols <= 0 || m.rows <= 0 || width <= 0 || height <= 0) return
            val base = 8f * resources.displayMetrics.density
            ppt = max(base, min((width - 40f) / m.cols, (height - 40f) / m.rows))
            panX = (width - m.cols * ppt) / 2f
            panY = (height + m.rows * ppt) / 2f
            fitted = true
        }

        private fun cellAt(x: Float, y: Float): IntArray =
            intArrayOf(floor((x - panX) / ppt).toInt(), floor((panY - y) / ppt).toInt())

        private fun screenX(col: Int) = panX + col * ppt
        private fun screenY(row: Int) = panY - row * ppt

        override fun onDraw(c: Canvas) {
            c.drawColor(0xFF14161A.toInt())
            val m = map ?: return
            if (!fitted) fit()
            drawChecker(c)
            val b = bmp
            for (layer in m.layers) {
                if (!layer.visible) continue
                for (row in 0 until layer.rows) for (col in 0 until layer.cols) {
                    val t = layer.get(col, row)
                    if (t < 0) continue
                    val left = screenX(col); val top = screenY(row + 1)
                    dst.set(left, top, left + ppt, top + ppt)
                    val a = (255 * layer.opacity).toInt().coerceIn(0, 255)
                    if (b != null) {
                        val ac = atlasCols(); val ar = atlasRows()
                        val tc = t % ac; val tr = t / ac
                        if (tr < ar && tc * doc.tileW + doc.tileW <= b.width && tr * doc.tileH + doc.tileH <= b.height) {
                            src.set(tc * doc.tileW, tr * doc.tileH, tc * doc.tileW + doc.tileW, tr * doc.tileH + doc.tileH)
                            paint.alpha = a
                            c.drawBitmap(b, src, dst, paint)
                            paint.alpha = 255
                            continue
                        }
                    }
                    paint.color = 0xFF3A5A8A.toInt()
                    paint.alpha = a
                    c.drawRect(dst, paint)
                    paint.alpha = 255
                }
            }
            // grid
            if (showGrid && ppt >= 6f) {
                for (col in 0..m.cols) c.drawLine(screenX(col), screenY(0), screenX(col), screenY(m.rows), line)
                for (row in 0..m.rows) c.drawLine(screenX(0), screenY(row), screenX(m.cols), screenY(row), line)
            }
            // map bounds
            line.color = 0xAAFFFFFF.toInt()
            c.drawRect(screenX(0), screenY(m.rows), screenX(m.cols), screenY(0), line)
            line.color = 0x33FFFFFF.toInt()
            // pending rectangle preview
            rectStart?.let { s ->
                val e = cellAt(lastX, lastY)
                val x0 = min(s[0], e[0]); val x1 = max(s[0], e[0])
                val y0 = min(s[1], e[1]); val y1 = max(s[1], e[1])
                paint.color = 0x554C8DFF
                c.drawRect(screenX(x0), screenY(y1 + 1), screenX(x1 + 1), screenY(y0), paint)
            }
            if (b == null && m.layers.all { it.isEmpty() }) {
                c.drawText("Pick a tileset (  button) or generate one, then drag to paint", width / 2f, height / 2f, hint)
            }
        }

        private fun drawChecker(c: Canvas) {
            val size = 16f * resources.displayMetrics.density
            var y = 0f; var yi = 0
            while (y < height) {
                var x = if (yi % 2 == 0) 0f else size
                while (x < width) {
                    paint.color = 0xFF1B1E23.toInt()
                    c.drawRect(x, y, x + size, y + size, paint)
                    x += size * 2
                }
                y += size; yi++
            }
        }

        private fun paintAt(x: Float, y: Float) {
            val cell = cellAt(x, y)
            val m = map ?: return
            if (cell[0] < 0 || cell[1] < 0 || cell[0] >= m.cols || cell[1] >= m.rows) return
            if (strokeCells.any { it[0] == cell[0] && it[1] == cell[1] }) return
            strokeCells.add(cell)
            onStroke?.invoke(listOf(cell), strokingErase)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            val m = map ?: return true
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = e.x; lastY = e.y
                    if (tool == PICK) {
                        onPicked?.invoke(m.at(cellAt(e.x, e.y)[0], cellAt(e.x, e.y)[1]))
                        return true
                    }
                    if (tool == RECT) { rectStart = cellAt(e.x, e.y); return true }
                    stroking = true
                    strokingErase = tool == ERASE
                    strokeCells = ArrayList()
                    snapshot() // one undo step per stroke
                    paintAt(e.x, e.y)
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (e.pointerCount >= 2) {
                        pinching = true
                        pinchDist = spread(e)
                        lastX = midX(e); lastY = midY(e)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (pinching && e.pointerCount >= 2) {
                        val d = spread(e)
                        if (pinchDist > 4f && d > 4f) {
                            val k = (d / pinchDist).coerceIn(0.5f, 2f)
                            val mx = midX(e); val my = midY(e)
                            panX = mx + (panX - mx) * k
                            panY = my + (panY - my) * k
                            ppt = (ppt * k).coerceIn(3f, 240f)
                            pinchDist = d
                        }
                        val mx = midX(e); val my = midY(e)
                        panX += mx - lastX; panY += my - lastY
                        lastX = mx; lastY = my
                        invalidate()
                    } else if (stroking) {
                        paintAt(e.x, e.y)
                        // interpolate so fast drags do not leave gaps
                        var x = lastX; var y = lastY
                        val dx = e.x - lastX; val dy = e.y - lastY
                        val steps = (sqrt(dx * dx + dy * dy) / (ppt / 2f)).toInt().coerceIn(0, 24)
                        for (i in 1..steps) { x += dx / steps; y += dy / steps; paintAt(x, y) }
                        lastX = e.x; lastY = e.y
                    } else {
                        lastX = e.x; lastY = e.y
                        if (rectStart != null) invalidate()
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> if (e.pointerCount <= 2) pinching = false
                MotionEvent.ACTION_UP -> {
                    if (pinching) { pinching = false; invalidate(); return true }
                    if (rectStart != null) {
                        val s = rectStart!!; val en = cellAt(e.x, e.y); rectStart = null
                        val x0 = max(0, min(s[0], en[0])); val x1 = min(m.cols - 1, max(s[0], en[0]))
                        val y0 = max(0, min(s[1], en[1])); val y1 = min(m.rows - 1, max(s[1], en[1]))
                        val cells = ArrayList<IntArray>()
                        for (row in y0..y1) for (col in x0..x1) cells.add(intArrayOf(col, row))
                        if (cells.isNotEmpty()) { snapshot(); onStroke?.invoke(cells, false) }
                        invalidate()
                        return true
                    }
                    stroking = false
                    strokeCells = ArrayList()
                    invalidate()
                }
                MotionEvent.ACTION_CANCEL -> { stroking = false; pinching = false; rectStart = null }
            }
            return true
        }

        private fun spread(e: MotionEvent): Float {
            val dx = e.getX(0) - e.getX(1); val dy = e.getY(0) - e.getY(1)
            return sqrt(dx * dx + dy * dy)
        }
        private fun midX(e: MotionEvent) = (e.getX(0) + e.getX(1)) / 2f
        private fun midY(e: MotionEvent) = (e.getY(0) + e.getY(1)) / 2f
    }
}
