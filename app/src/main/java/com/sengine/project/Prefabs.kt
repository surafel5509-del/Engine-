package com.sengine.project

import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.Collider3D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Light
import com.sengine.engine.core.MeshRenderer
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SpriteRenderer
import com.sengine.engine.core.Water

/**
 * Ready-made world objects that drop straight into a scene: water, fire, weather, lights, props.
 * Script-free, so they work in any project; tweak everything afterwards in the inspector.
 */
object Prefabs {
    val PREFABS_2D = listOf("Water Pool", "Floating Crate", "Campfire", "Torch", "Rain", "Snow", "Dust Cloud", "Smoke Stack",
        "Waterfall Splash", "Magic Portal", "Explosion FX", "Falling Leaves")
    val PREFABS_3D = listOf("Lake 3D", "Floating Barrel 3D", "Campfire 3D", "Burning Barrel 3D", "Street Lamp 3D", "Fountain 3D",
        "Low-poly Tree 3D", "Hut 3D", "Rock 3D", "Rain 3D", "Snow 3D", "Crate Stack 3D")

    private fun particles(g: GameObject, preset: String, tweak: ParticleEmitter.() -> Unit = {}): ParticleEmitter =
        g.add(ParticleEmitter().also { it.applyPreset(ParticleEmitter.PRESETS.indexOf(preset)); it.tweak() })

    private fun child(s: Scene, parent: GameObject, name: String, x: Float, y: Float, z: Float = 0f): GameObject =
        s.create(name, parent).also { it.x = x; it.y = y; it.z = z }

    private fun mesh(g: GameObject, kind: Int, color: Long, sx: Float, sy: Float, sz: Float): GameObject {
        g.scaleX = sx; g.scaleY = sy; g.scaleZ = sz
        g.add(MeshRenderer().also { it.mesh = kind; it.color = color.toInt() }); return g
    }

    /** Creates prefab [name] at (x, y, z) and returns its root object. */
    fun create(s: Scene, name: String, x: Float, y: Float, z: Float = 0f): GameObject {
        val root = s.create(s.uniqueName(name), null).also { it.x = x; it.y = y; it.z = z }
        when (name) {
            // ------------------------------------------------------------ 2D
            "Water Pool" -> {
                root.add(Water().also { it.width = 10f; it.height = 3f })
                child(s, root, "Pool Floor", 0f, -1.75f).also { it.scaleX = 11f; it.scaleY = 0.5f; it.add(SpriteRenderer().also { r -> r.color = 0xFF5D4037.toInt() }); it.add(Collider2D()) }
                child(s, root, "Pool Wall L", -5.25f, 0f).also { it.scaleX = 0.5f; it.scaleY = 4f; it.add(SpriteRenderer().also { r -> r.color = 0xFF6D4C41.toInt() }); it.add(Collider2D()) }
                child(s, root, "Pool Wall R", 5.25f, 0f).also { it.scaleX = 0.5f; it.scaleY = 4f; it.add(SpriteRenderer().also { r -> r.color = 0xFF6D4C41.toInt() }); it.add(Collider2D()) }
            }
            "Floating Crate" -> {
                root.add(SpriteRenderer().also { it.color = 0xFFBF8F5A.toInt() })
                root.add(Collider2D()); root.add(Rigidbody2D().also { it.mass = 0.6f })
            }
            "Campfire" -> {
                child(s, root, "Logs", 0f, -0.35f).also { it.scaleX = 1.2f; it.scaleY = 0.25f; it.add(SpriteRenderer().also { r -> r.color = 0xFF5D4037.toInt() }) }
                particles(root, "Fire")
                child(s, root, "Smoke", 0f, 1f).also { particles(it, "Smoke") { rate = 6f } }
            }
            "Torch" -> {
                child(s, root, "Handle", 0f, -0.6f).also { it.scaleX = 0.15f; it.scaleY = 1f; it.add(SpriteRenderer().also { r -> r.color = 0xFF6D4C41.toInt() }) }
                particles(root, "Torch")
            }
            "Rain" -> { root.y += 8f; particles(root, "Rain") { spread = 6f; direction = 265f } }
            "Snow" -> { root.y += 8f; particles(root, "Snow") }
            "Dust Cloud" -> particles(root, "Dust") { rate = 30f; spread = 360f }
            "Smoke Stack" -> particles(root, "Smoke")
            "Waterfall Splash" -> particles(root, "Splash") { rate = 50f }
            "Magic Portal" -> {
                root.add(SpriteRenderer().also { it.shape = 1; it.color = 0xFFB388FF.toInt() }); root.scaleX = 2f; root.scaleY = 2f
                particles(root, "Magic")
            }
            "Explosion FX" -> particles(root, "Explosion")
            "Falling Leaves" -> { root.y += 6f; particles(root, "Leaves") }

            // ------------------------------------------------------------ 3D
            "Lake 3D" -> {
                root.add(Water().also { it.mode = 1; it.width = 20f; it.depth = 20f; it.detail = 48 })
                child(s, root, "Lake Bed", 0f, -1.5f).also { mesh(it, 0, 0xFF6D5A3F, 22f, 0.5f, 22f); it.add(Collider3D()) }
            }
            "Floating Barrel 3D" -> {
                root.y += 3f
                mesh(root, 3, 0xFF8D6E63, 0.8f, 1.1f, 0.8f); root.add(Collider3D()); root.add(Rigidbody3D().also { it.mass = 0.7f })
            }
            "Campfire 3D" -> {
                for (i in 0 until 4) child(s, root, "Log$i", 0f, 0.12f).also { mesh(it, 3, 0xFF5D4037, 0.18f, 1f, 0.18f); it.rotX = 80f; it.rotY = i * 45f }
                child(s, root, "Flames", 0f, 0.4f).also { particles(it, "Fire") }
                child(s, root, "Smoke", 0f, 1.6f).also { particles(it, "Smoke") { rate = 6f } }
                child(s, root, "Glow", 0f, 1f).also { it.add(Light().also { l -> l.kind = 1; l.color = 0xFFFF9A3C.toInt(); l.intensity = 2.2f; l.range = 9f }) }
            }
            "Burning Barrel 3D" -> {
                root.y += 0.6f
                mesh(root, 3, 0xFFC62828, 0.8f, 1.2f, 0.8f); root.add(Collider3D())
                child(s, root, "Flames", 0f, 0.7f).also { particles(it, "Fire") { startSize = 0.8f } }
                child(s, root, "Glow", 0f, 1.4f).also { it.add(Light().also { l -> l.kind = 1; l.color = 0xFFFF8A3D.toInt(); l.intensity = 2f; l.range = 8f }) }
            }
            "Street Lamp 3D" -> {
                child(s, root, "Post", 0f, 2f).also { mesh(it, 3, 0xFF37474F, 0.15f, 4f, 0.15f) }
                child(s, root, "Lamp", 0f, 4.05f).also { mesh(it, 0, 0xFFFFF3C4, 0.5f, 0.2f, 0.5f); it.get<MeshRenderer>()!!.unlit = true }
                child(s, root, "Light", 0f, 3.6f).also { it.add(Light().also { l -> l.kind = 1; l.color = 0xFFFFD08A.toInt(); l.intensity = 2f; l.range = 11f }) }
            }
            "Fountain 3D" -> {
                for ((i, c) in listOf(Triple(0f, -3f, true), Triple(0f, 3f, true), Triple(-3f, 0f, false), Triple(3f, 0f, false)).withIndex())
                    child(s, root, "Rim$i", c.first, 0.35f, c.second).also { mesh(it, 0, 0xFFE0D6C8, if (c.third) 6.4f else 0.4f, 0.7f, if (c.third) 0.4f else 6f); it.add(Collider3D()) }
                child(s, root, "Water", 0f, 0.5f).also { it.add(Water().also { w -> w.mode = 1; w.width = 5.6f; w.depth = 5.6f; w.waveHeight = 0.05f; w.waveLength = 2f; w.detail = 30 }) }
                child(s, root, "Spray", 0f, 0.8f).also { particles(it, "Splash") { rate = 40f; speed = 4f; spread = 25f } }
            }
            "Low-poly Tree 3D" -> {
                child(s, root, "Trunk", 0f, 1f).also { mesh(it, 3, 0xFF6D4C41, 0.35f, 2f, 0.35f); it.add(Collider3D()) }
                child(s, root, "Leaves", 0f, 2.9f).also { mesh(it, 4, 0xFF43A047, 2.2f, 2.6f, 2.2f) }
                child(s, root, "Leaves Top", 0f, 3.9f).also { mesh(it, 4, 0xFF66BB6A, 1.5f, 1.8f, 1.5f) }
            }
            "Hut 3D" -> {
                child(s, root, "Walls", 0f, 1.25f).also { mesh(it, 0, 0xFFD7B98E, 4f, 2.5f, 4f); it.add(Collider3D()) }
                child(s, root, "Roof", 0f, 3.2f).also { mesh(it, 7, 0xFF8D4B3A, 5f, 1.6f, 5f) }
                child(s, root, "Door", 0f, 0.8f, 2.01f).also { mesh(it, 0, 0xFF5D4037, 0.9f, 1.6f, 0.05f) }
            }
            "Rock 3D" -> { root.y += 0.4f; mesh(root, 1, 0xFF8D8D8D, 1.6f, 0.9f, 1.3f); root.add(Collider3D().also { it.shape = 1 }) }
            "Rain 3D" -> { root.y += 12f; particles(root, "Rain") }
            "Snow 3D" -> { root.y += 12f; particles(root, "Snow") }
            "Crate Stack 3D" -> {
                for ((i, c) in listOf(Triple(0f, 0.5f, 0f), Triple(1.05f, 0.5f, 0f), Triple(0.5f, 1.5f, 0f)).withIndex())
                    child(s, root, "Crate$i", c.first, c.second, c.third).also { mesh(it, 0, 0xFFB5835A, 1f, 1f, 1f); it.add(Collider3D()); it.add(Rigidbody3D()) }
            }
        }
        return root
    }
}
