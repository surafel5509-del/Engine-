package com.sengine.ui

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sengine.engine.Engine
import com.sengine.engine.render.SceneRenderer
import com.sengine.project.ProjectManager

/** Runs a project full-screen, exactly like an exported game. */
class PlayerActivity : AppCompatActivity() {

    private lateinit var engine: Engine
    private lateinit var glView: GLSurfaceView
    private lateinit var fpsText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val fpsTick = object : Runnable {
        override fun run() {
            fpsText.text = "${engine.fps.toInt()} FPS"
            handler.postDelayed(this, 500)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val standalone = intent.getBooleanExtra("standalone", false)
        val project = intent.getStringExtra("projectDir")?.let { com.sengine.project.Project(java.io.File(it)) }
            ?: ProjectManager.open(this, intent.getStringExtra("project") ?: run { finish(); return })
        val opts = if (standalone) com.sengine.export.GameRuntime.buildInfo(this) ?: org.json.JSONObject() else org.json.JSONObject()
        requestedOrientation = when (opts.optString("orientation", "")) {
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            "sensor" -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else -> if (project.orientation == 1) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        fullscreen = opts.optBoolean("fullscreen", true)
        val startOverride = opts.optString("startScene", "").takeIf { it.isNotBlank() && project.sceneExists(it) }
        val sceneName = intent.getStringExtra("scene") ?: startOverride ?: project.startScene
        engine = Engine(project, project.loadScene(sceneName))
        engine.platform = object : com.sengine.engine.Platform {
            override fun vibrate(ms: Int) {
                try {
                    val v = getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                    v?.vibrate(android.os.VibrationEffect.createOneShot(ms.toLong().coerceIn(1, 2000), android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } catch (_: Throwable) {}
            }
            override fun quit() { handler.post { finish() } }
            override fun openUrl(url: String) { handler.post { try { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) } catch (_: Exception) {} } }
            override fun toast(text: String) { handler.post { android.widget.Toast.makeText(this@PlayerActivity, text, android.widget.Toast.LENGTH_SHORT).show() } }
        }
        if (opts.optBoolean("keepScreenOn", true)) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        engine.listeners.add(object : Engine.Listener {
            override fun onLog(level: Int, message: String) {
                if (level >= 2) handler.post { android.widget.Toast.makeText(this@PlayerActivity, message, android.widget.Toast.LENGTH_SHORT).show() }
            }
        })

        val root = FrameLayout(this)
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(SceneRenderer(engine, null))
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        glView.setOnTouchListener { v, e -> forwardTouch(v, e); true }
        root.addView(glView)
        root.addView(GameControlsView(this) { engine.input }.also { it.projectLayout = project.loadControls() })
        fpsText = label("", 11f, 0x99FFFFFF.toInt()).apply { setPadding(dp(10), dp(6), 0, 0) }
        if (!standalone) {
            root.addView(fpsText, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START))
            root.addView(button("✕", 0x55000000) { finish() },
                FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(8), dp(8), 0) })
        }
        if (standalone && opts.optBoolean("showFps", false)) root.addView(fpsText, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START))
        if (standalone && opts.optBoolean("splash", true)) addSplash(root, opts)
        setContentView(root)
        hideSystemUi()
        engine.play()
    }

    private fun forwardTouch(v: View, e: MotionEvent) {
        val inp = engine.input
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> inp.touch(0, e.x, e.y)
            MotionEvent.ACTION_MOVE -> inp.touch(1, e.x, e.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> inp.touch(2, e.x, e.y)
        }
    }

    private var fullscreen = true

    /** Branded splash overlay for exported games (icon, title, "Made with S Engine"), fades out. */
    private fun addSplash(root: FrameLayout, opts: org.json.JSONObject) {
        val splash = vbox().apply {
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(opts.optInt("splashColor", 0xFF0E1120.toInt()), 0xFF000000.toInt()))
            isClickable = true
        }
        val icon = android.widget.ImageView(this).apply {
            try { setImageResource(com.sengine.R.mipmap.ic_game) } catch (_: Throwable) {}
        }
        splash.addView(icon, android.widget.LinearLayout.LayoutParams(dp(110), dp(110)))
        splash.addView(label(opts.optString("name", "Game"), 26f, C.TEXT, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(4)) })
        val sub = opts.optString("splashText", "").ifBlank { "Made with S Engine" }
        splash.addView(label(sub, 13f, C.DIM).apply { gravity = Gravity.CENTER })
        root.addView(splash, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val ms = (opts.optDouble("splashSeconds", 1.8) * 1000).toLong().coerceIn(300, 8000)
        handler.postDelayed({ splash.animate().alpha(0f).setDuration(450).withEndAction { root.removeView(splash) }.start() }, ms)
    }

    private fun hideSystemUi() {
        if (!fullscreen) return
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
        engine.input.keys.add(keyCode); return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        engine.input.keys.remove(keyCode); return super.onKeyUp(keyCode, event)
    }

    override fun onResume() { super.onResume(); glView.onResume(); handler.post(fpsTick); hideSystemUi() }
    override fun onPause() { super.onPause(); glView.onPause(); handler.removeCallbacks(fpsTick) }

    override fun onDestroy() {
        super.onDestroy()
        if (::engine.isInitialized) engine.release()
    }
}
