package com.sengine.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sengine.engine.Engine
import com.sengine.engine.core.AssetKind
import com.sengine.engine.core.Animator
import com.sengine.engine.core.Camera3D
import com.sengine.engine.core.Collider3D
import com.sengine.engine.core.Light
import com.sengine.engine.core.MeshRenderer
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.AudioSource
import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.ScriptComponent
import com.sengine.engine.core.SpriteRenderer
import com.sengine.engine.core.TextRenderer
import com.sengine.engine.core.Tilemap
import com.sengine.engine.render.EditorState
import com.sengine.engine.render.SceneRenderer
import com.sengine.engine.render.Tool
import com.sengine.project.Project
import com.sengine.project.ProjectManager
import com.sengine.project.Templates

class EditorActivity : AppCompatActivity(), EditorHost {

    override lateinit var engine: Engine
    override lateinit var history: History
    override lateinit var project: Project
    val state = EditorState()

    private lateinit var glView: GLSurfaceView
    private lateinit var controller: ViewportController
    private lateinit var inspector: InspectorPanel
    private lateinit var hierarchy: HierarchyAdapter
    private lateinit var controls: GameControlsView

    // Godot 4 Studio UI Dock Elements
    private lateinit var headerBar: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var statsText: TextView
    private lateinit var mode2dBtn: TextView
    private lateinit var mode3dBtn: TextView
    private lateinit var scriptWorkspaceBtn: TextView
    private lateinit var gameWorkspaceBtn: TextView
    private lateinit var assetLibWorkspaceBtn: TextView

    private lateinit var leftDock: LinearLayout
    private lateinit var tabSceneTree: TextView
    private lateinit var tabFileSystem: TextView
    private lateinit var sceneTreeContainer: View
    private lateinit var fileSystemContainer: View

    private lateinit var centerViewportArea: FrameLayout
    private lateinit var sceneTabsBar: LinearLayout
    private lateinit var sceneTabName: TextView

    private lateinit var bottomDock: LinearLayout
    private lateinit var bottomDockContent: FrameLayout
    private lateinit var tabConsole: TextView
    private lateinit var tabDebugger: TextView
    private lateinit var tabAudio: TextView
    private lateinit var tabAnimation: TextView
    private lateinit var tabShaders: TextView
    private lateinit var consoleText: TextView
    private lateinit var consoleScroll: ScrollView
    private lateinit var debuggerText: TextView
    private lateinit var debuggerScroll: ScrollView

    private lateinit var rightDock: LinearLayout
    private lateinit var tabInspector: TextView
    private lateinit var tabNodeSignals: TextView
    private lateinit var tabHistory: TextView
    private lateinit var inspectorContainer: View
    private lateinit var nodeSignalsContainer: LinearLayout
    private lateinit var historyContainer: ScrollView
    private lateinit var historyText: TextView

    private lateinit var assetsRow: LinearLayout
    private lateinit var assetsScroll: HorizontalScrollView

    private lateinit var playBtn: android.widget.ImageView
    private lateinit var pauseBtn: android.widget.ImageView
    private lateinit var stopBtn: android.widget.ImageView
    private lateinit var stepBtn: android.widget.ImageView
    private val toolButtons = HashMap<Tool, android.widget.ImageView>()

    private val handler = Handler(Looper.getMainLooper())
    private var lastObjectCount = -1
    private var importKind = AssetKind.TEXTURE

    private val ticker = object : Runnable {
        override fun run() {
            inspector.refreshValues()
            val count = engine.scene.objects.size
            if (engine.mode != Engine.Mode.EDIT && count != lastObjectCount) refreshHierarchy()
            val mode = when (engine.mode) { Engine.Mode.EDIT -> "EDIT"; Engine.Mode.PLAY -> "▶ PLAYING"; Engine.Mode.PAUSED -> "⏸ PAUSED" }
            statsText.text = "$mode  •  ${engine.scene.name}  •  ${engine.fps.toInt()} FPS  •  $count objects" +
                (if (state.mode3D) "  •  3D" else "  •  2D") + (if (controller.snap) "  •  snap" else "") +
                if (state.showProfiler) String.format("\nscripts %.2f ms  •  physics %.2f ms  •  render %.2f ms  •  %d draw calls  •  heap %d MB",
                    engine.scriptMs, engine.physicsMs, engine.renderMs, engine.drawCalls,
                    (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576) else ""

            updateDebuggerInfo()
            handler.postDelayed(this, 200)
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importAsset(uri)
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) try {
            contentResolver.openOutputStream(uri)?.use { ProjectManager.exportZip(project, it) }
            toast("Project exported")
        } catch (e: Exception) { toast("Export failed: ${e.message}") }
    }

    private val engineListener = object : Engine.Listener {
        override fun onLog(level: Int, message: String) { handler.post { appendConsole(level, message) } }
        override fun onModeChanged(mode: Engine.Mode) { handler.post { updateModeUi() } }
        override fun onSceneReplaced() {
            handler.post {
                refreshHierarchy()
                if (engine.mode == Engine.Mode.EDIT) inspector.rebuild()
            }
        }
    }

    // ================================================================== lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = ProjectManager.open(this, intent.getStringExtra("project") ?: run { finish(); return })
        val sceneName = if (project.sceneExists(project.startScene)) project.startScene
        else project.listScenes().firstOrNull() ?: "Main"
        val scene = if (project.sceneExists(sceneName)) project.loadScene(sceneName) else Scene(sceneName).also {
            it.create("Main Camera").add(Camera2D())
        }
        engine = Engine(project, scene)
        history = History(engine)
        engine.listeners.add(engineListener)
        engine.platform = object : com.sengine.engine.Platform {
            override fun vibrate(ms: Int) {
                try {
                    val v = getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                    v?.vibrate(android.os.VibrationEffect.createOneShot(ms.toLong().coerceIn(1, 2000), android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } catch (_: Throwable) {}
            }
            override fun quit() { runOnUiThread { toast("game.quit() — ignored in editor") } }
            override fun openUrl(url: String) { runOnUiThread { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {} } }
            override fun toast(text: String) { runOnUiThread { this@EditorActivity.toast(text) } }
        }

        buildGodot4StudioUi()

        synchronized(engine.lock) {
            scene.updateTransforms()
            val cam = engine.mainCamera()
            if (cam != null) {
                state.view.cx = cam.world.tx; state.view.cy = cam.world.ty
                state.view.size = (cam.getAny<Camera2D>()!!.size * 1.3f)
            } else state.view.size = 6f
        }
        val auto3D = synchronized(engine.lock) { engine.mainCamera3D() != null && engine.mainCamera() == null }
        if (intent.getBooleanExtra("mode3d", false) || auto3D) setMode3D(true) else setMode3D(false)

        refreshHierarchy()
        inspector.rebuild()
        refreshAssets()
        updateModeUi()
        appendConsole(0, "S Engine Godot 4 Studio — Project '${project.name}', Scene '${scene.name}'")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (engine.mode != Engine.Mode.EDIT) { engine.stop(); return }
                saveScene(silent = true)
                finish()
            }
        })
    }

    private var reloadSceneOnResume = false

    override fun onResume() {
        super.onResume()
        if (reloadSceneOnResume) { reloadSceneOnResume = false; if (engine.mode == Engine.Mode.EDIT) openScene(engine.scene.name) }
        glView.onResume()
        handler.post(ticker)
        refreshAssets()
    }

    override fun onPause() {
        super.onPause()
        glView.onPause()
        handler.removeCallbacks(ticker)
        if (engine.mode == Engine.Mode.EDIT && history.dirty) saveScene(silent = true)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::engine.isInitialized) {
            engine.listeners.remove(engineListener)
            engine.release()
        }
    }

    // ================================================================== Godot 4 Multi-Dock UI Construction
    @SuppressLint("ClickableViewAccessibility")
    private fun buildGodot4StudioUi() {
        val root = vbox().apply { setBackgroundColor(C.BG) }

        // 1. TOP HEADER BAR (Godot 4 style)
        headerBar = hbox().apply { setPadding(dp(6), dp(4), dp(6), dp(4)); setBackgroundColor(C.HEADER) }

        // Left Header: Back button + Title
        val backBtn = iconButton("back", "Back to projects", C.TEXT, C.PANEL2, 34) { onBackPressedDispatcher.onBackPressed() }
        headerBar.addView(backBtn, lp(dp(34), dp(34)).margins(0, 0, dp(4), 0))
        titleText = label("", 12f, C.TEXT, true).apply { setPadding(dp(4), 0, dp(8), 0); maxWidth = dp(140); isSingleLine = true }
        headerBar.addView(titleText)

        // Center Header: Workspace Mode Switcher (2D | 3D | Script | Game | AssetLib)
        val modeSwitcher = hbox().apply {
            background = round(C.PANEL2, dp(6).toFloat())
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        mode2dBtn = button("2D", C.PANEL2) { setMode3D(false) }.apply { textSize = 11f; setPadding(dp(8), 0, dp(8), 0) }
        mode3dBtn = button("3D", C.PANEL2) { setMode3D(true) }.apply { textSize = 11f; setPadding(dp(8), 0, dp(8), 0) }
        scriptWorkspaceBtn = button("Script", C.PANEL2) { openScriptWorkspace() }.apply { textSize = 11f; setPadding(dp(8), 0, dp(8), 0) }
        gameWorkspaceBtn = button("Game", C.PANEL2) { saveScene(silent = true); startActivity(Intent(this, PlayerActivity::class.java).putExtra("project", project.name)) }.apply { textSize = 11f; setPadding(dp(8), 0, dp(8), 0) }
        assetLibWorkspaceBtn = button("AssetLib", C.PANEL2) { startActivity(Intent(this, AssetStoreActivity::class.java).putExtra("project", project.name)) }.apply { textSize = 11f; setPadding(dp(8), 0, dp(8), 0) }

        modeSwitcher.addView(mode2dBtn)
        modeSwitcher.addView(mode3dBtn)
        modeSwitcher.addView(scriptWorkspaceBtn)
        modeSwitcher.addView(gameWorkspaceBtn)
        modeSwitcher.addView(assetLibWorkspaceBtn)
        headerBar.addView(modeSwitcher, lp(WRAP, dp(34)).margins(dp(8), 0, dp(8), 0))

        headerBar.addView(View(this), lp(0, 1, 1f)) // spacer

        // Right Header: Playback controls + Quick Tools
        playBtn = iconButton("play", "Play Scene", C.TEXT, C.PANEL2, 34) { if (engine.mode == Engine.Mode.EDIT) startPlay() else engine.stop() }
        pauseBtn = iconButton("pause", "Pause", C.TEXT, C.PANEL2, 34) { if (engine.mode == Engine.Mode.PAUSED) engine.play() else engine.pause() }
        stopBtn = iconButton("stop", "Stop", C.TEXT, C.PANEL2, 34) { engine.stop() }
        stepBtn = iconButton("step", "Step Frame", C.TEXT, C.PANEL2, 34) { engine.stepFrame() }

        val saveBtn = iconButton("save", "Save Scene", C.TEXT, C.PANEL2, 34) { saveScene() }
        val apkBtn = iconButton("rocket", "Build APK", C.ACCENT, C.PANEL2, 34) { saveScene(silent = true); startActivity(Intent(this, BuildActivity::class.java).putExtra("project", project.name)) }
        val aiBtn = iconButton("doctor", "AI Assistant", C.TEXT, C.PANEL2, 34) { startActivity(Intent(this, AgentActivity::class.java)) }
        val moreBtn = iconButton("more", "Main Menu", C.TEXT, C.PANEL2, 34) { mainMenu(it) }

        headerBar.addView(playBtn, lp(dp(34), dp(34)).margins(dp(2), 0, dp(2), 0))
        headerBar.addView(pauseBtn, lp(dp(34), dp(34)).margins(dp(2), 0, dp(2), 0))
        headerBar.addView(stopBtn, lp(dp(34), dp(34)).margins(dp(2), 0, dp(2), 0))
        headerBar.addView(stepBtn, lp(dp(34), dp(34)).margins(dp(2), 0, dp(2), 0))
        headerBar.addView(saveBtn, lp(dp(34), dp(34)).margins(dp(4), 0, dp(2), 0))
        headerBar.addView(apkBtn, lp(dp(34), dp(34)).margins(dp(2), 0, dp(2), 0))
        headerBar.addView(aiBtn, lp(dp(34), dp(34)).margins(dp(2), 0, dp(2), 0))
        headerBar.addView(moreBtn, lp(dp(34), dp(34)).margins(dp(2), 0, dp(2), 0))

        val headerScroll = HorizontalScrollView(this).apply { addView(headerBar); isHorizontalScrollBarEnabled = false }
        root.addView(headerScroll, lp(MATCH, WRAP))

        // 2. MIDDLE AREA (Multi-Dock Layout: Left Dock | Center Viewport | Right Dock)
        val middle = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        // --- LEFT DOCK (Scene Tree & FileSystem Docks) ---
        leftDock = vbox().apply { setBackgroundColor(C.PANEL) }
        val leftDockHeader = hbox().apply { setBackgroundColor(C.HEADER); setPadding(dp(4), dp(2), dp(4), dp(2)) }
        tabSceneTree = button("Scene", C.HEADER) { showLeftDockTab(0) }.apply { textSize = 11f }
        tabFileSystem = button("FileSystem", C.HEADER) { showLeftDockTab(1) }.apply { textSize = 11f }
        leftDockHeader.addView(tabSceneTree)
        leftDockHeader.addView(tabFileSystem)
        leftDockHeader.addView(View(this), lp(0, 1, 1f))
        leftDockHeader.addView(button("＋", C.HEADER) { addObjectMenu(it) }.apply { textSize = 12f })
        leftDock.addView(leftDockHeader, lp(MATCH, WRAP))

        // Left Dock Content Stack
        val leftDockContent = FrameLayout(this)

        // Scene Tree Panel
        sceneTreeContainer = vbox().apply { setBackgroundColor(C.PANEL) }
        hierarchy = HierarchyAdapter(this,
            onClick = { select(it.id) },
            onLongClick = { go, v -> objectMenu(go, v) },
            onToggleActive = { go -> history.record(state.selectedId); synchronized(engine.lock) { go.active = !go.active }; refreshHierarchy(); inspector.refreshValues() })
        val rv = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@EditorActivity); adapter = hierarchy }
        (sceneTreeContainer as LinearLayout).addView(rv, lp(MATCH, MATCH))
        leftDockContent.addView(sceneTreeContainer)

        // FileSystem Panel
        fileSystemContainer = vbox().apply { setBackgroundColor(C.PANEL); visibility = View.GONE }
        val fsToolbar = hbox().apply { setPadding(dp(4), dp(4), dp(4), dp(4)) }
        fsToolbar.addView(button("+ Script") { newScriptDialog { refreshAssets(); openScript(it) } }.apply { textSize = 10f }, lp(0, WRAP, 1f))
        fsToolbar.addView(button("+ Sprite") { saveScene(silent = true); startActivity(Intent(this, SpriteStudioActivity::class.java).putExtra("project", project.name)) }.apply { textSize = 10f }, lp(0, WRAP, 1f))
        (fileSystemContainer as LinearLayout).addView(fsToolbar, lp(MATCH, WRAP))

        assetsRow = hbox().apply { setPadding(dp(6), dp(6), dp(6), dp(6)) }
        assetsScroll = HorizontalScrollView(this).apply { addView(assetsRow) }
        (fileSystemContainer as LinearLayout).addView(assetsScroll, lp(MATCH, MATCH))
        leftDockContent.addView(fileSystemContainer)

        leftDock.addView(leftDockContent, lp(MATCH, 0, 1f))
        middle.addView(leftDock, lp(dp(200), MATCH))

        // --- CENTER VIEWPORT AREA ---
        centerViewportArea = FrameLayout(this)
        val centerContainer = vbox()

        // Center Scene Tabs
        sceneTabsBar = hbox().apply { setBackgroundColor(C.HEADER); setPadding(dp(6), dp(2), dp(6), dp(2)) }
        sceneTabName = label("${project.startScene}.scene ✕", 11f, C.ACCENT, true)
        sceneTabsBar.addView(sceneTabName)
        sceneTabsBar.addView(View(this), lp(0, 1, 1f))
        sceneTabsBar.addView(button("Snap", C.HEADER) { controller.snap = !controller.snap }.apply { textSize = 10f })
        sceneTabsBar.addView(button("Grid", C.HEADER) { state.showGrid = !state.showGrid }.apply { textSize = 10f })
        sceneTabsBar.addView(button("Prof", C.HEADER) { state.showProfiler = !state.showProfiler }.apply { textSize = 10f })
        centerContainer.addView(sceneTabsBar, lp(MATCH, WRAP))

        // Viewport Stack
        val vp = FrameLayout(this)
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(SceneRenderer(engine, state))
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        controller = ViewportController(this, engine, state)
        glView.setOnTouchListener(controller)
        vp.addView(glView)
        controls = GameControlsView(this) { engine.input }
        vp.addView(controls)

        statsText = label("", 11f, 0xCCFFFFFF.toInt()).apply {
            setPadding(dp(8), dp(3), dp(8), dp(3)); background = round(0x88000000.toInt(), dp(4).toFloat())
        }
        vp.addView(statsText, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply { setMargins(dp(6), dp(6), 0, 0) })

        // Gizmo Tool Overlay Buttons
        val gizmoBar = vbox().apply {
            background = round(0xAA1C1D21.toInt(), dp(6).toFloat())
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        for ((tool, icon) in listOf(Tool.HAND to "cursor", Tool.MOVE to "move", Tool.ROTATE to "rotate", Tool.SCALE to "scale")) {
            val b = iconButton(icon, tool.name, C.TEXT, C.PANEL2, 32) { setTool(tool) }
            toolButtons[tool] = b
            gizmoBar.addView(b, lp(dp(32), dp(32)).margins(0, dp(1), 0, dp(1)))
        }
        val frameBtn = iconButton("target", "Frame Target", C.TEXT, C.PANEL2, 32) { controller.frame(synchronized(engine.lock) { engine.scene.findById(state.selectedId) }) }
        gizmoBar.addView(frameBtn, lp(dp(32), dp(32)).margins(0, dp(1), 0, dp(1)))
        vp.addView(gizmoBar, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(6), dp(6), 0) })

        centerContainer.addView(vp, lp(MATCH, 0, 1f))

        // --- COLLAPSIBLE BOTTOM DOCK (Console / Debugger / Audio / Animation / Shader / Tilemap) ---
        bottomDock = vbox().apply { setBackgroundColor(C.PANEL) }
        val bottomTabs = hbox().apply { setBackgroundColor(C.HEADER); setPadding(dp(4), dp(2), dp(4), dp(2)) }
        tabConsole = button("Output / Console", C.HEADER) { showBottomDockTab(0) }.apply { textSize = 11f }
        tabDebugger = button("Debugger", C.HEADER) { showBottomDockTab(1) }.apply { textSize = 11f }
        tabAudio = button("Audio", C.HEADER) { showBottomDockTab(2) }.apply { textSize = 11f }
        tabAnimation = button("Animation", C.HEADER) { openAnimationEditor(null) }.apply { textSize = 11f }
        tabShaders = button("Tilemap", C.HEADER) { startActivity(Intent(this@EditorActivity, TilemapEditorActivity::class.java).putExtra("project", project.name).putExtra("scene", engine.scene.name)) }.apply { textSize = 11f }

        bottomTabs.addView(tabConsole)
        bottomTabs.addView(tabDebugger)
        bottomTabs.addView(tabAudio)
        bottomTabs.addView(tabAnimation)
        bottomTabs.addView(tabShaders)
        bottomTabs.addView(View(this), lp(0, 1, 1f))
        bottomTabs.addView(button("Clear", C.HEADER) { consoleText.text = "" }.apply { textSize = 11f })
        bottomTabs.addView(button("▾", C.HEADER) { toggle(bottomDockContent) }.apply { textSize = 11f })
        bottomDock.addView(bottomTabs, lp(MATCH, WRAP))

        bottomDockContent = FrameLayout(this)

        // Console Scroll View
        consoleText = label("", 11f, 0xFFCFD2D6.toInt()).apply { typeface = Typeface.MONOSPACE; setPadding(dp(8), dp(4), dp(8), dp(4)); setTextIsSelectable(true) }
        consoleScroll = ScrollView(this).apply { addView(consoleText) }
        bottomDockContent.addView(consoleScroll)

        // Debugger Scroll View
        debuggerText = label("Debugger initialized.", 11f, 0xFF4FC3F7.toInt()).apply { typeface = Typeface.MONOSPACE; setPadding(dp(8), dp(4), dp(8), dp(4)); setTextIsSelectable(true) }
        debuggerScroll = ScrollView(this).apply { addView(debuggerText); visibility = View.GONE }
        bottomDockContent.addView(debuggerScroll)

        bottomDock.addView(bottomDockContent, lp(MATCH, dp(110)))
        centerContainer.addView(bottomDock, lp(MATCH, WRAP))

        centerViewportArea.addView(centerContainer)
        middle.addView(centerViewportArea, lp(0, MATCH, 1f))

        // --- RIGHT DOCK (Inspector / Node Signals / History) ---
        rightDock = vbox().apply { setBackgroundColor(C.PANEL) }
        val rightDockHeader = hbox().apply { setBackgroundColor(C.HEADER); setPadding(dp(4), dp(2), dp(4), dp(2)) }
        tabInspector = button("Inspector", C.HEADER) { showRightDockTab(0) }.apply { textSize = 11f }
        tabNodeSignals = button("Node", C.HEADER) { showRightDockTab(1) }.apply { textSize = 11f }
        tabHistory = button("History", C.HEADER) { showRightDockTab(2) }.apply { textSize = 11f }
        rightDockHeader.addView(tabInspector)
        rightDockHeader.addView(tabNodeSignals)
        rightDockHeader.addView(tabHistory)
        rightDock.addView(rightDockHeader, lp(MATCH, WRAP))

        val rightDockContent = FrameLayout(this)

        // Inspector Content
        inspectorContainer = vbox()
        val inspScrollView = ScrollView(this).apply { addView(inspectorContainer); isFillViewport = true }
        inspector = InspectorPanel(this, this, inspectorContainer as LinearLayout)
        rightDockContent.addView(inspScrollView)

        // Node Signals / Groups Content
        nodeSignalsContainer = vbox().apply { setPadding(dp(8), dp(8), dp(8), dp(8)); visibility = View.GONE }
        nodeSignalsContainer.addView(label("NODE SIGNALS & GROUPS", 11f, C.DIM, true))
        nodeSignalsContainer.addView(label("\nGroups:\n• Player\n• Enemy\n• Collectibles\n\nSignals:\n• body_entered()\n• area_exited()\n• animation_finished()", 12f, C.TEXT))
        rightDockContent.addView(nodeSignalsContainer)

        // History Content
        historyText = label("Undo / Redo Stack:\n[1] Initial Scene State", 11f, C.TEXT).apply { setPadding(dp(8), dp(8), dp(8), dp(8)) }
        historyContainer = ScrollView(this).apply { addView(historyText); visibility = View.GONE }
        rightDockContent.addView(historyContainer)

        rightDock.addView(rightDockContent, lp(MATCH, 0, 1f))
        middle.addView(rightDock, lp(dp(250), MATCH))

        root.addView(middle, lp(MATCH, 0, 1f))

        setContentView(root)
        setTool(Tool.MOVE)
        showLeftDockTab(0)
        showBottomDockTab(0)
        showRightDockTab(0)
        updateTitle()
    }

    private fun setMode3D(enabled: Boolean) {
        state.mode3D = enabled
        mode2dBtn.setBackgroundColor(if (!enabled) C.ACCENT else C.PANEL2)
        mode3dBtn.setBackgroundColor(if (enabled) C.ACCENT else C.PANEL2)
        if (enabled) synchronized(engine.lock) { controller.frame(engine.scene.findById(state.selectedId)) }
    }

    private fun showLeftDockTab(tab: Int) {
        sceneTreeContainer.visibility = if (tab == 0) View.VISIBLE else View.GONE
        fileSystemContainer.visibility = if (tab == 1) View.VISIBLE else View.GONE
        tabSceneTree.setTextColor(if (tab == 0) C.ACCENT else C.DIM)
        tabFileSystem.setTextColor(if (tab == 1) C.ACCENT else C.DIM)
    }

    private fun showBottomDockTab(tab: Int) {
        consoleScroll.visibility = if (tab == 0) View.VISIBLE else View.GONE
        debuggerScroll.visibility = if (tab == 1) View.VISIBLE else View.GONE
        tabConsole.setTextColor(if (tab == 0) C.ACCENT else C.DIM)
        tabDebugger.setTextColor(if (tab == 1) C.ACCENT else C.DIM)
        tabAudio.setTextColor(if (tab == 2) C.ACCENT else C.DIM)
        bottomDockContent.visibility = View.VISIBLE
        if (tab == 2) startActivity(Intent(this, MusicEditorActivity::class.java).putExtra("project", project.name))
    }

    private fun showRightDockTab(tab: Int) {
        inspectorContainer.parent?.let { (it as View).visibility = if (tab == 0) View.VISIBLE else View.GONE }
        nodeSignalsContainer.visibility = if (tab == 1) View.VISIBLE else View.GONE
        historyContainer.visibility = if (tab == 2) View.VISIBLE else View.GONE
        tabInspector.setTextColor(if (tab == 0) C.ACCENT else C.DIM)
        tabNodeSignals.setTextColor(if (tab == 1) C.ACCENT else C.DIM)
        tabHistory.setTextColor(if (tab == 2) C.ACCENT else C.DIM)
    }

    private fun updateDebuggerInfo() {
        if (::debuggerText.isInitialized) {
            val sb = StringBuilder()
            sb.append("--- GODOT 4 DEBUGGER ---\n")
            sb.append("Mode: ").append(engine.mode).append("\n")
            sb.append("FPS: ").append(engine.fps.toInt()).append("\n")
            sb.append("Scene: ").append(engine.scene.name).append(" (").append(engine.scene.objects.size).append(" nodes)\n")
            sb.append("Draw Calls: ").append(engine.drawCalls).append("\n")
            val sel = engine.scene.findById(state.selectedId)
            if (sel != null) {
                sb.append("\nSelected Node: ").append(sel.name).append(" [ID ").append(sel.id).append("]\n")
                sb.append("Position: (").append(sel.x).append(", ").append(sel.y).append(", ").append(sel.z).append(")\n")
                sb.append("Layer: ").append(sel.layer).append(" | Group: ").append(sel.group).append("\n")
                sb.append("Components: ").append(sel.components.joinToString { it.javaClass.simpleName }).append("\n")
            } else {
                sb.append("\nNo node selected.\n")
            }
            debuggerText.text = sb.toString()
        }
    }

    private fun openScriptWorkspace() {
        startActivity(Intent(this, ScriptEditorActivity::class.java).putExtra("project", project.name))
    }

    private fun toggle(v: View) { v.visibility = if (v.visibility == View.VISIBLE) View.GONE else View.VISIBLE }

    private fun updateTitle() {
        titleText.text = "${project.name} / ${engine.scene.name}"
        if (::sceneTabName.isInitialized) sceneTabName.text = "${engine.scene.name}.scene ✕"
    }

    private fun setTool(t: Tool) {
        state.tool = t
        for ((tool, b) in toolButtons) b.setBg(if (tool == t) C.ACCENT else C.PANEL2)
    }

    private var lastUiMode = Engine.Mode.EDIT

    private fun updateModeUi() {
        val m = engine.mode
        playBtn.setIconTint(if (m == Engine.Mode.EDIT) "play" else "stop", C.TEXT, 20)
        playBtn.setBg(if (m == Engine.Mode.EDIT) C.PANEL2 else C.GREEN)
        pauseBtn.setBg(if (m == Engine.Mode.PAUSED) C.YELLOW else C.PANEL2)
        stepBtn.alpha = if (m == Engine.Mode.PAUSED) 1f else 0.4f
        headerBar.setBackgroundColor(if (m == Engine.Mode.EDIT) C.HEADER else 0xFF1D2E45.toInt())
        controls.visibility = if (m == Engine.Mode.EDIT) View.GONE else View.VISIBLE
        if (m == Engine.Mode.PLAY && lastUiMode == Engine.Mode.EDIT) controls.projectLayout = project.loadControls()
        if (m != Engine.Mode.PAUSED) controls.reset()
        lastUiMode = m
        if (m == Engine.Mode.EDIT) { refreshHierarchy(); inspector.rebuild() }
        updateTitle()
    }

    fun hideKeyboard(v: View) {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
    }

    // ================================================================== EditorHost
    override fun selectedId() = state.selectedId

    override fun select(id: Long) {
        currentFocus?.let { it.clearFocus(); hideKeyboard(it) }
        state.selectedId = id
        hierarchy.selectedId = id
        hierarchy.notifyDataSetChanged()
        inspector.rebuild()
    }

    override fun onStructureChanged() = refreshHierarchy()

    fun onObjectEdited() = inspector.refreshValues()

    fun refreshHierarchy() {
        val (items, all) = synchronized(engine.lock) { engine.scene.hierarchy() to engine.scene.objects.toList() }
        lastObjectCount = all.size
        hierarchy.selectedId = state.selectedId
        hierarchy.submit(items, all)
    }

    override fun openScript(name: String) {
        when (AssetKind.of(name)) {
            AssetKind.ANIMATION -> openAnimationEditor(name)
            AssetKind.SONG -> { saveScene(silent = true); startActivity(Intent(this, MusicEditorActivity::class.java).putExtra("project", project.name).putExtra("asset", name)) }
            AssetKind.MODEL -> if (name.endsWith(".smodel")) { saveScene(silent = true); startActivity(Intent(this, ModelEditorActivity::class.java).putExtra("project", project.name).putExtra("asset", name)) }
                else toast("OBJ models are read-only — create a .smodel to edit")
            else -> if (name.endsWith(".bp")) startActivity(Intent(this, BlueprintEditorActivity::class.java).putExtra("project", project.name).putExtra("asset", name))
                else startActivity(Intent(this, ScriptEditorActivity::class.java).putExtra("project", project.name).putExtra("asset", name))
        }
    }

    fun openAnimationEditor(name: String?) {
        saveScene(silent = true)
        startActivity(Intent(this, AnimationEditorActivity::class.java).putExtra("project", project.name).apply { if (name != null) putExtra("asset", name) })
    }

    private fun newAssetDialog(title: String, base: String, ext: String, content: (String) -> String, open: Boolean = true) {
        val f = field(base)
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(f, lp(MATCH, WRAP)) })
            .setPositiveButton("Create") { _, _ ->
                var n = f.text.toString().trim().replace(Regex("[^A-Za-z0-9_\\-]"), "").ifBlank { base }
                if (!n.endsWith(".$ext")) n += ".$ext"
                n = project.uniqueAssetName(n)
                project.writeAsset(n, content(n))
                refreshAssets()
                if (open) openScript(n)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun newScriptDialog(onCreated: (String) -> Unit) {
        val f = field("NewBehaviour")
        MaterialAlertDialogBuilder(this)
            .setTitle("New Script")
            .setView(LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(f, lp(MATCH, WRAP)) })
            .setPositiveButton("Create") { _, _ ->
                var n = f.text.toString().trim().replace(Regex("[^A-Za-z0-9_\\-]"), "").ifBlank { "NewBehaviour" }
                if (!n.endsWith(".js")) n += ".js"
                n = project.uniqueAssetName(n)
                project.writeAsset(n, Templates.NEW_SCRIPT)
                refreshAssets()
                onCreated(n)
            }
            .setNeutralButton("GDScript") { _, _ ->
                var n = f.text.toString().trim().replace(Regex("[^A-Za-z0-9_\\-]"), "").ifBlank { "NewScript" }
                if (!n.endsWith(".gd")) n += ".gd"
                n = project.uniqueAssetName(n)
                project.writeAsset(n, "func update(dt):\n    pass\n")
                refreshAssets()
                onCreated(n)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ================================================================== play / undo / save
    private fun startPlay() {
        currentFocus?.let { it.clearFocus(); hideKeyboard(it) }
        consoleText.text = ""
        engine.play()
    }

    private fun undo() {
        if (engine.mode != Engine.Mode.EDIT) return
        val r = history.undo(state.selectedId) ?: run { toast("Nothing to undo"); return }
        applySnapshot(r.first, r.second)
    }

    private fun redo() {
        if (engine.mode != Engine.Mode.EDIT) return
        val r = history.redo(state.selectedId) ?: run { toast("Nothing to redo"); return }
        applySnapshot(r.first, r.second)
    }

    private fun applySnapshot(s: Scene, sel: Long) {
        synchronized(engine.lock) { s.name = engine.scene.name; engine.replaceScene(s) }
        state.selectedId = sel
        refreshHierarchy(); inspector.rebuild()
    }

    private fun saveScene(silent: Boolean = false) {
        if (engine.mode != Engine.Mode.EDIT) { if (!silent) toast("Stop play mode before saving"); return }
        try {
            synchronized(engine.lock) { project.saveScene(engine.scene) }
            project.saveMeta()
            history.dirty = false
            if (!silent) toast("Saved ${engine.scene.name}") else appendConsole(0, "Auto-saved ${engine.scene.name}")
        } catch (e: Exception) { toast("Save failed: ${e.message}") }
    }

    // ================================================================== object creation
    private fun addObjectMenu(anchor: View) {
        if (engine.mode != Engine.Mode.EDIT) { toast("Stop play mode to add objects"); return }
        val pm = PopupMenu(this, anchor)
        pm.menu.add("Empty"); pm.menu.add("Empty Child")
        val m2 = pm.menu.addSubMenu("2D Object")
        listOf("Square", "Circle", "Triangle", "Tilemap", "Text", "UI Text", "Camera", "Particle System",
            "Physics Box", "Physics Ball", "Static Platform", "Trigger Zone", "Animated Sprite").forEach { m2.add(it) }
        val m3 = pm.menu.addSubMenu("3D Object")
        listOf("Cube", "Sphere", "Plane", "Cylinder", "Cone", "Torus", "Capsule", "Pyramid",
            "Physics Cube 3D", "Physics Sphere 3D", "Ground 3D", "3D Camera", "Directional Light", "Point Light").forEach { m3.add(it) }
        val p2 = pm.menu.addSubMenu("Prefabs 2D (water, fire, weather…)")
        com.sengine.project.Prefabs.PREFABS_2D.forEach { p2.add(it) }
        val p3 = pm.menu.addSubMenu("Prefabs 3D (lake, campfire, lamps…)")
        com.sengine.project.Prefabs.PREFABS_3D.forEach { p3.add(it) }
        pm.setOnMenuItemClickListener {
            if (!it.hasSubMenu()) {
                val t = it.title.toString()
                if (t in com.sengine.project.Prefabs.PREFABS_2D || t in com.sengine.project.Prefabs.PREFABS_3D) createPrefab(t) else createObject(t)
            }
            true
        }
        pm.show()
    }

    private fun createPrefab(name: String) {
        history.record(state.selectedId)
        val go = synchronized(engine.lock) {
            val x = if (state.mode3D) snap(state.orbitX) else snap(state.view.cx)
            val y = if (state.mode3D) snap(state.orbitY) else snap(state.view.cy)
            val z = if (state.mode3D) snap(state.orbitZ) else 0f
            com.sengine.project.Prefabs.create(engine.scene, name, x, y, z)
        }
        refreshHierarchy()
        select(go.id)
    }

    private fun createObject(kind: String) {
        history.record(state.selectedId)
        val go = synchronized(engine.lock) {
            val scene = engine.scene
            val parent = if (kind == "Empty Child") scene.findById(state.selectedId) else null
            val g = scene.create(if (kind == "Empty Child") "GameObject" else kind, parent)
            if (parent == null) {
                if (state.mode3D) { g.x = snap(state.orbitX); g.y = snap(state.orbitY); g.z = snap(state.orbitZ) }
                else { g.x = snap(state.view.cx); g.y = snap(state.view.cy) }
            }
            val meshKinds = MeshRenderer.MESHES
            when (kind) {
                in meshKinds -> g.add(MeshRenderer().also { it.mesh = meshKinds.indexOf(kind) }).also { if (kind == "Plane") { g.scaleX = 10f; g.scaleZ = 10f } }
                "Physics Cube 3D" -> { g.y += 3f; g.add(MeshRenderer().also { it.color = 0xFFFFB74D.toInt() }); g.add(Collider3D()); g.add(Rigidbody3D()) }
                "Physics Sphere 3D" -> { g.y += 3f; g.add(MeshRenderer().also { it.mesh = 1; it.color = 0xFF4FC3F7.toInt() }); g.add(Collider3D().also { it.shape = 1 }); g.add(Rigidbody3D().also { it.bounciness = 0.5f }) }
                "Ground 3D" -> { g.scaleX = 20f; g.scaleY = 0.5f; g.scaleZ = 20f; g.y = -0.25f; g.add(MeshRenderer().also { it.color = 0xFF6D8B5A.toInt() }); g.add(Collider3D()) }
                "3D Camera" -> { g.y = 3f; g.z = 10f; g.rotX = -12f; g.add(Camera3D()) }
                "Directional Light" -> { g.rotX = -50f; g.rotY = 30f; g.add(Light()) }
                "Point Light" -> { g.y += 2f; g.add(Light().also { it.kind = 1; it.color = 0xFFFFC870.toInt() }) }
                "Tilemap" -> { g.add(Tilemap()) }
                "UI Text" -> { g.x = 0f; g.y = 4f; g.add(TextRenderer().also { it.screenSpace = true; it.text = "Score: 0" }) }
                "Animated Sprite" -> { g.add(SpriteRenderer()); g.add(Animator()) }
                "Empty", "Empty Child" -> g.name = scene.uniqueName("GameObject")
                "Square" -> g.add(SpriteRenderer())
                "Circle" -> g.add(SpriteRenderer().also { it.shape = 1 })
                "Triangle" -> g.add(SpriteRenderer().also { it.shape = 2 })
                "Text" -> g.add(TextRenderer())
                "Camera" -> g.add(Camera2D())
                "Particle System" -> g.add(ParticleEmitter())
                "Physics Box" -> { g.add(SpriteRenderer().also { it.color = 0xFFFFB74D.toInt() }); g.add(Collider2D()); g.add(Rigidbody2D()) }
                "Physics Ball" -> {
                    g.add(SpriteRenderer().also { it.shape = 1; it.color = 0xFF4FC3F7.toInt() })
                    g.add(Collider2D().also { it.shape = 1 }); g.add(Rigidbody2D().also { it.bounciness = 0.6f })
                }
                "Static Platform" -> { g.scaleX = 4f; g.scaleY = 0.5f; g.add(SpriteRenderer().also { it.color = 0xFF8D6E63.toInt() }); g.add(Collider2D()) }
                "Trigger Zone" -> { g.scaleX = 2f; g.scaleY = 2f; g.add(Collider2D().also { it.isTrigger = true }) }
            }
            g
        }
        refreshHierarchy()
        select(go.id)
    }

    private fun snap(v: Float) = Math.round(v * 2f) / 2f

    private fun objectMenu(go: GameObject, anchor: View) {
        if (engine.mode != Engine.Mode.EDIT) { select(go.id); return }
        val pm = PopupMenu(this, anchor)
        listOf("Rename", "Duplicate", "Delete", "Create Child", "Move Up", "Move Down", "Unparent", "Frame in View").forEach { pm.menu.add(it) }
        pm.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Rename" -> renameDialog(go)
                "Frame in View" -> controller.frame(go)
                else -> {
                    history.record(state.selectedId)
                    var newSel = state.selectedId
                    synchronized(engine.lock) {
                        val scene = engine.scene
                        when (item.title) {
                            "Duplicate" -> newSel = scene.duplicate(go).also { it.x += 0.5f; it.y -= 0.5f }.id
                            "Delete" -> { scene.remove(go); if (newSel == go.id) newSel = -1 }
                            "Create Child" -> newSel = scene.create("GameObject", go).id
                            "Move Up" -> scene.moveInOrder(go, -1)
                            "Move Down" -> scene.moveInOrder(go, 1)
                            "Unparent" -> InspectorPanel.reparentKeepWorld(go, null)
                        }
                        Unit
                    }
                    refreshHierarchy()
                    select(newSel)
                }
            }
            true
        }
        pm.show()
    }

    private fun renameDialog(go: GameObject) {
        val f = field(go.name)
        MaterialAlertDialogBuilder(this)
            .setTitle("Rename")
            .setView(LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(f, lp(MATCH, WRAP)) })
            .setPositiveButton("OK") { _, _ ->
                history.record(state.selectedId)
                synchronized(engine.lock) { go.name = f.text.toString().ifBlank { go.name } }
                refreshHierarchy(); inspector.rebuild()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ================================================================== menus & scenes
    private fun openHelp(topic: String?) {
        val i = Intent(this, HelpActivity::class.java).putExtra("project", project.name)
        if (topic != null) i.putExtra("topic", topic)
        startActivity(i)
    }

    private fun mainMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        val entries = listOf(
            "Save Scene", "Scenes…", "Build & Run (fullscreen)", "Build APK…", "AI Agent", "Tilemap Editor", "Asset Store", "Sprite Studio", "Texture Studio", "UI Creator", "Animation Editor", "Music Editor", "3D Model Editor", "Controls Editor", "Game Doctor", "Help Center", "Export Project (.zip)",
            "Toggle Profiler", "Toggle Grid", "Toggle Colliders", "Snap: " + if (controller.snap) "ON" else "OFF", "Script API Reference", "About S Engine Studio"
        )
        entries.forEach { pm.menu.add(it) }
        pm.setOnMenuItemClickListener { item ->
            val t = item.title.toString()
            when {
                t == "Save Scene" -> saveScene()
                t == "Scenes…" -> scenesDialog()
                t.startsWith("Build & Run") -> { saveScene(silent = true); startActivity(Intent(this, PlayerActivity::class.java).putExtra("project", project.name)) }
                t == "Build APK…" -> { saveScene(silent = true); startActivity(Intent(this, BuildActivity::class.java).putExtra("project", project.name)) }
                t == "Asset Store" -> startActivity(Intent(this, AssetStoreActivity::class.java).putExtra("project", project.name))
                t == "Tilemap Editor" -> startActivity(Intent(this, TilemapEditorActivity::class.java).putExtra("project", project.name).putExtra("scene", engine.scene.name))
                t == "Animation Editor" -> openAnimationEditor(null)
                t == "AI Agent" -> { saveScene(silent = true); startActivity(Intent(this, AgentActivity::class.java)) }
                t == "Sprite Studio" -> { saveScene(silent = true); startActivity(Intent(this, SpriteStudioActivity::class.java).putExtra("project", project.name)) }
                t == "Texture Studio" -> { saveScene(silent = true); startActivity(Intent(this, TextureStudioActivity::class.java).putExtra("project", project.name)) }
                t == "UI Creator" -> {
                    saveScene(silent = true); reloadSceneOnResume = true
                    startActivity(Intent(this, UICreatorActivity::class.java).putExtra("project", project.name).putExtra("scene", engine.scene.name))
                }
                t == "Music Editor" -> { saveScene(silent = true); startActivity(Intent(this, MusicEditorActivity::class.java).putExtra("project", project.name)) }
                t == "3D Model Editor" -> { saveScene(silent = true); startActivity(Intent(this, ModelEditorActivity::class.java).putExtra("project", project.name)) }
                t == "Controls Editor" -> { saveScene(silent = true); startActivity(Intent(this, ControlsEditorActivity::class.java).putExtra("project", project.name)) }
                t == "Game Doctor" -> { saveScene(silent = true); openHelp("doctor") }
                t == "Help Center" -> openHelp(null)
                t.contains("Profiler") -> state.showProfiler = !state.showProfiler
                t.startsWith("Export") -> { saveScene(silent = true); exportLauncher.launch("${project.name}.zip") }
                t.contains("Grid") -> state.showGrid = !state.showGrid
                t.contains("Colliders") -> state.showColliders = !state.showColliders
                t.startsWith("Snap") -> controller.snap = !controller.snap
                t == "Script API Reference" -> showText("Script API", ScriptEditorActivity.API_DOC)
                t.startsWith("About") -> showText("About S Engine Godot Studio",
                    "S Engine Godot 4 Studio Edition\n\nA 2D & 3D game studio with Godot multi-dock workspace.\n\n" +
                        "• Tilemaps, Layers & Groups, GDScript / Lua / JS scripting\n" +
                        "• Built-in multi-provider AI game dev agent\n" +
                        "• 3D models, shaders, particle systems, audio editor\n" +
                        "• Directly builds installable Android APKs")
            }
            true
        }
        pm.show()
    }

    private fun showText(title: String, text: String) {
        val tv = label(text, 12f).apply { typeface = Typeface.MONOSPACE; setPadding(dp(18), dp(10), dp(18), dp(10)); setTextIsSelectable(true) }
        MaterialAlertDialogBuilder(this).setTitle(title).setView(ScrollView(this).apply { addView(tv) }).setPositiveButton("Close", null).show()
    }

    private fun scenesDialog() {
        if (engine.mode != Engine.Mode.EDIT) { toast("Stop play mode first"); return }
        saveScene(silent = true)
        val scenes = project.listScenes()
        val labels = scenes.map { s ->
            s + (if (s == engine.scene.name) "   (open)" else "") + (if (s == project.startScene) "   ★ start" else "")
        } + "+ New Scene"
        MaterialAlertDialogBuilder(this)
            .setTitle("Scenes")
            .setItems(labels.toTypedArray()) { _, i ->
                if (i == scenes.size) newSceneDialog() else sceneActions(scenes[i])
            }
            .show()
    }

    private fun sceneActions(name: String) {
        val actions = arrayOf("Open", "Set as Start Scene", "Duplicate", "Delete")
        MaterialAlertDialogBuilder(this)
            .setTitle(name)
            .setItems(actions) { _, i ->
                when (i) {
                    0 -> openScene(name)
                    1 -> { project.startScene = name; project.saveMeta(); toast("$name is start scene") }
                    2 -> {
                        var n = "$name Copy"; var k = 2
                        while (project.sceneExists(n)) n = "$name Copy ${k++}"
                        project.sceneFile(name).copyTo(project.sceneFile(n))
                        toast("Created $n")
                    }
                    3 -> {
                        if (name == engine.scene.name) toast("Can't delete open scene")
                        else { project.deleteScene(name); toast("Deleted $name") }
                    }
                }
            }
            .show()
    }

    private fun newSceneDialog() {
        val f = field("Level2")
        MaterialAlertDialogBuilder(this)
            .setTitle("New Scene")
            .setView(LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(f, lp(MATCH, WRAP)) })
            .setPositiveButton("Create") { _, _ ->
                val n = ProjectManager.sanitize(f.text.toString())
                if (n.isBlank() || project.sceneExists(n)) { toast("Invalid name"); return@setPositiveButton }
                val s = Scene(n)
                s.create("Main Camera").add(Camera2D())
                project.saveScene(s)
                openScene(n)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openScene(name: String) {
        val s = project.loadScene(name)
        synchronized(engine.lock) { engine.replaceScene(s) }
        history.clear(); history.dirty = false
        state.selectedId = -1
        refreshHierarchy(); inspector.rebuild(); updateTitle()
        appendConsole(0, "Opened scene $name")
    }

    // ================================================================== assets
    fun refreshAssets() {
        if (!::assetsRow.isInitialized) return
        assetsRow.removeAllViews()
        val assets = project.listAssets()
        if (assets.isEmpty()) assetsRow.addView(label("No assets yet.", 12f, C.DIM).apply { setPadding(dp(8), dp(20), 0, 0) })
        for (name in assets) assetsRow.addView(assetCard(name), lp(dp(88), MATCH).margins(dp(3), 0, dp(3), 0))
    }

    private fun assetCard(name: String): View {
        val kind = AssetKind.of(name)
        val card = vbox().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = round(C.PANEL2, dp(6).toFloat())
            setPadding(dp(4), dp(6), dp(4), dp(4))
        }
        if (kind == AssetKind.TEXTURE) {
            val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                iv.setImageBitmap(BitmapFactory.decodeFile(project.assetFile(name).absolutePath, opts))
            } catch (_: Throwable) {}
            card.addView(iv, lp(dp(56), dp(48)))
        } else {
            val (glyph, color) = when (kind) {
                AssetKind.SCRIPT -> (if (name.endsWith(".gd")) "GD" to 0xFF4FC3F7.toInt() else if (name.endsWith(".lua")) "LUA" to 0xFF81D4FA.toInt() else if (name.endsWith(".bp")) "BP" to 0xFF4FC3F7.toInt() else "JS" to C.YELLOW)
                AssetKind.SOUND -> "♪" to C.GREEN
                AssetKind.SHADER -> "GLSL" to 0xFFE040FB.toInt()
                AssetKind.ANIMATION -> "▶▶" to 0xFFFF8A65.toInt()
                AssetKind.MODEL -> "3D" to 0xFF80CBC4.toInt()
                else -> "?" to C.DIM
            }
            card.addView(label(glyph, 20f, color, true).apply { gravity = Gravity.CENTER }, lp(dp(56), dp(48)))
        }
        card.addView(label(name, 10f, C.TEXT).apply { gravity = Gravity.CENTER; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
        card.setOnClickListener { assetMenu(name, kind, it) }
        return card
    }

    private fun assetMenu(name: String, kind: AssetKind?, anchor: View) {
        val pm = PopupMenu(this, anchor)
        val sel = synchronized(engine.lock) { engine.scene.findById(state.selectedId) }
        when (kind) {
            AssetKind.SCRIPT -> { pm.menu.add("Edit"); if (sel != null) pm.menu.add("Attach to ${sel.name}") }
            AssetKind.TEXTURE -> { pm.menu.add("Create Sprite"); pm.menu.add("Edit in Sprite Studio"); if (sel != null) pm.menu.add("Assign to ${sel.name}") }
            AssetKind.SOUND -> { pm.menu.add("Preview"); if (sel != null) pm.menu.add("Add AudioSource to ${sel.name}") }
            AssetKind.SHADER -> { pm.menu.add("Edit"); if (sel != null) pm.menu.add("Use shader on ${sel.name}") }
            AssetKind.ANIMATION -> { pm.menu.add("Edit"); if (sel != null) pm.menu.add("Play on ${sel.name}") }
            AssetKind.MODEL -> { if (name.endsWith(".smodel")) pm.menu.add("Edit"); pm.menu.add("Create 3D Model Object"); if (sel != null) pm.menu.add("Use model on ${sel.name}") }
            AssetKind.SONG -> { pm.menu.add("Edit"); if (sel != null) pm.menu.add("Add AudioSource to ${sel.name}") }
            AssetKind.DATA -> pm.menu.add("Edit")
            null -> {}
        }
        pm.menu.add("Delete")
        pm.setOnMenuItemClickListener { item ->
            val t = item.title.toString()
            when {
                t == "Edit" -> openScript(name)
                t == "Preview" -> try {
                    android.media.MediaPlayer().apply {
                        setDataSource(project.assetFile(name).absolutePath); setOnCompletionListener { it.release() }; prepare(); start()
                    }
                } catch (e: Exception) { toast("Can't play: ${e.message}") }
                t == "Edit in Sprite Studio" -> startActivity(Intent(this, SpriteStudioActivity::class.java).putExtra("project", project.name).putExtra("file", name))
                t == "Create Sprite" -> {
                    history.record(state.selectedId)
                    val go = synchronized(engine.lock) {
                        val g = engine.scene.create(name.substringBeforeLast('.'))
                        g.x = snap(state.view.cx); g.y = snap(state.view.cy)
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(project.assetFile(name).absolutePath, opts)
                        if (opts.outWidth > 0) g.scaleY = opts.outHeight.toFloat() / opts.outWidth
                        g.add(SpriteRenderer().also { it.texture = name })
                        g
                    }
                    refreshHierarchy(); select(go.id)
                }
                sel != null && t.startsWith("Attach") -> {
                    history.record(state.selectedId)
                    synchronized(engine.lock) { sel.add(ScriptComponent().also { it.script = name }) }
                    inspector.rebuild()
                }
                sel != null && t.startsWith("Assign") -> {
                    history.record(state.selectedId)
                    synchronized(engine.lock) {
                        (sel.getAny<SpriteRenderer>() ?: sel.add(SpriteRenderer())).texture = name
                    }
                    inspector.rebuild()
                }
                sel != null && t.startsWith("Use shader") -> {
                    history.record(state.selectedId)
                    synchronized(engine.lock) {
                        val mr = sel.getAny<MeshRenderer>()
                        if (mr != null) mr.shader = name else (sel.getAny<SpriteRenderer>() ?: sel.add(SpriteRenderer())).shader = name
                    }
                    inspector.rebuild()
                }
                sel != null && t.startsWith("Play on") -> {
                    history.record(state.selectedId)
                    synchronized(engine.lock) {
                        if (sel.getAny<SpriteRenderer>() == null) sel.add(SpriteRenderer())
                        (sel.getAny<Animator>() ?: sel.add(Animator())).clip = name
                    }
                    inspector.rebuild()
                }
                t == "Create 3D Model Object" -> {
                    history.record(state.selectedId)
                    val go = synchronized(engine.lock) {
                        val g = engine.scene.create(name.substringBeforeLast('.'))
                        g.x = snap(state.orbitX); g.y = snap(state.orbitY); g.z = snap(state.orbitZ)
                        g.add(MeshRenderer().also { it.mesh = MeshRenderer.MESHES.size - 1; it.model = name })
                        g
                    }
                    if (!state.mode3D) setMode3D(true)
                    refreshHierarchy(); select(go.id)
                }
                sel != null && t.startsWith("Use model") -> {
                    history.record(state.selectedId)
                    synchronized(engine.lock) {
                        val mr = sel.getAny<MeshRenderer>() ?: sel.add(MeshRenderer())
                        mr.mesh = MeshRenderer.MESHES.size - 1; mr.model = name
                    }
                    inspector.rebuild()
                }
                sel != null && t.startsWith("Add AudioSource") -> {
                    history.record(state.selectedId)
                    synchronized(engine.lock) { sel.add(AudioSource().also { it.clip = name }) }
                    inspector.rebuild()
                }
                t == "Delete" -> MaterialAlertDialogBuilder(this)
                    .setTitle("Delete $name?")
                    .setPositiveButton("Delete") { _, _ -> project.assetFile(name).delete(); refreshAssets() }
                    .setNegativeButton("Cancel", null).show()
            }
            true
        }
        pm.show()
    }

    private fun importAsset(uri: Uri) {
        try {
            var display = "asset"
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) display = it.getString(0)
            }
            var n = display.replace(Regex("[^A-Za-z0-9_.\\-]"), "_")
            if (AssetKind.of(n) == null) {
                val mime = contentResolver.getType(uri) ?: ""
                n += when {
                    mime.contains("png") -> ".png"
                    mime.contains("jpeg") || mime.contains("jpg") -> ".jpg"
                    mime.contains("webp") -> ".webp"
                    mime.contains("ogg") -> ".ogg"
                    mime.contains("mpeg") || mime.contains("mp3") -> ".mp3"
                    mime.contains("wav") -> ".wav"
                    importKind == AssetKind.MODEL -> ".obj"
                    importKind == AssetKind.SOUND -> ".ogg"
                    else -> ".png"
                }
            }
            n = project.uniqueAssetName(n)
            project.assetsDir.mkdirs()
            contentResolver.openInputStream(uri)!!.use { input -> project.assetFile(n).outputStream().use { input.copyTo(it) } }
            refreshAssets()
            toast("Imported $n")
        } catch (e: Exception) {
            toast("Import failed: ${e.message}")
        }
    }

    // ================================================================== console & input
    private fun appendConsole(level: Int, msg: String) {
        if (!::consoleText.isInitialized) return
        val color = when (level) { 2 -> "#FF7B72"; 1 -> "#E3B341"; else -> "#CFD2D6" }
        val line = android.text.Html.fromHtml("<font color='$color'>${android.text.TextUtils.htmlEncode(msg)}</font><br>", android.text.Html.FROM_HTML_MODE_LEGACY)
        consoleText.append(line)
        if (consoleText.text.length > 20000) consoleText.text = consoleText.text.subSequence(consoleText.text.length - 15000, consoleText.text.length)
        consoleScroll.post { consoleScroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (engine.mode != Engine.Mode.EDIT && keyCode != KeyEvent.KEYCODE_BACK) {
            engine.input.keys.add(keyCode); return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        engine.input.keys.remove(keyCode)
        return super.onKeyUp(keyCode, event)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
