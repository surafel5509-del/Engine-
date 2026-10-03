package com.sengine.project.games

import com.sengine.engine.core.Camera3D
import com.sengine.engine.core.Collider3D
import com.sengine.engine.core.Light
import com.sengine.engine.core.MeshRenderer
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.ScriptComponent
import com.sengine.engine.core.TextRenderer
import com.sengine.project.Project

/** Voxel Survivor: 3D Voxel Survival game with craftable blocks and daytime survival. */
object VoxelSurvivorGame {

    fun build(p: Project) {
        p.writeAsset("VoxelCharacter.lua", """
-- Voxel Character Controller
local speed = 6.0

function update(dt)
    self.vx = input.axisX * speed
    self.vz = -input.axisY * speed
    if input.aDown and self.grounded then
        self.vy = 7.0
    end
end
""".trimIndent())

        val s = Scene("Main")
        s.gravityY = -9.8f

        val cam = s.create("Camera3D")
        cam.y = 3f; cam.z = 6f
        cam.add(Camera3D().also { it.follow = "VoxelPlayer"; it.offsetY = 3f; it.offsetZ = 6f })

        val sun = s.create("Sun")
        sun.rotX = -50f; sun.rotY = 40f
        sun.add(Light())

        // Voxel terrain ground made of cube blocks
        val ground = s.create("VoxelGround")
        ground.scaleX = 20f; ground.scaleY = 0.5f; ground.scaleZ = 20f
        ground.add(MeshRenderer().also { it.mesh = 0; it.color = 0xFF558B2F.toInt() })
        ground.add(Collider3D())

        val player = s.create("VoxelPlayer")
        player.y = 2f
        player.scaleX = 0.8f; player.scaleY = 1.6f; player.scaleZ = 0.8f
        player.add(MeshRenderer().also { it.mesh = 0; it.color = 0xFFFFD54F.toInt() })
        player.add(Collider3D())
        player.add(Rigidbody3D().also { it.friction = 0.2f })
        player.add(ScriptComponent().also { it.script = "VoxelCharacter.lua" })

        val hud = s.create("HUD")
        hud.add(TextRenderer().also { it.text = "VOXEL SURVIVOR 3D - SURVIVE & BUILD"; it.size = 0.35f; it.screenSpace = true; it.color = 0xFFFFFFFF.toInt() })
        hud.y = 4.2f

        p.saveScene(s)
        p.startScene = "Main"
    }
}
