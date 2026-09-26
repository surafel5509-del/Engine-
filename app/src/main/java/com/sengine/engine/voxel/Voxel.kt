package com.sengine.engine.voxel

import com.sengine.engine.core.VoxelWorld
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.math.floor

/** Block definitions. Atlas is 4x4 tiles. */
object Blocks {
    const val AIR = 0; const val GRASS = 1; const val DIRT = 2; const val STONE = 3; const val SAND = 4; const val WATER = 5
    const val WOOD = 6; const val LEAVES = 7; const val PLANKS = 8; const val BRICK = 9; const val GLASS = 10
    const val COBBLE = 11; const val SNOW = 12; const val BEDROCK = 13; const val GOLD = 14

    val names = listOf("Air", "Grass", "Dirt", "Stone", "Sand", "Water", "Wood", "Leaves", "Planks", "Brick", "Glass", "Cobblestone", "Snow", "Bedrock", "Gold Ore")

    /** tiles: top, side, bottom */
    private val tiles = arrayOf(
        intArrayOf(0, 0, 0), intArrayOf(0, 1, 2), intArrayOf(2, 2, 2), intArrayOf(3, 3, 3), intArrayOf(4, 4, 4), intArrayOf(5, 5, 5),
        intArrayOf(7, 6, 7), intArrayOf(8, 8, 8), intArrayOf(9, 9, 9), intArrayOf(10, 10, 10), intArrayOf(11, 11, 11),
        intArrayOf(12, 12, 12), intArrayOf(13, 13, 2), intArrayOf(14, 14, 14), intArrayOf(15, 15, 15),
    )

    fun tile(block: Int, face: Int): Int = tiles.getOrElse(block) { tiles[3] }[face]
    fun solid(b: Int) = b != AIR && b != WATER
    fun opaque(b: Int) = b != AIR && b != WATER && b != GLASS && b != LEAVES
    val count get() = names.size
}

/** Block storage + generation + meshing for one [VoxelWorld]. Pure Kotlin (testable on the JVM). */
class VoxelData(val sx: Int, val sy: Int, val sz: Int, val chunk: Int = 16) {
    val blocks = ByteArray(sx * sy * sz)
    val chunksX = (sx + chunk - 1) / chunk
    val chunksZ = (sz + chunk - 1) / chunk
    val dirty = BooleanArray(chunksX * chunksZ) { true }
    /** Built chunk vertex data (pos3 normal3 uv2); consumed by the renderer. */
    val meshes = arrayOfNulls<FloatArray>(chunksX * chunksZ)
    /** Incremented each time a chunk mesh is rebuilt (renderers compare against their uploaded copy). */
    val meshStamp = IntArray(chunksX * chunksZ)
    var version = 0
        private set

    fun inside(x: Int, y: Int, z: Int) = x in 0 until sx && y in 0 until sy && z in 0 until sz
    fun get(x: Int, y: Int, z: Int): Int = if (inside(x, y, z)) blocks[(y * sz + z) * sx + x].toInt() and 0xFF else if (y < 0) Blocks.BEDROCK else Blocks.AIR

    fun set(x: Int, y: Int, z: Int, b: Int) {
        if (!inside(x, y, z)) return
        blocks[(y * sz + z) * sx + x] = b.toByte()
        markDirty(x, z)
        if (x % chunk == 0) markDirty(x - 1, z)
        if (x % chunk == chunk - 1) markDirty(x + 1, z)
        if (z % chunk == 0) markDirty(x, z - 1)
        if (z % chunk == chunk - 1) markDirty(x, z + 1)
        version++
    }

    private fun markDirty(x: Int, z: Int) {
        if (x < 0 || z < 0 || x >= sx || z >= sz) return
        dirty[(z / chunk) * chunksX + x / chunk] = true
    }

    fun surfaceY(x: Int, z: Int): Int {
        for (y in sy - 1 downTo 0) if (Blocks.solid(get(x, y, z))) return y + 1
        return 0
    }

    // ------------------------------------------------------------------ generation
    private fun hash(x: Int, z: Int, seed: Int): Float {
        var h = x * 374761393 + z * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0x7FFFFFFF) / 2147483647f
    }

    private fun noise(x: Float, z: Float, seed: Int): Float {
        val x0 = floor(x).toInt(); val z0 = floor(z).toInt()
        val fx = x - x0; val fz = z - z0
        val ux = fx * fx * (3 - 2 * fx); val uz = fz * fz * (3 - 2 * fz)
        val a = hash(x0, z0, seed); val b = hash(x0 + 1, z0, seed); val c = hash(x0, z0 + 1, seed); val d = hash(x0 + 1, z0 + 1, seed)
        return (a + (b - a) * ux) + ((c + (d - c) * ux) - (a + (b - a) * ux)) * uz
    }

    fun fbm(x: Float, z: Float, seed: Int, octaves: Int = 4): Float {
        var amp = 1f; var freq = 1f; var sum = 0f; var norm = 0f
        for (i in 0 until octaves) { sum += noise(x * freq, z * freq, seed + i * 31) * amp; norm += amp; amp *= 0.5f; freq *= 2f }
        return sum / norm
    }

    fun generate(w: VoxelWorld) {
        val seed = w.seed
        val water = w.waterLevel.coerceAtMost(sy - 2)
        for (z in 0 until sz) for (x in 0 until sx) {
            val n = fbm(x / 48f * w.roughness, z / 48f * w.roughness, seed)
            val mountains = fbm(x / 90f, z / 90f, seed + 99, 3)
            var h = (6 + n * w.terrainHeight + (mountains - 0.5f).coerceAtLeast(0f) * w.terrainHeight * 1.6f).toInt()
            h = h.coerceIn(2, sy - 8)
            for (y in 0 until h) {
                val b = when {
                    y == 0 -> Blocks.BEDROCK
                    y < h - 4 -> if (hash(x * 7 + y, z * 3 - y, seed) > 0.985f && y < h - 6) Blocks.GOLD else Blocks.STONE
                    y < h - 1 -> if (h <= water + 1) Blocks.SAND else Blocks.DIRT
                    else -> when {
                        h <= water + 1 -> Blocks.SAND
                        h > water + w.terrainHeight * 0.95f -> Blocks.SNOW
                        else -> Blocks.GRASS
                    }
                }
                blocks[(y * sz + z) * sx + x] = b.toByte()
            }
            for (y in h until water) blocks[(y * sz + z) * sx + x] = Blocks.WATER.toByte()
        }
        if (w.trees) {
            for (z in 3 until sz - 3) for (x in 3 until sx - 3) {
                if (hash(x, z, seed + 7) < 0.985f) continue
                val y = surfaceY(x, z)
                if (get(x, y - 1, z) != Blocks.GRASS || y + 7 >= sy) continue
                val th = 4 + (hash(z, x, seed) * 2).toInt()
                for (k in 0 until th) blocks[((y + k) * sz + z) * sx + x] = Blocks.WOOD.toByte()
                for (dy in th - 2..th + 1) {
                    val r = if (dy >= th) 1 else 2
                    for (dz in -r..r) for (dx in -r..r) {
                        if (dx == 0 && dz == 0 && dy < th) continue
                        if (kotlin.math.abs(dx) == r && kotlin.math.abs(dz) == r && hash(x + dx, z + dz + dy, seed) > 0.5f) continue
                        val px = x + dx; val py = y + dy; val pz = z + dz
                        if (inside(px, py, pz) && get(px, py, pz) == Blocks.AIR) blocks[(py * sz + pz) * sx + px] = Blocks.LEAVES.toByte()
                    }
                }
            }
        }
        dirty.fill(true); version++
    }

    // ------------------------------------------------------------------ meshing
    private class Buf { var d = FloatArray(4096); var n = 0
        fun v(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float, vv: Float) {
            if (n + 8 > d.size) d = d.copyOf(d.size * 2)
            d[n++] = x; d[n++] = y; d[n++] = z; d[n++] = nx; d[n++] = ny; d[n++] = nz; d[n++] = u; d[n++] = vv
        }
    }

    private val faces = arrayOf(
        // nx,ny,nz, then 4 corners (x,y,z) counter-clockwise seen from outside, face index for tile (0 top,1 side,2 bottom)
        floatArrayOf(0f, 1f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 0f, 1f, 0f, 0f),
        floatArrayOf(0f, -1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 1f, 2f),
        floatArrayOf(1f, 0f, 0f, 1f, 0f, 1f, 1f, 0f, 0f, 1f, 1f, 0f, 1f, 1f, 1f, 1f),
        floatArrayOf(-1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f, 0f, 1f),
        floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 1f, 1f, 1f, 1f, 0f, 1f, 1f, 1f),
        floatArrayOf(0f, 0f, -1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f),
    )
    private val dirs = arrayOf(intArrayOf(0, 1, 0), intArrayOf(0, -1, 0), intArrayOf(1, 0, 0), intArrayOf(-1, 0, 0), intArrayOf(0, 0, 1), intArrayOf(0, 0, -1))

    /** Rebuilds up to [max] dirty chunks; returns how many were rebuilt. */
    fun rebuildDirty(max: Int): Int {
        var n = 0
        for (i in dirty.indices) {
            if (!dirty[i]) continue
            meshes[i] = buildChunk(i % chunksX, i / chunksX)
            meshStamp[i]++
            dirty[i] = false
            if (++n >= max) break
        }
        return n
    }

    fun buildChunk(cx: Int, cz: Int): FloatArray {
        val b = Buf()
        val t = 0.25f; val e = 0.002f
        for (y in 0 until sy) for (z in cz * chunk until minOf(sz, cz * chunk + chunk)) for (x in cx * chunk until minOf(sx, cx * chunk + chunk)) {
            val id = get(x, y, z)
            if (id == Blocks.AIR) continue
            for (f in 0 until 6) {
                val d = dirs[f]
                val nb = get(x + d[0], y + d[1], z + d[2])
                val visible = if (id == Blocks.WATER) nb == Blocks.AIR && f == 0 else !Blocks.opaque(nb) && nb != id
                if (!visible) continue
                val fd = faces[f]
                val tile = Blocks.tile(id, fd[15].toInt())
                val u0 = (tile % 4) * t + e; val u1 = (tile % 4 + 1) * t - e
                val v0 = (tile / 4) * t + e; val v1 = (tile / 4 + 1) * t - e
                val top = if (id == Blocks.WATER) 0.88f else 1f
                fun c(k: Int) = floatArrayOf(x + fd[3 + k * 3], y + fd[4 + k * 3] * top, z + fd[5 + k * 3])
                val p0 = c(0); val p1 = c(1); val p2 = c(2); val p3 = c(3)
                // side faces: v1 at bottom (y=0), v0 at top
                val uv = if (f >= 2) arrayOf(floatArrayOf(u0, v1), floatArrayOf(u1, v1), floatArrayOf(u1, v0), floatArrayOf(u0, v0))
                else arrayOf(floatArrayOf(u0, v1), floatArrayOf(u1, v1), floatArrayOf(u1, v0), floatArrayOf(u0, v0))
                for (k in intArrayOf(0, 1, 2, 0, 2, 3)) {
                    val p = when (k) { 0 -> p0; 1 -> p1; 2 -> p2; else -> p3 }
                    b.v(p[0], p[1], p[2], fd[0], fd[1], fd[2], uv[k][0], uv[k][1])
                }
            }
        }
        return b.d.copyOf(b.n)
    }

    // ------------------------------------------------------------------ queries
    class Hit(val x: Int, val y: Int, val z: Int, val nx: Int, val ny: Int, val nz: Int, val block: Int, val distance: Float)

    /** Voxel DDA ray cast in local block coordinates. */
    fun raycast(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, maxDist: Float): Hit? {
        var x = floor(ox).toInt(); var y = floor(oy).toInt(); var z = floor(oz).toInt()
        val stepX = if (dx > 0) 1 else -1; val stepY = if (dy > 0) 1 else -1; val stepZ = if (dz > 0) 1 else -1
        val tdx = if (dx != 0f) kotlin.math.abs(1f / dx) else Float.MAX_VALUE
        val tdy = if (dy != 0f) kotlin.math.abs(1f / dy) else Float.MAX_VALUE
        val tdz = if (dz != 0f) kotlin.math.abs(1f / dz) else Float.MAX_VALUE
        var tmx = if (dx != 0f) ((if (dx > 0) x + 1 - ox else ox - x) * tdx) else Float.MAX_VALUE
        var tmy = if (dy != 0f) ((if (dy > 0) y + 1 - oy else oy - y) * tdy) else Float.MAX_VALUE
        var tmz = if (dz != 0f) ((if (dz > 0) z + 1 - oz else oz - z) * tdz) else Float.MAX_VALUE
        var nx = 0; var ny = 0; var nz = 0; var t = 0f
        while (t <= maxDist) {
            val b = get(x, y, z)
            if (b != Blocks.AIR && b != Blocks.WATER && inside(x, y, z)) return Hit(x, y, z, nx, ny, nz, b, t)
            if (tmx < tmy && tmx < tmz) { x += stepX; t = tmx; tmx += tdx; nx = -stepX; ny = 0; nz = 0 }
            else if (tmy < tmz) { y += stepY; t = tmy; tmy += tdy; nx = 0; ny = -stepY; nz = 0 }
            else { z += stepZ; t = tmz; tmz += tdz; nx = 0; ny = 0; nz = -stepZ }
        }
        return null
    }

    // ------------------------------------------------------------------ persistence
    fun save(file: File) {
        val def = Deflater(Deflater.BEST_SPEED); def.setInput(blocks); def.finish()
        val out = ByteArrayOutputStream(); val buf = ByteArray(65536)
        while (!def.finished()) { val n = def.deflate(buf); out.write(buf, 0, n) }
        file.parentFile?.mkdirs(); file.writeBytes(out.toByteArray())
    }

    fun load(file: File): Boolean {
        if (!file.exists()) return false
        return try {
            val inf = Inflater(); inf.setInput(file.readBytes())
            var off = 0
            while (!inf.finished() && off < blocks.size) { val n = inf.inflate(blocks, off, blocks.size - off); if (n == 0 && inf.needsInput()) break; off += n }
            dirty.fill(true); version++; true
        } catch (_: Exception) { false }
    }

    companion object {
        fun create(w: VoxelWorld): VoxelData = VoxelData(w.chunksX * 16, w.height, w.chunksZ * 16).also { it.generate(w) }
    }
}
