package com.sengine.engine.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * One grid layer of a [Tilemap]. `cells` holds atlas indices in row-major order,
 * starting at the bottom-left of the map; `-1` (or any negative value) is an empty cell.
 */
class TileLayer(
    var name: String = "Layer",
    var cols: Int = 1,
    var rows: Int = 1,
    cells: IntArray = IntArray(cols * rows) { -1 },
) {
    var visible = true
    var opacity = 1f
    /** When true every non-empty cell of this layer blocks 2D physics. */
    var solid = false
    var cells: IntArray = if (cells.size == cols * rows) cells else IntArray(cols * rows) { -1 }

    fun inBounds(col: Int, row: Int) = col in 0 until cols && row in 0 until rows
    fun index(col: Int, row: Int) = row * cols + col

    fun get(col: Int, row: Int): Int = if (inBounds(col, row)) cells[index(col, row)] else -1

    fun set(col: Int, row: Int, tile: Int) { if (inBounds(col, row)) cells[index(col, row)] = tile }

    fun fill(tile: Int) { java.util.Arrays.fill(cells, tile) }
    fun clear() = fill(-1)
    fun isEmpty(): Boolean = cells.none { it >= 0 }
    fun count(tile: Int): Int = cells.count { it == tile }

    fun copy(): TileLayer = TileLayer(name, cols, rows, cells.copyOf()).also {
        it.visible = visible; it.opacity = opacity; it.solid = solid
    }

    /** Resizes the grid, keeping the existing cells anchored at the bottom-left. */
    fun resize(newCols: Int, newRows: Int) {
        val nc = newCols.coerceIn(1, 4096); val nr = newRows.coerceIn(1, 4096)
        if (nc == cols && nr == rows) return
        val next = IntArray(nc * nr) { -1 }
        for (row in 0 until minOf(rows, nr)) for (col in 0 until minOf(cols, nc)) {
            next[row * nc + col] = cells[row * cols + col]
        }
        cells = next; cols = nc; rows = nr
    }

    /** Horizontal runs of non-empty cells as `[col, row, length, tile]` (used by the renderer and physics). */
    fun runs(): List<IntArray> {
        val out = ArrayList<IntArray>()
        for (row in 0 until rows) {
            var col = 0
            while (col < cols) {
                val t = cells[row * cols + col]
                if (t < 0) { col++; continue }
                var len = 1
                while (col + len < cols && cells[row * cols + col + len] == t) len++
                out.add(intArrayOf(col, row, len, t))
                col += len
            }
        }
        return out
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("cols", cols)
        put("rows", rows)
        put("visible", visible)
        put("opacity", opacity.toDouble())
        put("solid", solid)
        put("data", Tilemap.encode(cells))
    }

    companion object {
        fun fromJson(o: JSONObject): TileLayer {
            val cols = o.optInt("cols", 1).coerceAtLeast(1)
            val rows = o.optInt("rows", 1).coerceAtLeast(1)
            val l = TileLayer(
                o.optString("name", "Layer"),
                cols,
                rows,
                Tilemap.decode(o.optString("data", ""), cols * rows),
            )
            l.visible = o.optBoolean("visible", true)
            l.opacity = o.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f)
            l.solid = o.optBoolean("solid", false)
            return l
        }
    }
}

/**
 * A tilemap document — the data behind a `.tmap` asset. It is deliberately free of any Android
 * or editor dependency so it can be unit tested on the JVM and reused by the renderer, the
 * tilemap editor and (later) the physics world.
 *
 * Coordinates: cell (0, 0) is the bottom-left of the map and cell (col, row) occupies
 * `[col, col+1) x [row, row+1)` in tile units, measured from the owning object's origin.
 */
class Tilemap(
    var tileset: String = "",
    var tileW: Int = 16,
    var tileH: Int = 16,
    var layers: MutableList<TileLayer> = mutableListOf(),
) {
    /** Atlas cells that block physics, on top of the per-layer [TileLayer.solid] flag. */
    val solidTiles = LinkedHashSet<Int>()

    val cols: Int get() = layers.firstOrNull()?.cols ?: 0
    val rows: Int get() = layers.firstOrNull()?.rows ?: 0

    fun isEmpty(): Boolean = layers.all { it.isEmpty() }

    /** Topmost non-empty tile at a cell (used by the eyedropper), or -1. */
    fun at(col: Int, row: Int): Int {
        for (i in layers.indices.reversed()) {
            val l = layers[i]
            if (!l.visible) continue
            val t = l.get(col, row)
            if (t >= 0) return t
        }
        return -1
    }

    /** True when 2D physics should treat the cell as solid. */
    fun solidAt(col: Int, row: Int): Boolean {
        for (l in layers) {
            if (!l.visible) continue
            val t = l.get(col, row)
            if (t < 0) continue
            if (l.solid || t in solidTiles) return true
        }
        return false
    }

    /**
     * Collision geometry: horizontal runs of solid cells merged across rows into boxes,
     * as `floatArrayOf(x, y, w, h)` in tile units (origin at the map's bottom-left).
     */
    fun solidRects(): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        val runs = HashMap<Int, MutableList<IntArray>>() // row -> runs of solid cells
        for (row in 0 until rows) {
            var col = 0
            while (col < cols) {
                if (!solidAt(col, row)) { col++; continue }
                var len = 1
                while (col + len < cols && solidAt(col + len, row)) len++
                runs.getOrPut(row) { ArrayList() }.add(intArrayOf(col, len))
                col += len
            }
        }
        // Merge identical runs that are vertically adjacent, so a floor becomes one box.
        fun key(row: Int, col: Int, len: Int): Long = (row.toLong() shl 40) or (col.toLong() shl 20) or len.toLong()
        val used = HashSet<Long>()
        for (row in 0 until rows) {
            for (r in runs[row] ?: emptyList()) {
                if (key(row, r[0], r[1]) in used) continue
                var h = 1
                while (true) {
                    val next = runs[row + h] ?: break
                    if (next.none { it[0] == r[0] && it[1] == r[1] }) break
                    used.add(key(row + h, r[0], r[1]))
                    h++
                }
                out.add(floatArrayOf(r[0].toFloat(), row.toFloat(), r[1].toFloat(), h.toFloat()))
            }
        }
        return out
    }

    fun setSize(newCols: Int, newRows: Int) { for (l in layers) l.resize(newCols, newRows) }

    fun usedTiles(): Set<Int> {
        val out = LinkedHashSet<Int>()
        for (l in layers) for (c in l.cells) if (c >= 0) out.add(c)
        return out
    }

    fun copy(): Tilemap = Tilemap(tileset, tileW, tileH, layers.map { it.copy() }.toMutableList()).also {
        it.solidTiles.addAll(solidTiles)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("tileset", tileset)
        put("tileW", tileW)
        put("tileH", tileH)
        val arr = JSONArray()
        for (l in layers) arr.put(l.toJson())
        put("layers", arr)
        val st = JSONArray()
        for (t in solidTiles) st.put(t)
        put("solidTiles", st)
    }

    companion object {
        const val EXT = "tmap"
        const val VERSION = 1

        fun fromJson(o: JSONObject): Tilemap {
            val m = Tilemap(
                o.optString("tileset", ""),
                o.optInt("tileW", 16).coerceIn(1, 1024),
                o.optInt("tileH", 16).coerceIn(1, 1024),
            )
            val arr = o.optJSONArray("layers") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val l = arr.optJSONObject(i) ?: continue
                m.layers.add(TileLayer.fromJson(l))
            }
            val st = o.optJSONArray("solidTiles")
            if (st != null) for (i in 0 until st.length()) m.solidTiles.add(st.optInt(i))
            return m
        }

        /** A blank two-layer map, ready to paint. */
        fun starter(tileset: String = "", tileW: Int = 16, tileH: Int = 16, cols: Int = 30, rows: Int = 18): Tilemap =
            Tilemap(tileset, tileW, tileH, mutableListOf(
                TileLayer("Background", cols, rows),
                TileLayer("Ground", cols, rows).also { it.solid = true },
            ))

        /**
         * Run-length codec for layer data: comma separated `value` or `value*count` tokens,
         * where `-1` is an empty cell. Keeps `.tmap` files small for sparse levels.
         */
        fun encode(cells: IntArray): String {
            if (cells.isEmpty()) return ""
            val sb = StringBuilder()
            var i = 0
            while (i < cells.size) {
                val v = cells[i]
                var n = 1
                while (i + n < cells.size && cells[i + n] == v) n++
                if (sb.isNotEmpty()) sb.append(',')
                sb.append(v)
                if (n > 1) sb.append('*').append(n)
                i += n
            }
            return sb.toString()
        }

        fun decode(data: String, size: Int): IntArray {
            val out = IntArray(size) { -1 }
            if (data.isBlank()) return out
            var i = 0
            for (token in data.split(',')) {
                if (token.isEmpty()) continue
                val star = token.indexOf('*')
                if (star < 0) {
                    if (i < size) out[i] = token.toIntOrNull() ?: -1
                    i++
                } else {
                    val v = token.substring(0, star).toIntOrNull() ?: -1
                    val n = token.substring(star + 1).toIntOrNull() ?: 1
                    for (k in 0 until n.coerceAtLeast(0)) { if (i < size) out[i] = v; i++ }
                }
                if (i >= size) break
            }
            return out
        }
    }
}

/**
 * Places a tilemap asset ([Tilemap], `.tmap`) in the scene. The document is loaded lazily and
 * cached by the editor and the renderer, mirroring how `VoxelWorld` caches its generated data.
 */
class TileMap : Component() {
    override val type = "TileMap"

    /** `.tmap` asset name. */
    var map = ""
    /** World units per tile. */
    var tileSize = 1f
    var opacity = 1f
    var tint = 0xFFFFFFFF.toInt()

    // runtime
    @Transient var doc: Tilemap? = null
    @Transient var docKey = ""

    /** Loads (and caches) the document; [key] should change whenever the asset file changes. */
    fun ensure(key: String, load: () -> Tilemap?): Tilemap? {
        if (doc != null && docKey == key) return doc
        val loaded = load()
        if (loaded != null) { doc = loaded; docKey = key }
        return doc
    }

    fun invalidate() { doc = null; docKey = "" }

    override fun resetRuntime() = invalidate()

    override fun props() = listOf(
        Prop.Asset("Tilemap", AssetKind.TILEMAP, { map }, { map = it; invalidate() }),
        Prop.F("Tile Size", { tileSize }, { tileSize = it.coerceIn(0.01f, 64f) }, 0.05f),
        Prop.F("Opacity", { opacity }, { opacity = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.Color("Tint", { tint }, { tint = it }),
    )
}
