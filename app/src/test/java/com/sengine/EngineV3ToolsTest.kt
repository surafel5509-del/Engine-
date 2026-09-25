package com.sengine

import com.sengine.engine.audio.Song
import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SpriteRenderer
import com.sengine.engine.model.ModelOps
import com.sengine.engine.model.ModelPresets
import com.sengine.engine.model.SModel
import com.sengine.project.AssetLibrary
import com.sengine.project.GameDoctor
import com.sengine.project.ScriptRecipes
import com.sengine.project.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.math.abs
import kotlin.math.sqrt

/** Tests for the v3 creative tools: model ops, presets, music synth, Game Doctor. */
class EngineV3ToolsTest {

    private fun emptyProject(): Project {
        val dir = Files.createTempDirectory("sengine3t").toFile()
        return Project(File(dir, "Tools")).also { it.saveMeta() }
    }

    @Test
    fun extrudeInsetAndSubdivide() {
        val p = SModel.primitive(0)
        assertEquals(8, p.verts.size); assertEquals(6, p.faces.size)
        // extrude the +Y face (indices 2,6,7,3 → y = 0.5)
        val top = p.faces.indexOfFirst { f -> f.all { p.verts[it][1] > 0.4f } }
        assertTrue(top >= 0)
        ModelOps.extrude(p, setOf(top), 0.5f)
        assertEquals(12, p.verts.size)
        assertEquals(10, p.faces.size) // 6 + 4 side walls
        assertTrue(p.faces[top].all { abs(p.verts[it][1] - 1.0f) < 1e-4f })
        // side wall normals must point outwards (away from the centre axis)
        for (fi in 6 until 10) {
            val n = SModel.faceNormal(p, p.faces[fi]); val c = SModel.faceCenter(p, p.faces[fi])
            assertTrue("wall $fi faces inward", n[0] * c[0] + n[2] * c[2] > 0f)
        }
        ModelOps.inset(p, setOf(top), 0.3f)
        assertEquals(16, p.verts.size); assertEquals(14, p.faces.size)

        val cube = SModel.primitive(0)
        ModelOps.subdivide(cube, true)
        assertEquals(24, cube.faces.size)
        assertEquals(26, cube.verts.size) // 8 + 12 edges + 6 faces
        // Catmull-Clark pulls corners inwards: every vertex within the original cube, not all on the corners
        val maxR = cube.verts.maxOf { sqrt(it[0] * it[0] + it[1] * it[1] + it[2] * it[2]) }
        assertTrue(maxR < 0.87f)
        ModelOps.subdivide(cube, true)
        assertEquals(96, cube.faces.size)
    }

    @Test
    fun mirrorMergeDeleteAndObj() {
        val p = SModel.primitive(1) // plane
        ModelOps.translate(p, p.verts.indices.toSet(), 0.5f, 0f, 0f) // x in [0, 1]
        ModelOps.mirror(p, 0)
        assertEquals(6, p.verts.size) // 2 welded on the plane
        assertEquals(2, p.faces.size)
        val dup = p.copy()
        dup.verts.add(floatArrayOf(0f, 0f, 0f)); dup.verts.add(floatArrayOf(0.0001f, 0f, 0f))
        dup.faces.add(intArrayOf(6, 7, 0))
        ModelOps.mergeByDistance(dup, 0.001f)
        assertTrue(dup.faces.all { it.toSet().size >= 3 })
        ModelOps.deleteFaces(p, setOf(0))
        assertEquals(1, p.faces.size); assertEquals(4, p.verts.size)

        val m = ModelPresets.build(0)
        val obj = m.toObj()
        val back = ModelOps.fromObj(obj)
        assertEquals(m.parts.sumOf { it.verts.size }, back.verts.size)
        assertEquals(m.parts.sumOf { it.faces.size }, back.faces.size)
    }

    @Test
    fun presetsAreValidAndAnimated() {
        for (i in ModelPresets.NAMES.indices) {
            val m = ModelPresets.build(i)
            assertTrue(ModelPresets.NAMES[i], m.parts.isNotEmpty())
            for (p in m.parts) {
                assertTrue(p.faces.all { f -> f.size >= 3 && f.all { it in p.verts.indices } })
                assertTrue(p.parent < m.parts.size)
            }
            // round trip
            val again = SModel.parse(m.toJson().toString())
            assertEquals(m.parts.size, again.parts.size); assertEquals(m.clips.size, again.clips.size)
            // clips reference existing parts and sample without NaNs
            for (c in m.clips) {
                for (tr in c.tracks) assertNotNull("${c.name}:${tr.part}", m.parts.firstOrNull { it.name == tr.part })
                val mats = Array(m.parts.size) { FloatArray(16) }
                for (t in listOf(0f, c.length * 0.3f, c.length * 0.9f)) { m.matrices(c, t, mats); assertTrue(mats.all { a -> a.all { !it.isNaN() } }) }
            }
        }
        val walker = ModelPresets.character()
        val walk = walker.clip("walk")!!
        val a = Array(walker.parts.size) { FloatArray(16) }; val b = Array(walker.parts.size) { FloatArray(16) }
        walker.matrices(walk, 0f, a); walker.matrices(walk, 0.4f, b)
        val leg = walker.parts.indexOfFirst { it.name == "LegL" }
        assertFalse("leg should swing", a[leg].contentEquals(b[leg]))
    }

    @Test
    fun songComposeRenderAndSerialize() {
        for (style in Song.STYLES.indices) {
            val s = Song.compose(style, 42)
            assertEquals(4, s.tracks.size)
            assertTrue(s.tracks.all { it.notes.isNotEmpty() })
            val back = Song.parse(s.toJson().toString())
            assertEquals(s.tracks.sumOf { it.notes.size }, back.tracks.sumOf { it.notes.size })
            assertEquals(s.bpm, back.bpm)
        }
        val s = Song.compose(0, 1)
        val samples = s.render(1, tail = false)
        assertEquals((s.durationSeconds * Song.RATE).toInt(), samples.size, 2)
        val peak = samples.maxOf { abs(it) }
        assertTrue("audible", peak > 0.1f); assertTrue("no clipping", peak <= 1f)
        val wav = s.renderWav()
        assertEquals("RIFF", String(wav, 0, 4)); assertEquals("WAVE", String(wav, 8, 4))
        // same seed → same song
        assertEquals(Song.compose(3, 9).toJson().toString(), Song.compose(3, 9).toJson().toString())
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) = assertTrue("$actual != $expected ± $tolerance", abs(expected - actual) <= tolerance)

    @Test
    fun gameDoctorFindsAndFixesProblems() {
        val p = emptyProject()
        val s = Scene("Main")
        val body = s.create("Box"); body.add(SpriteRenderer().also { it.texture = "missing.png" }); body.add(Rigidbody2D())
        p.saveScene(s); p.startScene = "Nope"; p.saveMeta()
        p.writeAsset("bad.js", "function update(dt) { var x = ; }")
        p.writeAsset("good.js", "function start() { scene.load('Level9'); }")
        val issues = GameDoctor.check(p)
        val titles = issues.map { it.title }
        println("Doctor: " + titles.joinToString(" | "))
        assertTrue(titles.any { it.startsWith("Start scene missing") })
        assertTrue(titles.any { it.startsWith("Script error in bad.js") })
        assertTrue(titles.any { it.startsWith("good.js loads missing scene") })
        assertTrue(titles.any { it.contains("has no camera") })
        assertTrue(titles.any { it.startsWith("Missing texture 'missing.png'") })
        assertTrue(titles.any { it.contains("Rigidbody2D without collider") })
        assertNull(GameDoctor.syntaxError("function a() { return 1; }", "ok.js"))
        // apply all fixes → only the script problems remain
        for (i in issues) i.fix?.invoke()
        val after = GameDoctor.check(p).filter { it.severity >= 1 }.map { it.title }
        println("After fixes: " + after.joinToString(" | "))
        assertTrue(after.none { it.startsWith("Start scene missing") || it.contains("has no camera") || it.startsWith("Missing texture") || it.contains("without collider") })
        assertTrue(p.loadScene("Main").objects.any { it.getAny<Camera2D>() != null })
        assertTrue(after.any { it.startsWith("Script error") })
    }

    @Test
    fun recipesCompileAndFullEditionItemsInstall() {
        for (r in ScriptRecipes.all) assertNull("${r.file}", GameDoctor.syntaxError(r.code, r.file))
        val dir = Files.createTempDirectory("store3").toFile()
        val p = Project(File(dir, "Store3")); p.saveMeta()
        val cats = setOf("Music", "3D Models", "Sounds", "Scripts")
        for (item in AssetLibrary.items.filter { it.category in cats }) {
            item.install(p)
            assertTrue("${item.title} installed", item.installed(p))
        }
        val song = Song.parse(p.readAsset("RacingRush.song")!!)
        assertTrue(song.tracks.isNotEmpty())
        val car = SModel.parse(p.readAsset("Car.smodel")!!)
        assertTrue(car.parts.size > 3)
        val titles = AssetLibrary.items.map { it.title }
        assertEquals("unique titles", titles.size, titles.toSet().size)
        assertTrue(AssetLibrary.items.count { it.category == "Packs" } >= 11)
        println("SIM store items v3=${AssetLibrary.items.size}")
    }
}
