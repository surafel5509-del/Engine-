package com.sengine.engine.voxel

import android.graphics.Bitmap

/** Procedural 64x64 block atlas (4x4 tiles of 16px) matching [Blocks] tile indices. */
object VoxelAtlas {
    const val TILE = 16

    fun create(): Bitmap {
        val px = IntArray(64 * 64)
        for (t in 0 until 16) {
            val ox = (t % 4) * TILE; val oy = (t / 4) * TILE
            for (y in 0 until TILE) for (x in 0 until TILE) px[(oy + y) * 64 + ox + x] = pixel(t, x, y)
        }
        return Bitmap.createBitmap(px, 64, 64, Bitmap.Config.ARGB_8888)
    }

    fun rnd(t: Int, x: Int, y: Int): Float {
        var h = x * 73856093 xor (y * 19349663) xor (t * 83492791)
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private fun rgb(r: Float, g: Float, b: Float, a: Float = 1f): Int =
        ((a.coerceIn(0f, 1f) * 255).toInt() shl 24) or ((r.coerceIn(0f, 1f) * 255).toInt() shl 16) or ((g.coerceIn(0f, 1f) * 255).toInt() shl 8) or (b.coerceIn(0f, 1f) * 255).toInt()

    private fun shade(c: FloatArray, n: Float, amt: Float, a: Float = 1f) = rgb(c[0] * (1 - amt + n * amt * 2), c[1] * (1 - amt + n * amt * 2), c[2] * (1 - amt + n * amt * 2), a)

    private val GRASS = floatArrayOf(0.36f, 0.66f, 0.25f)
    private val DIRT = floatArrayOf(0.53f, 0.37f, 0.24f)
    private val STONE = floatArrayOf(0.5f, 0.5f, 0.52f)
    private val SAND = floatArrayOf(0.88f, 0.82f, 0.58f)
    private val WATER = floatArrayOf(0.2f, 0.45f, 0.85f)
    private val BARK = floatArrayOf(0.42f, 0.3f, 0.18f)
    private val WOOD = floatArrayOf(0.72f, 0.56f, 0.34f)
    private val LEAVES = floatArrayOf(0.2f, 0.52f, 0.2f)
    private val BRICK = floatArrayOf(0.66f, 0.26f, 0.2f)
    private val SNOW = floatArrayOf(0.94f, 0.96f, 1f)

    fun pixel(t: Int, x: Int, y: Int): Int {
        val n = rnd(t, x, y)
        return when (t) {
            0 -> shade(GRASS, n, 0.18f)
            1 -> {
                val edge = 3 + (rnd(99, x, 0) * 3).toInt()
                if (y < edge) shade(GRASS, n, 0.18f) else shade(DIRT, n, 0.2f)
            }
            2 -> shade(DIRT, n, 0.22f)
            3 -> if (rnd(7, x / 3, y / 3) > 0.8f) shade(STONE, n, 0.1f).let { darken(it, 0.75f) } else shade(STONE, n, 0.14f)
            4 -> shade(SAND, n, 0.08f)
            5 -> {
                val wave = if ((x + y * 2 + (rnd(5, 0, y) * 4).toInt()) % 7 == 0) 1.25f else 1f
                rgb(WATER[0] * wave, WATER[1] * wave, WATER[2] * wave, 0.72f)
            }
            6 -> { val stripe = if (x % 4 == 0) 0.7f else 1f; shade(floatArrayOf(BARK[0] * stripe, BARK[1] * stripe, BARK[2] * stripe), n, 0.12f) }
            7 -> {
                val dx = x - 7.5f; val dy = y - 7.5f
                val r = kotlin.math.sqrt(dx * dx + dy * dy)
                if (r > 7f) shade(BARK, n, 0.1f) else { val ring = if (r.toInt() % 3 == 0) 0.82f else 1f; shade(floatArrayOf(WOOD[0] * ring, WOOD[1] * ring, WOOD[2] * ring), n, 0.08f) }
            }
            8 -> if (n > 0.82f) rgb(0.12f, 0.35f, 0.12f) else shade(LEAVES, rnd(8, x / 2, y / 2), 0.3f)
            9 -> { val line = y % 4 == 3 || (x == (if ((y / 4) % 2 == 0) 5 else 12)); if (line) shade(WOOD, n, 0.05f).let { darken(it, 0.62f) } else shade(WOOD, n, 0.1f) }
            10 -> {
                val row = y / 4; val off = if (row % 2 == 0) 0 else 4
                val mortar = y % 4 == 3 || (x + off) % 8 == 7
                if (mortar) rgb(0.78f, 0.76f, 0.72f) else shade(BRICK, n, 0.14f)
            }
            11 -> {
                val border = x == 0 || y == 0 || x == 15 || y == 15
                if (border) rgb(0.82f, 0.93f, 0.98f) else if ((x == y + 3 || x == y + 4) && x in 4..12) rgb(1f, 1f, 1f, 0.55f) else rgb(0.8f, 0.9f, 1f, 0f)
            }
            12 -> {
                val cell = rnd(12, (x + (y / 5) * 3) / 5, y / 5)
                val edge = x % 5 == 0 || y % 5 == 0
                if (edge) rgb(0.3f, 0.3f, 0.32f) else shade(floatArrayOf(0.45f + cell * 0.15f, 0.45f + cell * 0.15f, 0.47f + cell * 0.15f), n, 0.08f)
            }
            13 -> shade(SNOW, n, 0.04f)
            14 -> shade(floatArrayOf(0.2f, 0.2f, 0.22f), n, 0.45f)
            15 -> if (rnd(15, x / 2, y / 2) > 0.78f) rgb(1f, 0.82f, 0.2f + n * 0.2f) else shade(STONE, n, 0.14f)
            else -> rgb(1f, 0f, 1f)
        }
    }

    private fun darken(c: Int, f: Float): Int {
        val a = c ushr 24
        val r = ((c shr 16) and 0xFF) * f; val g = ((c shr 8) and 0xFF) * f; val b = (c and 0xFF) * f
        return (a shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }
}
