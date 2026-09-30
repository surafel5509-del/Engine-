package com.sengine.project.games

import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SpriteRenderer
import com.sengine.engine.core.TextRenderer
import com.sengine.project.Project
import com.sengine.project.games.GameKit.circle
import com.sengine.project.games.GameKit.install
import com.sengine.project.games.GameKit.obj
import com.sengine.project.games.GameKit.script
import com.sengine.project.games.GameKit.sprite

/**
 * "Open World 2D" — the official open sample: a tiny cozy overworld meant to be picked apart
 * and remixed. Wander with the joystick, talk to Ada (A), chop trees for wood (A near a tree),
 * build a campfire, and watch the day/night cycle. Everything is deliberately simple and
 * heavily commented — the scripts are the tutorial.
 */
internal object OpenWorldGame {
    fun build(p: Project) {
        GameKit.install(p, "Grass Tile", "Dirt", "Sand", "Water", "Tree", "Cloud", "Hero", "Slime", "Wood Planks",
            "Soft Particle", "Spark", "Coin Pickup", "Power Up", "UI Click", "Jump", "Hit",
            "Hero Run (4 frames)", "Slime Bounce (4 frames)", "Fire (6 frames)", "Chiptune Adventure (song)", "Rotator")
        for (s in scripts) p.writeAsset(s, Games.res("openworld/$s"))
        p.saveScene(world())
        p.saveControls(com.sengine.engine.controls.ControlLayout.presets.first { it.name == "Classic (Joystick + A/B)" }.copy())
        p.startScene = "Overworld"
        p.orientation = 0
    }

    private val scripts = listOf("OwPlayer.js", "OwTree.js", "OwNpc.js", "OwWorld.js")

    private fun spr(s: Scene, name: String, x: Float, y: Float, w: Float, h: Float, tex: String, tiled: Boolean = false, order: Int = 0): GameObject =
        obj(s, name, x, y, w, h).also {
            it.order = order
            it.add(SpriteRenderer().also { r -> r.texture = tex; if (tiled) { r.tileX = w / 2f; r.tileY = h / 2f } })
        }

    private fun world(): Scene {
        val s = Scene("Overworld")

        // layered ground patches — replace with your own map!
        spr(s, "Grass", 0f, 0f, 44f, 44f, "Grass.png", tiled = true, order = -50)
        val pond = obj(s, "Pond", 8f, -6f, 7f, 4.5f).also { it.order = -40 }
        pond.add(SpriteRenderer().also { r -> r.texture = "Water.png"; r.tileX = 3.5f; r.tileY = 2.2f })
        pond.circle(false)
        spr(s, "Beach", 8f, -9.2f, 8f, 1.4f, "Sand.png", tiled = true, order = -41)
        val path = obj(s, "Path", -4f, 4f, 14f, 2.2f).also { it.order = -40 }
        path.add(SpriteRenderer().also { r -> r.texture = "Dirt.png"; r.tileX = 7f; r.tileY = 1.1f })
        for (i in 0 until 5) {
            val c = obj(s, "Cloud", -16f + i * 8f, 12f + (i % 3) * 3f, 4f, 2f).also { it.order = 60 }
            c.add(SpriteRenderer().also { r -> r.texture = "Cloud.png"; r.color = 0x66FFFFFF.toInt() })
            c.script("Rotator.js", "x=0, y=0, z=0")
            c.script("OwWorld.js", "cloud=true, drift=0.3")
        }

        // trees (choppable) — A to chop
        val treeSpots = listOf(
            floatArrayOf(-12f, 8f), floatArrayOf(-9f, -8f), floatArrayOf(-14f, 2f), floatArrayOf(14f, 10f),
            floatArrayOf(17f, 3f), floatArrayOf(-4f, 12f), floatArrayOf(-16f, -14f), floatArrayOf(12f, 14f))
        treeSpots.forEachIndexed { i, t ->
            val tree = obj(s, "Tree", t[0], t[1], 1.6f, 1.6f).also { it.order = 10; it.tag = "Tree" }
            tree.add(SpriteRenderer().also { r -> r.texture = "Tree2D.png" })
            tree.circle(false, 0.55f)
            tree.script("OwTree.js", "wood=3, index=$i")
        }

        // friendly slimes — pure decoration, they hop around
        for (i in 0 until 3) {
            val sl = obj(s, "Slime", -6f + i * 5f, -2f - i, 0.9f, 0.9f).also { it.order = 9; it.tag = "Critter" }
            sl.add(SpriteRenderer().also { r -> r.texture = "Slime.png"; r.color = 0xFF8BC34A.toInt() })
            sl.circle(false, 0.4f)
            sl.script("OwWorld.js", "slime=true, range=2, speed=1.2")
        }

        // campfire template (spawned when you build)
        val fire = obj(s, "Campfire", 0f, -20f, 1.4f, 1.4f).off().also { it.order = 11 }
        fire.add(SpriteRenderer().also { r -> r.texture = "Fire.png"; r.color = 0xFFFF9800.toInt() })
        fire.add(ParticleEmitter().also {
            it.emitting = true; it.texture = "SoftDot.png"; it.applyPreset(ParticleEmitter.PRESETS.indexOf("Torch"))
        })

        // Ada the builder — talk with A
        val npc = obj(s, "Ada", 4f, 6f, 1f, 1f).also { it.order = 10; it.tag = "Npc" }
        npc.add(SpriteRenderer().also { r -> r.texture = "Hero.png"; r.color = 0xFFCE93D8.toInt() })
        npc.circle(false, 0.45f)
        npc.script("OwNpc.js")
        val marker = obj(s, "TalkMark", 0f, 0.8f, 0.5f, 0.5f, parent = npc).also { it.order = 12 }
        marker.add(TextRenderer().also { r -> r.text = "!"; r.size = 0.5f; r.bold = true; r.color = 0xFFFFD54F.toInt() })

        // the player
        val hero = obj(s, "Player", -2f, 2f, 1.1f, 1.1f)
        hero.tag = "Player"
        hero.order = 12
        hero.add(SpriteRenderer().also { r -> r.texture = "Hero.png" })
        hero.circle(false, 0.42f)
        hero.script("OwPlayer.js", "speed=5")

        // gentle camera
        obj(s, "Main Camera").add(Camera2D().also {
            it.size = 5.5f; it.background = 0xFF87CEEB.toInt(); it.follow = "Player"; it.smoothing = 4f
        })

        // day/night wash + HUD
        obj(s, "Night", 0f, 0f, 44f, 44f).also { it.order = 55 }.add(SpriteRenderer().also { r -> r.color = 0x00101830.toInt() })
        GameKit.text(s, "WoodText", "Wood 0", -3.4f, -0.4f, 0.36f, 0xFFE2C088, anchor = GameKit.TL)
        GameKit.text(s, "TimeText", "Day", 3.4f, -0.4f, 0.36f, 0xFFE2E8F0, anchor = GameKit.TR)
        GameKit.text(s, "ToastText", "", 0f, 0.7f, 0.34f, 0xFFFFFFB0, anchor = GameKit.BOTTOM)
        GameKit.text(s, "HintText", "A: chop / talk / build", 0f, 0.2f, 0.26f, 0x99FFFFFF, anchor = GameKit.BOTTOM)

        obj(s, "OwDirector").script("OwWorld.js")
        return s
    }
}
