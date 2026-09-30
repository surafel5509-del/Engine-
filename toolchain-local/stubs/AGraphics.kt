package android.graphics

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/** Headless android.graphics: int-array-backed Bitmap, rasterizing Canvas, Paint, Path, etc. */

enum class BitmapConfig { ALPHA_8, RGB_565, ARGB_4444, ARGB_8888 }

class Bitmap private constructor(val width: Int, val height: Int, val config: BitmapConfig) {
    class Config { companion object { val ALPHA_8 = BitmapConfig.ALPHA_8; val RGB_565 = BitmapConfig.RGB_565; val ARGB_4444 = BitmapConfig.ARGB_4444; val ARGB_8888 = BitmapConfig.ARGB_8888 } }
    val pixels = IntArray(width * height)

    enum class CompressFormat { JPEG, PNG, WEBP }

    fun eraseColor(c: Int) { for (i in pixels.indices) pixels[i] = c }
    fun getPixel(x: Int, y: Int): Int = if (x in 0 until width && y in 0 until height) pixels[y * width + x] else 0
    fun setPixel(x: Int, y: Int, c: Int) { if (x in 0 until width && y in 0 until height) pixels[y * width + x] = c }

    fun setPixels(src: IntArray, offset: Int, stride: Int, x: Int, y: Int, w: Int, h: Int) {
        for (row in 0 until h) for (col in 0 until w) {
            val sx = x + col; val sy = y + row
            if (sx in 0 until width && sy in 0 until height) pixels[sy * width + sx] = src[offset + row * stride + col]
        }
    }

    fun getPixels(dst: IntArray, offset: Int, stride: Int, x: Int, y: Int, w: Int, h: Int) {
        for (row in 0 until h) for (col in 0 until w) {
            val sx = x + col; val sy = y + row
            dst[offset + row * stride + col] = if (sx in 0 until width && sy in 0 until height) pixels[sy * width + sx] else 0
        }
    }

    fun compress(format: CompressFormat, quality: Int, stream: OutputStream): Boolean {
        val argb = IntArray(width * height)
        for (i in argb.indices) argb[i] = pixels[i]
        stream.write(pngBytes(width, height, argb))
        return true
    }

    private fun pngBytes(w: Int, h: Int, argb: IntArray): ByteArray {
        val raw = ByteArray((w * 4 + 1) * h)
        var k = 0
        for (y in 0 until h) {
            raw[k++] = 0
            for (x in 0 until w) {
                val c = argb[y * w + x]
                raw[k++] = (c shr 16).toByte(); raw[k++] = (c shr 8).toByte(); raw[k++] = c.toByte(); raw[k++] = (c ushr 24).toByte()
            }
        }
        val def = Deflater(6); def.setInput(raw); def.finish()
        val z = ByteArrayOutputStream(); val buf = ByteArray(65536)
        while (!def.finished()) { val n = def.deflate(buf); z.write(buf, 0, n) }
        def.end()
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        fun chunk(type: String, data: ByteArray) {
            val d = DataOutputStream(out)
            d.writeInt(data.size); val t = type.toByteArray(Charsets.US_ASCII); d.write(t); d.write(data)
            val crc = CRC32(); crc.update(t); crc.update(data); d.writeInt(crc.value.toInt())
        }
        val ihdr = ByteArrayOutputStream().also { DataOutputStream(it).apply { writeInt(w); writeInt(h); writeByte(8); writeByte(6); writeByte(0); writeByte(0); writeByte(0) } }
        chunk("IHDR", ihdr.toByteArray()); chunk("IDAT", z.toByteArray()); chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    fun createBitmap(): Bitmap = createBitmap(this)
    fun isRecycled(): Boolean = false
    fun recycle() {}
    fun sameAs(other: Bitmap): Boolean = width == other.width && height == other.height && pixels.contentEquals(other.pixels)

    companion object {
        @JvmStatic fun createBitmap(w: Int, h: Int, config: BitmapConfig): Bitmap {
            val b = Bitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), config)
            return b
        }
        @JvmStatic fun createBitmap(src: Bitmap): Bitmap {
            val b = Bitmap(src.width, src.height, src.config)
            System.arraycopy(src.pixels, 0, b.pixels, 0, src.pixels.size)
            return b
        }
        @JvmStatic fun createBitmap(colors: IntArray, width: Int, height: Int, config: BitmapConfig): Bitmap {
            val b = Bitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), config)
            for (y in 0 until b.height) for (x in 0 until b.width) b.pixels[y * b.width + x] = colors[y * width + x]
            return b
        }
        @JvmStatic fun createBitmap(src: Bitmap, x: Int, y: Int, w: Int, h: Int): Bitmap {
            val b = Bitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), src.config)
            for (row in 0 until b.height) for (col in 0 until b.width)
                b.pixels[row * b.width + col] = src.getPixel(x + col, y + row)
            return b
        }
        @JvmStatic fun createScaledBitmap(src: Bitmap, dw: Int, dh: Int, filter: Boolean): Bitmap {
            val b = Bitmap(dw.coerceAtLeast(1), dh.coerceAtLeast(1), src.config)
            for (y in 0 until b.height) for (x in 0 until b.width) {
                val sx = (x * src.width / b.width).coerceIn(0, src.width - 1)
                val sy = (y * src.height / b.height).coerceIn(0, src.height - 1)
                b.pixels[y * b.width + x] = src.pixels[sy * src.width + sx]
            }
            return b
        }
    }
}

object BitmapFactory {
    @JvmStatic fun decodeFile(path: String?): Bitmap? = null
    @JvmStatic fun decodeByteArray(data: ByteArray?, offset: Int = 0, length: Int = data?.size ?: 0): Bitmap? = null
    @JvmStatic fun decodeResource(res: Any?, id: Int): Bitmap? = null
}

class RectF() {
    var left = 0f; var top = 0f; var right = 0f; var bottom = 0f
    constructor(l: Float, t: Float, r: Float, b: Float) : this() { left = l; top = t; right = r; bottom = b }
    constructor(r: Rect) : this(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    fun set(l: Float, t: Float, r: Float, b: Float) { left = l; top = t; right = r; bottom = b }
    fun set(r: RectF) { left = r.left; top = r.top; right = r.right; bottom = r.bottom }
    fun sort() { if (left > right) { val t = left; left = right; right = t }; if (top > bottom) { val t = top; top = bottom; bottom = t } }
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
    fun contains(r: RectF): Boolean = r.left >= left && r.top >= top && r.right <= right && r.bottom <= bottom
    fun inset(dx: Float, dy: Float) { left += dx; top += dy; right -= dx; bottom -= dy }
    fun offset(dx: Float, dy: Float) { left += dx; top += dy; right += dx; bottom += dy }
    fun isEmpty(): Boolean = left >= right || top >= bottom
}

class Rect() {
    var left = 0; var top = 0; var right = 0; var bottom = 0
    constructor(l: Int, t: Int, r: Int, b: Int) : this() { left = l; top = t; right = r; bottom = b }
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    fun set(l: Int, t: Int, r: Int, b: Int) { left = l; top = t; right = r; bottom = b }
    fun contains(x: Int, y: Int): Boolean = x in left..right && y in top..bottom
    fun offset(dx: Int, dy: Int) { left += dx; top += dy; right += dx; bottom += dy }
}

object Color {
    const val BLACK = -0x1000000
    const val DKGRAY = -0xbbbbbb
    const val GRAY = -0x777778
    const val LTGRAY = -0x333334
    const val WHITE = -0x1
    const val RED = -0x10000
    const val GREEN = -0xff0100
    const val BLUE = -0xffff01
    const val YELLOW = -0x100
    const val CYAN = -0xff0101
    const val MAGENTA = -0xff01
    const val TRANSPARENT = 0

    @JvmStatic fun alpha(c: Int): Int = c ushr 24
    @JvmStatic fun red(c: Int): Int = c shr 16 and 0xFF
    @JvmStatic fun green(c: Int): Int = c shr 8 and 0xFF
    @JvmStatic fun blue(c: Int): Int = c and 0xFF
    @JvmStatic fun rgb(r: Int, g: Int, b: Int): Int = -0x1000000 or (r shl 16) or (g shl 8) or b
    @JvmStatic fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b
    @JvmStatic fun parseColor(s: String): Int {
        if (s.startsWith("#")) {
            val hex = s.substring(1)
            return when (hex.length) {
                6 -> (-0x1000000L or hex.toLong(16)).toInt()
                8 -> hex.toLong(16).toInt()
                3 -> {
                    val r = Integer.parseInt(hex.substring(0, 1), 16) * 17
                    val g = Integer.parseInt(hex.substring(1, 2), 16) * 17
                    val b = Integer.parseInt(hex.substring(2, 3), 16) * 17
                    rgb(r, g, b)
                }
                else -> throw IllegalArgumentException("Unknown color: $s")
            }
        }
        throw IllegalArgumentException("Unknown color: $s")
    }
    fun HSVToColor(hsv: FloatArray): Int {
        val h = (hsv[0] % 360f) / 60f; val s = hsv[1]; val v = hsv[2]
        val i = h.toInt(); val f = h - i
        val p = v * (1 - s); val q = v * (1 - s * f); val t = v * (1 - s * (1 - f))
        val (r, g, b) = when (i) { 0 -> listOf(v, t, p); 1 -> listOf(q, v, p); 2 -> listOf(p, v, t); 3 -> listOf(p, q, v); 4 -> listOf(t, p, v); else -> listOf(v, p, q) }
        return rgb((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
    }
    fun colorToHSV(outHSV: FloatArray, c: Int) {
        val r = red(c) / 255f; val g = green(c) / 255f; val b = blue(c) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
        val h = when { d == 0f -> 0f; max == r -> 60f * (((g - b) / d) % 6f); max == g -> 60f * ((b - r) / d + 2f); else -> 60f * ((r - g) / d + 4f) }
        outHSV[0] = if (h < 0) h + 360f else h; outHSV[1] = if (max == 0f) 0f else d / max; outHSV[2] = max
    }
}

class Path {
    enum class Direction { CW, CCW }
    private val pts = ArrayList<Float>()
    private val ops = ArrayList<Int>() // 0 moveTo 1 lineTo 2 quadTo 3 close
    private val quads = ArrayList<Float>()
    fun reset() { pts.clear(); ops.clear(); quads.clear() }
    fun rewind() = reset()
    fun moveTo(x: Float, y: Float) { pts.add(x); pts.add(y); ops.add(0) }
    fun lineTo(x: Float, y: Float) { pts.add(x); pts.add(y); ops.add(1) }
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) { pts.add(x1); pts.add(y1); pts.add(x2); pts.add(y2); ops.add(2) }
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) { quadTo(x1, y1, x2, y2); lineTo(x3, y3) }
    fun close() { ops.add(3) }
    fun isEmpty(): Boolean = ops.isEmpty()
    /** Flattened polygon points (even-odd fill approximation): [x0,y0,x1,y1,...]. */
    fun flattened(): FloatArray {
        val out = ArrayList<Float>()
        var i = 0
        var started = false
        for (op in ops) {
            when (op) {
                0, 1 -> { out.add(pts[i]); out.add(pts[i + 1]); i += 2; started = true }
                2 -> {
                    val x0 = out[out.size - 2]; val y0 = out[out.size - 1]
                    val cx = pts[i]; val cy = pts[i + 1]; val x = pts[i + 2]; val y = pts[i + 3]; i += 4
                    for (t in 1..4) {
                        val u = t / 4f
                        out.add((1 - u) * (1 - u) * x0 + 2 * (1 - u) * u * cx + u * u * x)
                        out.add((1 - u) * (1 - u) * y0 + 2 * (1 - u) * u * cy + u * u * y)
                    }
                }
                3 -> { if (started && out.size >= 2) { out.add(out[0]); out.add(out[1]) } }
            }
        }
        return out.toFloatArray()
    }
}

open class Shader() {
    class TileMode { companion object { val CLAMP = TileMode(); val REPEAT = TileMode(); val MIRROR = TileMode() } }
}
class LinearGradient(x0: Float, y0: Float, x1: Float, y1: Float, color0: Int, color1: Int, tile: Shader.TileMode) : Shader() {
    constructor(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, positions: FloatArray?, tile: Shader.TileMode) : this(x0, y0, x1, y1, colors.firstOrNull() ?: 0, colors.lastOrNull() ?: 0, tile)
}
class RadialGradient(cx: Float, cy: Float, radius: Float, color0: Int, color1: Int, tile: Shader.TileMode) : Shader() {
    constructor(cx: Float, cy: Float, radius: Float, colors: IntArray, positions: FloatArray?, tile: Shader.TileMode) : this(cx, cy, radius, colors.firstOrNull() ?: 0, colors.lastOrNull() ?: 0, tile)
}

object PorterDuff {
    enum class Mode { SRC, SRC_OVER, DST, DST_OVER, SRC_IN, DST_IN, SRC_OUT, DST_OUT, SRC_ATOP, DST_ATOP, XOR, DARKEN, LIGHTEN, MULTIPLY, SCREEN, ADD, CLEAR }
}
class PorterDuffXfermode(val mode: PorterDuff.Mode) : Xfermode()
open class Xfermode()

class Typeface private constructor() {
    companion object {
        val DEFAULT = Typeface()
        val DEFAULT_BOLD = Typeface()
        val SANS_SERIF = Typeface()
        val SERIF = Typeface()
        val MONOSPACE = Typeface()
        @JvmStatic fun create(family: Typeface?, style: Int): Typeface = Typeface()
        @JvmStatic fun create(family: String, style: Int): Typeface = Typeface()
        @JvmStatic fun defaultFromStyle(style: Int): Typeface = Typeface()
    }
}

class Paint() {
    constructor(flags: Int) : this()

    companion object {
        const val ANTI_ALIAS_FLAG = 1
        const val FILTER_BITMAP_FLAG = 2
        const val DITHER_FLAG = 4
        const val TEXT_ANTI_ALIAS_FLAG = 8
        const val FAKE_BOLD_TEXT_FLAG = 16
        const val UNDERLINE_TEXT_FLAG = 32
        const val STRIKE_THRU_TEXT_FLAG = 64
    }

    var color = -0x1000000
    var strokeWidth = 1f
    var textSize = 12f
    var isAntiAlias = false
    var isDither = false
    var isFilterBitmap = false
    var isFakeBoldText = false
    var isStrikeThruText = false
    var isUnderlineText = false
    var typeface: Typeface? = Typeface.DEFAULT
    var shader: Shader? = null
    var xfermode: Xfermode? = null
    var alpha: Int
        get() = color ushr 24
        set(v) { color = (color and 0xFFFFFF) or (v shl 24) }
    var style = Style.FILL
    var strokeCap = Cap.BUTT
    var strokeJoin = Join.MITER

    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Cap { BUTT, ROUND, SQUARE }
    enum class Join { MITER, ROUND, BEVEL }
    enum class Align { LEFT, CENTER, RIGHT }

    var textAlign = Align.LEFT

    fun set(p: Paint) { color = p.color; strokeWidth = p.strokeWidth; textSize = p.textSize; style = p.style; isAntiAlias = p.isAntiAlias; typeface = p.typeface; shader = p.shader }
    fun reset() { color = -0x1000000; strokeWidth = 1f; textSize = 12f; style = Style.FILL; isAntiAlias = false; shader = null }
    fun measureText(text: String): Float = text.length * textSize * 0.6f
    fun measureText(text: CharSequence, start: Int, end: Int): Float = (end - start) * textSize * 0.6f
    fun ascent(): Float = -textSize
    fun descent(): Float = textSize * 0.2f
    fun setShadowLayer(radius: Float, dx: Float, dy: Float, color: Int) {}

    class FontMetrics {
        var top = 0f
        var ascent = 0f
        var descent = 0f
        var bottom = 0f
        var leading = 0f
    }

    private val fm = FontMetrics()
    val fontMetrics: FontMetrics get() { fm.ascent = -textSize; fm.descent = textSize * 0.2f; fm.top = -textSize * 1.1f; fm.bottom = textSize * 0.3f; return fm }
}

/** Software canvas rasterizing into a Bitmap's pixel array. */
class Canvas {
    var bitmap: Bitmap? = null
    private val saveCount = ArrayList<Int>()

    constructor() {}
    constructor(b: Bitmap) { bitmap = b }

    @JvmName("attachBitmap") fun setBitmap(b: Bitmap?) { bitmap = b }
    val width: Int get() = bitmap?.width ?: 0
    val height: Int get() = bitmap?.height ?: 0

    fun save(): Int { saveCount.add(0); return saveCount.size }
    fun restore() { if (saveCount.isNotEmpty()) saveCount.removeAt(saveCount.size - 1) }
    fun restoreToCount(count: Int) { while (saveCount.size > count && saveCount.isNotEmpty()) saveCount.removeAt(saveCount.size - 1) }
    fun rotate(deg: Float, px: Float, py: Float) {}
    fun rotate(deg: Float) {}
    fun translate(dx: Float, dy: Float) {}
    fun scale(sx: Float, sy: Float) {}
    fun scale(sx: Float, sy: Float, px: Float, py: Float) {}
    fun skew(sx: Float, sy: Float) {}
    fun concat(m: Any?) {}
    fun clipRect(l: Float, t: Float, r: Float, b: Float): Boolean = true
    fun clipRect(r: RectF): Boolean = true
    fun saveLayer(l: Float, t: Float, r: Float, b: Float, p: Paint?): Int = save()
    fun drawColor(c: Int) {
        val b = bitmap ?: return
        for (i in b.pixels.indices) b.pixels[i] = c
    }
    fun drawARGB(a: Int, r: Int, g: Int, bl: Int) = drawColor(Color.argb(a, r, g, bl))
    fun drawRGB(r: Int, g: Int, bl: Int) = drawColor(Color.rgb(r, g, bl))

    private fun blend(x: Int, y: Int, c: Int, p: Paint?) {
        val b = bitmap ?: return
        if (x < 0 || y < 0 || x >= b.width || y >= b.height) return
        val src = c
        if (src == 0) return
        val a = Color.alpha(src)
        if (a >= 255) { b.pixels[y * b.width + x] = src; return }
        val dst = b.pixels[y * b.width + x]
        val ia = 255 - a
        val nr = (Color.red(src) * a + Color.red(dst) * ia) / 255
        val ng = (Color.green(src) * a + Color.green(dst) * ia) / 255
        val nb = (Color.blue(src) * a + Color.blue(dst) * ia) / 255
        b.pixels[y * b.width + x] = Color.argb(255, nr, ng, nb)
    }

    fun drawPoint(x: Float, y: Float, paint: Paint?) = blend(x.toInt(), y.toInt(), paint?.color ?: 0, paint)

    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, paint: Paint?) {
        val n = kotlin.math.max(kotlin.math.abs(x1 - x0), kotlin.math.abs(y1 - y0)).toInt() + 1
        for (i in 0..n) {
            val t = if (n == 0) 0f else i / n.toFloat()
            val cx = x0 + (x1 - x0) * t; val cy = y0 + (y1 - y0) * t
            val w = ((paint?.strokeWidth ?: 1f) * 0.5f).toInt()
            for (dy in -w..w) for (dx in -w..w) blend(cx.toInt() + dx, cy.toInt() + dy, paint?.color ?: 0, paint)
        }
    }

    fun drawRect(l: Float, t: Float, r: Float, b: Float, paint: Paint?) {
        val p = paint ?: return
        if (p.style == Paint.Style.STROKE) {
            drawLine(l, t, r, t, p); drawLine(r, t, r, b, p); drawLine(r, b, l, b, p); drawLine(l, b, l, t, p)
        } else {
            for (y in t.toInt()..b.toInt()) for (x in l.toInt()..r.toInt()) blend(x, y, p.color, p)
        }
    }
    fun drawRect(r: RectF, paint: Paint?) = drawRect(r.left, r.top, r.right, r.bottom, paint)
    fun drawRect(r: Rect, paint: Paint?) = drawRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), paint)

    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint?) {
        val p = paint ?: return
        if (radius <= 0) return
        if (p.style == Paint.Style.STROKE) {
            val n = (radius * 8 + 16).toInt()
            var px = cx + radius; var py = cy
            for (i in 1..n) {
                val a = Math.PI * 2 * i / n
                val nx = cx + (radius * kotlin.math.cos(a)).toFloat()
                val ny = cy + (radius * kotlin.math.sin(a)).toFloat()
                drawLine(px, py, nx, ny, p); px = nx; py = ny
            }
        } else {
            for (y in (cy - radius).toInt()..(cy + radius + 1).toInt())
                for (x in (cx - radius).toInt()..(cx + radius + 1).toInt()) {
                    val dx = x + 0.5f - cx; val dy = y + 0.5f - cy
                    if (dx * dx + dy * dy <= radius * radius) blend(x, y, p.color, p)
                }
        }
    }

    fun drawOval(rect: RectF, paint: Paint?) {
        val p = paint ?: return
        val cx = rect.centerX; val cy = rect.centerY
        val rx = rect.width / 2; val ry = rect.height / 2
        if (rx <= 0 || ry <= 0) return
        for (y in (cy - ry).toInt()..(cy + ry + 1).toInt())
            for (x in (cx - rx).toInt()..(cx + rx + 1).toInt()) {
                val nx = (x + 0.5f - cx) / rx; val ny = (y + 0.5f - cy) / ry
                val inside = nx * nx + ny * ny <= 1f
                val ring = nx * nx + ny * ny >= ((rx - p.strokeWidth) / rx).let { it * it } && nx * nx + ny * ny <= ((rx + p.strokeWidth) / rx).let { it * it }
                if (p.style == Paint.Style.STROKE) { if (ring) blend(x, y, p.color, p) }
                else if (inside) blend(x, y, p.color, p)
            }
    }

    fun drawArc(rect: RectF, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint?) {
        val p = paint ?: return
        val cx = rect.centerX; val cy = rect.centerY
        val rx = rect.width / 2; val ry = rect.height / 2
        for (y in (cy - ry).toInt()..(cy + ry + 1).toInt())
            for (x in (cx - rx).toInt()..(cx + rx + 1).toInt()) {
                val nx = x + 0.5f - cx; val ny = y + 0.5f - cy
                val inOval = (nx / rx) * (nx / rx) + (ny / ry) * (ny / ry) <= 1f
                if (!inOval) continue
                var ang = Math.toDegrees(kotlin.math.atan2(ny.toDouble(), nx.toDouble()))
                if (ang < 0) ang += 360.0
                var a0 = startAngle % 360; if (a0 < 0) a0 += 360
                var rel = ang - a0; if (rel < 0) rel += 360
                val inArc = rel <= sweepAngle
                if (!inArc) continue
                val rr = (nx * nx + ny * ny)
                val fill = p.style != Paint.Style.STROKE || rr >= ((rx - p.strokeWidth) * (rx - p.strokeWidth)) || useCenter
                if (fill) blend(x, y, p.color, p)
            }
    }

    fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, paint: Paint?) = drawRoundRect(RectF(l, t, r, b), rx, ry, paint)

    fun drawPaint(paint: Paint?) {}

    fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint?) {
        val p = paint ?: return
        drawRect(rect.left + rx, rect.top, rect.right - rx, rect.bottom, p)
        drawRect(rect.left, rect.top + rx, rect.right, rect.bottom - rx, p)
        drawCircle(rect.left + rx, rect.top + rx, rx, p)
        drawCircle(rect.right - rx, rect.top + rx, rx, p)
        drawCircle(rect.left + rx, rect.bottom - rx, rx, p)
        drawCircle(rect.right - rx, rect.bottom - rx, rx, p)
    }

    fun drawPath(path: Path, paint: Paint?) {
        val p = paint ?: return
        val f = path.flattened()
        if (f.size < 6) return
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in f.indices step 2) { if (f[i + 1] < minY) minY = f[i + 1]; if (f[i + 1] > maxY) maxY = f[i + 1] }
        if (p.style == Paint.Style.STROKE) {
            for (i in 0 until f.size - 2 step 2) drawLine(f[i], f[i + 1], f[i + 2], f[i + 3], p)
            return
        }
        // even-odd scanline fill
        for (y in minY.toInt()..maxY.toInt()) {
            val yc = y + 0.5f
            val xs = ArrayList<Float>()
            for (i in 0 until f.size - 2 step 2) {
                val x0 = f[i]; val y0 = f[i + 1]; val x1 = f[i + 2]; val y1 = f[i + 3]
                if ((y0 <= yc && y1 > yc) || (y1 <= yc && y0 > yc)) {
                    xs.add(x0 + (yc - y0) / (y1 - y0) * (x1 - x0))
                }
            }
            xs.sort()
            var k = 0
            while (k + 1 < xs.size) {
                for (x in xs[k].toInt()..xs[k + 1].toInt()) blend(x, y, p.color, p)
                k += 2
            }
        }
    }

    fun drawBitmap(b: Bitmap, left: Float, top: Float, paint: Paint?) {
        val dst = bitmap ?: return
        for (y in 0 until b.height) for (x in 0 until b.width) {
            val c = b.pixels[y * b.width + x]
            if (c != 0) blend(left.toInt() + x, top.toInt() + y, c, paint)
        }
    }

    fun drawBitmap(b: Bitmap, src: Rect?, dst: RectF, paint: Paint?) {
        val s = src ?: Rect(0, 0, b.width, b.height)
        val scaled = Bitmap.createScaledBitmap(Bitmap.createBitmap(b, s.left.coerceIn(0, b.width - 1), s.top.coerceIn(0, b.height - 1),
            (s.width).coerceAtLeast(1), (s.height).coerceAtLeast(1)), dst.width.toInt().coerceAtLeast(1), dst.height.toInt().coerceAtLeast(1), false)
        drawBitmap(scaled, dst.left, dst.top, paint)
    }

    fun drawText(text: String, x: Float, y: Float, paint: Paint?) {
        val p = paint ?: return
        var cx = x
        for (ch in text) {
            val w = (p.textSize * 0.6f).toInt().coerceAtLeast(1)
            val h = (p.textSize * 0.2f).toInt().coerceAtLeast(1)
            drawRect(cx, y - h, cx + w, y, p)
            cx += w + 1
        }
    }
}
