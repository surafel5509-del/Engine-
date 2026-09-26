package com.sengine.project.games

import com.sengine.engine.core.Camera3D
import com.sengine.engine.core.Collider3D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Light
import com.sengine.engine.core.MeshRenderer
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.UIPanel
import com.sengine.engine.core.Water
import com.sengine.project.Project
import com.sengine.project.games.GameKit.body3
import com.sengine.project.games.GameKit.col3
import com.sengine.project.games.GameKit.mesh
import com.sengine.project.games.GameKit.off
import com.sengine.project.games.GameKit.particles
import com.sengine.project.games.GameKit.script

/**
 * "Strike Force" — complete 3D first-person shooter: a walled desert compound (day) and a night raid,
 * patrolling soldiers with line-of-sight AI, 3 weapons (rifle / shotgun / pistol) with ADS, recoil and
 * headshots, frag grenades, explosive barrels with fire, regenerating health, objectives + extraction.
 */
internal object FpsGame {
    private val scripts = listOf("SFPlayer.js", "SFEnemy.js", "SFBarrel.js", "SFGrenade.js", "SFPickup.js", "SFGame.js", "SFMenu.js")

    private const val CUBE = 0; private const val SPHERE = 1; private const val CYL = 3; private const val CUSTOM = 8

    fun build(p: Project) {
        GameKit.install(p, "Character (walk/idle) (editable)", "Low-poly Car (editable)", "Tree (editable)", "Desert Sand", "Concrete", "Brick Wall",
            "Wooden Crate", "Asphalt", "Marble", "Sand", "Wood Planks", "Metal Plate", "Camo", "Ammo Box", "Soft Particle", "Rusty Metal",
            "Pistol Shot", "Shotgun Blast", "Reload", "Empty Click", "Explosion", "Hit", "Footstep", "Power Up", "Nitro Whoosh",
            "Win Jingle", "Game Over", "UI Click (default)", "Action Battle (song)", "Menu Theme (song)")
        for (s in scripts) p.writeAsset(s, Games.res("fps/$s"))
        p.saveScene(menu())
        p.saveScene(mission(false))
        p.saveScene(mission(true))
        p.startScene = "Menu"
        p.orientation = 0
    }

    // ------------------------------------------------------------------ helpers
    private fun GameObject.noShadow(): GameObject { get<MeshRenderer>()?.castShadows = false; return this }
    private fun GameObject.unlit(): GameObject { get<MeshRenderer>()?.unlit = true; get<MeshRenderer>()?.castShadows = false; return this }

    private fun solid(s: Scene, name: String, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, color: Long, tex: String = "", tiling: Float = 1f): GameObject =
        GameKit.obj3(s, name, x, y, z, sx, sy, sz).mesh(CUBE, color, tex, tiling).col3()

    private fun fx(s: Scene, name: String, preset: String, tweak: ParticleEmitter.() -> Unit = {}): GameObject =
        GameKit.obj3(s, name, 0f, -50f, 0f).particles { applyPreset(ParticleEmitter.PRESETS.indexOf(preset)); emitting = false; rate = 0f; texture = "SoftDot.png"; tweak() }.off()

    private fun camera(s: Scene, night: Boolean): GameObject =
        GameKit.obj3(s, "Main Camera", 0f, 1.7f, 34f).also {
            it.add(Camera3D().also { c ->
                c.follow = ""; c.fov = 72f; c.near = 0.05f; c.far = 260f; c.quality = 2; c.shadowDistance = 45f
                c.skyTop = (if (night) 0xFF03060F else 0xFF4F86C6).toInt(); c.skyHorizon = (if (night) 0xFF17213A else 0xFFE9D2A8).toInt(); c.sunDisc = !night
            })
        }

    /** Viewmodel weapons parented to the camera (the player script positions / swaps them). */
    private fun viewmodels(s: Scene, cam: GameObject) {
        fun part(parent: GameObject, name: String, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, color: Long, kind: Int = CUBE) =
            GameKit.obj3(s, name, x, y, z, sx, sy, sz, parent).mesh(kind, color).noShadow()
        fun flash(parent: GameObject, z: Float) =
            GameKit.obj3(s, "Flash", 0f, 0.02f, z, 0.16f, 0.16f, 0.16f, parent).mesh(SPHERE, 0xFFFFE082).unlit().also { it.get<MeshRenderer>()!!.enabled = false }

        val rifle = GameKit.obj3(s, "Rifle", 0.26f, -0.24f, -0.55f, parent = cam)
        part(rifle, "Receiver", 0f, 0f, 0f, 0.07f, 0.1f, 0.42f, 0xFF2B2B2B)
        part(rifle, "Barrel", 0f, 0.015f, -0.33f, 0.028f, 0.028f, 0.3f, 0xFF151515)
        part(rifle, "Handguard", 0f, 0.0f, -0.18f, 0.075f, 0.08f, 0.18f, 0xFF4A4A3A)
        part(rifle, "Mag", 0f, -0.1f, -0.04f, 0.045f, 0.14f, 0.07f, 0xFF1E1E1E)
        part(rifle, "Stock", 0f, -0.02f, 0.25f, 0.06f, 0.09f, 0.16f, 0xFF3A3A30)
        part(rifle, "Sight", 0f, 0.075f, -0.02f, 0.035f, 0.04f, 0.1f, 0xFF101010)
        flash(rifle, -0.52f)

        val shotgun = GameKit.obj3(s, "Shotgun", 0.26f, -0.24f, -0.55f, parent = cam)
        part(shotgun, "Receiver", 0f, 0f, 0f, 0.08f, 0.1f, 0.34f, 0xFF262626)
        part(shotgun, "Barrel", 0f, 0.025f, -0.34f, 0.04f, 0.04f, 0.4f, 0xFF121212)
        part(shotgun, "Pump", 0f, -0.035f, -0.26f, 0.06f, 0.05f, 0.16f, 0xFF6D4C2F)
        part(shotgun, "Stock", 0f, -0.03f, 0.24f, 0.07f, 0.1f, 0.2f, 0xFF6D4C2F)
        flash(shotgun, -0.58f)

        val pistol = GameKit.obj3(s, "Pistol", 0.22f, -0.22f, -0.45f, parent = cam)
        part(pistol, "Slide", 0f, 0.02f, -0.04f, 0.05f, 0.06f, 0.22f, 0xFF3A3A3A)
        part(pistol, "Grip", 0f, -0.07f, 0.04f, 0.045f, 0.13f, 0.06f, 0xFF1C1C1C)
        flash(pistol, -0.2f)
    }

    private fun soldier(s: Scene, x: Float, z: Float, y: Float = 1f, params: String = "", tint: Long = 0xFFB8A57E) {
        val e = GameKit.obj3(s, "Hostile", x, y, z).script("SFEnemy.js", params)
        e.tag = "Enemy"
        e.add(Collider3D().also { it.sizeX = 0.7f; it.sizeY = 1.9f; it.sizeZ = 0.7f })
        e.add(Rigidbody3D().also { it.friction = 0f; it.drag = 0f })
        GameKit.obj3(s, "Body", 0f, -0.97f, 0f, 1f, 1f, 1f, e).mesh(CUSTOM, tint, model = "Character.smodel").also { it.get<MeshRenderer>()!!.animation = "Idle" }
        GameKit.obj3(s, "Helmet", 0f, 0.98f, 0f, 0.62f, 0.2f, 0.62f, e).mesh(CUBE, 0xFF5B5A3A)
        GameKit.obj3(s, "Vest", 0f, 0.2f, 0f, 0.76f, 0.6f, 0.46f, e).mesh(CUBE, 0xFF6B6745)
        GameKit.obj3(s, "Gun", 0.28f, 0.15f, 0.4f, 0.08f, 0.1f, 0.6f, e).mesh(CUBE, 0xFF1E1E1E)
        GameKit.obj3(s, "MuzzleFlash", 0.28f, 0.17f, 0.78f, 0.2f, 0.2f, 0.2f, e).mesh(SPHERE, 0xFFFFD54F).unlit().also { it.get<MeshRenderer>()!!.enabled = false }
    }

    private fun barrel(s: Scene, x: Float, z: Float) {
        val b = GameKit.obj3(s, "Barrel", x, 0.6f, z, 0.8f, 1.2f, 0.8f).mesh(CYL, 0xFFC62828, "Rust.png").col3().script("SFBarrel.js")
        b.tag = "Barrel"
        GameKit.obj3(s, "Band", 0f, 0.3f, 0f, 1.04f, 0.08f, 1.04f, b).mesh(CYL, 0xFFFFD54F).noShadow()
    }

    // ------------------------------------------------------------------ scenes
    private fun menu(): Scene {
        val s = Scene("Menu")
        s.ambient = 0xFF5A5448.toInt()
        s.fog = true; s.fogStart = 20f; s.fogEnd = 70f; s.fogColor = 0xFFE3C9A0.toInt()
        camera(s, false).also { it.y = 3.2f; it.z = 9f; it.rotX = -12f }
        GameKit.obj3(s, "Sun", 0f, 20f, 0f).also { it.rotX = -40f; it.rotY = 140f }.add(Light().also { it.kind = 0; it.intensity = 1.15f; it.color = 0xFFFFE6C0.toInt() })
        GameKit.obj3(s, "MenuManager", 0f, 0f, 0f).script("SFMenu.js")
        GameKit.obj3(s, "Ground", 0f, -0.5f, 0f, 60f, 1f, 60f).mesh(CUBE, 0xFFE0C08A, "DesertSand.png", 10f)
        for ((i, c) in listOf(Triple(-2f, 0f, 20f), Triple(1.6f, -1.2f, -30f), Triple(2.5f, 1.8f, 75f)).withIndex()) {
            val sol = GameKit.obj3(s, "Squad$i", c.first, 0f, c.second).also { it.rotY = c.third }
            GameKit.obj3(s, "Body", 0f, 0f, 0f, 1f, 1f, 1f, sol).mesh(CUSTOM, 0xFF8FA07A, model = "Character.smodel").also { it.get<MeshRenderer>()!!.animation = "Idle" }
            GameKit.obj3(s, "Helmet", 0f, 1.95f, 0f, 0.62f, 0.2f, 0.62f, sol).mesh(CUBE, 0xFF4B5A34)
        }
        for ((i, c) in listOf(-4f to -2f, -4.8f to -0.8f, 4f to -3f, -4.4f to -1.4f).withIndex())
            GameKit.obj3(s, "Crate$i", c.first, if (i == 3) 1.7f else 0.55f, c.second, 1.1f, 1.1f, 1.1f).mesh(CUBE, 0xFFFFFFFF, "Crate.png").also { it.rotY = i * 17f }
        GameKit.obj3(s, "Wreck", 5f, 0.1f, -4f, 1.3f, 1.3f, 1.3f).mesh(CUSTOM, 0xFF3A3A3A, model = "Car.smodel").also { it.rotY = 30f }
        GameKit.obj3(s, "WreckFire", 5f, 0.8f, -4f).particles { applyPreset(ParticleEmitter.PRESETS.indexOf("Fire")); texture = "SoftDot.png"; startSize = 1.1f }
        GameKit.obj3(s, "WreckSmoke", 5f, 1.8f, -4f).particles { applyPreset(ParticleEmitter.PRESETS.indexOf("Smoke")); texture = "SoftDot.png"; startSize = 0.8f; endSize = 3f }

        GameKit.text(s, "Title", "STRIKE FORCE", 4.2f, -1.2f, 1.1f, 0xFFFFFFFF, anchor = GameKit.TL, align = 0)
            .also { it.x = 0.8f }
        GameKit.text(s, "Subtitle", "Modern tactical combat", 0.85f, -2.1f, 0.34f, 0xFFD7CCB8, anchor = GameKit.TL, align = 0)
        val main = GameKit.panel(s, "MainPanel", 3.9f, 0f, 6.8f, 5.6f, 0xD0101010, anchor = GameKit.LEFT, border = 0x55FFFFFF)
        GameKit.button(s, "Mission1Btn", "OPERATION DUST", 0f, 1.95f, 6f, 0.9f, 0xFFF5F5F5, "scene:Mission", main, textSize = 0.34f)
            .get<com.sengine.engine.core.UIButton>()!!.textColor = 0xFF111111.toInt()
        GameKit.button(s, "Mission2Btn", "NIGHT RAID", 0f, 0.9f, 6f, 0.9f, 0xFF2E2E2E, "scene:NightRaid", main, textSize = 0.34f)
        GameKit.button(s, "SettingsBtn", "Settings", 0f, -0.2f, 6f, 0.8f, 0xFF2A2A2A, "show:SettingsPanel;hide:MainPanel", main)
        GameKit.button(s, "HelpBtn", "Field manual", 0f, -1.15f, 6f, 0.8f, 0xFF2A2A2A, "show:HelpPanel;hide:MainPanel", main)
        GameKit.button(s, "QuitBtn", "Quit", 0f, -2.1f, 6f, 0.8f, 0xFF1A1A1A, "quit", main)

        val set = GameKit.panel(s, "SettingsPanel", 3.9f, 0f, 6.8f, 5.4f, 0xE0101010, anchor = GameKit.LEFT, border = 0x55FFFFFF).off()
        GameKit.text(s, "SetTitle", "SETTINGS", 0f, 2.1f, 0.55f, parent = set)
        GameKit.button(s, "MusicBtn", "Music: ON", 0f, 1.1f, 5.8f, 0.8f, 0xFF2A2A2A, "call:toggleMusic", set)
        GameKit.button(s, "DiffBtn", "Difficulty: Regular", 0f, 0.15f, 5.8f, 0.8f, 0xFF2A2A2A, "call:cycleDifficulty", set)
        GameKit.button(s, "SensBtn", "Look sensitivity: 45", 0f, -0.8f, 5.8f, 0.8f, 0xFF2A2A2A, "call:cycleSens", set)
        GameKit.button(s, "SetBackBtn", "Back", 0f, -1.95f, 3f, 0.8f, 0xFFF5F5F5, "hide:SettingsPanel;show:MainPanel", set)
            .get<com.sengine.engine.core.UIButton>()!!.textColor = 0xFF111111.toInt()

        val help = GameKit.panel(s, "HelpPanel", 0f, 0f, 10f, 6.2f, 0xE8101010, border = 0x55FFFFFF).off()
        GameKit.text(s, "HelpTitle", "FIELD MANUAL", 0f, 2.5f, 0.55f, parent = help)
        GameKit.text(s, "HelpBody", "Left stick: move.  Drag the right side of the screen: look.\nFIRE shoots, AIM zooms down the sights (more accurate).\nRELOAD, SWITCH weapon (M4 / SPAS-12 / M1911), GRENADE.\nHeadshots do double damage. Red barrels explode!\nHealth regenerates when you stay out of fire.\nClear all hostiles, then reach the green extraction flare.", 0f, 0.2f, 0.29f, 0xFFE0E0E0, help)
        GameKit.button(s, "HelpBackBtn", "Understood", 0f, -2.5f, 3.4f, 0.8f, 0xFFF5F5F5, "hide:HelpPanel;show:MainPanel", help)
            .get<com.sengine.engine.core.UIButton>()!!.textColor = 0xFF111111.toInt()
        return s
    }

    private fun mission(night: Boolean): Scene {
        val s = Scene(if (night) "NightRaid" else "Mission")
        s.ambient = (if (night) 0xFF1C2233 else 0xFF5C574C).toInt()
        s.fog = true
        if (night) { s.fogStart = 12f; s.fogEnd = 55f; s.fogColor = 0xFF0B1020.toInt() } else { s.fogStart = 45f; s.fogEnd = 140f; s.fogColor = 0xFFE6CFA4.toInt() }
        val cam = camera(s, night)
        viewmodels(s, cam)
        GameKit.obj3(s, "Sun", 0f, 30f, 0f).also { it.rotX = if (night) -65f else -48f; it.rotY = 130f }
            .add(Light().also { it.kind = 0; it.intensity = if (night) 0.35f else 1.15f; it.color = (if (night) 0xFF8FA8FF else 0xFFFFE8C8).toInt() })
        GameKit.obj3(s, "Game", 0f, 0f, 0f).script("SFGame.js")

        // ground, roads, perimeter
        GameKit.obj3(s, "Ground", 0f, -0.5f, 0f, 84f, 1f, 84f).mesh(CUBE, if (night) 0xFF8A7E68 else 0xFFE3C595, "DesertSand.png", 14f).col3()
        GameKit.obj3(s, "Road", 0f, 0.01f, 0f, 6f, 0.02f, 80f).mesh(CUBE, 0xFF9A9A9A, "Asphalt.png", 8f).noShadow()
        GameKit.obj3(s, "CrossRoad", 0f, 0.012f, 12f, 80f, 0.02f, 5f).mesh(CUBE, 0xFF9A9A9A, "Asphalt.png", 8f).noShadow()
        val wallC = 0xFFB9B2A5
        solid(s, "WallN", 0f, 1.75f, -41f, 84f, 3.5f, 1f, wallC, "Concrete.png", 12f)
        solid(s, "WallS", 0f, 1.75f, 41f, 84f, 3.5f, 1f, wallC, "Concrete.png", 12f)
        solid(s, "WallW", -41f, 1.75f, 0f, 1f, 3.5f, 84f, wallC, "Concrete.png", 12f)
        solid(s, "WallE", 41f, 1.75f, 0f, 1f, 3.5f, 84f, wallC, "Concrete.png", 12f)

        // buildings (solid blocks with roofs)
        fun building(name: String, x: Float, z: Float, w: Float, h: Float, d: Float, color: Long = 0xFFD9C3A0) {
            solid(s, name, x, h / 2, z, w, h, d, color, "Brick.png", 3f)
            GameKit.obj3(s, "${name}Roof", x, h + 0.15f, z, w + 0.6f, 0.3f, d + 0.6f).mesh(CUBE, 0xFF6D5C4A).noShadow()
            // windows
            for (k in 0 until (w / 3).toInt().coerceAtLeast(1)) {
                val wx = x - w / 2 + 1.5f + k * 3f
                GameKit.obj3(s, "${name}Win$k", wx, h * 0.6f, z + d / 2 + 0.02f, 1f, 0.8f, 0.04f)
                    .mesh(CUBE, if (night) 0xFFFFC266 else 0xFF30363D).also { if (night) it.unlit() else it.noShadow() }
            }
        }
        building("Barracks", -14f, 10f, 8f, 5f, 8f)
        building("Depot", 15f, 5f, 10f, 4f, 6f, 0xFFCDB894)
        building("Tower Block", -19f, -14f, 6f, 7f, 10f)
        building("Workshop", 17f, -18f, 8f, 5f, 8f, 0xFFC9AE8A)
        building("Command", 0f, -27f, 12f, 4.5f, 5f, 0xFFBFA684)

        // watch tower with a sniper
        for ((i, c) in listOf(-1.3f to -1.3f, 1.3f to -1.3f, -1.3f to 1.3f, 1.3f to 1.3f).withIndex())
            GameKit.obj3(s, "TowerLeg$i", 24f + c.first, 2f, 22f + c.second, 0.3f, 4f, 0.3f).mesh(CYL, 0xFF6D4C2F)
        solid(s, "TowerDeck", 24f, 4.1f, 22f, 3.2f, 0.3f, 3.2f, 0xFF7B5A3A, "Wood.png")
        GameKit.obj3(s, "TowerRoof", 24f, 6.6f, 22f, 3.6f, 0.2f, 3.6f).mesh(CUBE, 0xFF5D4037).noShadow()

        // cover: sandbags, crates, jersey barriers
        for ((i, c) in listOf(Triple(-4f, 22f, 0f), Triple(5f, 26f, 0f), Triple(-8f, -6f, 90f), Triple(9f, -9f, 0f), Triple(-3f, -18f, 0f), Triple(26f, 4f, 90f), Triple(-26f, 2f, 90f)).withIndex()) {
            val horiz = c.third == 0f
            solid(s, "Sandbags$i", c.first, 0.45f, c.second, if (horiz) 3.2f else 0.9f, 0.9f, if (horiz) 0.9f else 3.2f, 0xFFB59F76, "Sand.png", 2f)
        }
        val crates = listOf(-10f to 20f, -9f to 21.3f, 11f to 14f, 12.2f to 14f, 11.6f to 14f, -24f to -24f, 22f to -8f, 6f to -20f, -6f to 2f, 30f to 30f, -30f to 28f, 31f to -30f)
        for ((i, c) in crates.withIndex()) solid(s, "Crate$i", c.first, 0.6f, c.second, 1.2f, 1.2f, 1.2f, 0xFFFFFFFF, "Crate.png")
        solid(s, "CrateTop", 11.6f, 1.8f, 14f, 1.2f, 1.2f, 1.2f, 0xFFFFFFFF, "Crate.png")
        for ((i, c) in listOf(-9f to 24f, 13f to 12f, 3f to -8f, -20f to -6f, 20f to -12f, 1f to -22f, 27f to 20f, -6f to 14f).withIndex()) barrel(s, c.first, c.second)

        // plaza fountain with real water (buoyant, animated)
        solid(s, "FountainN", 0f, 0.35f, -4f, 8.4f, 0.7f, 0.4f, 0xFFE0D6C8, "Marble.png")
        solid(s, "FountainS", 0f, 0.35f, 4f, 8.4f, 0.7f, 0.4f, 0xFFE0D6C8, "Marble.png")
        solid(s, "FountainW", -4f, 0.35f, 0f, 0.4f, 0.7f, 8f, 0xFFE0D6C8, "Marble.png")
        solid(s, "FountainE", 4f, 0.35f, 0f, 0.4f, 0.7f, 8f, 0xFFE0D6C8, "Marble.png")
        GameKit.obj3(s, "FountainWater", 0f, 0.5f, 0f).add(Water().also {
            it.mode = 1; it.width = 7.6f; it.depth = 7.6f; it.waveHeight = 0.05f; it.waveLength = 2f; it.detail = 36
            it.color = if (night) 0xCC1B3A5C.toInt() else 0xCC2E86C1.toInt(); it.deepColor = 0xE0103050.toInt()
        })
        GameKit.obj3(s, "FountainSpray", 0f, 0.8f, 0f).particles { applyPreset(ParticleEmitter.PRESETS.indexOf("Splash")); rate = 40f; speed = 4f; spread = 25f; texture = "SoftDot.png" }

        // burning wreck
        GameKit.obj3(s, "Wreck", 7f, 0.1f, 19f, 1.3f, 1.3f, 1.3f).mesh(CUSTOM, 0xFF2E2E2E, model = "Car.smodel").also { it.rotY = 35f }
        solid(s, "WreckHull", 7f, 0.7f, 19f, 2.2f, 1.4f, 2.6f, 0x00000000).also { it.get<MeshRenderer>()!!.enabled = false }
        GameKit.obj3(s, "WreckFire", 7f, 1f, 19f).particles { applyPreset(ParticleEmitter.PRESETS.indexOf("Fire")); texture = "SoftDot.png"; startSize = 1.2f }
        GameKit.obj3(s, "WreckSmoke", 7f, 2.2f, 19f).particles { applyPreset(ParticleEmitter.PRESETS.indexOf("Smoke")); texture = "SoftDot.png"; startSize = 1f; endSize = 3.5f }
        if (night) GameKit.obj3(s, "WreckGlow", 7f, 1.5f, 19f).add(Light().also { it.kind = 1; it.color = 0xFFFF8A3D.toInt(); it.intensity = 2.5f; it.range = 10f })

        // trees and lamps
        for (i in 0 until 14) {
            val a = i / 14f * 6.283f
            val x = kotlin.math.cos(a) * 36f; val z = kotlin.math.sin(a) * 36f
            GameKit.obj3(s, "Palm$i", x, 0f, z, 1.4f, 1.6f, 1.4f).mesh(CUSTOM, 0xFFFFFFFF, model = "TreeModel.smodel").noShadow()
        }
        for ((i, c) in listOf(-4f to 30f, 4f to 16f, -4f to -12f, 4f to -30f).withIndex()) {
            GameKit.obj3(s, "LampPost$i", c.first, 2f, c.second, 0.15f, 4f, 0.15f).mesh(CYL, 0xFF37474F)
            GameKit.obj3(s, "LampHead$i", c.first, 4.05f, c.second, 0.5f, 0.2f, 0.5f).mesh(CUBE, 0xFFFFF3C4).unlit()
            if (night) GameKit.obj3(s, "LampLight$i", c.first, 3.6f, c.second).add(Light().also { it.kind = 1; it.color = 0xFFFFD08A.toInt(); it.intensity = 2f; it.range = 11f })
        }

        // extraction zone
        GameKit.obj3(s, "Extraction", 0f, 0f, -35f)
        val beacon = GameKit.obj3(s, "ExtractBeacon", 0f, 0f, -35f)
        GameKit.obj3(s, "Ring", 0f, 0.05f, 0f, 6f, 0.05f, 6f, beacon).mesh(CYL, 0x6644FF66).unlit()
        GameKit.obj3(s, "Pillar", 0f, 6f, 0f, 0.6f, 12f, 0.6f, beacon).mesh(CYL, 0x5544FF66).unlit()
        GameKit.obj3(s, "Flare", 0f, 0.3f, 0f, parent = beacon).particles { applyPreset(ParticleEmitter.PRESETS.indexOf("Smoke")); startColor = 0xAA66FF66.toInt(); endColor = 0x0066FF66; texture = "SoftDot.png"; rate = 25f }
        GameKit.obj3(s, "FlareLight", 0f, 2f, 0f, parent = beacon).add(Light().also { it.kind = 1; it.color = 0xFF66FF66.toInt(); it.intensity = 3f; it.range = 12f })

        // player
        val player = GameKit.obj3(s, "Player", 0f, 1f, 34f).script("SFPlayer.js")
        player.tag = "Player"
        player.add(Collider3D().also { it.sizeX = 0.6f; it.sizeY = 1.8f; it.sizeZ = 0.6f })
        player.add(Rigidbody3D().also { it.friction = 0f; it.drag = 0f })

        // hostiles
        val tint = if (night) 0xFF7C8A9A else 0xFFB8A57E
        val squad = mutableListOf(
            Triple(-10f, 16f, "px=0,pz=-8"), Triple(12f, 20f, "px=-8,pz=0"), Triple(-2f, 8f, "px=8,pz=0"),
            Triple(18f, 0f, "guard=true"), Triple(-14f, 3f, "px=0,pz=-10"), Triple(8f, -12f, "px=-10,pz=0"),
            Triple(-20f, -22f, "px=10,pz=0"), Triple(20f, -24f, "guard=true"), Triple(0f, -20f, "px=6,pz=0"), Triple(-6f, -32f, "px=12,pz=0"),
        )
        if (night) squad += listOf(Triple(-28f, 10f, "px=0,pz=12"), Triple(28f, -2f, "px=0,pz=-12"), Triple(5f, 2f, "guard=true"))
        for ((x, z, prm) in squad) soldier(s, x, z, params = prm, tint = tint)
        soldier(s, 24f, 22f, y = 5.3f, params = "guard=true", tint = tint)   // sniper on the tower

        // templates
        val grenade = GameKit.obj3(s, "Grenade", 0f, -50f, 0f, 0.22f, 0.26f, 0.22f).mesh(SPHERE, 0xFF3E4A2E).col3(sphere = true).body3(bounce = 0.35f, friction = 0.8f).script("SFGrenade.js").off()
        grenade.get<Collider3D>()!!.radius = 0.6f
        GameKit.obj3(s, "AmmoCrate", 0f, -50f, 0f, 0.6f, 0.45f, 0.6f).mesh(CUBE, 0xFFFFFFFF, "Ammo.png").script("SFPickup.js").off()
        fx(s, "Impact", "Sparks") { lifetime = 0.3f; speed = 2.5f; startSize = 0.08f }
        fx(s, "BloodHit", "Blood") { startSize = 0.12f; speed = 2f }
        fx(s, "Explosion", "Explosion") { startSize = 1.6f; speed = 6f; lifetime = 0.9f }
        GameKit.obj3(s, "Fire", 0f, -50f, 0f).particles { applyPreset(ParticleEmitter.PRESETS.indexOf("Fire")); texture = "SoftDot.png"; startSize = 1f }.off()

        // HUD
        GameKit.text(s, "Crosshair", "+", 0f, 0f, 0.6f, 0xDDFFFFFF)
        GameKit.text(s, "HitMarker", "x", 0f, 0.55f, 0.32f, 0xFFFF5252).off()
        GameKit.obj(s, "HurtFX").also { it.order = 190; it.active = false; it.add(UIPanel().also { p -> p.width = 40f; p.height = 12f; p.color = 0x55B71C1C; p.borderColor = 0; p.corner = 0f }) }
        GameKit.text(s, "ObjectiveText", "", 0.5f, -0.5f, 0.28f, 0xFFFFFFFF, anchor = GameKit.TL, align = 0)
        GameKit.text(s, "TimerText", "0:00", 0f, -0.45f, 0.36f, 0xFFFFFFFF, anchor = GameKit.TOP)
        GameKit.text(s, "ScoreText", "SCORE 0", -1.6f, -1.4f, 0.28f, 0xFFD7CCB8, anchor = GameKit.TR, align = 2)
        val hp = GameKit.panel(s, "HealthPanel", 3f, 1.5f, 5.4f, 0.9f, 0xAA101010, anchor = GameKit.BL, border = 0x33FFFFFF)
        GameKit.bar(s, "HealthBar", 0f, 0f, 5f, 0.3f, 0xFFF5F5F5, hp)
        val ammo = GameKit.panel(s, "AmmoPanel", -3.2f, 3.2f, 5.6f, 1.4f, 0xAA101010, anchor = GameKit.BR, border = 0x33FFFFFF)
        GameKit.text(s, "WeaponText", "M4 CARBINE", 0f, 0.35f, 0.28f, 0xFFD7CCB8, ammo)
        GameKit.text(s, "AmmoText", "30 / 150", 0f, -0.1f, 0.46f, 0xFFFFFFFF, ammo)
        GameKit.text(s, "GrenadeText", "GRENADES 3", 0f, -0.5f, 0.22f, 0xFFB0BEC5, ammo)
        GameKit.bar(s, "ReloadBar", 0f, -1.1f, 2.4f, 0.14f, 0xFFFFFFFF, value = 0f).off()
        GameKit.text(s, "ReloadText", "", 0f, -1.5f, 0.26f, 0xFFFFFFFF)
        GameKit.text(s, "BannerText", "", 0f, 2f, 0.7f, 0xFFFFFFFF).off()
        GameKit.text(s, "ToastText", "", 0f, 1.1f, 0.34f, 0xFFFFD54F).off()
        GameKit.pauseMenu(s, "Menu", 0xFF424242)

        val win = GameKit.panel(s, "WinPanel", 0f, 0f, 7.6f, 5.8f, 0xF0101010, border = 0x8866FF66).off()
        GameKit.text(s, "WinTitle", "MISSION ACCOMPLISHED", 0f, 2.2f, 0.55f, 0xFF66FF66, win)
        GameKit.text(s, "WinStats", "", 0f, 0.5f, 0.3f, parent = win)
        GameKit.button(s, "WinNextBtn", if (night) "Replay" else "Night Raid", 0f, -1.2f, 4.4f, 0.9f, 0xFFF5F5F5, if (night) "reload" else "scene:NightRaid", win)
            .get<com.sengine.engine.core.UIButton>()!!.textColor = 0xFF111111.toInt()
        GameKit.button(s, "WinMenuBtn", "Main Menu", 0f, -2.25f, 4.4f, 0.85f, 0xFF3A3A3A, "scene:Menu", win)

        val over = GameKit.panel(s, "GameOverPanel", 0f, 0f, 7.4f, 5.4f, 0xF0101010, border = 0x88EF5350).off()
        GameKit.text(s, "LoseTitle", "K.I.A.", 0f, 1.9f, 0.8f, 0xFFEF5350, over)
        GameKit.text(s, "LoseStats", "", 0f, 0.6f, 0.3f, 0xFFE0E0E0, over)
        GameKit.button(s, "RetryBtn", "Retry mission", 0f, -0.8f, 4.4f, 0.9f, 0xFFF5F5F5, "reload", over)
            .get<com.sengine.engine.core.UIButton>()!!.textColor = 0xFF111111.toInt()
        GameKit.button(s, "LoseMenuBtn", "Main Menu", 0f, -1.9f, 4.4f, 0.85f, 0xFF3A3A3A, "scene:Menu", over)
        return s
    }
}
