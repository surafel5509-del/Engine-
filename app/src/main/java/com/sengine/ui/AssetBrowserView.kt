package com.sengine.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.BitmapFactory
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sengine.engine.core.AssetKind
import com.sengine.project.AssetHub
import com.sengine.project.Project
import java.io.File
import java.util.concurrent.Executors

/**
 * Unity-style docked asset browser: category chips, live search, thumbnail grid, long-press actions
 * (assign / open / instantiate / favourite / rename / duplicate / export / delete) and zip import.
 */
class AssetBrowserView(
    private val activity: Activity,
    private val project: Project,
    private val callbacks: Callbacks,
) : LinearLayout(activity) {

    interface Callbacks {
        fun onAssignToSelection(file: String, kind: AssetKind?)
        fun onOpenScript(file: String)
        fun onInstantiatePrefab(file: String)
        fun onImportFiles()
        fun onImportZip()
        fun onOpenStore()
        fun onExportZip(files: List<String>)
    }

    val hub = AssetHub(project)
    private var all = listOf<AssetHub.Info>()
    private var category = "All"
    private var search = ""
    private val grid = GridLayout(activity)
    private val chips = LinearLayout(activity)
    private val searchField = EditText(activity)
    private val countLabel = TextView(activity)
    private val thumbs = HashMap<String, android.graphics.Bitmap>()
    private val pool = Executors.newSingleThreadExecutor()

    init {
        orientation = VERTICAL
        setBackgroundColor(C.PANEL)

        // ---- chips row
        chips.orientation = LinearLayout.HORIZONTAL
        chips.setPadding(dp(6), dp(3), dp(6), dp(3))
        addView(HorizontalScrollView(activity).apply {
            addView(chips); isHorizontalScrollBarEnabled = false; setBackgroundColor(C.HEADER)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // ---- search + actions row
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(3), dp(6), dp(3))
            gravity = Gravity.CENTER_VERTICAL
        }
        searchField.hint = "Search assets…"
        searchField.setSingleLine(true)
        searchField.textSize = 12f
        searchField.setTextColor(C.TEXT)
        searchField.setHintTextColor(C.DIM)
        row.addView(searchField, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, dp(6), 0) })
        row.addView(countLabel.apply { textSize = 10f; setTextColor(C.DIM); setPadding(0, 0, dp(8), 0) })
        row.addView(button("Import Files") { callbacks.onImportFiles() })
        row.addView(button("Import ZIP") { callbacks.onImportZip() })
        row.addView(button("Store") { callbacks.onOpenStore() })
        addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // ---- grid
        grid.setPadding(dp(6), dp(2), dp(6), dp(6))
        addView(ScrollView(activity).apply { addView(grid) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))

        searchField.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { search = s?.toString() ?: ""; rebuildGrid() }
        })
    }

    // ------------------------------------------------------------------ data + rebuild

    fun refresh() {
        all = hub.scan()
        rebuildChips()
        rebuildGrid()
    }

    private fun shown(): List<AssetHub.Info> = hub.query(all, category, search)

    private fun countIn(c: String): Int = when (c) {
        "All" -> all.size
        "Favorites" -> all.count { it.favorite }
        "In folders" -> all.count { it.folder.isNotEmpty() }
        else -> all.count { it.category == c }
    }

    @SuppressLint("SetTextI18n")
    private fun rebuildChips() {
        chips.removeAllViews()
        for (c in AssetHub.CATEGORIES) {
            val active = c == category
            chips.addView(button(if (c == "All") "All ${countIn(c)}" else "$c (${countIn(c)})",
                if (active) C.ACCENT else C.PANEL2, if (active) 0xFFFFFFFF.toInt() else C.DIM) {
                category = c; rebuildChips(); rebuildGrid()
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).margins(dp(2), 0, dp(2), 0))
        }
    }

    @SuppressLint("SetTextI18n")
    private fun rebuildGrid() {
        grid.removeAllViews()
        val list = shown()
        countLabel.text = "${list.size}"
        if (list.isEmpty()) {
            val t = TextView(activity).apply {
                text = "No assets here yet — use Import Files / Import ZIP, or the Store."
                setTextColor(C.DIM); textSize = 12f
                setPadding(dp(8), dp(16), dp(8), dp(8))
            }
            grid.addView(t)
            return
        }
        grid.columnCount = 5
        for (info in list) grid.addView(cell(info), gridLp())
    }

    private fun gridLp(): GridLayout.LayoutParams = GridLayout.LayoutParams().apply {
        width = dp(84)
        height = LinearLayout.LayoutParams.WRAP_CONTENT
        setMargins(dp(3), dp(3), dp(3), dp(3))
    }

    @SuppressLint("SetTextI18n")
    private fun cell(info: AssetHub.Info): LinearLayout {
        val c = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        c.background = round(if (info.favorite) 0xFF3A4254.toInt() else C.PANEL2, dp(6).toFloat())

        val name = info.file.substringAfterLast('/')
        val img = ImageView(activity).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        val cached = thumbs[info.file]
        if (cached != null) {
            img.setImageBitmap(cached)
        } else if (info.kind == AssetKind.TEXTURE) {
            img.setImageResource(android.R.drawable.ic_menu_gallery)
            val f = File(project.assetsDir, info.file)
            pool.execute {
                val b = try { BitmapFactory.decodeFile(f.absolutePath) } catch (_: Throwable) { null } ?: return@execute
                thumbs[info.file] = b
                activity.runOnUiThread { img.setImageBitmap(b) }
            }
        } else img.setImageResource(glyphIcon(info.kind))

        c.addView(img, LinearLayout.LayoutParams(dp(56), dp(56)))
        val tv = TextView(activity).apply {
            text = if (info.favorite) "★ $name" else name
            textSize = 9f; setTextColor(C.TEXT); maxLines = 2
            gravity = Gravity.CENTER_HORIZONTAL
        }
        c.addView(tv, LinearLayout.LayoutParams(dp(76), LinearLayout.LayoutParams.WRAP_CONTENT))
        c.setOnClickListener { actions(info) }
        return c
    }

    private fun glyphIcon(kind: AssetKind?): Int = when (kind) {
        AssetKind.SCRIPT -> android.R.drawable.ic_menu_edit
        AssetKind.ANIMATION -> android.R.drawable.ic_media_play
        AssetKind.SOUND, AssetKind.SONG -> android.R.drawable.ic_media_play
        AssetKind.SHADER -> android.R.drawable.ic_menu_manage
        AssetKind.MODEL -> android.R.drawable.ic_menu_compass
        AssetKind.PREFAB -> android.R.drawable.ic_menu_save
        else -> android.R.drawable.ic_menu_help
    }

    // ------------------------------------------------------------------ actions

    private fun actions(info: AssetHub.Info) {
        val name = info.file.substringAfterLast('/')
        val options = ArrayList<String>()
        val canAssign = info.kind == AssetKind.TEXTURE || info.kind == AssetKind.ANIMATION
        val isScript = info.kind == AssetKind.SCRIPT && name.endsWith(".js")
        val isBlueprint = name.endsWith(".bp")
        val isPrefab = info.kind == AssetKind.PREFAB
        if (canAssign) options.add("Assign to selected")
        if (isScript) options.add("Open script")
        if (isBlueprint) options.add("Open blueprint")
        if (isPrefab) options.add("Instantiate to scene")
        options.add(if (info.favorite) "Unfavourite" else "Favourite")
        options.add("Rename")
        options.add("Duplicate")
        options.add("Export ZIP")
        options.add("Delete")
        val pm = android.widget.PopupMenu(activity, this)
        options.forEach { pm.menu.add(it) }
        pm.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Assign to selected" -> callbacks.onAssignToSelection(info.file, info.kind)
                "Open script", "Open blueprint" -> callbacks.onOpenScript(info.file)
                "Instantiate to scene" -> callbacks.onInstantiatePrefab(info.file)
                "Favourite", "Unfavourite" -> { hub.toggleFavorite(info.file); refresh() }
                "Rename" -> renameDialog(info)
                "Duplicate" -> {
                    val src = File(project.assetsDir, info.file)
                    val base = name.substringBeforeLast('.')
                    val ext = name.substringAfterLast('.', "")
                    val dst = project.uniqueAssetName("$base copy.$ext")
                    project.assetsDir.mkdirs()
                    src.copyTo(File(project.assetsDir, dst))
                    refresh()
                    Toast.makeText(activity, "Created $dst", Toast.LENGTH_SHORT).show()
                }
                "Export ZIP" -> callbacks.onExportZip(listOf(info.file))
                "Delete" -> MaterialAlertDialogBuilder(activity)
                    .setTitle("Delete $name?").setMessage("This cannot be undone.")
                    .setPositiveButton("Delete") { _, _ -> hub.delete(info.file); refresh() }
                    .setNegativeButton("Cancel", null).show()
            }
            true
        }
        pm.show()
    }

    private fun renameDialog(info: AssetHub.Info) {
        val f = EditText(activity).apply { setText(info.file.substringAfterLast('/')); setSingleLine(true) }
        MaterialAlertDialogBuilder(activity)
            .setTitle("Rename asset")
            .setView(LinearLayout(activity).apply {
                setPadding(dp(20), dp(8), dp(20), 0)
                addView(f, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            })
            .setPositiveButton("OK") { _, _ ->
                val newName = f.text.toString().trim()
                if (newName.isNotEmpty() && newName != info.file.substringAfterLast('/')) {
                    val dst = if (info.file.contains('/')) info.file.substringBeforeLast('/') + "/" + newName else newName
                    val renamed = hub.rename(info.file, dst)
                    Toast.makeText(activity, if (renamed != null) "Renamed to $renamed" else "Rename failed", Toast.LENGTH_SHORT).show()
                }
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------------ helpers

    private fun button(text: String, color: Int = C.PANEL2, tint: Int = C.TEXT, onClick: (View) -> Unit): TextView =
        TextView(activity).apply {
            this.text = text; textSize = 11f; setTextColor(tint)
            background = round(color, dp(6).toFloat())
            setPadding(dp(8), dp(5), dp(8), dp(5))
            gravity = Gravity.CENTER
            setOnClickListener(onClick)
        }

    private val dp: (Int) -> Int get() = { v -> (activity.resources.displayMetrics.density * v).toInt() }

    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)

    private fun LinearLayout.LayoutParams.margins(l: Int, t: Int, r: Int, b: Int) = apply { setMargins(l, t, r, b) }
}
