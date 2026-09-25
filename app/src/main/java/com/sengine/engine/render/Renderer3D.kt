package com.sengine.engine.render

import android.opengl.GLES20
import com.sengine.engine.core.Light
import com.sengine.engine.core.MeshRenderer
import com.sengine.engine.core.Scene
import com.sengine.engine.math.Mat4
import java.nio.FloatBuffer
import kotlin.math.sqrt

/** Forward Blinn-Phong mesh renderer with gradient sky, fog, 1 directional + 4 point lights. */
class Renderer3D {
    lateinit var shaders: ShaderLibrary
    private var skyProg = 0
    private var skyPos = 0; private var skyInv = 0; private var skyTop = 0; private var skyHorizon = 0; private var skyGround = 0; private var skySun = 0
    private lateinit var fullscreen: FloatBuffer

    private val mvp = FloatArray(16)
    private val inv = FloatArray(16)
    private val normalMat = FloatArray(16)

    // per-frame lighting
    private val ambient = FloatArray(3)
    private val dirDir = floatArrayOf(-0.4f, -1f, -0.3f)
    private val dirColor = FloatArray(3)
    private val pointPos = FloatArray(16)
    private val pointColor = FloatArray(12)
    private val fogColor = FloatArray(3)
    private val fog = FloatArray(3)
    lateinit var view: View3D

    var drawCalls = 0
    var time = 0f
    var resW = 1f
    var resH = 1f

    fun init(shaders: ShaderLibrary) {
        this.shaders = shaders
        skyProg = GL.compile(SKY_VS, SKY_FS)
        skyPos = GLES20.glGetAttribLocation(skyProg, "aPos")
        skyInv = GLES20.glGetUniformLocation(skyProg, "uInvVP")
        skyTop = GLES20.glGetUniformLocation(skyProg, "uTop")
        skyHorizon = GLES20.glGetUniformLocation(skyProg, "uHorizon")
        skyGround = GLES20.glGetUniformLocation(skyProg, "uGround")
        skySun = GLES20.glGetUniformLocation(skyProg, "uSun")
        fullscreen = GL.floatBuffer(8)
        fullscreen.put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)).position(0)
    }

    var sunDisc = true

    fun drawSky(v: View3D, top: Int, horizon: Int) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glUseProgram(skyProg)
        GLES20.glUniformMatrix4fv(skyInv, 1, false, v.invViewProj, 0)
        GLES20.glUniform3f(skyTop, GL.r(top), GL.g(top), GL.b(top))
        GLES20.glUniform3f(skyHorizon, GL.r(horizon), GL.g(horizon), GL.b(horizon))
        GLES20.glUniform3f(skyGround, GL.r(horizon) * 0.45f, GL.g(horizon) * 0.45f, GL.b(horizon) * 0.5f)
        GLES20.glUniform4f(skySun, -dirDir[0], -dirDir[1], -dirDir[2], if (sunDisc) 1f else 0f)
        fullscreen.position(0)
        GLES20.glEnableVertexAttribArray(skyPos)
        GLES20.glVertexAttribPointer(skyPos, 2, GLES20.GL_FLOAT, false, 0, fullscreen)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(skyPos)
        GLES20.glDepthMask(true)
        drawCalls++
    }

    /** Collects lights from the scene. With no lights, a default sun is used. */
    fun setupLights(scene: Scene, v: View3D) {
        view = v
        val amb = scene.ambient
        ambient[0] = GL.r(amb); ambient[1] = GL.g(amb); ambient[2] = GL.b(amb)
        fogColor[0] = GL.r(scene.fogColor); fogColor[1] = GL.g(scene.fogColor); fogColor[2] = GL.b(scene.fogColor)
        fog[0] = scene.fogStart; fog[1] = scene.fogEnd; fog[2] = if (scene.fog) 1f else 0f
        var hasDir = false
        dirColor.fill(0f)
        pointPos.fill(0f); pointColor.fill(0f)
        // default: sun from above
        dirDir[0] = -0.4f; dirDir[1] = -1f; dirDir[2] = -0.3f
        var points = 0
        // point lights: nearest 4 to camera
        val pts = ArrayList<Pair<Float, Pair<FloatArray, Light>>>()
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val l = go.get<Light>() ?: continue
            val w = go.world3
            if (l.kind == 0) {
                if (!hasDir) {
                    val d = Mat4.dir(w, 0f, 0f, -1f)
                    val len = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]).coerceAtLeast(1e-6f)
                    dirDir[0] = d[0] / len; dirDir[1] = d[1] / len; dirDir[2] = d[2] / len
                    dirColor[0] = GL.r(l.color) * l.intensity; dirColor[1] = GL.g(l.color) * l.intensity; dirColor[2] = GL.b(l.color) * l.intensity
                    hasDir = true
                }
            } else {
                pts.add(v.distanceTo(w[12], w[13], w[14]) to (floatArrayOf(w[12], w[13], w[14]) to l))
            }
        }
        val anyLight = hasDir || pts.isNotEmpty()
        if (!anyLight) { dirColor[0] = 0.85f; dirColor[1] = 0.83f; dirColor[2] = 0.8f }
        for ((_, pl) in pts.sortedBy { it.first }) {
            if (points >= 4) break
            val (p, l) = pl
            pointPos[points * 4] = p[0]; pointPos[points * 4 + 1] = p[1]; pointPos[points * 4 + 2] = p[2]; pointPos[points * 4 + 3] = l.range
            pointColor[points * 3] = GL.r(l.color) * l.intensity; pointColor[points * 3 + 1] = GL.g(l.color) * l.intensity; pointColor[points * 3 + 2] = GL.b(l.color) * l.intensity
            points++
        }
    }

    // ------------------------------------------------------------------ shadows
    private var depthProg = 0
    private var dPos = 0; private var dMVP = 0
    private var shadowFbo = 0; private var shadowTex = 0; private var shadowDepth = 0; private var shadowSize = 0
    private var shadowGen = -1
    val lightVP = FloatArray(16)
    private val lightView = FloatArray(16)
    private val lightProj = FloatArray(16)
    var shadowsOn = false; private set
    private var shadowPcf = false
    var grade = false
    private val skyColor = FloatArray(3)

    fun setSkyColor(c: Int) { skyColor[0] = GL.r(c); skyColor[1] = GL.g(c); skyColor[2] = GL.b(c) }

    private fun ensureShadowTarget(size: Int) {
        if (depthProg == 0 || shadowGen != Meshes.contextGen) {
            depthProg = GL.compile(DEPTH_VS, DEPTH_FS)
            dPos = GLES20.glGetAttribLocation(depthProg, "aPos")
            dMVP = GLES20.glGetUniformLocation(depthProg, "uMVP")
            shadowFbo = 0; shadowSize = 0; shadowGen = Meshes.contextGen
        }
        if (shadowFbo != 0 && shadowSize == size) return
        if (shadowFbo != 0) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(shadowFbo), 0)
            GLES20.glDeleteTextures(1, intArrayOf(shadowTex), 0)
            GLES20.glDeleteRenderbuffers(1, intArrayOf(shadowDepth), 0)
        }
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0); shadowTex = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, shadowTex)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, size, size, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glGenRenderbuffers(1, ids, 0); shadowDepth = ids[0]
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, shadowDepth)
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, size, size)
        GLES20.glGenFramebuffers(1, ids, 0); shadowFbo = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, shadowFbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, shadowTex, 0)
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, shadowDepth)
        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) { shadowFbo = 0 }
        shadowSize = size
    }

    /**
     * Renders shadow casters into the shadow map from the directional light, fitted around the
     * camera. Call after [setupLights] and before the main pass. [restoreFbo]/viewport are restored.
     */
    fun renderShadows(casters: List<Pair<Mesh, FloatArray>>, quality: Int, distance: Float, restoreW: Int, restoreH: Int) {
        shadowsOn = false
        if (quality <= 0 || casters.isEmpty()) return
        val size = if (quality >= 2) 2048 else 1024
        val prev = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, prev, 0)
        ensureShadowTarget(size)
        if (shadowFbo == 0) { GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prev[0]); return }
        // fit an orthographic light frustum around the view focus
        val f = view.forward()
        val half = distance * 0.5f
        val cx = view.eye[0] + f[0] * half; val cy = view.eye[1] + f[1] * half; val cz = view.eye[2] + f[2] * half
        val texel = distance / size
        // snap the centre to texels to avoid shimmering
        val sx = Math.floor((cx / texel).toDouble()).toFloat() * texel
        val sz = Math.floor((cz / texel).toDouble()).toFloat() * texel
        val d = dirDir
        val back = distance * 1.5f
        val upY = if (kotlin.math.abs(d[1]) > 0.95f) 0f else 1f
        val upZ = if (upY == 0f) 1f else 0f
        Mat4.lookAt(lightView, sx - d[0] * back, cy - d[1] * back, sz - d[2] * back, sx, cy, sz, 0f, upY, upZ)
        Mat4.ortho(lightProj, -half * 1.2f, half * 1.2f, -half * 1.2f, half * 1.2f, 0.1f, back * 2.5f)
        Mat4.mul(lightVP, lightProj, lightView)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, shadowFbo)
        GLES20.glViewport(0, 0, size, size)
        GLES20.glClearColor(1f, 1f, 1f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(true)
        GLES20.glUseProgram(depthProg)
        GLES20.glEnableVertexAttribArray(dPos)
        for ((mesh, model) in casters) {
            if (!inFrustum(lightVP, mesh, model)) continue
            Mat4.mul(mvp, lightVP, model)
            GLES20.glUniformMatrix4fv(dMVP, 1, false, mvp, 0)
            mesh.bind()
            GLES20.glVertexAttribPointer(dPos, 3, GLES20.GL_FLOAT, false, 32, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount)
            drawCalls++
        }
        GLES20.glDisableVertexAttribArray(dPos)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prev[0])
        GLES20.glViewport(0, 0, restoreW, restoreH)
        shadowsOn = true
        shadowPcf = quality >= 2
    }

    // ------------------------------------------------------------------ culling
    private val cullTmp = FloatArray(3)

    /** Bounding-sphere test of a mesh (model space bounds transformed by [model]) against a view-projection frustum. */
    fun inFrustum(vp: FloatArray, mesh: Mesh, model: FloatArray): Boolean {
        val mn = mesh.min; val mx = mesh.max
        val c = Mat4.point(model, (mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2, (mn[2] + mx[2]) / 2, cullTmp)
        val ex = (mx[0] - mn[0]) / 2; val ey = (mx[1] - mn[1]) / 2; val ez = (mx[2] - mn[2]) / 2
        val sc = maxOf(Mat4.scaleOf(model, 0), Mat4.scaleOf(model, 1), Mat4.scaleOf(model, 2))
        val r = sqrt(ex * ex + ey * ey + ez * ez) * sc
        for (i in 0 until 3) for (sgn in intArrayOf(1, -1)) {
            val a = vp[3] + sgn * vp[i]; val b = vp[7] + sgn * vp[4 + i]; val cc = vp[11] + sgn * vp[8 + i]; val dd = vp[15] + sgn * vp[12 + i]
            val len = sqrt(a * a + b * b + cc * cc)
            if (a * c[0] + b * c[1] + cc * c[2] + dd < -r * len) return false
        }
        return true
    }

    fun visible(mesh: Mesh, model: FloatArray) = inFrustum(view.viewProj, mesh, model)

    fun drawMesh(mesh: Mesh, model: FloatArray, mr: MeshRenderer, tex: Tex?, program: MeshProgram?, colorOverride: Int = 0) {
        val p = program ?: shaders.defaultMesh
        GLES20.glUseProgram(p.id)
        Mat4.mul(mvp, view.viewProj, model)
        Mat4.invert(inv, model)
        Mat4.transpose(normalMat, inv)
        GLES20.glUniformMatrix4fv(p.uMVP, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(p.uModel, 1, false, model, 0)
        GLES20.glUniformMatrix4fv(p.uNormalMat, 1, false, normalMat, 0)
        val col = if (colorOverride != 0) mulColor(mr.color, colorOverride) else mr.color
        GLES20.glUniform4f(p.uColor, GL.r(col), GL.g(col), GL.b(col), GL.a(col))
        GLES20.glUniform1f(p.uTiling, mr.tiling)
        GLES20.glUniform3f(p.uCamPos, view.eye[0], view.eye[1], view.eye[2])
        GLES20.glUniform3fv(p.uAmbient, 1, ambient, 0)
        if (p.uSkyColor >= 0) GLES20.glUniform3fv(p.uSkyColor, 1, skyColor, 0)
        GLES20.glUniform3fv(p.uDirDir, 1, dirDir, 0)
        GLES20.glUniform3fv(p.uDirColor, 1, dirColor, 0)
        GLES20.glUniform4fv(p.uPointPos, 4, pointPos, 0)
        GLES20.glUniform3fv(p.uPointColor, 4, pointColor, 0)
        GLES20.glUniform1f(p.uSpec, mr.specular)
        GLES20.glUniform1f(p.uShine, mr.shininess)
        GLES20.glUniform1f(p.uEmission, mr.emission)
        GLES20.glUniform1f(p.uUnlit, if (mr.unlit) 1f else 0f)
        GLES20.glUniform3fv(p.uFogColor, 1, fogColor, 0)
        GLES20.glUniform3fv(p.uFog, 1, fog, 0)
        if (p.uTime >= 0) GLES20.glUniform1f(p.uTime, time)
        if (p.uParam >= 0) GLES20.glUniform1f(p.uParam, mr.shaderParam)
        if (p.uResolution >= 0) GLES20.glUniform2f(p.uResolution, resW, resH)
        if (p.uGrade >= 0) GLES20.glUniform1f(p.uGrade, if (grade) 1f else 0f)
        if (p.uShadow >= 0) {
            if (shadowsOn) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, shadowTex)
                GLES20.glUniform1i(p.uShadowMap, 1)
                GLES20.glUniformMatrix4fv(p.uLightVP, 1, false, lightVP, 0)
                GLES20.glUniform4f(p.uShadow, 1f, 1f / shadowSize, 0.0015f, if (shadowPcf) 1f else 0f)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            } else {
                GLES20.glUniform4f(p.uShadow, 0f, 0f, 0f, 0f)
                GLES20.glUniformMatrix4fv(p.uLightVP, 1, false, identity, 0)
            }
        }
        if (tex != null) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex.id)
            if (mr.tiling != 1f && isPot(tex.w) && isPot(tex.h)) {
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
            }
            GLES20.glUniform1i(p.uTex, 0)
            GLES20.glUniform1f(p.uUseTex, 1f)
        } else GLES20.glUniform1f(p.uUseTex, 0f)
        mesh.bind()
        GLES20.glEnableVertexAttribArray(p.aPos)
        GLES20.glVertexAttribPointer(p.aPos, 3, GLES20.GL_FLOAT, false, 32, 0)
        if (p.aNormal >= 0) {
            GLES20.glEnableVertexAttribArray(p.aNormal)
            GLES20.glVertexAttribPointer(p.aNormal, 3, GLES20.GL_FLOAT, false, 32, 12)
        }
        if (p.aUV >= 0) {
            GLES20.glEnableVertexAttribArray(p.aUV)
            GLES20.glVertexAttribPointer(p.aUV, 2, GLES20.GL_FLOAT, false, 32, 24)
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount)
        GLES20.glDisableVertexAttribArray(p.aPos)
        if (p.aNormal >= 0) GLES20.glDisableVertexAttribArray(p.aNormal)
        if (p.aUV >= 0) GLES20.glDisableVertexAttribArray(p.aUV)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        drawCalls++
    }

    private val identity = Mat4.identity()

    private fun mulColor(a: Int, b: Int): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) * ((b shr s) and 0xFF) / 255) and 0xFF
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun isPot(n: Int) = n > 0 && (n and (n - 1)) == 0

    companion object {
        const val DEPTH_VS = """
uniform mat4 uMVP;
attribute vec3 aPos;
void main() { gl_Position = uMVP * vec4(aPos, 1.0); }
"""
        const val DEPTH_FS = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
void main() {
  vec4 e = fract(gl_FragCoord.z * vec4(1.0, 255.0, 65025.0, 16581375.0));
  e -= e.yzww * vec4(1.0 / 255.0, 1.0 / 255.0, 1.0 / 255.0, 0.0);
  gl_FragColor = e;
}
"""
        const val SKY_VS = """
attribute vec2 aPos;
varying vec2 vNdc;
void main() { vNdc = aPos; gl_Position = vec4(aPos, 0.9999, 1.0); }
"""
        const val SKY_FS = """
precision mediump float;
varying vec2 vNdc;
uniform mat4 uInvVP;
uniform vec3 uTop;
uniform vec3 uHorizon;
uniform vec3 uGround;
uniform vec4 uSun;
void main() {
  vec4 a = uInvVP * vec4(vNdc, -1.0, 1.0);
  vec4 b = uInvVP * vec4(vNdc, 1.0, 1.0);
  vec3 d = normalize(b.xyz / b.w - a.xyz / a.w);
  vec3 c;
  if (d.y >= 0.0) c = mix(uHorizon, uTop, pow(d.y, 0.6));
  else c = mix(uHorizon, uGround, clamp(-d.y * 4.0, 0.0, 1.0));
  if (uSun.w > 0.5) {
    float s = max(dot(d, normalize(uSun.xyz)), 0.0);
    c += vec3(1.0, 0.92, 0.75) * (pow(s, 1200.0) * 4.0 + pow(s, 60.0) * 0.35 + pow(s, 6.0) * 0.08);
  }
  gl_FragColor = vec4(c, 1.0);
}
"""
    }
}
