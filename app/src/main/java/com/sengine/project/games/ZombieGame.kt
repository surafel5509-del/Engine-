package com.sengine.project.games

import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SpriteRenderer
import com.sengine.project.Project
import com.sengine.project.games.GameKit.body
import com.sengine.project.games.GameKit.box
import com.sengine.project.games.GameKit.circle
import com.sengine.project.games.GameKit.off
import com.sengine.project.games.GameKit.particles
import com.sengine.project.games.GameKit.script
import com.sengine.project.games.GameKit.sprite

/**
 * "Dead Zone" — complete 2D top-down zombie survival: twin-stick controls, 3 weapons with magazines
 * and reloading, 3 zombie types, endless scaling waves, loot, upgrades between waves, records.
 */
internal object ZombieGame {
    private val scripts = listOf("DZPlayer.js", "DZBullet.js", "DZZombie.js", "DZPickup.js", "DZSplat.js", "DZGame.js", "DZMenu.js", "DZWander.js")

    fun build(p: Project) {
        GameKit.install(p, "Soldier (top-down)", "Zombie (top-down)", "Zombie Brute", "Zombie Ground", "Blood Splat", "Health Kit", "Ammo Box",
            "Muzzle Flash", "Glow Bullet", "Wooden Crate", "Concrete", "Race Car (top-down)", "Crosshair", "Soft Particle",
            "Pistol Shot", "Shotgun Blast", "Reload", "Empty Click", "Zombie Groan", "Footstep", "Hit", "Power Up", "Boss Roar", "Win Jingle", "Game Over",
            "UI Click (default)", "Spooky Night (song)")
        for (s in scripts) p.writeAsset(s, Games.res("zombie/$s"))
        p.saveScene(menu())
        p.saveScene(arena())
        p.startScene = "Menu"
        p.orientation = 0
    }

    private fun menu(): Scene {
        val s = Scene("Menu"); s.gravityY = 0f
        GameKit.camera2D(s, 6f, 0xFF120C0C)
        GameKit.obj(s, "Ground", 0f, 0f, 30f, 16f).sprite(0xFF6B5F55, 0, "DeadGround.png").also { it.get<SpriteRenderer>()!!.tileX = 6f; it.get<SpriteRenderer>()!!.tileY = 3f; it.order = -100 }
        GameKit.obj(s, "MenuManager").script("DZMenu.js")
        for ((i, pos) in listOf(-8f to 2f, 7.5f to -2.5f, -6f to -3.5f, 9f to 3f).withIndex()) {
            GameKit.obj(s, "Deco$i", pos.first, pos.second, 1.6f, 1.6f).sprite(if (i == 3) 0xFFFFFFFF else 0xFFDDDDDD, 1, if (i == 3) "ZombieBrute.png" else "Zombie.png").script("DZWander.js")
        }
        GameKit.obj(s, "Fog").particles {
            rate = 6f; lifetime = 6f; speed = 0.6f; direction = 0f; spread = 180f; startSize = 4f; endSize = 6f; startColor = 0x22FFFFFF; endColor = 0x00FFFFFF; texture = "SoftDot.png"
        }
        GameKit.text(s, "Title", "DEAD ZONE", 0f, 3.4f, 1.3f, 0xFFEF4444)
        GameKit.text(s, "Subtitle", "Survive the undead horde", 0f, 2.4f, 0.35f, 0xFFD4C4B0)
        val main = GameKit.panel(s, "MainPanel", 0f, -1.1f, 5.2f, 5f, 0xCC1A1010, border = 0x66EF4444)
        GameKit.button(s, "PlayBtn", "SURVIVE", 0f, 1.5f, 4.2f, 1f, 0xFFDC2626, "scene:Arena", main, textSize = 0.45f)
        GameKit.button(s, "SettingsBtn", "Settings", 0f, 0.35f, 4.2f, 0.85f, 0xFF4B3A3A, "show:SettingsPanel;hide:MainPanel", main)
        GameKit.button(s, "HelpBtn", "How to play", 0f, -0.7f, 4.2f, 0.85f, 0xFF4B3A3A, "show:HelpPanel;hide:MainPanel", main)
        GameKit.button(s, "QuitBtn", "Quit", 0f, -1.75f, 4.2f, 0.85f, 0xFF2A2020, "quit", main)
        GameKit.text(s, "RecordText", "", 0f, 0.5f, 0.35f, 0xFFFFD166, anchor = GameKit.BOTTOM)

        val set = GameKit.panel(s, "SettingsPanel", 0f, -0.6f, 6.4f, 5.4f, border = 0x66EF4444).off()
        GameKit.text(s, "SetTitle", "SETTINGS", 0f, 2.1f, 0.6f, parent = set)
        GameKit.button(s, "MusicBtn", "Music: ON", 0f, 1f, 5f, 0.85f, 0xFF4B3A3A, "call:toggleMusic", set)
        GameKit.button(s, "SfxBtn", "Sound FX: 100%", 0f, 0f, 5f, 0.85f, 0xFF4B3A3A, "call:cycleSfx", set)
        GameKit.button(s, "DiffBtn", "Difficulty: Normal", 0f, -1f, 5f, 0.85f, 0xFF4B3A3A, "call:cycleDifficulty", set)
        GameKit.button(s, "SetBackBtn", "Back", 0f, -2.1f, 3f, 0.8f, 0xFFDC2626, "hide:SettingsPanel;show:MainPanel", set)

        val help = GameKit.panel(s, "HelpPanel", 0f, -0.6f, 9.4f, 6f, border = 0x66EF4444).off()
        GameKit.text(s, "HelpTitle", "HOW TO SURVIVE", 0f, 2.4f, 0.6f, parent = help)
        GameKit.text(s, "HelpBody", "Left stick: move.   Right stick: aim & shoot.\nNot aiming? You auto-aim at the closest zombie.\nRELOAD button reloads, SPECIAL switches weapon.\nPistol (infinite), Shotgun and Rifle use ammo boxes.\nRunners appear from wave 3, Brutes from wave 5.\nPick an upgrade after every wave!", 0f, 0.2f, 0.3f, 0xFFE8DCCB, help)
        GameKit.button(s, "HelpBackBtn", "Let's go", 0f, -2.4f, 3f, 0.8f, 0xFFDC2626, "hide:HelpPanel;show:MainPanel", help)
        return s
    }

    private fun arena(): Scene {
        val s = Scene("Arena"); s.gravityY = 0f
        GameKit.camera2D(s, 7f, 0xFF0D0A08, follow = "Player")
        val hw = 20f; val hh = 13f
        GameKit.obj(s, "Ground", 0f, 0f, hw * 2 + 4, hh * 2 + 4).sprite(0xFF8A7A6A, 0, "DeadGround.png").also { it.get<SpriteRenderer>()!!.tileX = 10f; it.get<SpriteRenderer>()!!.tileY = 7f; it.order = -100 }
        GameKit.obj(s, "Game").script("DZGame.js")

        // walls
        fun wall(name: String, x: Float, y: Float, w: Float, h: Float, color: Long = 0xFF6B6B70, tex: String = "Concrete.png") {
            val o = GameKit.obj(s, name, x, y, w, h).sprite(color, 0, tex).box().body(type = 2)
            o.tag = "Wall"; o.order = -10
            o.get<SpriteRenderer>()!!.tileX = maxOf(1f, w / 2); o.get<SpriteRenderer>()!!.tileY = maxOf(1f, h / 2)
        }
        wall("WallTop", 0f, hh + 0.5f, hw * 2 + 2, 1f); wall("WallBottom", 0f, -hh - 0.5f, hw * 2 + 2, 1f)
        wall("WallLeft", -hw - 0.5f, 0f, 1f, hh * 2); wall("WallRight", hw + 0.5f, 0f, 1f, hh * 2)
        // cover: crates, concrete blocks and wrecked cars
        val crates = listOf(-6f to 4f, -5f to 4f, 6f to -4f, 6f to -5f, -12f to -7f, 12f to 7f, 0f to 8f, 0f to -8f, -15f to 2f, 15f to -2f)
        for ((i, c) in crates.withIndex()) wall("Crate$i", c.first, c.second, 1f, 1f, 0xFFFFFFFF, "Crate.png")
        for ((i, c) in listOf(-9f to 0f, 9f to 0f, 0f to 3.5f).withIndex()) wall("Block$i", c.first, c.second, 3f, 1f)
        for ((i, c) in listOf(Triple(-13f, 8f, 20f), Triple(13f, -8f, -35f), Triple(4f, 10f, 80f)).withIndex()) {
            val car = GameKit.obj(s, "Wreck$i", c.first, c.second, 1.6f, 3.2f).sprite(0xFF9E9E9E, 0, "CarTop.png").box().body(type = 2)
            car.rotation = c.third; car.tag = "Wall"; car.order = -9
        }
        // spawn points around the edge
        val sp = listOf(-18f to 11f, 0f to 11.5f, 18f to 11f, 18.5f to 0f, 18f to -11f, 0f to -11.5f, -18f to -11f, -18.5f to 0f)
        for ((i, c) in sp.withIndex()) GameKit.obj(s, "SpawnPoint$i", c.first, c.second).also { it.tag = "SpawnPoint" }

        // player
        val player = GameKit.obj(s, "Player", 0f, 0f, 1.3f, 1.3f).sprite(0xFFFFFFFF, 1, "Soldier.png").circle(false, 0.35f).body(type = 0, gravity = 0f, friction = 0f)
            .script("DZPlayer.js")
        player.tag = "Player"; player.order = 10
        player.get<Rigidbody2D>()!!.drag = 0f

        // zombies (templates)
        fun zombie(name: String, kind: String, size: Float, tex: String, color: Long) {
            val z = GameKit.obj(s, name, 0f, 30f, size, size).sprite(color, 1, tex).circle(false, 0.33f).body(type = 0, gravity = 0f, friction = 0f)
                .script("DZZombie.js", "kind=$kind").off()
            z.tag = "Zombie"; z.order = 8
        }
        zombie("Walker", "walker", 1.3f, "Zombie.png", 0xFFFFFFFF)
        zombie("Runner", "runner", 1.15f, "Zombie.png", 0xFFFFD0A0)
        zombie("Brute", "brute", 2.1f, "ZombieBrute.png", 0xFFFFFFFF)

        val bullet = GameKit.obj(s, "Bullet", 0f, 0f, 0.35f, 0.12f).sprite(0xFFFFF59D, 0).box(true).body(type = 1).script("DZBullet.js").off()
        bullet.tag = "Bullet"; bullet.order = 9
        GameKit.obj(s, "Muzzle", 0f, 0f, 0.9f, 0.9f).sprite(0xFFFFFFFF, 1, "MuzzleFlash.png").off().order = 12
        GameKit.obj(s, "Splat", 0f, 0f, 1.6f, 1.6f).sprite(0xCC7F0000, 1, "Splat.png").script("DZSplat.js").off().order = -50
        for ((name, kind, tex) in listOf(Triple("Medkit", "medkit", "Medkit.png"), Triple("AmmoBox", "ammo", "Ammo.png"))) {
            val pu = GameKit.obj(s, name, 0f, 0f, 0.8f, 0.8f).sprite(if (kind == "medkit") 0xFFFFFFFF else 0xFFFFE082, 4, tex).circle(true).body(type = 1).script("DZPickup.js", "kind=$kind").off()
            pu.tag = "Pickup"; pu.order = 5
        }
        GameKit.obj(s, "Blood").particles {
            emitting = false; rate = 0f; spread = 360f; speed = 2.5f; lifetime = 0.45f; startSize = 0.22f; endSize = 0.05f; startColor = 0xFFB71C1C.toInt(); endColor = 0x004A0000
        }.off().order = 15
        GameKit.obj(s, "Dust").particles {
            emitting = false; rate = 0f; spread = 360f; speed = 1.5f; lifetime = 0.3f; startSize = 0.15f; startColor = 0xFFBDBDBD.toInt(); endColor = 0x00757575
        }.off().order = 15

        // HUD
        val hud = GameKit.panel(s, "HudPanel", 3.2f, -0.8f, 6f, 1.3f, 0xAA140C0C, anchor = GameKit.TL, border = 0x00000000)
        GameKit.bar(s, "HealthBar", 0f, 0.25f, 5.4f, 0.32f, 0xFFEF4444, hud)
        GameKit.text(s, "HpText", "100 / 100", 0f, 0.25f, 0.22f, parent = hud)
        GameKit.text(s, "WeaponText", "PISTOL", -1.4f, -0.3f, 0.3f, 0xFFFFD166, hud)
        GameKit.text(s, "AmmoText", "12 / INF", 1.4f, -0.3f, 0.3f, parent = hud)
        GameKit.text(s, "WaveText", "WAVE 1", 0f, -0.45f, 0.45f, 0xFFEF4444, anchor = GameKit.TOP)
        GameKit.text(s, "ZombiesText", "", 0f, -1f, 0.26f, 0xFFD4C4B0, anchor = GameKit.TOP)
        GameKit.text(s, "ScoreText", "SCORE 0", -1.8f, -1.3f, 0.3f, anchor = GameKit.TR, align = 2)
        GameKit.text(s, "KillsText", "KILLS 0", -1.8f, -1.8f, 0.26f, 0xFFD4C4B0, anchor = GameKit.TR, align = 2)
        GameKit.bar(s, "ReloadBar", 0f, -1.2f, 2.4f, 0.16f, 0xFFFFD166, value = 0f).off()
        GameKit.text(s, "ReloadText", "", 0f, -1.6f, 0.26f, 0xFFFFD166)
        GameKit.text(s, "BannerText", "", 0f, 1.6f, 0.8f, 0xFFFFFFFF).off()
        GameKit.text(s, "ToastText", "", 0f, 0.8f, 0.4f, 0xFF4ADE80).off()
        GameKit.pauseMenu(s, "Menu", 0xFFDC2626)

        val up = GameKit.panel(s, "UpgradePanel", 0f, 0f, 7f, 5.2f, border = 0x88FFD166).off()
        GameKit.text(s, "UpTitle", "CHOOSE AN UPGRADE", 0f, 1.9f, 0.5f, 0xFFFFD166, up)
        for (i in 1..3) GameKit.button(s, "Upgrade$i", "Upgrade", 0f, 1.1f - i * 1.1f, 5.6f, 0.9f, 0xFF7C2D12, "call:pickUpgrade", up)

        val over = GameKit.panel(s, "GameOverPanel", 0f, 0f, 7.4f, 5.4f, border = 0x88EF4444).off()
        GameKit.text(s, "DeadTitle", "YOU DIED", 0f, 1.9f, 0.8f, 0xFFEF4444, over)
        GameKit.text(s, "DeadStats", "", 0f, 0.6f, 0.33f, parent = over)
        GameKit.button(s, "RetryBtn", "Try Again", 0f, -0.8f, 4.4f, 0.9f, 0xFFDC2626, "reload", over)
        GameKit.button(s, "DeadMenuBtn", "Main Menu", 0f, -1.9f, 4.4f, 0.9f, 0xFF4B3A3A, "scene:Menu", over)
        return s
    }
}
