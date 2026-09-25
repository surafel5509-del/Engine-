package com.sengine.engine.core

import com.sengine.engine.math.Affine
import com.sengine.engine.math.Mat4

class GameObject(var id: Long, var name: String) {
    var tag: String = "Untagged"
    var active: Boolean = true
    /** Sorting order: higher is drawn on top. */
    var order: Int = 0

    var x = 0f
    var y = 0f
    var rotation = 0f
    var scaleX = 1f
    var scaleY = 1f
    // 3D extension (rotation above is the Z rotation)
    var z = 0f
    var rotX = 0f
    var rotY = 0f
    var scaleZ = 1f

    var parent: GameObject? = null
    val components = mutableListOf<Component>()

    @Volatile var destroyed = false

    /** Cached world transform, refreshed by [Scene.updateTransforms]. */
    val world = Affine()
    /** Cached 3D world matrix (column-major), refreshed by [Scene.updateTransforms]. */
    val world3 = Mat4.identity()

    fun localMatrix3(out: FloatArray = FloatArray(16)): FloatArray =
        Mat4.trs(out, x, y, z, rotX, rotY, rotation, scaleX, scaleY, scaleZ)

    fun computeWorld3(): FloatArray {
        val local = localMatrix3()
        val p = parent ?: return local
        return Mat4.mul(FloatArray(16), p.computeWorld3(), local)
    }

    fun setWorldPosition3(wx: Float, wy: Float, wz: Float) {
        val p = parent
        if (p == null) { x = wx; y = wy; z = wz; return }
        val inv = FloatArray(16)
        if (!Mat4.invert(inv, p.computeWorld3())) return
        val l = Mat4.point(inv, wx, wy, wz)
        x = l[0]; y = l[1]; z = l[2]
    }

    fun localMatrix(out: Affine = Affine()): Affine = out.setTRS(x, y, rotation, scaleX, scaleY)

    fun computeWorld(): Affine {
        val local = localMatrix()
        val p = parent ?: return applyAnchor(local)
        return Affine().setMul(p.computeWorld(), local)
    }

    /** Screen anchor of a root UI object (0 = centre / none). */
    fun uiAnchor(): Int {
        for (c in components) {
            val a = when (c) {
                is UIPanel -> c.anchor
                is UIButton -> c.anchor
                is UIProgress -> c.anchor
                is SpriteRenderer -> if (c.screenSpace) c.anchor else 0
                is TextRenderer -> if (c.screenSpace) c.anchor else 0
                else -> 0
            }
            if (a != 0) return a
        }
        return 0
    }

    /** Offsets a root object's world matrix by its UI anchor (UI canvas half-size from [Scene.uiHalfW]). */
    fun applyAnchor(m: Affine): Affine {
        val a = uiAnchor()
        if (a != 0) { val (ox, oy) = anchorOffset(a, Scene.uiHalfW, 5f); m.tx += ox; m.ty += oy }
        return m
    }

    fun setWorldPosition(wx: Float, wy: Float) {
        val p = parent
        if (p == null) {
            val a = uiAnchor()
            val (ox, oy) = if (a != 0) anchorOffset(a, Scene.uiHalfW, 5f) else (0f to 0f)
            x = wx - ox; y = wy - oy
        } else {
            val inv = p.computeWorld().inverted() ?: return
            x = inv.mapX(wx, wy); y = inv.mapY(wx, wy)
        }
    }

    fun isActiveInHierarchy(): Boolean = active && !destroyed && (parent?.isActiveInHierarchy() ?: true)

    fun isAncestorOf(other: GameObject): Boolean {
        var p = other.parent
        while (p != null) {
            if (p === this) return true
            p = p.parent
        }
        return false
    }

    fun <T : Component> add(c: T): T {
        c.gameObject = this
        components.add(c)
        return c
    }

    inline fun <reified T : Component> get(): T? = components.firstOrNull { it is T && it.enabled } as T?
    inline fun <reified T : Component> getAny(): T? = components.firstOrNull { it is T } as T?
}
