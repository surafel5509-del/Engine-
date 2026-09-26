package com.sengine

import com.sengine.engine.Engine
import com.sengine.engine.controls.ControlLayout
import com.sengine.engine.core.Collider3D
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SceneSerializer
import com.sengine.engine.core.ScriptComponent
import com.sengine.engine.core.UIButton
import com.sengine.engine.core.VoxelWorld
import com.sengine.engine.model.SModel
import com.sengine.engine.voxel.Blocks
import com.sengine.engine.voxel.VoxelData
import com.sengine.project.Project
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Headless tests for the Full Edition core: voxels, models, game UI, controls, storage, input. */
class EngineV3Test {

    private fun emptyProject(): Project {
        val dir = Files.createTempDirectory("sengine3").toFile()
        return Project(File(dir, "Test")).also { it.saveMeta() }
    }

    private class Run(val engine: Engine, val errors: MutableList<String>)

    private fun start(p: Project, scene: Scene): Run {
        p.saveScene(scene); p.startScene = scene.name; p.saveMeta()
        val e = Engine(p, p.loadScene(scene.name))
        e.gameView.widthPx = 1600; e.gameView.heightPx = 900
        val errors = ArrayList<String>()
        e.listeners.add(object : Engine.Listener {
            override fun onLog(level: Int, message: String) {
                if (level >= 2) errors.add(message)
                println("SIM v3 log[$level]: $message")
            }
        })
        e.play()
        return Run(e, errors)
    }

    private fun Run.frames(n: Int, each: (Int) -> Unit = {}) {
        repeat(n) { i -> each(i); synchronized(engine.lock) { engine.tick(1f / 60f) } }
    }

    @Test
    fun voxelGenerationEditingAndRaycast() {
        val vw = VoxelWorld().also { it.chunksX = 2; it.chunksZ = 2; it.height = 40; it.trees = false; it.waterLevel = 0 }
        val d = VoxelData.create(vw)
        val sy = d.surfaceY(10, 10)
        println("SIM voxel surface at (10,10) = $sy")
        assertTrue(sy in 2 until 40)
        val hit = d.raycast(10.5f, 39f, 10.5f, 0f, -1f, 0f, 60f)
        assertNotNull(hit)
        assertEquals(sy - 1, hit!!.y)
        assertEquals(1, hit.ny)
        d.set(10, sy, 10, Blocks.BRICK)
        assertEquals(Blocks.BRICK, d.get(10, sy, 10))
        val rebuilt = d.rebuildDirty(100)
        assertTrue(rebuilt > 0)
        assertTrue((d.meshes[0]?.size ?: 0) > 0)
        val f = File(Files.createTempDirectory("vox").toFile(), "w.world")
        d.save(f)
        val d2 = VoxelData(d.sx, d.sy, d.sz)
        assertTrue(d2.load(f))
        assertEquals(Blocks.BRICK, d2.get(10, sy, 10))
    }

    @Test
    fun rigidbodyLandsOnVoxelTerrain() {
        val p = emptyProject()
        val s = Scene("Vox")
        s.create("World").add(VoxelWorld().also { it.chunksX = 2; it.chunksZ = 2; it.height = 40; it.trees = false; it.waterLevel = 0 })
        val box = s.create("Box")
        box.x = 8.5f; box.y = 39f; box.z = 8.5f
        box.add(Collider3D()); box.add(Rigidbody3D())
        val r = start(p, s)
        r.frames(240)
        val w = r.engine.scene.find("World")!!.get<VoxelWorld>()!!.data!!
        val sy = w.surfaceY(8, 8)
        val b = r.engine.scene.find("Box")!!
        println("SIM voxel box y=${b.y} surface=$sy grounded=${b.get<Rigidbody3D>()!!.grounded}")
        assertTrue("box should rest on terrain", kotlin.math.abs(b.y - (sy + 0.5f)) < 0.3f)
        assertTrue(r.errors.isEmpty())
    }

    @Test
    fun smodelPrimitivesRoundTripAndAnimation() {
        val m = SModel()
        for (k in SModel.PRIMITIVES.indices) m.parts.add(SModel.primitive(k).also { it.pos[0] = k * 2f })
        m.parts[1].parent = 0
        val clip = com.sengine.engine.model.SClip("Spin", 2f, true)
        val tr = clip.track(m.parts[0].name)
        tr.keys.add(com.sengine.engine.model.SKey(0f, floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 1f, 1f)))
        tr.keys.add(com.sengine.engine.model.SKey(1f, floatArrayOf(0f, 2f, 0f), floatArrayOf(0f, 90f, 0f), floatArrayOf(1f, 1f, 1f)))
        m.clips.add(clip)
        val m2 = SModel.parse(m.toJson().toString())
        assertEquals(m.parts.size, m2.parts.size)
        val cube = m2.buildPart(0)
        assertEquals(36 * 8, cube.sumOf { it.second.size })
        val pos = FloatArray(3); val rot = FloatArray(3); val sc = FloatArray(3)
        m2.pose(0, m2.clip("spin"), 0.5f, pos, rot, sc)
        assertTrue(pos[1] > 0.5f && pos[1] < 1.5f)
        m2.pose(0, m2.clip("spin"), 1f, pos, rot, sc)
        assertEquals(2f, pos[1], 1e-3f)
        val mats = Array(m2.parts.size) { FloatArray(16) }
        m2.matrices(m2.clip("spin"), 1f, mats)
        // child follows the parent's animated height
        assertEquals(2f, mats[1][13], 1e-3f)
        for (i in m2.parts.indices) assertTrue("part $i builds", m2.buildPart(i).isNotEmpty())
        assertTrue(m2.toObj().contains("\nf "))
    }

    @Test
    fun uiButtonClickRunsActionsAndStorage() {
        val p = emptyProject()
        p.writeAsset("menu.js", """
            var clicked = 0;
            function startGame(name) { clicked++; storage.set("started", name); storage.set("best", 42); }
            function onUIClick(name) { log("clicked " + name); }
            function update(dt) { if (input.tapped) storage.set("gameTap", true); }
        """.trimIndent())
        val s = Scene("Menu")
        val ctl = s.create("Controller")
        ctl.add(ScriptComponent().also { it.script = "menu.js" })
        val btn = s.create("Play")
        btn.add(UIButton().also { it.text = "PLAY"; it.action = "call:startGame; hide:Play"; it.width = 4f; it.height = 1f })
        val r = start(p, s)
        r.frames(2)
        // button at UI (0,0) -> screen centre
        r.engine.input.touch(0, 800f, 450f)
        r.frames(1)
        assertTrue(r.engine.scene.find("Play")!!.get<UIButton>()!!.pressed)
        r.engine.input.touch(2, 800f, 450f)
        r.frames(2)
        assertEquals("Play", r.engine.storage.get("started"))
        assertEquals(false, r.engine.scene.find("Play")!!.active)
        assertTrue("UI tap must not reach the game", !r.engine.storage.has("gameTap"))
        // a tap elsewhere is a game tap
        r.engine.input.touch(0, 100f, 100f); r.engine.input.touch(2, 100f, 100f)
        r.frames(2)
        assertTrue(r.engine.storage.has("gameTap"))
        assertTrue(r.errors.toString(), r.errors.isEmpty())
    }

    @Test
    fun namedButtonsSticksTimeScaleAndProps() {
        val p = emptyProject()
        p.writeAsset("t.js", """
            var fired = 0, t0 = 0;
            function update(dt) {
              if (input.buttonDown("Fire")) fired++;
              if (fired > 0) { self.x = input.stickX("aim"); }
              if (time.frame == 5) { time.scale = 0; }
              self.setProp("MeshRenderer", "Emission", 0.5);
              storage.set("em", self.getProp("MeshRenderer", "emission"));
              storage.set("fired", fired);
            }
        """.trimIndent())
        val s = Scene("T")
        val go = s.create("Obj")
        go.add(com.sengine.engine.core.MeshRenderer())
        go.add(ScriptComponent().also { it.script = "t.js" })
        val r = start(p, s)
        r.frames(3)
        r.engine.input.rawButtons["Fire"] = true
        r.engine.input.rawSticks["aim"] = floatArrayOf(0.75f, 0f)
        r.frames(3)
        assertEquals(1.0, (r.engine.storage.get("fired") as Number).toDouble(), 1e-9)
        assertEquals(0.75f, r.engine.scene.find("Obj")!!.x, 1e-4f)
        assertEquals(0.5, (r.engine.storage.get("em") as Number).toDouble(), 1e-6)
        val t = r.engine.time
        r.frames(10)
        assertEquals("time.scale = 0 freezes game time", t, r.engine.time, 1e-9)
        assertTrue(r.errors.toString(), r.errors.isEmpty())
    }

    @Test
    fun controlPresetsSerialize() {
        assertTrue(ControlLayout.presets.size >= 6)
        for (l in ControlLayout.presets) {
            val back = ControlLayout.fromJson(JSONObject(l.toJson().toString()))
            assertEquals(l.name, back.name)
            assertEquals(l.controls.size, back.controls.size)
            for ((a, b) in l.controls.zip(back.controls)) { assertEquals(a.id, b.id); assertEquals(a.color, b.color); assertEquals(a.x, b.x, 1e-6f) }
        }
        val p = emptyProject()
        p.saveControls(ControlLayout.presets[4])
        assertEquals(ControlLayout.presets[4].name, p.loadControls().name)
    }

    @Test
    fun uiComponentsSerialize() {
        val s = Scene("S")
        val b = s.create("B")
        b.add(UIButton().also { it.text = "Go"; it.action = "scene:Level1"; it.anchor = 6 })
        s.create("W").add(VoxelWorld().also { it.seed = 99 })
        val back = SceneSerializer.fromJson(JSONObject(SceneSerializer.toJson(s).toString()))
        val bb = back.find("B")!!.get<UIButton>()!!
        assertEquals("Go", bb.text); assertEquals("scene:Level1", bb.action); assertEquals(6, bb.anchor)
        assertEquals(99, back.find("W")!!.get<VoxelWorld>()!!.seed)
    }
}
