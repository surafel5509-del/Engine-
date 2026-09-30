package com.sengine.engine.model

import com.sengine.engine.math.Mat4
import com.sengine.engine.script.NativeScripts
import org.json.JSONObject
import kotlin.math.abs

/**
 * Kotlin front-end of the native Model & Animation Studio kernel (`engine/ModelKit.cpp` in libsengine):
 * bevel, loop cut, bridge, PolyGroups, UV unwrapping, humanoid rig binding and auto-animation.
 */
object ModelStudio {
    val available: Boolean get() = NativeScripts.available

    val UV_MODES = listOf("Box (world tiling)", "Planar (front)", "Cylindrical", "Spherical", "Smart (per PolyGroup charts)")

    val AUTO_ANIMATIONS: List<String> by lazy {
        val native: List<String> = try { if (available) NativeScripts.nAutoAnimationKinds().split(',').filter { it.isNotBlank() } else emptyList() } catch (_: Throwable) { emptyList() }
        (native.ifEmpty { listOf("Idle", "Walk", "Run", "Jump", "Wave", "Punch", "Dance", "Death", "Celebrate", "Crouch", "Spin", "Bounce", "Hover", "Shake", "Swing", "Pulse") }) +
            AutoAnimator.ALL_KINDS.filter { k -> (native.ifEmpty { listOf("Idle", "Walk", "Run", "Jump", "Wave", "Punch", "Dance", "Death", "Celebrate", "Crouch") }).none { it.equals(k, true) } }
    }

    private fun need() { if (!available) throw IllegalStateException("The native engine library (libsengine.so) is not available") }

    // ---------------------------------------------------------------- mesh <-> native arrays
    private class Packed(val verts: FloatArray, val faces: IntArray, val attrs: IntArray, val uvs: FloatArray)

    private fun pack(p: SPart): Packed {
        p.fixColors()
        val v = FloatArray(p.verts.size * 3)
        p.verts.forEachIndexed { i, a -> v[i * 3] = a[0]; v[i * 3 + 1] = a[1]; v[i * 3 + 2] = a[2] }
        val f = ArrayList<Int>(p.faces.size * 5)
        val uv = ArrayList<Float>()
        for ((fi, face) in p.faces.withIndex()) {
            f.add(face.size); face.forEach { f.add(it) }
            val u = p.uvs[fi]
            if (u != null) u.forEach { uv.add(it) } else repeat(face.size * 2) { uv.add(Float.NaN) }
        }
        val a = IntArray(p.faces.size * 2)
        for (i in p.faces.indices) { a[i * 2] = p.faceColors[i]; a[i * 2 + 1] = p.groups[i] }
        return Packed(v, f.toIntArray(), a, uv.toFloatArray())
    }

    private fun unpack(p: SPart, r: Array<Any?>): IntArray {
        val v = r[0] as FloatArray; val f = r[1] as IntArray; val a = r[2] as IntArray; val uv = r[3] as FloatArray
        p.verts.clear(); var i = 0
        while (i + 2 < v.size) { p.verts.add(floatArrayOf(v[i], v[i + 1], v[i + 2])); i += 3 }
        p.faces.clear(); p.faceColors.clear(); p.groups.clear(); p.uvs.clear()
        i = 0; var o = 0; var fi = 0
        while (i < f.size) {
            val n = f[i++]
            p.faces.add(IntArray(n) { f[i + it] }); i += n
            p.faceColors.add(a.getOrElse(fi * 2) { 0 }); p.groups.add(a.getOrElse(fi * 2 + 1) { 0 })
            val u = if (o + n * 2 <= uv.size && !uv[o].isNaN()) FloatArray(n * 2) { uv[o + it] } else null
            p.uvs.add(u); o += n * 2; fi++
        }
        p.fixColors()
        return r[4] as IntArray
    }

    private fun op(p: SPart, code: Int, ia: IntArray, fa: FloatArray): IntArray {
        need()
        val k = pack(p)
        return unpack(p, NativeScripts.nMeshOp(code, k.verts, k.faces, k.attrs, k.uvs, ia, fa))
    }

    /** Rounded bevel of the selected faces; returns the cap faces. */
    fun bevel(p: SPart, faces: Set<Int>, width: Float = 0.25f, depth: Float = 0.08f, segments: Int = 3): Set<Int> =
        op(p, 1, intArrayOf(segments) + faces.sorted().toIntArray(), floatArrayOf(width, depth)).toSet()

    /** Loop cut across the edge between vertices a and b; returns the number of faces cut. */
    fun loopCut(p: SPart, a: Int, b: Int, t: Float = 0.5f): Int = op(p, 2, intArrayOf(a, b), floatArrayOf(t)).firstOrNull() ?: 0

    /** Bridges two faces with a tube; returns the new faces. */
    fun bridge(p: SPart, fa: Int, fb: Int, segments: Int = 1): Set<Int> = op(p, 3, intArrayOf(fa, fb, segments), floatArrayOf()).toSet()

    /** Automatic PolyGroups by crease angle; returns the group count. */
    fun autoGroups(p: SPart, angle: Float = 30f): Int = op(p, 4, intArrayOf(), floatArrayOf(angle)).firstOrNull() ?: 0

    /** Faces in the same PolyGroup(s) as [faces] (pure Kotlin, no native call needed). */
    fun groupFaces(p: SPart, faces: Set<Int>): Set<Int> {
        p.fixColors()
        val g = faces.filter { it in p.faces.indices }.map { p.groups[it] }.toSet()
        return p.faces.indices.filter { p.groups[it] in g }.toSet()
    }

    /** Puts the selected faces into a new PolyGroup; returns its id. */
    fun newGroup(p: SPart, faces: Set<Int>): Int {
        p.fixColors()
        val id = (p.groups.maxOrNull() ?: 0) + 1
        for (f in faces) if (f in p.faces.indices) p.groups[f] = id
        return id
    }

    fun unwrap(p: SPart, mode: Int, scale: Float = 1f) { op(p, 6, intArrayOf(mode), floatArrayOf(scale)) }

    // ---------------------------------------------------------------- auto animation
    /** Generates (or replaces) a clip named [kind] for the model's current part hierarchy. */
    fun autoAnimate(m: SModel, kind: String, length: Float = 0f): SClip {
        // Native kernel first for its built-in kinds; the Kotlin AutoAnimator covers every other
        // creature / vehicle / prop kind (and acts as the fallback when libsengine is unavailable).
        if (available) {
            try {
                val names = m.parts.map { it.name }.toTypedArray()
                val parents = IntArray(m.parts.size) { m.parts[it].parent }
                val rest = FloatArray(m.parts.size * 9)
                m.parts.forEachIndexed { i, p -> p.pos.copyInto(rest, i * 9); p.rot.copyInto(rest, i * 9 + 3); p.scale.copyInto(rest, i * 9 + 6) }
                val (mn, mx) = m.bounds()
                val json = NativeScripts.nAutoAnimate(kind, names, parents, rest, (mx[1] - mn[1]).coerceAtLeast(0.01f), length)
                val clip = SClip.fromJson(JSONObject(json))
                val i = m.clips.indexOfFirst { it.name.equals(clip.name, true) }
                if (i >= 0) m.clips[i] = clip else m.clips += clip
                return clip
            } catch (_: Throwable) { /* fall through to the Kotlin generator */ }
        }
        return AutoAnimator.generate(m, kind, length)
    }
}

/** Point-and-click humanoid rigging: joint templates, automatic placement and rigid binding into a part hierarchy. */
object Rigging {
    class JointDef(val name: String, val parent: Int, val label: String)

    /** Standard humanoid skeleton (left = -X, the S Engine character convention). */
    val HUMANOID = listOf(
        JointDef("Hips", -1, "Hips / pelvis"), JointDef("Spine", 0, "Waist"), JointDef("Chest", 1, "Chest"),
        JointDef("Neck", 2, "Neck"), JointDef("Head", 3, "Head"),
        JointDef("Shoulder_L", 2, "Left shoulder"), JointDef("UpperArm_L", 5, "Left arm"), JointDef("Forearm_L", 6, "Left elbow"),
        JointDef("Hand_L", 7, "Left hand"), JointDef("Fingers_L", 8, "Left fingers"),
        JointDef("Shoulder_R", 2, "Right shoulder"), JointDef("UpperArm_R", 10, "Right arm"), JointDef("Forearm_R", 11, "Right elbow"),
        JointDef("Hand_R", 12, "Right hand"), JointDef("Fingers_R", 13, "Right fingers"),
        JointDef("Thigh_L", 0, "Left leg"), JointDef("Shin_L", 15, "Left knee"), JointDef("Foot_L", 16, "Left foot"),
        JointDef("Thigh_R", 0, "Right leg"), JointDef("Shin_R", 18, "Right knee"), JointDef("Foot_R", 19, "Right foot"),
    )

    /** Mirror partner index (L <-> R) or -1. */
    fun mirrorOf(i: Int): Int {
        val n = HUMANOID.getOrNull(i)?.name ?: return -1
        val other = when { n.endsWith("_L") -> n.dropLast(2) + "_R"; n.endsWith("_R") -> n.dropLast(2) + "_L"; else -> return -1 }
        return HUMANOID.indexOfFirst { it.name == other }
    }

    /** Guesses joint positions from the model bounds (detects arms-down vs T-pose from the proportions). */
    fun autoPlace(m: SModel): MutableList<SJoint> { val (mn, mx) = m.bounds(); return place(mn, mx) }

    /** Humanoid joint layout for a character filling the box [mn]..[mx]. */
    fun place(mn: FloatArray, mx: FloatArray): MutableList<SJoint> {
        val w = mx[0] - mn[0]; val h = (mx[1] - mn[1]).coerceAtLeast(0.01f)
        val cx = (mn[0] + mx[0]) / 2; val cz = (mn[2] + mx[2]) / 2; val y0 = mn[1]
        val tPose = w > h * 0.8f
        fun at(fx: Float, fy: Float, useW: Boolean = true) = floatArrayOf(cx + fx * (if (useW) w else h), y0 + fy * h, cz)
        val left = mutableMapOf(
            "Shoulder" to if (tPose) at(-0.06f, 0.81f) else at(-0.2f, 0.82f),
            "UpperArm" to if (tPose) at(-0.12f, 0.80f) else at(-0.38f, 0.80f),
            "Forearm" to if (tPose) at(-0.26f, 0.80f) else at(-0.41f, 0.62f),
            "Hand" to if (tPose) at(-0.40f, 0.80f) else at(-0.42f, 0.47f),
            "Fingers" to if (tPose) at(-0.46f, 0.80f) else at(-0.43f, 0.41f),
            "Thigh" to at(-0.09f, 0.49f, false), "Shin" to at(-0.09f, 0.27f, false), "Foot" to at(-0.09f, 0.05f, false),
        )
        val centre = mapOf("Hips" to at(0f, 0.50f), "Spine" to at(0f, 0.60f), "Chest" to at(0f, 0.72f), "Neck" to at(0f, 0.83f), "Head" to at(0f, 0.88f))
        return HUMANOID.map { d ->
            val base = d.name.substringBefore('_')
            val pos = when {
                d.name.endsWith("_L") -> left[base]!!.copyOf()
                d.name.endsWith("_R") -> left[base]!!.copyOf().also { it[0] = 2 * cx - it[0] }
                else -> centre[d.name]!!.copyOf()
            }
            SJoint(d.name, d.parent, pos)
        }.toMutableList()
    }

    /**
     * Rigid-binds the visible geometry to the joints: every face goes to its nearest bone (native segmentation), each
     * joint becomes a part pivoted at the joint, parented like the skeleton. Existing clips are dropped (old part names).
     */
    fun bind(m: SModel, joints: List<SJoint>): SModel {
        require(joints.isNotEmpty()) { "Place the joints first" }
        val mats = Array(m.parts.size) { FloatArray(16) }
        m.matrices(null, 0f, mats)
        val verts = ArrayList<Float>(); val faces = ArrayList<Int>()
        class F(val corners: List<FloatArray>, val color: Int, val uv: FloatArray?)
        val all = ArrayList<F>()
        val textures = HashSet<String>()
        for ((pi, p) in m.parts.withIndex()) {
            if (!p.visible) continue
            p.fixColors()
            if (p.texture.isNotBlank()) textures += p.texture
            for ((fi, f) in p.faces.withIndex()) {
                if (f.size < 3 || f.any { it !in p.verts.indices }) continue
                val cs = f.map { vi -> val v = p.verts[vi]; Mat4.point(mats[pi], v[0], v[1], v[2]) }
                faces.add(f.size)
                for (c in cs) { faces.add(verts.size / 3); verts.add(c[0]); verts.add(c[1]); verts.add(c[2]) }
                all += F(cs, p.faceColors[fi].let { if (it == 0) p.color else it }, p.uvs[fi])
            }
        }
        if (all.isEmpty()) throw IllegalStateException("Nothing to bind: the model has no visible faces")
        val jp = FloatArray(joints.size * 3); joints.forEachIndexed { i, j -> j.pos.copyInto(jp, i * 3) }
        val parents = IntArray(joints.size) { joints[it].parent }
        val seg = if (ModelStudio.available) NativeScripts.nRigSegment(verts.toFloatArray(), faces.toIntArray(), jp, parents)
            else throw IllegalStateException("The native engine library (libsengine.so) is not available")
        val out = SModel()
        for ((j, joint) in joints.withIndex()) {
            val par = joint.parent.takeIf { it in joints.indices && it != j } ?: -1
            val p = SPart(joint.name, par)
            val base = if (par >= 0) joints[par].pos else floatArrayOf(0f, 0f, 0f)
            for (k in 0 until 3) p.pos[k] = joint.pos[k] - base[k]
            val colors = HashMap<Int, Int>()
            for ((fi, f) in all.withIndex()) {
                if (seg.getOrElse(fi) { 0 } != j) continue
                val idx = IntArray(f.corners.size) { c ->
                    val w = f.corners[c]; p.verts.add(floatArrayOf(w[0] - joint.pos[0], w[1] - joint.pos[1], w[2] - joint.pos[2])); p.verts.size - 1
                }
                p.faces.add(idx); p.faceColors.add(f.color); p.groups.add(0); p.uvs.add(f.uv)
                colors[f.color] = (colors[f.color] ?: 0) + 1
            }
            colors.maxByOrNull { it.value }?.let { p.color = it.key }
            for (i in p.faceColors.indices) if (p.faceColors[i] == p.color) p.faceColors[i] = 0
            if (textures.size == 1) p.texture = textures.first()
            p.fixColors()
            ModelOps.mergeByDistance(p, 1e-4f)
            out.parts += p
        }
        out.rig.addAll(joints.map { SJoint(it.name, it.parent, it.pos.copyOf()) })
        return out
    }
}

/** `.sanim` animation files: export a clip with its rest pose, import + retarget onto any other model. */
object AnimIO {
    fun export(m: SModel, clip: SClip): String {
        val (mn, mx) = m.bounds()
        val rest = JSONObject()
        for (p in m.parts) rest.put(p.name, JSONObject().put("pos", SPart.arr(p.pos)).put("rot", SPart.arr(p.rot)).put("scale", SPart.arr(p.scale)).put("x", p.pos[0].toDouble()))
        return JSONObject().put("format", "sanim").put("version", 1).put("height", (mx[1] - mn[1]).toDouble())
            .put("clip", clip.toJson()).put("rest", rest).toString(2)
    }

    class Result(val clip: SClip, val mapped: Map<String, String>, val unmapped: List<String>)

    /** Imports a .sanim onto [m]: tracks are matched by name / humanoid role and retargeted as offsets from the rest pose. */
    fun import(m: SModel, text: String, rename: String? = null): Result {
        val o = JSONObject(text)
        require(o.optString("format") == "sanim") { "Not an S Engine animation (.sanim) file" }
        val src = SClip.fromJson(o.getJSONObject("clip"))
        val rest = o.optJSONObject("rest") ?: JSONObject()
        val (mn, mx) = m.bounds()
        val ratio = ((mx[1] - mn[1]) / o.optDouble("height", 1.0).toFloat().coerceAtLeast(0.001f)).takeIf { it.isFinite() && it > 0f } ?: 1f
        val out = SClip(rename ?: src.name, src.length, src.loop)
        val mapped = LinkedHashMap<String, String>(); val unmapped = ArrayList<String>()
        for (tr in src.tracks) {
            val r = rest.optJSONObject(tr.part)
            val target = match(m, tr.part, r?.optDouble("x", 0.0)?.toFloat() ?: 0f)
            if (target == null) { unmapped += tr.part; continue }
            if (out.tracks.any { it.part == target.name }) continue
            mapped[tr.part] = target.name
            val sPos = SPart.farr(r?.optJSONArray("pos"), tr.keys.firstOrNull()?.pos ?: floatArrayOf(0f, 0f, 0f))
            val sRot = SPart.farr(r?.optJSONArray("rot"), floatArrayOf(0f, 0f, 0f))
            val sScale = SPart.farr(r?.optJSONArray("scale"), floatArrayOf(1f, 1f, 1f))
            val nt = out.track(target.name)
            for (k in tr.keys) nt.keys += SKey(k.t,
                FloatArray(3) { target.pos[it] + (k.pos[it] - sPos[it]) * ratio },
                FloatArray(3) { target.rot[it] + (k.rot[it] - sRot[it]) },
                FloatArray(3) { target.scale[it] * (k.scale[it] / (if (abs(sScale[it]) < 1e-5f) 1f else sScale[it])) })
        }
        val i = m.clips.indexOfFirst { it.name.equals(out.name, true) }
        if (i >= 0) m.clips[i] = out else m.clips += out
        return Result(out, mapped, unmapped)
    }

    // ---------------------------------------------------------------- name matching / retargeting
    private val ROLES = listOf(
        "forearm" to listOf("forearm", "lowerarm", "elbow"), "hand" to listOf("hand", "wrist", "palm"), "finger" to listOf("finger"),
        "shoulder" to listOf("shoulder", "clavicle"), "upperarm" to listOf("upperarm", "arm"),
        "shin" to listOf("shin", "calf", "knee", "lowerleg"), "foot" to listOf("foot", "toe", "ankle"), "thigh" to listOf("thigh", "upperleg", "leg"),
        "neck" to listOf("neck"), "head" to listOf("head", "skull"), "chest" to listOf("chest", "torso", "body", "upperspine"),
        "spine" to listOf("spine", "waist", "belly"), "hips" to listOf("hips", "hip", "pelvis", "root"),
    )

    /** Canonical "role:side" key of a part name (side from the name, else from the rest x: -X = left). */
    fun canonical(name: String, x: Float): String {
        val n = name.lowercase().filter { it.isLetterOrDigit() }
        val role = ROLES.firstOrNull { (_, keys) -> keys.any { n.contains(it) } }?.first ?: n
        val side = when {
            n.contains("left") || Regex("(?i)(^l[_. ]|[_. ]l$)").containsMatchIn(name) || Regex("[a-z0-9]L$").containsMatchIn(name) -> "L"
            n.contains("right") || Regex("(?i)(^r[_. ]|[_. ]r$)").containsMatchIn(name) || Regex("[a-z0-9]R$").containsMatchIn(name) -> "R"
            x < -1e-3f && role != n -> "L"
            x > 1e-3f && role != n -> "R"
            else -> ""
        }
        return "$role:$side"
    }

    private fun match(m: SModel, name: String, x: Float): SPart? {
        m.parts.firstOrNull { it.name == name }?.let { return it }
        m.parts.firstOrNull { it.name.equals(name, true) }?.let { return it }
        val key = canonical(name, x)
        return m.parts.firstOrNull { canonical(it.name, it.pos[0]) == key }
    }
}
