package com.sengine.engine.jobs

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

/**
 * Cooperative job system (worker pool) used by the Ultimate physics, animation, AI and
 * rendering subsystems to spread a frame over all CPU cores.
 *
 * Design notes:
 *  * [parallelFor] splits `[0, count)` into chunks and runs them on the pool.
 *  * A thread that waits for jobs also *runs* queued jobs, so nested parallelism never deadlocks.
 *  * Work items write only to the slot owned by their index, which keeps results deterministic.
 */
class JobSystem(val threadCount: Int = defaultThreadCount()) {

    private class Task(val name: String, val lo: Int, val hi: Int, val body: (Int) -> Unit, val once: (() -> Unit)? = null)

    private val lock = ReentrantLock()
    private val work = lock.newCondition()
    private val queue = ArrayDeque<Task>()
    private var pending = 0
    private val workers = ArrayList<Thread>()
    @Volatile private var shutdown = false
    private val failures = ArrayList<Throwable>()

    /** Total jobs executed since start (profiling / tests). */
    val executed = AtomicLong()
    /** Jobs picked up by a thread that was waiting for others (cooperative scheduling). */
    val stolen = AtomicLong()

    init {
        repeat(threadCount.coerceAtLeast(0)) { i ->
            val t = Thread({ workerLoop() }, "sengine-job-$i")
            t.isDaemon = true
            workers.add(t)
            t.start()
        }
    }

    private fun workerLoop() {
        while (!shutdown) {
            val task = take(true) ?: continue
            runTask(task)
        }
    }

    private fun take(block: Boolean): Task? {
        lock.lock()
        try {
            if (queue.isEmpty()) {
                if (!block || shutdown) return null
                work.await(20, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (queue.isEmpty()) return null
            }
            return queue.removeFirstOrNull()
        } catch (_: InterruptedException) {
            return null
        } finally {
            lock.unlock()
        }
    }

    private fun runTask(task: Task) {
        try {
            if (task.once != null) task.once.invoke()
            else {
                var i = task.lo
                while (i < task.hi) { task.body(i); i++ }
            }
        } catch (t: Throwable) {
            synchronized(failures) { failures.add(t) }
        } finally {
            executed.incrementAndGet()
            lock.lock()
            try {
                pending--
                work.signalAll()
            } finally {
                lock.unlock()
            }
        }
    }

    private fun enqueue(t: Task) {
        lock.lock()
        try {
            pending++
            queue.addLast(t)
            work.signalAll()
        } finally {
            lock.unlock()
        }
    }

    /** Blocks until every queued job finished; the calling thread helps instead of idling. */
    fun waitAll() {
        while (true) {
            lock.lock()
            try {
                if (pending == 0) return
                if (queue.isEmpty()) {
                    work.await(4, java.util.concurrent.TimeUnit.MILLISECONDS)
                    continue
                }
            } finally {
                lock.unlock()
            }
            val task = take(false) ?: continue
            stolen.incrementAndGet()
            runTask(task)
        }
    }

    /**
     * Runs `body(i)` for every `i` in `[0, count)`, split into chunks of at least [grain].
     * Blocks until all chunks are done. The body must only write to the slot owned by `i`.
     */
    fun parallelFor(count: Int, grain: Int = 64, name: String = "parallelFor", body: (Int) -> Unit) {
        if (count <= 0) return
        val chunks = (workerCount() * 4).coerceAtLeast(1)
        val chunk = if (grain <= 0) count else maxOf(grain, (count + chunks - 1) / chunks)
        if (threadCount == 0 || count <= chunk) {
            for (i in 0 until count) body(i)
            return
        }
        var lo = 0
        while (lo < count) {
            val hi = minOf(lo + chunk, count)
            enqueue(Task(name, lo, hi, body))
            lo = hi
        }
        waitAll()
    }

    /** Runs a single background job; the caller continues immediately. */
    fun submit(name: String = "job", body: () -> Unit) {
        enqueue(Task(name, 0, 1, {}, body))
    }

    /** Waits for all submitted jobs. */
    fun complete() = waitAll()

    /** Rethrows the first exception captured from a job (call after [waitAll]). */
    fun checkFailures() {
        val first: Throwable? = synchronized(failures) { failures.firstOrNull() }
        if (first != null) {
            synchronized(failures) { failures.clear() }
            throw RuntimeException("job failed", first)
        }
    }

    fun workerCount(): Int = if (threadCount == 0) 1 else threadCount

    fun shutdown() {
        shutdown = true
        lock.lock()
        try { work.signalAll() } finally { lock.unlock() }
        for (w in workers) w.join(200)
        workers.clear()
    }

    fun stats(): String = "JobSystem(threads=$threadCount, executed=${executed.get()}, cooperative=${stolen.get()})"

    companion object {
        /**
         * Process-wide worker pool. Games run one simulation, so a single pool avoids
         * oversubscribing the device (and the unit tests that create many Engine instances).
         */
        val shared: JobSystem by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { JobSystem() }

        fun defaultThreadCount(): Int {
            val cores = Runtime.getRuntime().availableProcessors()
            // Keep two cores free for the render thread + OS on mobile devices.
            return (cores - 2).coerceIn(1, 8)
        }
    }
}
