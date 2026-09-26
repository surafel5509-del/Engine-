package com.sengine.engine.texture

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Procedural, seamlessly tiling texture generator (pure Kotlin).
 * Used by the Texture Studio, the AI agent (generate_texture) and the store.
 */
object TextureGen {
    val STYLES = listOf(
        "Noise", "Clouds", "Bricks", "Tiles", "Wood", "Planks", "Marble", "Stone", "Cobblestone", "Grass",
        "Dirt", "Sand", "Snow", "Water", "Lava", "Metal", "Rust", "Camo", "Checker", "Stripes",
        "Hex", "Circuit", "Fabric", "Crystal", "Gradient", "Asphalt", "Concrete", "Leaves", "Ice", "Fire"
    )

    class Params(
        var style: String = "Noise",
        var size: Int = 128,
        var colorA: Int = 0xFF3A3A3A.toInt(),
        var colorB: Int = 0xFFBDBDBD.toInt(),
        var scale: Float = 4f,
        var seed: Int = 1,
        var contrast: Float = 1f,
        var roughness: Float = 0.5f,
    )

    /** Suggested colours for a style (A = dark, B = light). */
    fun defaults(style: String): Pair<Int, Int> = when (style) {
        "Bricks" -> 0xFF5A2E22.toInt() to 0xFFB5563A.toInt()
        "Wood", "Planks" -> 0xFF5B3A1E.toInt() to 0xFFB07A45.toInt()
        "Marble" -> 0xFF8A8A8A.toInt() to 0xFFF4F4F4.toInt()
        "Stone", "Cobblestone" -> 0xFF4A4A4E.toInt() to 0xFF9A9AA0.toInt()
        "Grass" -> 0xFF2E5E1E.toInt() to 0xFF6DBE45.toInt()
        "Leaves" -> 0xFF1C4A18.toInt() to 0xFF4E9A2E.toInt()
        "Dirt" -> 0xFF3E2A1A.toInt() to 0xFF7A5634.toInt()
        "Sand" -> 0xFFC2A56B.toInt() to 0xFFEBD9A4.toInt()
        "Snow" -> 0xFFC8D6E6.toInt() to 0xFFFFFFFF.toInt()
        "Ice" -> 0xFF7FB6E0.toInt() to 0xFFE6F6FF.toInt()
        "Water" -> 0xFF0B3D6E.toInt() to 0xFF3FA3E0.toInt()
        "Lava", "Fire" -> 0xFF5A0A00.toInt() to 0xFFFFC23A.toInt()
        "Metal" -> 0xFF5C6066.toInt() to 0xFFC9CED6.toInt()
        "Rust" -> 0xFF4A1E0C.toInt() to 0xFFB5602A.toInt()
        "Camo" -> 0xFF3B4A2A.toInt() to 0xFF8A8A5A.toInt()
        "Circuit" -> 0xFF06240F.toInt() to 0xFF36E07A.toInt()
        "Crystal" -> 0xFF2A1060.toInt() to 0xFFB58CFF.toInt()
        "Asphalt" -> 0xFF222326.toInt() to 0xFF55575C.toInt()
        "Concrete" -> 0xFF6E6E6E.toInt() to 0xFFA8A8A8.toInt()
        "Fabric" -> 0xFF28324A.toInt() to 0xFF5A6E9A.toInt()
        else -> 0xFF202020.toInt() to 0xFFE0E0E0.toInt()
    }

    // ---- tileable value noise
    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0x7FFFFFFF) / 2147483647f
    }

    private fun smooth(t: Float) = t * t * (3 - 2 * t)

    /** Value noise with integer [period] so the result tiles. */
    fun noise(x: Float, y: Float, period: Int, seed: Int): Float {
        val xi = floor(x).toInt(); val yi = floor(y).toInt()
        val fx = smooth(x - xi); val fy = smooth(y - yi)
        fun h(a: Int, b: Int) = hash(Math.floorMod(a, period), Math.floorMod(b, period), seed)
        val a = h(xi, yi); val b = h(xi + 1, yi); val c = h(xi, yi + 1); val d = h(xi + 1, yi + 1)
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy
    }

    fun fbm(u: Float, v: Float, base: Int, octaves: Int, seed: Int, rough: Float = 0.5f): Float {
        var sum = 0f; var amp = 1f; var tot = 0f; var p = base
        for (o in 0 until octaves) {
            sum += noise(u * p, v * p, p, seed + o * 17) * amp
            tot += amp; amp *= rough; p *= 2
        }
        return sum / tot
    }

    private fun cellular(u: Float, v: Float, n: Int, seed: Int): Pair<Float, Float> {
        val x = u * n; val y = v * n
        val xi = floor(x).toInt(); val yi = floor(y).toInt()
        var d1 = 9f; var d2 = 9f
        for (j in -1..1) for (i in -1..1) {
            val cx = xi + i; val cy = yi + j
            val px = cx + hash(Math.floorMod(cx, n), Math.floorMod(cy, n), seed)
            val py = cy + hash(Math.floorMod(cx, n), Math.floorMod(cy, n), seed + 99)
            val d = sqrt((px - x) * (px - x) + (py - y) * (py - y))
            if (d < d1) { d2 = d1; d1 = d } else if (d < d2) d2 = d
        }
        return d1 to d2
    }

    fun mix(a: Int, b: Int, t0: Float): Int {
        val t = t0.coerceIn(0f, 1f)
        fun ch(s: Int) = (((a shr s) and 0xFF) + ((((b shr s) and 0xFF) - ((a shr s) and 0xFF)) * t)).toInt().coerceIn(0, 255)
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun shade(c: Int, k: Float): Int {
        fun ch(s: Int) = (((c shr s) and 0xFF) * k).toInt().coerceIn(0, 255)
        return (c and 0xFF000000.toInt()) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Generates ARGB pixels (size × size). */
    fun generate(p: Params): IntArray {
        val n = p.size.coerceIn(8, 1024)
        val out = IntArray(n * n)
        val sc = p.scale.coerceIn(1f, 32f).toInt().coerceAtLeast(1)
        val s = p.seed
        for (y in 0 until n) for (x in 0 until n) {
            val u = x / n.toFloat(); val v = y / n.toFloat()
            val c: Int = when (p.style) {
                "Clouds" -> mix(p.colorA, p.colorB, fbm(u, v, sc, 5, s, 0.55f) * 1.4f - 0.2f)
                "Bricks" -> {
                    val rows = sc * 2; val cols = sc
                    val ry = v * rows; val row = floor(ry).toInt()
                    val rx = u * cols + if (row % 2 == 1) 0.5f else 0f
                    val fx = rx - floor(rx); val fy = ry - row
                    val mortar = fx < 0.05f || fy < 0.1f
                    val tint = 0.8f + 0.35f * hash(Math.floorMod(floor(rx).toInt(), cols), row, s)
                    if (mortar) shade(0xFFB8B0A0.toInt(), 0.75f + 0.2f * fbm(u, v, 16, 2, s))
                    else shade(mix(p.colorA, p.colorB, 0.4f + 0.6f * fbm(u, v, 8, 3, s)), tint)
                }
                "Tiles" -> {
                    val fx = u * sc - floor(u * sc); val fy = v * sc - floor(v * sc)
                    val edge = minOf(fx, fy, 1 - fx, 1 - fy)
                    if (edge < 0.04f) shade(p.colorA, 0.6f)
                    else shade(mix(p.colorB, p.colorA, fbm(u, v, 8, 3, s) * 0.4f), if (edge < 0.08f) 1.12f else 1f)
                }
                "Wood" -> {
                    val w = fbm(u, v, 2, 3, s) * 6f
                    val ring = sin((u * sc * 6 + w) * Math.PI.toFloat() * 2) * 0.5f + 0.5f
                    mix(p.colorA, p.colorB, ring * 0.7f + fbm(u * 1f, v * 4f, 8, 3, s) * 0.3f)
                }
                "Planks" -> {
                    val py = v * sc; val row = floor(py).toInt(); val fy = py - row
                    val grain = sin((u * 40 + fbm(u, v, 4, 3, s + row) * 8) ) * 0.5f + 0.5f
                    val base = mix(p.colorA, p.colorB, 0.35f + grain * 0.4f + hash(row, 1, s) * 0.25f)
                    if (fy < 0.05f) shade(base, 0.45f) else base
                }
                "Marble" -> {
                    val t = abs(sin((u * sc + fbm(u, v, 4, 5, s) * 5f) * Math.PI.toFloat()))
                    mix(p.colorA, p.colorB, Math.pow(t.toDouble(), 0.3).toFloat())
                }
                "Stone", "Concrete", "Asphalt" -> {
                    val f = fbm(u, v, sc, 6, s, p.roughness.coerceIn(0.3f, 0.8f))
                    var c0 = mix(p.colorA, p.colorB, (f - 0.2f) * 1.6f)
                    if (p.style == "Asphalt" && hash(x, y, s) > 0.97f) c0 = shade(c0, 1.5f)
                    c0
                }
                "Cobblestone" -> {
                    val (d1, d2) = cellular(u, v, sc * 2, s)
                    val gap = d2 - d1
                    if (gap < 0.08f) shade(p.colorA, 0.5f) else mix(p.colorA, p.colorB, 0.4f + (1 - d1) * 0.5f + fbm(u, v, 16, 2, s) * 0.2f)
                }
                "Grass", "Leaves" -> {
                    val f = fbm(u, v, sc * 2, 4, s)
                    val blade = hash(x, y, s)
                    mix(p.colorA, p.colorB, f * 0.8f + blade * 0.35f - 0.1f)
                }
                "Dirt", "Sand", "Snow" -> mix(p.colorA, p.colorB, fbm(u, v, sc, 4, s) * 0.8f + hash(x, y, s) * 0.3f)
                "Water", "Ice" -> {
                    val (d1, _) = cellular(u, v, sc, s)
                    val f = fbm(u, v, sc, 4, s)
                    mix(p.colorA, p.colorB, f * 0.6f + (if (p.style == "Water") (1 - d1) * (1 - d1) * 0.6f else d1 * 0.4f))
                }
                "Lava", "Fire" -> {
                    val f = fbm(u, v, sc, 5, s, 0.6f)
                    val t = if (p.style == "Lava") Math.pow(f.toDouble(), 2.5).toFloat() * 2.2f else (f * (1.2f - v)) * 1.8f
                    if (t > 0.7f) mix(p.colorB, 0xFFFFFFE0.toInt(), (t - 0.7f) * 2f) else mix(p.colorA, p.colorB, t / 0.7f)
                }
                "Metal" -> {
                    val brush = fbm(u * 0.1f, v * 8f, sc, 3, s) * 0.3f + hash(x, y / 4, s) * 0.08f
                    mix(p.colorA, p.colorB, 0.45f + brush + 0.15f * sin(v * 6.28f))
                }
                "Rust" -> {
                    val f = fbm(u, v, sc, 5, s)
                    if (f > 0.52f) mix(p.colorA, p.colorB, (f - 0.5f) * 3f) else mix(0xFF6A6E72.toInt(), 0xFF9AA0A6.toInt(), f)
                }
                "Camo" -> {
                    val f1 = fbm(u, v, sc, 3, s); val f2 = fbm(u, v, sc, 3, s + 5)
                    when { f1 > 0.58f -> p.colorA; f2 > 0.55f -> p.colorB; f1 < 0.4f -> shade(p.colorA, 0.6f); else -> mix(p.colorA, p.colorB, 0.5f) }
                }
                "Checker" -> if ((floor(u * sc * 2).toInt() + floor(v * sc * 2).toInt()) % 2 == 0) p.colorA else p.colorB
                "Stripes" -> if (floor((u + v) * sc * 2).toInt() % 2 == 0) p.colorA else p.colorB
                "Hex" -> {
                    val (d1, d2) = cellular(u, v, sc * 2, s + 1000)
                    if (d2 - d1 < 0.06f) p.colorA else mix(p.colorB, p.colorA, d1 * 0.5f)
                }
                "Circuit" -> {
                    val g = sc * 4
                    val cx = floor(u * g).toInt(); val cy = floor(v * g).toInt()
                    val fx = u * g - cx; val fy = v * g - cy
                    val hRun = hash(cx, cy, s) > 0.5f
                    val line = if (hRun) abs(fy - 0.5f) < 0.08f else abs(fx - 0.5f) < 0.08f
                    val pad = hash(cx, cy, s + 3) > 0.85f && (fx - 0.5f) * (fx - 0.5f) + (fy - 0.5f) * (fy - 0.5f) < 0.06f
                    if (line || pad) p.colorB else shade(p.colorA, 0.9f + fbm(u, v, 8, 2, s) * 0.3f)
                }
                "Fabric" -> {
                    val wx = sin(u * n * 0.8f) * 0.5f + 0.5f; val wy = sin(v * n * 0.8f) * 0.5f + 0.5f
                    mix(p.colorA, p.colorB, (if ((x / 2 + y / 2) % 2 == 0) wx else wy) * 0.6f + fbm(u, v, 8, 2, s) * 0.3f)
                }
                "Crystal" -> {
                    val (d1, d2) = cellular(u, v, sc, s)
                    mix(p.colorA, p.colorB, (d2 - d1) * 2f + d1 * 0.3f)
                }
                "Gradient" -> mix(p.colorA, p.colorB, v)
                else -> mix(p.colorA, p.colorB, fbm(u, v, sc, 4, s, p.roughness))
            }
            out[y * n + x] = if (p.contrast != 1f) contrast(c, p.contrast) else c
        }
        return out
    }

    private fun contrast(c: Int, k: Float): Int {
        fun ch(s: Int) = ((((c shr s) and 0xFF) - 128) * k + 128).toInt().coerceIn(0, 255)
        return (c and 0xFF000000.toInt()) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Builds a tangent-space normal map from the luminance of [src] (for lit 3D materials). */
    fun normalMap(src: IntArray, n: Int, strength: Float = 2f): IntArray {
        fun lum(x: Int, y: Int): Float { val c = src[Math.floorMod(y, n) * n + Math.floorMod(x, n)]; return (((c shr 16) and 0xFF) * 0.3f + ((c shr 8) and 0xFF) * 0.59f + (c and 0xFF) * 0.11f) / 255f }
        val out = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (lum(x + 1, y) - lum(x - 1, y)) * strength
            val dy = (lum(x, y + 1) - lum(x, y - 1)) * strength
            val len = sqrt(dx * dx + dy * dy + 1f)
            val r = ((-dx / len) * 0.5f + 0.5f) * 255; val g = ((-dy / len) * 0.5f + 0.5f) * 255; val b = ((1f / len) * 0.5f + 0.5f) * 255
            out[y * n + x] = (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
        }
        return out
    }

    fun png(p: Params): ByteArray = PngEncoder.encode(p.size, p.size, generate(p))

    /** Parses "#RRGGBB" / "#AARRGGBB" / named colours used by the agent. */
    fun parseColor(s: String?, fallback: Int): Int {
        if (s.isNullOrBlank()) return fallback
        val named = mapOf("black" to 0xFF000000, "white" to 0xFFFFFFFF, "red" to 0xFFE53935, "green" to 0xFF43A047, "blue" to 0xFF1E88E5,
            "yellow" to 0xFFFDD835, "orange" to 0xFFFB8C00, "purple" to 0xFF8E24AA, "gray" to 0xFF808080, "grey" to 0xFF808080,
            "brown" to 0xFF6D4C41, "cyan" to 0xFF00ACC1, "pink" to 0xFFEC407A, "gold" to 0xFFFFC107)
        named[s.lowercase()]?.let { return it.toInt() }
        return try { var h = s.trim().removePrefix("#"); if (h.length == 6) h = "FF$h"; h.toLong(16).toInt() } catch (_: Exception) { fallback }
    }
}
