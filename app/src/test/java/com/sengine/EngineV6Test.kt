package com.sengine

import com.sengine.engine.core.MeshRenderer
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SceneSerializer
import com.sengine.engine.model.AnimIO
import com.sengine.engine.model.ModelPresets
import com.sengine.engine.model.ModelStudio
import com.sengine.engine.model.Rigging
import com.sengine.engine.model.SJoint
import com.sengine.engine.model.SModel
import com.sengine.engine.model.SPart
import com.sengine.engine.script.NativeScripts
import com.sengine.project.Prefabs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** v6: Model & Animation Studio (native mesh ops, rigging, auto animation, .sanim) + PBR material data. */
class EngineV6Test {

    private fun nativeRequired() {
        if (System.getenv("SENGINE_NATIVE_LIB") != null) assertTrue("native library failed to load", NativeScripts.available)
        assumeTrue("native library not available", NativeScripts.available)
    }

    private fun cube(): SPart = SModel.primitive(0).also { it.name = "Cube"; it.fixColors() }

    private fun valid(p: SPart) {
        assertEquals(p.faces.size, p.faceColors.size); assertEquals(p.faces.size, p.groups.size); assertEquals(p.faces.size, p.uvs.size)
        for ((i, f) in p.faces.withIndex()) {
            assertTrue("face $i too small", f.size >= 3)
            assertTrue("face $i bad index", f.all { it in p.verts.indices })
            p.uvs[i]?.let { u -> assertEquals(f.size * 2, u.size); assertTrue(u.all { it.isFinite() }) }
        }
        assertTrue(p.verts.all { v -> v.all { it.isFinite() } })
    }

    @Test
    fun meshOpsBevelLoopBridgeGroupsUv() {
        nativeRequired()
        val b = cube(); val caps = ModelStudio.bevel(b, setOf(0, 1, 2, 3, 4, 5), 0.25f, 0.08f, 2); valid(b)
        assertTrue("bevel adds faces", b.faces.size > 6); assertEquals(6, caps.size)
        val l = cube(); val f0 = l.faces[0]; val cuts = ModelStudio.loopCut(l, f0[0], f0[1], 0.5f); valid(l)
        assertEquals(4, cuts); assertEquals(10, l.faces.size)
        val br = cube()
        val top = br.faces.indices.maxByOrNull { SModel.faceCenter(br, br.faces[it])[1] }!!
        val bottom = br.faces.indices.minByOrNull { SModel.faceCenter(br, br.faces[it])[1] }!!
        val tube = ModelStudio.bridge(br, top, bottom, 1); valid(br)
        assertTrue("bridge made faces", tube.isNotEmpty())
        val g = cube(); assertEquals(6, ModelStudio.autoGroups(g, 30f)); valid(g)
        assertEquals(1, ModelStudio.groupFaces(g, setOf(0)).size)
        for (mode in ModelStudio.UV_MODES.indices) { val u = cube(); ModelStudio.unwrap(u, mode, 1f); valid(u); assertTrue("mode $mode uvs", u.hasUVs) }
        println("SIM v6 meshops bevel=${b.faces.size} loop=$cuts bridge=${tube.size}")
    }

    @Test
    fun modelJsonKeepsGroupsUvsTextureRig() {
        val m = SModel(mutableListOf(cube()))
        val p = m.parts[0]
        p.groups[2] = 7; p.uvs[1] = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f); p.texture = "bricks.png"
        m.rig += SJoint("Hips", -1, floatArrayOf(0f, 1f, 0f))
        val back = SModel.parse(m.toJson().toString())
        val q = back.parts[0]
        assertEquals(7, q.groups[2]); assertEquals("bricks.png", q.texture); assertEquals(1f, q.uvs[1]!![2], 1e-4f)
        assertEquals(null, q.uvs[0]); assertEquals("Hips", back.rig[0].name)
        // edits keep the per-face lists in sync
        com.sengine.engine.model.ModelOps.extrude(q, setOf(1), 0.2f); q.fixColors(); valid(q)
        com.sengine.engine.model.ModelOps.deleteFaces(q, setOf(0)); valid(q)
    }

    @Test
    fun rigBindAutoAnimateAndRetarget() {
        nativeRequired()
        val src = ModelPresets.character()
        val faces = src.parts.filter { it.visible }.sumOf { it.faces.size }
        val joints = Rigging.autoPlace(src)
        assertEquals(Rigging.HUMANOID.size, joints.size)
        val bound = Rigging.bind(src, joints)
        assertEquals(joints.size, bound.parts.size)
        assertEquals("all faces kept", faces, bound.parts.sumOf { it.faces.size })
        bound.parts.forEach { valid(it) }
        assertTrue("several bones got geometry", bound.parts.count { it.faces.isNotEmpty() } >= 6)
        val walk = ModelStudio.autoAnimate(bound, "Walk")
        assertTrue(walk.tracks.size >= 4)
        val mats = Array(bound.parts.size) { FloatArray(16) }
        bound.matrices(walk, 0.3f, mats); assertTrue(mats.all { m -> m.all { it.isFinite() } })
        // humanoid preset is rigged and animated
        val h = ModelPresets.humanoid()
        assertEquals(Rigging.HUMANOID.size, h.rig.size)
        assertTrue(h.clip("Walk") != null && h.clip("Jump") != null)
        // .sanim export from the mannequin, import onto the original (unrigged-names) character
        val text = AnimIO.export(h, h.clip("Walk")!!)
        val target = ModelPresets.character()
        val r = AnimIO.import(target, text, "WalkImported")
        assertTrue("mapped ${r.mapped}", r.mapped.size >= 4)
        assertTrue(r.mapped.values.contains("ArmL") && r.mapped.values.contains("LegR"))
        assertTrue(target.clip("WalkImported") != null)
        println("SIM v6 rig bones=${bound.parts.count { it.faces.isNotEmpty() }} walkTracks=${walk.tracks.size} retarget=${r.mapped.size}/${r.mapped.size + r.unmapped.size}")
    }

    @Test
    fun canonicalNames() {
        assertEquals("upperarm:L", AnimIO.canonical("ArmL", -0.5f))
        assertEquals("thigh:R", AnimIO.canonical("LegR", 0.2f))
        assertEquals("forearm:L", AnimIO.canonical("Forearm_L", 0f))
        assertEquals("hand:R", AnimIO.canonical("RightHand", 0f))
        assertEquals("hips:", AnimIO.canonical("Hips", 0f))
        assertEquals(-1, Rigging.mirrorOf(0)); assertEquals("UpperArm_R", Rigging.HUMANOID[Rigging.mirrorOf(6)].name)
    }

    @Test
    fun presetsPrefabsAndPbr() {
        for (i in ModelPresets.NAMES.indices) {
            val m = ModelPresets.build(i)
            assertTrue(ModelPresets.NAMES[i], m.parts.isNotEmpty())
            m.parts.forEach { valid(it.also { p -> p.fixColors() }) }
            SModel.parse(m.toJson().toString())
        }
        val s = Scene("Main")
        val a = Prefabs.create(s, "Studio 3-Point Lighting", 0f, 0f, 0f)
        val b = Prefabs.create(s, "Studio Backdrop", 0f, 0f, 0f)
        assertTrue(s.objects.count { it.parent == a } >= 3 && s.objects.count { it.parent == b } >= 8)
        val go = s.create("Ball", null)
        go.add(MeshRenderer().also { it.pbr = true; it.metallic = 0.8f; it.roughness = 0.3f; it.normalMap = "n.png"; it.normalStrength = 1.5f })
        val json = SceneSerializer.toJson(s).toString()
        val back = SceneSerializer.fromJson(org.json.JSONObject(json)).objects.first { it.name == "Ball" }.get<MeshRenderer>()!!
        assertTrue(back.pbr); assertEquals(0.8f, back.metallic, 1e-4f); assertEquals("n.png", back.normalMap)
        assertEquals(false, MeshRenderer().pbr)
    }
}
