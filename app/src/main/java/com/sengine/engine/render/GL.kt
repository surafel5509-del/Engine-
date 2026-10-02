package com.sengine.engine.render

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay

/**
 * Graphics capability layer. S Engine 7 requests an **OpenGL ES 3.0** context and falls back to
 * ES 2.0 automatically on old devices. When a 3.x context is active every built-in shader is
 * transpiled from GLSL ES 100 to GLSL ES 300 (`in`/`out`, `texture()`, MRT-ready output),
 * so the same source drives both paths.
 */
object Gfx {
    /** True once an ES 3.x context was detected on the GL thread. */
    @Volatile var es3 = false
        private set
    /** Full GL_VERSION string, shown in the editor status bar. */
    @Volatile var version = ""
        private set
    /** Renderer string (GPU name). */
    @Volatile var renderer = ""
        private set
    @Volatile var vendor = ""
        private set

    /** Call once per surface creation (GL thread) before compiling shaders. */
    fun detect() {
        try {
            version = GLES20.glGetString(GLES20.GL_VERSION) ?: ""
            renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: ""
            vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: ""
        } catch (_: Throwable) {
            version = ""; renderer = ""
        }
        es3 = version.contains("OpenGL ES 3")
        Log.i("SEngine", "GL context: $version ($renderer) — ES3 path: $es3")
    }

    /** Short label for UI, e.g. "OpenGL ES 3.2". */
    fun label(): String {
        val m = Regex("OpenGL ES (\\d+\\.\\d+)").find(version)
        return "OpenGL ES " + (m?.groupValues?.get(1) ?: (if (es3) "3.0" else "2.0"))
    }

    /**
     * Transpiles GLSL ES 100 source to GLSL ES 300 when running on an ES3 context.
     * [fragment] selects `varying` → `in` (fragment) vs `out` (vertex).
     */
    fun upgrade(src: String, fragment: Boolean): String {
        if (!es3) return src
        val body = src.trimStart()
        if (body.startsWith("#version")) return src // already versioned
        var out = StringBuilder(src.length + 64)
        out.append("#version 300 es\n")
        if (fragment) out.append("out highp vec4 sFragColor;\n")
        // Derivatives are core in ES3 — the OES extension block becomes a no-op.
        var s = body
        s = s.replace("#extension GL_OES_standard_derivatives : enable", "")
        s = s.replace("#ifdef GL_OES_standard_derivatives", "#if 1")
        // word-boundary keyword rewrites
        s = kw(s, "attribute", "in")
        s = kw(s, "varying", if (fragment) "in" else "out")
        s = s.replace("texture2D", "texture")
        s = s.replace("gl_FragColor", "sFragColor")
        out.append(s)
        return out.toString()
    }

    /** Replaces whole-word `from` with `to`, leaving identifiers like `uTexture2Data` intact. */
    private fun kw(s: String, from: String, to: String): String =
        s.replace(Regex("(?<![A-Za-z0-9_])$from(?![A-Za-z0-9_])"), to)

    /**
     * Builds a [GLSurfaceView] that creates an ES 3.0 context when the device supports it and
     * silently falls back to ES 2.0 otherwise (no crash on old GPUs).
     */
    fun surface(context: Context, renderer: GLSurfaceView.Renderer): GLSurfaceView =
        GLSurfaceView(context).apply {
            setEGLContextFactory(Es3Factory)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

    private object Es3Factory : GLSurfaceView.EGLContextFactory {
        override fun createContext(egl: EGL10, display: EGLDisplay, config: EGLConfig): EGLContext {
            val att3 = intArrayOf(0x3098, 3, EGL10.EGL_NONE)
            var ctx = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, att3)
            if (ctx == null || ctx == EGL10.EGL_NO_CONTEXT) {
                Log.w("SEngine", "ES 3.0 context unavailable — falling back to ES 2.0")
                val att2 = intArrayOf(0x3098, 2, EGL10.EGL_NONE)
                ctx = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, att2)
            }
            return ctx ?: EGL10.EGL_NO_CONTEXT
        }

        override fun destroyContext(egl: EGL10, display: EGLDisplay, context: EGLContext) {
            egl.eglDestroyContext(display, context)
        }
    }
}

object GL {
    /** Compiles and links; returns program id (0 on failure) and the error log. */
    fun tryCompile(vs: String, fs: String): Pair<Int, String?> {
        val v = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
        GLES20.glShaderSource(v, Gfx.upgrade(vs, false)); GLES20.glCompileShader(v)
        val st = IntArray(1)
        GLES20.glGetShaderiv(v, GLES20.GL_COMPILE_STATUS, st, 0)
        if (st[0] == 0) { val e = GLES20.glGetShaderInfoLog(v); GLES20.glDeleteShader(v); return 0 to "vertex: $e" }
        val f = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
        GLES20.glShaderSource(f, Gfx.upgrade(fs, true)); GLES20.glCompileShader(f)
        GLES20.glGetShaderiv(f, GLES20.GL_COMPILE_STATUS, st, 0)
        if (st[0] == 0) { val e = GLES20.glGetShaderInfoLog(f); GLES20.glDeleteShader(v); GLES20.glDeleteShader(f); return 0 to e }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v); GLES20.glAttachShader(p, f); GLES20.glLinkProgram(p)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, st, 0)
        GLES20.glDeleteShader(v); GLES20.glDeleteShader(f)
        if (st[0] == 0) { val e = GLES20.glGetProgramInfoLog(p); GLES20.glDeleteProgram(p); return 0 to "link: $e" }
        return p to null
    }

    fun compile(vs: String, fs: String): Int {
        val v = shader(GLES20.GL_VERTEX_SHADER, vs)
        val f = shader(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        val st = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, st, 0)
        if (st[0] == 0) Log.e("SEngine", "Link error: " + GLES20.glGetProgramInfoLog(p))
        return p
    }

    private fun shader(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, Gfx.upgrade(src, type == GLES20.GL_FRAGMENT_SHADER))
        GLES20.glCompileShader(s)
        val st = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, st, 0)
        if (st[0] == 0) Log.e("SEngine", "Shader error: " + GLES20.glGetShaderInfoLog(s))
        return s
    }

    fun floatBuffer(n: Int): FloatBuffer =
        ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    fun r(c: Int) = ((c shr 16) and 0xFF) / 255f
    fun g(c: Int) = ((c shr 8) and 0xFF) / 255f
    fun b(c: Int) = (c and 0xFF) / 255f
    fun a(c: Int) = ((c ushr 24) and 0xFF) / 255f
}
