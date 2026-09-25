package com.sengine.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable

/**
 * S Engine icon set: crisp vector line icons defined on a 24x24 grid with a tiny drawing DSL
 * (l polyline, g closed polygon, p filled polygon, c/C circle, r/R round rect, e ellipse,
 * a arc, q quad curve, t text). Rendered at any size and tint.
 */
object Icons {
    private val defs: Map<String, String> = mapOf(
        "play" to "p 8 5 19 12 8 19",
        "pause" to "R 6 5 4 14 1;R 14 5 4 14 1",
        "stop" to "R 6 6 12 12 2",
        "step" to "p 5 5 14 12 5 19;R 16 5 3 14 1",
        "save" to "r 4 4 16 16 2;l 8 4 8 9 15 9 15 4;r 7 13 10 7 1",
        "undo" to "l 9 5 4 10 9 15;q 4 10 19 8 19 19",
        "redo" to "l 15 5 20 10 15 15;q 20 10 5 8 5 19",
        "folder" to "g 3 6 9 6 11 8 21 8 21 19 3 19",
        "folder_open" to "g 3 6 9 6 11 8 19 8 19 10;g 3 19 6 10 22 10 19 19",
        "plus" to "l 12 5 12 19;l 5 12 19 12",
        "close" to "l 6 6 18 18;l 18 6 6 18",
        "check" to "l 5 12 10 17 19 7",
        "trash" to "l 4 7 20 7;l 9 7 9 4 15 4 15 7;g 6 7 7 20 17 20 18 7;l 10 11 10 16;l 14 11 14 16",
        "copy" to "r 8 8 12 12 2;l 5 16 4 16 4 4 16 4 16 5",
        "eye" to "q 2 12 12 2 22 12;q 22 12 12 22 2 12;c 12 12 3",
        "eye_off" to "q 2 12 12 2 22 12;q 22 12 12 22 2 12;l 4 4 20 20",
        "gear" to "c 12 12 3.5;c 12 12 8;l 12 2 12 4;l 12 20 12 22;l 2 12 4 12;l 20 12 22 12;l 5 5 6.5 6.5;l 17.5 17.5 19 19;l 19 5 17.5 6.5;l 6.5 17.5 5 19",
        "help" to "c 12 12 9;q 9 9 12 6 15 9;q 15 11 12 12.5;l 12 12.5 12 14;C 12 17 1.2",
        "info" to "c 12 12 9;l 12 11 12 17;C 12 7.5 1.2",
        "search" to "c 10 10 6;l 15 15 20 20",
        "menu" to "l 4 6 20 6;l 4 12 20 12;l 4 18 20 18",
        "more" to "C 12 5 1.8;C 12 12 1.8;C 12 19 1.8",
        "back" to "l 11 5 4 12 11 19;l 4 12 20 12",
        "forward" to "l 13 5 20 12 13 19;l 20 12 4 12",
        "home" to "l 3 11 12 3 21 11;g 6 10 6 20 18 20 18 10;r 10 14 4 6 0",
        "cube" to "g 12 3 20 7.5 20 16.5 12 21 4 16.5 4 7.5;l 4 7.5 12 12 20 7.5;l 12 12 12 21",
        "sphere" to "c 12 12 8.5;e 12 12 8.5 3.2 0;e 12 12 3.2 8.5 0",
        "camera" to "r 3 7 13 10 2;g 16 10 21 7 21 17 16 14",
        "light" to "q 7 14 5 4 12 3;q 12 3 19 4 17 14;l 7 14 9 16 15 16 17 14;l 9 19 15 19;l 10 21.5 14 21.5",
        "sun" to "c 12 12 4;l 12 2 12 4.5;l 12 19.5 12 22;l 2 12 4.5 12;l 19.5 12 22 12;l 5 5 6.8 6.8;l 17.2 17.2 19 19;l 19 5 17.2 6.8;l 6.8 17.2 5 19",
        "moon" to "q 14 3 5 7 7 15;q 9 22 20 17",
        "code" to "l 8 7 3 12 8 17;l 16 7 21 12 16 17;l 14 5 10 19",
        "blueprint" to "r 2 4 7 6 1.5;r 15 14 7 6 1.5;r 15 3 7 5 1.5;q 9 7 12 7 15 5.5;q 9 7 12 17 15 17",
        "film" to "r 3 4 18 16 2;l 7 4 7 20;l 17 4 17 20;l 3 9 7 9;l 3 15 7 15;l 17 9 21 9;l 17 15 21 15",
        "music" to "l 9 17 9 5 20 3 20 15;C 6.5 17.5 2.8;C 17.5 15.5 2.8",
        "gamepad" to "q 2 17 4 7 9 7;l 9 7 15 7;q 15 7 20 7 22 17;q 22 20 18 17 15 14;l 15 14 9 14;q 9 14 6 17 2 17;l 7 9.5 7 12.5;l 5.5 11 8.5 11;C 16 10 1;C 18 12 1",
        "image" to "r 3 4 18 16 2;C 9 9.5 1.8;l 4 18 10 12 14 16 16 14 20 18",
        "speaker" to "p 4 9 8 9 13 5 13 19 8 15 4 15;q 16 9 18 12 16 15;q 18.5 6.5 22 12 18.5 17.5",
        "hammer" to "l 13 7 4 16 7 19 16 10;g 12 4 18 4 20 7 17 10 12 7",
        "store" to "g 4 8 20 8 19 20 5 20;q 8 8 8 3 12 3;q 12 3 16 3 16 8",
        "layers" to "g 12 3 21 8 12 13 3 8;l 3 12 12 17 21 12;l 3 16 12 21 21 16",
        "move" to "l 12 3 12 21;l 3 12 21 12;l 9 6 12 3 15 6;l 9 18 12 21 15 18;l 6 9 3 12 6 15;l 18 9 21 12 18 15",
        "rotate" to "a 12 12 8 -60 300;l 17 3 16.5 6.5 20 7",
        "scale" to "r 3 11 10 10 1;l 13 11 20 4;l 14 4 20 4 20 10",
        "grid" to "r 3 3 18 18 2;l 9 3 9 21;l 15 3 15 21;l 3 9 21 9;l 3 15 21 15",
        "magnet" to "l 6 4 6 13;q 6 20 12 20;q 18 20 18 13;l 18 13 18 4;l 6 7 9 7;l 15 7 18 7;l 9 4 9 13;q 9 16.5 12 16.5;q 15 16.5 15 13;l 15 13 15 4",
        "brush" to "l 20 3 11 13;q 7 12 6 16;q 5 20 3 21;q 9 21 11 18;q 13 16 11 13",
        "text" to "l 5 5 19 5;l 12 5 12 20;l 9 20 15 20",
        "sparkle" to "p 12 2 14 10 22 12 14 14 12 22 10 14 2 12 10 10;p 19 2 20 4 22 5 20 6 19 8 18 6 16 5 18 4",
        "atom" to "e 12 12 9 3.5 30;e 12 12 9 3.5 -30;e 12 12 9 3.5 90;C 12 12 1.8",
        "wand" to "l 4 20 16 8;l 18 2 18 6;l 16 4 20 4;l 21 9 21 11;l 20 10 22 10;l 10 3 10 5;l 9 4 11 4",
        "target" to "c 12 12 8;c 12 12 3;l 12 1 12 6;l 12 18 12 23;l 1 12 6 12;l 18 12 23 12",
        "map" to "g 3 6 9 4 15 6 21 4 21 18 15 20 9 18 3 20;l 9 4 9 18;l 15 6 15 20",
        "car" to "l 3 16 3 12 6 11 8 7 16 7 18 11 21 12 21 16;l 3 16 21 16;C 7 16.5 2;C 17 16.5 2;l 8 11 16 11",
        "ui" to "r 3 4 18 16 2;l 3 8 21 8;R 7 12 10 4 2",
        "wave" to "l 2 12 5 12 7 6 10 18 13 4 16 20 18 10 20 12 22 12",
        "chart" to "l 4 20 20 20;R 6 12 3 8 1;R 11 7 3 13 1;R 16 10 3 10 1",
        "doctor" to "l 3 12 7 12 9 7 12 17 15 9 17 12 21 12",
        "share" to "C 18 5 2.5;C 6 12 2.5;C 18 19 2.5;l 8 11 16 6;l 8 13 16 18",
        "download" to "l 12 3 12 15;l 7 10 12 15 17 10;l 4 20 20 20",
        "upload" to "l 12 21 12 7;l 7 12 12 7 17 12;l 4 4 20 4",
        "phone" to "r 7 2 10 20 2;l 10 18 14 18",
        "key" to "c 8 12 4.5;l 12.5 12 21 12;l 18 12 18 16;l 15 12 15 15",
        "palette" to "q 3 3 12 3;q 21 3 21 12;q 21 16 17 15;q 14 15 15 18;q 16 21 12 21;q 3 21 3 12;q 3 3 12 3;C 8 9 1.3;C 12 7 1.3;C 16 9 1.3",
        "sliders" to "l 4 6 20 6;l 4 12 20 12;l 4 18 20 18;C 9 6 2;C 15 12 2;C 7 18 2",
        "dpad" to "g 9 3 15 3 15 9 21 9 21 15 15 15 15 21 9 21 9 15 3 15 3 9 9 9",
        "joystick" to "c 12 17 7 ;C 12 9 3.5;l 12 12.5 12 16",
        "book" to "g 4 4 10 4 12 6 12 20 10 18 4 18;g 20 4 14 4 12 6 12 20 14 18 20 18",
        "bolt" to "p 13 2 4 14 11 14 10 22 20 9 13 9",
        "mountain" to "g 2 20 9 8 13 14 16 10 22 20;l 7 11.5 9 13 11 11",
        "tree" to "p 12 2 19 12 15 12 20 18 4 18 9 12 5 12;l 12 18 12 22",
        "mesh" to "g 12 3 20 8 20 16 12 21 4 16 4 8;l 4 8 20 16;l 20 8 4 16;l 12 3 12 21;C 12 3 1.5;C 20 8 1.5;C 20 16 1.5;C 12 21 1.5;C 4 16 1.5;C 4 8 1.5",
        "bone" to "C 5.5 5.5 2.5;C 8 3.5 2;C 18.5 18.5 2.5;C 16 20.5 2;l 7 7 17 17",
        "keyframe" to "g 12 3 21 12 12 21 3 12",
        "vertex" to "g 4 18 12 5 20 18;C 12 5 2.2;C 4 18 2.2;C 20 18 2.2",
        "edge" to "g 4 18 12 5 20 18;l 4 18 12 5",
        "face" to "p 4 18 12 5 20 18",
        "extrude" to "g 4 14 12 18 20 14 12 10;l 12 10 12 2;l 9 5 12 2 15 5",
        "subdivide" to "r 4 4 16 16 1;l 12 4 12 20;l 4 12 20 12",
        "mirror" to "l 12 2 12 22;g 10 6 3 18 10 18;g 14 6 21 18 14 18",
        "frame" to "l 3 8 3 3 8 3;l 16 3 21 3 21 8;l 21 16 21 21 16 21;l 8 21 3 21 3 16;C 12 12 2",
        "cursor" to "p 5 3 19 12 12 13 9 20",
        "scene" to "r 3 3 18 18 2;l 3 16 9 10 13 14 16 11 21 15;C 16 7 1.8",
        "script" to "g 6 3 15 3 19 7 19 21 6 21;l 15 3 15 7 19 7;l 9 12 15 12;l 9 16 13 16",
        "shader" to "c 12 12 8.5;p 12 3.5 12 20.5 20.5 12",
        "particles" to "C 6 16 2;C 12 9 2.5;C 18 15 1.6;C 16 5 1.2;C 7 6 1;C 11 19 1.2",
        "physics" to "C 12 7 3.5;l 3 20 21 20;l 12 11 12 14;l 9 16 15 16",
        "collider" to "r 4 4 16 16 1;l 4 8 4 4 8 4;l 16 4 20 4 20 8;l 20 16 20 20 16 20;l 8 20 4 20 4 16",
        "rigidbody" to "R 7 3 10 10 2;l 12 15 12 21;l 9 18 12 21 15 18",
        "sound" to "l 4 9 4 15;l 8 6 8 18;l 12 3 12 21;l 16 7 16 17;l 20 10 20 14",
        "animator" to "C 6 12 2;C 12 12 2;C 18 12 2;l 3 5 21 5;l 3 19 21 19",
        "fire" to "q 12 2 18 9 17 15;q 16 21 12 21;q 8 21 7 15;q 6 11 10 8;q 10 12 12 12;q 13 7 12 2",
        "shield" to "q 12 2 20 5 20 5;l 20 5 20 12;q 20 19 12 22;q 4 19 4 12;l 4 12 4 5;q 12 2 4 5 12 2",
        "star" to "p 12 2 14.9 8.6 22 9.3 16.6 14 18.2 21 12 17.3 5.8 21 7.4 14 2 9.3 9.1 8.6",
        "heart" to "q 12 7 7 1 3 8;q 1 13 12 21;q 23 13 21 8;q 17 1 12 7",
        "trophy" to "l 7 3 17 3 17 9;q 17 15 12 15;q 7 15 7 9;l 7 9 7 3;l 7 5 3 5 3 8;q 3 11 7 11;l 17 5 21 5 21 8;q 21 11 17 11;l 12 15 12 19;l 8 21 16 21",
        "lock" to "r 5 10 14 11 2;q 8 10 8 3 12 3;q 16 3 16 10",
        "unlock" to "r 5 10 14 11 2;q 8 10 8 3 12 3;q 16 3 16 6",
        "refresh" to "a 12 12 8 30 300;l 20 3 20 8 15 8",
        "fullscreen" to "l 3 9 3 3 9 3;l 15 3 21 3 21 9;l 21 15 21 21 15 21;l 9 21 3 21 3 15",
        "wrench" to "l 4 20 13 11;q 11 6 15 4 17 5;l 17 5 14 8 16 10 19 7;q 20 9 18 13 13 11",
        "rocket" to "q 12 2 17 7 16 15;l 16 15 8 15;q 7 7 12 2;l 8 15 5 19 9 17;l 16 15 19 19 15 17;C 12 9 1.8",
        "chip" to "r 6 6 12 12 2;l 9 2 9 6;l 15 2 15 6;l 9 18 9 22;l 15 18 15 22;l 2 9 6 9;l 2 15 6 15;l 18 9 22 9;l 18 15 22 15",
        "world" to "c 12 12 9;e 12 12 4 9 0;l 3 12 21 12",
        "clock" to "c 12 12 9;l 12 7 12 12 16 14",
        "gift" to "r 3 8 18 5 1;r 5 13 14 8 1;l 12 8 12 21;q 12 8 7 3 7 6;q 7 8 12 8;q 12 8 17 3 17 6;q 17 8 12 8",
    )

    val names: Set<String> get() = defs.keys

    private val path = Path()
    private val rect = RectF()

    fun draw(c: Canvas, name: String, cx: Float, cy: Float, size: Float, color: Int, paint: Paint) {
        val src = defs[name] ?: defs["help"]!!
        val s = size / 24f
        val ox = cx - size / 2; val oy = cy - size / 2
        paint.color = color
        paint.strokeWidth = 1.9f * s
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.isAntiAlias = true
        for (op in src.split(';')) {
            val parts = op.trim().split(Regex("\\s+"))
            if (parts.isEmpty() || parts[0].isEmpty()) continue
            val k = parts[0]
            val n = parts.drop(1).mapNotNull { it.toFloatOrNull() }
            fun X(i: Int) = ox + n[i] * s
            fun Y(i: Int) = oy + n[i] * s
            when (k) {
                "l", "g", "p" -> {
                    path.reset(); path.moveTo(X(0), Y(1))
                    var i = 2
                    while (i + 1 < n.size) { path.lineTo(X(i), Y(i + 1)); i += 2 }
                    if (k != "l") path.close()
                    paint.style = if (k == "p") Paint.Style.FILL else Paint.Style.STROKE
                    c.drawPath(path, paint)
                }
                "c", "C" -> { paint.style = if (k == "C") Paint.Style.FILL else Paint.Style.STROKE; c.drawCircle(X(0), Y(1), n[2] * s, paint) }
                "r", "R" -> {
                    paint.style = if (k == "R") Paint.Style.FILL else Paint.Style.STROKE
                    rect.set(X(0), Y(1), X(0) + n[2] * s, Y(1) + n[3] * s)
                    val r = n.getOrElse(4) { 0f } * s
                    c.drawRoundRect(rect, r, r, paint)
                }
                "e" -> {
                    paint.style = Paint.Style.STROKE
                    c.save(); c.rotate(n[4], X(0), Y(1))
                    rect.set(X(0) - n[2] * s, Y(1) - n[3] * s, X(0) + n[2] * s, Y(1) + n[3] * s)
                    c.drawOval(rect, paint); c.restore()
                }
                "a" -> {
                    paint.style = Paint.Style.STROKE
                    rect.set(X(0) - n[2] * s, Y(1) - n[2] * s, X(0) + n[2] * s, Y(1) + n[2] * s)
                    c.drawArc(rect, n[3], n[4], false, paint)
                }
                "q" -> {
                    paint.style = Paint.Style.STROKE
                    path.reset(); path.moveTo(X(0), Y(1))
                    if (n.size >= 6) path.quadTo(X(2), Y(3), X(4), Y(5)) else path.lineTo(X(2), Y(3))
                    c.drawPath(path, paint)
                }
            }
        }
        paint.style = Paint.Style.FILL
    }

    /** A tintable drawable for use in ImageViews / TextView compound drawables. */
    class IconDrawable(val name: String, var iconColor: Int, private val sizePx: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun draw(canvas: Canvas) {
            val b = bounds
            draw(canvas, name, b.exactCenterX(), b.exactCenterY(), minOf(b.width(), b.height()).toFloat(), iconColor, paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
        override fun getIntrinsicWidth() = sizePx
        override fun getIntrinsicHeight() = sizePx
    }

    fun drawable(ctx: Context, name: String, tint: Int, sizeDp: Int = 22): IconDrawable =
        IconDrawable(name, tint, (sizeDp * ctx.resources.displayMetrics.density).toInt()).also { it.setBounds(0, 0, it.intrinsicWidth, it.intrinsicHeight) }

    @Suppress("unused")
    private val bold = Typeface.DEFAULT_BOLD
}
