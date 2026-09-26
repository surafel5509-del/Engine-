package com.sengine.project.games

import com.sengine.engine.core.Camera3D
import com.sengine.engine.core.Collider3D
import com.sengine.engine.core.Landscape
import com.sengine.engine.core.Light
import com.sengine.engine.core.MeshRenderer
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.Water
import com.sengine.project.Project
import com.sengine.project.games.GameKit.col3
import com.sengine.project.games.GameKit.mesh
import com.sengine.project.games.GameKit.off
import com.sengine.project.games.GameKit.particles
import com.sengine.project.games.GameKit.script

/**
 * "Native World" — an open-world sample whose gameplay is written entirely in native C++ scripts
 * (PlayerController / WorldBuilder / Crystal / GameManager .cpp) running on the libsengine VM, on top of the
 * C++ Landscape generator (fBm + ridges + island falloff + hydraulic erosion).
 */
internal object NativeWorldGame {
    const val SCENE = "NativeWorld"

    fun build(p: Project) {
        GameKit.install(p, "Coin Pickup", "Adventure (song)")
        for (s in listOf("PlayerController.cpp", "WorldBuilder.cpp", "Crystal.cpp", "GameManager.cpp")) p.writeAsset(s, Games.res("native/$s"))
        p.saveScene(world())
        p.saveControls(com.sengine.engine.controls.ControlLayout.presets.first { it.name.startsWith("Platformer") }.copy())
        p.startScene = SCENE
        p.orientation = 0
    }

    private fun world(): Scene {
        val s = Scene(SCENE)
        s.fog = true; s.fogStart = 45f; s.fogEnd = 130f; s.ambient = 0xFF5A6474.toInt()
        GameKit.obj3(s, "Main Camera", 0f, 20f, 30f).add(Camera3D().also { c ->
            c.follow = "Player"; c.offsetX = 0f; c.offsetY = 5.5f; c.offsetZ = 9f; c.smoothing = 5f
            c.fov = 62f; c.far = 320f; c.skyTop = 0xFF2F6FC4.toInt(); c.skyHorizon = 0xFFCFE3F2.toInt(); c.shadowDistance = 50f; c.quality = 2
        })
        GameKit.obj3(s, "Sun", 0f, 60f, 0f).also { it.rotX = -50f; it.rotY = 40f }.add(Light().also { it.kind = 0; it.intensity = 1.1f })

        GameKit.obj3(s, "Landscape", 0f, 0f, 0f).add(Landscape().also {
            it.resolution = 97; it.size = 120f; it.height = 16f; it.seed = 2026; it.octaves = 6; it.frequency = 2.6f
            it.ridge = 0.4f; it.falloff = 0.65f; it.erosion = 12000; it.waterLevel = 0.16f
        })
        GameKit.obj3(s, "Lake", 0f, 2.6f, 0f).add(Water().also { it.mode = 1; it.width = 260f; it.depth = 260f; it.height = 6f; it.detail = 40 })

        val player = GameKit.obj3(s, "Player", 0f, 12f, 8f, 0.8f, 1.6f, 0.8f).mesh(6, 0xFFF59E0B).script("PlayerController.cpp", "speed=7")
        player.tag = "Player"
        player.add(Collider3D())
        player.add(Rigidbody3D().also { it.friction = 0f; it.drag = 0f })
        GameKit.obj3(s, "Visor", 0f, 0.3f, 0.45f, 0.7f, 0.2f, 0.2f, player).mesh(0, 0xFF1E293B)

        // templates spawned by WorldBuilder.cpp
        val tree = GameKit.obj3(s, "Tree", 0f, -80f, 0f).off()
        GameKit.obj3(s, "Trunk", 0f, 1.2f, 0f, 0.35f, 2.4f, 0.35f, tree).mesh(3, 0xFF6D4C41).col3()
        GameKit.obj3(s, "Leaves", 0f, 3.4f, 0f, 2.2f, 2.8f, 2.2f, tree).mesh(4, 0xFF2E7D32)
        val crystal = GameKit.obj3(s, "Crystal", 0f, -80f, 0f, 0.6f, 1.1f, 0.6f).mesh(1, 0xFF22D3EE).col3(sphere = true, trigger = true).script("Crystal.cpp").off()
        crystal.tag = "Crystal"
        crystal.get<MeshRenderer>()?.let { it.unlit = true; it.castShadows = false }
        GameKit.obj3(s, "Glow", 0f, 0f, 0f, 1f, 1f, 1f, crystal).particles {
            rate = 6f; spread = 360f; speed = 0.6f; lifetime = 1.2f; startSize = 0.18f; endSize = 0.02f; gravity = 1.5f
            startColor = 0xFF67E8F9.toInt(); endColor = 0x0022D3EE
        }

        GameKit.obj(s, "WorldBuilder").script("WorldBuilder.cpp", "trees=45;crystals=10;seed=42")
        GameKit.obj(s, "GameManager").script("GameManager.cpp")
        GameKit.text(s, "HUD", "Crystals 0 / 0", 0f, -0.6f, 0.42f, 0xFFFFFFFF, anchor = GameKit.TOP)
        GameKit.text(s, "Hint", "Native C++ scripts · Joystick: move · A: jump · B: sprint", 0f, 0.5f, 0.26f, 0xFFE2E8F0, anchor = GameKit.BOTTOM)
        GameKit.pauseMenu(s, SCENE)
        return s
    }
}
