package com.sengine.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.sengine.engine.Input
import com.sengine.engine.controls.ControlDef
import com.sengine.engine.controls.ControlLayout
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * Data-driven virtual game controller (joysticks, buttons, D-pads, steering arrows, look pads).
 * Touches that don't hit a control fall through to the game view. In [editing] mode the controls
 * can be selected and dragged (used by the Controls Editor).
 */
class GameControlsView(context: Context, private val input: () -> Input?) : View(context) {

    var layout: ControlLayout = ControlLayout.default()
        set(v) { field = v; reset() }
    /** Layout restored by input.setControls("project"). */
    var projectLayout: ControlLayout = ControlLayout.default()
        set(v) { field = v; layout = v.copy() }
    private var hiddenByScript = false
    private val poll = object : Runnable {
        override fun run() {
            val inp = input()
            if (inp != null && !editing) {
                inp.controlsRequest?.let { req ->
                    inp.controlsRequest = null
                    val r = req.trim().lowercase()
                    layout = when {
                        r == "project" || r == "default" || r.isEmpty() -> projectLayout.copy()
                        r == "none" || r == "hidden" -> ControlLayout("None")
                        else -> (ControlLayout.presets.firstOrNull { it.name.lowercase().startsWith(r) }
                            ?: ControlLayout.presets.firstOrNull { it.name.lowercase().contains(r) } ?: projectLayout).copy()
                    }
                }
                if (inp.controlsHidden != hiddenByScript) { hiddenByScript = inp.controlsHidden; if (hiddenByScript) reset(); invalidate() }
            }
            postDelayed(this, 150)
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); post(poll) }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); removeCallbacks(poll) }
    var editing = false
    var selected = -1
        set(v) { field = v; invalidate() }
    var onSelect: ((Int) -> Unit)? = null
    var onChanged: (() -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEEFFFFFF.toInt(); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val dash = DashPathEffect(floatArrayOf(14f, 10f), 0f)
    private val rect = RectF()

    private class Touch(val control: Int, var x: Float, var y: Float, val downTime: Long, val sx: Float, val sy: Float, var dir: Int = -1)
    private val touches = HashMap<Int, Touch>()
    private var dragOffX = 0f
    private var dragOffY = 0f

    private val minDim get() = min(width, height).toFloat()
    private fun cx(c: ControlDef) = c.x * width
    private fun cy(c: ControlDef) = c.y * height
    private fun radius(c: ControlDef) = c.size * minDim / 2f

    private fun lookRect(c: ControlDef): RectF {
        val hw = c.size * width / 2f; val hh = c.size * height / 2f
        return RectF(cx(c) - hw, cy(c) - hh, cx(c) + hw, cy(c) + hh)
    }

    private fun steerCenters(c: ControlDef): Pair<Float, Float> { val r = radius(c); return (cx(c) - r * 1.25f) to (cx(c) + r * 1.25f) }

    /** Index of the control under a point, preferring small controls over look pads. */
    private fun hit(x: Float, y: Float): Int {
        var best = -1; var bestD = Float.MAX_VALUE
        layout.controls.forEachIndexed { i, c ->
            val r = radius(c)
            val d = when (c.type) {
                "look" -> null
                "steer" -> { val (l, rr) = steerCenters(c); val dd = min(hypot(x - l, y - cy(c)), hypot(x - rr, y - cy(c))); if (dd < r * 1.3f) dd else null }
                "joystick" -> hypot(x - cx(c), y - cy(c)).takeIf { it < r * 1.45f }
                "dpad" -> hypot(x - cx(c), y - cy(c)).takeIf { it < r * 1.3f }
                else -> hypot(x - cx(c), y - cy(c)).takeIf { it < r * 1.25f }
            }
            if (d != null && d < bestD) { bestD = d; best = i }
        }
        if (best >= 0) return best
        layout.controls.forEachIndexed { i, c -> if (c.type == "look" && lookRect(c).contains(x, y)) return i }
        return -1
    }

    override fun onDraw(canvas: Canvas) {
        if (hiddenByScript && !editing) return
        if (editing) drawGrid(canvas)
        layout.controls.forEachIndexed { i, c ->
            val active = touches.values.firstOrNull { it.control == i }
            when (c.type) {
                "joystick" -> drawJoystick(canvas, c, active)
                "dpad" -> drawDpad(canvas, c, active)
                "steer" -> drawSteer(canvas, c, active)
                "look" -> if (editing) {
                    val r = lookRect(c)
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.pathEffect = dash; paint.color = 0x88FFFFFF.toInt()
                    canvas.drawRoundRect(r, 24f, 24f, paint); paint.pathEffect = null; paint.style = Paint.Style.FILL
                    paint.color = 0x14FFFFFF; canvas.drawRoundRect(r, 24f, 24f, paint)
                    text.textSize = minDim * 0.035f; canvas.drawText("LOOK PAD (${c.id})", r.centerX(), r.centerY(), text)
                }
                else -> drawButton(canvas, c, active != null)
            }
            if (editing && i == selected) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f; paint.color = 0xFFFFD33D.toInt()
                if (c.type == "look") canvas.drawRoundRect(lookRect(c), 24f, 24f, paint)
                else canvas.drawCircle(cx(c), cy(c), radius(c) * (if (c.type == "steer") 2.4f else 1.12f), paint)
                paint.style = Paint.Style.FILL
            }
        }
    }

    private fun drawGrid(canvas: Canvas) {
        paint.color = 0x22FFFFFF; paint.strokeWidth = 1f
        for (i in 1 until 10) {
            canvas.drawLine(width * i / 10f, 0f, width * i / 10f, height.toFloat(), paint)
            canvas.drawLine(0f, height * i / 10f, width.toFloat(), height * i / 10f, paint)
        }
    }

    private fun alpha(color: Int, a: Float) = (((a.coerceIn(0f, 1f) * 255).toInt()) shl 24) or (color and 0xFFFFFF)

    private fun drawButton(canvas: Canvas, c: ControlDef, pressed: Boolean) {
        val x = cx(c); val y = cy(c); val r = radius(c) * (if (pressed) 0.94f else 1f)
        paint.style = Paint.Style.FILL
        paint.color = alpha(0x000000, c.opacity * 0.5f); canvas.drawCircle(x, y + r * 0.08f, r, paint)
        paint.color = alpha(c.color, if (pressed) (c.opacity + 0.35f) else c.opacity); canvas.drawCircle(x, y, r, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = r * 0.08f
        paint.color = alpha(0xFFFFFF, if (pressed) 0.9f else 0.45f); canvas.drawCircle(x, y, r * 0.94f, paint)
        paint.style = Paint.Style.FILL
        val white = alpha(0xFFFFFF, 0.95f)
        if (c.icon.isNotEmpty()) {
            Icons.draw(canvas, c.icon, x, y - (if (c.label.isNotEmpty()) r * 0.12f else 0f), r * 0.95f, white, iconPaint)
            if (c.label.isNotEmpty()) { text.textSize = r * 0.3f; canvas.drawText(c.label, x, y + r * 0.62f, text) }
        } else {
            val l = c.label.ifEmpty { c.id }
            text.textSize = if (l.length <= 2) r * 0.8f else r * 0.36f
            canvas.drawText(l, x, y + text.textSize * 0.35f, text)
        }
    }

    private fun drawJoystick(canvas: Canvas, c: ControlDef, t: Touch?) {
        val x = cx(c); val y = cy(c); val r = radius(c)
        paint.style = Paint.Style.FILL
        paint.color = alpha(0x000000, c.opacity * 0.35f); canvas.drawCircle(x, y, r, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = r * 0.05f
        paint.color = alpha(c.color, c.opacity + 0.2f); canvas.drawCircle(x, y, r, paint)
        paint.color = alpha(0xFFFFFF, 0.18f); canvas.drawCircle(x, y, r * 0.62f, paint)
        paint.style = Paint.Style.FILL
        // direction ticks
        paint.color = alpha(0xFFFFFF, 0.35f)
        for (k in 0 until 4) {
            val a = Math.toRadians(k * 90.0)
            canvas.drawCircle(x + (Math.cos(a) * r * 0.82).toFloat(), y + (Math.sin(a) * r * 0.82).toFloat(), r * 0.035f, paint)
        }
        var kx = x; var ky = y
        if (t != null) {
            var dx = t.x - x; var dy = t.y - y
            val d = hypot(dx, dy); if (d > r) { dx = dx / d * r; dy = dy / d * r }
            kx += dx; ky += dy
        }
        paint.color = alpha(c.color, if (t != null) 0.95f else 0.75f); canvas.drawCircle(kx, ky, r * 0.42f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = r * 0.04f; paint.color = alpha(0xFFFFFF, 0.7f)
        canvas.drawCircle(kx, ky, r * 0.42f, paint); paint.style = Paint.Style.FILL
        if (c.icon.isNotEmpty()) Icons.draw(canvas, c.icon, kx, ky, r * 0.46f, alpha(0xFFFFFF, 0.9f), iconPaint)
    }

    private fun drawDpad(canvas: Canvas, c: ControlDef, t: Touch?) {
        val x = cx(c); val y = cy(c); val r = radius(c); val w = r * 0.36f
        paint.style = Paint.Style.FILL
        for (k in 0 until 4) {
            val on = t != null && t.dir == k
            paint.color = alpha(c.color, if (on) c.opacity + 0.35f else c.opacity)
            when (k) {
                0 -> rect.set(x + w * 0.9f, y - w, x + r, y + w)
                1 -> rect.set(x - w, y - r, x + w, y - w * 0.9f)
                2 -> rect.set(x - r, y - w, x - w * 0.9f, y + w)
                else -> rect.set(x - w, y + w * 0.9f, x + w, y + r)
            }
            canvas.drawRoundRect(rect, w * 0.4f, w * 0.4f, paint)
            val icon = arrayOf("forward", "upload", "back", "download")[k]
            val ix = rect.centerX(); val iy = rect.centerY()
            if (k == 1 || k == 3) {
                paint.color = alpha(0xFFFFFF, 0.9f)
                val s = w * 0.55f; val dir = if (k == 1) -1 else 1
                val p = android.graphics.Path().apply { moveTo(ix - s, iy - dir * s * 0.5f); lineTo(ix + s, iy - dir * s * 0.5f); lineTo(ix, iy + dir * s * 0.7f); close() }
                canvas.drawPath(p, paint)
            } else Icons.draw(canvas, if (k == 0) "play" else "back", ix, iy, w * 1.2f, alpha(0xFFFFFF, 0.9f), iconPaint)
            @Suppress("UNUSED_VARIABLE") val unused = icon
        }
        paint.color = alpha(c.color, c.opacity * 0.7f); canvas.drawCircle(x, y, w * 0.9f, paint)
    }

    private fun drawSteer(canvas: Canvas, c: ControlDef, t: Touch?) {
        val (l, rr) = steerCenters(c); val y = cy(c); val r = radius(c)
        for ((k, px) in listOf(l, rr).withIndex()) {
            val on = t != null && t.dir == k
            paint.style = Paint.Style.FILL
            paint.color = alpha(c.color, if (on) c.opacity + 0.35f else c.opacity); canvas.drawCircle(px, y, r, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = r * 0.07f; paint.color = alpha(0xFFFFFF, if (on) 0.9f else 0.45f)
            canvas.drawCircle(px, y, r * 0.94f, paint); paint.style = Paint.Style.FILL
            paint.color = alpha(0xFFFFFF, 0.95f)
            val s = r * 0.42f; val d = if (k == 0) -1 else 1
            val p = android.graphics.Path().apply { moveTo(px + d * s, y); lineTo(px - d * s * 0.6f, y - s); lineTo(px - d * s * 0.6f, y + s); close() }
            canvas.drawPath(p, paint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean = if (editing) editTouch(e) else playTouch(e)

    private fun editTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val i = hit(e.x, e.y)
                selected = i; onSelect?.invoke(i)
                if (i >= 0) { val c = layout.controls[i]; dragOffX = cx(c) - e.x; dragOffY = cy(c) - e.y }
            }
            MotionEvent.ACTION_MOVE -> if (selected >= 0 && width > 0) {
                val c = layout.controls[selected]
                c.x = (((e.x + dragOffX) / width) * 100).toInt() / 100f
                c.y = (((e.y + dragOffY) / height) * 100).toInt() / 100f
                c.x = c.x.coerceIn(0f, 1f); c.y = c.y.coerceIn(0f, 1f)
                invalidate()
            }
            MotionEvent.ACTION_UP -> onChanged?.invoke()
        }
        return true
    }

    private fun playTouch(e: MotionEvent): Boolean {
        val inp = input() ?: return false
        if (hiddenByScript && e.actionMasked == MotionEvent.ACTION_DOWN) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex; val id = e.getPointerId(i); val x = e.getX(i); val y = e.getY(i)
                val c = if (inp.overUI(x, y) || hiddenByScript) -1 else hit(x, y)
                if (c < 0) {
                    if (e.actionMasked == MotionEvent.ACTION_DOWN) return false
                    // secondary finger on the game area / UI: deliver a click to the UI system
                    inp.pointers.add(Input.Pointer(0, 100 + id, x, y)); inp.pointers.add(Input.Pointer(2, 100 + id, x, y))
                    return true
                }
                val t = Touch(c, x, y, System.currentTimeMillis(), x, y)
                touches[id] = t
                apply(t, inp, true)
            }
            MotionEvent.ACTION_MOVE -> for (k in 0 until e.pointerCount) {
                val t = touches[e.getPointerId(k)] ?: continue
                val nx = e.getX(k); val ny = e.getY(k)
                val c = layout.controls.getOrNull(t.control) ?: continue
                if (c.type == "look") {
                    val s = 220f / minDim
                    inp.rawLookX += (nx - t.x) * s; inp.rawLookY += (ny - t.y) * s
                }
                t.x = nx; t.y = ny
                apply(t, inp, true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val all = e.actionMasked != MotionEvent.ACTION_POINTER_UP
                val ids = if (all) touches.keys.toList() else listOf(e.getPointerId(e.actionIndex))
                for (id in ids) {
                    val t = touches.remove(id) ?: continue
                    apply(t, inp, false)
                    val c = layout.controls.getOrNull(t.control)
                    // a quick tap on a look pad counts as a game tap
                    if (c?.type == "look" && e.actionMasked != MotionEvent.ACTION_CANCEL &&
                        System.currentTimeMillis() - t.downTime < 280 && hypot(t.x - t.sx, t.y - t.sy) < minDim * 0.03f) {
                        inp.rawTouchSX = t.x; inp.rawTouchSY = t.y; inp.tapPending = true
                    }
                }
            }
        }
        invalidate()
        return true
    }

    private fun apply(t: Touch, inp: Input, down: Boolean) {
        val c = layout.controls.getOrNull(t.control) ?: return
        when (c.type) {
            "joystick" -> {
                var sx = 0f; var sy = 0f
                if (down) {
                    val r = radius(c)
                    var dx = t.x - cx(c); var dy = t.y - cy(c)
                    val d = hypot(dx, dy); if (d > r) { dx = dx / d * r; dy = dy / d * r }
                    sx = dx / r; sy = -dy / r
                    if (hypot(sx, sy) < 0.12f) { sx = 0f; sy = 0f }
                }
                if (c.id == "move") { inp.joyX = sx; inp.joyY = sy } else inp.rawSticks[c.id] = floatArrayOf(sx, sy)
                inp.rawButtons[c.id] = down && (sx != 0f || sy != 0f)
            }
            "dpad" -> {
                var dir = -1
                if (down) {
                    val dx = t.x - cx(c); val dy = t.y - cy(c)
                    if (hypot(dx, dy) > radius(c) * 0.18f) {
                        val a = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble()))
                        dir = when { a > -45 && a <= 45 -> 0; a > 45 && a <= 135 -> 1; a > 135 || a <= -135 -> 2; else -> 3 }
                    }
                }
                t.dir = dir
                val sx = when (dir) { 0 -> 1f; 2 -> -1f; else -> 0f }; val sy = when (dir) { 1 -> 1f; 3 -> -1f; else -> 0f }
                if (c.id == "move") { inp.joyX = sx; inp.joyY = sy } else inp.rawSticks[c.id] = floatArrayOf(sx, sy)
                inp.rawButtons["Left"] = dir == 2; inp.rawButtons["Right"] = dir == 0
                inp.rawButtons["Up"] = dir == 1; inp.rawButtons["Down"] = dir == 3
            }
            "steer" -> {
                val (l, r) = steerCenters(c)
                val dir = if (!down) -1 else if (abs(t.x - l) < abs(t.x - r)) 0 else 1
                t.dir = dir
                inp.rawButtons["Left"] = dir == 0; inp.rawButtons["Right"] = dir == 1
                inp.joyX = when (dir) { 0 -> -1f; 1 -> 1f; else -> 0f }
            }
            "look" -> { inp.rawButtons[c.id] = down }
            else -> {
                inp.rawButtons[c.id] = down
                if (c.id == "A") inp.rawA = down
                if (c.id == "B") inp.rawB = down
            }
        }
    }

    fun reset() {
        touches.clear()
        input()?.let { it.joyX = 0f; it.joyY = 0f; it.rawA = false; it.rawB = false; it.rawButtons.clear(); it.rawSticks.clear() }
        invalidate()
    }
}
