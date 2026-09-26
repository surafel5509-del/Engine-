package com.sengine.ui

import android.content.Context
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Spinner
import com.sengine.engine.core.Component
import com.sengine.engine.core.Prop

/** Generic property editor used by the v3 studios (UI Creator etc.). Calls [changed] after every edit. */
object PropForm {
    fun build(ctx: Context, c: Component, into: LinearLayout, changed: () -> Unit) {
        with(ctx) {
            into.addView(label(c.type, 13f, C.ACCENT, true), lp(MATCH, WRAP).margins(0, dp(10), 0, dp(4)))
            for (p in c.props()) {
                val row = hbox().apply { gravity = Gravity.CENTER_VERTICAL }
                row.addView(label(p.name, 12f, C.DIM), lp(dp(96), WRAP))
                val editor: android.view.View = when (p) {
                    is Prop.F -> field(fmt(p.get()), numeric = true).apply { onDone { v -> v.toFloatOrNull()?.let { p.set(it); changed() } } }
                    is Prop.I -> field(p.get().toString(), numeric = true).apply { onDone { v -> v.toIntOrNull()?.let { p.set(it); changed() } } }
                    is Prop.S -> field(p.get()).apply { onDone { v -> p.set(v); changed() } }
                    is Prop.Asset -> field(p.get()).apply { hint = p.kind.name.lowercase(); setHintTextColor(C.DIM); onDone { v -> p.set(v); changed() } }
                    is Prop.B -> CheckBox(ctx).apply { isChecked = p.get(); buttonTintList = android.content.res.ColorStateList.valueOf(C.ACCENT); setOnCheckedChangeListener { _, b -> p.set(b); changed() } }
                    is Prop.Color -> android.view.View(ctx).apply {
                        background = round(p.get(), dp(6).toFloat(), 1, C.BORDER)
                        setOnClickListener { ColorPickerDialog.show(ctx, p.get()) { col -> p.set(col); background = round(col, dp(6).toFloat(), 1, C.BORDER); changed() } }
                    }
                    is Prop.Choice -> Spinner(ctx).apply {
                        adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, p.options)
                        setSelection(p.get().coerceIn(0, p.options.size - 1))
                        onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(a: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) { if (pos != p.get()) { p.set(pos); changed() } }
                            override fun onNothingSelected(a: android.widget.AdapterView<*>?) {}
                        }
                    }
                }
                row.addView(editor, if (p is Prop.Color) lp(0, dp(30), 1f) else lp(0, WRAP, 1f))
                into.addView(row, lp(MATCH, WRAP).margins(0, dp(2), 0, dp(2)))
            }
        }
    }

    private fun android.widget.EditText.onDone(apply: (String) -> Unit) {
        setOnFocusChangeListener { _, has -> if (!has) apply(text.toString()) }
        setOnEditorActionListener { _, _, _ -> apply(text.toString()); false }
    }
}
