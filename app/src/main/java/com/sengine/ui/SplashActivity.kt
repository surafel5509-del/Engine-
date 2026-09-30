package com.sengine.ui

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * S Engine splash screen (Unity style). Shown once when the editor app opens: the S Engine
 * logo scales in, a loading bar fills, then the dashboard appears. Exported games use the
 * same visual language inside [PlayerActivity.addSplash].
 */
class SplashActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Exported games skip the editor splash entirely (their player has its own splash).
        com.sengine.export.GameRuntime.standaloneProject(this)?.let { game ->
            startActivity(Intent(this, PlayerActivity::class.java).putExtra("projectDir", game.dir.absolutePath).putExtra("standalone", true))
            finish()
            return
        }

        val root = android.widget.FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        val box = vbox().apply { gravity = Gravity.CENTER }
        root.addView(box, android.widget.FrameLayout.LayoutParams(MATCH, MATCH))

        // logo mark: rounded gradient square with the engine glyph
        val mark = ImageView(this).apply {
            setImageDrawable(Icons.drawable(this@SplashActivity, "rocket", 0xFF000000.toInt(), 34))
            scaleType = ImageView.ScaleType.CENTER
            background = gradient(0xFFFFFFFF.toInt(), 0xFFB9C4D8.toInt(), dp(22).toFloat())
            alpha = 0f
            scaleX = 0.6f; scaleY = 0.6f
        }
        box.addView(mark, lp(dp(96), dp(96)))

        val title = label("S ENGINE", 30f, 0xFFFFFFFF.toInt(), true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
            letterSpacing = 0.12f
            alpha = 0f
        }
        box.addView(title, lp(WRAP, WRAP))

        box.addView(label("2D & 3D GAME ENGINE  •  ULTIMATE STUDIO", 11f, C.DIM).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
            letterSpacing = 0.18f
            alpha = 0f
        }, lp(WRAP, WRAP))

        // loading bar
        val barBack = LinearLayout(this).apply {
            background = round(0x22FFFFFF, dp(6).toFloat())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        val barFill = TextView(this).apply { background = gradient(C.ACCENT, 0xFF7C4DFF.toInt(), dp(5).toFloat()) }
        barBack.addView(barFill, lp(0, dp(8), 1f))
        val barHolder = android.widget.FrameLayout(this)
        barHolder.addView(barBack, android.widget.FrameLayout.LayoutParams(dp(190), dp(14)))
        barHolder.alpha = 0f
        box.addView(barHolder, lp(WRAP, WRAP).margins(0, dp(28), 0, 0))

        box.addView(label("v7.0  •  made with ❤  for mobile game creators", 10f, 0xFF6B7280.toInt()).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }, lp(WRAP, WRAP))

        setContentView(root)

        mark.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(520)
            .setInterpolator(AccelerateDecelerateInterpolator()).start()
        title.animate().alpha(1f).setStartDelay(180).setDuration(420).start()
        barHolder.animate().alpha(1f).setStartDelay(320).setDuration(300).start()

        val anim = ValueAnimator.ofFloat(0.04f, 1f).apply {
            duration = 1250
            startDelay = 350
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val v = it.animatedValue as Float
                barFill.layoutParams = lp((190.dpF() * v).toInt().coerceAtLeast(2), dp(8))
                barFill.layoutParams.width = ((190.dpF() - 4.dpF()) * v).toInt().coerceAtLeast(2)
                barFill.requestLayout()
            }
        }
        anim.start()

        handler.postDelayed({
            startActivity(Intent(this, ProjectsActivity::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 1900)
    }

    private fun Float.dpF(): Float = this * resources.displayMetrics.density
}
