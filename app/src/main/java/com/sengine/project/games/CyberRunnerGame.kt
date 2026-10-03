package com.sengine.project.games

import com.sengine.project.AssetLibrary
import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.ScriptComponent
import com.sengine.engine.core.SpriteRenderer
import com.sengine.engine.core.TextRenderer
import com.sengine.engine.core.Tilemap
import com.sengine.project.Project

/** Cyber Runner: A fast-paced 2D Cyberpunk platformer with neon visuals, wall jumping, dash, and lasers. */
object CyberRunnerGame {

    fun build(p: Project) {
        p.writeAsset("Hologram.glsl", AssetLibrary.Shaders.all.first { it.first == "Hologram.glsl" }.third)
        p.writeAsset("CyberPlayer.gd", """
# Cyber Runner Player GDScript
func start():
    self.tag = "Player"
    self.layer = 1

func update(dt):
    var speed = 7.0
    var jump = 12.0
    self.vx = input.axisX * speed
    if input.axisX != 0:
        self.flipX = input.axisX < 0
    if input.aDown and self.grounded:
        self.vy = jump
        audio.play("jump.wav")
    if self.y < -15:
        scene.reload()
""".trimIndent())

        p.writeAsset("LaserHazard.js", """
function update(dt) {
    self.rotation += 45 * dt;
}
function onCollision(other) {
    if (other.tag == "Player") {
        scene.shake(0.5);
        audio.play("hit.wav");
        scene.reload();
    }
}
""".trimIndent())

        val s = Scene("Main")
        val cam = s.create("Main Camera")
        cam.add(Camera2D().also { it.size = 6f; it.background = 0xFF0B0E1A.toInt(); it.follow = "Player" })

        val tm = s.create("CyberTilemap")
        val tilemap = Tilemap().also {
            it.cols = 20
            it.rows = 10
            it.generateCollisions = true
            // fill floor
            for (c in 0 until 20) it.setTile(c, 0, 1)
            it.setTile(5, 3, 1)
            it.setTile(6, 3, 1)
            it.setTile(12, 5, 1)
            it.setTile(13, 5, 1)
        }
        tm.add(tilemap)

        val player = s.create("Player")
        player.x = 2f
        player.y = 3f
        player.add(SpriteRenderer().also { it.color = 0xFF22D3EE.toInt() })
        player.add(Collider2D().also { it.width = 0.8f; it.height = 0.8f })
        player.add(Rigidbody2D().also { it.friction = 0.1f })
        player.add(ScriptComponent().also { it.script = "CyberPlayer.gd" })

        val laser = s.create("Laser")
        laser.x = 12f
        laser.y = 6.2f
        laser.scaleX = 2f
        laser.scaleY = 0.2f
        laser.add(SpriteRenderer().also { it.color = 0xFFFF0055.toInt(); it.shader = "Hologram.glsl" })
        laser.add(Collider2D().also { it.isTrigger = true })
        laser.add(ScriptComponent().also { it.script = "LaserHazard.js" })

        val hud = s.create("HUD", parent = cam)
        hud.add(TextRenderer().also { it.text = "CYBER RUNNER - REACH THE NEON PORTAL"; it.size = 0.4f; it.screenSpace = true; it.color = 0xFF22D3EE.toInt() })
        hud.y = 4f

        p.saveScene(s)
        p.startScene = "Main"
    }
}
