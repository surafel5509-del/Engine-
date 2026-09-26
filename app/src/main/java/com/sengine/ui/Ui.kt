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

/** Monochrome "Noir" palette: pure black surfaces, white accents, status colours kept subtle. */
object C {
    const val BG = 0xFF000000.toInt()
    const val PANEL = 0xFF0F0F0F.toInt()
    const val PANEL2 = 0xFF1E1E1E.toInt()
    const val HEADER = 0xFF080808.toInt()
    const val FIELD = 0xFF050505.toInt()
    const val ACCENT = 0xFFF5F5F5.toInt()
    const val ACCENT2 = 0xFFBDBDBD.toInt()
    const val TEXT = 0xFFF2F2F2.toInt()
    const val DIM = 0xFF8C8C8C.toInt()
    const val SEL = 0xFF3A3A3A.toInt()
    const val RED = 0xFFFF5A5A.toInt()
    const val GREEN = 0xFF6EE7A8.toInt()
    const val YELLOW = 0xFFF5D06B.toInt()
    const val ORANGE = 0xFFFFA566.toInt()
    const val PURPLE = 0xFFC4B5FD.toInt()
    const val PINK = 0xFFF9A8D4.toInt()
    const val BORDER = 0xFF2B2B2B.toInt()

    /** True when [c] is a light colour (text on it must be dark). */
    fun isLight(c: Int): Boolean {
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b) > 170 && ((c ushr 24) > 0x80)
    }
    /** Readable foreground for a background: black on light, the requested colour otherwise. */
    fun on(bg: Int, fg: Int): Int = if (isLight(bg) && isLight(fg)) 0xFF000000.toInt() else fg
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
        setTextColor(C.on(color, textColor))
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(6), dp(12), dp(6))
        minWidth = dp(40)
        background = RippleDrawable(ColorStateList.valueOf(if (C.isLight(color)) 0x33000000 else 0x44FFFFFF), round(color, dp(8).toFloat(), if (C.isLight(color)) 0 else 1, C.BORDER), null)
        isClickable = true
        isFocusable = true
        setOnClickListener(onClick)
    }

fun TextView.setButtonColor(color: Int) {
    background = RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), round(color, context.dp(8).toFloat(), if (C.isLight(color)) 0 else 1, C.BORDER), null)
    setTextColor(if (C.isLight(color)) 0xFF000000.toInt() else C.TEXT)
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
        setImageDrawable(Icons.drawable(this@iconButton, icon, C.on(bg, tint), (sizeDp * 0.55f).toInt()))
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
        setCompoundDrawables(Icons.drawable(this@iconTextButton, icon, C.on(color, tint), 18), null, null, null)
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
