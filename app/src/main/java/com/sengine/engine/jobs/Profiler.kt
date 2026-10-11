package com.sengine.engine.jobs

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight frame profiler used by every Ultimate subsystem: named CPU scopes, counters,
 * per-frame aggregation and a ring buffer history for graphs.
 *
 * `Profiler` is cheap when disabled ([enabled] = false short-circuits to `0L`).
 */
class Profiler(var enabled: Boolean = true, val historyFrames: Int = 120) {

    class Sample(val name: String) {
        var lastMs = 0f
        var averageMs = 0f
        var peakMs = 0f
        var callsThisFrame = 0
        var totalCalls = 0L
        var totalMs = 0.0
        val history = FloatArray(120)
        var historyHead = 0
        var historySize = 0
        fun push(ms: Float) {
            history[historyHead] = ms
            historyHead = (historyHead + 1) % history.size
            if (historySize < history.size) historySize++
        }
    }

    private val samples = LinkedHashMap<String, Sample>()
    private val counters = ConcurrentHashMap<String, AtomicLong>()
    private val frameSamples = ArrayList<Pair<String, Long>>(32)
    private var frameStartNs = 0L
    private var frameDepth = 0

    var frameMs = 0f; private set
    var lastFrameMs = 0f; private set
    var frameIndex = 0L; private set
    private val frameHistory = FloatArray(historyFrames)
    private var frameHead = 0
    private var frameCount = 0

    fun sample(name: String): Sample = samples.getOrPut(name) { Sample(name) }
    fun names(): List<String> = samples.keys.toList()

    fun beginFrame() {
        if (!enabled) return
        frameStartNs = System.nanoTime()
        frameDepth = 0
        frameSamples.clear()
        for (s in samples.values) s.callsThisFrame = 0
    }

    fun endFrame() {
        if (!enabled) return
        lastFrameMs = ((System.nanoTime() - frameStartNs) / 1e6f)
        frameMs = lastFrameMs
        frameHistory[frameHead] = frameMs
        frameHead = (frameHead + 1) % frameHistory.size
        if (frameCount < frameHistory.size) frameCount++
        frameIndex++
        for ((name, ns) in frameSamples) {
            val s = sample(name)
            s.lastMs = ns / 1e6f
            s.callsThisFrame++
            s.totalCalls++
            s.totalMs += s.lastMs
            s.peakMs = maxOf(s.peakMs, s.lastMs)
            s.averageMs = (s.totalMs / s.totalCalls).toFloat()
            s.push(s.lastMs)
        }
    }

    /** Times [body] under [name] and returns its result. */
    inline fun <T> scope(name: String, body: () -> T): T {
        if (!enabled) return body()
        val t0 = System.nanoTime()
        try {
            return body()
        } finally {
            record(name, System.nanoTime() - t0)
        }
    }

    fun beginScope(name: String) {
        if (enabled) frameSamples.add(name to -System.nanoTime())
    }

    fun endScope(name: String) {
        if (!enabled) return
        for (i in frameSamples.indices.reversed()) {
            val (n, v) = frameSamples[i]
            if (n == name && v < 0) { frameSamples[i] = n to (-v + System.nanoTime()) / 2; return }
        }
    }

    fun record(name: String, nanos: Long) { if (enabled) frameSamples.add(name to nanos) }

    fun increment(counter: String, amount: Long = 1) {
        if (!enabled) return
        counters.getOrPut(counter) { AtomicLong() }.addAndGet(amount)
    }

    fun counter(counter: String): Long = counters[counter]?.get() ?: 0L
    fun counterNames(): List<String> = counters.keys.sorted()
    fun resetCounters() { counters.clear() }

    /** Average frame time over the buffered history. */
    fun averageFrameMs(): Float {
        if (frameCount == 0) return 0f
        var sum = 0f
        for (i in 0 until frameCount) sum += frameHistory[i]
        return sum / frameCount
    }

    fun fps(): Float { val ms = averageFrameMs(); return if (ms <= 0f) 0f else 1000f / ms }

    fun historyOf(name: String): FloatArray? = samples[name]?.history

    fun summary(): String = buildString {
        append("frame ${"%.2f".format(frameMs)}ms (${"%.1f".format(fps())} fps)")
        for (s in samples.values.sortedByDescending { it.lastMs }) {
            if (s.lastMs <= 0f && s.totalCalls == 0L) continue
            append("; ${s.name} ${"%.2f".format(s.lastMs)}ms")
        }
        for (c in counterNames()) append("; $c=${counter(c)}")
    }

    fun dump(): String = buildString {
        appendLine("Profiler frame #$frameIndex  ${"%.2f".format(frameMs)}ms  (avg ${"%.2f".format(averageFrameMs())}ms)")
        for (s in samples.values.sortedByDescending { it.averageMs }) {
            appendLine("  %-22s last=%6.2fms avg=%6.2fms peak=%6.2fms calls=${s.totalCalls}".format(s.name, s.lastMs, s.averageMs, s.peakMs))
        }
        for (c in counterNames()) appendLine("  counter $c = ${counter(c)}")
    }
}
