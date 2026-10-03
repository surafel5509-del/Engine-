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

/** Apex Drift: 3D Arcade Racing Game with sports cars, asphalt tracks, and drift physics. */
object ApexDriftGame {

    fun build(p: Project) {
        p.writeAsset("CarDriver.js", """
var speed = 12.0;
var turn = 90.0;
function update(dt) {
    if (input.axisY != 0) {
        self.move(0, 0, -input.axisY * speed * dt);
    }
    if (input.axisX != 0) {
        self.rotate(0, -input.axisX * turn * dt, 0);
    }
}
""".trimIndent())

        val s = Scene("Main")
        s.gravityY = -9.8f

        val cam = s.create("Camera3D")
        cam.y = 4f; cam.z = 8f; cam.rotX = -15f
        cam.add(Camera3D().also { it.follow = "RaceCar"; it.offsetY = 3.5f; it.offsetZ = 7f })

        val sun = s.create("Sun")
        sun.rotX = -45f; sun.rotY = 30f
        sun.add(Light())

        val track = s.create("Track")
        track.scaleX = 40f; track.scaleY = 0.2f; track.scaleZ = 100f
        track.y = -0.1f
        track.add(MeshRenderer().also { it.mesh = 0; it.color = 0xFF222225.toInt() })
        track.add(Collider3D())

        val car = s.create("RaceCar")
        car.y = 0.5f
        car.scaleX = 1.2f; car.scaleY = 0.8f; car.scaleZ = 2.2f
        car.add(MeshRenderer().also { it.mesh = 0; it.color = 0xFFFF1744.toInt() })
        car.add(Collider3D())
        car.add(Rigidbody3D().also { it.friction = 0.8f })
        car.add(ScriptComponent().also { it.script = "CarDriver.js" })

        val hud = s.create("HUD")
        hud.add(TextRenderer().also { it.text = "APEX DRIFT 3D - JOYSTICK TO ACCELERATE / STEER"; it.size = 0.35f; it.screenSpace = true; it.color = 0xFFFFFFFF.toInt() })
        hud.y = 4.2f

        p.saveScene(s)
        p.startScene = "Main"
    }
}
