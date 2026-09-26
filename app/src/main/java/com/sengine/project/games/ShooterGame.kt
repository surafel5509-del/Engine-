package com.sengine.project.games

import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Scene
import com.sengine.project.Project
import com.sengine.project.games.GameKit.body
import com.sengine.project.games.GameKit.box
import com.sengine.project.games.GameKit.circle
import com.sengine.project.games.GameKit.off
import com.sengine.project.games.GameKit.particles
import com.sengine.project.games.GameKit.script
import com.sengine.project.games.GameKit.sprite

/**
 * "Sky Strike" — complete 2D top-down shoot 'em up: main menu with settings, level select with
 * stars and locks, 3 levels of waves + a boss each, power-ups, bombs, HUD, pause, win/lose screens.
 */
internal object ShooterGame {
    private val scripts = listOf("SkyPlayer.js", "SkyBullet.js", "SkyEnemyBullet.js", "SkyEnemy.js", "SkyPowerUp.js", "SkyBoss.js", "SkyGame.js", "SkyStar.js", "SkyMenu.js")

    fun build(p: Project) {
        GameKit.install(p, "Spaceship", "Boss Ship", "Enemy Drone", "Glow Bullet", "Laser Bolt", "Shield Bubble", "Soft Particle", "Spark",
            "Health Kit", "Star", "Heart Icon", "Laser", "Explosion", "Hit", "Power Up", "Boss Roar", "UI Click (default)",
            "Chiptune Adventure (song)", "Action Battle (song)", "Menu Theme (song)")
        for (s in scripts) p.writeAsset(s, Games.res("shooter/$s"))
        p.saveScene(menu())
        p.saveScene(levels())
        for (l in 1..3) p.saveScene(level(l))
        p.startScene = "Menu"
        p.orientation = 0
    }

    private fun starTemplate(s: Scene) {
        GameKit.obj(s, "Star", 0f, 0f, 0.08f, 0.08f).sprite(0xCCFFFFFF, 1).script("SkyStar.js").off().order = -50
    }

    private fun menu(): Scene {
        val s = Scene("Menu"); s.gravityY = 0f
        GameKit.camera2D(s, 8f, 0xFF070B1E)
        starTemplate(s)
        GameKit.obj(s, "MenuManager").script("SkyMenu.js")
        GameKit.obj(s, "HeroShip", -7f, -1f, 2.6f, 2.6f).sprite(0xFFFFFFFF, 2, "Spaceship.png").also { it.rotation = -20f }
        GameKit.obj(s, "BossDeco", 8f, 2f, 5f, 5f).sprite(0xFFFFFFFF, 1, "BossShip.png").also { it.rotation = 200f }
        GameKit.text(s, "Title", "SKY STRIKE", 0f, 3.3f, 1.3f, 0xFF7DD3FC)
        GameKit.text(s, "Subtitle", "A S Engine shoot 'em up", 0f, 2.3f, 0.35f, 0xFFB4C0E0)
        val main = GameKit.panel(s, "MainPanel", 0f, -1.2f, 5.2f, 5.2f, 0xCC101830)
        GameKit.button(s, "PlayBtn", "PLAY", 0f, 1.6f, 4.2f, 1f, 0xFF22C55E, "scene:Levels", main, textSize = 0.45f)
        GameKit.button(s, "SettingsBtn", "Settings", 0f, 0.4f, 4.2f, 0.85f, 0xFF4C6FFF, "show:SettingsPanel;hide:MainPanel", main)
        GameKit.button(s, "HelpBtn", "How to play", 0f, -0.7f, 4.2f, 0.85f, 0xFF4C6FFF, "show:HelpPanel;hide:MainPanel", main)
        GameKit.button(s, "QuitBtn", "Quit", 0f, -1.8f, 4.2f, 0.85f, 0xFF3A4566, "quit", main)
        GameKit.text(s, "BestText", "HIGH SCORE 0", 0f, 0.5f, 0.35f, 0xFFFFD166, anchor = GameKit.BOTTOM)

        val set = GameKit.panel(s, "SettingsPanel", 0f, -0.6f, 6.4f, 6.2f).off()
        GameKit.text(s, "SetTitle", "SETTINGS", 0f, 2.5f, 0.6f, parent = set)
        GameKit.button(s, "MusicBtn", "Music: ON", 0f, 1.4f, 5f, 0.85f, 0xFF4C6FFF, "call:toggleMusic", set)
        GameKit.button(s, "SfxBtn", "Sound FX: 100%", 0f, 0.4f, 5f, 0.85f, 0xFF4C6FFF, "call:cycleSfx", set)
        GameKit.button(s, "DiffBtn", "Difficulty: Normal", 0f, -0.6f, 5f, 0.85f, 0xFF4C6FFF, "call:cycleDifficulty", set)
        GameKit.button(s, "ResetBtn", "Reset progress", 0f, -1.6f, 5f, 0.85f, 0xFFB91C1C, "call:resetProgress", set)
        GameKit.button(s, "SetBackBtn", "Back", 0f, -2.6f, 3f, 0.8f, 0xFF3A4566, "hide:SettingsPanel;show:MainPanel", set)

        val help = GameKit.panel(s, "HelpPanel", 0f, -0.6f, 9f, 6.2f).off()
        GameKit.text(s, "HelpTitle", "HOW TO PLAY", 0f, 2.5f, 0.6f, parent = help)
        GameKit.text(s, "HelpBody", "Move with the joystick — your ship fires automatically.\nPress B to drop a BOMB that clears the screen.\nCollect power-ups: + health, triple shot, shield, bombs.\nSurvive every wave and destroy the boss.\nFinish with more health to earn 3 stars!", 0f, 0.3f, 0.3f, 0xFFD6DEFF, help)
        GameKit.button(s, "HelpBackBtn", "Got it", 0f, -2.5f, 3f, 0.8f, 0xFF22C55E, "hide:HelpPanel;show:MainPanel", help)
        return s
    }

    private fun levels(): Scene {
        val s = Scene("Levels"); s.gravityY = 0f
        GameKit.camera2D(s, 8f, 0xFF070B1E)
        starTemplate(s)
        GameKit.obj(s, "MenuManager").script("SkyMenu.js")
        GameKit.text(s, "Title", "SELECT LEVEL", 0f, 3.6f, 0.9f, 0xFF7DD3FC)
        val names = listOf("Nebula Outpost", "Asteroid Belt", "Mothership")
        val colors = listOf(0xFF2563EB, 0xFF7C3AED, 0xFFDC2626)
        for (l in 1..3) {
            val y = 2f - l * 1.5f
            GameKit.button(s, "Level${l}Btn", "LEVEL $l", -1f, y + 0.6f, 6f, 1.1f, colors[l - 1], "scene:Level$l", textSize = 0.42f)
            GameKit.text(s, "Level${l}Name", names[l - 1], 3.2f, y + 0.6f, 0.3f, 0xFFB4C0E0, align = 0)
        }
        GameKit.button(s, "BackBtn", "Back", 0f, -3.9f, 3f, 0.8f, 0xFF3A4566, "scene:Menu")
        GameKit.text(s, "BestText", "", 0f, 0.4f, 0.3f, 0xFFFFD166, anchor = GameKit.BOTTOM)
        return s
    }

    private fun level(l: Int): Scene {
        val s = Scene("Level$l"); s.gravityY = 0f
        val bg = listOf(0xFF0A1030, 0xFF1A0B2E, 0xFF2A0A12)[l - 1]
        GameKit.camera2D(s, 8f, bg)
        starTemplate(s)
        GameKit.obj(s, "Game").script("SkyGame.js", "level=$l")

        val player = GameKit.obj(s, "Player", 0f, -5f, 1.4f, 1.4f).sprite(0xFFFFFFFF, 2, "Spaceship.png").box(true, 0.6f, 0.7f).body(type = 1)
            .script("SkyPlayer.js")
        player.tag = "Player"; player.order = 10
        GameKit.obj(s, "Flame", 0f, -0.45f, parent = player).particles {
            direction = -90f; spread = 18f; speed = 3.5f; rate = 70f; lifetime = 0.3f; startSize = 0.22f; startColor = 0xFF7DD3FC.toInt(); endColor = 0x001E40AF; additive = true
        }
        GameKit.obj(s, "ShieldFx", 0f, 0f, 1.7f, 1.7f, player).sprite(0xAA22D3EE, 1, "Shield.png").off().order = 11

        // projectiles
        val bullet = GameKit.obj(s, "Bullet", 0f, 0f, 0.25f, 0.7f).sprite(0xFFFFF59D, 0, "Laser.png").box(true).body(type = 1).script("SkyBullet.js").off()
        bullet.tag = "Bullet"; bullet.get<Rigidbody2D>()!!.startVy = 20f; bullet.order = 8
        val eb = GameKit.obj(s, "EnemyBullet", 0f, 0f, 0.35f, 0.35f).sprite(0xFFFF6B6B, 1, "Bullet.png").circle(true).body(type = 1).script("SkyEnemyBullet.js").off()
        eb.tag = "EnemyBullet"; eb.order = 9

        // enemies
        fun enemy(name: String, kind: String, size: Float, color: Long, tex: String, shape: Int) {
            val e = GameKit.obj(s, name, 0f, 12f, size, size).sprite(color, shape, tex).box(true, 0.8f, 0.8f).body(type = 1).script("SkyEnemy.js", "kind=$kind").off()
            e.tag = "Enemy"; e.order = 5
            if (kind == "fighter") e.rotation = 180f
        }
        enemy("Drone", "drone", 1.3f, 0xFFFFFFFF, "Drone.png", 1)
        enemy("Fighter", "fighter", 1.2f, 0xFFFF7A7A, "Spaceship.png", 2)
        enemy("Heavy", "heavy", 2.4f, 0xFFFFB74D, "BossShip.png", 0)
        val boss = GameKit.obj(s, "Boss", 0f, 12f, 6f, 6f).sprite(listOf(0xFFFFFFFF, 0xFFE0B0FF, 0xFFFF9090)[l - 1], 0, "BossShip.png")
            .box(true, 0.8f, 0.6f).body(type = 1).script("SkyBoss.js", "level=$l").off()
        boss.tag = "Boss"; boss.order = 6

        // power-ups
        for ((name, kind, color) in listOf(Triple("PowerHeal", "heal", 0xFF4ADE80), Triple("PowerTriple", "triple", 0xFFFACC15), Triple("PowerShield", "shield", 0xFF22D3EE), Triple("PowerBomb", "bomb", 0xFFF472B6))) {
            val pu = GameKit.obj(s, name, 0f, 0f, 0.9f, 0.9f).sprite(color, 4, if (kind == "heal") "Medkit.png" else "Star.png").circle(true).body(type = 1).script("SkyPowerUp.js", "kind=$kind").off()
            pu.tag = "PowerUp"; pu.order = 7
        }

        // effects
        GameKit.obj(s, "Explosion").particles {
            emitting = false; rate = 0f; spread = 360f; speed = 5f; lifetime = 0.6f; startSize = 0.4f; startColor = 0xFFFFB347.toInt(); endColor = 0x00D50000; additive = true; texture = "SoftDot.png"
        }.off().order = 20
        GameKit.obj(s, "BigExplosion").particles {
            emitting = false; rate = 0f; spread = 360f; speed = 9f; lifetime = 1.1f; startSize = 0.8f; startColor = 0xFFFFF3B0.toInt(); endColor = 0x00FF3D00; additive = true; texture = "SoftDot.png"; maxParticles = 400
        }.off().order = 21
        GameKit.obj(s, "Spark").particles {
            emitting = false; rate = 0f; spread = 360f; speed = 3f; lifetime = 0.25f; startSize = 0.18f; startColor = 0xFFFFFFFF.toInt(); endColor = 0x00FFEB3B; additive = true
        }.off().order = 20

        // HUD
        val hud = GameKit.panel(s, "HudPanel", 3.3f, -0.75f, 6.2f, 1.2f, 0x99101828, anchor = GameKit.TL, border = 0x00000000)
        GameKit.text(s, "HpLabel", "HP", -2.6f, 0.2f, 0.3f, 0xFF4ADE80, hud)
        GameKit.bar(s, "HealthBar", 0.3f, 0.2f, 5f, 0.3f, 0xFF4ADE80, hud)
        GameKit.text(s, "BombText", "BOMBS x2", -1.5f, -0.3f, 0.26f, 0xFFF472B6, hud)
        GameKit.text(s, "LevelText", "LEVEL $l", 1.6f, -0.3f, 0.26f, 0xFFB4C0E0, hud)
        GameKit.text(s, "ScoreText", "0", 0f, -0.5f, 0.55f, 0xFFFFD166, anchor = GameKit.TOP)
        GameKit.text(s, "WaveText", "WAVE 1", 0f, -1.1f, 0.3f, 0xFFB4C0E0, anchor = GameKit.TOP)
        GameKit.bar(s, "BossBar", 0f, -1.6f, 8f, 0.35f, 0xFFEF4444, anchor = GameKit.TOP).off()
        GameKit.text(s, "BannerText", "", 0f, 1f, 0.8f, 0xFFFFFFFF).off()
        GameKit.text(s, "ToastText", "", 0f, -1.5f, 0.45f, 0xFF7DD3FC).off()
        GameKit.pauseMenu(s, "Menu", 0xFF22C55E)

        val win = GameKit.panel(s, "VictoryPanel", 0f, 0f, 7f, 5.8f).off()
        GameKit.text(s, "WinTitle", if (l == 3) "GALAXY SAVED!" else "LEVEL COMPLETE!", 0f, 2.1f, 0.65f, 0xFF4ADE80, win)
        GameKit.text(s, "WinStars", "★★★", 0f, 1.2f, 0.8f, 0xFFFFD166, win)
        GameKit.text(s, "WinScore", "", 0f, 0.3f, 0.35f, parent = win)
        if (l < 3) GameKit.button(s, "NextBtn", "Next Level", 0f, -0.9f, 4.4f, 0.9f, 0xFF22C55E, "scene:Level${l + 1}", win)
        else GameKit.button(s, "NextBtn", "Play Again", 0f, -0.9f, 4.4f, 0.9f, 0xFF22C55E, "scene:Levels", win)
        GameKit.button(s, "WinMenuBtn", "Main Menu", 0f, -2f, 4.4f, 0.9f, 0xFF3A4566, "scene:Menu", win)

        val lose = GameKit.panel(s, "GameOverPanel", 0f, 0f, 7f, 5f).off()
        GameKit.text(s, "LoseTitle", "GAME OVER", 0f, 1.7f, 0.75f, 0xFFEF4444, lose)
        GameKit.text(s, "LoseScore", "", 0f, 0.7f, 0.35f, parent = lose)
        GameKit.button(s, "RetryBtn", "Retry", 0f, -0.4f, 4.4f, 0.9f, 0xFF22C55E, "reload", lose)
        GameKit.button(s, "LoseMenuBtn", "Main Menu", 0f, -1.5f, 4.4f, 0.9f, 0xFF3A4566, "scene:Menu", lose)
        return s
    }
}
