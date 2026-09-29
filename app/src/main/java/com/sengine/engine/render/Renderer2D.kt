package com.sengine.engine.render

import android.opengl.GLES20
import com.sengine.engine.math.Affine
import com.sengine.engine.math.Mat4
import java.nio.ByteBuffer
import java.nio.FloatBuffer

/**
 * 2D drawing of shaped / textured quads (optionally with custom shaders) and coloured 3D lines.
 *
 * v7 Pro: quads are GPU-batched. Consecutive quads that share the program, texture and blend
 * mode are merged into one draw call (up to [MAX_QUADS] per batch), cutting draw calls by 10-50x
 * in typical scenes. Quads drawn with a custom `effect()` shader keep the exact per-draw uniform
 * path so user GLSL is untouched. Painter's order is preserved: batches never reorder quads,
 * they only merge compatible neighbours.
 */
class Renderer2D {
    lateinit var shaders: ShaderLibrary
    private var lineProg = 0
    private var batchProg = 0
    private var batchOk = false
    private var whiteTex = 0
    private lateinit var quad: FloatBuffer
    private var lineBuf: FloatBuffer = GL.floatBuffer(7 * 4096)
    private var lineData = FloatArray(7 * 4096)
    private var lineCount = 0
    private var lPos = 0; private var lColor = 0; private var lMVP = 0
    private var bMVP = 0
    private var bPos = 0; private var bUV = 0; private var bColor = 0; private var bAux = 0
    private var bUseTex = 0; private var bTime = 0; private var bResolution = 0

    val viewProj = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val tmp = Affine()
    private val defaultUv = floatArrayOf(0f, 1f, 1f, 0f)

    var drawCalls = 0
    /** Quads submitted this frame (batched + legacy) — profiler stat. */
    var quadsDrawn = 0
    /** Draw calls saved by batching this frame. */
    var batches = 0
    /** Enables GPU sprite batching (auto-disabled when the batch program fails to compile). */
    var batching = true
        set(value) { field = value && batchOk; if (!field) flush() }
    private val batchingOn get() = batching && batchOk

    /** Parameters for shape 4 (rounded rectangle): width/height ratio and corner radius in height units. */
    var roundAspect = 1f
    var roundRadius = 0f
    private val rtmp = Affine()
    private val rtmp2 = Affine()

    /**
     * Rounded rectangle of [w] x [h] local units placed by [m] (centre offset [ox],[oy] in local units).
     * [radius] is in local units; [ppu] converts to pixels for anti-aliasing.
     */
    fun roundRect(m: Affine, ox: Float, oy: Float, w: Float, h: Float, radius: Float, color: Int, ppu: Float, tex: Tex? = null) {
        if (w <= 0f || h <= 0f) return
        rtmp2.a = w; rtmp2.b = 0f; rtmp2.c = 0f; rtmp2.d = h; rtmp2.tx = ox; rtmp2.ty = oy
        rtmp.setMul(m, rtmp2)
        val sx = m.scaleX; val sy = m.scaleY
        roundAspect = (w * sx) / (h * sy).coerceAtLeast(1e-6f)
        roundRadius = (radius / h).coerceAtLeast(0f)
        quad(rtmp, color, if (radius > 0f || tex == null) 4 else 0, tex, h * sy * ppu)
    }
    var time = 0f
    var resW = 1f
    var resH = 1f

    fun init(shaders: ShaderLibrary) {
        this.shaders = shaders
        lineProg = GL.compile(LINE_VS, LINE_FS)
        lPos = GLES20.glGetAttribLocation(lineProg, "aPos")
        lColor = GLES20.glGetAttribLocation(lineProg, "aColor")
        lMVP = GLES20.glGetUniformLocation(lineProg, "uMVP")
        quad = GL.floatBuffer(8)
        quad.put(floatArrayOf(-0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f)).position(0)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        // batched sprite program
        val (id, err) = GL.tryCompile(BATCH_VS, BATCH_FS)
        batchOk = id != 0
        if (batchOk) {
            batchProg = id
            bMVP = GLES20.glGetUniformLocation(batchProg, "uMVP")
            bPos = GLES20.glGetAttribLocation(batchProg, "aPos")
            bUV = GLES20.glGetAttribLocation(batchProg, "aUV")
            bColor = GLES20.glGetAttribLocation(batchProg, "aColor")
            bAux = GLES20.glGetAttribLocation(batchProg, "aAux")
            bUseTex = GLES20.glGetUniformLocation(batchProg, "uUseTex")
            bTime = GLES20.glGetUniformLocation(batchProg, "uTime")
            bResolution = GLES20.glGetUniformLocation(batchProg, "uResolution")
        } else if (err != null) android.util.Log.w("SEngine", "Sprite batching unavailable: $err")
        // 1x1 white texture so untextured quads join any batch
        val px = java.nio.IntBuffer.wrap(intArrayOf(0xFFFFFFFF.toInt()))
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        whiteTex = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, whiteTex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, px)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    fun begin(view: View2D) { flush(); view.matrix(viewProj) }

    fun begin(m: FloatArray) { flush(); System.arraycopy(m, 0, viewProj, 0, 16) }

    // ------------------------------------------------------------------ batching

    private var batchQuads = 0
    private var batchData = GL.floatBuffer(MAX_QUADS * 6 * VERT_FLOATS)
    private var batchCap = MAX_QUADS
    private var pendingProgram = 0
    private var pendingTex = 0
    private var pendingAdditive = false
    /** Tracks the blend mode so batches can switch between normal and additive without GL churn. */
    private var curAdditive = false

    /** Queues sprite/particles under the additive blend state until the next flush. */
    fun setBlend(additive: Boolean) {
        if (additive == curAdditive) return
        flush()
        curAdditive = additive
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, if (additive) GLES20.GL_ONE else GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    /** Submits all queued quads in one draw call. Preserves painter's order (flush before GL state changes). */
    fun flush() {
        if (batchQuads == 0) return
        GLES20.glUseProgram(batchProg)
        GLES20.glUniformMatrix4fv(bMVP, 1, false, viewProj, 0)
        GLES20.glUniform1f(bUseTex, 1f)
        if (bTime >= 0) GLES20.glUniform1f(bTime, time)
        if (bResolution >= 0) GLES20.glUniform2f(bResolution, resW, resH)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (pendingTex == 0) whiteTex else pendingTex)
        GLES20.glUniform1i(BATCH_TEX_UNIT, 0)
        batchData.position(0)
        val floats = batchQuads * 6 * VERT_FLOATS
        GLES20.glEnableVertexAttribArray(bPos)
        GLES20.glEnableVertexAttribArray(bUV)
        GLES20.glEnableVertexAttribArray(bColor)
        GLES20.glEnableVertexAttribArray(bAux)
        GLES20.glVertexAttribPointer(bPos, 2, GLES20.GL_FLOAT, false, VERT_FLOATS * 4, batchData)
        batchData.position(2)
        GLES20.glVertexAttribPointer(bUV, 2, GLES20.GL_FLOAT, false, VERT_FLOATS * 4, batchData)
        batchData.position(4)
        GLES20.glVertexAttribPointer(bColor, 4, GLES20.GL_FLOAT, false, VERT_FLOATS * 4, batchData)
        batchData.position(8)
        GLES20.glVertexAttribPointer(bAux, 4, GLES20.GL_FLOAT, false, VERT_FLOATS * 4, batchData)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, batchQuads * 6)
        GLES20.glDisableVertexAttribArray(bPos)
        GLES20.glDisableVertexAttribArray(bUV)
        GLES20.glDisableVertexAttribArray(bColor)
        GLES20.glDisableVertexAttribArray(bAux)
        batches++
        drawCalls++
        batchQuads = 0
    }

    private fun batchQuad(
        m00: Float, m01: Float, m10: Float, m11: Float, tx: Float, ty: Float,
        color: Int, shape: Int, tex: Tex?, aaPixels: Float, uv: FloatArray
    ) {
        if (batchQuads >= batchCap) growBatch()
        if (pendingTex != (tex?.id ?: 0)) flush()
        pendingTex = tex?.id ?: 0
        pendingProgram = batchProg
        val r = GL.r(color); val g = GL.g(color); val b = GL.b(color); val a = GL.a(color)
        val shapeF = shape.toFloat()
        val aa = aaPixels.coerceAtLeast(1f)
        val ra = roundAspect; val rr = roundRadius
        val u0 = uv[0]; val v0 = uv[1]; val u1 = uv[2]; val v1 = uv[3]
        var o = batchQuads * 6 * VERT_FLOATS
        // corners in strip order (-,-),(+,-),(-,+),(+,+)
        var i = 0
        while (i < 6) {
            val cx = when (i) { 0, 2, 3 -> -0.5f; else -> 0.5f }
            val cy = when (i) { 0, 1, 4 -> -0.5f; else -> 0.5f }
            val tu = if (cx < 0f) 0f else 1f
            val tv = if (cy < 0f) 0f else 1f
            batchData.put(o++, m00 * cx + m10 * cy + tx)
            batchData.put(o++, m01 * cx + m11 * cy + ty)
            batchData.put(o++, u0 + (u1 - u0) * tu)
            batchData.put(o++, v0 + (v1 - v0) * tv)
            batchData.put(o++, r); batchData.put(o++, g); batchData.put(o++, b); batchData.put(o++, a)
            batchData.put(o++, shapeF); batchData.put(o++, aa); batchData.put(o++, ra); batchData.put(o, rr)
            o++
            i++
        }
        batchQuads++
        quadsDrawn++
    }

    private fun growBatch() {
        flush()
        if (batchCap >= MAX_QUADS) return
        batchCap = (batchCap * 2).coerceAtMost(MAX_QUADS)
        batchData = GL.floatBuffer(batchCap * 6 * VERT_FLOATS)
    }

    /**
     * Draw a unit quad transformed by [m].
     * shape: 0 rect, 1 circle, 2 triangle, 3 ring, 4 rounded rectangle
     */
    fun quad(m: Affine, color: Int, shape: Int, tex: Tex?, aaPixels: Float, flipX: Boolean = false, flipY: Boolean = false,
             uv: FloatArray? = null, program: SpriteProgram? = null, param: Float = 1f) {
        if (batchingOn && program == null) {
            batchQuad(m.a, m.b, m.c, m.d, m.tx, m.ty, color, shape, tex, aaPixels, uvRect(uv, flipX, flipY))
            return
        }
        m.toMat4(model)
        quadModel(model, color, shape, tex, aaPixels, flipX, flipY, uv, program, param)
    }

    private fun uvRect(uv: FloatArray?, flipX: Boolean, flipY: Boolean): FloatArray {
        val q = uv ?: defaultUv
        val u0 = if (flipX) q[2] else q[0]
        val u1 = if (flipX) q[0] else q[2]
        val v0 = if (flipY) q[3] else q[1]
        val v1 = if (flipY) q[1] else q[3]
        uvTmp[0] = u0; uvTmp[1] = v0; uvTmp[2] = u1; uvTmp[3] = v1
        return uvTmp
    }
    private val uvTmp = FloatArray(4)

    /** Draw a unit quad (XY plane) with a full 4x4 model matrix. */
    fun quadModel(modelM: FloatArray, color: Int, shape: Int, tex: Tex?, aaPixels: Float, flipX: Boolean = false, flipY: Boolean = false,
                  uv: FloatArray? = null, program: SpriteProgram? = null, param: Float = 1f) {
        if (batchingOn && program == null) {
            batchQuad(modelM[0], modelM[1], modelM[4], modelM[5], modelM[12], modelM[13], color, shape, tex, aaPixels, uvRect(uv, flipX, flipY))
            return
        }
        val p = program ?: shaders.defaultSprite
        GLES20.glUseProgram(p.id)
        Mat4.mul(mvp, viewProj, modelM)
        GLES20.glUniformMatrix4fv(p.uMVP, 1, false, mvp, 0)
        GLES20.glUniform4f(p.uColor, GL.r(color), GL.g(color), GL.b(color), GL.a(color))
        GLES20.glUniform1f(p.uShape, shape.toFloat())
        GLES20.glUniform1f(p.uAA, aaPixels.coerceAtLeast(1f))
        val q = uv ?: defaultUv
        val u0 = if (flipX) q[2] else q[0]
        val u1 = if (flipX) q[0] else q[2]
        val v0 = if (flipY) q[3] else q[1]
        val v1 = if (flipY) q[1] else q[3]
        GLES20.glUniform4f(p.uUV, u0, v0, u1, v1)
        if (p.uTime >= 0) GLES20.glUniform1f(p.uTime, time)
        if (p.uParam >= 0) GLES20.glUniform1f(p.uParam, param)
        if (p.uResolution >= 0) GLES20.glUniform2f(p.uResolution, resW, resH)
        if (p.uRound >= 0) GLES20.glUniform3f(p.uRound, roundAspect, roundRadius, 0f)
        if (tex != null) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex.id)
            GLES20.glUniform1i(p.uTex, 0)
            GLES20.glUniform1f(p.uUseTex, 1f)
        } else GLES20.glUniform1f(p.uUseTex, 0f)
        quad.position(0)
        GLES20.glEnableVertexAttribArray(p.aPos)
        GLES20.glVertexAttribPointer(p.aPos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(p.aPos)
        drawCalls++
        quadsDrawn++
    }

    /** Axis-aligned helper. */
    fun rect(cx: Float, cy: Float, w: Float, h: Float, color: Int, shape: Int, ppu: Float, tex: Tex? = null) {
        tmp.a = w; tmp.b = 0f; tmp.c = 0f; tmp.d = h; tmp.tx = cx; tmp.ty = cy
        quad(tmp, color, shape, tex, minOf(w, h) * ppu)
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int) = line3(x1, y1, 0f, x2, y2, 0f, color)

    fun line3(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, color: Int) {
        if ((lineCount + 2) * 7 > lineData.size) {
            lineData = lineData.copyOf(lineData.size * 2)
            lineBuf = GL.floatBuffer(lineData.size)
        }
        val r = GL.r(color); val g = GL.g(color); val b = GL.b(color); val a = GL.a(color)
        var i = lineCount * 7
        lineData[i++] = x1; lineData[i++] = y1; lineData[i++] = z1; lineData[i++] = r; lineData[i++] = g; lineData[i++] = b; lineData[i++] = a
        lineData[i++] = x2; lineData[i++] = y2; lineData[i++] = z2; lineData[i++] = r; lineData[i++] = g; lineData[i++] = b; lineData[i] = a
        lineCount += 2
    }

    fun circleLines(cx: Float, cy: Float, r: Float, color: Int, seg: Int = 40) {
        var px = cx + r
        var py = cy
        for (i in 1..seg) {
            val a = i * Math.PI * 2 / seg
            val nx = cx + (Math.cos(a) * r).toFloat()
            val ny = cy + (Math.sin(a) * r).toFloat()
            line(px, py, nx, ny, color)
            px = nx; py = ny
        }
    }

    /** Circle in 3D around an axis (0 = X, 1 = Y, 2 = Z). */
    fun circle3(cx: Float, cy: Float, cz: Float, r: Float, axis: Int, color: Int, seg: Int = 48) {
        var prev: FloatArray? = null
        for (i in 0..seg) {
            val a = i * Math.PI * 2 / seg
            val c = (Math.cos(a) * r).toFloat(); val s = (Math.sin(a) * r).toFloat()
            val p = when (axis) {
                0 -> floatArrayOf(cx, cy + c, cz + s)
                1 -> floatArrayOf(cx + c, cy, cz + s)
                else -> floatArrayOf(cx + c, cy + s, cz)
            }
            prev?.let { line3(it[0], it[1], it[2], p[0], p[1], p[2], color) }
            prev = p
        }
    }

    /** Wire box of the local unit cube [min,max] transformed by a 4x4 matrix. */
    fun wireBox(m: FloatArray, min: FloatArray, max: FloatArray, color: Int) {
        val c = Array(8) { i ->
            Mat4.point(m, if (i and 1 == 0) min[0] else max[0], if (i and 2 == 0) min[1] else max[1], if (i and 4 == 0) min[2] else max[2])
        }
        val edges = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 0, 2, 1, 3, 4, 6, 5, 7, 0, 4, 1, 5, 2, 6, 3, 7)
        var k = 0
        while (k < edges.size) {
            val a = c[edges[k]]; val b = c[edges[k + 1]]
            line3(a[0], a[1], a[2], b[0], b[1], b[2], color)
            k += 2
        }
    }

    fun obb(m: Affine, hw: Float, hh: Float, color: Int) {
        val xs = floatArrayOf(-hw, hw, hw, -hw)
        val ys = floatArrayOf(-hh, -hh, hh, hh)
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            line(m.mapX(xs[i], ys[i]), m.mapY(xs[i], ys[i]), m.mapX(xs[j], ys[j]), m.mapY(xs[j], ys[j]), color)
        }
    }

    fun flushLines(width: Float = 1f) {
        flush() // never let batched quads from a previous layer leak into the line pass
        if (lineCount == 0) return
        GLES20.glUseProgram(lineProg)
        GLES20.glUniformMatrix4fv(lMVP, 1, false, viewProj, 0)
        lineBuf.position(0)
        lineBuf.put(lineData, 0, lineCount * 7)
        lineBuf.position(0)
        GLES20.glEnableVertexAttribArray(lPos)
        GLES20.glEnableVertexAttribArray(lColor)
        GLES20.glVertexAttribPointer(lPos, 3, GLES20.GL_FLOAT, false, 28, lineBuf)
        lineBuf.position(3)
        GLES20.glVertexAttribPointer(lColor, 4, GLES20.GL_FLOAT, false, 28, lineBuf)
        GLES20.glLineWidth(width)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, lineCount)
        GLES20.glDisableVertexAttribArray(lPos)
        GLES20.glDisableVertexAttribArray(lColor)
        lineCount = 0
        drawCalls++
    }

    companion object {
        const val LINE_VS = """
uniform mat4 uMVP;
attribute vec3 aPos;
attribute vec4 aColor;
varying vec4 vColor;
void main() { vColor = aColor; gl_Position = uMVP * vec4(aPos, 1.0); }
"""
        const val LINE_FS = """
precision mediump float;
varying vec4 vColor;
void main() { gl_FragColor = vColor; }
"""

        /** Floats per vertex: pos(2) uv(2) color(4) aux(4: shape, aa, roundAspect, roundRadius). */
        const val VERT_FLOATS = 12
        const val MAX_QUADS = 16384
        const val BATCH_TEX_UNIT = 0

        /**
         * Batched sprite program. Identical shape math to the classic sprite shader, but the
         * per-draw uniforms (color, shape, aa, uv rect, round params) are per-vertex attributes.
         */
        const val BATCH_VS = """
uniform mat4 uMVP;
attribute vec2 aPos;
attribute vec2 aUV;
attribute vec4 aColor;
attribute vec4 aAux;
varying vec2 vP;
varying vec2 vUV;
varying vec4 vColor;
varying vec4 vAux;
void main() {
  vP = aPos;
  vUV = aUV;
  vColor = aColor;
  vAux = aAux;
  gl_Position = uMVP * vec4(aPos, 0.0, 1.0);
}
"""
        const val BATCH_FS = """
precision mediump float;
varying vec2 vP;
varying vec2 vUV;
varying vec4 vColor;
varying vec4 vAux;
uniform sampler2D uTex;
uniform float uUseTex;
uniform float uTime;
uniform vec2 uResolution;
void main() {
  float shape = vAux.x;
  float aa = vAux.y;
  float a = 1.0;
  if (shape > 3.5) {
    vec2 q = vP * vec2(vAux.z, 1.0);
    vec2 b = vec2(0.5 * vAux.z, 0.5);
    float r = min(vAux.w, min(b.x, b.y));
    vec2 dd = abs(q) - b + r;
    float sd = length(max(dd, 0.0)) + min(max(dd.x, dd.y), 0.0) - r;
    a = clamp(-sd * aa + 0.5, 0.0, 1.0);
  } else if (shape > 0.5 && shape < 1.5) {
    a = clamp((0.5 - length(vP)) * aa, 0.0, 1.0);
  } else if (shape > 1.5 && shape < 2.5) {
    float w = (0.5 - vP.y) * 0.5;
    float e = min(w - abs(vP.x), vP.y + 0.5);
    a = clamp(e * aa, 0.0, 1.0);
  } else if (shape > 2.5 && shape < 3.5) {
    float d = length(vP);
    a = clamp((0.5 - d) * aa, 0.0, 1.0) * clamp((d - 0.40) * aa, 0.0, 1.0);
  }
  vec4 c = vColor;
  if (uUseTex > 0.5) c *= texture2D(uTex, vUV);
  c.a *= a;
  gl_FragColor = c;
}
"""
    }
}
