package com.sengine.project.games

import com.sengine.engine.core.Camera3D
import com.sengine.engine.core.Collider3D
import com.sengine.engine.core.Light
import com.sengine.engine.core.MeshRenderer
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.VoxelWorld
import com.sengine.project.Project
import com.sengine.project.games.GameKit.mesh
import com.sengine.project.games.GameKit.off
import com.sengine.project.games.GameKit.particles
import com.sengine.project.games.GameKit.script

/**
 * "MiniCraft" — complete 3D voxel sandbox: title screen over a live world, new/continue with
 * saved worlds, 3 world sizes, first-person controls with look pad, walking/jumping/auto-step,
 * swimming, fly mode, block breaking/placing with a 12-slot hotbar and selection highlight,
 * day/night cycle, wandering animals, autosave and pause menu.
 */
internal object CraftGame {
    fun build(p: Project) {
        GameKit.install(p, "Block Break", "Block Place", "Footstep", "UI Click (default)", "Block World (song)", "Soft Particle")
        for (s in listOf("CraftPlayer.js", "CraftAnimal.js", "CraftMenu.js")) p.writeAsset(s, Games.res("craft/$s"))
        p.saveScene(menu())
        p.saveScene(world())
        p.saveControls(com.sengine.engine.controls.ControlLayout.presets.first { it.name.startsWith("First Person") }.copy())
        p.startScene = "CraftMenu"
        p.orientation = 0
    }

    private fun camera(s: Scene) = GameKit.obj3(s, "Main Camera", 16f, 34f, 40f).also {
        it.add(Camera3D().also { c -> c.follow = ""; c.fov = 70f; c.far = 220f; c.skyTop = 0xFF3B7BD4.toInt(); c.skyHorizon = 0xFFBFD8F0.toInt(); c.shadowDistance = 45f; c.quality = 2 })
    }

    private fun menu(): Scene {
        val s = Scene("CraftMenu")
        s.fog = true; s.fogStart = 30f; s.fogEnd = 90f
        camera(s)
        GameKit.obj3(s, "Sun", 0f, 50f, 0f).also { it.rotX = -55f; it.rotY = 30f }.add(Light().also { it.kind = 0; it.intensity = 1.05f })
        GameKit.obj3(s, "World", 0f, 0f, 0f).add(VoxelWorld().also { it.chunksX = 2; it.chunksZ = 2; it.height = 40; it.seed = 4242; it.waterLevel = 13; it.terrainHeight = 20f })
        GameKit.obj(s, "MenuManager").script("CraftMenu.js")
        GameKit.text(s, "Title", "MINICRAFT", 0f, -1f, 1.3f, 0xFFFFFFFF, anchor = GameKit.TOP)
        GameKit.text(s, "Subtitle", "Build anything. Explore forever.", 0f, -1.9f, 0.36f, 0xFFFDE68A, anchor = GameKit.TOP)
        val main = GameKit.panel(s, "MainPanel", 0f, -1.3f, 5.6f, 5.8f, 0xCC1C1917, border = 0x66A3E635)
        GameKit.button(s, "NewBtn", "New World", 0f, 2f, 4.6f, 0.95f, 0xFF65A30D, "call:newWorld", main, textSize = 0.42f)
        GameKit.button(s, "ContinueBtn", "Continue World", 0f, 0.9f, 4.6f, 0.85f, 0xFF4D7C0F, "call:continueWorld", main)
        GameKit.button(s, "SizeBtn", "World size: Medium", 0f, -0.15f, 4.6f, 0.8f, 0xFF44403C, "call:cycleSize", main)
        GameKit.button(s, "MusicBtn", "Music: ON", 0f, -1.1f, 4.6f, 0.8f, 0xFF44403C, "call:toggleMusic", main)
        GameKit.button(s, "HelpBtn", "How to play", -1.2f, -2.1f, 2.2f, 0.8f, 0xFF44403C, "show:HelpPanel;hide:MainPanel", main, textSize = 0.3f)
        GameKit.button(s, "QuitBtn", "Quit", 1.2f, -2.1f, 2.2f, 0.8f, 0xFF292524, "quit", main, textSize = 0.3f)
        GameKit.text(s, "SeedText", "", 0f, 0.45f, 0.28f, 0xFFD6D3D1, anchor = GameKit.BOTTOM)
        val help = GameKit.panel(s, "HelpPanel", 0f, -0.6f, 9.6f, 6.2f, 0xE61C1917, border = 0x66A3E635).off()
        GameKit.text(s, "HelpTitle", "HOW TO PLAY", 0f, 2.5f, 0.55f, parent = help)
        GameKit.text(s, "HelpBody", "Joystick: walk.  Drag the right side of the screen: look around.\nA: jump / swim up.  You climb single blocks automatically.\nBREAK: mine the block you look at.  PLACE: build with the\nselected hotbar block (tap a slot at the top to choose).\nFLY toggles flying.  Your world autosaves every minute.", 0f, 0.3f, 0.29f, 0xFFE7E5E4, help)
        GameKit.button(s, "HelpBackBtn", "OK", 0f, -2.5f, 3f, 0.8f, 0xFF65A30D, "hide:HelpPanel;show:MainPanel", help)
        return s
    }

    private fun world(): Scene {
        val s = Scene("World")
        s.fog = true; s.fogStart = 40f; s.fogEnd = 95f; s.ambient = 0xFF55606E.toInt()
        camera(s)
        GameKit.obj3(s, "Sun", 0f, 60f, 0f).also { it.rotX = -60f; it.rotY = 35f }.add(Light().also { it.kind = 0; it.intensity = 1.05f })
        GameKit.obj3(s, "World", 0f, 0f, 0f).add(VoxelWorld().also { it.chunksX = 6; it.chunksZ = 6; it.height = 48; it.seed = 1337; it.waterLevel = 14; it.terrainHeight = 22f })
        val player = GameKit.obj3(s, "Player", 48.5f, 45f, 48.5f).script("CraftPlayer.js", "pitch=-15")
        player.tag = "Player"
        player.add(Collider3D().also { it.sizeX = 0.6f; it.sizeY = 1.8f; it.sizeZ = 0.6f })
        player.add(Rigidbody3D().also { it.friction = 0f; it.drag = 0f })
        GameKit.obj3(s, "Selection", 0f, -50f, 0f, 1.03f, 1.03f, 1.03f).mesh(0, 0x40FFFFFF).also { it.get<MeshRenderer>()!!.unlit = true; it.get<MeshRenderer>()!!.castShadows = false }
        GameKit.obj3(s, "BreakFx", 0f, -50f, 0f).particles {
            emitting = false; rate = 0f; spread = 360f; speed = 2.5f; lifetime = 0.6f; startSize = 0.16f; endSize = 0.04f; gravity = -9f
            startColor = 0xFF8D6E63.toInt(); endColor = 0x805D4037.toInt()
        }.off()
        // animals
        for ((i, pos) in listOf(40f to 40f, 58f to 44f, 44f to 60f, 62f to 62f, 30f to 55f).withIndex()) {
            val body = GameKit.obj3(s, "Animal$i", pos.first, 30f, pos.second, 0.7f, 0.6f, 1.1f).mesh(0, if (i % 2 == 0) 0xFFF5F5F5 else 0xFFF8BBD0).script("CraftAnimal.js")
            GameKit.obj3(s, "Head", 0f, 0.45f, 0.6f, 0.75f, 0.75f, 0.45f, body).mesh(0, if (i % 2 == 0) 0xFFE0E0E0 else 0xFFF48FB1)
            for (l in 0 until 4) GameKit.obj3(s, "Leg$l", if (l % 2 == 0) -0.3f else 0.3f, -0.6f, if (l < 2) 0.35f else -0.35f, 0.25f, 0.6f, 0.2f, body).mesh(0, 0xFF6D4C41)
        }
        // HUD
        GameKit.text(s, "Crosshair", "+", 0f, 0f, 0.6f, 0xDDFFFFFF)
        for (i in 0 until 12) GameKit.button(s, "Slot$i", "Block", -6.05f + i * 1.1f, -0.55f, 1.05f, 0.75f, 0xCC1F2937, "call:selectSlot", anchor = GameKit.TOP, textSize = 0.18f)
        GameKit.text(s, "BlockText", "", 0f, -1.25f, 0.3f, 0xFFFDE68A, anchor = GameKit.TOP)
        GameKit.text(s, "ClockText", "", 0.3f, -1.3f, 0.24f, 0xFFE7E5E4, anchor = GameKit.TL, align = 0)
        GameKit.button(s, "FlyBtn", "FLY", 0.9f, -0.6f, 1.4f, 0.75f, 0x99101828, "call:toggleFly", anchor = GameKit.TL, textSize = 0.26f)
        GameKit.text(s, "ToastText", "", 0f, 1.6f, 0.38f, 0xFFFFFFFF).off()
        GameKit.button(s, "PauseBtn", "II", -0.7f, -0.6f, 0.9f, 0.75f, 0x99101828, "pause;show:PausePanel", anchor = GameKit.TR, textSize = 0.36f)
        val pp = GameKit.panel(s, "PausePanel", 0f, 0f, 6f, 5.4f, 0xE61C1917, border = 0x66A3E635).off()
        GameKit.text(s, "PauseTitle", "GAME MENU", 0f, 1.9f, 0.6f, parent = pp)
        GameKit.button(s, "ResumeBtn", "Back to Game", 0f, 0.8f, 4.4f, 0.85f, 0xFF65A30D, "resume;hide:PausePanel", pp)
        GameKit.button(s, "SaveBtn", "Save World", 0f, -0.25f, 4.4f, 0.85f, 0xFF44403C, "call:saveGame", pp)
        GameKit.button(s, "QuitWorldBtn", "Save & Quit to Title", 0f, -1.3f, 4.4f, 0.85f, 0xFF44403C, "resume;call:saveAndQuit", pp)
        return s
    }
}
