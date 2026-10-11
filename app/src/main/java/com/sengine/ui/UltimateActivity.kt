package com.sengine.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sengine.engine.Engine
import com.sengine.engine.jobs.JobSystem
import com.sengine.engine.ultimate.UltimateDiagnostics
import com.sengine.engine.ultimate.UltimateRuntime
import com.sengine.engine.ultimate.UltimateSubsystems

/**
 * Ultimate Edition Hub: live view of every v7 subsystem (pro physics, AI/navigation, animation
 * graph, render pipeline, assets, networking, audio DSP, VFX, cinematics, gameplay framework),
 * the frame profiler, runtime diagnostics and the feature reference.
 */
class UltimateActivity : AppCompatActivity() {

    private var tab = 0
    private lateinit var tabs: List<TextView>
    private lateinit var content: LinearLayout
    private lateinit var statusLine: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var live = true
    /** Standalone runtime used when no engine is running (diagnostics from the editor). */
    private var ownRuntime: UltimateRuntime? = null
    private var lastResults: List<UltimateDiagnostics.Result> = emptyList()

    private val refresh = object : Runnable {
        override fun run() {
            if (live) rebuild()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox().apply { setBackgroundColor(C.BG) }
        val bar = hbox().apply { setBackgroundColor(C.HEADER); setPadding(dp(6), dp(5), dp(10), dp(5)) }
        bar.addView(iconButton("back", "Back", sizeDp = 36) { finish() })
        bar.addView(label("Ultimate Hub", 16f, C.TEXT, true).apply { setPadding(dp(10), 0, dp(16), 0) })
        val tabBox = hbox().apply { background = round(C.PANEL2, dp(10).toFloat()); setPadding(dp(2), dp(2), dp(2), dp(2)) }
        tabs = listOf("grid" to "Systems", "chart" to "Profiler", "doctor" to "Diagnostics", "book" to "Reference").mapIndexed { i, (ic, t) ->
            iconTextButton(ic, t, C.PANEL2) { showTab(i) }.also { tabBox.addView(it) }
        }
        bar.addView(tabBox)
        bar.addView(View(this), lp(0, 1, 1f))
        statusLine = label("", 11f, C.DIM)
        bar.addView(statusLine)
        bar.addView(iconTextButton("refresh", if (live) "Live" else "Paused", if (live) C.ACCENT else C.PANEL2) {
            live = !live; (it as TextView).text = if (live) "Live" else "Paused"
            it.setButtonColor(if (live) C.ACCENT else C.PANEL2); rebuild()
        })
        root.addView(bar, lp(MATCH, WRAP))
        content = vbox().apply { setPadding(dp(16), dp(12), dp(16), dp(40)) }
        root.addView(ScrollView(this).apply { addView(content) }, lp(MATCH, 0, 1f))
        setContentView(root)
        showTab(0)
        handler.postDelayed(refresh, 1000)
    }

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        ownRuntime?.release()
        ownRuntime = null
        super.onDestroy()
    }

    /** The live runtime (play mode) or a standalone one built for diagnostics. */
    private fun runtime(): UltimateRuntime {
        Engine.current?.let { return it.ultimate }
        ownRuntime?.let { return it }
        val rt = UltimateRuntime({ com.sengine.engine.core.Scene("hub") }, JobSystem(0))
        UltimateSubsystems.registerAll(rt)
        rt.start()
        ownRuntime = rt
        return rt
    }

    private fun showTab(i: Int) {
        tab = i
        tabs.forEachIndexed { k, t ->
            t.setButtonColor(if (k == i) C.ACCENT else C.PANEL2)
            t.setTextColor(if (k == i) 0xFF000000.toInt() else C.TEXT)
        }
        rebuild()
    }

    private fun rebuild() {
        content.removeAllViews()
        val rt = runtime()
        statusLine.text = "${rt.count()} subsystems | ${rt.ecs.entityCount} entities | ${rt.jobs.workerCount()} job threads"
        when (tab) {
            0 -> systems(rt)
            1 -> profiler(rt)
            2 -> diagnostics(rt)
            else -> reference()
        }
    }

    private fun card(title: String, tint: Int = C.ACCENT2): LinearLayout =
        card(C.PANEL).apply {
            addView(label(title, 13f, tint, true))
            content.addView(this, lp(MATCH, WRAP).margins(0, dp(6), 0, 0))
        }

    private fun row(parent: LinearLayout, name: String, value: String, color: Int = C.TEXT) {
        val h = hbox()
        h.addView(label(name, 12f, C.DIM), lp(0, WRAP, 1f))
        h.addView(label(value, 12f, color, true))
        parent.addView(h, lp(MATCH, WRAP))
    }

    private fun systems(rt: UltimateRuntime) {
        for (category in rt.categoryNames()) {
            val box = card(category)
            for (s in rt.inCategory(category)) {
                val h = vbox().apply { setPadding(0, dp(4), 0, dp(2)) }
                val head = hbox()
                head.addView(label(s.displayName, 13f, C.TEXT, true), lp(0, WRAP, 1f))
                head.addView(label(s.id, 11f, C.DIM))
                h.addView(head)
                h.addView(label(s.status(), 11f, C.ACCENT2))
                val counters = s.counters()
                if (counters.isNotEmpty()) {
                    h.addView(label(counters.entries.joinToString("   ") { "${it.key}=${it.value}" }, 11f, C.DIM))
                }
                box.addView(h)
            }
        }
        if (rt.count() == 0) {
            val box = card("No subsystems registered")
            box.addView(label("Open a project and press Play to run the full v7 runtime.", 12f, C.DIM))
        }
    }

    private fun profiler(rt: UltimateRuntime) {
        val p = rt.profiler
        val box = card("Frame")
        row(box, "Frame time", "%.2f ms".format(p.frameMs))
        row(box, "Average", "%.2f ms".format(p.averageFrameMs()))
        row(box, "FPS", "%.1f".format(p.fps()), if (p.fps() > 50f) C.GREEN else C.YELLOW)
        row(box, "Frames", p.frameIndex.toString())
        row(box, "Fixed steps (last frame)", rt.time.fixedStepsLastFrame.toString())
        row(box, "Interpolation alpha", "%.2f".format(rt.time.interpolation))
        row(box, "Simulation time", "%.2f s".format(rt.time.time))
        if (rt.time.saturated) row(box, "Warning", "simulation saturated", C.RED)

        val samples = card("Scopes")
        val names = p.names().filter { (p.sample(it).totalCalls) > 0 }
        if (names.isEmpty()) samples.addView(label("No samples yet - press Play to profile a frame.", 12f, C.DIM))
        for (n in names.sortedByDescending { p.sample(it).averageMs }) {
            val s = p.sample(n)
            row(samples, n, "last %.2f  avg %.2f  peak %.2f ms".format(s.lastMs, s.averageMs, s.peakMs),
                if (s.averageMs > 8f) C.YELLOW else C.TEXT)
        }
        val counters = card("Counters")
        for ((k, v) in rt.counters()) row(counters, k, v)
        val actions = card("Actions")
        val h = hbox()
        h.addView(button("Reset counters", C.PANEL2) { rt.profiler.resetCounters(); rebuild() })
        h.addView(button("Dump to log", C.PANEL2) {
            println(rt.dump())
            android.widget.Toast.makeText(this, "Runtime dump written to logcat", android.widget.Toast.LENGTH_SHORT).show()
        })
        actions.addView(h)
    }

    private fun diagnostics(rt: UltimateRuntime) {
        val head = card("Runtime checks")
        head.addView(label("${UltimateDiagnostics.names.size} built-in invariants: maths, job system, ECS, fixed timestep and every registered subsystem self-test.", 12f, C.DIM))
        val buttons = hbox()
        buttons.addView(button("Run all checks", C.ACCENT, 0xFF000000.toInt()) {
            lastResults = UltimateDiagnostics.runAll()
            rebuild()
        })
        buttons.addView(button("Clear", C.PANEL2) { lastResults = emptyList(); rebuild() })
        head.addView(buttons)
        if (lastResults.isNotEmpty()) {
            val passed = lastResults.count { it.ok }
            val summary = card("Result", if (passed == lastResults.size) C.GREEN else C.RED)
            summary.addView(label("$passed / ${lastResults.size} passed", 13f, if (passed == lastResults.size) C.GREEN else C.RED, true))
            for (r in lastResults) {
                val line = label((if (r.ok) "✔ " else "✘ ") + r.name + "  " + "%.1f".format(r.ms) + "ms", 12f, if (r.ok) C.TEXT else C.RED)
                summary.addView(line)
                if (!r.ok) summary.addView(label("    " + r.detail, 11f, C.YELLOW))
            }
        }
        val info = card("Runtime")
        row(info, "Subsystems", rt.count().toString())
        row(info, "ECS entities", rt.ecs.entityCount.toString())
        row(info, "Job threads", rt.jobs.workerCount().toString())
        row(info, "Jobs executed", rt.jobs.executed.get().toString())
        row(info, "Fixed delta", "%.4f s".format(rt.time.fixedDelta))
    }

    private fun reference() {
        for ((title, body) in REFERENCE) {
            val box = card(title)
            box.addView(label(body, 12f, C.DIM))
        }
    }

    companion object {
        private val REFERENCE = listOf(
            "Physics Pro" to "Rigid bodies (sphere, box, capsule, cylinder, convex hull, mesh, plane, heightfield, compound), GJK/EPA + SAT narrowphase, warm-started sequential impulse solver, joints and motors, CCD, sleeping islands, character controller, raycast vehicle, ragdolls, destruction and PBD cloth/rope.",
            "AI & Navigation" to "Navmesh bake from level geometry, A* + funnel paths, NavMeshAgent steering, RVO crowd avoidance, behaviour trees with blackboards, perception with memory, utility AI and GOAP planning, squads and cover.",
            "Animation Pro" to "Skeletons and skinning, TRS/float clips with cubic sampling, 1D/2D blend trees, additive layers and masks, animator state machines with crossfades, two-bone/FABRIK IK, retargeting.",
            "Render Pipeline" to "Render graph, frustum + occlusion culling, LOD chains with hysteresis, clustered forward lighting, cascaded shadow maps, PBR material variants, ACES tonemapping, bloom, SSAO, FXAA, colour grading.",
            "Assets" to "GUID asset database with dependency graph and hot reload, OBJ/PNG/WAV importers, binary asset packs with CRC, ref-counted async streaming with budgets, prefab overrides, versioned scene format with migrations.",
            "Networking" to "Bit-level serialisation with quantisation, reliable/unreliable channels, delta snapshots and interest management, client prediction + reconciliation, lag compensation, RPCs, clock sync and lobby.",
            "Audio DSP + VFX" to "Mixer bus tree with ducking and snapshots, biquad EQ, compressor/limiter, reverb, delay, spatialisation with attenuation/doppler, plus a module based particle system, VFX graph, decals, trails and post volumes.",
            "Gameplay Framework" to "Retained-mode GUI with layout groups and widgets, tweening, action-map input with processors/interactions and gestures, timeline sequencer, virtual cameras, undo/redo command stack, gizmos, inspector, plugin registry, profiler and localisation.",
            "Runtime" to "One UltimateRuntime drives every subsystem: shared job pool, ECS world mirrored from the scene graph, fixed-step clock with interpolation, and a frame profiler with per-scope history."
        )
    }
}
