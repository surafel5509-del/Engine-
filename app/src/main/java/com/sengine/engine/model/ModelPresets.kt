package com.sengine.engine.model

import kotlin.random.Random

/** Ready-made editable models (with animations) for the Model Editor and the asset library. */
object ModelPresets {
    val NAMES = listOf("Low-poly Car", "Character (walk/idle)", "Tree", "House", "Sword", "Rock", "Spaceship", "Robot Turret")

    fun build(i: Int): SModel = when (i) {
        0 -> car(); 1 -> character(); 2 -> tree(); 3 -> house(); 4 -> sword(); 5 -> rock(); 6 -> ship(); else -> turret()
    }

    private fun part(kind: Int, name: String, color: Int, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, parent: Int = -1, segs: Int = 16): SPart =
        SModel.primitive(kind, segs).also {
            it.name = name; it.color = color; it.parent = parent
            it.pos[0] = x; it.pos[1] = y; it.pos[2] = z; it.scale[0] = sx; it.scale[1] = sy; it.scale[2] = sz
        }

    /** Bakes a part's scale into its vertices so children don't inherit non-uniform scale. */
    private fun bake(p: SPart): SPart { ModelOps.applyScale(p); return p }

    private fun key(t: Float, px: Float = 0f, py: Float = 0f, pz: Float = 0f, rx: Float = 0f, ry: Float = 0f, rz: Float = 0f, s: Float = 1f) =
        SKey(t, floatArrayOf(px, py, pz), floatArrayOf(rx, ry, rz), floatArrayOf(s, s, s))

    fun car(): SModel {
        val m = SModel()
        val body = part(0, "Body", 0xFFE53935.toInt(), 0f, 0.45f, 0f, 1.8f, 0.45f, 3.6f)
        // taper the front of the body a little (hood)
        for (v in body.verts) if (v[2] > 0 && v[1] > 0) v[1] -= 0.25f
        m.parts += body
        val cabin = part(0, "Cabin", 0xFF263238.toInt(), 0f, 0.9f, -0.35f, 1.55f, 0.45f, 1.7f)
        for (v in cabin.verts) if (v[1] > 0) { v[2] *= 0.7f; v[0] *= 0.9f }
        m.parts += cabin
        val wheelPos = listOf(floatArrayOf(-0.95f, 0.35f, 1.15f), floatArrayOf(0.95f, 0.35f, 1.15f), floatArrayOf(-0.95f, 0.35f, -1.15f), floatArrayOf(0.95f, 0.35f, -1.15f))
        wheelPos.forEachIndexed { k, p ->
            m.parts += part(2, "Wheel${k + 1}", 0xFF212121.toInt(), p[0], p[1], p[2], 0.7f, 0.28f, 0.7f, segs = 14).also { it.rot[2] = 90f }
        }
        m.parts += part(0, "Spoiler", 0xFF212121.toInt(), 0f, 0.95f, -1.7f, 1.6f, 0.08f, 0.3f)
        m.parts += part(0, "LightL", 0xFFFFF59D.toInt(), -0.6f, 0.45f, 1.81f, 0.3f, 0.12f, 0.05f)
        m.parts += part(0, "LightR", 0xFFFFF59D.toInt(), 0.6f, 0.45f, 1.81f, 0.3f, 0.12f, 0.05f)
        val drive = SClip("Drive", 0.5f, true)
        for (k in 1..4) {
            val w = m.parts[k + 1]
            drive.track(w.name).keys += listOf(
                SKey(0f, w.pos.copyOf(), floatArrayOf(0f, 0f, 90f), w.scale.copyOf()),
                SKey(0.25f, w.pos.copyOf(), floatArrayOf(180f, 0f, 90f), w.scale.copyOf()),
                SKey(0.4999f, w.pos.copyOf(), floatArrayOf(359f, 0f, 90f), w.scale.copyOf()))
        }
        m.clips += drive
        return m
    }

    fun character(): SModel {
        val m = SModel()
        val skin = 0xFFFFCC80.toInt(); val shirt = 0xFF1E88E5.toInt(); val pants = 0xFF37474F.toInt()
        m.parts += bake(part(0, "Torso", shirt, 0f, 1.15f, 0f, 0.7f, 0.8f, 0.4f))           // 0
        m.parts += bake(part(0, "Head", skin, 0f, 0.65f, 0f, 0.55f, 0.55f, 0.55f, parent = 0)) // 1
        m.parts += part(0, "ArmL", shirt, -0.5f, 0.1f, 0f, 0.22f, 0.75f, 0.22f, parent = 0)   // 2
        m.parts += part(0, "ArmR", shirt, 0.5f, 0.1f, 0f, 0.22f, 0.75f, 0.22f, parent = 0)    // 3
        m.parts += part(0, "LegL", pants, -0.18f, 0.6f, 0f, 0.26f, 0.8f, 0.26f)               // 4
        m.parts += part(0, "LegR", pants, 0.18f, 0.6f, 0f, 0.26f, 0.8f, 0.26f)                // 5
        // move limb pivots to the top (shoulder / hip) so rotations swing naturally
        for (i in listOf(2, 3, 4, 5)) { for (v in m.parts[i].verts) v[1] -= 0.5f; m.parts[i].pos[1] += if (i < 4) 0.3f else 0.2f }
        m.parts += part(0, "EyeL", 0xFF212121.toInt(), -0.12f, 0.05f, 0.28f, 0.1f, 0.1f, 0.04f, parent = 1)
        m.parts += part(0, "EyeR", 0xFF212121.toInt(), 0.12f, 0.05f, 0.28f, 0.1f, 0.1f, 0.04f, parent = 1)
        fun limb(clip: SClip, i: Int, angles: List<Pair<Float, Float>>) {
            val p = m.parts[i]
            clip.track(p.name).keys += angles.map { (t, a) -> SKey(t, p.pos.copyOf(), floatArrayOf(a, 0f, 0f), p.scale.copyOf()) }
        }
        val walk = SClip("Walk", 0.8f, true)
        limb(walk, 2, listOf(0f to 35f, 0.4f to -35f)); limb(walk, 3, listOf(0f to -35f, 0.4f to 35f))
        limb(walk, 4, listOf(0f to -30f, 0.4f to 30f)); limb(walk, 5, listOf(0f to 30f, 0.4f to -30f))
        walk.track("Torso").keys += listOf(SKey(0f, m.parts[0].pos.copyOf(), floatArrayOf(0f, 0f, 0f), m.parts[0].scale.copyOf()),
            SKey(0.2f, floatArrayOf(0f, 1.2f, 0f), floatArrayOf(0f, 0f, 0f), m.parts[0].scale.copyOf()),
            SKey(0.4f, m.parts[0].pos.copyOf(), floatArrayOf(0f, 0f, 0f), m.parts[0].scale.copyOf()),
            SKey(0.6f, floatArrayOf(0f, 1.2f, 0f), floatArrayOf(0f, 0f, 0f), m.parts[0].scale.copyOf()))
        val idle = SClip("Idle", 2f, true)
        idle.track("Torso").keys += listOf(SKey(0f, m.parts[0].pos.copyOf(), floatArrayOf(0f, 0f, 0f), m.parts[0].scale.copyOf()),
            SKey(1f, m.parts[0].pos.copyOf(), floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 1.03f, 1f)))
        limb(idle, 1, listOf(0f to 0f, 1f to 6f))
        val wave = SClip("Wave", 1f, true)
        m.parts[3].let { p -> wave.track(p.name).keys += listOf(SKey(0f, p.pos.copyOf(), floatArrayOf(0f, 0f, 150f), p.scale.copyOf()), SKey(0.5f, p.pos.copyOf(), floatArrayOf(0f, 0f, 110f), p.scale.copyOf())) }
        m.clips += listOf(idle, walk, wave)
        return m
    }

    fun tree(): SModel {
        val m = SModel()
        m.parts += part(2, "Trunk", 0xFF6D4C41.toInt(), 0f, 0.6f, 0f, 0.3f, 1.2f, 0.3f, segs = 8)
        m.parts += part(3, "Leaves1", 0xFF2E7D32.toInt(), 0f, 1.6f, 0f, 1.6f, 1.4f, 1.6f, segs = 10)
        m.parts += part(3, "Leaves2", 0xFF388E3C.toInt(), 0f, 2.3f, 0f, 1.2f, 1.1f, 1.2f, segs = 10)
        m.parts += part(3, "Leaves3", 0xFF43A047.toInt(), 0f, 2.9f, 0f, 0.8f, 0.9f, 0.8f, segs = 10)
        val sway = SClip("Sway", 3f, true)
        for (i in 1..3) { val p = m.parts[i]; sway.track(p.name).keys += listOf(SKey(0f, p.pos.copyOf(), floatArrayOf(0f, 0f, -3f), p.scale.copyOf()), SKey(1.5f, p.pos.copyOf(), floatArrayOf(0f, 0f, 3f), p.scale.copyOf())) }
        m.clips += sway
        return m
    }

    fun house(): SModel {
        val m = SModel()
        m.parts += part(0, "Walls", 0xFFFFE0B2.toInt(), 0f, 1f, 0f, 3f, 2f, 2.5f)
        m.parts += part(6, "Roof", 0xFFB71C1C.toInt(), 0f, 2.6f, 0f, 3.4f, 1.2f, 2.9f).also {
            // make the wedge a symmetric gable: move the ridge to the middle
            for (v in it.verts) if (v[1] > 0) v[2] = 0f
        }
        m.parts += part(0, "Door", 0xFF5D4037.toInt(), 0f, 0.6f, 1.26f, 0.6f, 1.2f, 0.05f)
        m.parts += part(0, "WindowL", 0xFF81D4FA.toInt(), -0.9f, 1.2f, 1.26f, 0.5f, 0.5f, 0.05f)
        m.parts += part(0, "WindowR", 0xFF81D4FA.toInt(), 0.9f, 1.2f, 1.26f, 0.5f, 0.5f, 0.05f)
        m.parts += part(0, "Chimney", 0xFF757575.toInt(), 0.9f, 3f, -0.5f, 0.35f, 0.9f, 0.35f)
        return m
    }

    fun sword(): SModel {
        val m = SModel()
        val blade = part(0, "Blade", 0xFFCFD8DC.toInt(), 0f, 1.1f, 0f, 0.16f, 1.6f, 0.04f)
        for (v in blade.verts) if (v[1] > 0) { v[0] *= 0.35f }
        // pivot at the grip so the whole sword swings from the hand
        for (v in blade.verts) v[1] += 0.69f
        blade.pos[1] = 0f
        m.parts += bake(blade)
        m.parts += part(0, "Guard", 0xFFFFC107.toInt(), 0f, 0.28f, 0f, 0.6f, 0.08f, 0.12f, parent = 0)
        m.parts += part(2, "Grip", 0xFF4E342E.toInt(), 0f, 0.05f, 0f, 0.08f, 0.4f, 0.08f, parent = 0, segs = 8)
        m.parts += part(4, "Pommel", 0xFFFFC107.toInt(), 0f, -0.18f, 0f, 0.14f, 0.14f, 0.14f, parent = 0, segs = 10)
        val swing = SClip("Swing", 0.6f, false)
        swing.track("Blade").keys += listOf(key(0f, rx = 0f), key(0.25f, rx = -100f), key(0.6f, rx = 0f))
        m.clips += swing
        return m
    }

    fun rock(seed: Int = 7): SModel {
        val m = SModel()
        val p = SModel.primitive(0).also { it.name = "Rock"; it.color = 0xFF8D8D8D.toInt() }
        ModelOps.subdivide(p, true); ModelOps.subdivide(p, true)
        val r = Random(seed)
        for (v in p.verts) { val k = 0.85f + r.nextFloat() * 0.35f; v[0] *= k * 1.3f; v[1] *= k * 0.8f; v[2] *= k }
        p.smooth = false
        m.parts += p
        return m
    }

    fun ship(): SModel {
        val m = SModel()
        val hull = part(3, "Hull", 0xFFECEFF1.toInt(), 0f, 0f, 0f, 0.8f, 2.4f, 0.8f, segs = 8).also { it.rot[0] = 90f }
        m.parts += hull
        m.parts += part(6, "WingL", 0xFF3949AB.toInt(), -0.9f, 0f, -0.4f, 1.2f, 0.1f, 1.2f).also { it.rot[1] = 180f }
        m.parts += part(6, "WingR", 0xFF3949AB.toInt(), 0.9f, 0f, -0.4f, 1.2f, 0.1f, 1.2f).also { it.rot[1] = 180f }
        m.parts += part(4, "Cockpit", 0xFF4FC3F7.toInt(), 0f, 0.3f, 0.3f, 0.45f, 0.35f, 0.8f)
        m.parts += part(2, "Engine", 0xFFFF7043.toInt(), 0f, 0f, -1.25f, 0.45f, 0.2f, 0.45f, segs = 10).also { it.rot[0] = 90f }
        val hover = SClip("Hover", 2f, true)
        for (p in m.parts) {
            hover.track(p.name).keys += listOf(SKey(0f, p.pos.copyOf(), p.rot.copyOf(), p.scale.copyOf()),
                SKey(1f, floatArrayOf(p.pos[0], p.pos[1] + 0.15f, p.pos[2]), p.rot.copyOf(), p.scale.copyOf()))
        }
        m.clips += hover
        return m
    }

    fun turret(): SModel {
        val m = SModel()
        m.parts += bake(part(2, "Base", 0xFF455A64.toInt(), 0f, 0.2f, 0f, 1.4f, 0.4f, 1.4f, segs = 12))
        m.parts += bake(part(4, "Head", 0xFF607D8B.toInt(), 0f, 0.55f, 0f, 1f, 0.8f, 1f, parent = 0))
        m.parts += part(2, "Barrel", 0xFF263238.toInt(), 0f, 0.1f, 0.6f, 0.18f, 1f, 0.18f, parent = 1, segs = 8).also { it.rot[0] = 90f }
        m.parts += part(4, "Eye", 0xFFFF1744.toInt(), 0f, 0.2f, 0.45f, 0.2f, 0.2f, 0.2f, parent = 1)
        val scan = SClip("Scan", 4f, true)
        m.parts[1].let { p -> scan.track(p.name).keys += listOf(key(0f, p.pos[0], p.pos[1], p.pos[2], ry = -60f), key(2f, p.pos[0], p.pos[1], p.pos[2], ry = 60f)) }
        m.clips += scan
        return m
    }
}
