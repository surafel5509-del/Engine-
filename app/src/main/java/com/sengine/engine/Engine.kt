package com.sengine.engine

import com.sengine.engine.core.AudioSource
import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Component
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SceneSerializer
import com.sengine.engine.physics.PhysicsWorld
import com.sengine.engine.render.View2D
import com.sengine.engine.script.ScriptSystem
import com.sengine.project.Project
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * The runtime core. All scene mutation happens while holding [lock];
 * simulation runs on the GL thread via [tick].
 */
class Engine(val project: Project, initialScene: Scene) {

    enum class Mode { EDIT, PLAY, PAUSED }

    interface Listener {
        fun onLog(level: Int, message: String) {}
        fun onModeChanged(mode: Mode) {}
        fun onSceneReplaced() {}
    }

    val lock = Any()
    @Volatile var scene: Scene = initialScene
        private set
    @Volatile var mode = Mode.EDIT
        private set

    val input = Input()
    val physics = PhysicsWorld()
    val physics3D = com.sengine.engine.physics.PhysicsWorld3D()
    val animation = com.sengine.engine.anim.AnimationSystem(project)
    val scripts = ScriptSystem(this)
    val audio = AudioSystem(project)
    val gameView = View2D()
    val ui = UISystem(this)
    val storage by lazy { Storage(java.io.File(project.saveDir, "storage.json")) }
    /** Host hooks (vibrate / quit / links); set by the player activity. */
    @Volatile var platform: Platform? = null
    /** Game speed multiplier (0 pauses gameplay while UI keeps working). */
    var timeScale = 1f
    var unscaledTime = 0.0; private set
    var deltaTime = 0f; private set
    @Volatile var culledObjects = 0
    /** Largest dt the simulation will accept before clamping (spiral-of-death guard). */
    var maxDeltaTime = 0.1f
    /** Physics sub-steps allowed per frame; excess time is dropped. */
    var maxPhysicsSteps = 5

    var time = 0.0; private set
    var frame = 0L; private set
    @Volatile var fps = 0f; private set
    // profiler (milliseconds, smoothed)
    @Volatile var scriptMs = 0f
    @Volatile var physicsMs = 0f
    @Volatile var renderMs = 0f
    @Volatile var drawCalls = 0
    private var fpsAcc = 0f
    private var fpsFrames = 0

    /** Live performance counters (v7 Pro). Read by the editor profiler, exported games and scripts. */
    val stats = EngineStats()

    class EngineStats {
        @Volatile var frameMs = 0f
        @Volatile var drawCalls = 0
        @Volatile var renderBatches = 0
        @Volatile var quadsDrawn = 0
        @Volatile var culled2D = 0
        @Volatile var culled3D = 0
        @Volatile var visible3D = 0
        @Volatile var objects = 0
        @Volatile var components = 0
        @Volatile var particles = 0
        @Volatile var bodies2D = 0
        @Volatile var pairs2D = 0
        @Volatile var contacts2D = 0
        @Volatile var bodies3D = 0
        @Volatile var pairs3D = 0
        @Volatile var contacts3D = 0
        @Volatile var scriptsRunning = 0
        @Volatile var heapMb = 0
        @Volatile var audioVoices = 0
        fun snapshot(): String =
            "frame %.2fms | draw calls %d (batches %d, quads %d) | culled 2D %d / 3D %d | objects %d (%d comps) | particles %d | bodies %d+%d (pairs %d+%d) | scripts %d | heap %d MB".format(
                frameMs, drawCalls, renderBatches, quadsDrawn, culled2D, culled3D + visible3D * 0, objects, components, particles,
                bodies2D, bodies3D, pairs2D, pairs3D, scriptsRunning, heapMb)
    }

    /** Global graphics/simulation quality switches (v7 Pro). Games may tune these at runtime. */
    val quality = QualitySettings()

    class QualitySettings {
        /** GPU sprite batching (draw-call reduction). Auto-disables if the driver fails to compile the program. */
        @Volatile var batching = true
        /** Viewport culling for 2D sprites, text and particles. */
        @Volatile var culling2D = true
        /** Shadow rendering master switch (Camera3D still decides quality level). */
        @Volatile var shadows = true
        /** Global particle budget multiplier (0..4). */
        @Volatile var particleBudget = 1f
        /** Skip updating particles that are far from any camera (3D distance in units, 0 = off). */
        @Volatile var particleCullDistance = 0f
        /** Enable the spatial-hash physics broadphase (disable to compare with brute force). */
        @Volatile var physicsBroadphase = true
    }

    val listeners = java.util.concurrent.CopyOnWriteArrayList<Listener>()
    private val commands = ConcurrentLinkedQueue<() -> Unit>()
    private var snapshot: String? = null
    private var snapshotScene = ""
    private var pendingSceneLoad: String? = null

    val logs = ArrayDeque<String>()

    init {
        physics.listener = scripts
        physics3D.listener = scripts
    }

    // ---------------------------------------------------------------- commands
    fun post(cmd: () -> Unit) { commands.add(cmd) }

    fun play() = post {
        when (mode) {
            Mode.EDIT -> startPlay()
            Mode.PAUSED -> setMode(Mode.PLAY)
            else -> {}
        }
    }

    fun pause() = post { if (mode == Mode.PLAY) setMode(Mode.PAUSED) }
    fun stop() = post { if (mode != Mode.EDIT) stopPlay() }
    fun stepFrame() = post { if (mode == Mode.PAUSED) runFrame(1f / 60f) }

    /** Replace the edited scene (editor only, call while holding lock). */
    fun replaceScene(s: Scene) {
        scene = s
        s.invalidateDrawList()
        listeners.forEach { it.onSceneReplaced() }
    }

    fun requestLoadScene(name: String) { pendingSceneLoad = name }

    fun log(level: Int, msg: String) {
        synchronized(logs) {
            logs.addLast(msg)
            while (logs.size > 300) logs.removeFirst()
        }
        listeners.forEach { it.onLog(level, msg) }
    }

    private fun setMode(m: Mode) {
        mode = m
        listeners.forEach { it.onModeChanged(m) }
    }

    // ---------------------------------------------------------------- play mode
    private fun startPlay() {
        snapshot = SceneSerializer.toJson(scene).toString()
        snapshotScene = scene.name
        time = 0.0; frame = 0
        input.clear()
        setMode(Mode.PLAY)
        beginScene()
        log(0, "▶ Play: ${scene.name}")
    }

    private fun beginScene() {
        timeScale = 1f
        for (go in scene.objects) for (c in go.components) c.resetRuntime()
        prepareVoxels(true)
        physics.reset()
        physics3D.reset()
        audio.start()
        scene.updateTransforms()
        snapCameraToTarget()
        updateCamera3DFollow(10f)
        scene.updateTransforms()
        updateGameView()
        scene.rebuildIndex()
        val sources = scene.index.audioSources
        for (i in sources.indices) {
            val src = sources[i]
            val go = src.gameObject ?: continue
            if (!go.isActiveInHierarchy()) continue
            if (src.playOnStart) audio.play(src.clip, src.volume, src.loop)
        }
        scripts.begin()
    }

    private fun endScene() {
        scripts.end()
        audio.stop()
    }

    private fun stopPlay() {
        endScene()
        val snap = snapshot
        if (snap != null) scene = SceneSerializer.fromJson(JSONObject(snap))
        snapshot = null
        input.clear()
        setMode(Mode.EDIT)
        scene.invalidateDrawList()
        listeners.forEach { it.onSceneReplaced() }
        log(0, "■ Stopped")
    }

    // ---------------------------------------------------------------- loop
    /** Called on the GL thread with [lock] held. */
    fun tick(dt: Float) {
        while (true) {
            val c = commands.poll() ?: break
            try { c() } catch (e: Exception) { log(2, "Engine error: ${e.message}") }
        }
        fpsAcc += dt; fpsFrames++
        if (fpsAcc >= 0.5f) { fps = fpsFrames / fpsAcc; fpsAcc = 0f; fpsFrames = 0 }

        when (mode) {
            Mode.PLAY -> runFrame(dt)
            Mode.EDIT -> { scene.updateTransforms(); scene.rebuildIndex(); updateParticles(dt); updateAnimators(dt, false); prepareVoxels(false); updateModelAnims(dt) }
            Mode.PAUSED -> scene.updateTransforms()
        }
    }

    private fun runFrame(dt0: Float) {
        val raw = dt0.coerceAtMost(maxDeltaTime)
        val dt = raw * timeScale.coerceIn(0f, 10f)
        deltaTime = dt
        time += dt; unscaledTime += raw; frame++
        val frameStart = System.nanoTime()
        scene.updateTransforms()
        updateGameView()
        ui.process()
        input.beginFrame(gameView)
        val t0 = System.nanoTime()
        scripts.update(dt)
        // runtime index: one linear pass replaces ~15 per-frame scene scans; scripts may
        // have added/removed components above, so build it fresh each frame
        scene.rebuildIndex()
        val t1 = System.nanoTime()
        physics.maxSteps = maxPhysicsSteps
        physics.step(scene, dt)
        physics3D.maxSteps = maxPhysicsSteps
        physics3D.step(scene, dt)
        val t2 = System.nanoTime()
        scriptMs = scriptMs * 0.9f + (t1 - t0) / 1e6f * 0.1f
        physicsMs = physicsMs * 0.9f + (t2 - t1) / 1e6f * 0.1f
        cleanupDestroyed()
        scene.updateTransforms()
        updateCameraFollow(dt)
        updateCamera3DFollow(dt)
        updateGameView()
        updateParticles(dt)
        updateAnimators(dt, true)
        updateModelAnims(dt)
        prepareVoxels(false)
        decayShake(dt)
        collectStats((System.nanoTime() - frameStart) / 1e6f)

        val load = pendingSceneLoad
        if (load != null) {
            pendingSceneLoad = null
            val snap = snapshot
            val fromSnapshot = snap != null && load == snapshotScene
            if (fromSnapshot || project.sceneExists(load)) {
                endScene()
                scene = if (fromSnapshot) SceneSerializer.fromJson(JSONObject(snap!!)) else project.loadScene(load)
                listeners.forEach { it.onSceneReplaced() }
                beginScene()
                log(0, "Loaded scene $load")
            } else log(2, "Scene not found: $load")
        }
    }

    private fun cleanupDestroyed() {
        if (scene.objects.none { it.destroyed }) return
        val dead = scene.objects.filter { it.destroyed || isUnderDestroyed(it) }
        for (d in dead) { d.destroyed = true; scripts.onDestroyed(d) }
        scene.objects.removeAll(dead.toSet())
        scene.invalidateDrawList()
        listeners.forEach { it.onSceneReplaced() }
    }

    private fun isUnderDestroyed(go: GameObject): Boolean {
        var p = go.parent
        while (p != null) { if (p.destroyed) return true; p = p.parent }
        return false
    }

    /** Creates voxel data for VoxelWorld objects and rebuilds a few dirty chunk meshes per frame. */
    private fun prepareVoxels(all: Boolean) {
        val worlds = scene.index.voxelWorlds
        for (wi in worlds.indices) {
            val go = worlds[wi].gameObject ?: continue
            val vw = worlds[wi]
            if (!go.isActiveInHierarchy()) continue
            var d = vw.data
            val key = vw.genKey()
            if (d == null || vw.dataKey != key) {
                d = com.sengine.engine.voxel.VoxelData.create(vw)
                vw.data = d; vw.dataKey = key
            }
            d.rebuildDirty(if (all) Int.MAX_VALUE else 3)
        }
    }

    private fun updateModelAnims(dt: Float) {
        val meshes = scene.index.meshes
        for (i in meshes.indices) {
            val mr = meshes[i]
            if (mr.playingAnim.isBlank() && mr.animation.isBlank()) continue
            mr.animTime += dt * mr.animSpeed
        }
    }

    fun mainCamera3D(): GameObject? {
        val cams = scene.index.cameras3
        for (i in cams.indices) { val go = cams[i].gameObject; if (go != null && go.isActiveInHierarchy()) return go }
        return null
    }

    private fun updateAnimators(dt: Float, playing: Boolean) {
        val anims = scene.index.animators
        for (i in anims.indices) {
            val a = anims[i]
            val go = a.gameObject ?: continue
            val sr = go.getAny<com.sengine.engine.core.SpriteRenderer>() ?: continue
            if (playing && !go.isActiveInHierarchy()) continue
            animation.update(a, sr, dt, playing)
        }
    }

    private fun updateCamera3DFollow(dt: Float) {
        val camGo = mainCamera3D() ?: return
        val cam = camGo.get<com.sengine.engine.core.Camera3D>()!!
        if (cam.follow.isBlank()) return
        val t = scene.find(cam.follow) ?: return
        val tw = t.world3
        val w = camGo.computeWorld3()
        val k = if (cam.smoothing <= 0f) 1f else (1f - exp(-cam.smoothing * dt))
        val gx = tw[12] + cam.offsetX; val gy = tw[13] + cam.offsetY; val gz = tw[14] + cam.offsetZ
        camGo.setWorldPosition3(w[12] + (gx - w[12]) * k, w[13] + (gy - w[13]) * k, w[14] + (gz - w[14]) * k)
    }

    fun shake(amount: Float) {
        mainCamera()?.getAny<Camera2D>()?.let { it.shake = maxOf(it.shake, amount) }
        mainCamera3D()?.getAny<com.sengine.engine.core.Camera3D>()?.let { it.shake = maxOf(it.shake, amount) }
    }

    private fun decayShake(dt: Float) {
        mainCamera()?.getAny<Camera2D>()?.let { it.shake = maxOf(0f, it.shake - dt * 2f * maxOf(1f, it.shake)) }
        mainCamera3D()?.getAny<com.sengine.engine.core.Camera3D>()?.let { it.shake = maxOf(0f, it.shake - dt * 2f * maxOf(1f, it.shake)) }
    }

    fun mainCamera(): GameObject? {
        val cams = scene.index.cameras2
        for (i in cams.indices) { val go = cams[i].gameObject; if (go != null && go.isActiveInHierarchy()) return go }
        return null
    }

    private fun snapCameraToTarget() {
        val camGo = mainCamera() ?: return
        val cam = camGo.get<Camera2D>()!!
        if (cam.follow.isBlank()) return
        val t = scene.find(cam.follow) ?: return
        camGo.setWorldPosition(t.world.tx, t.world.ty)
    }

    private fun updateCameraFollow(dt: Float) {
        val camGo = mainCamera() ?: return
        val cam = camGo.get<Camera2D>()!!
        if (cam.follow.isBlank()) return
        val t = scene.find(cam.follow) ?: return
        val w = camGo.computeWorld()
        val k = if (cam.smoothing <= 0f) 1f else (1f - exp(-cam.smoothing * dt))
        camGo.setWorldPosition(w.tx + (t.world.tx - w.tx) * k, w.ty + (t.world.ty - w.ty) * k)
    }

    fun updateGameView() {
        if (gameView.widthPx > 1 && gameView.heightPx > 1) com.sengine.engine.core.Scene.uiHalfW = 5f * gameView.widthPx / gameView.heightPx
        val camGo = mainCamera()
        if (camGo == null) {
            gameView.cx = 0f; gameView.cy = 0f; gameView.size = 5f
            return
        }
        val w = camGo.computeWorld()
        gameView.cx = w.tx; gameView.cy = w.ty
        gameView.size = camGo.get<Camera2D>()!!.size
    }

    fun backgroundColor(): Int = mainCamera()?.get<Camera2D>()?.background ?: 0xFF1B2533.toInt()

    // ---------------------------------------------------------------- particles
    private fun updateParticles(dt: Float) {
        val budget = quality.particleBudget.coerceIn(0f, 4f)
        val emitters = scene.index.particles
        var total = 0
        for (ei in emitters.indices) {
            val pe = emitters[ei]
            val go = pe.gameObject ?: continue
            val alive = go.isActiveInHierarchy() && pe.enabled
            val w = go.world
            if (alive && pe.emitting) pe.accumulator += pe.rate * dt
            var toEmit = pe.accumulator.toInt() + pe.pendingBurst
            pe.accumulator -= pe.accumulator.toInt()
            pe.pendingBurst = 0
            if (!alive) toEmit = 0
            val cap = if (budget >= 1f) pe.maxParticles else maxOf(1, (pe.maxParticles * budget).toInt())
            while (toEmit-- > 0 && pe.particles.size < cap) {
                val ang = Math.toRadians((pe.direction + w.rotationDeg + (Random.nextFloat() - 0.5f) * pe.spread).toDouble())
                val sp = pe.speed * (0.6f + Random.nextFloat() * 0.8f)
                pe.particles.add(
                    ParticleEmitter.Particle.obtain(
                        w.tx, w.ty, (cos(ang) * sp).toFloat(), (sin(ang) * sp).toFloat(),
                        0f, pe.lifetime * (0.7f + Random.nextFloat() * 0.6f)
                    )
                )
            }
            val parts = pe.particles
            var k = 0
            while (k < parts.size) {
                val p = parts[k]
                p.age += dt
                if (p.age >= p.life) { parts.removeAt(k); ParticleEmitter.Particle.recycle(p); continue }
                p.vy += pe.gravity * dt
                if (pe.wind != 0f) p.vx += pe.wind * dt
                if (pe.turbulence > 0f) { p.vx += (Random.nextFloat() - 0.5f) * pe.turbulence * 4f * dt; p.vy += (Random.nextFloat() - 0.5f) * pe.turbulence * 2f * dt }
                p.x += p.vx * dt; p.y += p.vy * dt
                k++
            }
            total += parts.size
        }
        stats.particles = total
    }

    fun findComponentOwner(c: Component): GameObject? = scene.objects.firstOrNull { c in it.components }

    fun release() {
        synchronized(lock) {
            if (mode != Mode.EDIT) endScene()
            audio.stop()
        }
    }

    private fun collectStats(frameMs: Float) {
        val s = stats
        s.frameMs = frameMs
        s.objects = scene.objects.size
        var comps = 0
        for (go in scene.objects) comps += go.components.size
        s.components = comps
        s.culled2D = culledObjects
        s.bodies2D = physics.bodyCount; s.pairs2D = physics.pairTests; s.contacts2D = physics.contactCount
        s.bodies3D = physics3D.bodyCount; s.pairs3D = physics3D.pairTests; s.contacts3D = physics3D.contactCount
        s.scriptsRunning = scripts.runningCount
        s.heapMb = ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576).toInt()
        s.audioVoices = audio.activeStreams
    }
}
