package com.sengine.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sengine.engine.core.AssetKind
import com.sengine.engine.math.Mat4
import com.sengine.engine.model.ModelOps
import com.sengine.engine.model.ModelPresets
import com.sengine.engine.model.SClip
import com.sengine.engine.model.SKey
import com.sengine.engine.model.SModel
import com.sengine.engine.model.SPart
import com.sengine.project.Project
import com.sengine.project.ProjectManager
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 3D Model Editor ("Blender-lite"): build models from parts made of editable polygons.
 *  - Object mode: add primitives / presets, move-rotate-scale parts, parenting, colours.
 *  - Edit mode: vertex & face selection, extrude, inset, Catmull-Clark subdivide, mirror,
 *    smooth, merge, flip, delete, face painting.
 *  - Animate mode: clips with keyframed part transforms (auto-key), timeline, playback.
 * Saves `.smodel` (rendered natively by MeshRenderer "Custom Model" incl. animations) and exports OBJ.
 */
class ModelEditorActivity : AppCompatActivity() {
    enum class Mode { OBJECT, EDIT, ANIMATE }
    enum class Tool { SELECT, MOVE, ROTATE, SCALE }

    private lateinit var project: Project
    private var asset = "Model.smodel"
    private var model = SModel()
    private var savedJson = ""
    private var partIdx = -1
    private var mode = Mode.OBJECT
    private var tool = Tool.MOVE
    private var axisLock = -1
    private var faceSelect = true
    private val selVerts = HashSet<Int>()
    private val selFaces = HashSet<Int>()
    private var clipIdx = 0
    private var time = 0f
    private var playing = false
    private var snap = false
    private var wireframe = false
    private var xray = false

    private lateinit var view3d: ModelView
    private lateinit var outliner: LinearLayout
    private lateinit var props: LinearLayout
    private lateinit var bottom: LinearLayout
    private lateinit var modeBtns: List<TextView>
    private lateinit var toolBtns: List<ImageView>
    private lateinit var axisBtns: List<TextView>
    private lateinit var stats: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val undo = ArrayDeque<String>()
    private val redo = ArrayDeque<String>()

    companion object {
        fun newModelJson(): String = SModel(mutableListOf(SModel.primitive(0).also { it.name = "Cube"; it.color = 0xFF5B7CFF.toInt() })).toJson().toString(2)
        private val AXIS_COLORS = intArrayOf(0xFFFF5C6C.toInt(), 0xFF34D399.toInt(), 0xFF5B7CFF.toInt())
    }

    private val part: SPart? get() = model.parts.getOrNull(partIdx)
    private val clip: SClip? get() = model.clips.getOrNull(clipIdx)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = ProjectManager.open(this, intent.getStringExtra("project")!!)
        asset = intent.getStringExtra("asset") ?: project.uniqueAssetName("Model.smodel")
        model = try { project.readAsset(asset)?.let { SModel.parse(it) } ?: SModel.parse(newModelJson()) } catch (e: Exception) {
            toast("Couldn't read model: ${e.message}"); SModel.parse(newModelJson())
        }
        savedJson = model.toJson().toString()
        partIdx = if (model.parts.isNotEmpty()) 0 else -1

        val root = vbox().apply { setBackgroundColor(C.BG) }
        // ------------------------------------------------------------ top bar
        val bar = hbox().apply { setPadding(dp(6), dp(5), dp(6), dp(5)) }
        fun gap(w: Int = 6) = bar.addView(View(this), lp(dp(w), 1))
        bar.addView(iconButton("back", "Back", sizeDp = 36) { onBackPressedDispatcher.onBackPressed() }); gap()
        val tb = vbox()
        tb.addView(label(asset, 13f, C.TEXT, true).apply { maxLines = 1 })
        tb.addView(label("3D Model Editor", 10f, C.ACCENT2))
        bar.addView(tb, lp(dp(120), WRAP)); gap()
        val modeBox = hbox().apply { background = round(C.PANEL2, dp(10).toFloat()); setPadding(dp(2), dp(2), dp(2), dp(2)) }
        modeBtns = listOf("Object" to Mode.OBJECT, "Edit" to Mode.EDIT, "Animate" to Mode.ANIMATE).map { (t, m) ->
            button(t, C.PANEL2) { setMode(m) }.apply { textSize = 12f }.also { modeBox.addView(it) }
        }
        bar.addView(modeBox); gap(10)
        toolBtns = listOf(Triple("cursor", "Select / orbit", Tool.SELECT), Triple("move", "Move", Tool.MOVE), Triple("rotate", "Rotate", Tool.ROTATE), Triple("scale", "Scale", Tool.SCALE)).map { (ic, d, t) ->
            iconButton(ic, d, sizeDp = 36) { tool = t; refreshToolbar() }.also { bar.addView(it); gap(3) }
        }
        gap(6)
        axisBtns = listOf("All", "X", "Y", "Z").mapIndexed { i, t ->
            button(t, C.PANEL2) { axisLock = i - 1; refreshToolbar() }.apply { textSize = 12f; minWidth = dp(34) }.also { bar.addView(it); gap(2) }
        }
        gap(8)
        bar.addView(iconButton("magnet", "Snap to grid (0.1)", sizeDp = 36) { v -> snap = !snap; v.alpha = if (snap) 1f else 0.45f }.also { it.alpha = 0.45f }); gap(3)
        bar.addView(iconButton("undo", "Undo", sizeDp = 36) { undo() }); gap(3)
        bar.addView(iconButton("redo", "Redo", sizeDp = 36) { redo() }); gap(3)
        bar.addView(iconButton("eye", "View", sizeDp = 36) { viewMenu(it) }); gap(3)
        bar.addView(iconButton("more", "More", sizeDp = 36) { moreMenu(it) }); gap(3)
        bar.addView(iconTextButton("save", "Save", C.ACCENT, 0xFFFFFFFF.toInt()) { save() })
        root.addView(HorizontalScrollView(this).apply { addView(bar); isHorizontalScrollBarEnabled = false; setBackgroundColor(C.HEADER) }, lp(MATCH, WRAP))

        // ------------------------------------------------------------ body
        val body = hbox().apply { gravity = Gravity.TOP }
        val left = vbox().apply { setBackgroundColor(C.PANEL) }
        val oh = hbox().apply { setPadding(dp(8), dp(4), dp(4), 0) }
        oh.addView(sectionHeader("layers", "Parts"), lp(0, WRAP, 1f))
        oh.addView(iconButton("plus", "Add part", C.ACCENT2, sizeDp = 30) { addPartMenu(it) })
        left.addView(oh)
        outliner = vbox().apply { setPadding(dp(4), 0, dp(4), 0) }
        props = vbox().apply { setPadding(dp(8), dp(4), dp(8), dp(10)) }
        val leftInner = vbox(); leftInner.addView(outliner); leftInner.addView(props)
        left.addView(ScrollView(this).apply { addView(leftInner) }, lp(MATCH, 0, 1f))
        body.addView(left, lp(dp(210), MATCH))
        val center = vbox()
        view3d = ModelView(this)
        center.addView(view3d, lp(MATCH, 0, 1f))
        stats = label("", 10f, C.DIM).apply { setPadding(dp(8), dp(2), dp(8), dp(2)); setBackgroundColor(C.HEADER) }
        center.addView(stats, lp(MATCH, WRAP))
        bottom = hbox().apply { setPadding(dp(6), dp(4), dp(6), dp(4)); setBackgroundColor(C.PANEL) }
        center.addView(HorizontalScrollView(this).apply { addView(this@ModelEditorActivity.bottom); isHorizontalScrollBarEnabled = false; setBackgroundColor(C.PANEL) }, lp(MATCH, WRAP))
        body.addView(center, lp(0, MATCH, 1f))
        root.addView(body, lp(MATCH, 0, 1f))
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (model.toJson().toString() == savedJson) { finish(); return }
                MaterialAlertDialogBuilder(this@ModelEditorActivity).setTitle("Save changes to $asset?")
                    .setPositiveButton("Save") { _, _ -> save(); finish() }
                    .setNegativeButton("Discard") { _, _ -> finish() }
                    .setNeutralButton("Cancel", null).show()
            }
        })
        setMode(Mode.OBJECT)
        view3d.frameAll()
    }

    // ================================================================ state helpers
    fun pushUndo() { undo.addLast(model.toJson().toString()); if (undo.size > 60) undo.removeFirst(); redo.clear() }
    private fun undo() { val s = undo.removeLastOrNull() ?: return; redo.addLast(model.toJson().toString()); model = SModel.parse(s); afterReplace() }
    private fun redo() { val s = redo.removeLastOrNull() ?: return; undo.addLast(model.toJson().toString()); model = SModel.parse(s); afterReplace() }
    private fun afterReplace() {
        partIdx = partIdx.coerceAtMost(model.parts.size - 1)
        clipIdx = clipIdx.coerceIn(0, max(0, model.clips.size - 1))
        selVerts.clear(); selFaces.clear()
        refreshAll()
    }

    private fun refreshAll() { refreshOutliner(); refreshProps(); refreshBottom(); refreshToolbar(); view3d.invalidate() }

    private fun setMode(m: Mode) {
        if (m == Mode.EDIT && part == null) { toast("Select a part to edit"); return }
        mode = m; playing = false
        selVerts.clear(); selFaces.clear()
        if (m == Mode.ANIMATE && model.clips.isEmpty()) { model.clips += SClip("Idle", 1f, true) }
        refreshAll()
    }

    private fun refreshToolbar() {
        modeBtns.forEachIndexed { i, b -> val on = mode.ordinal == i; b.setButtonColor(if (on) C.ACCENT else C.PANEL2); b.setTextColor(if (on) 0xFFFFFFFF.toInt() else C.TEXT) }
        toolBtns.forEachIndexed { i, b -> b.setBg(if (tool.ordinal == i) C.SEL else C.PANEL2) }
        axisBtns.forEachIndexed { i, b ->
            val on = axisLock == i - 1
            b.setButtonColor(if (on) (if (i == 0) C.SEL else AXIS_COLORS[i - 1]) else C.PANEL2)
            b.setTextColor(if (on) 0xFFFFFFFF.toInt() else if (i == 0) C.TEXT else AXIS_COLORS[i - 1])
        }
        updateStats()
    }

    private fun updateStats() {
        val p = part
        val base = "Parts ${model.parts.size} • Verts ${model.parts.sumOf { it.verts.size }} • Faces ${model.parts.sumOf { it.faces.size }} • Tris ${ModelOps.triCount(model)}"
        val sel = when (mode) {
            Mode.EDIT -> " • Selected: ${if (faceSelect) "${selFaces.size} faces" else "${selVerts.size} verts"}"
            Mode.ANIMATE -> " • ${clip?.name ?: "-"} ${fmt(time)}s / ${fmt(clip?.length ?: 0f)}s"
            else -> if (p != null) " • ${p.name}" else ""
        }
        stats.text = base + sel + "   |   1 finger: tool • 2 fingers: orbit + pinch zoom • 3 fingers: pan • double-tap: focus"
    }

    // ================================================================ outliner / properties
    private fun refreshOutliner() {
        outliner.removeAllViews()
        fun depth(i: Int): Int { var d = 0; var p = model.parts[i].parent; while (p in model.parts.indices && d < 8) { d++; p = model.parts[p].parent }; return d }
        model.parts.forEachIndexed { i, p ->
            val row = hbox().apply {
                background = round(if (i == partIdx) C.SEL else C.PANEL, dp(8).toFloat())
                setPadding(dp(6 + depth(i) * 12), dp(4), dp(2), dp(4))
                setOnClickListener { selectPart(i) }
            }
            row.addView(View(this).apply { background = round(p.color, dp(4).toFloat()) }, lp(dp(12), dp(12)).margins(0, 0, dp(6), 0))
            row.addView(label(p.name, 12f, if (p.visible) C.TEXT else C.DIM).apply { maxLines = 1 }, lp(0, WRAP, 1f))
            row.addView(iconButton(if (p.visible) "eye" else "eye_off", "Show/Hide", if (p.visible) C.DIM else C.RED, C.PANEL, 26) {
                p.visible = !p.visible; refreshOutliner(); view3d.invalidate()
            })
            outliner.addView(row, lp(MATCH, WRAP).margins(0, dp(1), 0, dp(1)))
        }
        if (model.parts.isEmpty()) outliner.addView(label("No parts — tap + to add a primitive or a preset model.", 11f, C.DIM).apply { setPadding(dp(8), dp(8), dp(8), dp(8)) })
    }

    private fun selectPart(i: Int) {
        if (mode == Mode.EDIT && i != partIdx) { selVerts.clear(); selFaces.clear() }
        partIdx = i; refreshOutliner(); refreshProps(); refreshBottom(); view3d.invalidate(); updateStats()
    }

    private fun refreshProps() {
        props.removeAllViews()
        val p = part ?: return
        props.addView(sectionHeader("sliders", "Properties"))
        val nameF = field(p.name)
        nameF.setOnEditorActionListener { _, _, _ -> renamePart(p, nameF.text.toString()); true }
        nameF.setOnFocusChangeListener { _, has -> if (!has) renamePart(p, nameF.text.toString()) }
        props.addView(nameF, lp(MATCH, WRAP))
        val cr = hbox().apply { setPadding(0, dp(6), 0, 0) }
        cr.addView(label("Colour", 12f, C.DIM), lp(0, WRAP, 1f))
        cr.addView(View(this).apply {
            background = round(p.color, dp(6).toFloat(), dp(1), C.BORDER)
            setOnClickListener { ColorPickerDialog.show(this@ModelEditorActivity, p.color) { c -> pushUndo(); p.color = c; refreshOutliner(); refreshProps(); view3d.invalidate() } }
        }, lp(dp(48), dp(26)))
        props.addView(cr)
        props.addView(CheckBox(this).apply {
            text = "Smooth shading"; setTextColor(C.TEXT); textSize = 12f; isChecked = p.smooth
            setOnCheckedChangeListener { _, b -> pushUndo(); p.smooth = b; view3d.invalidate() }
        })
        val parentName = model.parts.getOrNull(p.parent)?.name ?: "None"
        props.addView(button("Parent: $parentName") { chooseParent() }.apply { textSize = 12f }, lp(MATCH, WRAP))
        props.addView(label(if (mode == Mode.ANIMATE) "Transform (pose at ${fmt(time)}s)" else "Transform", 11f, C.DIM).apply { setPadding(0, dp(8), 0, dp(2)) })
        val pos = FloatArray(3); val rot = FloatArray(3); val sc = FloatArray(3)
        currentTrs(partIdx, pos, rot, sc)
        fun trsRow(title: String, arr: FloatArray, kind: Int) {
            val r = hbox()
            r.addView(label(title, 11f, C.DIM), lp(dp(26), WRAP))
            for (k in 0 until 3) {
                val f = field(fmt(arr[k]), numeric = true).apply { textSize = 11f; setPadding(dp(3), dp(2), dp(3), dp(2)); setTextColor(AXIS_COLORS[k]) }
                f.setOnEditorActionListener { _, _, _ ->
                    val v = f.text.toString().toFloatOrNull()
                    if (v != null) { pushUndo(); editTrs(partIdx) { po, ro, s -> when (kind) { 0 -> po[k] = v; 1 -> ro[k] = v; else -> s[k] = v } }; view3d.invalidate() }
                    false
                }
                r.addView(f, lp(0, WRAP, 1f).margins(dp(1), 0, dp(1), 0))
            }
            props.addView(r, lp(MATCH, WRAP).margins(0, dp(1), 0, dp(1)))
        }
        trsRow("Pos", pos, 0); trsRow("Rot", rot, 1); trsRow("Scl", sc, 2)
        props.addView(label("${p.verts.size} verts • ${p.faces.size} faces", 10f, C.DIM).apply { setPadding(0, dp(6), 0, 0) })
    }

    private fun renamePart(p: SPart, n0: String) {
        val n = n0.trim()
        if (n.isEmpty() || n == p.name) return
        if (model.parts.any { it !== p && it.name == n }) { toast("Name already used"); return }
        pushUndo()
        for (c in model.clips) c.tracks.filter { it.part == p.name }.forEach { it.part = n }
        p.name = n; refreshOutliner()
    }

    private fun chooseParent() {
        val p = part ?: return
        val options = listOf("None") + model.parts.filterIndexed { i, _ -> i != partIdx && !isDescendant(i, partIdx) }.map { it.name }
        MaterialAlertDialogBuilder(this).setTitle("Parent of ${p.name}").setItems(options.toTypedArray()) { _, w ->
            pushUndo()
            p.parent = if (w == 0) -1 else model.parts.indexOfFirst { it.name == options[w] }
            refreshOutliner(); refreshProps(); view3d.invalidate()
        }.show()
    }

    private fun isDescendant(i: Int, of: Int): Boolean { var p = model.parts[i].parent; var d = 0; while (p >= 0 && d < 32) { if (p == of) return true; p = model.parts.getOrNull(p)?.parent ?: -1; d++ }; return false }

    // ================================================================ TRS (rest pose or animated pose)
    private fun currentTrs(i: Int, pos: FloatArray, rot: FloatArray, sc: FloatArray) {
        if (i !in model.parts.indices) return
        if (mode == Mode.ANIMATE) model.pose(i, clip, time, pos, rot, sc)
        else { val p = model.parts[i]; p.pos.copyInto(pos); p.rot.copyInto(rot); p.scale.copyInto(sc) }
    }

    /** Edits the rest transform, or in Animate mode writes a keyframe at the current time (auto-key). */
    private fun editTrs(i: Int, f: (FloatArray, FloatArray, FloatArray) -> Unit) {
        if (i !in model.parts.indices) return
        if (mode == Mode.ANIMATE) {
            val c = clip ?: return
            val pos = FloatArray(3); val rot = FloatArray(3); val sc = FloatArray(3)
            model.pose(i, c, time, pos, rot, sc)
            f(pos, rot, sc)
            setKey(c, model.parts[i].name, time, pos, rot, sc)
        } else { val p = model.parts[i]; f(p.pos, p.rot, p.scale) }
    }

    private fun setKey(c: SClip, partName: String, t: Float, pos: FloatArray, rot: FloatArray, sc: FloatArray) {
        val tr = c.track(partName)
        val existing = tr.keys.firstOrNull { abs(it.t - t) < 0.02f }
        if (existing != null) { pos.copyInto(existing.pos); rot.copyInto(existing.rot); sc.copyInto(existing.scale) }
        else { tr.keys += SKey(t, pos.copyOf(), rot.copyOf(), sc.copyOf()); tr.keys.sortBy { it.t } }
    }

    // ================================================================ bottom tool strip
    private fun refreshBottom() {
        bottom.removeAllViews()
        fun tb(icon: String, text: String, color: Int = C.PANEL2, f: () -> Unit) =
            bottom.addView(iconTextButton(icon, text, color) { f() }, lp(WRAP, WRAP).margins(dp(2), 0, dp(2), 0))
        when (mode) {
            Mode.OBJECT -> {
                tb("plus", "Add") { addPartMenu(bottom) }
                tb("copy", "Duplicate") { duplicatePart(false) }
                tb("mirror", "Mirror copy X") { duplicatePart(true) }
                tb("trash", "Delete", C.PANEL2) { deletePart() }
                tb("scale", "Apply scale") { part?.let { pushUndo(); ModelOps.applyScale(it); refreshProps(); view3d.invalidate() } }
                tb("target", "Origin to centre") { originToCenter() }
                tb("palette", "Colour") { part?.let { p -> ColorPickerDialog.show(this, p.color) { c -> pushUndo(); p.color = c; refreshAll() } } }
                tb("frame", "Focus") { view3d.frameSelected() }
            }
            Mode.EDIT -> {
                val sm = button(if (faceSelect) "Faces" else "Vertices", C.SEL) { faceSelect = !faceSelect; selVerts.clear(); selFaces.clear(); refreshBottom(); updateStats(); view3d.invalidate() }
                sm.setCompoundDrawables(Icons.drawable(this, if (faceSelect) "face" else "vertex", C.TEXT, 18), null, null, null); sm.compoundDrawablePadding = dp(6); sm.textSize = 13f
                bottom.addView(sm, lp(WRAP, WRAP).margins(dp(2), 0, dp(2), 0))
                tb("check", "All") { selectAll(true) }
                tb("close", "None") { selectAll(false) }
                tb("extrude", "Extrude", C.ACCENT) { extrude() }
                tb("frame", "Inset") { withFaces { p, f -> ModelOps.inset(p, f, 0.25f) } }
                tb("subdivide", "Subdivide") { editOp { ModelOps.subdivide(it, false) } }
                tb("sphere", "Smooth subdiv") { editOp { ModelOps.subdivide(it, true) } }
                tb("wave", "Smooth verts") { editOp { p -> ModelOps.smooth(p, selectedVerts(p), 0.5f, 2) } }
                tb("mirror", "Mirror X") { editOp { ModelOps.mirror(it, 0) } }
                tb("magnet", "Merge") { editOp { p -> toast("Merged ${ModelOps.mergeByDistance(p, 0.01f)} vertices") } }
                tb("refresh", "Flip normals") { withFaces { p, f -> ModelOps.flip(p, f); f } }
                tb("brush", "Paint faces") { paintFaces() }
                tb("trash", "Delete") { deleteSelection() }
                tb("rocket", "Randomize") { editOp { p -> val v = selectedVerts(p).ifEmpty { p.verts.indices.toSet() }; for (i in v) for (k in 0 until 3) p.verts[i][k] += (Math.random().toFloat() - 0.5f) * 0.06f } }
            }
            Mode.ANIMATE -> buildTimeline()
        }
    }

    private fun buildTimeline() {
        val c = clip
        bottom.addView(button(c?.name ?: "No clip", C.SEL) { clipMenu(it) }.apply { textSize = 12f }, lp(WRAP, WRAP).margins(dp(2), 0, dp(4), 0))
        val play = iconButton(if (playing) "pause" else "play", "Play / pause", C.GREEN, sizeDp = 36) { playing = !playing; if (playing) startPlay(); refreshBottom() }
        bottom.addView(play)
        bottom.addView(iconButton("back", "Previous key", sizeDp = 36) { jumpKey(-1) }, lp(dp(36), dp(36)).margins(dp(3), 0, 0, 0))
        bottom.addView(iconButton("forward", "Next key", sizeDp = 36) { jumpKey(1) }, lp(dp(36), dp(36)).margins(dp(3), 0, dp(3), 0))
        val tl = Timeline(this)
        bottom.addView(tl, lp(dp(360), dp(40)))
        bottom.addView(iconTextButton("keyframe", "Key", C.YELLOW, 0xFF1A1D2B.toInt()) { insertKey() }, lp(WRAP, WRAP).margins(dp(4), 0, dp(2), 0))
        bottom.addView(iconTextButton("trash", "Del key") { deleteKey() }, lp(WRAP, WRAP).margins(dp(2), 0, dp(2), 0))
        bottom.addView(iconTextButton("clock", "Length ${fmt(c?.length ?: 1f)}s") { editClipLength() }, lp(WRAP, WRAP).margins(dp(2), 0, dp(2), 0))
        bottom.addView(CheckBox(this).apply { text = "Loop"; setTextColor(C.TEXT); isChecked = c?.loop ?: true; setOnCheckedChangeListener { _, b -> c?.loop = b } })
    }

    private val playTick = object : Runnable {
        var last = 0L
        override fun run() {
            if (!playing || mode != Mode.ANIMATE) return
            val now = SystemClock.uptimeMillis()
            val dt = if (last == 0L) 0f else (now - last) / 1000f
            last = now
            val c = clip ?: return
            time += dt
            if (time > c.length) time = if (c.loop) time % max(0.01f, c.length) else { playing = false; refreshBottom(); c.length }
            view3d.invalidate(); timelineRef?.invalidate(); updateStats()
            handler.postDelayed(this, 16)
        }
    }
    private var timelineRef: Timeline? = null
    private fun startPlay() { playTick.last = 0L; handler.removeCallbacks(playTick); handler.post(playTick) }

    private fun clipMenu(anchor: View) {
        val pm = android.widget.PopupMenu(this, anchor)
        model.clips.forEachIndexed { i, c -> pm.menu.add(0, i, i, (if (i == clipIdx) "● " else "") + c.name) }
        pm.menu.add(0, 1000, 1000, "+ New clip"); pm.menu.add(0, 1001, 1001, "Rename clip"); pm.menu.add(0, 1002, 1002, "Duplicate clip"); pm.menu.add(0, 1003, 1003, "Delete clip")
        pm.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1000 -> askText("New clip", "Run") { n -> pushUndo(); model.clips += SClip(n, 1f, true); clipIdx = model.clips.size - 1; time = 0f; refreshBottom() }
                1001 -> clip?.let { c -> askText("Rename clip", c.name) { n -> pushUndo(); c.name = n; refreshBottom() } }
                1002 -> clip?.let { c -> pushUndo(); val j = SClip.fromJson(c.toJson()); j.name = c.name + "2"; model.clips += j; clipIdx = model.clips.size - 1; refreshBottom() }
                1003 -> if (model.clips.size > 0) { pushUndo(); model.clips.removeAt(clipIdx); clipIdx = 0; if (model.clips.isEmpty()) model.clips += SClip("Idle", 1f, true); refreshBottom(); view3d.invalidate() }
                else -> { clipIdx = item.itemId; time = 0f; refreshBottom(); view3d.invalidate() }
            }
            true
        }
        pm.show()
    }

    private fun editClipLength() {
        val c = clip ?: return
        askText("Clip length (seconds)", fmt(c.length), numeric = true) { s -> s.toFloatOrNull()?.let { pushUndo(); c.length = it.coerceIn(0.1f, 60f); refreshBottom() } }
    }

    private fun insertKey() {
        val c = clip ?: return
        val targets = if (partIdx >= 0) listOf(partIdx) else model.parts.indices.toList()
        pushUndo()
        for (i in targets) { val pos = FloatArray(3); val rot = FloatArray(3); val sc = FloatArray(3); model.pose(i, c, time, pos, rot, sc); setKey(c, model.parts[i].name, time, pos, rot, sc) }
        toast("Key at ${fmt(time)}s"); timelineRef?.invalidate()
    }

    private fun deleteKey() {
        val c = clip ?: return; val p = part ?: return
        val tr = c.tracks.firstOrNull { it.part == p.name } ?: return
        val k = tr.keys.minByOrNull { abs(it.t - time) } ?: return
        if (abs(k.t - time) > 0.05f) { toast("No key at the playhead"); return }
        pushUndo(); tr.keys.remove(k); timelineRef?.invalidate(); view3d.invalidate()
    }

    private fun jumpKey(dir: Int) {
        val c = clip ?: return
        val times = c.tracks.filter { part == null || it.part == part!!.name }.flatMap { t -> t.keys.map { it.t } }.distinct().sorted()
        val next = if (dir > 0) times.firstOrNull { it > time + 0.005f } else times.lastOrNull { it < time - 0.005f }
        if (next != null) { time = next; refreshProps(); view3d.invalidate(); timelineRef?.invalidate(); updateStats() }
    }

    @SuppressLint("ViewConstructor")
    inner class Timeline(ctx: Context) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(9).toFloat(); color = C.DIM }
        init { timelineRef = this; background = round(C.FIELD, dp(8).toFloat()) }
        override fun onDraw(c: Canvas) {
            val cl = clip ?: return
            val w = width.toFloat(); val h = height.toFloat(); val pad = dp(8).toFloat()
            fun x(t: Float) = pad + (w - 2 * pad) * (t / max(0.01f, cl.length))
            paint.color = C.BORDER
            val steps = 10
            for (i in 0..steps) { val xx = pad + (w - 2 * pad) * i / steps; c.drawRect(xx, h - dp(8), xx + 1, h, paint) }
            c.drawText("0", pad, dp(10).toFloat(), tp); c.drawText("${fmt(cl.length)}s", w - pad - dp(18), dp(10).toFloat(), tp)
            for (tr in cl.tracks) {
                val sel = part?.name == tr.part
                paint.color = if (sel) C.YELLOW else 0x88FBBF24.toInt()
                for (k in tr.keys) {
                    val xx = x(k.t); val yy = h / 2
                    val r = if (sel) dp(5).toFloat() else dp(3).toFloat()
                    val path = Path(); path.moveTo(xx, yy - r); path.lineTo(xx + r, yy); path.lineTo(xx, yy + r); path.lineTo(xx - r, yy); path.close()
                    c.drawPath(path, paint)
                }
            }
            paint.color = C.ACCENT2; val px = x(time); c.drawRect(px - 1, 0f, px + 1.5f, h, paint)
        }
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            val cl = clip ?: return true
            val pad = dp(8).toFloat()
            time = ((e.x - pad) / (width - 2 * pad) * cl.length).coerceIn(0f, cl.length)
            if (e.actionMasked == MotionEvent.ACTION_UP) refreshProps()
            parent.requestDisallowInterceptTouchEvent(true)
            invalidate(); view3d.invalidate(); updateStats()
            return true
        }
    }

    // ================================================================ object operations
    private fun addPartMenu(anchor: View) {
        val pm = android.widget.PopupMenu(this, anchor)
        SModel.PRIMITIVES.forEachIndexed { i, n -> pm.menu.add(0, i, i, n) }
        pm.menu.add(0, 100, 100, "Preset model…")
        pm.menu.add(0, 101, 101, "Import OBJ from assets…")
        pm.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                100 -> MaterialAlertDialogBuilder(this).setTitle("Preset models").setItems(ModelPresets.NAMES.toTypedArray()) { _, w -> addPreset(w) }.show()
                101 -> importObj()
                else -> {
                    pushUndo()
                    val p = SModel.primitive(item.itemId, 16)
                    p.name = uniquePartName(p.name)
                    p.color = intArrayOf(0xFF5B7CFF.toInt(), 0xFF22D3EE.toInt(), 0xFF34D399.toInt(), 0xFFFBBF24.toInt(), 0xFFFF5C6C.toInt(), 0xFFA78BFA.toInt())[model.parts.size % 6]
                    p.pos[1] = 0.5f
                    model.parts += p; partIdx = model.parts.size - 1
                    if (mode == Mode.EDIT) { selVerts.clear(); selFaces.clear() }
                    refreshAll()
                }
            }
            true
        }
        pm.show()
    }

    private fun addPreset(w: Int) {
        val merge = model.parts.isNotEmpty()
        val preset = ModelPresets.build(w)
        pushUndo()
        if (!merge || (model.parts.size == 1 && model.parts[0].name == "Cube" && model.clips.isEmpty())) {
            model = preset
        } else {
            val base = model.parts.size
            for (p in preset.parts) { if (p.parent >= 0) p.parent += base; val old = p.name; p.name = uniquePartName(old)
                for (c in preset.clips) c.tracks.filter { it.part == old }.forEach { it.part = p.name } }
            model.parts += preset.parts; model.clips += preset.clips
        }
        partIdx = 0; clipIdx = 0; refreshAll(); view3d.frameAll()
        toast("Added ${ModelPresets.NAMES[w]}")
    }

    private fun importObj() {
        val objs = project.listAssets(AssetKind.MODEL).filter { it.endsWith(".obj") }
        if (objs.isEmpty()) { toast("No .obj files in Assets — import one from the editor's asset panel"); return }
        MaterialAlertDialogBuilder(this).setTitle("Import OBJ as part").setItems(objs.toTypedArray()) { _, w ->
            try {
                val p = ModelOps.fromObj(project.readAsset(objs[w]) ?: "", uniquePartName(objs[w].substringBeforeLast('.')))
                if (p.faces.isEmpty()) { toast("No faces found"); return@setItems }
                pushUndo(); model.parts += p; partIdx = model.parts.size - 1; refreshAll(); view3d.frameAll()
                toast("Imported ${p.verts.size} verts, ${p.faces.size} faces")
            } catch (e: Exception) { toast("Import failed: ${e.message}") }
        }.show()
    }

    private fun uniquePartName(base: String): String {
        if (model.parts.none { it.name == base }) return base
        var i = 2; while (model.parts.any { it.name == "$base$i" }) i++; return "$base$i"
    }

    private fun duplicatePart(mirror: Boolean) {
        val p = part ?: return
        pushUndo()
        val c = p.copy(); c.name = uniquePartName(p.name + if (mirror) "_M" else "")
        if (mirror) {
            c.pos[0] = -c.pos[0]; c.rot[1] = -c.rot[1]; c.rot[2] = -c.rot[2]
            for (v in c.verts) v[0] = -v[0]
            for (i in c.faces.indices) c.faces[i] = c.faces[i].reversedArray()
            // mirror the animation tracks too
            for (cl in model.clips) cl.tracks.firstOrNull { it.part == p.name }?.let { tr ->
                val nt = cl.track(c.name)
                nt.keys += tr.keys.map { k -> SKey(k.t, floatArrayOf(-k.pos[0], k.pos[1], k.pos[2]), floatArrayOf(k.rot[0], -k.rot[1], -k.rot[2]), k.scale.copyOf()) }
            }
        } else c.pos[0] += 0.5f
        model.parts += c; partIdx = model.parts.size - 1; refreshAll()
    }

    private fun deletePart() {
        val i = partIdx; if (i !in model.parts.indices) return
        pushUndo()
        val name = model.parts[i].name
        model.parts.removeAt(i)
        for (p in model.parts) { if (p.parent == i) p.parent = -1 else if (p.parent > i) p.parent-- }
        for (c in model.clips) c.tracks.removeAll { it.part == name }
        partIdx = min(i, model.parts.size - 1)
        refreshAll()
    }

    private fun originToCenter() {
        val p = part ?: return
        if (p.verts.isEmpty()) return
        pushUndo()
        val c = ModelOps.centroid(p, p.verts.indices.toSet())
        for (v in p.verts) for (k in 0 until 3) v[k] -= c[k]
        val m = FloatArray(16); Mat4.trs(m, 0f, 0f, 0f, p.rot[0], p.rot[1], p.rot[2], p.scale[0], p.scale[1], p.scale[2])
        val d = Mat4.dir(m, c[0], c[1], c[2])
        for (k in 0 until 3) p.pos[k] += d[k]
        refreshProps(); view3d.invalidate()
    }

    // ================================================================ edit operations
    private fun selectedVerts(p: SPart): Set<Int> = if (faceSelect) ModelOps.vertsOf(p, selFaces) else selVerts.filter { it in p.verts.indices }.toSet()

    private fun selectAll(on: Boolean) {
        val p = part ?: return
        selVerts.clear(); selFaces.clear()
        if (on) { if (faceSelect) selFaces.addAll(p.faces.indices) else selVerts.addAll(p.verts.indices) }
        view3d.invalidate(); updateStats()
    }

    private fun editOp(f: (SPart) -> Unit) {
        val p = part ?: return
        pushUndo(); f(p); p.fixColors()
        selVerts.retainAll(p.verts.indices.toSet()); selFaces.retainAll(p.faces.indices.toSet())
        refreshProps(); view3d.invalidate(); updateStats()
    }

    private fun withFaces(f: (SPart, Set<Int>) -> Set<Int>) {
        val p = part ?: return
        if (!faceSelect || selFaces.isEmpty()) { toast("Select faces first (Faces mode)"); return }
        pushUndo(); val r = f(p, selFaces.toSet()); selFaces.clear(); selFaces.addAll(r); p.fixColors()
        refreshProps(); view3d.invalidate(); updateStats()
    }

    private fun extrude() {
        withFaces { p, f -> ModelOps.extrude(p, f, 0.25f) }
        tool = Tool.MOVE; refreshToolbar()
        toast("Extruded — drag with Move to adjust")
    }

    private fun deleteSelection() {
        val p = part ?: return
        if (faceSelect && selFaces.isNotEmpty()) editOp { ModelOps.deleteFaces(it, selFaces.toSet()) }
        else if (!faceSelect && selVerts.isNotEmpty()) editOp { ModelOps.deleteVerts(it, selVerts.toSet()) }
        else { toast("Nothing selected"); return }
        selVerts.clear(); selFaces.clear(); view3d.invalidate()
        if (p.faces.isEmpty()) toast("Part is empty")
    }

    private fun paintFaces() {
        val p = part ?: return
        if (selFaces.isEmpty()) { toast("Select faces to paint"); return }
        ColorPickerDialog.show(this, p.faceColors.getOrElse(selFaces.first()) { 0 }.let { if (it == 0) p.color else it }) { c ->
            pushUndo(); p.fixColors(); for (f in selFaces) if (f in p.faceColors.indices) p.faceColors[f] = c; view3d.invalidate()
        }
    }

    // ================================================================ menus / io
    private fun viewMenu(anchor: View) {
        val pm = android.widget.PopupMenu(this, anchor)
        listOf("Front", "Back", "Right", "Left", "Top", "Perspective", "Frame all", "Frame selected", (if (wireframe) "✓ " else "") + "Wireframe", (if (xray) "✓ " else "") + "X-ray (see-through edit)").forEach { pm.menu.add(it) }
        pm.setOnMenuItemClickListener { item ->
            val t = item.title.toString().removePrefix("✓ ")
            when (t) {
                "Front" -> view3d.setView(0f, 0f); "Back" -> view3d.setView(180f, 0f); "Right" -> view3d.setView(90f, 0f)
                "Left" -> view3d.setView(-90f, 0f); "Top" -> view3d.setView(0f, 89f); "Perspective" -> view3d.setView(35f, 25f)
                "Frame all" -> view3d.frameAll(); "Frame selected" -> view3d.frameSelected()
                "Wireframe" -> { wireframe = !wireframe; view3d.invalidate() }
                else -> { xray = !xray; view3d.invalidate() }
            }
            true
        }
        pm.show()
    }

    private fun moreMenu(anchor: View) {
        val pm = android.widget.PopupMenu(this, anchor)
        listOf("Export OBJ to Assets", "Save as…", "Center model on ground", "Scale whole model…", "Clear model", "Help").forEach { pm.menu.add(it) }
        pm.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "Export OBJ to Assets" -> {
                    val n = project.uniqueAssetName(asset.substringBeforeLast('.') + ".obj")
                    try { project.writeAsset(n, model.toObj()); toast("Exported $n") } catch (e: Exception) { toast("Export failed: ${e.message}") }
                }
                "Save as…" -> askText("Save as", asset.substringBeforeLast('.') + "_copy") { n ->
                    asset = project.uniqueAssetName(n.replace(Regex("[^A-Za-z0-9_\\-]"), "") + ".smodel"); save()
                }
                "Center model on ground" -> {
                    pushUndo()
                    val (mn, mx) = model.bounds()
                    val dx = -(mn[0] + mx[0]) / 2; val dy = -mn[1]; val dz = -(mn[2] + mx[2]) / 2
                    for (p in model.parts) if (p.parent < 0) { p.pos[0] += dx; p.pos[1] += dy; p.pos[2] += dz }
                    for (c in model.clips) for (tr in c.tracks) { val p = model.parts.firstOrNull { it.name == tr.part } ?: continue
                        if (p.parent < 0) for (k in tr.keys) { k.pos[0] += dx; k.pos[1] += dy; k.pos[2] += dz } }
                    refreshProps(); view3d.frameAll()
                }
                "Scale whole model…" -> askText("Scale factor", "2", numeric = true) { s ->
                    val f = s.toFloatOrNull() ?: return@askText
                    pushUndo()
                    for (p in model.parts) { for (v in p.verts) for (k in 0 until 3) v[k] *= f; if (p.parent >= 0) for (k in 0 until 3) p.pos[k] *= f else for (k in 0 until 3) p.pos[k] *= f }
                    for (c in model.clips) for (tr in c.tracks) for (k in tr.keys) for (a in 0 until 3) k.pos[a] *= f
                    refreshProps(); view3d.frameAll()
                }
                "Clear model" -> { pushUndo(); model = SModel(); partIdx = -1; setMode(Mode.OBJECT) }
                "Help" -> startActivity(android.content.Intent(this, HelpActivity::class.java).putExtra("topic", "model"))
            }
            true
        }
        pm.show()
    }

    private fun askText(title: String, value: String, numeric: Boolean = false, onOk: (String) -> Unit) {
        val f = field(value, numeric = numeric)
        MaterialAlertDialogBuilder(this).setTitle(title)
            .setView(LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(f, lp(MATCH, WRAP)) })
            .setPositiveButton("OK") { _, _ -> val t = f.text.toString().trim(); if (t.isNotEmpty()) onOk(t) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun save() {
        try {
            for (p in model.parts) p.fixColors()
            project.writeAsset(asset, model.toJson().toString())
            savedJson = model.toJson().toString()
            toast("Saved $asset")
        } catch (e: Exception) { toast("Save failed: ${e.message}") }
    }

    override fun onPause() { super.onPause(); playing = false }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ================================================================ software 3D viewport
    @SuppressLint("ViewConstructor")
    inner class ModelView(ctx: Context) : View(ctx) {
        private var yaw = 35f; private var pitch = 25f; private var dist = 4f
        private val target = floatArrayOf(0f, 0.5f, 0f)
        private val eye = FloatArray(3); private val right = FloatArray(3); private val up = FloatArray(3); private val fwd = FloatArray(3)
        private var focal = 1f
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1).toFloat() }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(11).toFloat(); color = C.DIM }
        private val path = Path()
        private var mats = Array(0) { FloatArray(16) }

        // per-frame face buffers
        private var fCount = 0
        private var fDepth = FloatArray(1024); private var fPart = IntArray(1024); private var fIdx = IntArray(1024); private var fColor = IntArray(1024)
        private var fStart = IntArray(1024); private var fLen = IntArray(1024); private var fOrder = arrayOf<Int>()
        private var pts = FloatArray(8192); private var ptN = 0
        // projected vertices of the selected part (edit mode)
        private var vx = FloatArray(0); private var vy = FloatArray(0); private var vOk = BooleanArray(0)

        fun setView(y: Float, p: Float) { yaw = y; pitch = p; invalidate() }

        fun frameAll() {
            if (model.parts.isEmpty()) { target[0] = 0f; target[1] = 0.5f; target[2] = 0f; dist = 4f; invalidate(); return }
            val (mn, mx) = model.bounds()
            for (k in 0 until 3) target[k] = (mn[k] + mx[k]) / 2
            val r = hypot(hypot(mx[0] - mn[0], mx[1] - mn[1]), mx[2] - mn[2]) / 2
            dist = (r * 2.6f).coerceIn(1f, 200f); invalidate()
        }

        fun frameSelected() {
            val p = part ?: return frameAll()
            computeMats()
            val m = mats.getOrNull(partIdx) ?: return
            val vs = if (mode == Mode.EDIT) selectedVerts(p).ifEmpty { p.verts.indices.toSet() } else p.verts.indices.toSet()
            if (vs.isEmpty()) return
            val mn = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE); val mx = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            for (v in vs) { val w = Mat4.point(m, p.verts[v][0], p.verts[v][1], p.verts[v][2]); for (k in 0 until 3) { mn[k] = min(mn[k], w[k]); mx[k] = max(mx[k], w[k]) } }
            for (k in 0 until 3) target[k] = (mn[k] + mx[k]) / 2
            val r = hypot(hypot(mx[0] - mn[0], mx[1] - mn[1]), mx[2] - mn[2]) / 2
            dist = (r * 3f).coerceIn(0.5f, 200f); invalidate()
        }

        private fun computeMats() {
            if (mats.size != model.parts.size) mats = Array(model.parts.size) { FloatArray(16) }
            model.matrices(if (mode == Mode.ANIMATE) clip else null, time, mats)
        }

        private fun camera() {
            val cy = Math.toRadians(yaw.toDouble()); val cp = Math.toRadians(pitch.toDouble())
            eye[0] = target[0] + (dist * cos(cp) * sin(cy)).toFloat()
            eye[1] = target[1] + (dist * sin(cp)).toFloat()
            eye[2] = target[2] + (dist * cos(cp) * cos(cy)).toFloat()
            for (k in 0 until 3) fwd[k] = target[k] - eye[k]
            norm(fwd)
            // right = fwd x worldUp
            right[0] = -fwd[2]; right[1] = 0f; right[2] = fwd[0]
            if (right[0] * right[0] + right[2] * right[2] < 1e-6f) { right[0] = 1f; right[2] = 0f }
            norm(right)
            // up = right x fwd
            up[0] = right[1] * fwd[2] - right[2] * fwd[1]; up[1] = right[2] * fwd[0] - right[0] * fwd[2]; up[2] = right[0] * fwd[1] - right[1] * fwd[0]
            focal = height / 2f / tan(Math.toRadians(30.0)).toFloat()
        }

        private fun norm(a: FloatArray) { val l = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]).coerceAtLeast(1e-9f); a[0] /= l; a[1] /= l; a[2] /= l }

        /** Projects a world point; returns depth (<= 0 when behind the camera) and writes screen coords to out. */
        private fun project(x: Float, y: Float, z: Float, out: FloatArray): Float {
            val dx = x - eye[0]; val dy = y - eye[1]; val dz = z - eye[2]
            val cz = dx * fwd[0] + dy * fwd[1] + dz * fwd[2]
            if (cz < 0.02f) return -1f
            val cx = dx * right[0] + dy * right[1] + dz * right[2]
            val cyy = dx * up[0] + dy * up[1] + dz * up[2]
            out[0] = width / 2f + cx * focal / cz; out[1] = height / 2f - cyy * focal / cz
            return cz
        }

        private fun ensure(n: Int) {
            if (fDepth.size < n) {
                val s = max(n, fDepth.size * 2)
                fDepth = fDepth.copyOf(s); fPart = fPart.copyOf(s); fIdx = fIdx.copyOf(s); fColor = fColor.copyOf(s); fStart = fStart.copyOf(s); fLen = fLen.copyOf(s)
            }
        }
        private fun ensurePts(n: Int) { if (pts.size < n) pts = pts.copyOf(max(n, pts.size * 2)) }

        private fun shade(color: Int, k: Float): Int {
            val r = ((color shr 16) and 0xFF) * k; val g = ((color shr 8) and 0xFF) * k; val b = (color and 0xFF) * k
            return (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
        }

        override fun onDraw(c: Canvas) {
            c.drawColor(0xFF12162A.toInt())
            camera(); computeMats()
            val tmp = FloatArray(3); val tmp2 = FloatArray(3)
            // ground grid
            stroke.strokeWidth = 1f
            for (i in -10..10) {
                stroke.color = if (i == 0) 0 else if (i % 5 == 0) 0xFF2E3558.toInt() else 0xFF1E2440.toInt()
                if (i == 0) continue
                line3(c, i * 0.5f, 0f, -5f, i * 0.5f, 0f, 5f, tmp, tmp2)
                line3(c, -5f, 0f, i * 0.5f, 5f, 0f, i * 0.5f, tmp, tmp2)
            }
            stroke.strokeWidth = dp(1.5f).toFloat()
            stroke.color = 0xAAFF5C6C.toInt(); line3(c, -5f, 0f, 0f, 5f, 0f, 0f, tmp, tmp2)
            stroke.color = 0xAA5B7CFF.toInt(); line3(c, 0f, 0f, -5f, 0f, 0f, 5f, tmp, tmp2)

            // collect faces
            fCount = 0; ptN = 0
            val light = floatArrayOf(0.45f, 0.8f, 0.4f).also { norm(it) }
            val wv = ArrayList<FloatArray>()
            for ((pi, p) in model.parts.withIndex()) {
                if (!p.visible) continue
                val m = mats[pi]
                // transform verts
                wv.clear()
                for (v in p.verts) wv.add(Mat4.point(m, v[0], v[1], v[2]))
                val editing = mode == Mode.EDIT && pi == partIdx
                if (editing) {
                    if (vx.size < p.verts.size) { vx = FloatArray(p.verts.size); vy = FloatArray(p.verts.size); vOk = BooleanArray(p.verts.size) }
                    for (i in p.verts.indices) { val d = project(wv[i][0], wv[i][1], wv[i][2], tmp); vOk[i] = d > 0; vx[i] = tmp[0]; vy[i] = tmp[1] }
                }
                for ((fi, f) in p.faces.withIndex()) {
                    if (f.size < 3 || f.any { it !in wv.indices }) continue
                    // normal (Newell) in world space
                    var nx = 0f; var ny = 0f; var nz = 0f; var cx = 0f; var cy = 0f; var cz = 0f
                    for (k in f.indices) {
                        val a = wv[f[k]]; val b = wv[f[(k + 1) % f.size]]
                        nx += (a[1] - b[1]) * (a[2] + b[2]); ny += (a[2] - b[2]) * (a[0] + b[0]); nz += (a[0] - b[0]) * (a[1] + b[1])
                        cx += a[0]; cy += a[1]; cz += a[2]
                    }
                    cx /= f.size; cy /= f.size; cz /= f.size
                    val nl = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-9f); nx /= nl; ny /= nl; nz /= nl
                    val facing = nx * (eye[0] - cx) + ny * (eye[1] - cy) + nz * (eye[2] - cz)
                    val see = xray && editing
                    if (facing < 0 && !see && !wireframe) continue
                    ensure(fCount + 1); ensurePts(ptN + f.size * 2)
                    var ok = true; var depth = 0f
                    val start = ptN
                    for (vi in f) { val d = project(wv[vi][0], wv[vi][1], wv[vi][2], tmp); if (d <= 0) { ok = false; break }; depth += d; pts[ptN++] = tmp[0]; pts[ptN++] = tmp[1] }
                    if (!ok) { ptN = start; continue }
                    val lambert = max(0f, nx * light[0] + ny * light[1] + nz * light[2])
                    val head = max(0f, -(nx * fwd[0] + ny * fwd[1] + nz * fwd[2]))
                    val base = p.faceColors.getOrElse(fi) { 0 }.let { if (it == 0) p.color else it }
                    val k = if (facing < 0) 0.25f else 0.32f + 0.5f * lambert + 0.25f * head
                    fDepth[fCount] = depth / f.size; fPart[fCount] = pi; fIdx[fCount] = fi; fColor[fCount] = shade(base, k)
                    fStart[fCount] = start; fLen[fCount] = f.size
                    fCount++
                }
            }
            // painter's algorithm (far to near)
            fOrder = Array(fCount) { it }
            val dd = fDepth
            java.util.Arrays.sort(fOrder) { a, b -> dd[b].compareTo(dd[a]) }
            val selCol = 0xFFFF9F43.toInt()
            for (o in fOrder) {
                buildPath(o)
                val editingPart = mode == Mode.EDIT && fPart[o] == partIdx
                if (!wireframe) {
                    fill.color = fColor[o]
                    if (xray && editingPart) fill.alpha = 150
                    c.drawPath(path, fill)
                }
                if (editingPart && faceSelect && fIdx[o] in selFaces) { fill.color = 0x66FF9F43; c.drawPath(path, fill) }
                if (wireframe || editingPart) {
                    stroke.strokeWidth = 1f
                    stroke.color = if (editingPart) 0xCC0B0E1A.toInt() else 0xAA8F96B8.toInt()
                    if (wireframe && !editingPart) stroke.color = (fColor[o] and 0x00FFFFFF) or 0xCC000000.toInt()
                    c.drawPath(path, stroke)
                } else if (mode == Mode.OBJECT && fPart[o] == partIdx) {
                    stroke.strokeWidth = 1f; stroke.color = 0x55FF9F43; c.drawPath(path, stroke)
                }
            }
            // vertices
            val p = part
            if (mode == Mode.EDIT && p != null && p.visible) {
                val r = dp(3).toFloat()
                val sv = selectedVerts(p)
                for (i in p.verts.indices) {
                    if (i >= vOk.size || !vOk[i]) continue
                    val sel = i in sv
                    if (faceSelect && !sel) continue
                    fill.color = if (sel) selCol else 0xFFE8EAF6.toInt()
                    c.drawCircle(vx[i], vy[i], if (sel) r * 1.3f else r, fill)
                }
            }
            // gizmo axes at selection pivot
            drawGizmo(c, tmp, tmp2)
            // mode label
            text.color = C.DIM
            c.drawText(when (mode) { Mode.OBJECT -> "Object Mode"; Mode.EDIT -> "Edit Mode — ${if (faceSelect) "faces" else "vertices"}"; Mode.ANIMATE -> "Animate — ${clip?.name ?: ""} ${fmt(time)}s (auto-key)" }, dp(10).toFloat(), dp(18).toFloat(), text)
            if (axisLock >= 0) { text.color = AXIS_COLORS[axisLock]; c.drawText("Axis lock: ${"XYZ"[axisLock]}", dp(10).toFloat(), dp(34).toFloat(), text) }
        }

        private fun buildPath(o: Int) {
            path.rewind()
            val s = fStart[o]
            path.moveTo(pts[s], pts[s + 1])
            for (k in 1 until fLen[o]) path.lineTo(pts[s + k * 2], pts[s + k * 2 + 1])
            path.close()
        }

        private fun line3(c: Canvas, x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, a: FloatArray, b: FloatArray) {
            if (project(x1, y1, z1, a) <= 0 || project(x2, y2, z2, b) <= 0) return
            c.drawLine(a[0], a[1], b[0], b[1], stroke)
        }

        private fun pivotWorld(): FloatArray? {
            val p = part ?: return null
            val m = mats.getOrNull(partIdx) ?: return null
            if (mode == Mode.EDIT) {
                val vs = selectedVerts(p); if (vs.isEmpty()) return null
                val cc = ModelOps.centroid(p, vs); return Mat4.point(m, cc[0], cc[1], cc[2])
            }
            return floatArrayOf(m[12], m[13], m[14])
        }

        private fun drawGizmo(c: Canvas, a: FloatArray, b: FloatArray) {
            if (tool == Tool.SELECT) return
            val pv = pivotWorld() ?: return
            val len = dist * 0.18f
            stroke.strokeWidth = dp(2.5f).toFloat()
            for (k in 0 until 3) {
                if (axisLock >= 0 && axisLock != k) continue
                stroke.color = AXIS_COLORS[k]
                val e = pv.copyOf(); e[k] += len
                line3(c, pv[0], pv[1], pv[2], e[0], e[1], e[2], a, b)
                if (project(e[0], e[1], e[2], a) > 0) { fill.color = AXIS_COLORS[k]; c.drawCircle(a[0], a[1], dp(4).toFloat(), fill) }
            }
            if (project(pv[0], pv[1], pv[2], a) > 0) { fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(a[0], a[1], dp(3.5f).toFloat(), fill) }
        }

        // ------------------------------------------------------------ picking
        private fun pointInPoly(o: Int, x: Float, y: Float): Boolean {
            val s = fStart[o]; val n = fLen[o]
            var inside = false
            var j = n - 1
            for (i in 0 until n) {
                val xi = pts[s + i * 2]; val yi = pts[s + i * 2 + 1]; val xj = pts[s + j * 2]; val yj = pts[s + j * 2 + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi + 1e-9f) + xi) inside = !inside
                j = i
            }
            return inside
        }

        private fun pickFace(x: Float, y: Float, onlyPart: Int = -1): Pair<Int, Int>? {
            for (k in fOrder.indices.reversed()) {
                val o = fOrder[k]
                if (onlyPart >= 0 && fPart[o] != onlyPart) continue
                if (pointInPoly(o, x, y)) return fPart[o] to fIdx[o]
            }
            return null
        }

        private fun pickVert(x: Float, y: Float): Int {
            val p = part ?: return -1
            var best = -1; var bd = dp(26).toFloat()
            for (i in p.verts.indices) { if (i >= vOk.size || !vOk[i]) continue; val d = hypot(vx[i] - x, vy[i] - y); if (d < bd) { bd = d; best = i } }
            return best
        }

        private fun tap(x: Float, y: Float) {
            when (mode) {
                Mode.EDIT -> {
                    if (faceSelect) {
                        val hit = pickFace(x, y, partIdx)
                        if (hit != null) { if (!selFaces.remove(hit.second)) selFaces.add(hit.second) } else selFaces.clear()
                    } else {
                        val v = pickVert(x, y)
                        if (v >= 0) { if (!selVerts.remove(v)) selVerts.add(v) } else selVerts.clear()
                    }
                    invalidate(); updateStats()
                }
                else -> {
                    val hit = pickFace(x, y)
                    if (hit != null) selectPart(hit.first) else if (mode == Mode.OBJECT) { partIdx = -1; refreshOutliner(); refreshProps(); invalidate(); updateStats() }
                }
            }
        }

        // ------------------------------------------------------------ gestures
        private var downX = 0f; private var downY = 0f; private var lastX = 0f; private var lastY = 0f
        private var lastSpan = 0f; private var fingers = 0; private var moved = false
        private var dragMode = 0 // 0 none, 1 orbit, 2 transform, 3 multi
        private var lastTapTime = 0L
        private var undoPushed = false

        private fun span(e: MotionEvent): Float = if (e.pointerCount < 2) 0f else hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
        private fun ax(e: MotionEvent): Float { var s = 0f; for (i in 0 until e.pointerCount) s += e.getX(i); return s / e.pointerCount }
        private fun ay(e: MotionEvent): Float { var s = 0f; for (i in 0 until e.pointerCount) s += e.getY(i); return s / e.pointerCount }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x; downY = e.y; lastX = e.x; lastY = e.y; moved = false; fingers = 1; undoPushed = false
                    val hasSel = when (mode) { Mode.EDIT -> part != null && selectedVerts(part!!).isNotEmpty(); else -> part != null }
                    dragMode = if (tool != Tool.SELECT && hasSel) 2 else 1
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    fingers = e.pointerCount; dragMode = 3; lastX = ax(e); lastY = ay(e); lastSpan = span(e); moved = true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    fingers = e.pointerCount - 1
                    // recompute anchors from remaining pointers
                    var sx = 0f; var sy = 0f; var n = 0
                    for (i in 0 until e.pointerCount) if (i != e.actionIndex) { sx += e.getX(i); sy += e.getY(i); n++ }
                    if (n > 0) { lastX = sx / n; lastY = sy / n }
                    lastSpan = 0f
                }
                MotionEvent.ACTION_MOVE -> {
                    val x = ax(e); val y = ay(e)
                    val dx = x - lastX; val dy = y - lastY
                    if (abs(e.x - downX) > dp(5) || abs(e.y - downY) > dp(5)) moved = true
                    if (!moved) return true
                    when {
                        dragMode == 3 && e.pointerCount >= 3 -> pan(dx, dy)
                        dragMode == 3 && e.pointerCount == 2 -> {
                            yaw -= dx * 0.35f; pitch = (pitch + dy * 0.35f).coerceIn(-89f, 89f)
                            val s = span(e)
                            if (lastSpan > 0 && s > 0) dist = (dist * lastSpan / s).coerceIn(0.3f, 300f)
                            lastSpan = s
                        }
                        dragMode == 1 -> { yaw -= dx * 0.4f; pitch = (pitch + dy * 0.4f).coerceIn(-89f, 89f) }
                        dragMode == 2 -> { if (!undoPushed) { pushUndo(); undoPushed = true }; transform(dx, dy) }
                    }
                    lastX = x; lastY = y
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastTapTime < 300) frameSelected() else tap(e.x, e.y)
                        lastTapTime = now
                    } else if (dragMode == 2) { applySnap(); invalidate(); refreshProps(); updateStats(); timelineRef?.invalidate() }
                    dragMode = 0
                }
                MotionEvent.ACTION_CANCEL -> dragMode = 0
            }
            return true
        }

        private fun pan(dx: Float, dy: Float) {
            val s = dist / focal
            for (k in 0 until 3) target[k] += (-right[k] * dx + up[k] * dy) * s
        }

        private fun snapV(v: Float) = if (snap) Math.round(v * 10f) / 10f else v

        private fun worldDelta(dx: Float, dy: Float): FloatArray {
            val pv = pivotWorld()
            val depth = if (pv != null) max(0.1f, (pv[0] - eye[0]) * fwd[0] + (pv[1] - eye[1]) * fwd[1] + (pv[2] - eye[2]) * fwd[2]) else dist
            val s = depth / focal
            val d = FloatArray(3) { (right[it] * dx - up[it] * dy) * s }
            if (axisLock >= 0) {
                // move along the locked axis by the projected screen motion of that axis
                val axis = FloatArray(3).also { it[axisLock] = 1f }
                val sx = axis[0] * right[0] + axis[1] * right[1] + axis[2] * right[2]
                val sy = -(axis[0] * up[0] + axis[1] * up[1] + axis[2] * up[2])
                val l2 = sx * sx + sy * sy
                val amt = if (l2 > 1e-4f) (dx * sx + dy * sy) / l2 * s else 0f
                return FloatArray(3).also { it[axisLock] = amt }
            }
            return d
        }

        /** Grid snapping applied when a drag ends (0.1 units / 15 degrees). */
        fun applySnap() {
            if (!snap || part == null || mode == Mode.EDIT) return
            editTrs(partIdx) { pos, rot, _ ->
                if (tool == Tool.MOVE) for (k in 0 until 3) pos[k] = snapV(pos[k])
                if (tool == Tool.ROTATE) for (k in 0 until 3) rot[k] = Math.round(rot[k] / 15f) * 15f
            }
        }
        private fun transform(dx: Float, dy: Float) {
            val p = part ?: return
            when (mode) {
                Mode.EDIT -> {
                    val vs = selectedVerts(p); if (vs.isEmpty()) return
                    val m = mats.getOrNull(partIdx) ?: return
                    val inv = FloatArray(16); if (!Mat4.invert(inv, m)) return
                    when (tool) {
                        Tool.MOVE -> { val w = worldDelta(dx, dy); val l = Mat4.dir(inv, w[0], w[1], w[2]); ModelOps.translate(p, vs, l[0], l[1], l[2]) }
                        Tool.ROTATE -> ModelOps.rotate(p, vs, if (axisLock >= 0) axisLock else 1, dx * 0.5f)
                        Tool.SCALE -> { val f = exp(dx * 0.006f); val sx = if (axisLock < 0 || axisLock == 0) f else 1f; val sy = if (axisLock < 0 || axisLock == 1) f else 1f; val sz = if (axisLock < 0 || axisLock == 2) f else 1f; ModelOps.scale(p, vs, sx, sy, sz) }
                        else -> {}
                    }
                }
                else -> {
                    val w = worldDelta(dx, dy)
                    // convert world delta into the parent's space
                    val par = p.parent
                    val l = if (par in model.parts.indices) { val inv = FloatArray(16); if (Mat4.invert(inv, mats[par])) Mat4.dir(inv, w[0], w[1], w[2]) else w } else w
                    editTrs(partIdx) { pos, rot, sc ->
                        when (tool) {
                            Tool.MOVE -> {
                                for (k in 0 until 3) pos[k] += l[k]
                            }
                            Tool.ROTATE -> { val a = if (axisLock >= 0) axisLock else 1; rot[a] += dx * 0.5f }
                            Tool.SCALE -> { val f = exp(dx * 0.006f); for (k in 0 until 3) if (axisLock < 0 || axisLock == k) sc[k] = (sc[k] * f).coerceIn(0.001f, 1000f) }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}
