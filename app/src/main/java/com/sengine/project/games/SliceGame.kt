package com.sengine.project.games

import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SpriteRenderer
import com.sengine.project.Project
import com.sengine.project.games.GameKit.circle
import com.sengine.project.games.GameKit.install
import com.sengine.project.games.GameKit.obj
import com.sengine.project.games.GameKit.script

/**
 * "Slice Master" — complete fruit-slicing arcade:
 *  - Swipe anywhere to slice; fruit launched with real gravity, bombs end runs.
 *  - Combos for multi-slices, lives lost for missed fruit, best score saved.
 *  - Classic mode with escalating waves + 60-second Frenzy.
 *  - Menu with best score, HUD, pause, game over — all juice: splashes, sparks, shake.
 */
internal object SliceGame {
    private val fruitColors = listOf(0xFFFF6B35, 0xFFFFD23F, 0xFF3FB950, 0xFFE23B5B, 0xFF8B5CF6, 0xFF22D3EE).map { it.toInt() }

    fun build(p: Project) {
        GameKit.install(p, "Soft Particle", "Spark", "Sky Gradient", "Night Sky", "Cloud", "Laser", "Explosion", "Hit", "Power Up",
            "Coin Pickup", "Win Jingle", "Game Over", "UI Click", "Chill Lo-Fi (song)", "Menu Theme (song)", "Collectible (Blueprint)")
        p.writeAsset("SliceMenu.js", Games.res("slice/SliceMenu.js"))
        p.writeAsset("SliceDirector.js", Games.res("slice/SliceDirector.js"))
        p.writeAsset("SliceFruit.js", Games.res("slice/SliceFruit.js"))
        p.writeAsset("SliceBomb.js", Games.res("slice/SliceBomb.js"))
        p.saveScene(menu())
        p.saveScene(arcade())
        p.saveControls(com.sengine.engine.controls.ControlLayout.presets.first { it.name == "Touch Only" }.copy())
        p.startScene = "SliceMenu"
        p.orientation = 1 // portrait, like the classic
    }

    private fun menu(): Scene {
        val s = Scene("SliceMenu")
        obj(s, "Main Camera").add(Camera2D().also { it.size = 6f; it.background = 0xFF12172B.toInt() })
        val sky = obj(s, "Sky", 0f, 0f, 20f, 12f).also { it.order = -100 }
        sky.add(SpriteRenderer().also { it.texture = "NightSky.png" })
        GameKit.text(s, "Title", "SLICE MASTER", 0f, -0.6f, 0.95f, 0xFFFFD23F, anchor = GameKit.TOP)
        GameKit.text(s, "BestText", "BEST 0", 0f, 0.2f, 0.4f, 0xFF9FB6D8, anchor = GameKit.TOP)
        GameKit.button(s, "ClassicBtn", "CLASSIC", 0f, 0.6f, 4.6f, 1.05f, 0xFF22C55E, "scene:Arcade", anchor = GameKit.CENTER, textSize = 0.46f)
        GameKit.button(s, "FrenzyBtn", "FRENZY 60s", 0f, -0.7f, 4.6f, 1.05f, 0xFF8B5CF6, "call:startFrenzy", anchor = GameKit.CENTER, textSize = 0.46f)
        GameKit.button(s, "HowBtn", "How to Play", 0f, -1.9f, 4.6f, 0.85f, 0xFF4C6FFF, "show:HelpPanel", anchor = GameKit.CENTER)
        val help = GameKit.panel(s, "HelpPanel", 0f, -0.3f, 8.6f, 7f).off()
        GameKit.text(s, "HelpTitle", "HOW TO PLAY", 0f, 2.9f, 0.5f, parent = help)
        GameKit.text(s, "HelpBody", "Swipe across fruit to slice it — fast chains build combos.\nMiss a fruit and you lose a heart (3 in classic).\nSlice a BOMB and the run ends instantly!\nFrenzy mode: everything counts for 60 seconds,\nno lives — just chase the biggest score.", 0f, 0.6f, 0.3f, 0xFFE2E8F0, parent = help)
        GameKit.button(s, "HelpOkBtn", "OK", 0f, -2.6f, 3f, 0.85f, 0xFF22C55E, "hide:HelpPanel", parent = help)
        obj(s, "MenuManager").script("SliceMenu.js")
        return s
    }

    private fun arcade(): Scene {
        val s = Scene("Arcade")
        obj(s, "Main Camera").add(Camera2D().also { it.size = 6f; it.background = 0xFF12172B.toInt() })
        val sky = obj(s, "Sky", 0f, 0f, 20f, 12f).also { it.order = -100 }
        sky.add(SpriteRenderer().also { it.texture = "NightSky.png" })
        for (i in 0 until 4) {
            val c = obj(s, "Cloud", -7f + i * 4.5f, 2f + (i % 2) * 2f, 3f, 1.5f).also { it.order = -80 }
            c.add(SpriteRenderer().also { it.texture = "Cloud.png"; it.color = 0x55FFFFFF.toInt() })
            c.script("SliceFruit.js", "cloud=true")
        }
        // fruit + bomb templates (inactive; the director clones them)
        for (i in fruitColors.indices) fruitTemplate(s, "Fruit$i", fruitColors[i])
        bombTemplate(s)
        val bladeFx = obj(s, "SliceFX", 0f, 0f).off().also { it.order = 30 }
        bladeFx.add(ParticleEmitter().also {
            it.emitting = false; it.rate = 0f; it.spread = 360f; it.speed = 5f; it.lifetime = 0.5f; it.gravity = -5f
            it.startColor = 0xFFFFFFFF.toInt(); it.endColor = 0x00FFFFFF; it.texture = "SoftDot.png"; it.additive = true; it.maxParticles = 500
        })
        val sparks = obj(s, "Sparks", 0f, 0f).off().also { it.order = 30 }
        sparks.add(ParticleEmitter().also {
            it.emitting = false; it.rate = 0f; it.spread = 360f; it.speed = 7f; it.lifetime = 0.4f; it.gravity = -9f
            it.startColor = 0xFFFFF176.toInt(); it.endColor = 0x00FF8F00; it.texture = "Spark.png"; it.additive = true
        })
        obj(s, "Director").script("SliceDirector.js")
        hud(s)
        return s
    }

    private fun fruitTemplate(s: Scene, name: String, color: Int): GameObject {
        val f = obj(s, name, 0f, 8f, 0.8f, 0.8f).off().also { it.tag = "Fruit"; it.order = 10 }
        f.sprite(color.toLong(), 1)
        f.circle(false, 0.42f)
        f.script("SliceFruit.js")
        return f
    }

    private fun bombTemplate(s: Scene): GameObject {
        val b = obj(s, "Bomb", 0f, 8f, 0.85f, 0.85f).off().also { it.tag = "Bomb"; it.order = 10 }
        b.sprite(0xFF23262A, 1)
        b.circle(false, 0.44f)
        val fuse = obj(s, "Fuse", 0f, 0.55f, 0.2f, 0.3f, parent = b)
        fuse.add(SpriteRenderer().also { it.color = 0xFFFF8A3C.toInt(); it.shape = 2 })
        val glow = obj(s, "BombGlow", 0f, 0f, 1.3f, 1.3f, parent = b).also { it.order = -1 }
        glow.add(SpriteRenderer().also { it.color = 0x44FF5C6C.toInt(); it.shape = 1 })
        b.script("SliceBomb.js")
        return b
    }

    private fun hud(s: Scene) {
        GameKit.text(s, "ScoreText", "0", 0f, -0.5f, 0.85f, 0xFFFFFFFF, anchor = GameKit.TOP)
        GameKit.text(s, "ComboText", "", 0f, 0.5f, 0.42f, 0xFFFFD23F, anchor = GameKit.TOP)
        GameKit.text(s, "LivesText", "♥♥♥", 3.3f, -0.4f, 0.5f, 0xFFFF5C6C, anchor = GameKit.TR, align = 2)
        GameKit.text(s, "ModeText", "", -3.3f, -0.4f, 0.34f, 0xFFB9C4D8, anchor = GameKit.TL)
        GameKit.button(s, "PauseBtn", "II", 4.2f, 4.2f, 0.8f, 0.8f, 0x99101828, "pause;show:PausePanel", anchor = GameKit.BR, textSize = 0.4f)
        val pp = GameKit.panel(s, "PausePanel", 0f, 0f, 6f, 5.2f).off()
        GameKit.text(s, "PauseTitle", "PAUSED", 0f, 1.7f, 0.7f, parent = pp)
        GameKit.button(s, "ResumeBtn", "Resume", 0f, 0.55f, 4f, 0.9f, 0xFF22C55E, "resume;hide:PausePanel", pp)
        GameKit.button(s, "RestartBtn", "Restart", 0f, -0.55f, 4f, 0.9f, 0xFF3A4566, "resume;reload", pp)
        GameKit.button(s, "MenuBtn", "Menu", 0f, -1.65f, 4f, 0.9f, 0xFF3A4566, "resume;scene:SliceMenu", pp)
        val over = GameKit.panel(s, "OverPanel", 0f, 0f, 7.5f, 6f).off()
        GameKit.text(s, "OverTitle", "RUN OVER", 0f, 2f, 0.7f, 0xFFFF5C6C, parent = over)
        GameKit.text(s, "OverScore", "SCORE 0", 0f, 1f, 0.55f, 0xFFFFD23F, parent = over)
        GameKit.text(s, "OverBest", "BEST 0", 0f, 0.2f, 0.38f, 0xFFB9C4D8, parent = over)
        GameKit.button(s, "AgainBtn", "Play Again", 0f, -0.8f, 4.4f, 0.95f, 0xFF22C55E, "resume;reload", over)
        GameKit.button(s, "OverMenuBtn", "Menu", 0f, -1.9f, 4.4f, 0.9f, 0xFF3A4566, "resume;scene:SliceMenu", over)
    }
}
