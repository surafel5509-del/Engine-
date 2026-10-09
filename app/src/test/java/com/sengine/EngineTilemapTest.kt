package com.sengine

import com.sengine.engine.core.Scene
import com.sengine.engine.core.SceneSerializer
import com.sengine.engine.core.TileLayer
import com.sengine.engine.core.TileMap
import com.sengine.engine.core.Tilemap
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Milestone 1: the tilemap document model (layers, RLE codec, collision geometry, serialization) and the TileMap component. */
class EngineTilemapTest {

    private fun map(cols: Int = 4, rows: Int = 3): Tilemap =
        Tilemap("Tileset.png", 16, 16, mutableListOf(TileLayer("Ground", cols, rows)))

    // ------------------------------------------------------------------ cells

    @Test
    fun layerCellsAndBounds() {
        val m = map()
        val l = m.layers[0]
        assertEquals(4, m.cols); assertEquals(3, m.rows)
        assertTrue(l.isEmpty())
        l.set(0, 0, 5)
        l.set(3, 2, 9)
        assertEquals(5, l.get(0, 0))
        assertEquals(9, l.get(3, 2))
        assertEquals(-1, l.get(1, 1))
        assertEquals(2, l.cells.count { it >= 0 })
        // out-of-bounds writes are ignored instead of crashing the renderer
        l.set(99, 99, 7)
        assertEquals(-1, l.get(99, 99))
        assertEquals(2, l.cells.count { it >= 0 })
    }

    @Test
    fun fillAndClear() {
        val l = map().layers[0]
        l.fill(3)
        assertFalse(l.isEmpty())
        assertEquals(12, l.count(3))
        l.clear()
        assertTrue(l.isEmpty())
    }

    @Test
    fun resizeKeepsBottomLeftAnchor() {
        val l = map(4, 3).layers[0]
        l.set(0, 0, 1)
        l.set(3, 2, 2)   // top-right: must be dropped when shrinking
        l.resize(2, 2)
        assertEquals(2, l.cols); assertEquals(2, l.rows)
        assertEquals(1, l.get(0, 0))
        assertEquals(-1, l.get(1, 1))
        l.resize(5, 4)
        assertEquals(5, l.cols); assertEquals(4, l.rows)
        assertEquals(1, l.get(0, 0))
        assertEquals(-1, l.get(4, 3))
    }

    @Test
    fun runsMergeHorizontally() {
        val l = map(6, 2).layers[0]
        l.set(1, 0, 7); l.set(2, 0, 7); l.set(3, 0, 7); l.set(4, 0, 8)
        val runs = l.runs()
        assertEquals(2, runs.size)
        assertEquals(1, runs[0][0]); assertEquals(0, runs[0][1]); assertEquals(3, runs[0][2]); assertEquals(7, runs[0][3])
        assertEquals(4, runs[1][0]); assertEquals(1, runs[1][2]); assertEquals(8, runs[1][3])
        println("SIM tilemap runs merged 4 cells -> ${runs.size} quads")
    }

    @Test
    fun topmostVisibleTileIsPicked() {
        val m = map()
        val bg = TileLayer("Background", 4, 3)
        val fg = TileLayer("Ground", 4, 3)
        bg.set(1, 1, 3); fg.set(1, 1, 8)
        m.layers.add(0, bg); m.layers.add(fg)
        assertEquals(8, m.at(1, 1))
        fg.visible = false
        assertEquals(3, m.at(1, 1))
        assertEquals(-1, m.at(0, 0))
    }

    // ------------------------------------------------------------------ collision

    @Test
    fun solidsComeFromLayerFlagOrTileSet() {
        val m = map()
        val ground = m.layers[0]
        ground.solid = true
        ground.set(0, 0, 1)
        ground.set(1, 0, 2)
        assertTrue(m.solidAt(0, 0))
        assertTrue(m.solidAt(1, 0))
        assertFalse(m.solidAt(2, 0)) // empty cell is never solid

        ground.solid = false
        assertFalse(m.solidAt(0, 0))
        m.solidTiles.add(1)
        assertTrue(m.solidAt(0, 0))
        assertFalse(m.solidAt(1, 0))

        ground.visible = false
        assertFalse("hidden layers must not collide", m.solidAt(0, 0))
    }

    @Test
    fun solidRectsMergeIntoFloorAndWall() {
        val m = map(4, 4)
        val l = m.layers[0]
        l.solid = true
        for (col in 0 until 4) l.set(col, 0, 1)      // floor row
        l.set(0, 1, 1); l.set(0, 2, 1)               // wall on the left
        val rects = m.solidRects()
        assertEquals(2, rects.size)
        // floor: x 0..4, y 0..1
        val floor = rects.first { it[3] == 1f }
        assertEquals(0f, floor[0], 0f); assertEquals(0f, floor[1], 0f)
        assertEquals(4f, floor[2], 0f); assertEquals(1f, floor[3], 0f)
        // wall: 1x1 above the floor, merged vertically into a 1x2 box
        val wall = rects.first { it[3] == 2f }
        assertEquals(0f, wall[0], 0f); assertEquals(1f, wall[1], 0f)
        println("SIM tilemap collision: 7 solid cells -> ${rects.size} boxes")
    }

    // ------------------------------------------------------------------ codec

    @Test
    fun runLengthCodecRoundTrips() {
        val cells = intArrayOf(-1, -1, -1, 2, 2, 5, -1, 5, 5, 5)
        val encoded = Tilemap.encode(cells)
        assertEquals("-1*3,2*2,5,-1,5*3", encoded)
        assertArrayEqualsInt(cells, Tilemap.decode(encoded, cells.size))
    }

    @Test
    fun codecHandlesEmptyAndSparseData() {
        assertEquals("", Tilemap.encode(IntArray(0)))
        val decoded = Tilemap.decode("", 6)
        assertEquals(6, decoded.size)
        assertTrue(decoded.all { it == -1 })
        // a short payload leaves the rest empty rather than throwing
        assertArrayEqualsInt(intArrayOf(1, 2, -1, -1), Tilemap.decode("1,2", 4))
        // a run longer than the grid is clipped
        assertArrayEqualsInt(intArrayOf(4, 4), Tilemap.decode("4*99", 2))
    }

    // ------------------------------------------------------------------ serialization

    @Test
    fun tilemapJsonRoundTrips() {
        val m = map(3, 2)
        m.tileset = "Grass.png"
        m.tileW = 32; m.tileH = 24
        val l = m.layers[0]
        l.name = "Walls"; l.solid = true; l.opacity = 0.75f; l.visible = false
        l.set(0, 0, 4); l.set(2, 1, 11)
        m.solidTiles.add(11)

        val copy = Tilemap.fromJson(JSONObject(m.toJson().toString()))
        assertEquals("Grass.png", copy.tileset)
        assertEquals(32, copy.tileW); assertEquals(24, copy.tileH)
        assertEquals(1, copy.layers.size)
        val c = copy.layers[0]
        assertEquals("Walls", c.name)
        assertTrue(c.solid); assertFalse(c.visible)
        assertEquals(0.75f, c.opacity, 0.0001f)
        assertEquals(4, c.get(0, 0)); assertEquals(11, c.get(2, 1))
        assertEquals(-1, c.get(1, 1))
        assertTrue(copy.solidTiles.contains(11))
        assertEquals(m.usedTiles(), copy.usedTiles())
    }

    @Test
    fun starterHasTwoLayersWithSolidGround() {
        val m = Tilemap.starter("Tileset.png", 16, 16, 8, 6)
        assertEquals(2, m.layers.size)
        assertEquals(8, m.cols); assertEquals(6, m.rows)
        assertEquals(listOf("Background", "Ground"), m.layers.map { it.name })
        assertTrue(m.layers[1].solid)
        assertTrue(m.isEmpty())
        println("SIM tilemap starter 8x6 with ${m.layers.size} layers")
    }

    // ------------------------------------------------------------------ component

    @Test
    fun tileMapComponentSurvivesSceneSerialization() {
        val scene = Scene("Main")
        val go = scene.create("Level")
        val tm = TileMap()
        tm.map = "Level1.tmap"
        tm.tileSize = 1.5f
        tm.opacity = 0.5f
        tm.tint = 0xFF80D8FF.toInt()
        go.add(tm)

        val json = SceneSerializer.toJson(scene)
        val loaded = SceneSerializer.fromJson(JSONObject(json.toString()))
        val back = loaded.find("Level")!!.getAny<TileMap>()
        assertNotNull(back)
        assertEquals("Level1.tmap", back!!.map)
        assertEquals(1.5f, back.tileSize, 0.0001f)
        assertEquals(0.5f, back.opacity, 0.0001f)
        assertEquals(0xFF80D8FF.toInt(), back.tint)
        println("SIM tilemap scene round-trip ok (${json.toString().length} chars)")
    }

    @Test
    fun componentLoadsAndCachesDocument() {
        val tm = TileMap()
        tm.map = "Level1.tmap"
        var loads = 0
        val doc = Tilemap.starter("Tileset.png", 16, 16, 4, 3)

        assertNotNull(tm.ensure("v1") { loads++; doc })
        assertEquals(1, loads)
        // same key: cached, not reloaded
        assertNotNull(tm.ensure("v1") { loads++; doc })
        assertEquals(1, loads)
        // the asset changed on disk: reloaded
        assertNotNull(tm.ensure("v2") { loads++; doc })
        assertEquals(2, loads)
        // a failed load keeps the last good document
        assertNotNull(tm.ensure("v3") { null })
        assertEquals(2, loads)
        tm.invalidate()
        assertNull(tm.doc)
        assertNotNull(tm.ensure("v4") { loads++; doc })
        assertEquals(3, loads)
    }

    @Test
    fun componentDefaultsAreSane() {
        val tm = TileMap()
        assertEquals("TileMap", tm.type)
        assertEquals("", tm.map)
        assertEquals(1f, tm.tileSize, 0f)
        assertEquals(1f, tm.opacity, 0f)
        assertEquals(0xFFFFFFFF.toInt(), tm.tint)
        // every inspector property is editable and resettable
        assertTrue(tm.props().size >= 4)
    }

    private fun assertArrayEqualsInt(expected: IntArray, actual: IntArray) {
        assertEquals("length", expected.size, actual.size)
        for (i in expected.indices) assertEquals("cell $i", expected[i], actual[i])
    }
}
