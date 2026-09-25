package com.sengine.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sengine.project.GameDoctor
import com.sengine.project.Project
import com.sengine.project.ProjectManager
import kotlin.concurrent.thread

/** Help Center: searchable guide, script recipes (insert into project) and the Game Doctor. */
class HelpActivity : AppCompatActivity() {
    private var project: Project? = null
    private lateinit var nav: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var contentScroll: ScrollView
    private lateinit var tabs: List<TextView>
    private lateinit var search: android.widget.EditText
    private var tab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = intent.getStringExtra("project")?.let { try { ProjectManager.open(this, it) } catch (_: Exception) { null } }
        val root = vbox().apply { setBackgroundColor(C.BG) }
        val bar = hbox().apply { setBackgroundColor(C.HEADER); setPadding(dp(6), dp(5), dp(10), dp(5)) }
        bar.addView(iconButton("back", "Back", sizeDp = 36) { finish() })
        bar.addView(label("Help Center", 16f, C.TEXT, true).apply { setPadding(dp(10), 0, dp(16), 0) })
        val tabBox = hbox().apply { background = round(C.PANEL2, dp(10).toFloat()); setPadding(dp(2), dp(2), dp(2), dp(2)) }
        tabs = listOf("book" to "Guide", "code" to "Recipes", "doctor" to "Game Doctor").mapIndexed { i, (ic, t) ->
            iconTextButton(ic, t, C.PANEL2) { showTab(i) }.also { tabBox.addView(it) }
        }
        bar.addView(tabBox)
        bar.addView(View(this), lp(0, 1, 1f))
        search = field("").apply { hint = "Search help…"; setHintTextColor(C.DIM) }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { if (tab == 0) buildNav() }
        })
        bar.addView(search, lp(dp(200), WRAP))
        root.addView(bar, lp(MATCH, WRAP))

        val body = hbox().apply { gravity = Gravity.TOP }
        nav = vbox().apply { setPadding(dp(6), dp(6), dp(6), dp(6)) }
        body.addView(ScrollView(this).apply { addView(nav); setBackgroundColor(C.PANEL) }, lp(dp(240), MATCH))
        content = vbox().apply { setPadding(dp(24), dp(14), dp(24), dp(40)) }
        contentScroll = ScrollView(this).apply { addView(content) }
        body.addView(contentScroll, lp(0, MATCH, 1f))
        root.addView(body, lp(MATCH, 0, 1f))
        setContentView(root)

        val topic = intent.getStringExtra("topic")
        when (topic) {
            "doctor" -> showTab(2)
            "recipes" -> showTab(1)
            else -> { showTab(0); HelpContent.topics.firstOrNull { it.id == topic }?.let { showTopic(it) } }
        }
    }

    private fun showTab(i: Int) {
        tab = i
        tabs.forEachIndexed { k, t -> t.setButtonColor(if (k == i) C.ACCENT else C.PANEL2); t.setTextColor(if (k == i) 0xFFFFFFFF.toInt() else C.TEXT) }
        search.visibility = if (i == 0) View.VISIBLE else View.INVISIBLE
        when (i) {
            0 -> { buildNav(); showTopic(HelpContent.topics[0]) }
            1 -> buildRecipes()
            else -> runDoctor()
        }
    }

    // ---------------------------------------------------------------- guide
    private fun buildNav() {
        nav.removeAllViews()
        val q = search.text.toString().trim().lowercase()
        val list = if (q.isEmpty()) HelpContent.topics else HelpContent.topics.filter { it.title.lowercase().contains(q) || it.body.lowercase().contains(q) }
        nav.addView(sectionHeader("book", if (q.isEmpty()) "Topics (${list.size})" else "Results (${list.size})"))
        for (t in list) {
            val b = iconTextButton(t.icon, t.title, C.PANEL) { showTopic(t) }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }
            nav.addView(b, lp(MATCH, WRAP).margins(0, dp(1), 0, dp(1)))
        }
        if (list.isEmpty()) nav.addView(label("No matches.", 12f, C.DIM).apply { setPadding(dp(8), dp(8), 0, 0) })
    }

    private fun showTopic(t: HelpContent.Topic) {
        content.removeAllViews()
        render(t.body)
        contentScroll.scrollTo(0, 0)
    }

    /** Renders the tiny markup used by HelpContent. */
    private fun render(text: String) {
        var inCode = false
        val code = StringBuilder()
        val para = StringBuilder()
        fun flushPara() {
            if (para.isNotBlank()) content.addView(label(para.toString().trim(), 14f, C.TEXT).apply { setLineSpacing(0f, 1.2f); setTextIsSelectable(true) }, lp(MATCH, WRAP).margins(0, dp(4), 0, dp(6)))
            para.clear()
        }
        for (raw in text.trimIndent().lines()) {
            val line = raw.trimEnd()
            if (line.trim().startsWith("```")) {
                if (inCode) {
                    content.addView(label(code.toString().trimEnd(), 12f, 0xFFB8F2E6.toInt()).apply {
                        typeface = Typeface.MONOSPACE; setTextIsSelectable(true)
                        background = round(C.FIELD, dp(10).toFloat(), dp(1), C.BORDER); setPadding(dp(12), dp(10), dp(12), dp(10))
                    }, lp(MATCH, WRAP).margins(0, dp(4), 0, dp(8)))
                    code.clear()
                } else flushPara()
                inCode = !inCode; continue
            }
            if (inCode) { code.append(raw).append('\n'); continue }
            when {
                line.startsWith("# ") -> { flushPara(); content.addView(label(line.substring(2), 24f, C.TEXT, true), lp(MATCH, WRAP).margins(0, dp(6), 0, dp(8))) }
                line.startsWith("## ") -> { flushPara(); content.addView(label(line.substring(3), 17f, C.ACCENT2, true), lp(MATCH, WRAP).margins(0, dp(12), 0, dp(4))) }
                line.startsWith("- ") -> {
                    flushPara()
                    val row = hbox().apply { gravity = Gravity.TOP }
                    row.addView(label("•", 14f, C.ACCENT), lp(dp(16), WRAP))
                    row.addView(label(line.substring(2), 14f, C.TEXT).apply { setLineSpacing(0f, 1.15f) }, lp(0, WRAP, 1f))
                    content.addView(row, lp(MATCH, WRAP).margins(dp(4), dp(2), 0, dp(2)))
                }
                line.isBlank() -> flushPara()
                else -> para.append(line).append(' ')
            }
        }
        flushPara()
    }

    // ---------------------------------------------------------------- recipes
    private fun buildRecipes() {
        nav.removeAllViews()
        nav.addView(sectionHeader("code", "Recipes (${HelpContent.recipes.size})"))
        for (r in HelpContent.recipes) nav.addView(iconTextButton(r.icon, r.title, C.PANEL) { showRecipe(r) }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, lp(MATCH, WRAP).margins(0, dp(1), 0, dp(1)))
        showRecipe(HelpContent.recipes[0])
    }

    private fun showRecipe(r: HelpContent.Recipe) {
        content.removeAllViews()
        content.addView(label(r.title, 24f, C.TEXT, true))
        content.addView(label(r.description, 14f, C.DIM).apply { setPadding(0, dp(4), 0, dp(10)) })
        content.addView(label(r.code.trim(), 12f, 0xFFB8F2E6.toInt()).apply {
            typeface = Typeface.MONOSPACE; setTextIsSelectable(true)
            background = round(C.FIELD, dp(10).toFloat(), dp(1), C.BORDER); setPadding(dp(12), dp(10), dp(12), dp(10))
        }, lp(MATCH, WRAP))
        val row = hbox().apply { setPadding(0, dp(12), 0, 0) }
        row.addView(iconTextButton("copy", "Copy") {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText(r.file, r.code.trim())); toast("Copied")
        })
        val p = project
        if (p != null) row.addView(iconTextButton("plus", "Add ${r.file} to project", C.ACCENT, 0xFFFFFFFF.toInt()) {
            val n = p.uniqueAssetName(r.file)
            p.writeAsset(n, "// ${r.title} — ${r.description}\n" + r.code.trim() + "\n")
            toast("Created $n in Assets — attach it with a Script component")
        }, lp(WRAP, WRAP).margins(dp(8), 0, 0, 0))
        content.addView(row)
        contentScroll.scrollTo(0, 0)
    }

    // ---------------------------------------------------------------- doctor
    private fun runDoctor() {
        nav.removeAllViews()
        nav.addView(sectionHeader("doctor", "Game Doctor"))
        nav.addView(label("Scans scenes, scripts and assets for problems and offers automatic fixes.", 12f, C.DIM).apply { setPadding(dp(6), 0, dp(6), dp(8)) })
        nav.addView(iconTextButton("refresh", "Run again", C.ACCENT, 0xFFFFFFFF.toInt()) { runDoctor() }, lp(MATCH, WRAP))
        content.removeAllViews()
        val p = project
        if (p == null) { content.addView(label("Open Help from a project's editor to run the Game Doctor on it.", 14f, C.DIM)); return }
        content.addView(label("Checking ${p.name}…", 16f, C.TEXT, true))
        thread(name = "game-doctor") {
            val issues = try { GameDoctor.check(p) } catch (e: Exception) { listOf(GameDoctor.Issue(2, "Doctor failed", e.toString())) }
            runOnUiThread { showIssues(p, issues) }
        }
    }

    private fun showIssues(p: Project, issues: List<GameDoctor.Issue>) {
        content.removeAllViews()
        val errors = issues.count { it.severity == 2 }; val warns = issues.count { it.severity == 1 }
        val head = hbox()
        head.addView(label(if (errors + warns == 0) "✔ Healthy project" else "$errors errors • $warns warnings", 22f, if (errors > 0) C.RED else if (warns > 0) C.YELLOW else C.GREEN, true), lp(0, WRAP, 1f))
        val fixable = issues.filter { it.fix != null }
        if (fixable.isNotEmpty()) head.addView(iconTextButton("wand", "Fix all (${fixable.size})", C.PURPLE, 0xFFFFFFFF.toInt()) {
            MaterialAlertDialogBuilder(this).setTitle("Apply ${fixable.size} automatic fixes?")
                .setPositiveButton("Fix all") { _, _ -> var n = 0; for (i in fixable) try { i.fix!!.invoke(); n++ } catch (_: Exception) {}; toast("Applied $n fixes"); runDoctor() }
                .setNegativeButton("Cancel", null).show()
        })
        content.addView(head, lp(MATCH, WRAP).margins(0, 0, 0, dp(10)))
        for (i in issues) {
            val card = card(C.PANEL)
            val r = hbox()
            val (ic, col) = when (i.severity) { 2 -> "close" to C.RED; 1 -> "info" to C.YELLOW; else -> "check" to C.GREEN }
            r.addView(android.widget.ImageView(this).apply { setImageDrawable(Icons.drawable(this@HelpActivity, ic, col, 20)) }, lp(dp(28), dp(28)))
            val tb = vbox()
            tb.addView(label(i.title, 14f, C.TEXT, true))
            tb.addView(label(i.detail, 12f, C.DIM).apply { setTextIsSelectable(true) })
            r.addView(tb, lp(0, WRAP, 1f).margins(dp(8), 0, dp(8), 0))
            if (i.fix != null) r.addView(button(i.fixLabel ?: "Fix", C.ACCENT, 0xFFFFFFFF.toInt()) {
                try { i.fix?.invoke(); toast("Fixed"); runDoctor() } catch (e: Exception) { toast("Fix failed: ${e.message}") }
            }.apply { textSize = 12f })
            card.addView(r)
            content.addView(card, lp(MATCH, WRAP).margins(0, dp(4), 0, dp(4)))
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
