package com.sengine.engine.ultimate

import com.sengine.engine.core.Scene
import com.sengine.engine.core.TimeStep
import com.sengine.engine.ecs.EcsWorld
import com.sengine.engine.ecs.SceneEcsBridge
import com.sengine.engine.jobs.JobSystem
import com.sengine.engine.jobs.Profiler

/**
 * A pluggable engine subsystem. Every Ultimate module (physics, AI, animation, rendering,
 * assets, networking, audio, VFX, cinematics, gameplay) registers an adapter implementing this
 * interface, so the runtime can drive, profile, save and inspect all of them uniformly.
 */
interface UltimateSubsystem {
    /** Stable machine id, e.g. `physics`, `ai`, `net`. */
    val id: String
    /** Human readable name shown in the editor hub. */
    val displayName: String
    /** Category used for grouping in the editor hub. */
    val category: String get() = "Runtime"
    val enabled: Boolean get() = true
    /** Called once when the runtime starts (play mode / game launch). */
    fun initialize(runtime: UltimateRuntime) {}
    /** Called every rendered frame with the scaled frame delta. */
    fun update(dt: Float) {}
    /** Called inside the fixed simulation step (physics, netcode, AI decision tick). */
    fun fixedUpdate(fixedDt: Float) {}
    /** Called after the frame, before rendering (interpolation, sync back to the scene). */
    fun lateUpdate(dt: Float) {}
    /** Called when play mode stops. */
    fun shutdown() {}
    /** One line of live status for the editor hub / profiler. */
    fun status(): String = "ok"
    /** Sub-system specific counters for the profiler overlay. */
    fun counters(): Map<String, String> = emptyMap()
}

/**
 * The Ultimate Edition runtime: one object that owns the job system, the ECS world, the fixed-step
 * clock, the profiler and every registered subsystem. `Engine` creates one per project and drives
 * it from the main loop, so the classic scene graph and the new data oriented subsystems stay in
 * sync without either side knowing about the other.
 */
class UltimateRuntime(val sceneProvider: () -> Scene) {

    /** Cooperative worker pool shared by physics, animation, AI, rendering and streaming. */
    val jobs = JobSystem()
    /** Frame + counter profiler shared by all subsystems. */
    val profiler = Profiler()
    /** Fixed timestep clock (render frame vs simulation step). */
    val time = TimeStep()
    /** Data oriented world used by the gameplay/net/AI layers. */
    val ecs = EcsWorld()
    /** Mirrors the classic scene graph into the ECS world. */
    val bridge = SceneEcsBridge(ecs)

    private val subsystems = LinkedHashMap<String, UltimateSubsystem>()
    private val byCategory = LinkedHashMap<String, MutableList<String>>()
    var started = false; private set
    var frameIndex = 0L; private set
    var lastUpdateMs = 0f; private set
    /** Set to false to run without ECS mirroring (saves work for 2D-only projects). */
    var ecsMirroring = true

    /** Registers (or replaces) a subsystem. */
    fun register(subsystem: UltimateSubsystem): UltimateRuntime {
        subsystems[subsystem.id] = subsystem
        byCategory.getOrPut(subsystem.category) { ArrayList() }.let { if (!it.contains(subsystem.id)) it.add(subsystem.id) }
        if (started) subsystem.initialize(this)
        return this
    }

    fun unregister(id: String): Boolean {
        val s = subsystems.remove(id) ?: return false
        byCategory.values.forEach { it.remove(id) }
        s.shutdown()
        return true
    }

    fun subsystem(id: String): UltimateSubsystem? = subsystems[id]
    fun all(): List<UltimateSubsystem> = subsystems.values.toList()
    fun ids(): List<String> = subsystems.keys.toList()
    fun categoryNames(): List<String> = byCategory.keys.toList()
    fun inCategory(category: String): List<UltimateSubsystem> =
        byCategory[category]?.mapNotNull { subsystems[it] } ?: emptyList()
    fun count(): Int = subsystems.size

    fun start() {
        if (started) return
        started = true
        time.reset()
        for (s in subsystems.values) s.initialize(this)
    }

    fun stop() {
        if (!started) return
        for (s in subsystems.values) s.shutdown()
        started = false
    }

    /**
     * Advances the whole engine by [rawDelta] seconds. Returns the scaled frame delta.
     * Order: fixed steps (simulation) -> update (frame) -> late update (sync back).
     */
    fun advance(rawDelta: Float): Float {
        val t0 = System.nanoTime()
        profiler.beginFrame()
        if (!started) start()
        val scaled = time.advance(rawDelta) { fixedDt ->
            for (s in subsystems.values) {
                if (!s.enabled) continue
                profiler.scope("fixed:" + s.id) { s.fixedUpdate(fixedDt) }
            }
        }
        for (s in subsystems.values) {
            if (!s.enabled) continue
            profiler.scope("update:" + s.id) { s.update(scaled) }
        }
        if (ecsMirroring) profiler.scope("ecs:bridge") { bridge.syncFromScene(sceneProvider()) }
        for (s in subsystems.values) {
            if (!s.enabled) continue
            profiler.scope("late:" + s.id) { s.lateUpdate(scaled) }
        }
        profiler.endFrame()
        frameIndex++
        lastUpdateMs = (System.nanoTime() - t0) / 1e6f
        return scaled
    }

    /** Subsystem report for the editor hub (one line per subsystem). */
    fun report(): List<String> = subsystems.values.map { "${it.displayName} [${it.id}]: ${it.status()}" }

    fun counters(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        out["entities"] = ecs.entityCount.toString()
        out["jobs"] = jobs.workerCount().toString()
        out["frame"] = profiler.frameIndex.toString()
        for (s in subsystems.values) for ((k, v) in s.counters()) out["${s.id}.$k"] = v
        return out
    }

    fun stats(): String = buildString {
        append("UltimateRuntime(subsystems=${subsystems.size}, entities=${ecs.entityCount}, ")
        append("jobs=${jobs.workerCount()}, frame=${profiler.frameIndex}, ")
        append("${time.describe()})")
    }

    /** Full textual dump used by the editor hub and the diagnostics test runner. */
    fun dump(): String = buildString {
        appendLine(stats())
        appendLine(profiler.dump())
        for (s in subsystems.values) {
            appendLine("- ${s.displayName} (${s.id}, ${s.category}): ${s.status()}")
            for ((k, v) in s.counters()) appendLine("    $k = $v")
        }
        append("ecs: ${ecs.stats()}")
    }

    fun release() {
        stop()
        jobs.shutdown()
    }
}
