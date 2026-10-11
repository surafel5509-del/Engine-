package com.sengine.engine.ultimate

import com.sengine.engine.ecs.EcsWorld
import com.sengine.engine.ecs.TransformEcs
import com.sengine.engine.jobs.JobSystem
import com.sengine.engine.math.Quat
import com.sengine.engine.math.Vec3

/**
 * Self-test framework of the Ultimate Edition: named checks that validate engine invariants at
 * runtime (editor hub "Diagnostics" tab, build-time validation, CI). A check returns null on
 * success or a message describing the failure.
 */
object UltimateDiagnostics {

    class Result(val name: String, val ok: Boolean, val detail: String, val ms: Float) {
        override fun toString(): String = (if (ok) "PASS " else "FAIL ") + name + " (" + "%.1f".format(ms) + "ms) " + detail
    }

    private val checks = LinkedHashMap<String, () -> String?>()

    val names: List<String> get() = checks.keys.toList()

    fun register(name: String, check: () -> String?) { checks[name] = check }

    fun run(name: String): Result {
        val t0 = System.nanoTime()
        val body = checks[name] ?: return Result(name, false, "unknown check", 0f)
        val error = try { body() } catch (e: Throwable) { "exception: ${e.javaClass.simpleName}: ${e.message}" }
        return Result(name, error == null, error ?: "ok", (System.nanoTime() - t0) / 1e6f)
    }

    fun runAll(): List<Result> = checks.keys.map { run(it) }

    fun summary(results: List<Result>): String =
        "${results.count { it.ok }}/${results.size} checks passed" + (results.firstOrNull { !it.ok }?.let { " - first failure: ${it.name}: ${it.detail}" } ?: "")

    init {
        register("math.quaternion-roundtrip") {
            for (yaw in intArrayOf(0, 45, 90, 180, -90)) for (pitch in intArrayOf(0, 30, -30)) {
                val q = Quat.fromEulerDeg(yaw.toFloat(), pitch.toFloat(), 0f)
                val e = q.toEulerDeg()
                if (Math.abs(e[0] - yaw) > 0.5f && Math.abs(Math.abs(e[0]) - 180f) > 0.5f) return@register "yaw $yaw -> ${e[0]}"
                if (Math.abs(e[1] - pitch) > 0.5f) return@register "pitch $pitch -> ${e[1]}"
            }
            null
        }
        register("math.vec3-invariants") {
            val a = Vec3(3f, 4f, 0f)
            if (Math.abs(a.length() - 5f) > 1e-4f) return@register "length ${a.length()}"
            if (a.cross(Vec3(0f, 0f, 1f)).dot(a) > 1e-5f) return@register "cross not orthogonal"
            null
        }
        register("jobs.parallel-determinism") {
            val jobs = JobSystem(2)
            try {
                val out = FloatArray(4096)
                jobs.parallelFor(out.size, 32) { i -> out[i] = i.toFloat() }
                for (i in out.indices) if (out[i] != i.toFloat()) return@register "slot $i"
                jobs.checkFailures()
            } finally { jobs.shutdown() }
            null
        }
        register("ecs.create-destroy-reuse") {
            val w = EcsWorld()
            val a = w.create("a")
            if (!w.isAlive(a)) return@register "new entity not alive"
            w.destroy(a)
            if (w.isAlive(a)) return@register "destroyed entity still alive"
            val b = w.create("b")
            if (w.isAlive(a)) return@register "stale handle revived"
            w.add(b, TransformEcs())
            if (w.query(TransformEcs::class.java).size != 1) return@register "query mismatch"
            null
        }
        register("ecs.serialisation-roundtrip") {
            val w = EcsWorld()
            val e = w.create("player")
            w.add(e, TransformEcs().also { it.position.set(1f, 2f, 3f) })
            val json = w.toJson()
            val r = EcsWorld()
            r.fromJson(json)
            if (r.entityCount != 1) return@register "entity count ${r.entityCount}"
            null
        }
        register("time.fixed-step-accuracy") {
            val rt = UltimateRuntime({ com.sengine.engine.core.Scene("diag") }, JobSystem(0))
            try {
                var steps = 0
                rt.register(object : UltimateSubsystem {
                    override val id = "diag.counter"
                    override val displayName = "Diagnostic counter"
                    override fun fixedUpdate(fixedDt: Float) { steps++ }
                })
                rt.start()
                repeat(60) { rt.advance(1f / 60f) }
                if (steps < 55) return@register "only $steps fixed steps in 1s"
            } finally { rt.release() }
            null
        }
    }
}
