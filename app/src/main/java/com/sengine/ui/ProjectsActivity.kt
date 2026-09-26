package com.sengine.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sengine.engine.core.AssetKind
import com.sengine.project.Project
import com.sengine.project.ProjectManager
import com.sengine.project.Templates
import java.text.DateFormat
import java.util.Date

class ProjectsActivity : AppCompatActivity() {

    private lateinit var list: LinearLayout
    private var exporting: Project? = null

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val p = exporting ?: return@registerForActivityResult
        if (uri != null) try {
            contentResolver.openOutputStream(uri)?.use { ProjectManager.exportZip(p, it) }
            toast("Exported ${p.name}")
        } catch (e: Exception) {
            toast("Export failed: ${e.message}")
        }
        exporting = null
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importProject(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Exported game: go straight to the player.
        com.sengine.export.GameRuntime.standaloneProject(this)?.let { game ->
            startActivity(Intent(this, PlayerActivity::class.java).putExtra("projectDir", game.dir.absolutePath).putExtra("standalone", true))
            finish()
            return
        }
        val root = vbox().apply { setBackgroundColor(C.BG) }

        val header = hbox().apply {
            setPadding(dp(20), dp(16), dp(14), dp(14))
            background = gradient(0xFF111111.toInt(), 0xFF000000.toInt(), 0f)
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = android.widget.ImageView(this).apply {
            setImageDrawable(Icons.drawable(this@ProjectsActivity, "rocket", 0xFF000000.toInt(), 28))
            scaleType = android.widget.ImageView.ScaleType.CENTER
            background = gradient(0xFFFFFFFF.toInt(), 0xFF9E9E9E.toInt(), dp(14).toFloat())
        }
        header.addView(logo, lp(dp(52), dp(52)).margins(0, 0, dp(14), 0))
        val titleBox = vbox()
        titleBox.addView(label("S Engine", 26f, C.TEXT, true))
        titleBox.addView(label("2D & 3D game engine for Android  •  3rd Edition", 12f, C.DIM))
        header.addView(titleBox, lp(0, WRAP, 1f))
        header.addView(iconButton("help", "Help Center") { startActivity(Intent(this, HelpActivity::class.java)) }, lp(dp(44), dp(44)).margins(dp(4), 0, dp(4), 0))
        header.addView(iconButton("download", "Import project (.zip)") { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, lp(dp(44), dp(44)).margins(dp(4), 0, dp(8), 0))
        header.addView(iconTextButton("plus", "New Project", C.ACCENT, 0xFF000000.toInt()) { newProjectDialog() })
        root.addView(header, lp(MATCH, WRAP))

        val body = vbox()
        // AI agent hero
        val hero = hbox().apply {
            background = round(0xFF0C0C0C.toInt(), dp(16).toFloat(), 1, 0xFF3A3A3A.toInt())
            setPadding(dp(18), dp(14), dp(14), dp(14)); gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { startActivity(Intent(this@ProjectsActivity, AgentActivity::class.java)) }
        }
        hero.addView(android.widget.ImageView(this).apply {
            setImageDrawable(Icons.drawable(this@ProjectsActivity, "robot", 0xFF000000.toInt(), 30))
            scaleType = android.widget.ImageView.ScaleType.CENTER
            background = round(0xFFFFFFFF.toInt(), dp(14).toFloat())
        }, lp(dp(56), dp(56)))
        hero.addView(vbox().apply {
            addView(label("AI Game Agent", 18f, C.TEXT, true))
            addView(label("Describe any game — the agent plans it, builds scenes, scripts, art and music, tests itself and exports an APK. Bring your own OpenAI / Claude / Gemini / OpenRouter key or use the offline planner.", 12f, C.DIM))
        }, lp(0, WRAP, 1f).margins(dp(14), 0, dp(10), 0))
        hero.addView(iconTextButton("wand", "Create with AI", C.ACCENT, 0xFF000000.toInt()) { startActivity(Intent(this, AgentActivity::class.java)) })
        body.addView(hero, lp(MATCH, WRAP).margins(dp(20), dp(14), dp(20), dp(4)))
        body.addView(sectionHeader("gamepad", "SAMPLE GAMES — made with S Engine").apply { setPadding(dp(20), dp(14), dp(20), dp(6)) })
        val games = hbox().apply { setPadding(dp(14), 0, dp(14), dp(4)) }
        val gameIcons = listOf("target" to 0xFF2563EB.toInt(), "fire" to 0xFFDC2626.toInt(), "car" to 0xFFF59E0B.toInt(), "cube" to 0xFF65A30D.toInt(), "tank" to 0xFF4D7C0F.toInt(), "shield" to 0xFF57534E.toInt(), "star" to 0xFF7C3AED.toInt())
        com.sengine.project.games.Games.templates.forEachIndexed { i, t ->
            val c = vbox().apply { background = round(C.PANEL, dp(14).toFloat()); setPadding(dp(12), dp(12), dp(12), dp(12)) }
            val ic = android.widget.ImageView(this).apply {
                setImageDrawable(Icons.drawable(this@ProjectsActivity, gameIcons[i % gameIcons.size].first, 0xFFFFFFFF.toInt(), 30))
                scaleType = android.widget.ImageView.ScaleType.CENTER
                background = gradient(gameIcons[i % gameIcons.size].second, GameColors.dark(gameIcons[i % gameIcons.size].second), dp(12).toFloat())
            }
            c.addView(ic, lp(MATCH, dp(70)))
            c.addView(label(t.name.substringBefore(" ("), 15f, C.TEXT, true).apply { setPadding(0, dp(8), 0, 0) })
            c.addView(label(t.name.substringAfter("(").removeSuffix(")"), 11f, C.DIM))
            val row = hbox().apply { setPadding(0, dp(8), 0, 0) }
            row.addView(iconTextButton("play", "Play", C.GREEN, 0xFFFFFFFF.toInt()) { sample(t, true) }, lp(0, WRAP, 1f).margins(0, 0, dp(4), 0))
            row.addView(iconButton("code", "Open in editor", sizeDp = 36) { sample(t, false) })
            c.addView(row, lp(MATCH, WRAP))
            c.setOnClickListener { sample(t, true) }
            games.addView(c, lp(dp(190), WRAP).margins(dp(6), dp(4), dp(6), dp(4)))
        }
        body.addView(HorizontalScrollView(this).apply { addView(games); isHorizontalScrollBarEnabled = false }, lp(MATCH, WRAP))
        body.addView(sectionHeader("folder", "YOUR PROJECTS").apply { setPadding(dp(20), dp(14), dp(20), dp(6)) })
        list = vbox().apply { setPadding(dp(14), 0, dp(14), dp(20)) }
        body.addView(list, lp(MATCH, WRAP))
        root.addView(ScrollView(this).apply { addView(body) }, lp(MATCH, 0, 1f))
        setContentView(root)

        val prefs = getSharedPreferences("sengine", MODE_PRIVATE)
        if (!prefs.getBoolean("seeded", false)) {
            prefs.edit().putBoolean("seeded", true).apply()
            if (ProjectManager.list(this).isEmpty()) {
                ProjectManager.create(this, "Platformer Demo", Templates.all[1])
                ProjectManager.create(this, "Space Shooter", Templates.all[2])
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        runTestAutomation()
    }

    /**
     * Debug-build automation used by CI's emulator smoke test:
     *   adb shell am start -n com.sengine.app/com.sengine.ui.ProjectsActivity --es sengine_test open|open3d|play|build [--es project NAME]
     */
    private fun runTestAutomation() {
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        val action = intent.getStringExtra("sengine_test") ?: return
        intent.removeExtra("sengine_test")
        val name = intent.getStringExtra("project") ?: ProjectManager.list(this).firstOrNull()?.name ?: return
        if (!ProjectManager.exists(this, name)) {
            val t = Templates.all.firstOrNull { it.name == name } ?: Templates.all.last()
            ProjectManager.create(this, name, t)
        }
        when (action) {
            "open" -> startActivity(Intent(this, EditorActivity::class.java).putExtra("project", name))
            "open3d" -> startActivity(Intent(this, EditorActivity::class.java).putExtra("project", name).putExtra("mode3d", true))
            "play" -> startActivity(Intent(this, PlayerActivity::class.java).putExtra("project", name))
            "build" -> startActivity(Intent(this, BuildActivity::class.java).putExtra("project", name).putExtra("autobuild", true).putExtra("output", "test_game.apk"))
            "store" -> startActivity(Intent(this, AssetStoreActivity::class.java).putExtra("project", name))
            "anim" -> startActivity(Intent(this, AnimationEditorActivity::class.java).putExtra("project", name))
            "blueprint" -> {
                val p = ProjectManager.open(this, name)
                val bp = p.listAssets(com.sengine.engine.core.AssetKind.SCRIPT).firstOrNull { it.endsWith(".bp") }
                    ?: "Test.bp".also { p.writeAsset(it, com.sengine.engine.blueprint.Blueprint.defaultGraph().toJson().toString(2)) }
                startActivity(Intent(this, BlueprintEditorActivity::class.java).putExtra("project", name).putExtra("asset", bp))
            }
        }
    }

    private fun refresh() {
        list.removeAllViews()
        val projects = ProjectManager.list(this)
        if (projects.isEmpty()) {
            list.addView(label("No projects yet.\nTap “+ New Project” to create your first game.", 15f, C.DIM).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(60), 0, 0)
            }, lp(MATCH, WRAP))
        }
        for (p in projects) list.addView(card(p), lp(MATCH, WRAP).margins(0, dp(5), 0, dp(5)))
    }

    private fun card(p: Project): LinearLayout {
        val card = hbox().apply {
            background = round(C.PANEL, dp(10).toFloat())
            setPadding(dp(16), dp(12), dp(10), dp(12))
        }
        val icon = label(p.name.take(1).uppercase(), 22f, 0xFFFFFFFF.toInt(), true).apply {
            gravity = Gravity.CENTER
            background = round(colorFor(p.name), dp(10).toFloat())
        }
        card.addView(icon, lp(dp(48), dp(48)).margins(0, 0, dp(14), 0))
        val info = vbox()
        info.addView(label(p.name, 17f, C.TEXT, true))
        val scenes = p.listScenes().size
        val scripts = p.listAssets(AssetKind.SCRIPT).size
        val assets = p.listAssets().size
        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(p.dir.lastModified()))
        info.addView(label("$scenes scene(s) • $scripts script(s) • $assets asset(s)\nModified $date", 12f, C.DIM))
        card.addView(info, lp(0, WRAP, 1f))
        card.addView(iconButton("play", "Play", C.GREEN, C.PANEL2) { play(p) }, lp(dp(40), dp(40)).margins(dp(4), 0, dp(4), 0))
        card.addView(iconTextButton("code", "Open", C.ACCENT, 0xFFFFFFFF.toInt()) { open(p) }, lp(WRAP, WRAP).margins(dp(4), 0, dp(4), 0))
        val more = iconButton("more", "More") { v ->
            val pm = PopupMenu(this, v)
            pm.menu.add("Rename"); pm.menu.add("Duplicate"); pm.menu.add("Export .zip"); pm.menu.add("Delete")
            pm.setOnMenuItemClickListener {
                when (it.title) {
                    "Rename" -> renameDialog(p)
                    "Duplicate" -> { ProjectManager.duplicate(this, p); refresh() }
                    "Export .zip" -> { exporting = p; exportLauncher.launch("${p.name}.zip") }
                    "Delete" -> confirmDelete(p)
                }
                true
            }
            pm.show()
        }
        card.addView(more)
        card.setOnClickListener { open(p) }
        return card
    }

    /** Opens (creating on first use) one of the bundled sample games. */
    private fun sample(t: Templates.Template, playNow: Boolean) {
        val name = t.name.substringBefore(" (")
        val p = if (ProjectManager.exists(this, name)) ProjectManager.open(this, name) else {
            toast("Installing $name…")
            ProjectManager.create(this, name, t)
        }
        refresh()
        if (playNow) play(p) else open(p)
    }

    private object GameColors {
        fun dark(c: Int): Int {
            fun ch(s: Int): Int = (((c shr s) and 0xFF) * 3 / 5)
            return (c and 0xFF000000.toInt()) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }

    private fun colorFor(s: String): Int {
        val palette = intArrayOf(0xFF4C8DFF.toInt(), 0xFFE5534B.toInt(), 0xFF57AB5A.toInt(), 0xFFE0B341.toInt(), 0xFFAB47BC.toInt(), 0xFF26A69A.toInt())
        return palette[Math.abs(s.hashCode()) % palette.size]
    }

    private fun open(p: Project) {
        startActivity(Intent(this, EditorActivity::class.java).putExtra("project", p.name))
    }

    private fun play(p: Project) {
        startActivity(Intent(this, PlayerActivity::class.java).putExtra("project", p.name))
    }

    private fun newProjectDialog() {
        val box = vbox().apply { setPadding(dp(20), dp(8), dp(20), 0) }
        val name = field("My Game")
        box.addView(label("Project name", 12f, C.DIM))
        box.addView(name, lp(MATCH, WRAP).margins(0, dp(4), 0, dp(12)))
        box.addView(label("Template", 12f, C.DIM))
        val group = RadioGroup(this)
        Templates.all.forEachIndexed { i, t ->
            group.addView(RadioButton(this).apply {
                id = 1000 + i
                text = "${t.name}\n${t.description}"
                setTextColor(C.TEXT)
                textSize = 13f
                setPadding(dp(4), dp(6), 0, dp(6))
            })
        }
        group.check(1000)
        box.addView(group)
        MaterialAlertDialogBuilder(this)
            .setTitle("New Project")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Create") { _, _ ->
                val n = ProjectManager.sanitize(name.text.toString())
                when {
                    n.isBlank() -> toast("Invalid name")
                    ProjectManager.exists(this, n) -> toast("A project named \"$n\" already exists")
                    else -> {
                        val p = ProjectManager.create(this, n, Templates.all[group.checkedRadioButtonId - 1000])
                        open(p)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renameDialog(p: Project) {
        val f: EditText = field(p.name)
        MaterialAlertDialogBuilder(this)
            .setTitle("Rename project")
            .setView(LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(f, lp(MATCH, WRAP)) })
            .setPositiveButton("Rename") { _, _ ->
                val n = ProjectManager.sanitize(f.text.toString())
                if (n.isBlank() || ProjectManager.rename(this, p, n) == null) toast("Could not rename")
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(p: Project) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete ${p.name}?")
            .setMessage("This permanently deletes all scenes, scripts and assets of this project.")
            .setPositiveButton("Delete") { _, _ -> ProjectManager.delete(p); refresh() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importProject(uri: Uri) {
        try {
            var display = "Imported"
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) display = it.getString(0).substringBeforeLast('.')
            }
            val p = contentResolver.openInputStream(uri)!!.use { ProjectManager.importZip(this, it, display) }
            toast("Imported ${p.name}")
            refresh()
        } catch (e: Exception) {
            toast("Import failed: ${e.message}")
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
