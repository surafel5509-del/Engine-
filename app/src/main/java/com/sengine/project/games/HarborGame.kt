package com.sengine.project.games

import com.sengine.engine.core.Camera3D
import com.sengine.engine.core.Collider3D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Light
import com.sengine.engine.core.MeshRenderer
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.Water
import com.sengine.project.Project
import com.sengine.project.games.GameKit.body3
import com.sengine.project.games.GameKit.col3
import com.sengine.project.games.GameKit.mesh
import com.sengine.project.games.GameKit.obj3
import com.sengine.project.games.GameKit.off
import com.sengine.project.games.GameKit.particles
import com.sengine.project.games.GameKit.script

/**
 * "Sky Harbor" — complete 3D seaplane delivery sim:
 *  - Fly a floatplane over an archipelago (chase cam), bank with the Racing steering wheel,
 *    climb/dive with Gas/Brake, Nitro for boost.
 *  - Pick up cargo at the harbor, deliver it to the pad whose beacon is lit; rings and fuel buoys
 *    keep you flying; storm cells drain hull integrity.
 *  - Escalating deliveries, day→sunset sky, crash & splash physics, delivery streak bonuses.
 */
internal object HarborGame {
    private const val CUBE = 0; private const val SPHERE = 1; private const val CYL = 3; private const val CONE = 4; private const val CUSTOM = 8

    fun build(p: Project) {
        GameKit.install(p, "Sand", "Grass Tile", "Stone", "Wood Planks", "Wooden Crate", "Metal Plate", "Low-Poly Tree", "Rock", "Checker",
            "Water", "Sky Gradient", "Soft Particle", "Spark", "Explosion", "Hit", "Power Up", "Coin Pickup", "Win Jingle",
            "Game Over", "UI Click", "Jump", "Chiptune Adventure (song)", "Victory Fanfare (song)", "Rotator")
        for (s in scripts) p.writeAsset(s, Games.res("harbor/$s"))
        p.saveScene(menu())
        p.saveScene(isle())
        p.saveControls(com.sengine.engine.controls.ControlLayout.presets.first { it.name == "Racing" }.copy())
        p.startScene = "HarborMenu"
        p.orientation = 0
    }

    private val scripts = listOf("HbMenu.js", "HbPlane.js", "HbPad.js", "HbWorld.js")

    // ------------------------------------------------------------------ helpers
    private fun GameObject.noShadow(): GameObject { get<MeshRenderer>()?.castShadows = false; return this }

    private fun block(s: Scene, name: String, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, color: Long, tex: String = "", tiling: Float = 1f): GameObject =
        obj3(s, name, x, y, z, sx, sy, sz).mesh(CUBE, color, tex, tiling).col3()

    private fun fx(s: Scene, name: String, preset: Int, tweak: ParticleEmitter.() -> Unit = {}): GameObject =
        obj3(s, name, 0f, -60f, 0f).particles { applyPreset(preset); emitting = false; rate = 0f; texture = "SoftDot.png"; tweak() }.off()

    // ------------------------------------------------------------------ menu
    private fun menu(): Scene {
        val s = Scene("HarborMenu")
        val cam = obj3(s, "Main Camera", 0f, 2.2f, 9f)
        cam.add(Camera3D().also { c ->
            c.fov = 55f; c.skyTop = 0xFF8ED3FF.toInt(); c.skyHorizon = 0xFFFFD9A0.toInt(); c.sunDisc = true; c.quality = 2
        })
        cam.add(Light().also { it.kind = 0; it.intensity = 1.1f })
        // showcase plane slowly banking over water
        val sea = obj3(s, "Sea", 0f, -1.4f, 0f, 60f, 1f, 60f).mesh(CUBE, 0xFF1E88C9, "Water.png", 12f)
        sea.add(Water())
        val showcase = obj3(s, "Showcase", 0f, 1.2f, 0f)
        planeParts(s, showcase)
        showcase.script("Rotator.js", "x=0, y=24, z=0")
        GameKit.text(s, "Title", "SKY HARBOR", 0f, -1.4f, 0.9f, 0xFFFFFFFF, anchor = GameKit.TOP)
        GameKit.text(s, "Sub", "seaplane deliveries", 0f, -0.6f, 0.32f, 0xFFE8F4FF, anchor = GameKit.TOP)
        GameKit.button(s, "FlyBtn", "FLY", 0f, 1.2f, 4.6f, 1.05f, 0xFF0E9BD8, "scene:HarborIsle", anchor = GameKit.CENTER, textSize = 0.5f)
        GameKit.button(s, "HowBtn", "How to Play", 0f, 2.3f, 4.6f, 0.85f, 0xFF334155, "show:HelpPanel", anchor = GameKit.CENTER)
        GameKit.text(s, "RecordText", "", 0f, 3.6f, 0.3f, 0xFFE8F4FF, anchor = GameKit.BOTTOM)
        val help = GameKit.panel(s, "HelpPanel", 0f, -0.3f, 9.6f, 6.4f).off()
        GameKit.text(s, "HelpTitle", "HOW TO PLAY", 0f, 2.7f, 0.5f, parent = help)
        GameKit.text(s, "HelpBody", "Steer with the wheel, Gas to throttle up, Brake to dive,\nNitro for a boost. Fly low over the golden harbor pad\nto load cargo, then carry it to the pad with the lit beacon.\nFly through blue rings to refill fuel and earn bonus cash.\nStorm clouds (dark, rumbling) damage your hull.\nLand gently on water to scoop a refill. Don't stall!", 0f, 0.4f, 0.28f, 0xFFE2E8F0, parent = help)
        GameKit.button(s, "HelpOkBtn", "OK", 0f, -2.6f, 3f, 0.85f, 0xFF22C55E, "hide:HelpPanel", parent = help)
        obj(s, "HbMenuMgr").script("HbMenu.js")
        return s
    }

    /** Floatplane built from primitives, parented to [root]. */
    private fun planeParts(s: Scene, root: GameObject) {
        val f = obj3(s, "Fuselage", 0f, 0f, 0f, 0.5f, 0.5f, 2.2f, root).mesh(CYL, 0xFFE4572E).noShadow()
        f.rotation = 90f
        obj3(s, "Nose", 0f, 0f, -1.25f, 0.42f, 0.42f, 0.4f, root).mesh(CONE, 0xFFC74424).noShadow().also { it.rotation = -90f }
        obj3(s, "Cockpit", 0f, 0.3f, -0.5f, 0.34f, 0.3f, 0.7f, root).mesh(SPHERE, 0xFF9FD8F0).noShadow()
        val wing = obj3(s, "Wing", 0f, 0.22f, -0.2f, 4.4f, 0.08f, 0.8f, root).mesh(CUBE, 0xFFF5EFE0).noShadow()
        wing.script("Rotator.js", "x=0, y=0, z=0")
        obj3(s, "WingStrutL", -0.7f, -0.05f, -0.2f, 0.05f, 0.5f, 0.06f, root).mesh(CUBE, 0xFF8A8378).noShadow()
        obj3(s, "WingStrutR", 0.7f, -0.05f, -0.2f, 0.05f, 0.5f, 0.06f, root).mesh(CUBE, 0xFF8A8378).noShadow()
        obj3(s, "Tail", 0f, 0.45f, 1.1f, 0.06f, 0.7f, 0.6f, root).mesh(CUBE, 0xFFF5EFE0).noShadow()
        obj3(s, "Stab", 0f, 0.12f, 1.15f, 1.6f, 0.06f, 0.45f, root).mesh(CUBE, 0xFFE4572E).noShadow()
        obj3(s, "FloatL", -0.55f, -0.45f, 0.1f, 0.16f, 0.22f, 1.7f, root).mesh(CUBE, 0xFFB0A99A).noShadow()
        obj3(s, "FloatR", 0.55f, -0.45f, 0.1f, 0.16f, 0.22f, 1.7f, root).mesh(CUBE, 0xFFB0A99A).noShadow()
        obj3(s, "Prop", 0f, 0f, -1.5f, 1.6f, 0.12f, 0.04f, root).mesh(CUBE, 0xFF2B2B2B).noShadow().script("Rotator.js", "x=0, y=0, z=0")
    }

    // ------------------------------------------------------------------ island
    private fun isle(): Scene {
        val s = Scene("HarborIsle")
        val cam = obj3(s, "Main Camera", 0f, 6f, 14f)
        cam.add(Camera3D().also { c ->
            c.follow = "Plane"; c.offsetY = 3.4f; c.offsetZ = 9f; c.fov = 62f; c.near = 0.1f; c.far = 420f
            c.skyTop = 0xFF3E8ED8.toInt(); c.skyHorizon = 0xFFFFE3B0.toInt(); c.sunDisc = true; c.quality = 2; c.shadowDistance = 70f
        })
        cam.add(Light().also { it.kind = 0; it.intensity = 1.15f })

        // ocean
        val sea = obj3(s, "Sea", 0f, 0f, 0f, 300f, 1f, 300f).mesh(CUBE, 0xFF1E88C9, "Water.png", 40f)
        sea.add(Water())

        // islands (sand cones + grass tops + palms + rocks)
        island(s, "IsleA", 0f, -26f, 16f, 14f)
        island(s, "IsleB", 34f, 8f, 11f, 10f)
        island(s, "IsleC", -38f, 14f, 13f, 12f)
        island(s, "IsleD", -6f, 52f, 9f, 8f)
        island(s, "IsleE", 22f, -60f, 12f, 11f)
        island(s, "IsleF", -48f, -44f, 10f, 9f)

        // harbor home pad on IsleA + 5 delivery pads around the map
        pad(s, "HarborPad", -18f, -26f, 6f, true)
        pad(s, "Pad1", 30f, 8f, 6f, false)
        pad(s, "Pad2", -34f, 12f, 6f, false)
        pad(s, "Pad3", -4f, 49f, 5f, false)
        pad(s, "Pad4", 20f, -56f, 6f, false)
        pad(s, "Pad5", -45f, -40f, 5f, false)

        // boost rings between the islands
        ring(s, 0f, 5f, -4f)
        ring(s, -10f, 7f, 8f)
        ring(s, 16f, 6f, 20f)
        ring(s, -20f, 8f, -14f)
        ring(s, 30f, 7f, -18f)
        ring(s, 8f, 9f, 38f)

        // floating fuel buoys
        buoy(s, -16f, -22f)
        buoy(s, 24f, -8f)
        buoy(s, -8f, 30f)
        buoy(s, 40f, -40f)

        // storm cells (hostile weather)
        storm(s, 14f, 24f, 18f)
        storm(s, -26f, 20f, -30f)
        storm(s, 4f, 22f, 62f)

        // crash splash + pickup sparkle FX
        fx(s, "Splash", ParticleEmitter.PRESETS.indexOf("Splash"), { spread = 70f; speed = 6f; lifetime = 0.7f; gravity = -14f; startColor = 0xFFBFE9FF.toInt(); endColor = 0x00BFE9FF })
        fx(s, "Sparkle", ParticleEmitter.PRESETS.indexOf("Fire"), { spread = 360f; speed = 3f; lifetime = 0.5f; gravity = 2f; startColor = 0xFFFFF59D.toInt(); endColor = 0x00FFF59D })

        // the seaplane
        val plane = obj3(s, "Plane", 0f, 3f, -14f)
        plane.tag = "Player"
        plane.add(Collider3D().also { it.sizeX = 1.2f; it.sizeY = 0.8f; it.sizeZ = 2.2f })
        plane.add(Rigidbody3D().also { it.bodyType = 2; it.gravityScale = 0f; it.drag = 0f }) // kinematic; script flies it
        planeParts(s, plane)
        plane.script("HbPlane.js", "thrust=22, turn=70, pitchRate=55, maxSpeed=30")

        GameKit.text(s, "CargoText", "Cargo: none", -3.4f, -0.4f, 0.36f, 0xFFFFFFFF, anchor = GameKit.TL)
        GameKit.text(s, "JobText", "Deliveries 0  •  Cash 0", 0f, -0.4f, 0.36f, 0xFFFFD166, anchor = GameKit.TOP)
        GameKit.bar(s, "FuelBar", -3.2f, 0.15f, 2.4f, 0.22f, 0xFF57D16A, anchor = GameKit.BL, value = 1f)
        GameKit.text(s, "FuelText", "FUEL", -4.2f, 0.15f, 0.24f, 0xFFB9C4D8, anchor = GameKit.BL)
        GameKit.bar(s, "HullBar", 0.8f, 0.15f, 2.4f, 0.22f, 0xFF4C8DFF, anchor = GameKit.BL, value = 1f)
        GameKit.text(s, "HullText", "HULL", -0.1f, 0.15f, 0.24f, 0xFFB9C4D8, anchor = GameKit.BL)
        GameKit.radar(s, "Radar", 0f, 0f, 2.1f, "Pad,HarborPad,Storm", 120f, anchor = GameKit.TR, dot = 0xFFFFD166)
        GameKit.pauseMenu(s, "HarborMenu", 0xFF0E9BD8)
        val over = GameKit.panel(s, "OverPanel", 0f, 0f, 7.8f, 5.6f).off()
        GameKit.text(s, "OverTitle", "DOWN!", 0f, 1.8f, 0.7f, 0xFFFF5C6C, parent = over)
        GameKit.text(s, "OverStats", "", 0f, 0.7f, 0.34f, 0xFFE2E8F0, parent = over)
        GameKit.button(s, "AgainBtn", "Fly Again", 0f, -0.6f, 4.4f, 0.95f, 0xFF0E9BD8, "resume;reload", over)
        GameKit.button(s, "OverMenuBtn", "Menu", 0f, -1.7f, 4.4f, 0.9f, 0xFF3A4566, "resume;scene:HarborMenu", over)
        obj(s, "HbWorld").script("HbWorld.js")
        return s
    }

    private fun island(s: Scene, name: String, x: Float, z: Float, r: Float, h: Float) {
        val base = obj3(s, name, x, -h / 2 + 1.5f, z, r, h, r).mesh(CONE, 0xFFE7C27D, "Sand.png", 6f).col3()
        base.add(Rigidbody3D().also { it.bodyType = 0 })
        val top = obj3(s, name + "Top", x, 1.2f, z, r * 0.5f, 0.7f, r * 0.5f).mesh(CYL, 0xFF57A05B, "Grass.png", 5f)
        for (i in 0 until 3) {
            val a = i * 2.1f + x
            obj3(s, "Palm", x + kotlin.math.cos(a) * r * 0.3f, 2.4f, z + kotlin.math.sin(a) * r * 0.3f, 1.4f, 1.4f, 1.4f)
                .mesh(CUSTOM, 0xFF2E7D32, model = "Tree.obj")
        }
        obj3(s, "Rock", x - r * 0.35f, 1.8f, z + r * 0.3f, 1.2f, 1.1f, 1.2f).mesh(CUSTOM, 0xFF8D8D8D, model = "Rock.obj")
    }

    private fun pad(s: Scene, name: String, x: Float, z: Float, size: Float, home: Boolean, y: Float = 2.2f) {
        val deck = block(s, name, x, y, z, size, 0.5f, size, if (home) 0xFFFFC94D else 0xFF37474F, "Wood.png", 2f)
        deck.get<Collider3D>()!!.isTrigger = true
        deck.tag = "Pad"
        deck.script("HbPad.js", if (home) "home=true" else "")
        val post = obj3(s, name + "Post", x, y + 2f, z, 0.12f, 3f, 0.12f).mesh(CYL, 0xFF8D6E63)
        val beacon = obj3(s, name + "Beacon", x, y + 3.6f, z, 0.5f, 0.5f, 0.5f).mesh(SPHERE, 0xFF57D16A)
        beacon.script("Rotator.js", "x=40, y=90, z=0")
    }

    private fun ring(s: Scene, x: Float, y: Float, z: Float) {
        val r = obj3(s, "Ring", x, y, z, 2.6f, 2.6f, 2.6f).mesh(5, 0xFF4FC3F7).col3(true, true)
        r.tag = "Ring"
        r.script("HbPad.js", "ring=true")
    }

    private fun buoy(s: Scene, x: Float, z: Float) {
        val b = block(s, "Buoy", x, 0.4f, z, 0.7f, 1f, 0.7f, 0xFFFF7043, "Metal.png")
        b.get<Collider3D>()!!.isTrigger = true
        b.tag = "Buoy"
        b.script("HbPad.js", "buoy=true")
    }

    private fun storm(s: Scene, x: Float, y: Float, z: Float) {
        val cloud = obj3(s, "Storm", x, y, z, 9f, 3.5f, 9f).mesh(SPHERE, 0xFF37474F).noShadow()
        cloud.tag = "Storm"
        cloud.script("HbPad.js", "storm=true")
    }
}
