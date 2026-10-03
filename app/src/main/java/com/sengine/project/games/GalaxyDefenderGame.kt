package com.sengine.project.games

import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.ScriptComponent
import com.sengine.engine.core.SpriteRenderer
import com.sengine.engine.core.TextRenderer
import com.sengine.project.Project

/** Galaxy Defender: 2D Space Bullet Hell shooter with complex bullet patterns. */
object GalaxyDefenderGame {

    fun build(p: Project) {
        p.writeAsset("DefenderShip.gd", """
# Galaxy Defender Ship
func start():
    self.tag = "Player"

func update(dt):
    var speed = 8.0
    self.vx = input.axisX * speed
    self.vy = input.axisY * speed
""".trimIndent())

        val s = Scene("Main")
        s.gravityY = 0f

        val cam = s.create("Main Camera")
        cam.add(Camera2D().also { it.size = 8f; it.background = 0xFF050510.toInt() })

        val ship = s.create("Ship")
        ship.y = -5f
        ship.add(SpriteRenderer().also { it.color = 0xFF00E676.toInt(); it.shape = 2 })
        ship.add(Collider2D().also { it.shape = 1; it.width = 0.6f; it.height = 0.6f })
        ship.add(Rigidbody2D().also { it.bodyType = 1 })
        ship.add(ScriptComponent().also { it.script = "DefenderShip.gd" })

        val boss = s.create("Boss")
        boss.y = 4f
        boss.scaleX = 2f; boss.scaleY = 1.5f
        boss.add(SpriteRenderer().also { it.color = 0xFFFF1744.toInt() })
        boss.add(Collider2D().also { it.isTrigger = true })

        val hud = s.create("HUD", parent = cam)
        hud.add(TextRenderer().also { it.text = "GALAXY DEFENDER - BULLET HELL"; it.size = 0.4f; it.screenSpace = true; it.color = 0xFF00E676.toInt() })
        hud.y = 5f

        p.saveScene(s)
        p.startScene = "Main"
    }
}
