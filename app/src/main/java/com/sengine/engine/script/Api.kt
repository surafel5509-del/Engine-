package com.sengine.engine.script

import com.sengine.engine.Engine
import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.Component
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.SpriteRenderer
import com.sengine.engine.core.TextRenderer
import org.mozilla.javascript.Context
import kotlin.math.sqrt

/*
 * Objects exposed to JavaScript. Getter/setter pairs become JS properties
 * (e.g. getX()/setX() -> transform.x).
 */

class SObject(private val go: GameObject, private val engine: Engine, private val sys: ScriptSystem) {
    fun getId(): Double = go.id.toDouble()
    fun getName(): String = go.name
    fun setName(v: String) { go.name = v }
    fun getTag(): String = go.tag
    fun setTag(v: String) { go.tag = v }
    fun getActive(): Boolean = go.active
    fun setActive(v: Boolean) { go.active = v }
    fun getOrder(): Double = go.order.toDouble()
    fun setOrder(v: Double) { go.order = v.toInt() }

    fun getX(): Double = go.x.toDouble()
    fun setX(v: Double) { go.x = v.toFloat() }
    fun getY(): Double = go.y.toDouble()
    fun setY(v: Double) { go.y = v.toFloat() }
    fun getRotation(): Double = go.rotation.toDouble()
    fun setRotation(v: Double) { go.rotation = v.toFloat() }
    fun getScaleX(): Double = go.scaleX.toDouble()
    fun setScaleX(v: Double) { go.scaleX = v.toFloat() }
    fun getScaleY(): Double = go.scaleY.toDouble()
    fun setScaleY(v: Double) { go.scaleY = v.toFloat() }
    fun getWorldX(): Double = go.computeWorld().tx.toDouble()
    fun getWorldY(): Double = go.computeWorld().ty.toDouble()

    fun setPosition(x: Double, y: Double) { go.x = x.toFloat(); go.y = y.toFloat() }
    fun setWorldPosition(x: Double, y: Double) { go.setWorldPosition(x.toFloat(), y.toFloat()) }
    fun move(dx: Double, dy: Double) { go.x += dx.toFloat(); go.y += dy.toFloat() }
    fun rotate(deg: Double) { go.rotation += deg.toFloat() }

    // physics
    private val rb3 get() = go.getAny<com.sengine.engine.core.Rigidbody3D>()?.also { it.sleepTime = 0f }
    fun getVx(): Double = (rb3?.vx ?: go.getAny<Rigidbody2D>()?.vx ?: 0f).toDouble()
    fun setVx(v: Double) { rb3?.let { it.vx = v.toFloat(); return }; go.getAny<Rigidbody2D>()?.vx = v.toFloat() }
    fun getVy(): Double = (rb3?.vy ?: go.getAny<Rigidbody2D>()?.vy ?: 0f).toDouble()
    fun setVy(v: Double) { rb3?.let { it.vy = v.toFloat(); return }; go.getAny<Rigidbody2D>()?.vy = v.toFloat() }
    fun getVz(): Double = (rb3?.vz ?: 0f).toDouble()
    fun setVz(v: Double) { rb3?.vz = v.toFloat() }
    fun isGrounded(): Boolean = rb3?.grounded ?: go.getAny<Rigidbody2D>()?.grounded ?: false
    /** 0…1 how deep the body is in a Water volume. */
    fun getSubmerged(): Double = (rb3?.submerged ?: go.getAny<Rigidbody2D>()?.submerged ?: 0f).toDouble()
    fun isInWater(): Boolean = getSubmerged() > 0.0
    fun addForce(fx: Double, fy: Double) {
        rb3?.let { it.vx += (fx / it.mass).toFloat(); it.vy += (fy / it.mass).toFloat(); return }
        val rb = go.getAny<Rigidbody2D>() ?: return
        rb.vx += (fx / rb.mass).toFloat(); rb.vy += (fy / rb.mass).toFloat()
    }
    fun addForce(fx: Double, fy: Double, fz: Double) {
        val rb = rb3 ?: return addForce(fx, fy)
        rb.vx += (fx / rb.mass).toFloat(); rb.vy += (fy / rb.mass).toFloat(); rb.vz += (fz / rb.mass).toFloat()
    }
    fun setVelocity(vx: Double, vy: Double) {
        rb3?.let { it.vx = vx.toFloat(); it.vy = vy.toFloat(); return }
        val rb = go.getAny<Rigidbody2D>() ?: return
        rb.vx = vx.toFloat(); rb.vy = vy.toFloat()
    }
    fun setVelocity(vx: Double, vy: Double, vz: Double) {
        val rb = rb3 ?: return setVelocity(vx, vy)
        rb.vx = vx.toFloat(); rb.vy = vy.toFloat(); rb.vz = vz.toFloat()
    }

    // ---- 3D transform
    fun getZ(): Double = go.z.toDouble()
    fun setZ(v: Double) { go.z = v.toFloat() }
    fun getRotX(): Double = go.rotX.toDouble()
    fun setRotX(v: Double) { go.rotX = v.toFloat() }
    fun getRotY(): Double = go.rotY.toDouble()
    fun setRotY(v: Double) { go.rotY = v.toFloat() }
    fun getRotZ(): Double = go.rotation.toDouble()
    fun setRotZ(v: Double) { go.rotation = v.toFloat() }
    fun getScaleZ(): Double = go.scaleZ.toDouble()
    fun setScaleZ(v: Double) { go.scaleZ = v.toFloat() }
    fun getWorldZ(): Double = go.computeWorld3()[14].toDouble()
    fun setPosition(x: Double, y: Double, z: Double) { go.x = x.toFloat(); go.y = y.toFloat(); go.z = z.toFloat() }
    fun move(dx: Double, dy: Double, dz: Double) { go.x += dx.toFloat(); go.y += dy.toFloat(); go.z += dz.toFloat() }
    fun rotate(rx: Double, ry: Double, rz: Double) { go.rotX += rx.toFloat(); go.rotY += ry.toFloat(); go.rotation += rz.toFloat() }
    fun distanceTo3(o: SObject): Double {
        val a = go.computeWorld3(); val b = o.go.computeWorld3()
        val dx = a[12] - b[12]; val dy = a[13] - b[13]; val dz = a[14] - b[14]
        return Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble())
    }
    /** Local forward (-Z) direction in world space as [x, y, z]. */
    fun forward(): Any? {
        val d = com.sengine.engine.math.Mat4.dir(go.computeWorld3(), 0f, 0f, -1f)
        return sys.newArray(listOf(d[0].toDouble(), d[1].toDouble(), d[2].toDouble()))
    }

    // ---- animation
    fun play(clip: String) {
        val a = go.getAny<com.sengine.engine.core.Animator>() ?: return
        if (a.current == clip && a.playing && !a.finished) return
        a.current = clip; a.time = 0f; a.frame = 0; a.finished = false; a.playing = true
    }
    fun stopAnimation() { go.getAny<com.sengine.engine.core.Animator>()?.playing = false }
    fun getAnimation(): String = go.getAny<com.sengine.engine.core.Animator>()?.current ?: ""
    fun isAnimationFinished(): Boolean = go.getAny<com.sengine.engine.core.Animator>()?.finished ?: true
    fun setAnimSpeed(v: Double) { go.getAny<com.sengine.engine.core.Animator>()?.speed = v.toFloat() }

    // ---- rendering
    fun setShaderParam(v: Double) {
        go.getAny<com.sengine.engine.core.MeshRenderer>()?.shaderParam = v.toFloat()
        go.getAny<SpriteRenderer>()?.shaderParam = v.toFloat()
    }
    fun setMeshColor(hex: String) {
        val c = try { Component.parseColor(hex) } catch (e: Exception) { return }
        go.getAny<com.sengine.engine.core.MeshRenderer>()?.color = c
    }
    fun overlaps(other: SObject): Boolean {
        val a = go.computeWorld(); val b = other.go.computeWorld()
        val ca = go.getAny<Collider2D>(); val cb = other.go.getAny<Collider2D>()
        val aw = (ca?.width ?: 1f) * a.scaleX / 2; val ah = (ca?.height ?: 1f) * a.scaleY / 2
        val bw = (cb?.width ?: 1f) * b.scaleX / 2; val bh = (cb?.height ?: 1f) * b.scaleY / 2
        return kotlin.math.abs(a.tx - b.tx) < aw + bw && kotlin.math.abs(a.ty - b.ty) < ah + bh
    }

    // rendering
    fun getText(): String = go.getAny<TextRenderer>()?.text ?: ""
    fun setText(v: Any?) { go.getAny<TextRenderer>()?.text = Context.toString(v) }
    fun getColor(): String {
        val c = go.getAny<SpriteRenderer>()?.color ?: go.getAny<TextRenderer>()?.color ?: -1
        return String.format("#%08X", c)
    }
    fun setColor(hex: String) {
        val c = try { Component.parseColor(hex) } catch (e: Exception) { return }
        go.getAny<SpriteRenderer>()?.color = c
        go.getAny<TextRenderer>()?.color = c
    }
    fun getVisible(): Boolean = go.getAny<SpriteRenderer>()?.enabled ?: false
    fun setVisible(v: Boolean) {
        go.getAny<SpriteRenderer>()?.enabled = v
        go.getAny<TextRenderer>()?.enabled = v
    }
    fun setTexture(name: String) { go.getAny<SpriteRenderer>()?.texture = name }
    fun getFlipX(): Boolean = go.getAny<SpriteRenderer>()?.flipX ?: false
    fun setFlipX(v: Boolean) { go.getAny<SpriteRenderer>()?.flipX = v }

    // particles
    fun burst(n: Double) { go.getAny<ParticleEmitter>()?.let { it.pendingBurst += n.toInt() } }
    fun setEmitting(v: Boolean) { go.getAny<ParticleEmitter>()?.emitting = v }

    // camera
    fun getSize(): Double = (go.getAny<Camera2D>()?.size ?: 0f).toDouble()
    fun setSize(v: Double) { go.getAny<Camera2D>()?.size = v.toFloat() }

    // hierarchy & lifecycle
    fun getParent(): Any? = go.parent?.let { sys.toJs(it) }
    fun child(name: String): Any? = engine.scene.childrenOf(go).firstOrNull { it.name == name }?.let { sys.toJs(it) }
    fun destroy() { go.destroyed = true }
    fun hasComponent(type: String): Boolean = go.components.any { it.type.equals(type, true) }
    fun setComponentEnabled(type: String, v: Boolean) {
        go.components.filter { it.type.equals(type, true) }.forEach { it.enabled = v }
    }
    fun distanceTo(o: SObject): Double {
        val a = go.computeWorld(); val b = o.go.computeWorld()
        val dx = a.tx - b.tx; val dy = a.ty - b.ty
        return sqrt((dx * dx + dy * dy).toDouble())
    }
    fun send(fn: String, arg: Any?): Any? = sys.sendMessage(go, fn, arg)
    fun send(fn: String): Any? = sys.sendMessage(go, fn, null)
    fun `is`(o: SObject?): Boolean = o != null && o.go === go

    // ---- generic component property access: self.setProp("Light", "Intensity", 2)
    private fun findProp(type: String, label: String): com.sengine.engine.core.Prop? {
        val c = go.components.firstOrNull { it.type.equals(type, true) } ?: return null
        val norm = label.replace(" ", "").lowercase()
        return c.props().firstOrNull { it.name.replace(" ", "").lowercase() == norm }
    }
    fun setProp(type: String, label: String, value: Any?) {
        val p = findProp(type, label) ?: run { engine.log(1, "setProp: $type.$label not found on ${go.name}"); return }
        try {
            when (p) {
                is com.sengine.engine.core.Prop.F -> p.set(Context.toNumber(value).toFloat())
                is com.sengine.engine.core.Prop.I -> p.set(Context.toNumber(value).toInt())
                is com.sengine.engine.core.Prop.B -> p.set(Context.toBoolean(value))
                is com.sengine.engine.core.Prop.S -> p.set(Context.toString(value))
                is com.sengine.engine.core.Prop.Asset -> p.set(Context.toString(value))
                is com.sengine.engine.core.Prop.Color -> p.set(if (value is String) Component.parseColor(value) else Context.toNumber(value).toLong().toInt())
                is com.sengine.engine.core.Prop.Choice -> p.set(if (value is String) p.options.indexOfFirst { it.equals(value, true) }.coerceAtLeast(0) else Context.toNumber(value).toInt())
            }
        } catch (e: Exception) { engine.log(1, "setProp: ${e.message}") }
    }
    fun getProp(type: String, label: String): Any? = when (val p = findProp(type, label)) {
        is com.sengine.engine.core.Prop.F -> p.get().toDouble()
        is com.sengine.engine.core.Prop.I -> p.get().toDouble()
        is com.sengine.engine.core.Prop.B -> p.get()
        is com.sengine.engine.core.Prop.S -> p.get()
        is com.sengine.engine.core.Prop.Asset -> p.get()
        is com.sengine.engine.core.Prop.Color -> String.format("#%08X", p.get())
        is com.sengine.engine.core.Prop.Choice -> p.options.getOrNull(p.get()) ?: ""
        null -> null
    }

    // ---- 3D model animation (.smodel clips)
    fun playModelAnim(name: String) {
        val mr = go.getAny<com.sengine.engine.core.MeshRenderer>() ?: return
        if (mr.playingAnim == name) return
        mr.playingAnim = name; mr.animTime = 0f
    }
    fun stopModelAnim() { go.getAny<com.sengine.engine.core.MeshRenderer>()?.let { it.playingAnim = ""; it.animTime = 0f } }
    fun getModelAnim(): String = go.getAny<com.sengine.engine.core.MeshRenderer>()?.playingAnim ?: ""
    fun setModelAnimSpeed(v: Double) { go.getAny<com.sengine.engine.core.MeshRenderer>()?.animSpeed = v.toFloat() }

    // ---- UI components
    fun getValue(): Double = (go.getAny<com.sengine.engine.core.UIProgress>()?.value ?: 0f).toDouble()
    fun setValue(v: Double) { go.getAny<com.sengine.engine.core.UIProgress>()?.value = v.toFloat().coerceIn(0f, 1f) }
    fun getLabel(): String = go.getAny<com.sengine.engine.core.UIButton>()?.text ?: getText()
    fun setLabel(v: Any?) { val t = Context.toString(v); go.getAny<com.sengine.engine.core.UIButton>()?.text = t; go.getAny<TextRenderer>()?.text = t }
    fun getInteractable(): Boolean = go.getAny<com.sengine.engine.core.UIButton>()?.interactable ?: false
    fun setInteractable(v: Boolean) { go.getAny<com.sengine.engine.core.UIButton>()?.interactable = v }
    fun isPressed(): Boolean = go.getAny<com.sengine.engine.core.UIButton>()?.pressed ?: false

    // ---- orientation helpers
    /** Rotates (2D: rotation, 3D: rotY) to face another object or point. */
    fun lookAt(o: SObject) {
        val a = go.computeWorld3(); val b = o.go.computeWorld3()
        lookAtPoint(b[12].toDouble(), b[13].toDouble(), b[14].toDouble(), a)
    }
    fun lookAt(x: Double, y: Double) {
        val w = go.computeWorld()
        go.rotation = Math.toDegrees(Math.atan2(y - w.ty, x - w.tx)).toFloat()
    }
    fun lookAt(x: Double, y: Double, z: Double) = lookAtPoint(x, y, z, go.computeWorld3())
    private fun lookAtPoint(x: Double, y: Double, z: Double, a: FloatArray) {
        val dx = x - a[12]; val dz = z - a[14]
        if (go.getAny<com.sengine.engine.core.MeshRenderer>() != null || go.getAny<com.sengine.engine.core.Rigidbody3D>() != null || go.getAny<com.sengine.engine.core.Camera3D>() != null) {
            go.rotY = Math.toDegrees(Math.atan2(-dx, -dz)).toFloat()
            val dy = y - a[13]
            if (go.getAny<com.sengine.engine.core.Camera3D>() != null) go.rotX = Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))).toFloat()
        } else go.rotation = Math.toDegrees(Math.atan2(y - a[13], x - a[12])).toFloat()
    }
    /** Moves towards a point by at most [step]; returns true when arrived. */
    fun moveTowards(x: Double, y: Double, step: Double): Boolean {
        val w = go.computeWorld()
        val dx = x - w.tx; val dy = y - w.ty
        val d = Math.sqrt(dx * dx + dy * dy)
        if (d <= step || d < 1e-6) { go.setWorldPosition(x.toFloat(), y.toFloat()); return true }
        go.setWorldPosition((w.tx + dx / d * step).toFloat(), (w.ty + dy / d * step).toFloat())
        return false
    }
    fun right(): Any? {
        val d = com.sengine.engine.math.Mat4.dir(go.computeWorld3(), 1f, 0f, 0f)
        return sys.newArray(listOf(d[0].toDouble(), d[1].toDouble(), d[2].toDouble()))
    }
    fun wake() { go.getAny<com.sengine.engine.core.Rigidbody3D>()?.sleepTime = 0f }
    fun isSleeping(): Boolean = (go.getAny<com.sengine.engine.core.Rigidbody3D>()?.sleepTime ?: 0f) >= com.sengine.engine.physics.PhysicsWorld3D.SLEEP_AFTER
    override fun toString() = "GameObject(${go.name})"
}

class SScene(private val engine: Engine, private val sys: ScriptSystem) {
    fun getName(): String = engine.scene.name
    fun find(name: String): Any? = engine.scene.find(name)?.let { sys.toJs(it) }
    fun findAll(tag: String): Any? =
        sys.newArray(engine.scene.objects.filter { it.tag == tag && !it.destroyed && it.isActiveInHierarchy() }.map { sys.toJs(it) })
    fun count(tag: String): Double =
        engine.scene.objects.count { it.tag == tag && !it.destroyed && it.isActiveInHierarchy() }.toDouble()
    private fun spawnGo(name: String, x: Float, y: Float, z: Float?): GameObject? {
        val template = engine.scene.find(name) ?: run { engine.log(1, "spawn: '$name' not found"); return null }
        val copy = engine.scene.duplicate(template, null)
        copy.active = true
        if (z != null) copy.setWorldPosition3(x, y, z) else copy.setWorldPosition(x, y)
        for (d in listOf(copy) + engine.scene.objects.filter { copy.isAncestorOf(it) }) {
            d.components.forEach { it.resetRuntime() }
        }
        engine.scene.updateTransforms()
        sys.attach(copy)
        return copy
    }
    fun spawn(name: String, x: Double, y: Double): Any? = spawnGo(name, x.toFloat(), y.toFloat(), null)?.let { sys.toJs(it) }
    fun spawn(name: String): Any? {
        val t = engine.scene.find(name) ?: return null
        val w = t.computeWorld()
        return spawn(name, w.tx.toDouble(), w.ty.toDouble())
    }
    fun spawn(name: String, x: Double, y: Double, z: Double): Any? = spawnGo(name, x.toFloat(), y.toFloat(), z.toFloat())?.let { sys.toJs(it) }
    fun shake(amount: Double) = engine.shake(amount.toFloat())
    /** 3D raycast against Collider3D objects; returns the first object hit or null. */
    fun raycast(ox: Double, oy: Double, oz: Double, dx: Double, dy: Double, dz: Double, maxDist: Double): Any? {
        val l = Math.sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-9)
        return engine.physics3D.raycast(engine.scene, ox.toFloat(), oy.toFloat(), oz.toFloat(),
            (dx / l).toFloat(), (dy / l).toFloat(), (dz / l).toFloat(), maxDist.toFloat())?.let { sys.toJs(it) }
    }
    /** 3D raycast returning details: {object, x, y, z, nx, ny, nz, distance, block, blockX, blockY, blockZ} or null. */
    fun raycastHit(ox: Double, oy: Double, oz: Double, dx: Double, dy: Double, dz: Double, maxDist: Double): Any? {
        val l = Math.sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-9)
        val h = engine.physics3D.raycastHit(engine.scene, ox.toFloat(), oy.toFloat(), oz.toFloat(),
            (dx / l).toFloat(), (dy / l).toFloat(), (dz / l).toFloat(), maxDist.toFloat()) ?: return null
        return sys.newObject(mapOf("object" to h.go?.let { sys.toJs(it) }, "x" to h.x.toDouble(), "y" to h.y.toDouble(), "z" to h.z.toDouble(),
            "nx" to h.nx.toDouble(), "ny" to h.ny.toDouble(), "nz" to h.nz.toDouble(), "distance" to h.distance.toDouble(),
            "block" to h.block.toDouble(), "blockX" to h.blockX.toDouble(), "blockY" to h.blockY.toDouble(), "blockZ" to h.blockZ.toDouble()))
    }
    /** 2D raycast: {object, x, y, nx, ny, distance} or null. Optional tag filter. */
    fun raycast2D(ox: Double, oy: Double, dx: Double, dy: Double, maxDist: Double): Any? = raycast2D(ox, oy, dx, dy, maxDist, "")
    fun raycast2D(ox: Double, oy: Double, dx: Double, dy: Double, maxDist: Double, tag: String): Any? {
        val l = Math.sqrt(dx * dx + dy * dy).coerceAtLeast(1e-9)
        val h = engine.physics.raycast(engine.scene, ox.toFloat(), oy.toFloat(), (dx / l).toFloat(), (dy / l).toFloat(), maxDist.toFloat(), null, false, tag) ?: return null
        return sys.newObject(mapOf("object" to sys.toJs(h.go), "x" to h.x.toDouble(), "y" to h.y.toDouble(), "nx" to h.nx.toDouble(), "ny" to h.ny.toDouble(), "distance" to h.distance.toDouble()))
    }
    /** Objects with [tag] within [radius] of (x, y) — or (x, y, z) in 3D — nearest first. */
    fun findInRadius(tag: String, x: Double, y: Double, radius: Double): Any? {
        val r2 = radius * radius
        val list = engine.scene.objects.filter { it.tag == tag && !it.destroyed && it.isActiveInHierarchy() }
            .map { it to ((it.world.tx - x) * (it.world.tx - x) + (it.world.ty - y) * (it.world.ty - y)) }
            .filter { it.second <= r2 }.sortedBy { it.second }.map { sys.toJs(it.first) }
        return sys.newArray(list)
    }
    fun nearest(tag: String, x: Double, y: Double): Any? = engine.scene.objects
        .filter { it.tag == tag && !it.destroyed && it.isActiveInHierarchy() }
        .minByOrNull { (it.world.tx - x) * (it.world.tx - x) + (it.world.ty - y) * (it.world.ty - y) }?.let { sys.toJs(it) }
    fun nearest3(tag: String, x: Double, y: Double, z: Double): Any? = engine.scene.objects
        .filter { it.tag == tag && !it.destroyed && it.isActiveInHierarchy() }
        .minByOrNull { val w = it.world3; (w[12] - x) * (w[12] - x) + (w[13] - y) * (w[13] - y) + (w[14] - z) * (w[14] - z) }?.let { sys.toJs(it) }
    fun getVoxel(): Any? = sys.voxelApi
    fun getCamera3D(): Any? = engine.mainCamera3D()?.let { sys.toJs(it) }
    fun getGravity3D(): Double = engine.scene.gravity3D.toDouble()
    fun setGravity3D(v: Double) { engine.scene.gravity3D = v.toFloat() }
    fun load(sceneName: String) = engine.requestLoadScene(sceneName)
    fun reload() = engine.requestLoadScene(engine.scene.name)
    fun getCamera(): Any? = engine.mainCamera()?.let { sys.toJs(it) }
    fun getGravityX(): Double = engine.scene.gravityX.toDouble()
    fun setGravityX(v: Double) { engine.scene.gravityX = v.toFloat() }
    fun getGravityY(): Double = engine.scene.gravityY.toDouble()
    fun setGravityY(v: Double) { engine.scene.gravityY = v.toFloat() }
}

/** Plain public fields (Rhino exposes them with their exact names, e.g. input.aDown). */
class SInput(private val engine: Engine) {
    @JvmField var axisX = 0.0
    @JvmField var axisY = 0.0
    @JvmField var a = false
    @JvmField var b = false
    @JvmField var aDown = false
    @JvmField var bDown = false
    @JvmField var touching = false
    @JvmField var tapped = false
    @JvmField var touchX = 0.0
    @JvmField var touchY = 0.0
    @JvmField var axis2X = 0.0
    @JvmField var axis2Y = 0.0
    @JvmField var lookX = 0.0
    @JvmField var lookY = 0.0
    @JvmField var screenX = 0.0
    @JvmField var screenY = 0.0

    fun button(id: String): Boolean = engine.input.button(id)
    fun buttonDown(id: String): Boolean = engine.input.buttonDown(id)
    fun buttonUp(id: String): Boolean = engine.input.buttonUp(id)
    fun stickX(id: String): Double = engine.input.stickX(id).toDouble()
    fun stickY(id: String): Double = engine.input.stickY(id).toDouble()
    /** Switch the on-screen controller: a preset name ("Racing", "Twin-Stick", ...), "project" or "none". */
    fun setControls(name: String) { engine.input.controlsRequest = name }
    fun showControls(v: Boolean) { engine.input.controlsHidden = !v }

    fun sync() {
        val i = engine.input
        axis2X = i.axis2X.toDouble(); axis2Y = i.axis2Y.toDouble()
        lookX = i.lookX.toDouble(); lookY = i.lookY.toDouble()
        screenX = i.touchScreenX.toDouble(); screenY = i.touchScreenY.toDouble()
        axisX = i.axisX.toDouble(); axisY = i.axisY.toDouble()
        a = i.a; b = i.b; aDown = i.aDown; bDown = i.bDown
        touching = i.touching; tapped = i.tapped
        touchX = i.touchX.toDouble(); touchY = i.touchY.toDouble()
    }
}

class STime(private val engine: Engine) {
    fun getTime(): Double = engine.time
    fun getFrame(): Double = engine.frame.toDouble()
    fun getFps(): Double = engine.fps.toDouble()
    fun getDt(): Double = engine.deltaTime.toDouble()
    fun getScale(): Double = engine.timeScale.toDouble()
    fun setScale(v: Double) { engine.timeScale = v.toFloat().coerceIn(0f, 10f) }
    fun getUnscaledTime(): Double = engine.unscaledTime
}

class SAudio(private val engine: Engine) {
    fun play(name: String) = engine.audio.play(name)
    fun play(name: String, volume: Double) = engine.audio.play(name, volume.toFloat())
    fun play(name: String, volume: Double, pitch: Double) = engine.audio.play(name, volume.toFloat(), false, pitch.toFloat())
    fun loop(name: String, volume: Double) = engine.audio.play(name, volume.toFloat(), true)
    fun stop(stream: Double) = engine.audio.stopStream(stream.toInt())
    fun setPitch(stream: Double, pitch: Double) = engine.audio.setRate(stream.toInt(), pitch.toFloat())
    fun setVolume(stream: Double, v: Double) = engine.audio.setVolume(stream.toInt(), v.toFloat())
    fun playMusic(name: String) = engine.audio.playMusic(name)
    fun playMusic(name: String, volume: Double) = engine.audio.playMusic(name, volume.toFloat())
    fun stopMusic() = engine.audio.stopMusic()
    fun getMusicVolume(): Double = engine.audio.musicVolume.toDouble()
    fun setMusicVolume(v: Double) { engine.audio.musicVolume = v.toFloat() }
    fun getSfxVolume(): Double = engine.audio.sfxVolume.toDouble()
    fun setSfxVolume(v: Double) { engine.audio.sfxVolume = v.toFloat().coerceIn(0f, 1f) }
    fun beep() = engine.audio.beep()
    fun stopAll() = engine.audio.stopAll()
}

/** storage.get/set: persistent values saved per game. */
class SStorage(private val engine: Engine) {
    fun get(key: String): Any? = engine.storage.get(key)?.let { if (it is Number) it.toDouble() else it }
    fun get(key: String, def: Any?): Any? = if (engine.storage.has(key)) get(key) else def
    fun getNumber(key: String, def: Double): Double = (engine.storage.get(key) as? Number)?.toDouble() ?: (engine.storage.get(key) as? String)?.toDoubleOrNull() ?: def
    fun set(key: String, value: Any?) {
        val v: Any? = when (value) {
            null, is Boolean, is String -> value
            is Number -> value.toDouble()
            is org.mozilla.javascript.Undefined -> null
            else -> Context.toString(value)
        }
        engine.storage.set(key, v)
    }
    fun has(key: String) = engine.storage.has(key)
    fun remove(key: String) = engine.storage.remove(key)
    fun clear() = engine.storage.clear()
}

/** assets.text(name) reads a project text asset (json/txt/csv...). */
class SAssets(private val engine: Engine) {
    fun text(name: String): String? = engine.project.readAsset(name)
    fun exists(name: String): Boolean = engine.project.assetFile(name).exists()
}

/** ui.click(name), ui.show/hide — helpers for Game UI objects. */
class SUI(private val engine: Engine) {
    fun click(name: String): Boolean = engine.ui.clickByName(name)
    fun show(name: String) { engine.scene.find(name)?.active = true }
    fun hide(name: String) { engine.scene.find(name)?.active = false }
    fun toggle(name: String) { engine.scene.find(name)?.let { it.active = !it.active } }
    fun setText(name: String, text: Any?) {
        val go = engine.scene.find(name) ?: return
        val t = Context.toString(text)
        go.getAny<com.sengine.engine.core.UIButton>()?.text = t
        go.getAny<TextRenderer>()?.text = t
    }
    fun setProgress(name: String, v: Double) { engine.scene.find(name)?.getAny<com.sengine.engine.core.UIProgress>()?.value = v.toFloat().coerceIn(0f, 1f) }
}

/** Engine.platform features available to games. */
class SPlatform(private val engine: Engine) {
    fun vibrate(ms: Double) { engine.platform?.vibrate(ms.toInt()) }
    fun quit() { engine.platform?.quit() }
    fun openUrl(url: String) { engine.platform?.openUrl(url) }
    fun toast(text: Any?) { engine.platform?.toast(Context.toString(text)) }
    fun getStandalone(): Boolean = engine.platform != null && engine.project.saveDir.parentFile?.name == "gamesaves"
}

/** scene.voxel — block world access for the first active VoxelWorld (coordinates are world units). */
class SVoxel(private val engine: Engine, private val sys: ScriptSystem) {
    private fun world(): Pair<com.sengine.engine.core.GameObject, com.sengine.engine.voxel.VoxelData>? {
        for (go in engine.scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val vw = go.get<com.sengine.engine.core.VoxelWorld>() ?: continue
            val d = vw.data ?: continue
            return go to d
        }
        return null
    }
    private fun ox(go: com.sengine.engine.core.GameObject) = go.world3[12]
    private fun oy(go: com.sengine.engine.core.GameObject) = go.world3[13]
    private fun oz(go: com.sengine.engine.core.GameObject) = go.world3[14]
    fun getReady(): Boolean = world() != null
    fun getBlock(x: Double, y: Double, z: Double): Double {
        val (go, d) = world() ?: return 0.0
        return d.get(Math.floor(x - ox(go)).toInt(), Math.floor(y - oy(go)).toInt(), Math.floor(z - oz(go)).toInt()).toDouble()
    }
    fun setBlock(x: Double, y: Double, z: Double, id: Double) {
        val (go, d) = world() ?: return
        d.set(Math.floor(x - ox(go)).toInt(), Math.floor(y - oy(go)).toInt(), Math.floor(z - oz(go)).toInt(), id.toInt().coerceIn(0, com.sengine.engine.voxel.Blocks.count - 1))
    }
    /** Height (world y) of the first air block above the surface at (x, z). */
    fun surfaceY(x: Double, z: Double): Double {
        val (go, d) = world() ?: return 0.0
        return d.surfaceY(Math.floor(x - ox(go)).toInt(), Math.floor(z - oz(go)).toInt()) + oy(go).toDouble()
    }
    fun raycast(x: Double, y: Double, z: Double, dx: Double, dy: Double, dz: Double, maxDist: Double): Any? {
        val (go, d) = world() ?: return null
        val l = Math.sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-9)
        val h = d.raycast((x - ox(go)).toFloat(), (y - oy(go)).toFloat(), (z - oz(go)).toFloat(), (dx / l).toFloat(), (dy / l).toFloat(), (dz / l).toFloat(), maxDist.toFloat()) ?: return null
        return sys.newObject(mapOf("x" to (h.x + ox(go)).toDouble(), "y" to (h.y + oy(go)).toDouble(), "z" to (h.z + oz(go)).toDouble(),
            "nx" to h.nx.toDouble(), "ny" to h.ny.toDouble(), "nz" to h.nz.toDouble(), "block" to h.block.toDouble(), "distance" to h.distance.toDouble()))
    }
    fun blockName(id: Double): String = com.sengine.engine.voxel.Blocks.names.getOrElse(id.toInt()) { "?" }
    fun getBlockCount(): Double = com.sengine.engine.voxel.Blocks.count.toDouble()
    fun getSizeX(): Double = (world()?.second?.sx ?: 0).toDouble()
    fun getSizeY(): Double = (world()?.second?.sy ?: 0).toDouble()
    fun getSizeZ(): Double = (world()?.second?.sz ?: 0).toDouble()
    fun save(name: String): Boolean {
        val (_, d) = world() ?: return false
        return try { d.save(java.io.File(engine.project.saveDir, "$name.world")); true } catch (_: Exception) { false }
    }
    fun load(name: String): Boolean {
        val (_, d) = world() ?: return false
        return d.load(java.io.File(engine.project.saveDir, "$name.world"))
    }
    fun hasSave(name: String): Boolean = java.io.File(engine.project.saveDir, "$name.world").exists()
    fun deleteSave(name: String): Boolean = java.io.File(engine.project.saveDir, "$name.world").delete()
}

class SConsole(private val engine: Engine) {
    fun log(o: Any?) = engine.log(0, Context.toString(o))
    fun warn(o: Any?) = engine.log(1, "⚠ " + Context.toString(o))
    fun error(o: Any?) = engine.log(2, "✖ " + Context.toString(o))
}
