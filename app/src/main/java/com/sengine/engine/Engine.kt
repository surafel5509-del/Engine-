package com.sengine.engine

import com.sengine.engine.core.AudioSource
import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.CharacterController3D
import com.sengine.engine.core.Ragdoll
import com.sengine.engine.core.Collider3D
import com.sengine.engine.core.Component
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Joint3D
import com.sengine.engine.core.MeshRenderer
import com.sengine.engine.core.Rigidbody3D
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
import kotlin.math.max
import kotlin.math.min
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
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            go.get<AudioSource>()?.let { if (it.playOnStart) audio.play(it.clip, it.volume, it.loop) }
        }
        scripts.begin()
        if (mode == Mode.PLAY) for (go in scene.objects.toList()) {
            if (!go.isActiveInHierarchy()) continue
            val rd = go.getAny<Ragdoll>() ?: continue
            if (rd.enabled && rd.trigger == 1) spawnRagdoll(go)
        }
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
            Mode.EDIT -> { scene.updateTransforms(); updateParticles(dt); updateAnimators(dt, false); prepareVoxels(false); updateModelAnims(dt) }
            Mode.PAUSED -> scene.updateTransforms()
        }
    }

    private fun runFrame(dt0: Float) {
        val raw = dt0.coerceAtMost(0.1f)
        val dt = raw * timeScale.coerceIn(0f, 10f)
        deltaTime = dt
        time += dt; unscaledTime += raw; frame++
        scene.updateTransforms()
        updateGameView()
        ui.process()
        input.beginFrame(gameView)
        val t0 = System.nanoTime()
        updateCharacterControllers(dt)
        scripts.update(dt)
        val t1 = System.nanoTime()
        physics.step(scene, dt)
        physics3D.step(scene, dt)
        val t2 = System.nanoTime()
        scriptMs = scriptMs * 0.9f + (t1 - t0) / 1e6f * 0.1f
        physicsMs = physicsMs * 0.9f + (t2 - t1) / 1e6f * 0.1f
        tickRagdolls(dt)
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

    // ---------------------------------------------------------------- v7: character controllers & ragdolls
    private fun updateCharacterControllers(dt: Float) {
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val cc = go.get<CharacterController3D>() ?: continue
            val rb = go.get<Rigidbody3D>() ?: continue
            val mx = input.axisX; val my = input.axisY
            val mag = min(1f, kotlin.math.sqrt(mx * mx + my * my))
            cc.moving = mag > 0.05f
            val k = (if (rb.grounded) 0.35f else cc.airControl * 0.12f).coerceAtMost(1f)
            val tx = mx * cc.speed; val tz = -my * cc.speed
            rb.vx += (tx - rb.vx) * k
            rb.vz += (tz - rb.vz) * k
            if (input.aDown && rb.grounded) { rb.vy = cc.jump; rb.grounded = false }
            if (cc.rotateToMove && cc.moving) go.rotY = Math.toDegrees(kotlin.math.atan2(mx.toDouble(), (-my).toDouble())).toFloat()
            cc.grounded = rb.grounded
            physics3D.wake(rb)
        }
    }

    private class RagdollPiece(val pieces: MutableList<GameObject>, var life: Float)
    private val ragdollTimers = ArrayList<RagdollPiece>()

    /**
     * Turns a rigged model object into a jointed physics ragdoll: one dynamic body per rig part
     * (world-baked single-part models), fixed joints up the skeleton and an initial impulse.
     */
    fun spawnRagdoll(go: GameObject, impulse: Float? = null) {
        if (go.destroyed || !go.active) return
        val rd = go.getAny<Ragdoll>()
        val strength = impulse ?: rd?.strength ?: 5f
        val upBias = rd?.upBias ?: 0.6f
        val mr = go.getAny<MeshRenderer>()
        val modelName = mr?.model ?: ""
        val model = if (modelName.endsWith(".smodel")) try { com.sengine.engine.model.SModel.parse(project.readAsset(modelName) ?: "") } catch (_: Exception) { null } else null
        val pieces = ArrayList<GameObject>()
        val world = go.computeWorld3()
        if (model != null && model.parts.isNotEmpty() && (rd?.useModelRig != false && (model.rig.isNotEmpty() || model.parts.size > 1))) {
            val clip = model.clip(mr?.playingAnim?.ifBlank { mr?.animation ?: "" } ?: "")
            val mats = Array(model.parts.size) { FloatArray(16) }
            model.matrices(clip, mr?.animTime ?: 0f, mats)
            for ((i, part) in model.parts.withIndex()) {
                if (!part.visible || part.faces.isEmpty()) continue
                // bake the part's current world-space geometry into a single-part model
                val baked = com.sengine.engine.model.SPart(part.name)
                baked.color = part.color; baked.smooth = part.smooth
                for (v in part.verts) baked.verts.add(Mat4Point(world, mats[i], v))
                for ((fi, f) in part.faces.withIndex()) {
                    if (f.size < 3 || f.any { it !in part.verts.indices }) continue
                    baked.faces.add(f.copyOf()); baked.faceColors.add(part.faceColors[fi]); baked.groups.add(0); baked.uvs.add(part.uvs[fi]?.copyOf())
                }
                baked.fixColors()
                val asset = "_ragdoll_${go.id}_$i.smodel"
                project.writeAsset(asset, com.sengine.engine.model.SModel(mutableListOf(baked)).toJson().toString())
                val piece = scene.create("${go.name}.${part.name}")
                piece.tag = go.tag
                piece.add(MeshRenderer().also { it.mesh = MeshRenderer.MESHES.size - 1; it.model = asset; it.texture = part.texture; it.color = part.color })
                // box collider from the baked bounds
                var mnx = Float.MAX_VALUE; var mny = Float.MAX_VALUE; var mnz = Float.MAX_VALUE; var mxx = -mnx; var mxy = -mny; var mxz = -mnz
                for (v in baked.verts) { mnx = min(mnx, v[0]); mny = min(mny, v[1]); mnz = min(mnz, v[2]); mxx = max(mxx, v[0]); mxy = max(mxy, v[1]); mxz = max(mxz, v[2]) }
                piece.x = (mnx + mxx) / 2; piece.y = (mny + mxy) / 2; piece.z = (mnz + mxz) / 2
                piece.add(Collider3D().also { it.sizeX = max(0.05f, mxx - mnx); it.sizeY = max(0.05f, mxy - mny); it.sizeZ = max(0.05f, mxz - mnz) })
                piece.add(Rigidbody3D().also { it.friction = 0.6f })
                val pj = model.parts.getOrNull(part.parent)
                piece.add(Joint3D().also { j -> j.kind = 0; j.target = if (pj != null && pj.visible) "${go.name}.${pj.name}" else ""; j.breakForce = 0f })
                // impulse: mostly up + a little sideways, like a knock-back
                val a = kotlin.random.Random.nextFloat() * 6.2832f
                piece.get<Rigidbody3D>()!!.also {
                    it.vx = kotlin.math.cos(a) * strength * (1f - upBias) * 0.5f
                    it.vy = strength * upBias + 1f
                    it.vz = kotlin.math.sin(a) * strength * (1f - upBias) * 0.5f
                }
                pieces.add(piece)
            }
            if (pieces.isNotEmpty()) {
                go.active = false
                val life = rd?.lifetime ?: 0f
                if (life > 0f) ragdollTimers.add(RagdollPiece(pieces, life))
                log(0, "Ragdoll: ${pieces.size} parts from ${go.name}")
                return
            }
        }
        // fallback: no model rig — knock the whole object over with physics
        if (go.get<Rigidbody3D>() == null) go.add(Rigidbody3D())
        if (go.get<Collider3D>() == null) go.add(Collider3D())
        go.get<Rigidbody3D>()!!.also {
            it.vx += kotlin.random.Random.nextFloat() * strength - strength / 2
            it.vy += strength * upBias
            it.vz += kotlin.random.Random.nextFloat() * strength - strength / 2
        }
        physics3D.wake(go.get<Rigidbody3D>()!!)
    }

    private fun Mat4Point(world: FloatArray, part: FloatArray, v: FloatArray): FloatArray {
        val local = com.sengine.engine.math.Mat4.point(part, v[0], v[1], v[2])
        return com.sengine.engine.math.Mat4.point(world, local[0], local[1], local[2])
    }

    private fun tickRagdolls(dt: Float) {
        if (ragdollTimers.isEmpty()) return
        val it = ragdollTimers.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.life -= dt
            if (e.life <= 0f) { for (p in e.pieces) p.destroyed = true; it.remove() }
        }
    }

    private fun cleanupDestroyed() {
        if (scene.objects.none { it.destroyed }) return
        val dead = scene.objects.filter { it.destroyed || isUnderDestroyed(it) }
        for (d in dead) { d.destroyed = true; scripts.onDestroyed(d) }
        scene.objects.removeAll(dead.toSet())
        listeners.forEach { it.onSceneReplaced() }
    }

    private fun isUnderDestroyed(go: GameObject): Boolean {
        var p = go.parent
        while (p != null) { if (p.destroyed) return true; p = p.parent }
        return false
    }

    /** Creates voxel data for VoxelWorld objects and rebuilds a few dirty chunk meshes per frame. */
    private fun prepareVoxels(all: Boolean) {
        for (go in scene.objects) {
            val vw = go.get<com.sengine.engine.core.VoxelWorld>() ?: continue
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
        for (go in scene.objects) {
            val mr = go.get<com.sengine.engine.core.MeshRenderer>() ?: continue
            if (mr.playingAnim.isBlank() && mr.animation.isBlank()) continue
            mr.animTime += dt * mr.animSpeed
        }
    }

    fun mainCamera3D(): GameObject? = scene.objects.firstOrNull { it.isActiveInHierarchy() && it.get<com.sengine.engine.core.Camera3D>() != null }

    private fun updateAnimators(dt: Float, playing: Boolean) {
        for (go in scene.objects) {
            val a = go.getAny<com.sengine.engine.core.Animator>() ?: continue
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

    fun mainCamera(): GameObject? = scene.objects.firstOrNull { it.isActiveInHierarchy() && it.get<Camera2D>() != null }

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
        for (go in scene.objects) {
            val pe = go.getAny<ParticleEmitter>() ?: continue
            val alive = go.isActiveInHierarchy() && pe.enabled
            val w = go.world
            if (alive && pe.emitting) pe.accumulator += pe.rate * dt
            var toEmit = pe.accumulator.toInt() + pe.pendingBurst
            pe.accumulator -= pe.accumulator.toInt()
            pe.pendingBurst = 0
            if (!alive) toEmit = 0
            while (toEmit-- > 0 && pe.particles.size < pe.maxParticles) {
                val ang = Math.toRadians((pe.direction + w.rotationDeg + (Random.nextFloat() - 0.5f) * pe.spread).toDouble())
                val sp = pe.speed * (0.6f + Random.nextFloat() * 0.8f)
                pe.particles.add(
                    ParticleEmitter.Particle(
                        w.tx, w.ty, (cos(ang) * sp).toFloat(), (sin(ang) * sp).toFloat(),
                        0f, pe.lifetime * (0.7f + Random.nextFloat() * 0.6f)
                    )
                )
            }
            val it = pe.particles.iterator()
            while (it.hasNext()) {
                val p = it.next()
                p.age += dt
                if (p.age >= p.life) { it.remove(); continue }
                p.vy += pe.gravity * dt
                if (pe.wind != 0f) p.vx += pe.wind * dt
                if (pe.turbulence > 0f) { p.vx += (Random.nextFloat() - 0.5f) * pe.turbulence * 4f * dt; p.vy += (Random.nextFloat() - 0.5f) * pe.turbulence * 2f * dt }
                p.x += p.vx * dt; p.y += p.vy * dt
            }
        }
    }

    fun findComponentOwner(c: Component): GameObject? = scene.objects.firstOrNull { c in it.components }

    fun release() {
        synchronized(lock) {
            if (mode != Mode.EDIT) endScene()
            audio.stop()
        }
    }
}
