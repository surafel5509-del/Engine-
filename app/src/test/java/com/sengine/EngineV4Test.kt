package com.sengine

import com.sengine.engine.Engine
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.Collider3D
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SceneSerializer
import com.sengine.engine.core.Water
import com.sengine.engine.texture.PixelDoc
import com.sengine.project.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 3rd-edition features: water physics, particle presets, pixel documents. */
class EngineV4Test {

    private fun run(scene: Scene): Engine {
        val p = Project(File(Files.createTempDirectory("sengine4").toFile(), "T")).also { it.saveMeta() }
        p.saveScene(scene); p.startScene = scene.name; p.saveMeta()
        val e = Engine(p, p.loadScene(scene.name))
        e.gameView.widthPx = 1600; e.gameView.heightPx = 900
        e.play()
        return e
    }

    private fun Engine.frames(n: Int) = repeat(n) { synchronized(lock) { tick(1f / 60f) } }

    @Test
    fun water2DFloatsBodiesAndSplashes() {
        val s = Scene("Main")
        s.create("Pool").also { it.y = -2f; it.add(Water().apply { width = 20f; height = 4f }) } // surface at y=0
        val crate = s.create("Crate").also { it.y = 4f; it.add(Collider2D()); it.add(Rigidbody2D()) }
        val rock = s.create("Rock").also { it.x = 5f; it.y = 4f; it.add(Collider2D()); it.add(Rigidbody2D().apply { mass = 5f }) }
        s.create("Heavy Water Off").also { it.x = 30f }
        val e = run(s)
        var maxDrops = 0
        repeat(360) { e.frames(1); maxDrops = maxOf(maxDrops, e.scene.find("Pool")!!.get<Water>()!!.drops.size) }
        val c = e.scene.find("Crate")!!
        val sub = c.get<Rigidbody2D>()!!.submerged
        println("SIM water2d crate y=${c.y} submerged=$sub drops=$maxDrops")
        assertTrue("crate should float near the surface", c.y > -1.2f && c.y < 1.0f)
        assertTrue(sub > 0f)
        assertTrue("entering water splashes", maxDrops > 0)
        // round-trip through the serializer
        val json = SceneSerializer.toJson(e.scene).toString()
        assertTrue(json.contains("Water"))
    }

    @Test
    fun water3DBuoyancy() {
        val s = Scene("Sea")
        s.create("Sea").also { it.add(Water().apply { mode = 1; width = 30f; depth = 30f }) } // surface y=0
        s.create("Barrel").also { it.y = 3f; it.add(Collider3D()); it.add(Rigidbody3D()) }
        val e = run(s)
        e.frames(420)
        val b = e.scene.find("Barrel")!!
        println("SIM water3d barrel y=${b.y} submerged=${b.get<Rigidbody3D>()!!.submerged}")
        assertTrue("barrel floats", b.y > -1.5f && b.y < 1.2f)
    }

    @Test
    fun particlePresetsApplyAndEmit() {
        val pe = ParticleEmitter()
        pe.props().first { it.name == "Preset" }.let { (it as com.sengine.engine.core.Prop.Choice).set(ParticleEmitter.PRESETS.indexOf("Fire")) }
        assertTrue(pe.additive); assertTrue(pe.turbulence > 0f)
        val s = Scene("Main")
        s.create("Campfire").also { it.add(pe) }
        val e = run(s)
        e.frames(60)
        val live = e.scene.find("Campfire")!!.getAny<ParticleEmitter>()!!
        println("SIM fire particles=${live.particles.size} preset=${ParticleEmitter.PRESETS[live.preset]}")
        assertTrue(live.particles.size > 10)
        assertEquals("Fire", ParticleEmitter.PRESETS[live.preset])
    }

    @Test
    fun pixelDocDrawingUndoAndFrames() {
        val d = PixelDoc(16, 16)
        d.checkpoint(); d.rect(2, 2, 6, 6, 0xFFFF0000.toInt(), true)
        assertEquals(0xFFFF0000.toInt(), d.get(4, 4))
        d.checkpoint(); d.fill(0, 0, 0xFF00FF00.toInt())
        assertEquals(0xFF00FF00.toInt(), d.get(0, 15)); assertEquals(0xFFFF0000.toInt(), d.get(3, 3))
        d.undo(); assertEquals(0, d.get(0, 15))
        d.redo(); assertEquals(0xFF00FF00.toInt(), d.get(15, 15))
        d.addFrame(true); assertEquals(2, d.frames.size)
        d.addLayer()
        val back = PixelDoc(); back.load(org.json.JSONObject(d.toJson().toString()))
        assertEquals(2, back.frames.size); assertEquals(16, back.width)
        assertEquals(0xFFFF0000.toInt(), back.composite(0)[4 * 16 + 4])
    }

    @Test
    fun prefabsBuildAndRun() {
        val s = Scene("Main")
        var x = 0f
        for (n in com.sengine.project.Prefabs.PREFABS_2D + com.sengine.project.Prefabs.PREFABS_3D) { com.sengine.project.Prefabs.create(s, n, x, 0f, 0f); x += 30f }
        val count = s.objects.size
        val e = run(s)
        e.frames(120)
        println("SIM prefabs objects=$count after=${e.scene.objects.size}")
        assertTrue(count >= 24)
        assertTrue(SceneSerializer.toJson(e.scene).toString().contains("Water"))
    }
}
