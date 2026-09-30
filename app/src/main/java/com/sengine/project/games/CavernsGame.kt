package com.sengine.project.games

import com.sengine.engine.core.Animator
import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SpriteRenderer
import com.sengine.engine.core.TextRenderer
import com.sengine.project.Project
import com.sengine.project.games.GameKit.box
import com.sengine.project.games.GameKit.circle
import com.sengine.project.games.GameKit.install
import com.sengine.project.games.GameKit.label
import com.sengine.project.games.GameKit.obj
import com.sengine.project.games.GameKit.script
import com.sengine.project.games.GameKit.sprite

/**
 * "Crystal Caverns" — complete 2D platformer adventure:
 *  - Menu with level select (stars), settings, help; animated hero with run cycle.
 *  - 3 handcrafted caverns with gems, spikes, moving platforms, checkpoints and a goal gate.
 *  - 3 enemy types: bat (sine flyer), slime (patrol), golem (heavy walker, 3 HP).
 *  - HUD (gems / hearts / timer), pause, game over and victory with star ratings and saves.
 */
internal object CavernsGame {
    val LEVELS = listOf("Cavern 1 — Crystal Cave", "Cavern 2 — Fungus Depths", "Cavern 3 — Golem Hollow")
    val FILES = listOf("Cave1", "Cave2", "Cave3")

    fun build(p: Project) {
        GameKit.install(p, "Hero", "Hero Run (4 frames)", "Coin Spin (6 frames)", "Slime Bounce (4 frames)", "Slime", "Gem", "Heart",
            "Grass Tile", "Brick Wall", "Dirt", "Stone", "Metal Plate", "Sky Gradient", "Night Sky", "Mountains", "Soft Particle", "Spark", "Cloud",
            "Coin Pickup", "Jump", "Hit", "Power Up", "Explosion", "Win Jingle", "Game Over", "UI Click", "Hit Flash",
            "PlayerPlatformer", "EnemyPatrol", "Collectible", "Chiptune Loop", "Chiptune Adventure (song)", "Timed Spawner (Blueprint)")
        p.writeAsset("CaveMenu.js", Games.res("caverns/CaveMenu.js"))
        p.writeAsset("CavePlayer.js", Games.res("caverns/CavePlayer.js"))
        p.writeAsset("CaveBat.js", Games.res("caverns/CaveBat.js"))
        p.writeAsset("CaveSlime.js", Games.res("caverns/CaveSlime.js"))
        p.writeAsset("CaveGolem.js", Games.res("caverns/CaveGolem.js"))
        p.writeAsset("CaveGem.js", Games.res("caverns/CaveGem.js"))
        p.writeAsset("CaveLevel.js", Games.res("caverns/CaveLevel.js"))
        p.writeAsset("MovingPlatform.js", Games.res("caverns/MovingPlatform.js"))

        p.saveScene(menu())
        for (i in FILES.indices) p.saveScene(level(i))
        p.saveControls(com.sengine.engine.controls.ControlLayout.presets.first { it.name == "Platformer (D-Pad)" }.copy())
        p.startScene = "CaveMenu"
        p.orientation = 0
    }

    private fun backdrop(s: Scene, dark: Boolean): GameObject {
        val cam = obj(s, "Main Camera").add(Camera2D().also { it.size = 6f; it.background = (if (dark) 0xFF0B1020 else 0xFF171E33).toInt(); it.follow = "Player" })
        cam.get<Camera2D>()!!.smoothing = 8f
        val sky = obj(s, "Sky", 0f, 0f, 22f, 13f, parent = cam).also { it.order = -100 }
        sky.add(SpriteRenderer().also { it.texture = if (dark) "NightSky.png" else "Sky.png" })
        val mounts = obj(s, "Mountains", 0f, -3.4f, 26f, 9f).also { it.order = -90 }
        mounts.add(SpriteRenderer().also { it.texture = "Mountains.png"; it.color = if (dark) 0xFF3A4470.toInt() else 0xFFB9C4E0.toInt() })
        return cam
    }

    private fun menu(): Scene {
        val s = Scene("CaveMenu")
        backdrop(s, false)
        GameKit.text(s, "Title", "CRYSTAL CAVERNS", 0f, -0.7f, 1.0f, 0xFF7CD8FF, anchor = GameKit.TOP)
        GameKit.text(s, "Subtitle", "a platform adventure", 0f, -0.1f, 0.32f, 0xFF9FB6D8, anchor = GameKit.TOP)
        GameKit.text(s, "BestText", "STARS  0 / 9", 0f, 4.1f, 0.34f, 0xFFFFD166, anchor = GameKit.BOTTOM)
        for (i in LEVELS.indices) {
            GameKit.button(s, "Level${i + 1}Btn", LEVELS[i].substringBefore(" —"), -3.4f + i * 3.4f, 0.6f, 3.1f, 1f,
                if (i == 0) 0xFF22C55E else 0xFF3A4566, "scene:${FILES[i]}", anchor = GameKit.CENTER, textSize = 0.42f)
        }
        GameKit.button(s, "HowBtn", "How to Play", 0f, -0.5f, 3.6f, 0.85f, 0xFF4C6FFF, "show:HelpPanel", anchor = GameKit.CENTER)
        GameKit.button(s, "MusicBtn", "Music: ON", 0f, 3.2f, 3.2f, 0.75f, 0xFF334155, "call:music", anchor = GameKit.BOTTOM)
        val help = GameKit.panel(s, "HelpPanel", 0f, -0.4f, 9.6f, 6.2f).off()
        GameKit.text(s, "HelpTitle", "HOW TO PLAY", 0f, 2.6f, 0.5f, parent = help)
        GameKit.text(s, "HelpBody", "D-Pad / arrows: run.   A / Space: jump (hold for a higher jump).\nCollect every gem, avoid spikes and reach the crystal gate.\nBats swoop in waves, slimes patrol and golems take 3 hits.\nCheckpoints (blue crystals) save your progress in the level.\nFinish fast and with all gems to earn 3 stars!", 0f, 0.3f, 0.28f, 0xFFE2E8F0, parent = help)
        GameKit.button(s, "HelpOkBtn", "OK", 0f, -2.4f, 3f, 0.85f, 0xFF22C55E, "hide:HelpPanel", parent = help)
        obj(s, "MenuManager").script("CaveMenu.js")
        return s
    }

    // ------------------------------------------------------------------ level factory
    private fun level(index: Int): Scene {
        val name = FILES[index]
        val s = Scene(name)
        val dark = index >= 1
        backdrop(s, dark)
        val groundY = -3f

        // platforms: (x, y, w) tiles of brick
        data class Pl(val x: Float, val y: Float, val w: Float)
        val layouts = listOf(
            // Cavern 1: gentle intro
            listOf(Pl(-8f, groundY, 8), Pl(0f, groundY, 8), Pl(8f, groundY, 8), Pl(3.5f, -1.2f, 3), Pl(7.5f, 0.2f, 3), Pl(-4f, -0.8f, 3)),
            // Cavern 2: gaps + moving platforms
            listOf(Pl(-9f, groundY, 7), Pl(-1f, groundY, 5), Pl(6f, groundY, 5), Pl(12f, groundY, 8), Pl(-4f, -1.4f, 2.5f), Pl(2f, -0.2f, 2.5f), Pl(9f, 0.8f, 2.5f)),
            // Cavern 3: vertical climb to the gate
            listOf(Pl(-8f, groundY, 6), Pl(0f, groundY, 6), Pl(8f, groundY, 10), Pl(3f, -1.5f, 2.5f), Pl(6f, -0.2f, 2.5f), Pl(2.5f, 1.1f, 2.5f), Pl(-3f, 2.2f, 2.5f), Pl(-8f, 3.2f, 3f)),
        )[index]

        for (pl in layouts) {
            for (k in 0 until pl.w.toInt()) {
                val t = obj(s, "Tile", pl.x + k - pl.w / 2 + 0.5f, pl.y, 1f, 1f).also { it.order = -1 }
                t.add(SpriteRenderer().also { it.texture = if (pl.y == groundY) (if (dark) "Dirt.png" else "Grass.png") else "Brick.png" })
            }
            obj(s, "Collider", pl.x, pl.y, pl.w, 1f).box().also { it.order = -2 }
        }
        // level walls
        val left = layouts.minOf { it.x - it.w / 2 } - 0.5f
        val right = layouts.maxOf { it.x + it.w / 2 } + 0.5f
        obj(s, "WallL", left, 1f, 1f, 12f).box()
        obj(s, "WallR", right, 1f, 1f, 12f).box()

        // gems
        val gemSpots = listOf(
            listOf(3.5f to -0.4f, 7.5f to 1.0f, -4f to 0f, -6f to -2.2f, 2f to -2.2f, 10f to -2.2f),
            listOf(-4f to -0.6f, 2f to 0.6f, 9f to 1.6f, -6f to -2.2f, 1f to -2.2f, 13f to -2.2f, 7f to -2.2f),
            listOf(3f to -0.7f, 6f to 0.6f, 2.5f to 1.9f, -3f to 3f, -8f to 4f, -6f to -2.2f, 12f to -2.2f),
        )[index]
        for ((x, y) in gemSpots) {
            val g = obj(s, "Gem", x, y, 0.6f, 0.6f).also { it.tag = "Gem"; it.order = 5 }
            g.add(Animator().also { it.clip = "CoinSpin.anim" })
            g.sprite(0xFF7CD8FF, 1, "Gem.png").circle(true).script("CaveGem.js")
        }

        // spikes
        val spikes = listOf(listOf(1f, 5.5f), listOf(-2.5f, 8f, 3f), listOf(-1f, 4.5f, 10.5f))[index]
        for (x in spikes) {
            val sp = obj(s, "Spikes", x, groundY + 0.62f, 0.9f, 0.35f).also { it.order = 6 }
            sp.sprite(0xFFC9D4E4, 2)
            sp.add(Collider2D().also { it.isTrigger = true; it.width = 0.8f; it.height = 0.3f; it.offsetY = 0.05f })
        }
        // moving platforms
        val movers = listOf(listOf(5.5f to 0.4f), listOf(2.5f to -0.9f, 8f to 1.8f), listOf(9.5f to 0.9f))[index]
        for ((x, y) in movers) {
            obj(s, "MovingPlatform", x, y, 2.4f, 0.4f).also { it.order = 2 }
                .add(SpriteRenderer().also { it.texture = "Metal.png" })
                .box().body(type = 1).script("MovingPlatform.js", "range=1.6, speed=1.2")
        }

        // enemies per level: bats, slimes, golems
        val bats = listOf(listOf(2f to 1.2f, 9f to 2f), listOf(-5f to 1.5f, 3f to 2.4f, 10f to 1f), listOf(-4f to 3.4f, 1f to 2.2f, 7f to 1.6f))[index]
        for ((x, y) in bats) enemy(s, "Bat", x, y, 0.8f, 0.8f, 0xFF8B5CF6)
        val slimes = listOf(listOf(-2f to -2.35f, 6f to -2.35f), listOf(-6f to -2.35f, 7f to -2.35f), listOf(2f to -2.35f, 10f to -2.35f))[index]
        for ((x, y) in slimes) enemy(s, "Slime", x, y, 0.9f, 0.9f, 0xFF4CAF50, "SlimeBounce.anim")
        if (index >= 1) {
            val golems = if (index == 1) listOf(11f to -2.3f) else listOf(-5f to -2.3f, 5f to -2.3f)
            for ((x, y) in golems) enemy(s, "Golem", x, y, 1.3f, 1.5f, 0xFF78909C)
        }

        // checkpoint + goal gate
        val checkpointX = layouts.first().x + 2f
        val cp = obj(s, "Checkpoint", checkpointX, groundY + 1.1f, 0.5f, 1.6f).also { it.order = 4 }
        cp.sprite(0xFF4C8DFF, 0).circle(true)
        cp.add(Collider2D().also { it.isTrigger = true; it.width = 0.8f; it.height = 2f })
        val goalX = right - 1.6f
        val goal = obj(s, "Goal", goalX, groundY + 1.6f, 1.2f, 2.6f).also { it.tag = "Goal"; it.order = 4 }
        goal.sprite(0xFFFFD54F, 0)
        goal.add(Collider2D().also { it.isTrigger = true; it.width = 1.4f; it.height = 3f })
        goal.script("CaveLevel.js")

        // player
        val player = obj(s, "Player", layouts.first().x - 2f, groundY + 1f, 1f, 1f).also { it.tag = "Player"; it.order = 10 }
        player.add(SpriteRenderer().also { it.texture = "Hero.png"; it.shader = "HitFlash.glsl"; it.shaderParam = 0f })
        player.add(Animator().also { it.clip = "HeroRun.anim"; it.playOnStart = false })
        player.add(Collider2D().also { it.width = 0.6f; it.height = 0.95f })
        player.body(friction = 0f).script("CavePlayer.js", "speed=6, jump=11.5")

        // dust particles under the player's feet
        val dust = obj(s, "Dust", 0f, 0f)
        dust.active = false; dust.order = 20
        dust.add(ParticleEmitter().also { it.emitting = false; it.rate = 0f; it.spread = 70f; it.speed = 2f; it.lifetime = 0.5f; it.startColor = 0xFFBCAAA4.toInt(); it.endColor = 0x00BCAAA4; it.direction = 90f })

        hud(s, gemSpots.size)
        obj(s, "LevelManager").script("CaveLevel.js", "level=${index + 1}, totalGems=${gemSpots.size}")
        return s
    }

    private fun enemy(s: Scene, kind: String, x: Float, y: Float, w: Float, h: Float, color: Long, anim: String = ""): GameObject {
        val e = obj(s, kind, x, y, w, h).also { it.tag = "Enemy"; it.order = 8 }
        e.sprite(color, if (kind == "Bat") 1 else 0, if (kind == "Slime") "Slime.png" else "")
        if (anim.isNotBlank()) e.add(Animator().also { it.clip = anim })
        e.box()
        if (kind == "Bat") { e.body(type = 1); e.script("CaveBat.js", "range=2.2, speed=2") } else {
            e.body(type = 1); e.script(if (kind == "Slime") "CaveSlime.js" else "CaveGolem.js", "distance=2.5, speed=${if (kind == "Slime") 1.5 else 1.0}")
        }
        return e
    }

    private fun hud(s: Scene, gems: Int) {
        GameKit.text(s, "GemText", "0 / $gems", 0f, 4.35f, 0.42f, 0xFFFFD166, anchor = GameKit.TOP)
        GameKit.text(s, "TimerText", "0.0", 4f, 4.35f, 0.36f, 0xFFB9C4D8, anchor = GameKit.TOP, align = 2)
        GameKit.bar(s, "Hearts", -3.2f, -4.35f, 2.2f, 0.42f, 0xFFE53935, anchor = GameKit.BOTTOM)
        GameKit.button(s, "PauseBtn", "II", 4.3f, -4.3f, 0.8f, 0.8f, 0x99101828, "pause;show:PausePanel", anchor = GameKit.BOTTOM, textSize = 0.4f)
        val pp = GameKit.panel(s, "PausePanel", 0f, 0f, 6f, 5.2f).off()
        GameKit.text(s, "PauseTitle", "PAUSED", 0f, 1.7f, 0.7f, parent = pp)
        GameKit.button(s, "ResumeBtn", "Resume", 0f, 0.55f, 4f, 0.9f, 0xFF22C55E, "resume;hide:PausePanel", pp)
        GameKit.button(s, "RetryBtn", "Restart", 0f, -0.55f, 4f, 0.9f, 0xFF3A4566, "resume;reload", pp)
        GameKit.button(s, "CaveMenuBtn", "Menu", 0f, -1.65f, 4f, 0.9f, 0xFF3A4566, "resume;scene:CaveMenu", pp)
        val over = GameKit.panel(s, "GameOverPanel", 0f, 0f, 7.5f, 5.6f).off()
        GameKit.text(s, "OverTitle", "CAVE LOST", 0f, 1.8f, 0.7f, 0xFFFF5C6C, parent = over)
        GameKit.text(s, "OverStats", "", 0f, 0.7f, 0.36f, 0xFFE2E8F0, parent = over)
        GameKit.button(s, "OverRetryBtn", "Try Again", 0f, -0.4f, 4.4f, 0.9f, 0xFF22C55E, "resume;reload", over)
        GameKit.button(s, "OverMenuBtn", "Menu", 0f, -1.5f, 4.4f, 0.9f, 0xFF3A4566, "resume;scene:CaveMenu", over)
        val win = GameKit.panel(s, "WinPanel", 0f, 0f, 7.5f, 6f).off()
        GameKit.text(s, "WinTitle", "CAVE CLEARED!", 0f, 2.1f, 0.7f, 0xFFFFD166, parent = win)
        GameKit.text(s, "StarsText", "☆☆☆", 0f, 1.2f, 0.8f, 0xFFFFD166, parent = win)
        GameKit.text(s, "WinStats", "", 0f, 0.3f, 0.34f, 0xFFE2E8F0, parent = win)
        GameKit.button(s, "WinNextBtn", "Next Cave", 0f, -0.7f, 4.4f, 0.9f, 0xFF22C55E, "call:nextLevel", win)
        GameKit.button(s, "WinMenuBtn", "Menu", 0f, -1.8f, 4.4f, 0.9f, 0xFF3A4566, "resume;scene:CaveMenu", win)
    }
}

private fun GameObject.body(type: Int = 0, friction: Float = 0.2f): GameObject {
    add(Rigidbody2D().also { it.bodyType = type; it.friction = friction }); return this
}
