package com.sengine.engine.script

import com.sengine.engine.Engine
import com.sengine.engine.core.GameObject
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper
import java.lang.reflect.Method

/** Handle of a GameObject passed across the JNI boundary. */
class NObj(@JvmField val id: Long) { override fun toString() = "GameObject#$id" }

/** std::map / plain-object values crossing the JNI boundary. */
class NDict(@JvmField val keys: Array<String>, @JvmField val values: Array<Any?>) {
    fun toMap(): Map<String, Any?> = keys.indices.associate { keys[it] to values[it] }
}

/**
 * JNI entry points of `libsengine.so` — the native C++ engine layer (C++ script VM + landscape generator).
 * On devices the library ships inside the APK for arm64-v8a / armeabi-v7a / x86_64. JVM unit tests load a host build
 * through the SENGINE_NATIVE_LIB environment variable.
 */
object NativeScripts {
    val available: Boolean by lazy {
        try { System.loadLibrary("sengine"); true } catch (_: Throwable) {
            try { System.getenv("SENGINE_NATIVE_LIB")?.let { System.load(it); true } ?: false } catch (_: Throwable) { false }
        }
    }

    fun version(): String = if (available) try { nVersion() } catch (e: Throwable) { "error: ${e.message}" } else "not loaded"

    /** Parses a C++ script and returns null + message on failure (used by the editor's "Check" button and the Game Doctor). */
    fun check(name: String, source: String): String? {
        if (!available) return "Native engine library is not available on this device"
        return try { nFreeProgram(nCompile(name, source)); null } catch (e: Throwable) { e.message ?: "error" }
    }

    @JvmStatic external fun nVersion(): String
    @JvmStatic external fun nSetHost(host: NativeHost?)
    @JvmStatic external fun nCompile(name: String, source: String): Long
    @JvmStatic external fun nFreeProgram(handle: Long)
    @JvmStatic external fun nClassName(handle: Long): String
    @JvmStatic external fun nFields(handle: Long): Array<String>
    @JvmStatic external fun nNewInstance(handle: Long, selfId: Long): Long
    @JvmStatic external fun nFreeInstance(instance: Long)
    @JvmStatic external fun nSetField(instance: Long, name: String, value: String): Boolean
    @JvmStatic external fun nGetField(instance: Long, name: String): Any?
    @JvmStatic external fun nHas(instance: Long, fn: String): Boolean
    @JvmStatic external fun nCall(instance: Long, fn: String, args: Array<Any?>): Any?
    @JvmStatic external fun nCallFunction(handle: Long, fn: String, args: Array<Any?>): Any?
    @JvmStatic external fun nTerrainHeights(ints: IntArray, floats: FloatArray): FloatArray
    @JvmStatic external fun nTerrainMesh(ints: IntArray, floats: FloatArray, heights: FloatArray): FloatArray
    // Model & Animation Studio kernel (engine/ModelKit.cpp)
    @JvmStatic external fun nMeshOp(op: Int, verts: FloatArray, faces: IntArray, attrs: IntArray, uvs: FloatArray, iargs: IntArray, fargs: FloatArray): Array<Any?>
    @JvmStatic external fun nRigSegment(verts: FloatArray, faces: IntArray, joints: FloatArray, parents: IntArray): IntArray
    @JvmStatic external fun nAutoAnimate(kind: String, names: Array<String>, parents: IntArray, rest: FloatArray, height: Float, length: Float): String
    @JvmStatic external fun nAutoAnimationKinds(): String
}

/**
 * Engine side of the C++ bridge. Every engine call made by a C++ script lands in [invoke]:
 *  * `target != 0` — a GameObject: `get:x`, `set:position`, `Destroy`, `SetPosition` …
 *  * `target == 0` — static API: `Input::AxisX`, `Scene::Find`, `get:Time::deltaTime`, `Log`, `Spawn` …
 * Names are resolved case-insensitively against the same API objects the JavaScript runtime uses, so C++ scripts get
 * the complete engine API (Unity-style `GetAxisX()` / `SetPosition()` and Unreal-style `GetActorLocation()` too).
 */
class NativeHost(private val engine: Engine, private val sys: ScriptSystem, input: SInput) {
    private val namespaces: Map<String, Any> = mapOf(
        "input" to input, "scene" to SScene(engine, sys), "time" to STime(engine), "audio" to SAudio(engine),
        "storage" to SStorage(engine), "playerprefs" to SStorage(engine), "ui" to SUI(engine), "platform" to SPlatform(engine),
        "voxel" to sys.voxelApi, "assets" to SAssets(engine), "console" to SConsole(engine), "debug" to SConsole(engine),
        "gameobject" to SScene(engine, sys), "application" to SPlatform(engine),
    )
    private val fallbackOrder = listOf("input", "scene", "audio", "ui", "time", "platform", "storage")
    private val methodCache = HashMap<String, List<Method>>()

    @Suppress("unused") // called from native code
    fun log(level: Int, msg: String) = engine.log(level, msg)

    @Suppress("unused") // called from native code
    fun invoke(target: Long, fn: String, args: Array<Any?>): Any? {
        if (target != 0L) {
            val go = engine.scene.findById(target) ?: throw RuntimeException("GameObject #$target no longer exists")
            return objectCall(go, fn, args)
        }
        var mode = ""
        var name = fn
        if (name.startsWith("get:") || name.startsWith("set:")) { mode = name.substring(0, 3); name = name.substring(4) }
        val sep = name.lastIndexOf("::")
        if (sep < 0) return globalCall(name, args)
        val ns = name.substring(0, sep).substringAfterLast("::").lowercase()
        val member = name.substring(sep + 2)
        val api = namespaces[ns] ?: throw RuntimeException("unknown engine API '$ns' (use Input, Scene, Time, Audio, UI, Storage, Platform, Voxel)")
        val m = alias(ns, member)
        return when (mode) {
            "get" -> reflect(api, m, emptyArray(), "get") ?: reflect(api, m, emptyArray(), "")
            "set" -> { reflect(api, m, args, "set"); null }
            else -> reflect(api, m, args, "")
        }.let { out(it) }
    }

    private fun alias(ns: String, m: String): String = when (ns to m.lowercase()) {
        "time" to "deltatime", "time" to "delta", "time" to "getdeltatime", "time" to "getworlddeltaseconds" -> "dt"
        "time" to "framecount" -> "frame"
        "time" to "timescale" -> "scale"
        "time" to "realtimesincestartup", "time" to "unscaled" -> "unscaledTime"
        "time" to "gettime", "time" to "now", "time" to "gettimeseconds" -> "time"
        else -> m
    }

    // ------------------------------------------------------------------ objects
    private fun objectCall(go: GameObject, fn: String, args: Array<Any?>): Any? {
        val w = sys.wrap(go)
        val lower = fn.lowercase()
        fun vec(i: Int): DoubleArray = args.getOrNull(i) as? DoubleArray ?: DoubleArray(3)
        when (lower) {
            "get:position", "getposition", "getactorlocation", "get:location" -> return doubleArrayOf(go.x.toDouble(), go.y.toDouble(), go.z.toDouble())
            "set:position", "setactorlocation", "set:location" -> { val v = vec(0); go.x = v[0].toFloat(); go.y = v[1].toFloat(); go.z = v[2].toFloat(); return null }
            "get:velocity", "getvelocity" -> return doubleArrayOf(w.getVx(), w.getVy(), w.getVz())
            "set:velocity" -> { val v = vec(0); w.setVelocity(v[0], v[1], v[2]); return null }
            "get:eulerangles", "get:rotation3", "getactorrotation" -> return doubleArrayOf(go.rotX.toDouble(), go.rotY.toDouble(), go.rotation.toDouble())
            "set:eulerangles", "set:rotation3", "setactorrotation" -> { val v = vec(0); go.rotX = v[0].toFloat(); go.rotY = v[1].toFloat(); go.rotation = v[2].toFloat(); return null }
            "get:scale", "getactorscale3d", "get:localscale" -> return doubleArrayOf(go.scaleX.toDouble(), go.scaleY.toDouble(), go.scaleZ.toDouble())
            "set:scale", "setactorscale3d", "set:localscale" -> { val v = vec(0); go.scaleX = v[0].toFloat(); go.scaleY = v[1].toFloat(); go.scaleZ = v[2].toFloat(); return null }
            "get:valid", "isvalid", "get:alive" -> return !go.destroyed
            "get:id", "getid", "getuniqueid" -> return go.id
            "comparetag", "actorhastag", "hastag" -> return go.tag == args.getOrNull(0)?.toString()
            "destroy", "k2_destroyactor", "destroyactor" -> { go.destroyed = true; return null }
            "getactorforwardvector", "get:forward" -> return out(w.forward())
            "getactorrightvector", "get:right" -> return out(w.right())
            "addactorworldoffset", "translate" -> { if (args.size == 1) { val v = vec(0); w.move(v[0], v[1], v[2]); return null } }
            "addactorworldrotation", "addactorlocalrotation" -> { val v = vec(0); w.rotate(v[0], v[1], v[2]); return null }
            "sendmessage" -> return out(sys.sendMessage(go, args.getOrNull(0)?.toString() ?: "", args.getOrNull(1)?.let { inArg(it) }))
            "setactive", "setactorhiddeningame" -> { val v = truthy(args.getOrNull(0)); go.active = if (lower == "setactive") v else !v; return null }
        }
        val mode = when { fn.startsWith("get:") -> "get"; fn.startsWith("set:") -> "set"; else -> "" }
        val member = if (mode.isEmpty()) fn else fn.substring(4)
        return when (mode) {
            "get" -> out(reflect(w, member, emptyArray(), "get"))
            "set" -> { reflect(w, member, args, "set"); null }
            else -> out(reflect(w, member, args, ""))
        }
    }

    // ------------------------------------------------------------------ globals
    private fun globalCall(fn: String, args: Array<Any?>): Any? {
        val scene = namespaces["scene"] as SScene
        fun expanded(): Array<Any?> = expandVectors(args)
        return when (fn.lowercase()) {
            "find", "findobject", "findgameobject" -> out(scene.find(args.getOrNull(0)?.toString() ?: ""))
            "findall", "findwithtag", "findgameobjectswithtag", "findallwithtag" -> out(scene.findAll(args.getOrNull(0)?.toString() ?: ""))
            "spawn", "instantiate", "spawnactor" -> out(reflect(scene, "spawn", expanded(), ""))
            "destroy" -> { (args.getOrNull(0) as? NObj)?.let { engine.scene.findById(it.id)?.destroyed = true }; null }
            "loadscene", "openlevel" -> { scene.load(args.getOrNull(0)?.toString() ?: ""); null }
            "reloadscene" -> { scene.reload(); null }
            "playsound", "playsound2d", "playsoundatlocation" -> out(reflect(namespaces["audio"]!!, "play", args.take(1).toTypedArray(), ""))
            "playmusic" -> { (namespaces["audio"] as SAudio).playMusic(args.getOrNull(0)?.toString() ?: ""); null }
            "raycast", "linetrace", "linetracesinglebychannel" -> out(reflect(scene, "raycastHit", expanded(), ""))
            "shake", "camerashake" -> { scene.shake(num(args.getOrNull(0))); null }
            "vibrate" -> { (namespaces["platform"] as SPlatform).vibrate(num(args.getOrNull(0))); null }
            "settext" -> { (namespaces["ui"] as SUI).setText(args.getOrNull(0)?.toString() ?: "", args.getOrNull(1)?.let { plain(it) }); null }
            "gettime", "now" -> engine.time
            "getdeltatime", "deltatime" -> engine.deltaTime.toDouble()
            else -> {
                for (ns in fallbackOrder) {
                    val api = namespaces[ns]!!
                    if (candidates(api, fn, args.size).isNotEmpty() || candidates(api, fn, expanded().size).isNotEmpty()) return out(reflect(api, fn, args, ""))
                }
                throw RuntimeException("unknown function '$fn' — engine APIs are Input::, Scene::, Time::, Audio::, UI::, Storage::, Platform::")
            }
        }
    }

    // ------------------------------------------------------------------ reflection
    private fun methodsOf(target: Any, lowerName: String): List<Method> =
        methodCache.getOrPut(target.javaClass.name + "#" + lowerName) {
            target.javaClass.methods.filter { it.name.lowercase() == lowerName && it.declaringClass != Any::class.java }
        }

    private val fieldCache = HashMap<String, java.lang.reflect.Field?>()
    private fun fieldOf(target: Any, lowerName: String): java.lang.reflect.Field? =
        fieldCache.getOrPut(target.javaClass.name + "." + lowerName) {
            target.javaClass.fields.firstOrNull { it.name.lowercase() == lowerName && !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        }

    private fun candidates(target: Any, name: String, arity: Int): List<Method> {
        val l = name.lowercase()
        val names = linkedSetOf(l)
        if (l.startsWith("get") && l.length > 3) names += l.substring(3)
        if (arity == 0) { names += "get$l"; names += "is$l"; if (l.startsWith("is")) names += "get" + l.substring(2) }
        if (arity == 1 && l.startsWith("set").not()) names += "set$l"
        return names.flatMap { n -> methodsOf(target, n).filter { it.parameterCount == arity } }
    }

    private fun reflect(target: Any, name: String, args: Array<Any?>, mode: String): Any? {
        val l = name.lowercase()
        val list: List<Method> = when (mode) {
            "get" -> (methodsOf(target, "get$l") + methodsOf(target, "is$l")).filter { it.parameterCount == 0 }
            "set" -> methodsOf(target, "set$l").filter { it.parameterCount == 1 }
            else -> candidates(target, name, args.size)
        }
        var callArgs = args
        var pick = list.firstOrNull { compatible(it, callArgs) }
        if (pick == null && args.any { it is DoubleArray }) {
            // Vec3 arguments expand to x, y, z: SetPosition(Vec3(1,2,3)) -> setPosition(1, 2, 3)
            callArgs = expandVectors(args)
            pick = (if (mode.isEmpty()) candidates(target, name, callArgs.size) else list).firstOrNull { compatible(it, callArgs) }
        }
        if (pick == null) {
            // public @JvmField fields (e.g. Input::AxisX / GetAxisX / input.aDown)
            val l0 = if (mode.isEmpty() && callArgs.isEmpty()) l.removePrefix("get").removePrefix("is") else l
            val fname = if (mode.isEmpty() && callArgs.size == 1 && l.startsWith("set")) l.removePrefix("set") else l0
            val field = fieldOf(target, fname) ?: fieldOf(target, l)
            if (field != null) {
                if (callArgs.isEmpty() && mode != "set") return field.get(target)
                if (callArgs.size == 1 && mode != "get") { field.set(target, convert(callArgs[0], field.type)); return null }
            }
        }
        if (pick == null) {
            val what = if (target is SObject) "GameObject" else target.javaClass.simpleName.removePrefix("S")
            val verb = when (mode) { "get" -> "has no readable property"; "set" -> "has no writable property"; else -> "has no method" }
            throw RuntimeException("$what $verb '$name' taking ${callArgs.size} argument(s)")
        }
        val converted = Array(callArgs.size) { convert(callArgs[it], pick.parameterTypes[it]) }
        return try { pick.invoke(target, *converted) } catch (e: java.lang.reflect.InvocationTargetException) {
            throw RuntimeException(e.targetException?.message ?: e.targetException?.javaClass?.simpleName ?: "engine error")
        }
    }

    private fun expandVectors(args: Array<Any?>): Array<Any?> {
        val out = ArrayList<Any?>()
        for (a in args) if (a is DoubleArray) { out += a.getOrElse(0) { 0.0 }; out += a.getOrElse(1) { 0.0 }; out += a.getOrElse(2) { 0.0 } } else out += a
        return out.toTypedArray()
    }

    private fun compatible(m: Method, args: Array<Any?>): Boolean {
        for ((i, t) in m.parameterTypes.withIndex()) {
            val a = args[i]
            val ok = when {
                t == java.lang.Double.TYPE || t == java.lang.Double::class.java || t == java.lang.Float.TYPE || t == Integer.TYPE || t == java.lang.Long.TYPE -> a is Number || a is Boolean
                t == java.lang.Boolean.TYPE || t == java.lang.Boolean::class.java -> a is Boolean || a is Number
                t == String::class.java -> a == null || a is String || a is Number
                t == SObject::class.java -> a == null || a is NObj
                else -> true
            }
            if (!ok) return false
        }
        return true
    }

    private fun convert(a: Any?, t: Class<*>): Any? = when {
        t == java.lang.Double.TYPE || t == java.lang.Double::class.java -> num(a)
        t == java.lang.Float.TYPE -> num(a).toFloat()
        t == Integer.TYPE -> num(a).toInt()
        t == java.lang.Long.TYPE -> num(a).toLong()
        t == java.lang.Boolean.TYPE || t == java.lang.Boolean::class.java -> truthy(a)
        t == String::class.java -> a?.let { if (it is Number && it.toDouble() == Math.floor(it.toDouble())) it.toLong().toString() else it.toString() }
        t == SObject::class.java -> (a as? NObj)?.let { o -> engine.scene.findById(o.id)?.let { sys.wrap(it) } }
        else -> inArg(a)
    }

    private fun num(a: Any?): Double = when (a) { is Number -> a.toDouble(); is Boolean -> if (a) 1.0 else 0.0; is String -> a.toDoubleOrNull() ?: 0.0; else -> 0.0 }
    private fun truthy(a: Any?): Boolean = when (a) { is Boolean -> a; is Number -> a.toDouble() != 0.0; null -> false; else -> true }

    /** Native value -> value for the JS-facing API objects (GameObjects become SObjects, vectors {x,y,z}). */
    private fun inArg(a: Any?): Any? = when (a) {
        is NObj -> engine.scene.findById(a.id)?.let { sys.wrap(it) }
        is Long -> a.toDouble()
        is DoubleArray -> sys.newObject(mapOf("x" to a.getOrElse(0) { 0.0 }, "y" to a.getOrElse(1) { 0.0 }, "z" to a.getOrElse(2) { 0.0 }))
        is Array<*> -> sys.newArray(a.map { inArg(it) })
        is NDict -> sys.newObject(a.toMap().mapValues { inArg(it.value) })
        else -> a
    }

    private fun plain(a: Any?): Any? = if (a is Long) a.toDouble() else a

    /** Engine value -> native value. */
    fun out(r: Any?): Any? = when (r) {
        null, Unit, is Undefined -> null
        is Wrapper -> out(r.unwrap())
        is SObject -> NObj(r.rawObject().id)
        is GameObject -> NObj(r.id)
        is Int, is Long, is Short, is Byte -> (r as Number).toLong()
        is Number -> r.toDouble()
        is Boolean, is String -> r
        is CharSequence -> r.toString()
        is NativeArray -> Array<Any?>(r.length.toInt()) { i -> out(r.get(i, r)) }
        is Scriptable -> {
            val ids = (r as? ScriptableObject)?.ids ?: r.ids
            val keys = ids.map { it.toString() }
            if (keys.toSet() == setOf("x", "y", "z")) doubleArrayOf(num(ScriptableObject.getProperty(r, "x")), num(ScriptableObject.getProperty(r, "y")), num(ScriptableObject.getProperty(r, "z")))
            else NDict(keys.toTypedArray(), Array(keys.size) { i -> out(ScriptableObject.getProperty(r, keys[i])) })
        }
        is DoubleArray, is NObj, is NDict -> r
        is Array<*> -> Array<Any?>(r.size) { out(r[it]) }
        is List<*> -> Array<Any?>(r.size) { out(r[it]) }
        else -> r.toString()
    }
}
