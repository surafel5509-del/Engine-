package com.sengine.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

object C {
    const val BG = 0xFF0E1120.toInt()
    const val PANEL = 0xFF161A2D.toInt()
    const val PANEL2 = 0xFF232A47.toInt()
    const val HEADER = 0xFF111427.toInt()
    const val FIELD = 0xFF0A0D19.toInt()
    const val ACCENT = 0xFF5B7CFF.toInt()
    const val ACCENT2 = 0xFF22D3EE.toInt()
    const val TEXT = 0xFFE8EAF6.toInt()
    const val DIM = 0xFF8F96B8.toInt()
    const val SEL = 0xFF2E3A78.toInt()
    const val RED = 0xFFFF5C6C.toInt()
    const val GREEN = 0xFF34D399.toInt()
    const val YELLOW = 0xFFFBBF24.toInt()
    const val ORANGE = 0xFFFF9F43.toInt()
    const val PURPLE = 0xFFA78BFA.toInt()
    const val PINK = 0xFFF472B6.toInt()
    const val BORDER = 0xFF2A3154.toInt()
}

fun Context.dp(v: Number): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

fun round(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color); cornerRadius = radius
        if (stroke > 0) setStroke(stroke, strokeColor)
    }

fun Context.label(text: String, size: Float = 13f, color: Int = C.TEXT, bold: Boolean = false): TextView =
    TextView(this).apply {
        this.text = text
        setTextColor(color)
        textSize = size
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

/** Flat rounded button with ripple. */
fun Context.button(text: String, color: Int = C.PANEL2, textColor: Int = C.TEXT, onClick: (View) -> Unit): TextView =
    TextView(this).apply {
        this.text = text
        setTextColor(textColor)
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(6), dp(12), dp(6))
        minWidth = dp(40)
        background = RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), round(color, dp(8).toFloat()), null)
        isClickable = true
        isFocusable = true
        setOnClickListener(onClick)
    }

fun TextView.setButtonColor(color: Int) {
    background = RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), round(color, context.dp(8).toFloat()), null)
}

fun Context.field(value: String, numeric: Boolean = false, multiline: Boolean = false): EditText =
    EditText(this).apply {
        setText(value)
        setTextColor(C.TEXT)
        textSize = 13f
        setPadding(dp(6), dp(4), dp(6), dp(4))
        background = round(C.FIELD, dp(6).toFloat(), 1, C.BORDER)
        inputType = when {
            numeric -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        if (!multiline) {
            isSingleLine = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        setSelectAllOnFocus(numeric)
    }

fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun LinearLayout.LayoutParams.margins(l: Int, t: Int, r: Int, b: Int) = apply { setMargins(l, t, r, b) }

fun Context.vbox(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
fun Context.hbox(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
}

fun fmt(v: Float): String {
    if (v == Math.round(v).toFloat() && kotlin.math.abs(v) < 1e7) return Math.round(v).toString()
    return String.format(java.util.Locale.US, "%.3f", v).trimEnd('0').trimEnd('.')
}

/** Left-to-right gradient rounded rectangle. */
fun gradient(c1: Int, c2: Int, radius: Float, orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.TL_BR): GradientDrawable =
    GradientDrawable(orientation, intArrayOf(c1, c2)).apply { cornerRadius = radius }

/** Square icon button with ripple, tooltip and accessibility description. */
fun Context.iconButton(icon: String, desc: String, tint: Int = C.TEXT, bg: Int = C.PANEL2, sizeDp: Int = 40, onClick: (View) -> Unit): android.widget.ImageView =
    android.widget.ImageView(this).apply {
        setImageDrawable(Icons.drawable(this@iconButton, icon, tint, (sizeDp * 0.55f).toInt()))
        scaleType = android.widget.ImageView.ScaleType.CENTER
        contentDescription = desc
        tooltipText = desc
        background = RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), round(bg, dp(10).toFloat()), null)
        isClickable = true; isFocusable = true
        setOnClickListener(onClick)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

fun android.widget.ImageView.setIconTint(icon: String, tint: Int, sizeDp: Int = 22) {
    setImageDrawable(Icons.drawable(context, icon, tint, sizeDp))
}

fun android.widget.ImageView.setBg(bg: Int) {
    background = RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), round(bg, context.dp(10).toFloat()), null)
}

/** Button with a leading icon and a label. */
fun Context.iconTextButton(icon: String, text: String, color: Int = C.PANEL2, tint: Int = C.TEXT, onClick: (View) -> Unit): TextView =
    button(text, color, tint, onClick).apply {
        setCompoundDrawables(Icons.drawable(this@iconTextButton, icon, tint, 18), null, null, null)
        compoundDrawablePadding = dp(6)
        textSize = 13f
    }

/** Rounded card container. */
fun Context.card(color: Int = C.PANEL, radiusDp: Int = 14): LinearLayout = vbox().apply {
    background = round(color, dp(radiusDp).toFloat(), 1, C.BORDER)
    setPadding(dp(12), dp(10), dp(12), dp(10))
}

/** Small section header with an icon. */
fun Context.sectionHeader(icon: String, text: String, tint: Int = C.ACCENT2): TextView =
    label(text.uppercase(), 11f, C.DIM, true).apply {
        setCompoundDrawables(Icons.drawable(this@sectionHeader, icon, tint, 14), null, null, null)
        compoundDrawablePadding = dp(6)
        letterSpacing = 0.08f
        setPadding(dp(4), dp(10), dp(4), dp(4))
    }
