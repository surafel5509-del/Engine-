package com.sengine.project.games

import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.ScriptComponent
import com.sengine.engine.core.SpriteRenderer
import com.sengine.engine.core.TextRenderer
import com.sengine.engine.core.Tilemap
import com.sengine.project.Project

/** Dungeon Crawler: A 2D top-down RPG with inventory, slimes, keys, and doors. */
object DungeonCrawlerGame {

    fun build(p: Project) {
        p.writeAsset("HeroRPG.lua", """
-- Top-Down Hero Lua Script
local speed = 5.0

function update(dt)
    self.move(input.axisX * speed * dt, input.axisY * speed * dt)
    if input.axisX ~= 0 or input.axisY ~= 0 then
        self.rotation = math.atan2(input.axisY, input.axisX) * 180 / math.pi - 90
    end
end
""".trimIndent())

        p.writeAsset("SlimeAI.js", """
var speed = 1.5, dir = 1;
function update(dt) {
    self.move(dir * speed * dt, 0);
    if (Math.abs(self.x) > 4) dir = -dir;
}
function onCollision(other) {
    if (other.tag == "Player") {
        scene.shake(0.4);
        audio.play("hit.wav");
    }
}
""".trimIndent())

        val s = Scene("Main")
        s.gravityY = 0f

        val cam = s.create("Main Camera")
        cam.add(Camera2D().also { it.size = 7f; it.background = 0xFF121216.toInt(); it.follow = "Hero" })

        val tm = s.create("DungeonMap")
        val tilemap = Tilemap().also {
            it.cols = 16
            it.rows = 16
            it.generateCollisions = true
            for (x in 0 until 16) {
                it.setTile(x, 0, 1)
                it.setTile(x, 15, 1)
                it.setTile(0, x, 1)
                it.setTile(15, x, 1)
            }
        }
        tm.add(tilemap)

        val hero = s.create("Hero")
        hero.x = 3f; hero.y = 3f
        hero.tag = "Player"
        hero.add(SpriteRenderer().also { it.color = 0xFF4CAF50.toInt(); it.shape = 1 })
        hero.add(Collider2D().also { it.shape = 1; it.width = 0.8f; it.height = 0.8f })
        hero.add(Rigidbody2D().also { it.bodyType = 0; it.friction = 0f })
        hero.add(ScriptComponent().also { it.script = "HeroRPG.lua" })

        val slime = s.create("Slime")
        slime.x = 8f; slime.y = 8f
        slime.add(SpriteRenderer().also { it.color = 0xFFE91E63.toInt() })
        slime.add(Collider2D())
        slime.add(Rigidbody2D().also { it.bodyType = 1 })
        slime.add(ScriptComponent().also { it.script = "SlimeAI.js" })

        val hud = s.create("HUD", parent = cam)
        hud.add(TextRenderer().also { it.text = "DUNGEON CRAWLER - USE JOYSTICK TO MOVE"; it.size = 0.35f; it.screenSpace = true; it.color = 0xFFFFD54F.toInt() })
        hud.y = 4.8f

        p.saveScene(s)
        p.startScene = "Main"
    }
}
