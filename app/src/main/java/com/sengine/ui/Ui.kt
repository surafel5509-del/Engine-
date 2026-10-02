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

/**
 * S Engine 7 "Studio" palette — modelled on the Unity editor: graphite chrome, a working-blue
 * accent and clear status colours. Values are `var` so [Themes] can hot-swap the entire IDE
 * between dark / light at runtime.
 */
object C {
    // ---- surfaces
    var BG = 0xFF1B1B1D.toInt()        // app root / deepest level
    var PANEL = 0xFF27272B.toInt()     // side panels (hierarchy, inspector)
    var PANEL2 = 0xFF3A3A40.toInt()    // cards, raised buttons
    var HEADER = 0xFF414147.toInt()    // toolbar, panel headers, tabs
    var FIELD = 0xFF202024.toInt()     // text inputs
    // ---- accents
    var ACCENT = 0xFF4C80E0.toInt()    // Unity working blue
    var ACCENT2 = 0xFF7EB1F2.toInt()   // lighter blue for icons on dark
    var TEXT = 0xFFD2D2D6.toInt()
    var DIM = 0xFF8B8B93.toInt()
    var SEL = 0xFF3F5D82.toInt()       // Unity-style selection blue
    var BORDER = 0xFF111114.toInt()
    // ---- status
    var RED = 0xFFE5534B.toInt()
    var GREEN = 0xFF57AB5A.toInt()
    var YELLOW = 0xFFE3B341.toInt()
    var ORANGE = 0xFFE0823D.toInt()
    var PURPLE = 0xFFB083F0.toInt()
    var PINK = 0xFFEC8FC3.toInt()

    /** Play-mode tint for the toolbar (Unity tints the editor while playing). */
    val PLAY_TINT = 0xFF2A4116.toInt()

    /** True when [c] is a light colour (text on it must be dark). */
    fun isLight(c: Int): Boolean {
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b) > 170 && ((c ushr 24) > 0x80)
    }
    /** Readable foreground for a background: black on light, the requested colour otherwise. */
    fun on(bg: Int, fg: Int): Int = if (isLight(bg) && isLight(fg)) 0xFF000000.toInt() else fg
}

/** Runtime theme store (Unity-style dark editor by default, light / midnight variants too). */
object Themes {
    const val KEY = "sengine.theme"
    const val DARK = 0
    const val LIGHT = 1
    const val MIDNIGHT = 2

    val names = arrayOf("Unity Dark", "Light", "Midnight")

    fun current(ctx: Context): Int =
        ctx.getSharedPreferences("sengine", Context.MODE_PRIVATE).getInt(KEY, DARK)

    fun apply(ctx: Context, theme: Int) {
        when (theme) {
            LIGHT -> {
                C.BG = 0xFFF1F1F4.toInt(); C.PANEL = 0xFFE7E7EC.toInt(); C.PANEL2 = 0xFFD6D6DD.toInt()
                C.HEADER = 0xFFDBDBE2.toInt(); C.FIELD = 0xFFFFFFFF.toInt()
                C.ACCENT = 0xFF2F6BD8.toInt(); C.ACCENT2 = 0xFF3D6FBE.toInt()
                C.TEXT = 0xFF1D1D21.toInt(); C.DIM = 0xFF67676F.toInt(); C.SEL = 0xFFBFD4F2.toInt(); C.BORDER = 0xFFB9B9C2.toInt()
            }
            MIDNIGHT -> {
                C.BG = 0xFF0A0C10.toInt(); C.PANEL = 0xFF10131A.toInt(); C.PANEL2 = 0xFF1B2029.toInt()
                C.HEADER = 0xFF161A22.toInt(); C.FIELD = 0xFF0D0F14.toInt()
                C.ACCENT = 0xFF3D9BE9.toInt(); C.ACCENT2 = 0xFF6FC3FF.toInt()
                C.TEXT = 0xFFC9D4E2.toInt(); C.DIM = 0xFF6C7A8C.toInt(); C.SEL = 0xFF24425F.toInt(); C.BORDER = 0xFF060809.toInt()
            }
            else -> {
                C.BG = 0xFF1B1B1D.toInt(); C.PANEL = 0xFF27272B.toInt(); C.PANEL2 = 0xFF3A3A40.toInt()
                C.HEADER = 0xFF414147.toInt(); C.FIELD = 0xFF202024.toInt()
                C.ACCENT = 0xFF4C80E0.toInt(); C.ACCENT2 = 0xFF7EB1F2.toInt()
                C.TEXT = 0xFFD2D2D6.toInt(); C.DIM = 0xFF8B8B93.toInt(); C.SEL = 0xFF3F5D82.toInt(); C.BORDER = 0xFF111114.toInt()
            }
        }
        ctx.getSharedPreferences("sengine", Context.MODE_PRIVATE).edit().putInt(KEY, theme).apply()
    }

    /** Applies the saved theme and returns its id. */
    fun restore(ctx: Context): Int { val t = current(ctx); apply(ctx, t); return t }
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
