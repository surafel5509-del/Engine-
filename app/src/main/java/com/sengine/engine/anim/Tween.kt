package com.sengine.engine.anim

import com.sengine.engine.core.GameObject
import kotlin.math.pow

/**
 * Lightweight tween engine (S Engine 7). Animates transforms and opacity of GameObjects with
 * easing curves, usable from the editor Timeline tool and from scripts via `tween.*`.
 * Runs on the GL thread inside [com.sengine.engine.Engine.tick].
 */
class TweenManager {

    enum class Prop { X, Y, Z, ROT, ROT_X, ROT_Y, SCALE, SCALE_X, SCALE_Y, SCALE_Z, ALPHA }

    interface Ease { fun apply(t: Float): Float }

    class Tween(
        val go: GameObject,
        val prop: Prop,
        val from: Float,
        val to: Float,
        val duration: Float,
        val ease: Ease,
        val onComplete: (() -> Unit)? = null,
    ) {
        var t = 0f
        var done = false
        var delayed = 0f
    }

    private val active = ArrayList<Tween>()
    val count: Int get() = active.size

    val easings: Map<String, Ease> = linkedMapOf(
        "linear" to Linear,
        "sineIn" to SineIn, "sineOut" to SineOut, "sineInOut" to SineInOut,
        "quadIn" to QuadIn, "quadOut" to QuadOut, "quadInOut" to QuadInOut,
        "cubicIn" to CubicIn, "cubicOut" to CubicOut, "cubicInOut" to CubicInOut,
        "backIn" to BackIn, "backOut" to BackOut, "backInOut" to BackInOut,
        "elasticOut" to ElasticOut, "bounceOut" to BounceOut,
    )

    fun ease(name: String): Ease = easings[name] ?: Linear

    fun clear() = active.clear()

    fun kill(go: GameObject) {
        active.removeAll { it.go === go }
    }

    fun add(go: GameObject, prop: Prop, from: Float, to: Float, duration: Float, ease: Ease, delay: Float = 0f, onComplete: (() -> Unit)? = null): Tween {
        val tw = Tween(go, prop, from, to, duration.coerceAtLeast(0.0001f), ease, onComplete)
        tw.delayed = delay
        active += tw
        return tw
    }

    /** Convenience: tweens from the object's current value. */
    fun moveTo(go: GameObject, x: Float? = null, y: Float? = null, z: Float? = null, duration: Float, ease: Ease, delay: Float = 0f, onComplete: (() -> Unit)? = null) {
        if (x != null) add(go, Prop.X, go.x, x, duration, ease, delay, null)
        if (y != null) add(go, Prop.Y, go.y, y, duration, ease, delay, null)
        if (z != null) add(go, Prop.Z, go.z, z, duration, ease, delay, null)
        if (onComplete != null && x == null && y == null && z == null) onComplete()
        else if (onComplete != null) add(go, Prop.ALPHA, 0f, 0f, 0.0001f, ease, delay + duration, onComplete)
    }

    fun scaleTo(go: GameObject, s: Float, duration: Float, ease: Ease, delay: Float = 0f, onComplete: (() -> Unit)? = null) {
        add(go, Prop.SCALE, go.scaleX, s, duration, ease, delay, onComplete)
    }

    fun rotateTo(go: GameObject, degrees: Float, duration: Float, ease: Ease, delay: Float = 0f, onComplete: (() -> Unit)? = null) {
        add(go, Prop.ROT, go.rotation, degrees, duration, ease, delay, onComplete)
    }

    fun fadeTo(go: GameObject, alpha: Float, duration: Float, ease: Ease, delay: Float = 0f, onComplete: (() -> Unit)? = null) {
        val sr = go.getAny<com.sengine.engine.core.SpriteRenderer>()
        val from = sr?.let { com.sengine.engine.render.GL.a(it.color) } ?: 1f
        add(go, Prop.ALPHA, from, alpha.coerceIn(0f, 1f), duration, ease, delay, onComplete)
    }

    fun update(dt: Float) {
        if (active.isEmpty()) return
        val it = active.iterator()
        while (it.hasNext()) {
            val tw = it.next()
            if (tw.go.destroyed) { it.remove(); continue }
            if (tw.delayed > 0f) { tw.delayed -= dt; continue }
            tw.t += dt
            val k = (tw.t / tw.duration).coerceIn(0f, 1f)
            val v = tw.from + (tw.to - tw.from) * tw.ease.apply(k)
            apply(tw, v)
            if (k >= 1f) {
                tw.done = true
                it.remove()
                try { tw.onComplete?.invoke() } catch (_: Throwable) {}
            }
        }
    }

    private fun apply(tw: Tween, v: Float) {
        val go = tw.go
        when (tw.prop) {
            Prop.X -> go.x = v
            Prop.Y -> go.y = v
            Prop.Z -> go.z = v
            Prop.ROT -> go.rotation = v
            Prop.ROT_X -> go.rotX = v
            Prop.ROT_Y -> go.rotY = v
            Prop.SCALE -> { go.scaleX = v; go.scaleY = v; go.scaleZ = v }
            Prop.SCALE_X -> go.scaleX = v
            Prop.SCALE_Y -> go.scaleY = v
            Prop.SCALE_Z -> go.scaleZ = v
            Prop.ALPHA -> {
                val a = (v.coerceIn(0f, 1f) * 255f).toInt() shl 24
                go.getAny<com.sengine.engine.core.SpriteRenderer>()?.let { sr -> sr.color = (sr.color and 0xFFFFFF) or a }
                go.getAny<com.sengine.engine.core.TextRenderer>()?.let { tr -> tr.color = (tr.color and 0xFFFFFF) or a }
            }
        }
    }

    // ------------------------------------------------------------------ easings
    object Linear : Ease { override fun apply(t: Float) = t }
    object SineIn : Ease { override fun apply(t: Float) = (1f - kotlin.math.cos(t * Math.PI / 2)).toFloat() }
    object SineOut : Ease { override fun apply(t: Float) = kotlin.math.sin(t * Math.PI / 2).toFloat() }
    object SineInOut : Ease { override fun apply(t: Float) = (-(kotlin.math.cos(Math.PI * t) - 1) / 2).toFloat() }
    object QuadIn : Ease { override fun apply(t: Float) = t * t }
    object QuadOut : Ease { override fun apply(t: Float) = 1f - (1f - t) * (1f - t) }
    object QuadInOut : Ease { override fun apply(t: Float) = if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2f) / 2f }
    object CubicIn : Ease { override fun apply(t: Float) = t * t * t }
    object CubicOut : Ease { override fun apply(t: Float) = 1f - (1f - t).pow(3f) }
    object CubicInOut : Ease { override fun apply(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3f) / 2f }
    object BackIn : Ease { override fun apply(t: Float): Float { val c = 1.70158f; return c * t * t * t - c * t } }
    object BackOut : Ease { override fun apply(t: Float): Float { val c = 1.70158f; val u = t - 1f; return 1f + c * u * u * u + c * u } }
    object BackInOut : Ease {
        override fun apply(t: Float): Float {
            val c = 2.5949095f
            return if (t < 0.5f) (t * t * ((c + 1f) * 2f * t - c)) / 2f
            else (2f * t - 2f).let { u -> (u * u * ((c + 1f) * u + c) + 2f) / 2f }
        }
    }
    object ElasticOut : Ease {
        override fun apply(t: Float): Float {
            if (t == 0f || t == 1f) return t
            val c = (2f * Math.PI / 3f).toFloat()
            return 2f.pow(-10f * t) * kotlin.math.sin((t * 10f - 0.75f) * c) + 1f
        }
    }
    object BounceOut : Ease {
        override fun apply(t: Float): Float {
            var x = t
            val n1 = 7.5625f; val d1 = 2.75f
            if (x < 1f / d1) return n1 * x * x
            if (x < 2f / d1) { x -= 1.5f / d1; return n1 * x * x + 0.75f }
            if (x < 2.5f / d1) { x -= 2.25f / d1; return n1 * x * x + 0.9375f }
            x -= 2.625f / d1; return n1 * x * x + 0.984375f
        }
    }
}
