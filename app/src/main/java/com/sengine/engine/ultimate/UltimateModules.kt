package com.sengine.engine.ultimate

/**
 * Adapters that plug each Ultimate module into the [UltimateRuntime] subsystem registry.
 *
 * Every adapter is a thin, dependency-free wrapper: the module itself never needs to know about the
 * runtime, and the runtime never needs to know about the module. Add a module by registering it here.
 */
object UltimateModules {

    fun register(runtime: UltimateRuntime) {
        // Modules are appended here as they land; each registration is independent.
    }
}
