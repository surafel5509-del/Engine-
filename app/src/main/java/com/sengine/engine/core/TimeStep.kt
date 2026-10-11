package com.sengine.engine.core

/**
 * Professional fixed-timestep game clock: the render frame is decoupled from the simulation step
 * (the way Unity's FixedUpdate and Unreal's substeps work).
 *
 * * `advance(rawDelta) { fixedDt -> ... }` runs 0..maxSubSteps fixed steps and returns the scaled
 *   frame delta used for interpolation and per-frame systems.
 * * [interpolation] is the alpha in `[0,1)` used to blend two physics states for smooth rendering.
 * * [timeScale] slows down or pauses gameplay while UI keeps running.
 */
class TimeStep(
    var fixedDelta: Float = 1f / 60f,
    var maxSubSteps: Int = 8,
    var maxFrameDelta: Float = 0.25f
) {
    var timeScale = 1f
    /** Unscaled total time in seconds since [reset]. */
    var unscaledTime = 0.0; private set
    /** Scaled total time in seconds since [reset]. */
    var time = 0.0; private set
    /** Fixed-step simulation time in seconds. */
    var fixedTime = 0.0; private set
    var frame = 0L; private set
    var fixedFrame = 0L; private set
    /** Scaled delta of the last frame (clamped to [maxFrameDelta]). */
    var deltaTime = 0f; private set
    /** Unscaled delta of the last frame. */
    var unscaledDelta = 0f; private set
    /** Exponentially smoothed delta (useful for camera smoothing / UI). */
    var smoothDelta = 0f; private set
    /** Number of fixed steps executed in the last frame. */
    var fixedStepsLastFrame = 0; private set
    var interpolation = 0f; private set
    /** Set when the accumulator hit [maxSubSteps] (the simulation is behind real time). */
    var saturated = false; private set
    var maxSaturationCount = 0L; private set

    private var accumulator = 0f

    /** Smoothed frames per second. */
    fun fps(): Float = if (smoothDelta <= 0f) 0f else 1f / smoothDelta

    fun reset() {
        time = 0.0; unscaledTime = 0.0; fixedTime = 0.0
        frame = 0; fixedFrame = 0; accumulator = 0f
        deltaTime = 0f; unscaledDelta = 0f; smoothDelta = 0f
        fixedStepsLastFrame = 0; interpolation = 0f; saturated = false
    }

    /**
     * Advances the clock by [rawDelta] seconds and runs the fixed steps. Returns the scaled delta.
     */
    fun advance(rawDelta: Float, fixedStep: (Float) -> Unit = {}): Float {
        val raw = rawDelta.coerceIn(0f, maxFrameDelta)
        unscaledDelta = raw
        unscaledTime += raw
        val scaled = raw * timeScale
        deltaTime = scaled
        smoothDelta = if (smoothDelta <= 0f) scaled else smoothDelta + (scaled - smoothDelta) * 0.1f
        time += scaled
        frame++
        accumulator += scaled
        var steps = 0
        saturated = false
        while (accumulator >= fixedDelta) {
            if (steps >= maxSubSteps) { saturated = true; accumulator = 0f; maxSaturationCount++; break }
            fixedStep(fixedDelta)
            accumulator -= fixedDelta
            fixedTime += fixedDelta
            fixedFrame++
            steps++
        }
        fixedStepsLastFrame = steps
        interpolation = (accumulator / fixedDelta).coerceIn(0f, 1f)
        return scaled
    }

    fun describe(): String = "TimeStep(t=${"%.2f".format(time)}s frame=$frame fixed=$fixedFrame dt=${"%.4f".format(deltaTime)} steps=$fixedStepsLastFrame} alpha=${"%.2f".format(interpolation)} fps=${"%.1f".format(fps())})"
}
