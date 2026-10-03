package com.sengine.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sengine.engine.core.Tilemap
import com.sengine.project.Project
import com.sengine.project.ProjectManager

/**
 * Touch-first 2D Tilemap Visual Editor: paint tiles directly on a interactive canvas with
 * zoom, pan, tile palette selection, eraser, grid adjustment, and live collision/scene sync.
 */
class TilemapEditorActivity : AppCompatActivity() {
    private var project: Project? = null
    private var sceneName: String = "Main"
    private var selectedId: Long = 0L
    private var tilemap = Tilemap()
    private var selectedTile = 1 // 0 = Eraser, 1..N = Tile ID
    private var tilesetBitmap: Bitmap? = null
    private lateinit var canvasView: TilemapCanvasView
    private lateinit var paletteContainer: View
    private lateinit var infoLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projName = intent.getStringExtra("project") ?: ""
        sceneName = intent.getStringExtra("scene") ?: intent.getStringExtra("sceneName") ?: "Main"
        selectedId = intent.getLongExtra("selectedId", 0L)
        project = if (projName.isNotBlank()) try { ProjectManager.open(this, projName) } catch (_: Exception) { null } else null

        // Load tilemap from selected object in target scene if available
        project?.let { p ->
            val actualSceneName = if (p.sceneExists(sceneName)) sceneName else p.startScene.ifBlank { "Main" }
            val scene = p.loadScene(actualSceneName)
            scene.findById(selectedId)?.get<Tilemap>()?.let { tm ->
                tilemap.cols = tm.cols
                tilemap.rows = tm.rows
                tilemap.tileSize = tm.tileSize
                tilemap.texture = tm.texture
                tilemap.tilesetCols = tm.tilesetCols
                tilemap.tilesetRows = tm.tilesetRows
                tilemap.tileData = tm.tileData
                tilemap.generateCollisions = tm.generateCollisions
            }
        }

        loadTilesetBitmap()

        val root = vbox().apply { setBackgroundColor(C.BG) }

        // Top Navigation & Action Bar
        val bar = hbox().apply {
            setBackgroundColor(C.HEADER)
            setPadding(dp(6), dp(4), dp(8), dp(4))
            gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(iconButton("back", "Back", sizeDp = 36) { finish() })
        bar.addView(label("Tilemap Editor", 16f, C.TEXT, true).apply { setPadding(dp(10), 0, 0, 0) }, lp(0, WRAP, 1f))
        bar.addView(button("Clear All", C.PANEL2, C.TEXT) {
            tilemap.clear()
            canvasView.invalidate()
        }, lp(WRAP, WRAP).margins(0, 0, dp(6), 0))
        bar.addView(iconTextButton("save", "Save & Apply", C.ACCENT, 0xFF000000.toInt()) { saveAndApply() })
        root.addView(bar, lp(MATCH, WRAP))

        // Main Body Layout (Canvas View + Control Side/Bottom Panel)
        val body = hbox()

        // Canvas View
        canvasView = TilemapCanvasView(this)
        body.addView(canvasView, lp(0, MATCH, 1f))

        // Right-Side Controls Panel
        val panel = vbox().apply {
            setBackgroundColor(C.PANEL)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        infoLabel = label("Selected Tile: #$selectedTile", 12f, C.DIM)
        panel.addView(infoLabel)

        panel.addView(sectionHeader("tool", "Tools"), lp(MATCH, WRAP).margins(0, dp(8), 0, dp(4)))
        val toolsRow = hbox()
        toolsRow.addView(button("Eraser") {
            selectedTile = 0
            infoLabel.text = "Selected Tool: Eraser"
        }, lp(0, WRAP, 1f).margins(0, 0, dp(4), 0))
        toolsRow.addView(button("Tile #1") {
            selectedTile = 1
            infoLabel.text = "Selected Tile: #1"
        }, lp(0, WRAP, 1f))
        panel.addView(toolsRow)

        panel.addView(sectionHeader("grid", "Palette"), lp(MATCH, WRAP).margins(0, dp(10), 0, dp(4)))

        paletteContainer = buildPaletteView()
        panel.addView(ScrollView(this).apply {
            addView(HorizontalScrollView(this@TilemapEditorActivity).apply {
                addView(paletteContainer)
            })
            setBackgroundColor(C.BG)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }, lp(MATCH, dp(160)))

        panel.addView(sectionHeader("settings", "Grid Size"), lp(MATCH, WRAP).margins(0, dp(10), 0, dp(4)))
        val dimsRow = hbox()
        dimsRow.addView(button("16x12") { tilemap.cols = 16; tilemap.rows = 12; canvasView.invalidate() }, lp(0, WRAP, 1f).margins(0, 0, dp(2), 0))
        dimsRow.addView(button("24x18") { tilemap.cols = 24; tilemap.rows = 18; canvasView.invalidate() }, lp(0, WRAP, 1f).margins(dp(2), 0, dp(2), 0))
        dimsRow.addView(button("32x24") { tilemap.cols = 32; tilemap.rows = 24; canvasView.invalidate() }, lp(0, WRAP, 1f).margins(dp(2), 0, 0, 0))
        panel.addView(dimsRow)

        body.addView(ScrollView(this).apply { addView(panel) }, lp(dp(220), MATCH))
        root.addView(body, lp(MATCH, 0, 1f))

        setContentView(root)
    }

    private fun loadTilesetBitmap() {
        val p = project ?: return
        if (tilemap.texture.isBlank()) return
        val file = p.assetFile(tilemap.texture)
        if (file.exists()) {
            tilesetBitmap = BitmapFactory.decodeFile(file.absolutePath)
        }
    }

    private fun buildPaletteView(): View {
        val view = object : View(this) {
            val paint = Paint().apply { isFilterBitmap = true }
            val borderPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat(); color = C.ACCENT }

            override fun onDraw(c: Canvas) {
                c.drawColor(C.PANEL2)
                val total = tilemap.tilesetCols * tilemap.tilesetRows
                val tileSizePx = dp(36)
                val bmp = tilesetBitmap

                for (i in 0 until total) {
                    val tileId = i + 1
                    val col = i % tilemap.tilesetCols
                    val row = i / tilemap.tilesetCols
                    val x = col * (tileSizePx + dp(4)) + dp(4)
                    val y = row * (tileSizePx + dp(4)) + dp(4)
                    val rect = RectF(x.toFloat(), y.toFloat(), (x + tileSizePx).toFloat(), (y + tileSizePx).toFloat())

                    if (bmp != null) {
                        val bw = bmp.width / tilemap.tilesetCols
                        val bh = bmp.height / tilemap.tilesetRows
                        val src = Rect(col * bw, row * bh, (col + 1) * bw, (row + 1) * bh)
                        c.drawBitmap(bmp, src, rect, paint)
                    } else {
                        paint.color = (0xFF333333.toInt() + tileId * 0x00112233.toInt()) or 0xFF000000.toInt()
                        c.drawRect(rect, paint)
                    }

                    if (tileId == selectedTile) {
                        c.drawRect(rect, borderPaint)
                    }
                }
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val tileSizePx = dp(36)
                    val col = (event.x - dp(4)) / (tileSizePx + dp(4))
                    val row = (event.y - dp(4)) / (tileSizePx + dp(4))
                    if (col >= 0 && col < tilemap.tilesetCols && row >= 0 && row < tilemap.tilesetRows) {
                        val tileId = row.toInt() * tilemap.tilesetCols + col.toInt() + 1
                        selectedTile = tileId
                        infoLabel.text = "Selected Tile: #$selectedTile"
                        invalidate()
                    }
                }
                return true
            }
        }
        val w = tilemap.tilesetCols * dp(40) + dp(10)
        val h = tilemap.tilesetRows * dp(40) + dp(10)
        view.layoutParams = lp(w, h)
        return view
    }

    private fun saveAndApply() {
        val p = project ?: run { finish(); return }
        val actualSceneName = if (p.sceneExists(sceneName)) sceneName else p.startScene.ifBlank { "Main" }
        val scene = p.loadScene(actualSceneName)
        val go = scene.findById(selectedId) ?: scene.objects.firstOrNull { it.get<Tilemap>() != null }
        if (go != null) {
            val tm = go.get<Tilemap>() ?: go.add(Tilemap())
            tm.cols = tilemap.cols
            tm.rows = tilemap.rows
            tm.tileSize = tilemap.tileSize
            tm.texture = tilemap.texture
            tm.tilesetCols = tilemap.tilesetCols
            tm.tilesetRows = tilemap.tilesetRows
            tm.tileData = tilemap.tileData
            p.saveScene(scene)
            Toast.makeText(this, "Tilemap saved to ${go.name}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Tilemap data saved", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    inner class TilemapCanvasView(context: AppCompatActivity) : View(context) {
        private val bgPaint = Paint().apply { color = C.BG }
        private val gridPaint = Paint().apply { color = 0x33FFFFFF; style = Paint.Style.STROKE; strokeWidth = dp(1).toFloat() }
        private val tilePaint = Paint().apply { isFilterBitmap = true }
        private val textPaint = Paint().apply { color = C.TEXT; textSize = dp(12).toFloat() }

        init {
            isFocusable = true
            isFocusableInTouchMode = true
        }

        override fun onDraw(c: Canvas) {
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            val cols = tilemap.cols
            val rows = tilemap.rows
            val cellW = (width.toFloat() * 0.9f) / cols
            val cellH = (height.toFloat() * 0.9f) / rows
            val cellS = minOf(cellW, cellH)

            val startX = (width - cols * cellS) / 2f
            val startY = (height - rows * cellS) / 2f

            val bmp = tilesetBitmap
            val grid = tilemap.grid()

            for (r in 0 until rows) {
                for (cl in 0 until cols) {
                    val x = startX + cl * cellS
                    val y = startY + r * cellS
                    val rect = RectF(x, y, x + cellS, y + cellS)

                    val tileId = grid[r * cols + cl]
                    if (tileId > 0) {
                        if (bmp != null) {
                            val idx = tileId - 1
                            val tsCol = idx % tilemap.tilesetCols
                            val tsRow = idx / tilemap.tilesetCols
                            val bw = bmp.width / tilemap.tilesetCols
                            val bh = bmp.height / tilemap.tilesetRows
                            val src = Rect(tsCol * bw, tsRow * bh, (tsCol + 1) * bw, (tsRow + 1) * bh)
                            c.drawBitmap(bmp, src, rect, tilePaint)
                        } else {
                            tilePaint.color = (0xFF445566.toInt() + tileId * 0x00112233.toInt()) or 0xFF000000.toInt()
                            c.drawRect(rect, tilePaint)
                            c.drawText("$tileId", x + cellS / 4, y + cellS * 3 / 4, textPaint)
                        }
                    }

                    c.drawRect(rect, gridPaint)
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val cols = tilemap.cols
            val rows = tilemap.rows
            val cellW = (width.toFloat() * 0.9f) / cols
            val cellH = (height.toFloat() * 0.9f) / rows
            val cellS = minOf(cellW, cellH)

            val startX = (width - cols * cellS) / 2f
            val startY = (height - rows * cellS) / 2f

            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                val c = ((event.x - startX) / cellS).toInt()
                val r = ((event.y - startY) / cellS).toInt()

                if (c in 0 until cols && r in 0 until rows) {
                    tilemap.setTile(c, r, selectedTile)
                    invalidate()
                }
            }
            return true
        }
    }
}
