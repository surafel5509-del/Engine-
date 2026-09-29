package com.sengine

import com.sengine.engine.Engine
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SceneSerializer
import com.sengine.engine.render.Renderer2D
import com.sengine.engine.render.Tex
import com.sengine.project.Project
import com.sengine.project.Templates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.math.abs

/**
 * v7 Pro: coverage for the performance systems — spatial-hash broadphase parity,
 * draw-order list, transform walk, particle pooling, engine stats and the batched
 * 2D renderer state machine.
 */
class EngineProTest {

    private fun newProject(template: Int): Project {
        val dir = Files.createTempDirectory("sengine-pro").toFile()
        val p = Project(File(dir, "ProTest"))
        p.saveMeta()
        Templates.all[template].build(p)
        p.saveMeta()
        return p
    }

    // ---------------------------------------------------------------- broadphase

    private fun crowdScene(n: Int, spread: Float): Scene {
        val s = Scene("Crowd")
        val per = (Math.sqrt(n.toDouble()) + 0.5).toInt()
        for (i in 0 until n) {
            val go = s.create("b$i")
            go.x = (i % per) * spread + (i % 3) * 0.01f
            go.y = (i / per) * spread + (i % 5) * 0.01f
            go.add(Rigidbody2D().apply { bodyType = 0 })
            go.add(Collider2D())
        }
        s.gravityY = -9.81f
        return s
    }

    private fun runPhysics(scene: Scene, frames: Int): Pair<Float, Int> {
        val p = newProject(0)
        val e = Engine(p, scene)
        e.play()
        repeat(frames) { synchronized(e.lock) { e.tick(1f / 60f) } }
        val maxY = scene.objects.maxOf { it.y }
        val pairs = e.physics.pairTests
        e.release()
        return maxY to pairs
    }

    @Test
    fun broadphaseMatchesBruteForceResults() {
        // identical, deterministic layouts; one engine on the spatial hash, one on brute force
        val sceneGrid = crowdScene(80, 1.2f)
        val sceneBrute = crowdScene(80, 1.2f)

        val p = newProject(0)
        val engineGrid = Engine(p, sceneGrid)
        engineGrid.quality.physicsBroadphase = true
        engineGrid.play()
        val engineBrute = Engine(p, sceneBrute)
        engineBrute.quality.physicsBroadphase = false
        engineBrute.play()

        repeat(90) {
            synchronized(engineGrid.lock) { engineGrid.tick(1f / 60f) }
            synchronized(engineBrute.lock) { engineBrute.tick(1f / 60f) }
        }
        for (i in sceneGrid.objects.indices) {
            val a = sceneGrid.objects[i]
            val b = sceneBrute.objects[i]
            assertTrue("body $i diverged: grid(${a.x},${a.y}) brute(${b.x},${b.y})",
                abs(a.x - b.x) < 0.01f && abs(a.y - b.y) < 0.01f)
        }
        engineGrid.release(); engineBrute.release()
        println("SIM broadphase parity over 80 bodies x 90 frames ok (pairs grid=${engineGrid.physics.pairTests} brute=${engineBrute.physics.pairTests})")
    }

    @Test
    fun broadphasePrunesPairsInSparseCrowds() {
        val scene = crowdScene(400, 3f)
        val p = newProject(0)
        val e = Engine(p, scene)
        e.quality.physicsBroadphase = true
        e.play()
        repeat(30) { synchronized(e.lock) { e.tick(1f / 60f) } }
        val gridPairs = e.physics.pairTests
        e.quality.physicsBroadphase = false
        repeat(1) { synchronized(e.lock) { e.tick(1f / 60f) } }
        val brutePairs = e.physics.pairTests
        e.release()
        assertTrue("grid=$gridPairs brute=$brutePairs", gridPairs < brutePairs / 4)
        println("SIM broadphase pairs grid=$gridPairs brute=$brutePairs (400 bodies)")
    }

    // ---------------------------------------------------------------- draw list + find

    @Test
    fun drawListOrdersBySortOrderThenCreation() {
        val s = Scene("Order")
        val a = s.create("a"); a.order = 5
        val b = s.create("b")
        val c = s.create("c"); c.order = 5
        b.active = false
        s.updateTransforms()
        val list = s.drawList()
        assertEquals(listOf<GameObject>(a, c), list) // ascending order, stable; inactive skipped
        // changing order invalidates the cached list within the same frame
        b.active = true
        s.invalidateDrawList()
        assertEquals(listOf<GameObject>(b, a, c), s.drawList())
    }

    @Test
    fun findPrefersActiveAndSurvivesRenames() {
        val s = Scene("Find")
        val dead = s.create("dead"); dead.name = "hero"
        val alive = s.create("alive"); alive.name = "hero"
        alive.active = true; dead.active = false
        s.updateTransforms()
        assertEquals(alive, s.find("hero"))
        alive.active = false
        s.updateTransforms()
        assertEquals(dead, s.find("hero"))
        alive.name = "renamed"
        s.updateTransforms()
        assertEquals(alive, s.find("renamed"))
        assertEquals(alive, s.find("renamed")) // memoised path
    }

    @Test
    fun hierarchyTransformsMatchManualComposition() {
        val s = Scene("Tf")
        val parent = s.create("p"); parent.x = 3f; parent.y = -2f; parent.rotation = 30f; parent.scaleX = 2f; parent.scaleY = 1.5f
        val child = parent.let { s.create("c", it) }; child.x = 1f; child.y = 1f; child.rotation = -45f; child.scaleX = 0.5f; child.scaleY = 0.5f
        val grand = s.create("g", child); grand.x = 2f; grand.y = 0f
        s.updateTransforms()
        val manual = child.computeWorld()
        assertTrue(abs(child.world.tx - manual.tx) < 1e-4f && abs(child.world.ty - manual.ty) < 1e-4f)
        assertTrue(abs(child.world.a - manual.a) < 1e-4f && abs(child.world.d - manual.d) < 1e-4f)
        // grandchild world equals parent chain composition
        val gm = grand.computeWorld()
        assertTrue(abs(grand.world.tx - gm.tx) < 1e-4f && abs(grand.world.ty - gm.ty) < 1e-4f)
    }

    // ---------------------------------------------------------------- particles + stats

    @Test
    fun particlePoolRecyclesInstancesAndRespectsBudget() {
        val s = Scene("Part")
        val go = s.create("fx")
        val pe = go.add(ParticleEmitter())
        pe.rate = 2000f; pe.lifetime = 0.1f; pe.maxParticles = 4000
        val p = newProject(0)
        val e = Engine(p, s)
        e.play()
        repeat(30) { synchronized(e.lock) { e.tick(1f / 60f) } }
        assertTrue("emitter alive", pe.particles.isNotEmpty())
        e.quality.particleBudget = 0.25f
        repeat(10) { synchronized(e.lock) { e.tick(1f / 60f) } }
        assertTrue("budget cap: ${pe.particles.size}", pe.particles.size <= 1000)
        assertTrue("stats sees particles", e.stats.particles > 0)
        // pooled particles: the same instance objects are reused rather than freshly allocated
        val identities = HashSet<Any>(pe.particles)
        e.quality.particleBudget = 1f
        repeat(60) { synchronized(e.lock) { e.tick(1f / 60f) } }
        val reused = pe.particles.count { it in identities }
        assertTrue("pool should recycle instances (reused $reused of ${pe.particles.size})", reused > 0)
        e.release()
    }

    @Test
    fun engineStatsFillDuringPlay() {
        val p = newProject(1)
        val e = Engine(p, p.loadScene(p.startScene))
        e.gameView.widthPx = 1280; e.gameView.heightPx = 720
        e.play()
        repeat(40) { synchronized(e.lock) { e.tick(1f / 60f) } }
        val s = e.stats
        assertTrue(s.objects > 0)
        assertTrue(s.components >= s.objects)
        assertTrue(s.heapMb > 0)
        assertTrue("fps ${e.fps}", e.fps > 0f)
        assertTrue(s.scriptsRunning >= 0)
        e.release()
    }

    // ---------------------------------------------------------------- batched 2D renderer

    private fun testRenderer(): Renderer2D {
        val r = Renderer2D()
        val shaders = com.sengine.engine.render.ShaderLibrary(newProject(0)) {}
        shaders.init() // compiles against the inert GLES stubs; programs are plain handles
        r.init(shaders)
        r.batchProgramReady = true // GLES calls are inert here; the CPU batching logic still runs
        r.batching = true
        return r
    }

    @Test
    fun batchedRendererMergesCompatibleQuads() {
        val r = testRenderer()
        val t1 = Tex(11, 4, 4)
        val t2 = Tex(22, 4, 4)
        val m = com.sengine.engine.math.Affine().setTRS(0f, 0f, 0f, 2f, 2f)
        val view = FloatArray(16)
        android.opengl.Matrix.orthoM(view, 0, -5f, 5f, -5f, 5f, -1f, 1f)
        r.begin(view)
        repeat(50) { r.quad(m, 0xFFFFFFFF.toInt(), 0, t1, 10f) }
        assertEquals("no flush yet, all merged", 0, r.drawCalls)
        assertEquals(50, r.quadsDrawn)
        r.quad(m, 0xFFFFFFFF.toInt(), 0, t2, 10f) // texture switch -> previous batch must flush on next flush
        r.flush()
        assertEquals("50 quads in one batch + 1", 2, r.drawCalls)
        assertEquals(51, r.quadsDrawn)
        assertTrue("batches tracked", r.batches >= 2)
    }

    @Test
    fun batchedRendererKeepsLegacyPathFor3dMatrices() {
        val r = testRenderer()
        val view = FloatArray(16)
        android.opengl.Matrix.orthoM(view, 0, -5f, 5f, -5f, 5f, -1f, 1f)
        r.begin(view)
        // a 3D-rotated model matrix must not be flattened into the 2D batch
        val m3d = FloatArray(16).also { com.sengine.engine.math.Mat4.trs(it, 0f, 0f, 0f, 45f, 0f, 0f, 1f, 1f, 1f) }
        r.quadModel(m3d, 0xFFFFFFFF.toInt(), 0, null, 10f)
        assertEquals("3D matrix draws immediately on the legacy path", 1, r.drawCalls)
        assertEquals("no batch was formed", 0, r.batches)
    }

    @Test
    fun batchingDisabledFallsBackPerQuad() {
        val r = testRenderer()
        r.batching = false
        val m = com.sengine.engine.math.Affine().setTRS(0f, 0f, 0f, 1f, 1f)
        val view = FloatArray(16)
        android.opengl.Matrix.orthoM(view, 0, -5f, 5f, -5f, 5f, -1f, 1f)
        r.begin(view)
        repeat(10) { r.quad(m, 0xFFFFFFFF.toInt(), 0, null, 10f) }
        assertEquals(10, r.quadsDrawn) // legacy quads are counted too
        assertEquals("one draw call per quad", 10, r.drawCalls)
    }

    // ---------------------------------------------------------------- end-to-end + api

    @Test
    fun templatesStillPlayWithV7Systems() {
        val p = newProject(2)
        val e = Engine(p, p.loadScene(p.startScene))
        e.gameView.widthPx = 1280; e.gameView.heightPx = 720
        val errors = ArrayList<String>()
        e.listeners.add(object : Engine.Listener {
            override fun onLog(level: Int, message: String) { if (level >= 2) errors.add(message) }
        })
        e.play()
        repeat(180) { synchronized(e.lock) { e.tick(1f / 60f) } }
        assertTrue("engine errors: $errors", errors.isEmpty())
        assertTrue(e.stats.objects > 0)
        e.release()
        println("SIM v7 3s play: objects=${e.stats.objects} bodies=${e.stats.bodies2D} pairs=${e.stats.pairs2D} heap=${e.stats.heapMb}MB")
    }

    @Test
    fun scriptApiExposesStatsAndQuality() {
        val p = newProject(1)
        val e = Engine(p, p.loadScene(p.startScene))
        e.gameView.widthPx = 1280; e.gameView.heightPx = 720
        e.play()
        repeat(5) { synchronized(e.lock) { e.tick(1f / 60f) } }
        val js = """
            var st = scene.stats();
            var ok = st.fps >= 0 && st.objects > 0 && st.heapMb > 0;
            scene.setBatching(false); var batchingOff = scene.getBatching() === false;
            scene.setBatching(true);
            scene.setParticleBudget(2.5); var budget = scene.getParticleBudget() === 2.5;
            scene.setSolverIterations(3); var iters = scene.getSolverIterations() === 3;
            ok && batchingOff && budget && iters;
        """.trimIndent()
        val cx = org.mozilla.javascript.Context.enter()
        cx.optimizationLevel = -1
        try {
            val scope = cx.initStandardObjects()
            val sscene = com.sengine.engine.script.SScene(e, e.scripts)
            org.mozilla.javascript.ScriptableObject.putProperty(scope, "scene", sscene)
            val out = cx.evaluateString(scope, js, "proapi", 1, null)
            assertEquals(true, out)
        } finally {
            org.mozilla.javascript.Context.exit()
            e.release()
        }
    }
}
