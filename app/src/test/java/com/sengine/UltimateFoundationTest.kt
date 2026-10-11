package com.sengine

import com.sengine.engine.core.Scene
import com.sengine.engine.core.TimeStep
import com.sengine.engine.ecs.EcsWorld
import com.sengine.engine.ecs.Entity
import com.sengine.engine.ecs.SceneEcsBridge
import com.sengine.engine.ecs.TransformEcs
import com.sengine.engine.jobs.JobSystem
import com.sengine.engine.jobs.Profiler
import com.sengine.engine.math.Aabb3
import com.sengine.engine.math.Frustum3
import com.sengine.engine.math.Mat4
import com.sengine.engine.math.Quat
import com.sengine.engine.math.Ray3
import com.sengine.engine.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7 foundation: math, job system, ECS, fixed timestep and profiler. */
class UltimateFoundationTest {

    @Test
    fun quaternionsAndVectors() {
        val q = Quat.fromEulerDeg(90f, 0f, 0f)
        val f = q.rotate(Vec3.FORWARD)
        assertEquals(-1f, f.x, 1e-4f); assertEquals(0f, f.z, 1e-4f)
        val back = Quat.fromMat4(q.toMat4())
        assertTrue("mat round trip", back.dot(q) > 0.999f)
        val s = Quat.slerp(Quat.identity(), Quat.fromAxisAngle(0f, 1f, 0f, Math.PI.toFloat()), 0.5f)
        assertEquals(90f, s.toEulerDeg()[0], 0.5f)
        val r = Vec3(1f, 0f, 0f).rotateAxis(Vec3.UP, Math.PI.toFloat() / 2f)
        assertEquals(0f, r.z, 1e-4f); assertEquals(-1f, r.x, 1e-4f)
        assertEquals(90f, Vec3.toYaw(Vec3(1f, 0f, 0f)), 0.5f)
        assertEquals(45f, Vec3(1f, -1f, 0f).angleTo(Vec3(1f, 0f, 0f)), 0.5f)
        val a = Aabb3.fromCenterExtents(Vec3(0f, 0f, 0f), Vec3(1f, 1f, 1f))
        assertTrue(a.contains(Vec3(0.5f, 0.5f, 0.5f))); assertFalse(a.contains(Vec3(2f, 0f, 0f)))
        assertEquals(24f, a.surfaceArea(), 1e-3f)
        val moved = a.sweep(10f, 0f, 0f)
        assertEquals(11f, moved.max.x, 1e-3f)
        println("SIM foundation math ok quat=$q")
    }

    @Test
    fun frustumCulling() {
        val view = Mat4.lookAt(FloatArray(16), 0f, 0f, 10f, 0f, 0f, 0f)
        val proj = Mat4.perspective(FloatArray(16), 60f, 1.5f, 0.1f, 100f)
        val vp = Mat4.mul(FloatArray(16), proj, view)
        val frustum = Frustum3().setFromMatrix(vp)
        assertTrue("origin visible", frustum.testPoint(0f, 0f, 0f))
        assertFalse("behind camera hidden", frustum.testPoint(0f, 0f, 40f))
        assertTrue(frustum.testAabb(Aabb3.fromCenterExtents(Vec3(0f, 0f, 0f), Vec3(1f, 1f, 1f))))
        assertFalse(frustum.testAabb(Aabb3.fromCenterExtents(Vec3(500f, 0f, 0f), Vec3(1f, 1f, 1f))))
        val ray = Ray3(Vec3(0f, 0f, 5f), Vec3.FORWARD)
        assertEquals(0f, ray.pointAt(5f).z, 1e-4f)
        println("SIM foundation frustum planes=${frustum.planes.size}")
    }

    @Test
    fun jobSystemIsDeterministicAndParallel() {
        val jobs = JobSystem(4)
        try {
            val data = FloatArray(10_000)
            jobs.parallelFor(data.size, grain = 64) { i -> data[i] = i * 0.5f }
            var ok = true
            for (i in data.indices) if (data[i] != i * 0.5f) ok = false
            assertTrue("parallel writes exact", ok)
            val partial = FloatArray(1000)
            jobs.parallelFor(1000, grain = 16) { i -> partial[i] = 1f }
            assertEquals(1000f, partial.sum(), 1e-3f)
            jobs.submit { }
            jobs.complete()
            jobs.checkFailures()
            println("SIM foundation jobs ${jobs.stats()} steals=${jobs.stolen.get()}")
        } finally {
            jobs.shutdown()
        }
    }

    @Test
    fun ecsLifecycleQueriesAndSerialisation() {
        val world = EcsWorld()
        val a = world.create("A")
        val b = world.create("B", a)
        world.add(a, TransformEcs())
        world.add(b, TransformEcs())
        assertEquals(2, world.query(TransformEcs::class.java).size)
        world.destroy(b)
        assertEquals(1, world.query(TransformEcs::class.java).size)
        assertFalse(world.isAlive(b))
        val c = world.create("C")
        assertEquals("slot reused", b.index, c.index)
        assertFalse("stale handle rejected", world.isAlive(b))
        val bridge = SceneEcsBridge(world)
        val scene = Scene("Main")
        val go = scene.create("Player")
        go.x = 3f; go.y = 1.5f; go.z = -2f; go.tag = "Player"
        val child = scene.create("Gun", go)
        child.x = 0.5f
        assertEquals(2, bridge.syncFromScene(scene))
        val e = bridge.entityFor(go)!!
        assertEquals(3f, world.get(e, TransformEcs::class.java)!!.position.x, 1e-4f)
        val json = world.toJson()
        val restored = EcsWorld()
        val bridge2 = SceneEcsBridge(restored)
        restored.fromJson(json)
        assertEquals(2, restored.entityCount)
        println("SIM foundation ecs ${world.stats()} bridge=${bridge.describe()}")
    }

    @Test
    fun fixedTimestepAndProfiler() {
        val time = TimeStep(fixedDelta = 1f / 60f, maxSubSteps = 4)
        var steps = 0
        for (f in 0 until 60) time.advance(1f / 60f) { steps++ }
        assertEquals(60, steps)
        assertEquals(1f, time.time.toFloat(), 0.02f)
        assertEquals(60L, time.fixedFrame)
        // a hitch runs at most maxSubSteps
        val before = time.fixedFrame
        time.advance(0.25f) { steps++ }
        assertEquals(4L, time.fixedFrame - before)
        assertTrue("saturation detected", time.saturated)
        // time scale pauses the simulation but keeps unscaled time moving
        time.timeScale = 0f
        val frozen = time.fixedFrame
        time.advance(0.1f) { steps++ }
        assertEquals(frozen, time.fixedFrame)
        assertTrue(time.unscaledTime > 0.9)
        val prof = Profiler()
        prof.beginFrame()
        prof.scope("physics") { Thread.sleep(1) }
        prof.increment("bodies", 7)
        prof.endFrame()
        assertTrue(prof.sample("physics").lastMs >= 0.5f)
        assertEquals(7L, prof.counter("bodies"))
        assertTrue(prof.averageFrameMs() > 0f)
        println("SIM foundation time steps=${time.fixedFrame} profiler=${prof.summary()}")
    }
}
