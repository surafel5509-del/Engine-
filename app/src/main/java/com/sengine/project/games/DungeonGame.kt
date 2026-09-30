package com.sengine.project.games

import com.sengine.engine.core.Camera3D
import com.sengine.engine.core.Collider3D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Light
import com.sengine.engine.core.MeshRenderer
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.Scene
import com.sengine.project.Project
import com.sengine.project.games.GameKit.body3
import com.sengine.project.games.GameKit.col3
import com.sengine.project.games.GameKit.mesh
import com.sengine.project.games.GameKit.obj
import com.sengine.project.games.GameKit.obj3
import com.sengine.project.games.GameKit.off
import com.sengine.project.games.GameKit.particles
import com.sengine.project.games.GameKit.script

/**
 * "Dungeon Quest" — complete 3D action-RPG:
 *  - Explore torch-lit catacombs with a joystick, swing your sword (A) and dash (B).
 *  - Skeleton guards, spinning blade traps, gold chests, health potions, three soul flames
 *    that unseal the boss gate; a Lich boss with projectiles.
 *  - Levels & XP, minimap radar, pause, victory & defeat with run stats and records.
 */
internal object DungeonGame {
    private const val CUBE = 0; private const val SPHERE = 1; private const val CYL = 3; private const val CUSTOM = 8

    fun build(p: Project) {
        GameKit.install(p, "Stone", "Brick Wall", "Concrete", "Wood Planks", "Wooden Crate", "Metal Plate", "Checker",
            "Character (walk/idle) (editable)", "Crystal", "Barrel",
            "Soft Particle", "Spark", "Explosion", "Hit", "Power Up", "Coin Pickup", "Win Jingle", "Game Over",
            "UI Click", "Jump", "Boss Roar", "Action Battle (song)", "Menu Theme (song)", "Rotator")
        for (s in scripts) p.writeAsset(s, Games.res("dungeon/$s"))
        p.saveScene(menu())
        p.saveScene(crypt())
        p.saveControls(com.sengine.engine.controls.ControlLayout.presets.first { it.name == "Classic (Joystick + A/B)" }.copy())
        p.startScene = "DqMenu"
        p.orientation = 0
    }

    private val scripts = listOf("DqMenu.js", "DqHero.js", "DqEnemy.js", "DqGame.js")

    private fun GameObject.noShadow(): GameObject { get<MeshRenderer>()?.castShadows = false; return this }
    private fun GameObject.unlit(): GameObject { get<MeshRenderer>()?.unlit = true; get<MeshRenderer>()?.castShadows = false; return this }

    private fun wall(s: Scene, name: String, x: Float, z: Float, sx: Float, sz: Float, h: Float = 3f): GameObject =
        obj3(s, name, x, h / 2f, z, sx, h, sz).mesh(CUBE, 0xFF6E6259, "Brick.png", 3f).col3().body3(type = 0)

    private fun torch(s: Scene, x: Float, z: Float) {
        obj3(s, "TorchStick", x, 1.5f, z, 0.1f, 0.9f, 0.1f).mesh(CYL, 0xFF6D4C2F)
        val fl = obj3(s, "Torch", x, 2.15f, z, 0.4f, 0.5f, 0.4f).particles {
            applyPreset(ParticleEmitter.PRESETS.indexOf("Torch")); emitting = true; texture = "SoftDot.png"
        }
        fl.noShadow()
        val l = obj3(s, "TorchLight", x, 2.2f, z)
        l.add(Light().also { it.kind = 1; it.color = 0xFFFFB74D.toInt(); it.intensity = 2.6f; it.range = 8f })
    }

    // ------------------------------------------------------------------ menu
    private fun menu(): Scene {
        val s = Scene("DqMenu")
        val cam = obj3(s, "Main Camera", 0f, 3f, 8f)
        cam.add(Camera3D().also { c -> c.fov = 55f; c.skyTop = 0xFF060912.toInt(); c.skyHorizon = 0xFF131A2A.toInt(); c.sunDisc = false; c.quality = 2 })
        cam.add(Light().also { it.kind = 1; it.color = 0xFFFFB74D.toInt(); it.intensity = 2.4f; it.range = 16f })
        obj3(s, "MenuFloor", 0f, -0.5f, 0f, 20f, 1f, 20f).mesh(CUBE, 0xFF4A443F, "Stone.png", 6f)
        torch(s, -3f, 0f)
        torch(s, 3f, 0f)
        val hero = obj3(s, "Hero", 0f, 0f, 0f, 1.1f, 1.1f, 1.1f)
        hero.mesh(CUSTOM, 0xFFE0C398, model = "Character.smodel").also { it.get<MeshRenderer>()!!.animation = "Idle" }
        val cry = obj3(s, "MenuCrystal", 0f, 1f, -3f, 1.3f, 1.3f, 1.3f).mesh(CUSTOM, 0xFF7C4DFF, model = "Crystal.obj")
            .script("Rotator.js", "x=0, y=45, z=0")
        GameKit.text(s, "Title", "DUNGEON QUEST", 0f, -1.5f, 0.85f, 0xFFC5A0FF, anchor = GameKit.TOP)
        GameKit.text(s, "Sub", "delve the sunken crypt", 0f, -0.7f, 0.3f, 0xFFB9C4D8, anchor = GameKit.TOP)
        GameKit.button(s, "PlayBtn", "ENTER THE CRYPT", 0f, 1.1f, 5.4f, 1.05f, 0xFF7C4DFF, "scene:Crypt", anchor = GameKit.CENTER, textSize = 0.4f)
        GameKit.button(s, "HowBtn", "How to Play", 0f, 2.2f, 4.6f, 0.85f, 0xFF334155, "show:HelpPanel", anchor = GameKit.CENTER)
        GameKit.text(s, "RecordText", "", 0f, 3.6f, 0.3f, 0xFFB9C4D8, anchor = GameKit.BOTTOM)
        val help = GameKit.panel(s, "HelpPanel", 0f, -0.3f, 9.6f, 6.4f).off()
        GameKit.text(s, "HelpTitle", "HOW TO PLAY", 0f, 2.7f, 0.5f, parent = help)
        GameKit.text(s, "HelpBody", "Move with the joystick. A swings your sword,\nB dashes — use it to escape or cross blade traps.\nSmash pots for coins, open chests for gear and potions.\nCollect the 3 SOUL FLAMES to unseal the boss gate,\nthen defeat the Lich to win the crypt.\nYou level up as you slay — more damage, more health!", 0f, 0.4f, 0.28f, 0xFFE2E8F0, parent = help)
        GameKit.button(s, "HelpOkBtn", "OK", 0f, -2.6f, 3f, 0.85f, 0xFF7C4DFF, "hide:HelpPanel", parent = help)
        obj(s, "DqMenuMgr").script("DqMenu.js")
        return s
    }

    // ------------------------------------------------------------------ crypt
    private fun crypt(): Scene {
        val s = Scene("Crypt")
        val cam = obj3(s, "Main Camera", 0f, 12f, 10f)
        cam.add(Camera3D().also { c ->
            c.follow = "Hero"; c.offsetY = 10f; c.offsetZ = 7.5f; c.fov = 55f; c.far = 120f
            c.skyTop = 0xFF05070E.toInt(); c.skyHorizon = 0xFF10141F.toInt(); c.sunDisc = false
            c.quality = 1; c.shadowDistance = 26f
        })
        cam.add(Light().also { it.kind = 0; it.color = 0xFF6B7BA8.toInt(); it.intensity = 0.4f })

        // floor
        obj3(s, "Floor", 0f, -0.5f, 0f, 68f, 1f, 76f, ).mesh(CUBE, 0xFF4A443F, "Stone.png", 14f)

        // ------- entrance hall (z ~ +26..+36)
        wall(s, "W", -9f, 31f, 1.5f, 12f); wall(s, "W", 9f, 31f, 1.5f, 12f)
        wall(s, "W", 0f, 37f, 19.5f, 1.5f)
        torch(s, -8f, 33f); torch(s, 8f, 33f)

        // ------- corridor north into the hub (z ~ +14..+25)
        wall(s, "W", -4f, 20f, 1.5f, 12f); wall(s, "W", 4f, 20f, 1.5f, 12f)
        torch(s, -3.2f, 20f)

        // ------- hub chamber (z ~ -2..+14, x ~ -12..+12)
        wall(s, "W", -12.7f, 6f, 1.5f, 18f)
        wall(s, "W", 12.7f, 6f, 1.5f, 18f)
        wall(s, "W", -8f, 14.7f, 10f, 1.5f)   // north wall with gaps
        wall(s, "W", 8f, 14.7f, 10f, 1.5f)
        torch(s, -6f, 8f); torch(s, 6f, 8f); torch(s, 0f, 0f)
        // decorative columns
        for ((cx, cz) in listOf(-7f to 3f, 7f to 3f, -7f to 11f, 7f to 11f)) {
            obj3(s, "Column", cx, 2f, cz, 1.2f, 4f, 1.2f).mesh(CYL, 0xFF7A7066, "Stone.png", 2f).col3()
        }

        // ------- west arm (boss gate + arena) x ~ -34..-12
        wall(s, "W", -23f, 13.5f, 21f, 1.5f)
        wall(s, "W", -23f, -1.5f, 21f, 1.5f)
        wall(s, "W", -33.5f, 6f, 1.5f, 17f)
        torch(s, -18f, 8f); torch(s, -30f, 4f)
        // boss gate
        val gate = obj3(s, "BossGate", -14.5f, 2f, 6f, 2f, 4f, 8f).mesh(CUBE, 0xFF5E35B1, "Metal.png", 2f).col3().body3(type = 0)
        gate.tag = "Gate"
        // boss arena
        obj3(s, "BossFloor", -27f, -0.45f, 6f, 16f, 0.1f, 16f).mesh(CUBE, 0xFF3A3441, "Checker.png", 5f)
        val altar = obj3(s, "Altar", -31f, 0.7f, 6f, 1.6f, 1.4f, 1.6f).mesh(CUBE, 0xFF4E4258, "Stone.png", 1f).col3()
        altar.script("Rotator.js", "x=0, y=0, z=0")

        // ------- east arm (treasury) x ~ +12..+34
        wall(s, "W", 23f, 13.5f, 21f, 1.5f)
        wall(s, "W", 23f, -1.5f, 21f, 1.5f)
        wall(s, "W", 33.5f, 6f, 1.5f, 17f)
        torch(s, 18f, 8f); torch(s, 30f, 4f)
        chest(s, 30f, 9f, "big")
        chest(s, 27f, 2f, "small")
        chest(s, 31f, 3f, "small")

        // ------- north arm (soul flames 2 & 3) z ~ -14..-2
        wall(s, "W", -6f, -8f, 1.5f, 12f); wall(s, "W", 6f, -8f, 1.5f, 12f)
        wall(s, "W", 0f, -13.7f, 13.5f, 1.5f)
        wall(s, "W", -9f, -19f, 19.5f, 1.5f)
        torch(s, -4f, -6f); torch(s, 4f, -10f)

        // soul flames (3)
        soul(s, -26f, 6f)   // west, in front of the boss gate
        soul(s, 0f, -11f)   // north hall
        soul(s, 30f, 6f)    // treasury

        // blade traps in the corridors
        trap(s, -23f, 8f)
        trap(s, -23f, 4f)
        trap(s, 23f, 8f)
        trap(s, 0f, 22f)
        trap(s, 0f, 26f)

        // pots & barrels
        for ((px, pz) in listOf(-3f to 29f, 3f to 27f, -10f to 9f, 10f to 12f, -2f to -6f, 5f to -12f, 20f to 5f, -20f to 2f)) {
            pot(s, px, pz)
        }
        for ((bx, bz) in listOf(-8f to 13f, 8f to 1f, 16f to 11f)) {
            obj3(s, "Barrel2D", bx, 0.55f, bz, 0.9f, 1.1f, 0.9f).mesh(CUSTOM, 0xFF8D6E63, model = "Barrel.obj").col3()
        }

        // skeleton template
        skeleton(s)
        // boss
        boss(s)
        // hero
        val hero = obj3(s, "Hero", 0f, 0f, 33f)
        hero.tag = "Player"
        hero.add(Collider3D().also { it.sizeX = 0.8f; it.sizeY = 1.8f; it.sizeZ = 0.8f })
        hero.add(Rigidbody3D().also { it.bodyType = 2; it.gravityScale = 0f; it.drag = 0f })
        hero.mesh(CUSTOM, 0xFFE0C398, model = "Character.smodel").also { it.get<MeshRenderer>()!!.animation = "Idle" }
        // sword in hand (simple bright blade child)
        obj3(s, "Sword", 0.45f, -0.6f, -0.25f, 0.09f, 0.09f, 0.8f, hero).mesh(CUBE, 0xFFCFD8DC, "Metal.png").noShadow()
        hero.script("DqHero.js", "speed=6.5, hp=100, damage=25")

        // FX
        fx(s, "Sparkle", "Magic", { speed = 2.5f; lifetime = 0.6f; startColor = 0xFFB388FF.toInt(); endColor = 0x007C4DFF })
        fx(s, "HitFx", "Sparks", { speed = 6f; lifetime = 0.35f; startColor = 0xFFFFEB3B.toInt(); endColor = 0x00FF6F00 })
        fx(s, "SoulFx", "Magic", { speed = 1.5f; lifetime = 0.9f; startColor = 0xFF82B1FF.toInt(); endColor = 0x002962FF })

        // HUD
        GameKit.bar(s, "HpBar", 0f, 0.2f, 3.2f, 0.26f, 0xFFFF5C6C, anchor = GameKit.BOTTOM, value = 1f)
        GameKit.text(s, "HpText", "HP 100", 0f, 0f, 0.24f, 0xFFFFFFFF, anchor = GameKit.BOTTOM)
        GameKit.bar(s, "XpBar", 0f, -0.25f, 3.2f, 0.14f, 0xFF7C4DFF, anchor = GameKit.BOTTOM, value = 0f)
        GameKit.text(s, "LvlText", "Lv.1", -2.6f, -0.25f, 0.26f, 0xFFB388FF, anchor = GameKit.BOTTOM)
        GameKit.text(s, "SoulText", "SOULS 0/3", 3.2f, 0.2f, 0.3f, 0xFF82B1FF, anchor = GameKit.BOTTOM)
        GameKit.text(s, "GoldText", "0g", -3.2f, 0.2f, 0.32f, 0xFFFFD166, anchor = GameKit.BOTTOM)
        GameKit.radar(s, "Radar", 0f, 0f, 2.2f, "Enemy,Soul,Pickup,Gate", 60f, anchor = GameKit.TR, dot = 0xFF82B1FF)
        GameKit.pauseMenu(s, "DqMenu", 0xFF7C4DFF)
        val over = GameKit.panel(s, "OverPanel", 0f, 0f, 7.8f, 5.6f).off()
        GameKit.text(s, "OverTitle", "YOU DIED", 0f, 1.8f, 0.7f, 0xFFFF5C6C, parent = over)
        GameKit.text(s, "OverStats", "", 0f, 0.7f, 0.34f, 0xFFE2E8F0, parent = over)
        GameKit.button(s, "AgainBtn", "Respawn", 0f, -0.6f, 4.4f, 0.95f, 0xFF7C4DFF, "resume;reload", over)
        GameKit.button(s, "OverMenuBtn", "Menu", 0f, -1.7f, 4.4f, 0.9f, 0xFF3A4566, "resume;scene:DqMenu", over)
        val win = GameKit.panel(s, "WinPanel", 0f, 0f, 7.8f, 6f).off()
        GameKit.text(s, "WinTitle", "CRYPT CLEARED!", 0f, 1.8f, 0.7f, 0xFFFFD166, parent = win)
        GameKit.text(s, "WinStats", "", 0f, 0.7f, 0.34f, 0xFFE2E8F0, parent = win)
        GameKit.button(s, "WinAgainBtn", "Delve Again", 0f, -0.6f, 4.4f, 0.95f, 0xFF7C4DFF, "resume;reload", win)
        GameKit.button(s, "WinMenuBtn", "Menu", 0f, -1.7f, 4.4f, 0.9f, 0xFF3A4566, "resume;scene:DqMenu", win)
        obj(s, "DqDirector").script("DqGame.js")
        return s
    }

    private fun chest(s: Scene, x: Float, z: Float, kind: String) {
        val c = obj3(s, "Chest", x, 0.4f, z, 1f, 0.8f, 0.8f).mesh(CUBE, 0xFF8D6E63, "Wood.png", 1f).col3(true, true)
        c.tag = "Pickup"
        c.script("DqGame.js", "kind=\"chest\", chestKind=\"$kind\"")
        obj3(s, "Lid", 0f, 0.45f, 0f, 1.05f, 0.25f, 0.85f, c).mesh(CUBE, 0xFF6D4C2F, "Metal.png", 1f)
        obj3(s, "Lock", 0f, 0.1f, -0.42f, 0.16f, 0.16f, 0.08f, c).mesh(CUBE, 0xFFFFD54F).unlit()
    }

    private fun pot(s: Scene, x: Float, z: Float) {
        val p = obj3(s, "Pot", x, 0.4f, z, 0.6f, 0.8f, 0.6f).mesh(CYL, 0xFFA1887F, "Stone.png", 1f).col3(true, true)
        p.tag = "Pickup"
        p.script("DqGame.js", "kind=\"pot\"")
    }

    private fun soul(s: Scene, x: Float, z: Float) {
        val f = obj3(s, "Soul", x, 1.2f, z, 0.7f, 0.7f, 0.7f)
        f.tag = "Soul"
        f.mesh(SPHERE, 0xFF82B1FF).unlit().noShadow()
        f.script("DqGame.js", "kind=\"soul\"")
        val halo = obj3(s, "SoulHalo", x, 0.05f, z, 1.4f, 0.1f, 1.4f).mesh(CYL, 0x4482B1FF).unlit().noShadow()
        halo.script("Rotator.js", "x=0, y=60, z=0")
    }

    private fun trap(s: Scene, x: Float, z: Float) {
        val t = obj3(s, "Trap", x, 0.35f, z, 2.2f, 0.5f, 0.5f)
        t.tag = "Trap"
        t.mesh(CUBE, 0xFFB0BEC5, "Metal.png").noShadow()
        t.script("Rotator.js", "x=0, y=220, z=0")
        t.script("DqGame.js", "kind=\"trap\"")
    }

    private fun skeleton(s: Scene) {
        val e = obj3(s, "Skeleton", 0f, -40f, 0f).off()
        e.tag = "Enemy"
        e.add(Collider3D().also { it.sizeX = 0.8f; it.sizeY = 1.8f; it.sizeZ = 0.8f })
        e.mesh(CUSTOM, 0xFFE8E4D8, model = "Character.smodel").also { it.get<MeshRenderer>()!!.animation = "Idle" }
        e.script("DqEnemy.js", "speed=2.6, hp=40, damage=12")
    }

    private fun boss(s: Scene) {
        val b = obj3(s, "Lich", -27f, 0f, 6f).off()
        b.tag = "Enemy"
        b.add(Collider3D().also { it.sizeX = 1.4f; it.sizeY = 2.6f; it.sizeZ = 1.4f })
        b.mesh(CUSTOM, 0xFFB39DDB, model = "Character.smodel", tiling = 1f).also { it.get<MeshRenderer>()!!.animation = "Idle" }
        b.scaleX = 1.8f; b.scaleY = 1.8f; b.scaleZ = 1.8f
        obj3(s, "Crown", 0f, 0.5f, 0f, 0.5f, 0.25f, 0.5f, b).mesh(CYL, 0xFFFFD54F).noShadow()
        b.script("DqEnemy.js", "speed=2, hp=400, damage=25, boss=true")
        val eye = obj3(s, "BossEye", -27f, 1.5f, 6f, 0.3f, 0.3f, 0.3f).mesh(SPHERE, 0xFFE040FB).unlit().off()
        eye.script("DqGame.js", "kind=\"bossOrb\"")
    }

    private fun fx(s: Scene, name: String, preset: String, tweak: ParticleEmitter.() -> Unit = {}): GameObject =
        obj3(s, name, 0f, -50f, 0f).particles { applyPreset(ParticleEmitter.PRESETS.indexOf(preset)); emitting = false; rate = 0f; texture = "SoftDot.png"; tweak() }.off()
}
