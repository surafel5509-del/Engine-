package com.sengine.engine.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * v7 Automatic Animation Studio. One tap gives any model a full animation set:
 *
 *  - **Humanoid** — Idle, Walk, Run, Jump, Wave, Punch, Dance, Death, Celebrate, Crouch, Cheer,
 *    Clap, Taunt, Salute, Sit, Sleep, Attack, Hit, Carry, Push, Backflip, Spin, Bounce, Hover, Shake, Swing, Pulse
 *  - **Quadruped** (dogs, cats, horses, monsters) — Idle, Walk, Trot, Run, Sit, Eat, Attack, Jump, Death
 *  - **Bird** — Idle, Flap, Glide, Peck, Land, Take Off, Death
 *  - **Fish / sea creature** — Swim, Turn, Death
 *  - **Vehicle** (cars, trucks, tanks) — Idle, Drive, Brake, Drift, Crash, Death
 *  - **Prop / object** — Float, Spin, Wobble, Shake, Pulse, Bounce, Hover, Swing
 *
 * Clips are plain [SClip]s keyed on the bound part hierarchy (rigid skinning from the Model
 * Editor), so they play anywhere through MeshRenderer animation + `self.play(clip)` in scripts.
 * Roles are matched by part-name (works with any naming convention, like `.sanim` retargeting).
 */
object AutoAnimator {

    enum class Archetype(val label: String, val hint: String) {
        HUMANOID("Humanoid", "people, zombies, robots, aliens"),
        QUADRUPED("Quadruped", "dogs, cats, horses, lions, monsters"),
        BIRD("Bird", "birds, bats, dragons, chickens"),
        FISH("Fish", "fish, sharks, whales, snakes"),
        VEHICLE("Vehicle", "cars, trucks, tanks, karts"),
        PROP("Prop", "crates, lamps, rocks, pickups, anything"),
    }

    // =================================================================== detection
    fun detect(m: SModel): Archetype {
        val names = m.parts.map { it.name.lowercase() }
        fun has(v: String) = names.any { it.contains(v) }
        val wheels = names.count { it.contains("wheel") || it.contains("tire") || it.contains("tyre") }
        if (wheels >= 2) return Archetype.VEHICLE
        if (has("wing") || has("feather") || has("beak")) return Archetype.BIRD
        if (has("fin") || has("tailfin") || has("gill")) return Archetype.FISH
        val legs = names.count { it.contains("thigh") || it.contains("upperleg") || it.contains("leg") }
        val arms = names.count { it.contains("arm") || it.contains("hand") }
        if (legs >= 2 && arms >= 2) return Archetype.HUMANOID
        if (legs >= 4) return Archetype.QUADRUPED
        if (has("paw") || has("tail") || has("snout") || has("muzzle") || has("horn")) return Archetype.QUADRUPED
        val (mn, mx) = m.bounds()
        val w = mx[0] - mn[0]; val h = (mx[1] - mn[1]).coerceAtLeast(0.01f); val d = mx[2] - mn[2]
        if (h > w * 1.3f && h > d * 1.3f) return Archetype.HUMANOID
        if (w > h * 1.6f || d > h * 1.6f) return Archetype.VEHICLE
        return Archetype.PROP
    }

    // =================================================================== rig layouts
    private class JDef(val name: String, val parent: Int, val fx: Float, val fy: Float, val fz: Float, val useW: Boolean)

    /** Joint layout for [a]; every model can be rigged + fully animated in one tap. */
    fun joints(a: Archetype, mn: FloatArray, mx: FloatArray): List<SJoint> {
        val w = (mx[0] - mn[0]).coerceAtLeast(0.01f); val h = (mx[1] - mn[1]).coerceAtLeast(0.01f); val d = (mx[2] - mn[2]).coerceAtLeast(0.01f)
        val cx = (mn[0] + mx[0]) / 2f; val cz = (mn[2] + mx[2]) / 2f; val y0 = mn[1]
        fun at(j: JDef): FloatArray {
            val s = if (j.useW) w else h
            return floatArrayOf(cx + j.fx * s, y0 + j.fy * h, cz + j.fz * (if (j.useW) d else h))
        }
        val defs: List<JDef> = when (a) {
            Archetype.HUMANOID -> return Rigging.place(mn, mx)
            Archetype.QUADRUPED -> listOf(
                JDef("Root", -1, 0f, 0.62f, 0f, false),
                JDef("Spine", 0, 0f, 0.70f, 0f, false),
                JDef("Chest", 1, 0f, 0.74f, 0.12f, false),
                JDef("Neck", 2, 0f, 0.80f, 0.30f, false),
                JDef("Head", 3, 0f, 0.86f, 0.42f, false),
                JDef("Jaw", 4, 0f, 0.82f, 0.44f, false),
                JDef("Tail", 0, 0f, 0.66f, -0.34f, false),
                JDef("TailTip", 6, 0f, 0.70f, -0.52f, false),
                JDef("FrontLeg_L", 2, -0.22f, 0.62f, 0.34f, false),
                JDef("FrontPaw_L", 8, -0.22f, 0.06f, 0.34f, false),
                JDef("FrontLeg_R", 2, 0.22f, 0.62f, 0.34f, false),
                JDef("FrontPaw_R", 10, 0.22f, 0.06f, 0.34f, false),
                JDef("BackLeg_L", 0, -0.24f, 0.60f, -0.30f, false),
                JDef("BackPaw_L", 12, -0.24f, 0.05f, -0.32f, false),
                JDef("BackLeg_R", 0, 0.24f, 0.60f, -0.30f, false),
                JDef("BackPaw_R", 14, 0.24f, 0.05f, -0.32f, false),
            )
            Archetype.BIRD -> listOf(
                JDef("Root", -1, 0f, 0.55f, 0f, false),
                JDef("Chest", 0, 0f, 0.66f, 0.05f, false),
                JDef("Neck", 1, 0f, 0.78f, 0.05f, false),
                JDef("Head", 2, 0f, 0.88f, 0.04f, false),
                JDef("Beak", 3, 0f, 0.86f, 0.12f, false),
                JDef("TailFeathers", 0, 0f, 0.58f, -0.30f, false),
                JDef("Wing_L", 1, -0.18f, 0.70f, 0f, true),
                JDef("WingTip_L", 6, -0.45f, 0.70f, 0f, true),
                JDef("Wing_R", 1, 0.18f, 0.70f, 0f, true),
                JDef("WingTip_R", 8, 0.45f, 0.70f, 0f, true),
                JDef("Leg_L", 0, -0.08f, 0.30f, 0f, false),
                JDef("Foot_L", 10, -0.08f, 0.02f, 0f, false),
                JDef("Leg_R", 0, 0.08f, 0.30f, 0f, false),
                JDef("Foot_R", 12, 0.08f, 0.02f, 0f, false),
            )
            Archetype.FISH -> listOf(
                JDef("Root", -1, 0f, 0.5f, 0f, false),
                JDef("Body", 0, 0f, 0.5f, 0.1f, false),
                JDef("Head", 1, 0f, 0.52f, 0.38f, false),
                JDef("TailFin", 0, 0f, 0.5f, -0.42f, false),
                JDef("DorsalFin", 1, 0f, 0.72f, 0f, false),
                JDef("Fin_L", 1, -0.30f, 0.45f, 0.1f, true),
                JDef("Fin_R", 1, 0.30f, 0.45f, 0.1f, true),
            )
            Archetype.VEHICLE -> listOf(
                JDef("Chassis", -1, 0f, 0.42f, 0f, false),
                JDef("Cabin", 0, 0f, 0.78f, -0.05f, false),
                JDef("Wheel_FL", 0, -0.42f, 0.16f, 0.32f, true),
                JDef("Wheel_FR", 0, 0.42f, 0.16f, 0.32f, true),
                JDef("Wheel_RL", 0, -0.42f, 0.16f, -0.32f, true),
                JDef("Wheel_RR", 0, 0.42f, 0.16f, -0.32f, true),
            )
            Archetype.PROP -> listOf(JDef("Root", -1, 0f, 0f, 0f, true))
        }
        return defs.map { j -> SJoint(j.name, j.parent, at(j)) }
    }

    /** Detects the archetype, returns the rig; used by the one-tap rig+animate flow. */
    fun autoRig(m: SModel): Pair<Archetype, List<SJoint>> {
        val a = detect(m)
        val (mn, mx) = m.bounds()
        return a to joints(a, mn, mx)
    }

    // =================================================================== animation kinds
    fun kindsFor(a: Archetype): List<String> = when (a) {
        Archetype.HUMANOID -> HUMANOID_KINDS
        Archetype.QUADRUPED -> listOf("Idle", "Walk", "Trot", "Run", "Sit", "Eat", "Attack", "Jump", "Death")
        Archetype.BIRD -> listOf("Idle", "Flap", "Glide", "Peck", "Land", "Take Off", "Death")
        Archetype.FISH -> listOf("Swim", "Turn", "Death")
        Archetype.VEHICLE -> listOf("Idle", "Drive", "Brake", "Drift", "Crash", "Death")
        Archetype.PROP -> listOf("Float", "Spin", "Wobble", "Shake", "Pulse", "Bounce", "Hover", "Swing")
    }

    val HUMANOID_KINDS = listOf(
        "Idle", "Walk", "Run", "Jump", "Wave", "Punch", "Dance", "Death", "Celebrate", "Crouch",
        "Cheer", "Clap", "Taunt", "Salute", "Sit", "Sleep", "Attack", "Hit", "Carry", "Push", "Backflip",
        "Spin", "Bounce", "Hover", "Shake", "Swing", "Pulse",
    )

    val ALL_KINDS: List<String> get() = HUMANOID_KINDS + kindsFor(Archetype.QUADRUPED) + kindsFor(Archetype.BIRD) +
        kindsFor(Archetype.FISH) + kindsFor(Archetype.VEHICLE) + kindsFor(Archetype.PROP)

    /** Generates (or replaces) a clip on [m]. Falls back gracefully for unknown kinds. */
    fun generate(m: SModel, kind: String, lengthHint: Float = 0f): SClip {
        val archetype = detect(m)
        val clip = build(m, kind, lengthHint)
        val i = m.clips.indexOfFirst { it.name.equals(clip.name, true) }
        if (i >= 0) m.clips[i] = clip else m.clips += clip
        return clip
    }

    /** One-tap: generates the entire set for the (detected) archetype. Returns the clip names. */
    fun generateAll(m: SModel, archetype: Archetype = detect(m)): List<String> {
        val made = ArrayList<String>()
        for (k in kindsFor(archetype)) { generate(m, k); made.add(k) }
        return made
    }

    /** Rig + full animation set in one call (the Model Editor "⚡ Auto Animate" button). */
    fun autoAnimateEverything(m: SModel, bind: (List<SJoint>) -> SModel): List<String> {
        val (archetype, js) = autoRig(m)
        val bound = if (m.rig.size == js.size && m.parts.size == js.size) m else bind(js)
        bound.rig.clear(); bound.rig.addAll(js)
        return generateAll(bound, archetype)
    }

    // =================================================================== builder helpers
    private class B(val clip: SClip) {
        val rest = HashMap<String, FloatArray>()
        fun rot(part: String, t: Float, x: Float, y: Float, z: Float) {
            val r = rest[part] ?: floatArrayOf(0f, 0f, 0f)
            clip.track(part).keys.add(SKey(t, r.copyOf(), floatArrayOf(r[0] + x, r[1] + y, r[2] + z), floatArrayOf(1f, 1f, 1f)))
        }
        fun rotS(part: String, t: Float, x: Float, y: Float, z: Float, s: Float = 1f) {
            val r = rest[part] ?: floatArrayOf(0f, 0f, 0f)
            clip.track(part).keys.add(SKey(t, r.copyOf(), floatArrayOf(r[0] + x, r[1] + y, r[2] + z), floatArrayOf(s, s, s)))
        }
        fun pos(part: String, t: Float, dx: Float, dy: Float, dz: Float) {
            val r = rest[part] ?: floatArrayOf(0f, 0f, 0f)
            clip.track(part).keys.add(SKey(t, floatArrayOf(r[0] + dx, r[1] + dy, r[2] + dz), r.copyOf(), floatArrayOf(1f, 1f, 1f)))
        }
        fun pose(part: String, t: Float, dx: Float, dy: Float, dz: Float, x: Float, y: Float, z: Float) {
            val r = rest[part] ?: floatArrayOf(0f, 0f, 0f)
            clip.track(part).keys.add(SKey(t, floatArrayOf(r[0] + dx, r[1] + dy, r[2] + dz), floatArrayOf(r[0] + x, r[1] + y, r[2] + z), floatArrayOf(1f, 1f, 1f)))
        }
    }

    /** Left/right + role matching with the same tolerance as .sanim retargeting. */
    private class Finder(names: List<String>) {
        val lower = names.map { it.lowercase().filter { c -> c.isLetterOrDigit() } }
        fun find(role: String, side: String = ""): String? {
            fun cands(role: String) = when (role) {
                "thigh" -> listOf("thigh", "upperleg", "frontleg", "backleg", "leg")
                "shin" -> listOf("shin", "lowerleg", "knee", "paw", "foot")
                "foot" -> listOf("foot", "paw")
                "upperarm" -> listOf("upperarm", "arm", "wing")
                "forearm" -> listOf("forearm", "lowerarm", "wingtip", "elbow")
                "hand" -> listOf("hand", "wrist")
                "shoulder" -> listOf("shoulder", "clavicle")
                "hips" -> listOf("hips", "root", "pelvis", "chassis", "rootbody")
                "spine" -> listOf("spine", "waist", "body")
                "chest" -> listOf("chest", "torso")
                "neck" -> listOf("neck")
                "head" -> listOf("head", "cabin")
                "tail" -> listOf("tail", "tailtip", "tailfeathers")
                "wheel" -> listOf("wheel", "tire", "tyre")
                else -> listOf(role)
            }
            if (side.isNotBlank()) {
                val s = lower.indices.filter { i ->
                    val n = names[i].lowercase()
                    when (side) { "L" -> n.endsWith("_l") || n.contains("left"); "R" -> n.endsWith("_r") || n.contains("right"); else -> true }
                }
                for (c in cands(role)) s.firstOrNull { lower[it].contains(c) }?.let { return names[it] }
            }
            for (c in cands(role)) lower.firstOrNull { it.contains(c) }?.let { return names[it] }
            return null
        }
    }

    private fun build(m: SModel, kindIn: String, lengthHint: Float): SClip {
        val names = m.parts.map { it.name }
        val f = Finder(names)
        val height = m.bounds().let { (mn, mx) -> (mx[1] - mn[1]).coerceAtLeast(0.01f) }
        val clip = SClip(kindIn, if (lengthHint > 0f) lengthHint else 1f, true)
        val b = B(clip)
        for (p in m.parts) b.rest[p.name] = p.pos.copyOf()

        fun leg(t: Float, phase: Float, amp: Float = 28f, name: String = "leg"): Boolean {
            val t1 = f.find("thigh", name) ?: return false
            val t2 = f.find("shin", name)
            b.rot(t1, t, amp * sin(phase), 0f, 0f)
            if (t2 != null) b.rot(t2, t, amp * 0.8f * (1f - cos(phase)) * 0.5f, 0f, 0f)
            return true
        }
        fun arm(t: Float, phase: Float, amp: Float = 24f, side: String): Boolean {
            val a1 = f.find("upperarm", side) ?: return false
            val a2 = f.find("forearm", side)
            b.rot(a1, t, amp * sin(phase), 0f, 0f)
            if (a2 != null) b.rot(a2, t, 12f + 10f * sin(phase), 0f, 0f)
            return true
        }
        val rootPart: String? = f.find("hips") ?: f.find("spine") ?: names.firstOrNull()
        fun root(t: Float, dy: Float = 0f, rx: Float = 0f, ry: Float = 0f, rz: Float = 0f) {
            rootPart?.let { b.pose(it, t, 0f, dy * height, 0f, rx, ry, rz) }
        }
        fun headTurn(t: Float, ry: Float, rx: Float = 0f) { f.find("head")?.let { b.rot(it, t, rx, ry, 0f) } }
        fun tailWave(t: Float, length: Float, amp: Float = 18f) {
            val tail = f.find("tail") ?: return
            b.rot(tail, t, 0f, amp * sin(2.0 * PI * t / length), 0f)
        }
        fun wheelSpin(t: Float, length: Float, turns: Float = 2f) {
            for (w in names.filter { it.lowercase().contains("wheel") || it.lowercase().contains("tire") || it.lowercase().contains("tyre") })
                b.rot(w, t, 360f * turns * t / length, 0f, 0f)
        }

        val k = kindIn.lowercase()
        val isQuad = f.find("thigh") != null && f.find("upperarm") == null && f.find("wheel") == null
        val cycle = { amp: Float, len: Float, knee: Float ->
            for (s in 0..6) {
                val t = len * s / 6; val p = 2 * PI.toFloat() * t / len
                if (isQuad) {
                    f.find("thigh", "L")?.let { b.rot(it, t, amp * sin(p), 0f, 0f) }
                    f.find("thigh", "R")?.let { b.rot(it, t, amp * sin(p + PI), 0f, 0f) }
                    f.find("shin", "L")?.let { b.rot(it, t, knee * (1f - cos(p)) * 0.5f, 0f, 0f) }
                    f.find("shin", "R")?.let { b.rot(it, t, knee * (1f - cos(p + PI)) * 0.5f, 0f, 0f) }
                } else {
                    leg(t, p, amp); leg(t, p + PI, amp, "right")
                    arm(t, p + PI, amp * 0.8f, "L"); arm(t, p, amp * 0.8f, "R")
                }
                root(t, 0.02f * abs(sin(2 * p)).toFloat(), if (amp > 35f) 8f else 0f, if (isQuad) 0f else 3f * sin(p), 0f)
                tailWave(t, len)
            }
            Unit
        }
        when {
            // ------------------------------------------------ quadruped / generic creatures first
            (k == "walk" || k == "trot" || k == "run") && isQuad -> {
                clip.length = if (k == "run") 0.6f else if (k == "trot") 0.8f else 1f
                cycle(if (k == "run") 45f else 30f, clip.length, if (k == "run") 40f else 26f)
                headTurn(0f, 0f, if (k == "run") 8f else 2f)
            }
            k == "sit" && isQuad -> { clip.length = 1f; clip.loop = false
                for (t in listOf(0f, 1f)) {
                    f.find("thigh", "L")?.let { b.rot(it, t, -70f, 0f, 0f) }; f.find("thigh", "R")?.let { b.rot(it, t, -70f, 0f, 0f) }
                    f.find("shin", "L")?.let { b.rot(it, t, 80f, 0f, 0f) }; f.find("shin", "R")?.let { b.rot(it, t, 80f, 0f, 0f) }
                    root(t, -0.10f, -8f, 0f, 0f); f.find("tail")?.let { b.rot(it, t, 30f, 0f, 0f) }
                } }
            k == "idle" && isQuad -> { clip.length = 2.2f
                for (s in 0..4) { val t = 2.2f * s / 4; val p = 2 * PI.toFloat() * t / 2.2f
                    root(t, 0.008f * sin(p).toFloat(), 2f * sin(p).toFloat(), 0f, 0f)
                    f.find("head")?.let { b.rot(it, t, 4f * sin(p).toFloat(), 5f * sin(p / 2f), 0f) }; tailWave(t, 2.2f, 14f) } }
            k == "eat" -> { clip.length = 1.6f; for (s in 0..6) { val t = 1.6f * s / 6; val p = 2 * PI.toFloat() * t / 1.6f
                headTurn(t, 0f, 18f + 8f * sin(2 * p).toFloat()); tailWave(t, 1.6f, 10f) } }
            // ------------------------------------------------ humanoid
            k == "idle" -> { clip.length = 2f; for (s in 0..2) { val t = 2f * s / 2; val p = 2 * PI.toFloat() * t / 2f
                root(t, 0.008f * abs(sin(p)).toFloat(), 0f, 0f, 0f); arm(t, p, 3f, "L"); arm(t, p + PI, 3f, "R"); headTurn(t, 4f * sin(PI.toFloat() * t).toFloat()) } }
            k == "walk" -> { clip.length = 1f; cycle(26f, 1f, 22f); headTurn(0f, 0f, 0f) }
            k == "run" -> { clip.length = 0.7f; cycle(48f, 0.7f, 42f); headTurn(0f, 0f, 6f) }
            k == "jump" -> { clip.length = 0.9f; clip.loop = false
                fun j(t: Float, dy: Float, crouch: Float) { root(t, dy, crouch, 0f, 0f); leg(t, 0f, 6f + crouch); leg(t, 0f, 6f + crouch, "right"); arm(t, 0f, -30f * (dy + 0.2f) * 10f, "L"); arm(t, 0f, -30f * (dy + 0.2f) * 10f, "R") }
                j(0f, 0f, 0f); j(0.2f, -0.08f, 26f); j(0.45f, 0.35f, -8f); j(0.7f, 0.05f, 12f); j(0.9f, 0f, 0f) }
            k == "wave" -> { clip.length = 1.2f; for (s in 0..4) { val t = 1.2f * s / 4
                f.find("upperarm", "R")?.let { b.rot(it, t, 0f, 0f, -140f) } ?: f.find("upperarm", "L")?.let { b.rot(it, t, 0f, 0f, 140f) }
                f.find("forearm", "R")?.let { b.rot(it, t, 0f, 20f * sin(2 * PI.toFloat() * 2 * t / 1.2f), 0f) }
                f.find("forearm", "L")?.let { b.rot(it, t, 0f, 20f * sin(2 * PI.toFloat() * 2 * t / 1.2f), 0f) }
                headTurn(t, 0f, -4f) } }
            k == "punch" || k == "attack" -> { clip.length = 0.6f; clip.loop = false
                for ((t, ext) in listOf(0f to 0f, 0.15f to 0f, 0.3f to 1f, 0.45f to 0f, 0.6f to 0f)) {
                    val a = f.find("upperarm", "R") ?: f.find("upperarm", "L") ?: continue
                    b.rot(a, t, -70f * ext - 8f, 0f, 0f); f.find("forearm", "R")?.let { b.rot(it, t, -70f + 60f * ext, 0f, 0f) }
                    f.find("forearm", "L")?.let { b.rot(it, t, -70f + 60f * ext, 0f, 0f) }
                    f.find("chest")?.let { b.rot(it, t, 0f, -18f * ext, 0f) }
                } }
            k == "dance" -> { clip.length = 1.6f; for (s in 0..6) { val t = 1.6f * s / 6; val p = 2 * PI.toFloat() * t / 1.6f
                root(t, 0.03f * sin(2 * p).toFloat(), 0f, 20f * sin(p).toFloat(), 8f * cos(p).toFloat()); arm(t, p, 35f, "L"); arm(t, p + PI, 35f, "R"); leg(t, p, 20f); leg(t, p + PI, 20f, "right") } }
            k == "death" -> { clip.length = 1.1f; clip.loop = false; for (s in 0..4) { val t = 1.1f * s / 4; val k2 = t / 1.1f
                root(t, -0.42f * k2 * k2, 80f * k2 * k2, 0f, 0f); arm(t, 0f, 60f * k2, "L"); arm(t, 0f, 60f * k2, "R"); headTurn(t, 0f, 20f * k2) } }
            k == "celebrate" || k == "cheer" -> { clip.length = 1.4f; for (s in 0..5) { val t = 1.4f * s / 5; val p = 2 * PI.toFloat() * t / 1.4f
                root(t, 0.05f * abs(sin(p)).toFloat(), 0f, 0f, 0f)
                f.find("upperarm", "L")?.let { b.rot(it, t, 0f, 0f, 150f + 20f * sin(p)) }
                f.find("upperarm", "R")?.let { b.rot(it, t, 0f, 0f, -150f - 20f * sin(p)) }
                f.find("forearm", "L")?.let { b.rot(it, t, 0f, 0f, 10f * sin(p)) }
                f.find("forearm", "R")?.let { b.rot(it, t, 0f, 0f, -10f * sin(p)) }
                headTurn(t, 0f, -10f) } }
            k == "clap" -> { clip.length = 0.8f; for (s in 0..4) { val t = 0.8f * s / 4; val p = 2 * PI.toFloat() * 2 * t / 0.8f
                f.find("upperarm", "L")?.let { b.rot(it, t, -60f, 0f, 35f * cos(p)) }
                f.find("upperarm", "R")?.let { b.rot(it, t, -60f, 0f, -35f * cos(p)) }
                f.find("forearm", "L")?.let { b.rot(it, t, -40f, 0f, 0f) }
                f.find("forearm", "R")?.let { b.rot(it, t, -40f, 0f, 0f) } } }
            k == "taunt" -> { clip.length = 1.2f; for (s in 0..4) { val t = 1.2f * s / 4; val p = 2 * PI.toFloat() * t / 1.2f
                f.find("upperarm", "R")?.let { b.rot(it, t, 0f, 0f, -120f) }
                f.find("forearm", "R")?.let { b.rot(it, t, -60f + 30f * cos(p), 0f, 0f) }
                root(t, 0.015f * sin(2 * p).toFloat(), 0f, 10f * sin(p).toFloat(), 0f) } }
            k == "salute" -> { clip.length = 1.2f; clip.loop = false; f.find("upperarm", "R")?.let {
                b.rot(it, 0f, -20f, 0f, -90f); b.rot(it, 0.35f, -40f, 0f, -140f); b.rot(it, 0.9f, -40f, 0f, -140f); b.rot(it, 1.2f, -20f, 0f, -90f) }
                headTurn(0.4f, 0f, -6f); headTurn(1.0f, 0f, 0f) }
            k == "sit" -> { clip.length = 1f; clip.loop = false; for (t in listOf(0f, 1f)) {
                f.find("thigh", "L")?.let { b.rot(it, t, -85f, 0f, 0f) }; f.find("thigh", "R")?.let { b.rot(it, t, -85f, 0f, 0f) }
                f.find("shin", "L")?.let { b.rot(it, t, 85f, 0f, 0f) }; f.find("shin", "R")?.let { b.rot(it, t, 85f, 0f, 0f) }
                root(t, -0.18f, 4f, 0f, 0f) } }
            k == "sleep" -> { clip.length = 3f; for (s in 0..2) { val t = 3f * s / 2; root(t, -0.30f, 78f, 0f, 0f); f.find("head")?.let { b.rot(it, t, 12f * sin(PI.toFloat() * t).toFloat(), 0f, 8f) } } }
            k == "crouch" -> { clip.length = 1f; clip.loop = false; for (t in listOf(0f, 0.4f, 1f)) { val c = if (t == 0.4f) 1f else 0f
                leg(t, 0f, 40f * c); leg(t, 0f, 40f * c, "right"); root(t, -0.12f * c, 12f * c, 0f, 0f); arm(t, 0f, -25f * c, "L"); arm(t, 0f, -25f * c, "R") } }
            k == "hit" -> { clip.length = 0.5f; clip.loop = false; for (s in 0..3) { val t = 0.5f * s / 3
                val k2 = if (t < 0.2f) t / 0.2f else ((0.5f - t) / 0.3f).coerceAtLeast(0f)
                root(t, 0f, 14f * k2, 10f * k2, 0f); arm(t, 0f, 20f * k2, "L"); arm(t, 0f, 20f * k2, "R"); headTurn(t, 12f * k2) } }
            k == "carry" -> { clip.length = 1f; for (s in 0..4) { val t = 1f * s / 4; val p = 2 * PI.toFloat() * t
                leg(t, p, 20f); leg(t, p + PI, 20f, "right")
                f.find("upperarm", "L")?.let { b.rot(it, t, -70f, 0f, 15f) }; f.find("upperarm", "R")?.let { b.rot(it, t, -70f, 0f, -15f) }
                f.find("forearm", "L")?.let { b.rot(it, t, -50f, 0f, 0f) }; f.find("forearm", "R")?.let { b.rot(it, t, -50f, 0f, 0f) } } }
            k == "push" -> { clip.length = 1f; for (s in 0..4) { val t = 1f * s / 4; val p = 2 * PI.toFloat() * t
                leg(t, p, 14f); leg(t, p + PI, 14f, "right")
                f.find("upperarm", "L")?.let { b.rot(it, t, -55f, 0f, 10f) }; f.find("upperarm", "R")?.let { b.rot(it, t, -55f, 0f, -10f) }
                root(t, 0f, 15f, 0f, 0f) } }
            k == "backflip" -> { clip.length = 1f; clip.loop = false; for (s in 0..6) { val t = 1f * s / 6; val air = sin(2 * PI.toFloat() * t).coerceAtLeast(0f)
                root(t, 0.5f * air, -360f * t, 0f, 0f); arm(t, 0f, -60f * air, "L"); arm(t, 0f, -60f * air, "R"); leg(t, 0f, 60f * air); leg(t, 0f, 60f * air, "right") } }
            // ---------------------------------------------------------------- generic / props
            k == "spin" -> { clip.length = 1.2f; for (s in 0..4) { val t = 1.2f * s / 4; root(t, 0f, 0f, 360f * t / 1.2f, 0f) } }
            k == "bounce" -> { clip.length = 0.8f; for (s in 0..4) { val t = 0.8f * s / 4; val p = 2 * PI.toFloat() * t / 0.8f; root(t, 0.12f * abs(sin(p)).toFloat()) } }
            k == "hover" || k == "float" -> { clip.length = 2.2f; for (s in 0..5) { val t = 2.2f * s / 5; val p = 2 * PI.toFloat() * t / 2.2f
                root(t, 0.06f * sin(p).toFloat(), 5f * sin(p).toFloat(), if (k == "float") 360f * t / 2.2f else 0f, 4f * cos(p).toFloat()) } }
            k == "wobble" -> { clip.length = 0.9f; for (s in 0..4) { val t = 0.9f * s / 4; val p = 2 * PI.toFloat() * 2 * t / 0.9f; root(t, 0f, 0f, 0f, 14f * sin(p).toFloat()) } }
            k == "shake" -> { clip.length = 0.6f; for (s in 0..6) { val t = 0.6f * s / 6; val p = 2 * PI.toFloat() * 3 * t / 0.6f; root(t, 0f, 0f, 8f * sin(p).toFloat(), 0f) } }
            k == "pulse" -> { clip.length = 1f; for (s in 0..4) { val t = 1f * s / 4; val p = 2 * PI.toFloat() * t; b.rotS(names.firstOrNull() ?: "Root", t, 0f, 0f, 0f, 1f + 0.08f * sin(p)) } }
            k == "swing" -> { clip.length = 1.6f; for (s in 0..5) { val t = 1.6f * s / 5; val p = 2 * PI.toFloat() * t / 1.6f; root(t, 0f, 0f, 25f * sin(p).toFloat(), 0f) } }
            // ---------------------------------------------------------------- birds / fish / vehicles
            k == "flap" -> { clip.length = 0.6f; for (s in 0..4) { val t = 0.6f * s / 4; val p = 2 * PI.toFloat() * 2 * t / 0.6f
                f.find("upperarm", "L")?.let { b.rot(it, t, 0f, 0f, 55f * sin(p)) }; f.find("upperarm", "R")?.let { b.rot(it, t, 0f, 0f, -55f * sin(p)) }
                f.find("forearm", "L")?.let { b.rot(it, t, 0f, 0f, 30f * sin(p)) }; f.find("forearm", "R")?.let { b.rot(it, t, 0f, 0f, -30f * sin(p)) }
                root(t, 0.04f * sin(p).toFloat()) } }
            k == "glide" -> { clip.length = 1.5f; for (s in 0..3) { val t = 1.5f * s / 3; val p = 2 * PI.toFloat() * t / 1.5f
                f.find("upperarm", "L")?.let { b.rot(it, t, 0f, 0f, 80f + 5f * sin(p)) }; f.find("upperarm", "R")?.let { b.rot(it, t, 0f, 0f, -80f - 5f * sin(p)) }
                root(t, 0.02f * sin(p).toFloat(), -6f, 0f, 0f); tailWave(t, 1.5f, 8f) } }
            k == "peck" -> { clip.length = 0.8f; for (s in 0..4) { val t = 0.8f * s / 4; headTurn(t, 0f, 30f * abs(sin(2 * PI.toFloat() * t / 0.8f))) } }
            k == "land" -> { clip.length = 0.7f; clip.loop = false; for (s in 0..3) { val t = 0.7f * s / 3; val k2 = (1f - t / 0.7f).coerceIn(0f, 1f)
                f.find("upperarm", "L")?.let { b.rot(it, t, 0f, 0f, 80f * k2) }; f.find("upperarm", "R")?.let { b.rot(it, t, 0f, 0f, -80f * k2) }
                root(t, -0.05f * k2, 8f * k2) } }
            k == "take off" -> { clip.length = 0.9f; clip.loop = false; for (s in 0..4) { val t = 0.9f * s / 4; val p = 2 * PI.toFloat() * 3 * t / 0.9f
                f.find("upperarm", "L")?.let { b.rot(it, t, 0f, 0f, 70f * sin(p)) }; f.find("upperarm", "R")?.let { b.rot(it, t, 0f, 0f, -70f * sin(p)) }
                root(t, 0.30f * t / 0.9f, -18f * t / 0.9f) } }
            k == "swim" -> { clip.length = 1.1f; for (s in 0..6) { val t = 1.1f * s / 6; val p = 2 * PI.toFloat() * t / 1.1f
                val spine = f.find("spine") ?: names.firstOrNull() ?: "Root"; b.rot(spine, t, 0f, 12f * sin(p), 0f)
                f.find("tail")?.let { b.rot(it, t, 0f, 22f * sin(p + 0.8f), 0f) }
                f.find("fin", "L")?.let { b.rot(it, t, 0f, 0f, 20f * sin(p)) }; f.find("fin", "R")?.let { b.rot(it, t, 0f, 0f, 20f * sin(p + PI)) }
                root(t, 0.02f * sin(2 * p).toFloat()) } }
            k == "turn" -> { clip.length = 1f; for (s in 0..4) { val t = 1f * s / 4; root(t, 0f, 0f, 360f * t, 0f) } }
            k == "brake" -> { clip.length = 0.8f; clip.loop = false; for (s in 0..3) { val t = 0.8f * s / 3; val k2 = (1f - t / 0.8f).coerceIn(0f, 1f)
                root(t, 0f, 6f * k2, 0f, 0f); wheelSpin(t, 0.8f, 0.6f * (1f - k2)) } }
            k == "drive" -> { clip.length = 0.5f; for (s in 0..4) { val t = 0.5f * s / 4; val p = 2 * PI.toFloat() * t / 0.5f
                wheelSpin(t, 0.5f, 1f); root(t, 0.012f * sin(2 * p).toFloat(), 2f * sin(p).toFloat()) } }
            k == "drift" -> { clip.length = 1.2f; for (s in 0..5) { val t = 1.2f * s / 5; val p = 2 * PI.toFloat() * t / 1.2f
                root(t, 0f, 2f, 22f * sin(p).toFloat(), -6f * abs(sin(p)).toFloat()); wheelSpin(t, 1.2f, 2f) } }
            k == "crash" -> { clip.length = 0.9f; clip.loop = false; for (s in 0..4) { val t = 0.9f * s / 4; val k2 = (1f - t / 0.9f).coerceIn(0f, 1f)
                root(t, -0.04f * k2, -20f * k2 * k2, 15f * k2 * k2, 12f * k2 * k2) } }
            else -> { clip.length = 2f
                for (s in 0..3) { val t = 2f * s / 3; val p = 2 * PI.toFloat() * t / 2f; root(t, 0.02f * sin(p).toFloat(), 3f * sin(p).toFloat()) } }
        }
        // de-duplicate keys per track (keep sorted)
        for (tr in clip.tracks) { tr.keys.sortBy { it.t }; val seen = HashSet<Float>(); tr.keys.removeAll { !seen.add(Math.round(it.t * 1000f) / 1000f) } }
        if (clip.tracks.isEmpty()) { // constant clip so it still plays
            val root = f.find("hips") ?: names.firstOrNull() ?: return clip
            b.pose(root, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        }
        return clip
    }
}
