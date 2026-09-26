package com.sengine.project.games

import com.sengine.engine.core.ParticleEmitter
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
 * "Iron Tanks" — complete top-down tank battle: hull + independently aiming turret, destructible
 * brick walls, steel, rivers and bushes, three enemy tank types with lane-hunting AI, HQ to defend,
 * HE shells, power-ups, lives, 3 missions with stars and unlocks, settings and records.
 */
internal object TankGame {
    private val scripts = listOf("ITPlayer.js", "ITEnemy.js", "ITShell.js", "ITBrick.js", "ITHQ.js", "ITPickup.js", "ITGame.js", "ITMenu.js")

    /** 24 × 16 maps. B brick, S steel, W water, G bush, P player, E enemy gate, H headquarters. */
    private val MAPS = listOf(
        listOf(
            "E..........E..........E.",
            "........................",
            "..BB..BB..SS..BB..BB....",
            "..BB..BB......BB..BB....",
            "..BB..BB..BB..BB..BB..GG",
            "..........BB..........GG",
            "GG..BBBB........BBBB....",
            "GG..........SS..........",
            "....BB..BB......BB..BB..",
            "....BB..BB..BB..BB..BB..",
            "....BB......BB......BB..",
            "..GG....BBBBBBBB....GG..",
            "..GG..................GG",
            "..BB....P..BBB.....BB...",
            "..BB.......BHB.....BB...",
            "...........B.B..........",
        ),
        listOf(
            "E..........E..........E.",
            "....BB..........BB......",
            "..SSBB..GGGGGG..BBSS....",
            "........GGGGGG..........",
            "WWWW..WWWWWWWWWWWW..WWWW",
            "WWWW..WWWWWWWWWWWW..WWWW",
            "....BB..........BB......",
            "..BBBB..BB..BB..BBBB....",
            "........BB..BB..........",
            "GG..SS..........SS..GG..",
            "GG....BBBB..BBBB....GG..",
            "..........BB............",
            "..BB..GG........GG..BB..",
            "..BB....P..BBB.....BB...",
            "...........BHB..........",
            "...........B.B..........",
        ),
        listOf(
            "E....S.....E.....S....E.",
            "..........SSS...........",
            "..SSBBSS........SSBBSS..",
            "..S....S..BBBB..S....S..",
            "..S.GG.S........S.GG.S..",
            "..SSBBSS..WWWW..SSBBSS..",
            "..........WWWW..........",
            "BBBB..BB..........BB..BB",
            "....GGGG..SSSS..GGGG....",
            "..BB......BBBB......BB..",
            "..BBSS............SSBB..",
            "......BBBB....BBBB......",
            "..GG..................GG",
            "..SS....P..SSS.....SS...",
            "...........SHS..........",
            "...........B.B..........",
        ),
    )

    fun build(p: Project) {
        GameKit.install(p, "Tank Hull", "Tank Turret", "Bush", "Scorch Mark", "HQ Eagle", "Brick Wall", "Metal Plate", "Water", "Grass Field",
            "Concrete", "Camo", "Soft Particle", "Spark", "Muzzle Flash", "Glow Bullet", "Health Kit", "Star", "Shield Bubble", "Heart",
            "Pistol Shot", "Shotgun Blast", "Explosion", "Hit", "Power Up", "Block Break", "Boss Roar", "Win Jingle", "Game Over",
            "UI Click (default)", "Action Battle (song)", "Menu Theme (song)")
        for (s in scripts) p.writeAsset(s, Games.res("tanks/$s"))
        p.saveScene(menu())
        p.saveScene(battle())
        p.startScene = "Menu"
        p.orientation = 0
    }

    private fun menu(): Scene {
        val s = Scene("Menu"); s.gravityY = 0f
        GameKit.camera2D(s, 6f, 0xFF10140E)
        GameKit.obj(s, "Ground", 0f, 0f, 30f, 16f).sprite(0xFF7A8A6A, 0, "GrassField.png").also { it.get<SpriteRenderer>()!!.tileX = 6f; it.get<SpriteRenderer>()!!.tileY = 3f; it.order = -100 }
        GameKit.obj(s, "MenuManager").script("ITMenu.js")
        for ((i, pos) in listOf(-8f to -3f, 8.5f to 2.5f, -9f to 3.2f).withIndex()) {
            val t = GameKit.obj(s, "DecoTank$i", pos.first, pos.second, 1.8f, 1.8f).sprite(if (i == 0) 0xFF7CB342 else 0xFFE57373, 0, "TankHull.png")
            t.rotation = listOf(35f, 200f, -20f)[i]
            GameKit.obj(s, "DecoTurret$i", 0f, 0f, 1f, 1f, parent = t).sprite(if (i == 0) 0xFF9CCC65 else 0xFFEF9A9A, 0, "TankTurret.png").order = 1
        }
        GameKit.obj(s, "Smoke").particles { applyPreset(ParticleEmitter.PRESETS.indexOf("Smoke")); rate = 5f; texture = "SoftDot.png" }.also { it.x = 8.5f; it.y = 2.5f }
        GameKit.text(s, "Title", "IRON TANKS", 0f, 3.4f, 1.3f, 0xFFFFFFFF)
        GameKit.text(s, "Subtitle", "Defend the HQ. Destroy every enemy tank.", 0f, 2.4f, 0.34f, 0xFFBFC8B0)
        val main = GameKit.panel(s, "MainPanel", 0f, -1.1f, 6.6f, 5.6f, 0xE0101010, border = 0x66FFFFFF)
        for (l in 1..3) GameKit.button(s, "Mission${l}Btn", "Mission $l", 0f, 2.35f - l * 1f, 5.8f, 0.85f, if (l == 1) 0xFFF5F5F5 else 0xFF3A3A3A, "call:mission$l", main, textSize = 0.34f)
            .also { if (l == 1) it.get<com.sengine.engine.core.UIButton>()!!.textColor = 0xFF111111.toInt() }
        GameKit.button(s, "SettingsBtn", "Settings", -1.5f, -1.95f, 2.8f, 0.8f, 0xFF2A2A2A, "show:SettingsPanel;hide:MainPanel", main)
        GameKit.button(s, "HelpBtn", "How to play", 1.5f, -1.95f, 2.8f, 0.8f, 0xFF2A2A2A, "show:HelpPanel;hide:MainPanel", main)
        GameKit.text(s, "BestText", "", 0f, 0.5f, 0.34f, 0xFFFFD166, anchor = GameKit.BOTTOM)

        val set = GameKit.panel(s, "SettingsPanel", 0f, -0.6f, 6.4f, 5.4f, 0xE0101010, border = 0x66FFFFFF).off()
        GameKit.text(s, "SetTitle", "SETTINGS", 0f, 2.1f, 0.6f, parent = set)
        GameKit.button(s, "MusicBtn", "Music: ON", 0f, 1f, 5f, 0.85f, 0xFF2A2A2A, "call:toggleMusic", set)
        GameKit.button(s, "SfxBtn", "Sound FX: 100%", 0f, 0f, 5f, 0.85f, 0xFF2A2A2A, "call:cycleSfx", set)
        GameKit.button(s, "DiffBtn", "Difficulty: Veteran", 0f, -1f, 5f, 0.85f, 0xFF2A2A2A, "call:cycleDifficulty", set)
        GameKit.button(s, "SetBackBtn", "Back", 0f, -2.1f, 3f, 0.8f, 0xFFF5F5F5, "hide:SettingsPanel;show:MainPanel", set)
            .get<com.sengine.engine.core.UIButton>()!!.textColor = 0xFF111111.toInt()

        val help = GameKit.panel(s, "HelpPanel", 0f, -0.6f, 9.6f, 6f, 0xE0101010, border = 0x66FFFFFF).off()
        GameKit.text(s, "HelpTitle", "FIELD MANUAL", 0f, 2.4f, 0.6f, parent = help)
        GameKit.text(s, "HelpBody", "Left stick: drive (the hull turns toward the stick).\nRight stick: aim the turret and fire. Idle? It auto-aims.\nSPECIAL: high-explosive shell, blasts walls (4s reload).\nBricks crumble, steel doesn't, tanks can't cross rivers.\nHide in bushes. Protect the golden HQ at the bottom!\nCrates: wrench repair, star firepower, shield, heart life.", 0f, 0.2f, 0.29f, 0xFFE0E0E0, help)
        GameKit.button(s, "HelpBackBtn", "Roll out", 0f, -2.4f, 3f, 0.8f, 0xFFF5F5F5, "hide:HelpPanel;show:MainPanel", help)
            .get<com.sengine.engine.core.UIButton>()!!.textColor = 0xFF111111.toInt()
        return s
    }

    private fun battle(): Scene {
        val s = Scene("Battle"); s.gravityY = 0f
        GameKit.camera2D(s, 6.5f, 0xFF0B0E0A, follow = "Player")
        val cols = 24; val rows = 16
        val ox = -cols / 2f + 0.5f; val oy = rows / 2f - 0.5f
        GameKit.obj(s, "Ground", 0f, 0f, cols + 4f, rows + 4f).sprite(0xFF9AA58A, 0, "GrassField.png").also { it.get<SpriteRenderer>()!!.tileX = 8f; it.get<SpriteRenderer>()!!.tileY = 6f; it.order = -100 }
        GameKit.obj(s, "Game").script("ITGame.js")
        // map holder: the Game script reads the mission number and enables the matching layout
        for ((mi, map) in MAPS.withIndex()) {
            val root = GameKit.obj(s, "Map${mi + 1}")
            if (mi != 0) root.active = false
            root.tag = "MapRoot"
            for (r in 0 until rows) {
                val row = map.getOrElse(r) { "" }.padEnd(cols, '.').take(cols)
                for (c in 0 until cols) {
                    val x = ox + c; val y = oy - r
                    when (row[c]) {
                        'B' -> GameKit.obj(s, "Brick", x, y, 1f, 1f, root).sprite(0xFFFFFFFF, 0, "Brick.png").box().body(type = 2).script("ITBrick.js").also { it.tag = "Brick"; it.order = -5 }
                        'S' -> GameKit.obj(s, "Steel", x, y, 1f, 1f, root).sprite(0xFFCFD8DC, 0, "Metal.png").box().body(type = 2).also { it.tag = "Steel"; it.order = -5 }
                        'W' -> GameKit.obj(s, "River", x, y, 1.02f, 1.02f, root).sprite(0xFF90CAF9, 0, "Water.png").box().body(type = 2).also { it.tag = "Water"; it.order = -20 }
                        'G' -> GameKit.obj(s, "Bush", x, y, 1.25f, 1.25f, root).sprite(0xFFFFFFFF, 0, "Bush.png").also { it.tag = "Bush"; it.order = 30 }
                        'E' -> GameKit.obj(s, "EnemySpawn", x, y, parent = root).also { it.tag = "EnemySpawn" }
                        'H' -> GameKit.obj(s, "HQ", x, y, 1f, 1f, root).sprite(0xFFFFFFFF, 0, "HQ.png").box().body(type = 2).script("ITHQ.js").also { it.tag = "HQ"; it.order = -4 }
                        'P' -> GameKit.obj(s, "PlayerStart$mi", x, y, parent = root).also { it.tag = "PlayerStart" }
                    }
                }
            }
        }
        // border walls
        fun wall(name: String, x: Float, y: Float, w: Float, h: Float) {
            val o = GameKit.obj(s, name, x, y, w, h).sprite(0xFF607D8B, 0, "Concrete.png").box().body(type = 2)
            o.tag = "Wall"; o.order = -10
            o.get<SpriteRenderer>()!!.tileX = maxOf(1f, w / 2); o.get<SpriteRenderer>()!!.tileY = maxOf(1f, h / 2)
        }
        wall("WallTop", 0f, rows / 2f + 0.5f, cols + 2f, 1f); wall("WallBottom", 0f, -rows / 2f - 0.5f, cols + 2f, 1f)
        wall("WallLeft", -cols / 2f - 0.5f, 0f, 1f, rows.toFloat()); wall("WallRight", cols / 2f + 0.5f, 0f, 1f, rows.toFloat())

        // player tank: hull + turret child + shield bubble
        val player = GameKit.obj(s, "Player", ox + 8, oy - 13, 0.95f, 0.95f).sprite(0xFF8BC34A, 0, "TankHull.png").circle(false, 0.42f).body(type = 0, gravity = 0f, friction = 0f).script("ITPlayer.js")
        player.tag = "Player"; player.order = 10; player.rotation = 90f
        player.get<Rigidbody2D>()!!.drag = 0f
        GameKit.obj(s, "Turret", 0f, 0f, 1f, 1f, player).sprite(0xFFAED581, 0, "TankTurret.png").order = 11
        GameKit.obj(s, "ShieldFX", 0f, 0f, 1.7f, 1.7f, player).sprite(0x8840C4FF, 0, "Shield.png").order = 12

        // enemy templates
        fun enemy(name: String, kind: String, size: Float, hull: Long, turret: Long) {
            val e = GameKit.obj(s, name, 0f, 40f, size, size).sprite(hull, 0, "TankHull.png").circle(false, 0.42f).body(type = 0, gravity = 0f, friction = 0f)
                .script("ITEnemy.js", "kind=$kind").off()
            e.tag = "Enemy"; e.order = 9; e.rotation = 270f
            GameKit.obj(s, "Turret", 0f, 0f, 1f, 1f, e).sprite(turret, 0, "TankTurret.png").order = 10
        }
        enemy("EnemyTank", "light", 0.95f, 0xFFE57373, 0xFFEF9A9A)
        enemy("FastTank", "fast", 0.85f, 0xFFFFD54F, 0xFFFFE082)
        enemy("HeavyTank", "heavy", 1.15f, 0xFF9E9E9E, 0xFFBDBDBD)

        // shells
        fun shell(name: String, color: Long, w: Float, h: Float) {
            val b = GameKit.obj(s, name, 0f, 0f, w, h).sprite(color, 0, "Bullet.png").box(true).body(type = 1).script("ITShell.js").off()
            b.tag = "Shell"; b.order = 13
        }
        shell("Shell", 0xFFFFF59D, 0.38f, 0.16f)
        shell("HEShell", 0xFFFF7043, 0.5f, 0.24f)
        shell("EnemyShell", 0xFFFF8A80, 0.34f, 0.15f)

        // power-ups
        for ((name, kind, tex, tint) in listOf(
            listOf("Repair", "repair", "Medkit.png", 0xFFFFFFFF), listOf("StarUp", "star", "Star.png", 0xFFFFD54F),
            listOf("ShieldUp", "shield", "Shield.png", 0xFF80D8FF), listOf("LifeUp", "life", "Heart.png", 0xFFFF5252))) {
            val pu = GameKit.obj(s, name as String, 0f, 0f, 0.8f, 0.8f).sprite(tint as Long, 0, tex as String).circle(true, 0.45f).body(type = 1).script("ITPickup.js", "kind=$kind").off()
            pu.tag = "Pickup"; pu.order = 6
        }

        // effects
        GameKit.obj(s, "Flash", 0f, 0f, 0.8f, 0.8f).sprite(0xFFFFFFFF, 1, "MuzzleFlash.png").off().order = 14
        GameKit.obj(s, "Scorch", 0f, 0f, 1.6f, 1.6f).sprite(0xDDFFFFFF, 0, "Scorch.png").off().order = -30
        fun fx(name: String, preset: String, tweak: ParticleEmitter.() -> Unit = {}) {
            GameKit.obj(s, name).particles { applyPreset(ParticleEmitter.PRESETS.indexOf(preset)); emitting = false; rate = 0f; texture = "SoftDot.png"; tweak() }.off().order = 20
        }
        fx("Boom", "Sparks") { lifetime = 0.35f; speed = 3f }
        fx("BigBoom", "Explosion") { startSize = 0.9f; speed = 4.5f }
        fx("Debris", "Dust") { startColor = 0xFFB0694A.toInt(); endColor = 0x00B0694A; speed = 2.2f; gravity = 0f; lifetime = 0.5f; spread = 360f }
        fx("TrackDust", "Dust") { startSize = 0.2f; endSize = 0.45f; speed = 0.4f; lifetime = 0.6f }
        fx("SpawnFX", "Magic") { startColor = 0xFFFFFFFF.toInt(); endColor = 0x00FFFFFF; speed = 2.5f; lifetime = 0.6f }

        // HUD
        val hud = GameKit.panel(s, "HudPanel", 3.2f, -0.8f, 6f, 1.3f, 0xB0101010, anchor = GameKit.TL, border = 0x33FFFFFF)
        GameKit.bar(s, "HealthBar", 0f, 0.25f, 5.4f, 0.32f, 0xFF8BC34A, hud)
        GameKit.text(s, "HpText", "100 / 100", 0f, 0.25f, 0.22f, parent = hud)
        GameKit.text(s, "LivesText", "LIVES 3", -1.4f, -0.3f, 0.3f, 0xFFFF8A80, hud)
        GameKit.text(s, "HQText", "HQ 3/3", 1.4f, -0.3f, 0.3f, 0xFFFFD54F, hud)
        GameKit.text(s, "LevelText", "MISSION 1", 0f, -0.45f, 0.42f, 0xFFFFFFFF, anchor = GameKit.TOP)
        GameKit.text(s, "EnemiesText", "", 0f, -1f, 0.26f, 0xFFBDBDBD, anchor = GameKit.TOP)
        GameKit.text(s, "ScoreText", "SCORE 0", -1.8f, -1.3f, 0.3f, anchor = GameKit.TR, align = 2)
        GameKit.text(s, "KillsText", "KILLS 0", -1.8f, -1.8f, 0.26f, 0xFFBDBDBD, anchor = GameKit.TR, align = 2)
        GameKit.text(s, "HELabel", "HE SHELL", 2.2f, 1.25f, 0.24f, 0xFFFF7043, anchor = GameKit.BOTTOM)
        GameKit.bar(s, "HEBar", 2.2f, 0.9f, 2.2f, 0.18f, 0xFFFF7043, anchor = GameKit.BOTTOM, value = 1f)
        GameKit.text(s, "BannerText", "", 0f, 1.6f, 0.8f, 0xFFFFFFFF).off()
        GameKit.text(s, "ToastText", "", 0f, 0.8f, 0.4f, 0xFF8BC34A).off()
        GameKit.pauseMenu(s, "Menu", 0xFF424242)

        val win = GameKit.panel(s, "WinPanel", 0f, 0f, 7.4f, 5.8f, 0xF0101010, border = 0x88FFD54F).off()
        GameKit.text(s, "WinTitle", "MISSION COMPLETE", 0f, 2.2f, 0.62f, 0xFFFFD54F, win)
        GameKit.text(s, "WinStars", "", 0f, 1.35f, 0.7f, 0xFFFFD54F, win)
        GameKit.text(s, "WinStats", "", 0f, 0.2f, 0.3f, parent = win)
        GameKit.button(s, "NextBtn", "Next Mission", 0f, -1.2f, 4.4f, 0.9f, 0xFFF5F5F5, "call:nextLevel", win)
            .get<com.sengine.engine.core.UIButton>()!!.textColor = 0xFF111111.toInt()
        GameKit.button(s, "WinMenuBtn", "Main Menu", 0f, -2.25f, 4.4f, 0.85f, 0xFF3A3A3A, "scene:Menu", win)

        val over = GameKit.panel(s, "GameOverPanel", 0f, 0f, 7.4f, 5.4f, 0xF0101010, border = 0x88EF5350).off()
        GameKit.text(s, "LoseTitle", "MISSION FAILED", 0f, 1.9f, 0.7f, 0xFFEF5350, over)
        GameKit.text(s, "LoseReason", "", 0f, 1.05f, 0.34f, 0xFFFFFFFF, over)
        GameKit.text(s, "LoseStats", "", 0f, 0.35f, 0.3f, 0xFFBDBDBD, over)
        GameKit.button(s, "RetryBtn", "Retry", 0f, -0.8f, 4.4f, 0.9f, 0xFFF5F5F5, "reload", over)
            .get<com.sengine.engine.core.UIButton>()!!.textColor = 0xFF111111.toInt()
        GameKit.button(s, "LoseMenuBtn", "Main Menu", 0f, -1.9f, 4.4f, 0.85f, 0xFF3A3A3A, "scene:Menu", over)
        return s
    }
}
