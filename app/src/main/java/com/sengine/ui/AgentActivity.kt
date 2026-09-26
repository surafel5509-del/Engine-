package com.sengine.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sengine.agent.AgentConfig
import com.sengine.agent.AgentEvent
import com.sengine.agent.AgentHost
import com.sengine.agent.AgentTools
import com.sengine.agent.GameAgent
import com.sengine.project.Project
import com.sengine.project.ProjectManager
import com.sengine.project.Templates
import java.io.File
import kotlin.concurrent.thread

/**
 * AI Agent mode: describe any game; the agent thinks, plans a checklist, creates the project,
 * builds scenes/scripts/art/music, self-tests (doctor + headless play-test) and hands over an APK build.
 */
class AgentActivity : AppCompatActivity() {
    private lateinit var config: AgentConfig
    private lateinit var prompt: android.widget.EditText
    private lateinit var runBtn: TextView
    private lateinit var modelChip: TextView
    private lateinit var phaseText: TextView
    private lateinit var planBox: LinearLayout
    private lateinit var logBox: LinearLayout
    private lateinit var logScroll: ScrollView
    private lateinit var doneBar: LinearLayout
    private var agent: GameAgent? = null
    private var resultProject: String? = null
    private var steps = 0

    private val examples = listOf(
        "A top-down tank battle with destructible walls, 3 enemy types, power-ups and a boss",
        "A 3D first-person shooter in an abandoned city with rifles, enemies and missions",
        "A neon space shooter with waves, a boss and upgrades",
        "A cute platformer about a fox collecting gems in a forest",
        "A zombie survival game at night with shotguns and waves",
        "A 3D car racing game on a desert track with AI rivals",
        "A Minecraft-like voxel survival world with building",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = AgentConfig(File(filesDir, "agent/config.json"))
        val root = vbox().apply { setBackgroundColor(C.BG) }

        // --- top bar
        val bar = hbox().apply { setBackgroundColor(C.HEADER); setPadding(dp(6), dp(5), dp(10), dp(5)); gravity = Gravity.CENTER_VERTICAL }
        bar.addView(iconButton("back", "Back", sizeDp = 36) { finish() })
        bar.addView(android.widget.ImageView(this).apply { setImageDrawable(Icons.drawable(this@AgentActivity, "robot", C.ACCENT, 24)) }, lp(dp(34), dp(34)).margins(dp(8), 0, 0, 0))
        bar.addView(vbox().apply {
            addView(label("AI Game Agent", 16f, C.TEXT, true))
            addView(label("Prompt → plan → build → self-test → APK", 11f, C.DIM))
        }, lp(WRAP, WRAP).margins(dp(8), 0, 0, 0))
        bar.addView(View(this), lp(0, 1, 1f))
        modelChip = iconTextButton("chip", "", C.PANEL2) { chooseModel() }
        bar.addView(modelChip)
        bar.addView(iconButton("gear", "Agent settings", sizeDp = 36) { startActivity(Intent(this, AgentSettingsActivity::class.java)) }, lp(WRAP, WRAP).margins(dp(6), 0, 0, 0))
        root.addView(bar, lp(MATCH, WRAP))
        root.addView(View(this).apply { setBackgroundColor(C.BORDER) }, lp(MATCH, 1))

        // --- body
        val body = hbox().apply { gravity = Gravity.TOP }
        val left = vbox().apply { setPadding(dp(14), dp(12), dp(14), dp(12)) }
        left.addView(sectionHeader("wand", "Describe your game"))
        prompt = field("", multiline = true).apply {
            hint = "e.g. A top-down tank game with 10 levels, desert and snow maps, upgrades and a boss…"
            setHintTextColor(C.DIM); minLines = 4; gravity = Gravity.TOP
        }
        left.addView(prompt, lp(MATCH, WRAP).margins(0, dp(6), 0, dp(6)))
        val chips = hbox()
        for (ex in examples) chips.addView(label(ex.take(34) + "…", 11f, C.TEXT).apply {
            background = round(C.PANEL2, dp(14).toFloat(), 1, C.BORDER); setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { prompt.setText(ex) }
        }, lp(WRAP, WRAP).margins(0, 0, dp(6), 0))
        left.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) }, lp(MATCH, WRAP))
        runBtn = button("Start building", C.ACCENT, 0xFF000000.toInt()) { toggleRun() }.apply { textSize = 15f; setPadding(dp(16), dp(12), dp(16), dp(12)) }
        left.addView(runBtn, lp(MATCH, WRAP).margins(0, dp(10), 0, dp(6)))
        phaseText = label("Ready", 12f, C.DIM).apply { gravity = Gravity.CENTER }
        left.addView(phaseText, lp(MATCH, WRAP))
        left.addView(sectionHeader("check", "Plan"), lp(MATCH, WRAP).margins(0, dp(12), 0, dp(4)))
        planBox = vbox()
        planBox.addView(label("The agent's task checklist appears here.", 12f, C.DIM))
        left.addView(planBox, lp(MATCH, WRAP))
        doneBar = vbox().apply { visibility = View.GONE }
        left.addView(doneBar, lp(MATCH, WRAP).margins(0, dp(12), 0, 0))
        body.addView(ScrollView(this).apply { addView(left); setBackgroundColor(C.PANEL) }, lp(0, MATCH, 0.42f))
        body.addView(View(this).apply { setBackgroundColor(C.BORDER) }, lp(1, MATCH))

        val right = vbox().apply { setPadding(dp(12), dp(10), dp(12), dp(10)) }
        val rh = hbox().apply { gravity = Gravity.CENTER_VERTICAL }
        rh.addView(sectionHeader("code", "Agent activity"), lp(0, WRAP, 1f))
        rh.addView(iconButton("trash", "Clear", sizeDp = 30) { logBox.removeAllViews() })
        right.addView(rh)
        logBox = vbox()
        logScroll = ScrollView(this).apply { addView(logBox) }
        right.addView(logScroll, lp(MATCH, 0, 1f))
        body.addView(right, lp(0, MATCH, 0.58f))
        root.addView(body, lp(MATCH, 0, 1f))
        setContentView(root)
        intent.getStringExtra("prompt")?.let { prompt.setText(it) }
        log("info", "Welcome! Pick a model (offline planner works without a key) and describe a game.", C.DIM)
    }

    override fun onResume() {
        super.onResume()
        config.load()
        modelChip.text = "  " + config.active.name
    }

    private fun chooseModel() {
        val names = config.profiles.map { "${it.name}  ·  ${it.kind.title}${if (it.kind != com.sengine.agent.ProviderKind.LOCAL) " / " + it.model else ""}" }.toTypedArray()
        MaterialAlertDialogBuilder(this).setTitle("Switch model")
            .setSingleChoiceItems(names, config.profiles.indexOf(config.active)) { d, i ->
                config.activeId = config.profiles[i].id; config.save(); modelChip.text = "  " + config.active.name; d.dismiss()
            }
            .setNeutralButton("Manage models") { _, _ -> startActivity(Intent(this, AgentSettingsActivity::class.java)) }
            .show()
    }

    private fun toggleRun() {
        agent?.let { it.cancelled = true; phaseText.text = "Stopping…"; return }
        val text = prompt.text.toString().trim()
        if (text.length < 5) { Toast.makeText(this, "Describe the game first", Toast.LENGTH_SHORT).show(); return }
        val profile = config.active
        config.validate(profile)?.let { err ->
            MaterialAlertDialogBuilder(this).setTitle("Model not ready").setMessage(err)
                .setPositiveButton("Open settings") { _, _ -> startActivity(Intent(this, AgentSettingsActivity::class.java)) }
                .setNegativeButton("Cancel", null).show()
            return
        }
        logBox.removeAllViews(); planBox.removeAllViews(); doneBar.visibility = View.GONE; steps = 0
        log("user", text, C.TEXT, bold = true)
        val host = object : AgentHost {
            override fun createProject(name: String, template: Templates.Template): Project = ProjectManager.create(this@AgentActivity, name, template)
            override fun openProject(name: String): Project? = if (ProjectManager.exists(this@AgentActivity, name)) ProjectManager.open(this@AgentActivity, name) else null
            override fun buildApk(project: Project): String = "The APK builder will open when the agent finishes (signing happens on device)."
        }
        val tools = AgentTools(host)
        val a = GameAgent(config.modelFor(profile), tools, config.maxSteps) { ev -> runOnUiThread { onEvent(ev) } }
        agent = a
        runBtn.text = "Stop"; runBtn.setButtonColor(C.PANEL2); runBtn.setTextColor(C.RED)
        thread(name = "agent") {
            try { a.run(text) } catch (e: Throwable) { runOnUiThread { onEvent(AgentEvent.Error("Crash: ${e.message}")) } }
            runOnUiThread {
                agent = null
                runBtn.text = "Start building"; runBtn.setButtonColor(C.ACCENT)
                if (resultProject == null) resultProject = tools.project?.name
                showDoneBar(tools.buildRequested)
            }
        }
    }

    private fun onEvent(ev: AgentEvent) {
        when (ev) {
            is AgentEvent.Phase -> { phaseText.text = "● " + ev.name; log("phase", ev.name.uppercase(), C.ACCENT, bold = true) }
            is AgentEvent.Thought -> log("thought", ev.text, C.ACCENT2)
            is AgentEvent.PlanUpdated -> renderPlan(ev.tasks)
            is AgentEvent.ToolCall -> { steps++; log("tool", "→ ${ev.tool}  ${ev.args}", C.DIM, mono = true) }
            is AgentEvent.ToolOutput -> log("out", (if (ev.ok) "✓ " else "✗ ") + ev.text.take(900), if (ev.ok) C.GREEN else C.RED, mono = true)
            is AgentEvent.Error -> { phaseText.text = ev.text; log("error", ev.text, C.RED, bold = true) }
            is AgentEvent.Done -> {
                phaseText.text = "✓ Game ready"; resultProject = ev.project
                log("done", "GAME READY: ${ev.summary}", C.GREEN, bold = true)
            }
        }
    }

    private fun renderPlan(tasks: List<AgentTools.PlanTask>) {
        planBox.removeAllViews()
        val done = tasks.count { it.done }
        planBox.addView(UiBits.progress(this, if (tasks.isEmpty()) 0f else done / tasks.size.toFloat()), lp(MATCH, dp(6)).margins(0, 0, 0, dp(6)))
        for (t in tasks) {
            val row = hbox().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(3), 0, dp(3)) }
            row.addView(android.widget.ImageView(this).apply {
                setImageDrawable(Icons.drawable(this@AgentActivity, if (t.done) "check" else "frame", if (t.done) C.GREEN else C.DIM, 16))
            }, lp(dp(20), dp(20)))
            row.addView(label("${t.id}. ${t.title}", 12.5f, if (t.done) C.DIM else C.TEXT).apply {
                if (t.done) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                setPadding(dp(6), 0, 0, 0)
            }, lp(0, WRAP, 1f))
            planBox.addView(row)
        }
    }

    private fun showDoneBar(build: Boolean) {
        val name = resultProject ?: return
        doneBar.removeAllViews(); doneBar.visibility = View.VISIBLE
        doneBar.addView(sectionHeader("trophy", "Your game: $name"))
        val row = hbox()
        row.addView(iconTextButton("play", "Play", C.ACCENT, 0xFF000000.toInt()) { startActivity(Intent(this, PlayerActivity::class.java).putExtra("project", name)) }, lp(0, WRAP, 1f).margins(0, 0, dp(6), 0))
        row.addView(iconTextButton("cube", "Editor", C.PANEL2) { startActivity(Intent(this, EditorActivity::class.java).putExtra("project", name)) }, lp(0, WRAP, 1f).margins(0, 0, dp(6), 0))
        row.addView(iconTextButton("download", "Build APK", C.PANEL2) { openBuild(name) }, lp(0, WRAP, 1f))
        doneBar.addView(row, lp(MATCH, WRAP).margins(0, dp(6), 0, 0))
        if (build) openBuild(name)
    }

    private fun openBuild(name: String) {
        startActivity(Intent(this, BuildActivity::class.java).putExtra("project", name))
    }

    private fun log(kind: String, text: String, color: Int, bold: Boolean = false, mono: Boolean = false) {
        val t = label(text, if (mono) 11.5f else 13f, color, bold).apply {
            if (mono) typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            if (kind == "thought" || kind == "user") background = round(C.PANEL2, dp(8).toFloat())
            if (kind == "done") background = round(0x2222C55E, dp(8).toFloat(), 1, C.GREEN)
        }
        logBox.addView(t, lp(MATCH, WRAP).margins(0, dp(2), 0, dp(2)))
        while (logBox.childCount > 400) logBox.removeViewAt(0)
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }
}

/** Small shared widgets for the v3 screens. */
object UiBits {
    fun progress(ctx: android.content.Context, v: Float): View = object : View(ctx) {
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: android.graphics.Canvas) {
            val r = height / 2f
            p.color = C.PANEL2; c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, p)
            p.color = C.ACCENT; c.drawRoundRect(0f, 0f, width * v.coerceIn(0f, 1f), height.toFloat(), r, r, p)
        }
    }
}
