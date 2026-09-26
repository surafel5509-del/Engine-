package com.sengine.ui

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sengine.agent.AgentConfig
import com.sengine.agent.ChatMessage
import com.sengine.agent.LlmClient
import com.sengine.agent.ModelProfile
import com.sengine.agent.ProviderKind
import java.io.File
import kotlin.concurrent.thread

/** Manage AI model profiles: your own API keys for OpenAI, Claude, Gemini, OpenRouter, Groq, DeepSeek, Mistral, Grok, local servers… */
class AgentSettingsActivity : AppCompatActivity() {
    private lateinit var config: AgentConfig
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = AgentConfig(File(filesDir, "agent/config.json"))
        val root = vbox().apply { setBackgroundColor(C.BG) }
        val bar = hbox().apply { setBackgroundColor(C.HEADER); setPadding(dp(6), dp(5), dp(10), dp(5)); gravity = Gravity.CENTER_VERTICAL }
        bar.addView(iconButton("back", "Back", sizeDp = 36) { finish() })
        bar.addView(label("Agent Settings · AI Models", 16f, C.TEXT, true).apply { setPadding(dp(10), 0, 0, 0) }, lp(0, WRAP, 1f))
        bar.addView(iconTextButton("plus", "Add model", C.ACCENT, 0xFF000000.toInt()) { edit(null) })
        root.addView(bar, lp(MATCH, WRAP))
        val content = vbox().apply { setPadding(dp(16), dp(12), dp(16), dp(24)) }
        content.addView(label("Your keys are stored only on this device (app-private storage) and sent only to the provider you choose.", 12f, C.DIM))
        list = vbox()
        content.addView(list, lp(MATCH, WRAP).margins(0, dp(10), 0, 0))
        content.addView(sectionHeader("sliders", "Agent behaviour"), lp(MATCH, WRAP).margins(0, dp(16), 0, dp(4)))
        val stepsRow = hbox().apply { gravity = Gravity.CENTER_VERTICAL }
        stepsRow.addView(label("Max agent steps (model calls)", 13f), lp(0, WRAP, 1f))
        val stepsField = field(config.maxSteps.toString(), numeric = true)
        stepsRow.addView(stepsField, lp(dp(90), WRAP))
        stepsRow.addView(button("Save") { config.maxSteps = stepsField.text.toString().toIntOrNull()?.coerceIn(5, 300) ?: 60; config.save(); toast("Saved") }, lp(WRAP, WRAP).margins(dp(8), 0, 0, 0))
        content.addView(stepsRow)
        root.addView(ScrollView(this).apply { addView(content) }, lp(MATCH, 0, 1f))
        setContentView(root)
        render()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun render() {
        list.removeAllViews()
        for (p in config.profiles) {
            val active = p.id == config.activeId
            val card = card(if (active) C.PANEL2 else C.PANEL).apply {
                background = round(if (active) C.PANEL2 else C.PANEL, dp(12).toFloat(), if (active) 2 else 1, if (active) C.ACCENT else C.BORDER)
                setPadding(dp(14), dp(10), dp(10), dp(10))
            }
            val row = hbox().apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(android.widget.ImageView(this).apply { setImageDrawable(Icons.drawable(this@AgentSettingsActivity, if (p.kind == ProviderKind.LOCAL) "chip" else "robot", C.TEXT, 22)) }, lp(dp(28), dp(28)))
            row.addView(vbox().apply {
                addView(label(p.name + if (active) "   (active)" else "", 14f, C.TEXT, true))
                addView(label(if (p.kind == ProviderKind.LOCAL) "Built-in rules, works offline — re-skins the closest complete game" else "${p.kind.title} · ${p.model} · key ${if (p.apiKey.isBlank()) "not set" else "••••" + p.apiKey.takeLast(4)}", 11.5f, C.DIM))
            }, lp(0, WRAP, 1f).margins(dp(10), 0, 0, 0))
            if (!active) row.addView(button("Use") { config.activeId = p.id; config.save(); render() }, lp(WRAP, WRAP).margins(dp(4), 0, 0, 0))
            if (p.kind != ProviderKind.LOCAL) row.addView(iconButton("wrench", "Edit", sizeDp = 34) { edit(p) }, lp(WRAP, WRAP).margins(dp(4), 0, 0, 0))
            card.addView(row)
            list.addView(card, lp(MATCH, WRAP).margins(0, 0, 0, dp(8)))
        }
    }

    private fun edit(existing: ModelProfile?) {
        val p = existing ?: ModelProfile(name = "New model")
        val form = vbox().apply { setPadding(dp(20), dp(8), dp(20), 0) }
        fun row(title: String, v: View) { form.addView(label(title, 12f, C.DIM), lp(MATCH, WRAP).margins(0, dp(8), 0, dp(2))); form.addView(v, lp(MATCH, WRAP)) }
        val kinds = ProviderKind.values().filter { it != ProviderKind.LOCAL }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@AgentSettingsActivity, android.R.layout.simple_spinner_dropdown_item, kinds.map { it.title })
            setSelection(kinds.indexOf(p.kind).coerceAtLeast(0))
            background = round(C.FIELD, dp(8).toFloat(), 1, C.BORDER)
        }
        val name = field(p.name); val base = field(p.baseUrl); val model = field(p.model)
        val key = field(p.apiKey).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; hint = "sk-… / AIza… / key" ; setHintTextColor(C.DIM) }
        val temp = field(p.temperature.toString(), numeric = true); val maxTok = field(p.maxTokens.toString(), numeric = true)
        var firstSel = true
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (firstSel && existing != null) { firstSel = false; return }
                firstSel = false
                val k = kinds[pos]; base.setText(k.defaultBase); model.setText(k.defaultModel)
                if (name.text.isBlank() || name.text.toString() == "New model" || ProviderKind.values().any { it.title == name.text.toString() }) name.setText(k.title)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        row("Provider", spinner); row("Display name", name); row("API key", key); row("Model", model)
        row("Base URL (change for proxies / local servers)", base)
        val two = hbox(); two.addView(vbox().apply { addView(label("Temperature", 12f, C.DIM)); addView(temp) }, lp(0, WRAP, 1f).margins(0, dp(8), dp(8), 0))
        two.addView(vbox().apply { addView(label("Max tokens", 12f, C.DIM)); addView(maxTok) }, lp(0, WRAP, 1f).margins(0, dp(8), 0, 0)); form.addView(two)
        val status = label("", 12f, C.DIM); form.addView(status, lp(MATCH, WRAP).margins(0, dp(8), 0, 0))
        fun collect(): ModelProfile {
            p.kind = kinds[spinner.selectedItemPosition]; p.name = name.text.toString().ifBlank { p.kind.title }
            p.baseUrl = base.text.toString().trim(); p.model = model.text.toString().trim(); p.apiKey = key.text.toString().trim()
            p.temperature = temp.text.toString().toFloatOrNull()?.coerceIn(0f, 2f) ?: 0.4f
            p.maxTokens = maxTok.text.toString().toIntOrNull()?.coerceIn(256, 64000) ?: 4096
            return p
        }
        val d = MaterialAlertDialogBuilder(this).setTitle(if (existing == null) "Add AI model" else "Edit ${p.name}")
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton("Save", null).setNeutralButton("Test", null)
            .apply { if (existing != null) setNegativeButton("Delete") { _, _ -> config.profiles.remove(existing); config.save(); config.load(); render() } }
            .show()
        d.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val np = collect()
            if (existing == null) config.profiles.add(np)
            if (existing == null) config.activeId = np.id
            config.save(); render(); d.dismiss()
        }
        d.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
            val np = collect()
            config.validate(np)?.let { status.text = it; status.setTextColor(C.RED); return@setOnClickListener }
            status.text = "Testing…"; status.setTextColor(C.DIM)
            thread {
                val msg = try {
                    val r = LlmClient(np, 60_000).complete("Reply with exactly: OK", listOf(ChatMessage("user", "ping")))
                    "✓ Connected: " + r.take(60)
                } catch (e: Exception) { "✗ " + (e.message ?: "failed") }
                runOnUiThread { status.text = msg; status.setTextColor(if (msg.startsWith("✓")) C.GREEN else C.RED) }
            }
        }
    }
}
