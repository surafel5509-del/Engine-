package com.sengine.engine.ultimate

/**
 * Registration point of the Ultimate Edition: wires every v7 subsystem into an [UltimateRuntime].
 *
 * The runtime stays generic (it only knows [UltimateSubsystem]) while this file knows the concrete
 * modules, which keeps the integration in one place and lets the editor hub list everything.
 */
object UltimateSubsystems {

    /** Registers every available subsystem. Safe to call more than once. */
    fun registerAll(runtime: UltimateRuntime): UltimateRuntime {
        if (runtime.subsystem(CoreSubsystem.ID) == null) runtime.register(CoreSubsystem())
        if (runtime.subsystem(EcsSubsystem.ID) == null) runtime.register(EcsSubsystem())
        registerModules(runtime)
        return runtime
    }

    /**
     * Registers the gameplay modules. Each block is independent: if a module is not present the
     * runtime simply runs without it.
     */
    private fun registerModules(runtime: UltimateRuntime) {
        UltimateModules.register(runtime)
    }

    /** Core runtime bookkeeping: entity/job/frame counters exposed to the hub and profiler. */
    class CoreSubsystem : UltimateSubsystem {
        override val id = ID
        override val displayName = "Ultimate runtime"
        override val category = "Core"
        private var frames = 0L
        private var lastFps = 0f

        override fun fixedUpdate(fixedDt: Float) {}

        override fun update(dt: Float) {
            frames++
            if (dt > 0f) lastFps = lastFps * 0.9f + (1f / dt) * 0.1f
        }

        override fun status(): String = "%d frames, %.1f fps, fixed %.4fs".format(frames, lastFps, 1f / 60f)
        override fun counters(): Map<String, String> = mapOf("frames" to frames.toString(), "fps" to "%.1f".format(lastFps))

        companion object { const val ID = "core" }
    }

    /** Keeps the ECS world mirrored from the scene graph and reports entity counts. */
    class EcsSubsystem : UltimateSubsystem {
        override val id = ID
        override val displayName = "ECS world"
        override val category = "Core"
        private var peak = 0
        @Volatile private var runtime: UltimateRuntime? = null

        override fun initialize(rt: UltimateRuntime) {
            runtime = rt
            rt.ecsMirroring = true
        }

        override fun update(dt: Float) {
            val n = runtime?.ecs?.entityCount ?: 0
            if (n > peak) peak = n
        }

        override fun status(): String = "${runtime?.ecs?.entityCount ?: 0} entities (peak $peak)"

        override fun counters(): Map<String, String> = mapOf(
            "entities" to (runtime?.ecs?.entityCount ?: 0).toString(),
            "peak" to peak.toString(),
            "componentTypes" to (runtime?.ecs?.componentTypes()?.size ?: 0).toString()
        )

        companion object { const val ID = "ecs" }
    }
}
