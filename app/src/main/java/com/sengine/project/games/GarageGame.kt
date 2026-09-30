package com.sengine.project.games

import com.sengine.engine.core.Camera3D
import com.sengine.engine.core.Collider3D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Light
import com.sengine.engine.core.MeshRenderer
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.Scene
import com.sengine.project.Project
import com.sengine.project.games.GameKit.body3
import com.sengine.project.games.GameKit.col3
import com.sengine.project.games.GameKit.mesh
import com.sengine.project.games.GameKit.obj
import com.sengine.project.games.GameKit.obj3
import com.sengine.project.games.GameKit.off
import com.sengine.project.games.GameKit.particles
import com.sengine.project.games.GameKit.script

/**
 * "Zombie Garage" — complete 3D vehicle-combat arena:
 *  - Drive a battle pickup around a walled junkyard at night (Racing controls), running down
 *    zombie hordes or blasting them with the roof turret.
 *  - Wave-based survival with a between-wave garage shop (repair / armor / ammo / turret)
 *    and a persistent best-wave record.
 *  - Headlight cones, blood-less "goo" pops, muzzle flashes, burning barrels, ammo pickups.
 */
internal object GarageGame {
    private const val CUBE = 0; private const val SPHERE = 1; private const val CYL = 3; private const val CUSTOM = 8

    fun build(p: Project) {
        GameKit.install(p, "Metal Plate", "Rusty Metal", "Asphalt", "Wood Planks", "Wooden Crate", "Brick Wall", "Concrete", "Camo",
            "Soft Particle", "Spark", "Explosion", "Hit", "Power Up", "Coin Pickup", "Win Jingle", "Game Over", "UI Click",
            "Zombie Groan", "Shotgun Blast", "Pistol Shot", "Reload", "Nitro Whoosh", "Action Battle (song)", "Menu Theme (song)", "Rotator")
        for (s in scripts) p.writeAsset(s, Games.res("garage/$s"))
        p.saveScene(menu())
        p.saveScene(yard())
        p.saveControls(com.sengine.engine.controls.ControlLayout.presets.first { it.name == "Racing" }.copy())
        p.startScene = "GarageMenu"
        p.orientation = 0
    }

    private val scripts = listOf("ZgMenu.js", "ZgCar.js", "ZgZombie.js", "ZgGame.js")

    private fun GameObject.noShadow(): GameObject { get<MeshRenderer>()?.castShadows = false; return this }
    private fun GameObject.unlit(): GameObject { get<MeshRenderer>()?.unlit = true; get<MeshRenderer>()?.castShadows = false; return this }

    private fun block(s: Scene, name: String, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, color: Long, tex: String = "", tiling: Float = 1f): GameObject =
        obj3(s, name, x, y, z, sx, sy, sz).mesh(CUBE, color, tex, tiling).col3()

    // ------------------------------------------------------------------ scenes
    private fun menu(): Scene {
        val s = Scene("GarageMenu")
        val cam = obj3(s, "Main Camera", -6f, 2.6f, 6f)
        cam.add(Camera3D().also { c -> c.fov = 55f; c.skyTop = 0xFF0A0E1A.toInt(); c.skyHorizon = 0xFF1A2332.toInt(); c.sunDisc = false; c.quality = 2 })
        cam.add(Light().also { it.kind = 1; it.color = 0xFFFFB74D.toInt(); it.intensity = 2.2f; it.range = 18f })
        // garage diorama: floor + the battle pickup on a workbench glow
        block(s, "Floor", 0f, -0.5f, 0f, 24f, 1f, 24f, 0xFF26292E, "Concrete.png", 8f)
        block(s, "WallB", 0f, 2.5f, -12f, 24f, 6f, 1f, 0xFF1C1F26, "Metal.png", 6f)
        val truck = obj3(s, "Truck", 0f, 0.9f, 0f)
        carParts(s, truck)
        truck.script("Rotator.js", "x=0, y=18, z=0")
        GameKit.text(s, "Title", "ZOMBIE GARAGE", 0f, -1.5f, 0.9f, 0xFF8BC34A, anchor = GameKit.TOP)
        GameKit.text(s, "Sub", "drive it like you stole it", 0f, -0.7f, 0.3f, 0xFFB9C4D8, anchor = GameKit.TOP)
        GameKit.button(s, "PlayBtn", "SURVIVE", 0f, 1.1f, 4.6f, 1.05f, 0xFF57A83A, "scene:GarageYard", anchor = GameKit.CENTER, textSize = 0.48f)
        GameKit.button(s, "HowBtn", "How to Play", 0f, 2.2f, 4.6f, 0.85f, 0xFF334155, "show:HelpPanel", anchor = GameKit.CENTER)
        GameKit.text(s, "RecordText", "", 0f, 3.6f, 0.3f, 0xFFB9C4D8, anchor = GameKit.BOTTOM)
        val help = GameKit.panel(s, "HelpPanel", 0f, -0.3f, 9.6f, 6.4f).off()
        GameKit.text(s, "HelpTitle", "HOW TO PLAY", 0f, 2.7f, 0.5f, parent = help)
        GameKit.text(s, "HelpBody", "Steer with the wheel, Gas to drive, Brake to reverse,\nNitro to ram through hordes. Roadkill pays 10g!\nHold Fire to shoot the roof turret — aim is automatic.\nGrab AMMO crates and WRENCH pickups around the yard.\nBetween waves the GARAGE opens: repair, armor up,\nupgrade the turret. Zombies get meaner every wave.", 0f, 0.4f, 0.28f, 0xFFE2E8F0, parent = help)
        GameKit.button(s, "HelpOkBtn", "OK", 0f, -2.6f, 3f, 0.85f, 0xFF57A83A, "hide:HelpPanel", parent = help)
        obj(s, "ZgMenuMgr").script("ZgMenu.js")
        return s
    }

    /** The battle pickup built from primitives, parented to [root]. */
    private fun carParts(s: Scene, root: GameObject) {
        obj3(s, "Body", 0f, 0f, 0f, 1.5f, 0.55f, 3f, root).mesh(CUBE, 0xFF6B8E23, "Camo.png", 2f)
        obj3(s, "Cabin", 0f, 0.55f, -0.25f, 1.35f, 0.6f, 1.3f, root).mesh(CUBE, 0xFF55701D, "Camo.png", 2f)
        obj3(s, "Glass", 0f, 0.58f, -0.91f, 1.2f, 0.42f, 0.06f, root).mesh(CUBE, 0xFF9FD8F0).unlit()
        obj3(s, "Bed", 0f, 0.42f, 0.95f, 1.4f, 0.35f, 1.1f, root).mesh(CUBE, 0xFF3E5215)
        obj3(s, "BumperF", 0f, -0.12f, -1.62f, 1.6f, 0.3f, 0.2f, root).mesh(CUBE, 0xFF37474F, "Metal.png").also { it.tag = "Ram" }
        obj3(s, "RamSpike", 0f, 0.05f, -1.74f, 1.5f, 0.22f, 0.1f, root).mesh(CONE, 0xFFB0BEC5)
        obj3(s, "BumperB", 0f, -0.12f, 1.62f, 1.6f, 0.3f, 0.2f, root).mesh(CUBE, 0xFF37474F, "Metal.png")
        val wl = listOf(-0.78f to -1.05f, 0.78f to -1.05f, -0.78f to 1.05f, 0.78f to 1.05f)
        wl.forEachIndexed { i, (x, z) ->
            val w = obj3(s, "Wheel", x, -0.28f, z, 0.5f, 0.5f, 0.5f, root).mesh(CYL, 0xFF141414).noShadow()
            w.rotation = 0f
            w.tag = "Wheel"
            w.script("WheelSpin.js")
        }
        // roof turret
        obj3(s, "TurretBase", 0f, 0.92f, -0.2f, 0.5f, 0.18f, 0.5f, root).mesh(CYL, 0xFF263238)
        val turret = obj3(s, "Turret", 0f, 1.1f, -0.2f, 0.16f, 0.16f, 0.9f, root).mesh(CUBE, 0xFF101418)
        obj3(s, "Muzzle", 0f, 0f, -0.6f, 0.14f, 0.14f, 0.14f, turret).mesh(SPHERE, 0xFFFFEB3B).unlit().off()
        // headlights
        obj3(s, "LightL", -0.5f, 0f, -1.55f, 0.22f, 0.22f, 0.08f, root).mesh(SPHERE, 0xFFFFF9C4).unlit()
        obj3(s, "LightR", 0.5f, 0f, -1.55f, 0.22f, 0.22f, 0.08f, root).mesh(SPHERE, 0xFFFFF9C4).unlit()
    }

    private fun yard(): Scene {
        val s = Scene("GarageYard")
        val cam = obj3(s, "Main Camera", 0f, 10f, 12f)
        cam.add(Camera3D().also { c ->
            c.follow = "Car"; c.offsetY = 6.5f; c.offsetZ = 11f; c.fov = 58f; c.far = 220f
            c.skyTop = 0xFF070B14.toInt(); c.skyHorizon = 0xFF141C2C.toInt(); c.sunDisc = false
            c.quality = 2; c.shadowDistance = 50f
        })
        cam.add(Light().also { it.kind = 0; it.color = 0xFF8899CC.toInt(); it.intensity = 0.55f })
        // moon point light
        val moon = obj3(s, "MoonLight", 0f, 30f, 0f)
        moon.add(Light().also { it.kind = 1; it.color = 0xFFB3C7F7.toInt(); it.intensity = 1.4f; it.range = 90f })

        // arena
        block(s, "Ground", 0f, -0.5f, 0f, 76f, 1f, 76f, 0xFF2A2D33, "Asphalt.png", 16f)
        block(s, "WallN", 0f, 2f, -38f, 78f, 5f, 2f, 0xFF3A3F46, "Metal.png", 12f)
        block(s, "WallS", 0f, 2f, 38f, 78f, 5f, 2f, 0xFF3A3F46, "Metal.png", 12f)
        block(s, "WallW", -38f, 2f, 0f, 2f, 5f, 78f, 0xFF3A3F46, "Metal.png", 12f)
        block(s, "WallE", 38f, 2f, 0f, 2f, 5f, 78f, 0xFF3A3F46, "Metal.png", 12f)

        // junk piles / crates / ramps for cover and bumps
        val junk = listOf(
            floatArrayOf(-14f, -10f, 3f), floatArrayOf(16f, -18f, 2.5f), floatArrayOf(-24f, 14f, 3f),
            floatArrayOf(24f, 20f, 2.5f), floatArrayOf(-6f, 24f, 2f), floatArrayOf(8f, -28f, 2f),
            floatArrayOf(-30f, -26f, 2f), floatArrayOf(30f, -4f, 2.5f), floatArrayOf(-18f, 2f, 1.6f), floatArrayOf(4f, 10f, 1.6f))
        junk.forEachIndexed { i, j ->
            block(s, "Junk", j[0], j[2] * 0.5f, j[1], 2.4f, j[2], 2.4f, 0xFF4E4037, "Wood.png", 2f)
            block(s, "JunkTop", j[0] + 0.4f, j[2] + 0.35f, j[1] - 0.3f, 1.2f, 0.7f, 1.2f, 0xFF6D5741, "Crate.png", 1f)
        }
        // burning barrels
        barrel(s, -20f, -20f)
        barrel(s, 22f, 12f)
        barrel(s, -8f, 30f)
        barrel(s, 12f, -12f)

        // zombie template + parts (cloned by the director)
        zombie(s)
        // turret bullet
        val bullet = obj3(s, "Bullet", 0f, -40f, 0f, 0.18f, 0.18f, 0.55f).off().mesh(CYL, 0xFFFFF176)
        bullet.rotation = 90f
        bullet.tag = "Bullet"
        bullet.script("ZgGame.js", "bullet=true")
        // goo pop + muzzle + explosion FX
        fx(s, "Goo", "Sparks", { startColor = 0xFF9CCC65.toInt(); endColor = 0x0033691E; speed = 6f; lifetime = 0.5f; gravity = -10f })
        fx(s, "Muzzle", "Muzzle Flash", { })
        fx(s, "Blast", "Explosion", { })
        // pickups
        pickup(s, "AmmoBox", 0xFF42A5F5, -28f, 8f)
        pickup(s, "Wrench", 0xFFFFD54F, 28f, -24f)
        pickup(s, "AmmoBox", 0xFF42A5F5, 2f, -32f)
        pickup(s, "Wrench", 0xFFFFD54F, -32f, -6f)

        // the truck
        val car = obj3(s, "Car", 0f, 0.8f, -30f)
        car.tag = "Player"
        car.add(Collider3D().also { it.sizeX = 1.6f; it.sizeY = 1.2f; it.sizeZ = 3.4f })
        car.add(Rigidbody3D().also { it.bodyType = 2; it.gravityScale = 0f; it.drag = 0f })
        carParts(s, car)
        car.script("ZgCar.js", "accel=14, maxSpeed=26, grip=3, hp=100")

        // HUD
        GameKit.text(s, "WaveText", "WAVE 1", 0f, -0.4f, 0.5f, 0xFF8BC34A, anchor = GameKit.TOP)
        GameKit.text(s, "LeftText", "0 zombies", 0f, 0.35f, 0.3f, 0xFFB9C4D8, anchor = GameKit.TOP)
        GameKit.bar(s, "HpBar", 0f, 0.18f, 3.4f, 0.26f, 0xFFFF5C6C, anchor = GameKit.BOTTOM, value = 1f)
        GameKit.text(s, "HpText", "TRUCK 100%", 0f, -0.02f, 0.24f, 0xFFFFFFFF, anchor = GameKit.BOTTOM)
        GameKit.text(s, "AmmoText", "AMMO 120", 3.3f, 0.15f, 0.3f, 0xFF42A5F5, anchor = GameKit.BOTTOM)
        GameKit.text(s, "CashText", "0g", -3.3f, 0.15f, 0.34f, 0xFFFFD166, anchor = GameKit.BOTTOM)
        GameKit.radar(s, "Radar", 0f, 0f, 2.1f, "Zombie,Pickup", 70f, anchor = GameKit.TR, dot = 0xFF9CCC65)
        GameKit.pauseMenu(s, "GarageMenu", 0xFF57A83A)
        // garage shop (between waves)
        val shop = GameKit.panel(s, "ShopPanel", 0f, -0.2f, 8.6f, 6.8f, 0xF010181F).off()
        GameKit.text(s, "ShopTitle", "GARAGE", 0f, 2.7f, 0.55f, 0xFF8BC34A, parent = shop)
        GameKit.text(s, "ShopCash", "0g", 0f, 2.1f, 0.34f, 0xFFFFD166, parent = shop)
        GameKit.button(s, "RepairBtn", "Repair 40g", -2f, 0.9f, 3.6f, 0.95f, 0xFF43A047, "call:buyRepair", shop, textSize = 0.3f)
        GameKit.button(s, "ArmorBtn", "Armor 60g", 2f, 0.9f, 3.6f, 0.95f, 0xFF1E88E5, "call:buyArmor", shop, textSize = 0.3f)
        GameKit.button(s, "AmmoBtn", "Ammo 30g", -2f, -0.3f, 3.6f, 0.95f, 0xFF0288D1, "call:buyAmmo", shop, textSize = 0.3f)
        GameKit.button(s, "TurretBtn", "Turret 90g", 2f, -0.3f, 3.6f, 0.95f, 0xFF8E24AA, "call:buyTurret", shop, textSize = 0.3f)
        GameKit.button(s, "NextWaveBtn", "START NEXT WAVE", 0f, -1.7f, 5.6f, 1.1f, 0xFF57A83A, "call:startWave", shop, textSize = 0.34f)
        // game over
        val over = GameKit.panel(s, "OverPanel", 0f, 0f, 7.8f, 5.6f).off()
        GameKit.text(s, "OverTitle", "TRUCK DOWN", 0f, 1.8f, 0.7f, 0xFFFF5C6C, parent = over)
        GameKit.text(s, "OverStats", "", 0f, 0.7f, 0.34f, 0xFFE2E8F0, parent = over)
        GameKit.button(s, "AgainBtn", "Try Again", 0f, -0.6f, 4.4f, 0.95f, 0xFF57A83A, "resume;reload", over)
        GameKit.button(s, "OverMenuBtn", "Menu", 0f, -1.7f, 4.4f, 0.9f, 0xFF3A4566, "resume;scene:GarageMenu", over)
        obj(s, "ZgDirector").script("ZgGame.js")
        return s
    }

    private fun barrel(s: Scene, x: Float, z: Float) {
        val b = obj3(s, "BurnBarrel", x, 0.6f, z, 0.7f, 1.2f, 0.7f).mesh(CYL, 0xFF8D6E63, "Rust.png").col3()
        val fire = obj3(s, "Fire", 0f, 0.9f, 0f, 0.6f, 0.9f, 0.6f, b).particles {
            applyPreset(ParticleEmitter.PRESETS.indexOf("Torch")); emitting = true; texture = "SoftDot.png"
        }
        fire.noShadow()
        b.add(Light().also { it.kind = 1; it.color = 0xFFFF9800.toInt(); it.intensity = 2.4f; it.range = 9f })
    }

    private fun zombie(s: Scene) {
        val z = obj3(s, "Zombie", 0f, -40f, 0f).off()
        z.tag = "Zombie"
        z.add(Collider3D().also { it.sizeX = 0.7f; it.sizeY = 1.7f; it.sizeZ = 0.7f })
        obj3(s, "Body", 0f, -0.85f, 0f, 0.6f, 0.7f, 0.4f, z).mesh(CUBE, 0xFF557B4F, "Camo.png")
        obj3(s, "Head", 0f, -0.25f, 0f, 0.45f, 0.45f, 0.45f, z).mesh(CUBE, 0xFF7DA36F)
        obj3(s, "EyeL", -0.11f, -0.22f, -0.23f, 0.08f, 0.08f, 0.02f, z).mesh(CUBE, 0xFFFF5252).unlit()
        obj3(s, "EyeR", 0.11f, -0.22f, -0.23f, 0.08f, 0.08f, 0.02f, z).mesh(CUBE, 0xFFFF5252).unlit()
        obj3(s, "ArmL", -0.42f, -0.75f, -0.15f, 0.16f, 0.6f, 0.16f, z).mesh(CUBE, 0xFF557B4F)
        obj3(s, "ArmR", 0.42f, -0.75f, -0.15f, 0.16f, 0.6f, 0.16f, z).mesh(CUBE, 0xFF557B4F)
        obj3(s, "LegL", -0.16f, -1.35f, 0f, 0.2f, 0.65f, 0.2f, z).mesh(CUBE, 0xFF37474F)
        obj3(s, "LegR", 0.16f, -1.35f, 0f, 0.2f, 0.65f, 0.2f, z).mesh(CUBE, 0xFF37474F)
        z.script("ZgZombie.js", "speed=2.2, hp=30, damage=10")
    }

    private fun pickup(s: Scene, name: String, color: Long, x: Float, z: Float) {
        val pk = obj3(s, name, x, 0.6f, z, 0.6f, 0.6f, 0.6f)
        pk.tag = "Pickup"
        pk.mesh(CUBE, color, "Metal.png").col3(true, true)
        pk.script("ZgGame.js", "kind=\"$name\"")
        val halo = obj3(s, "Halo", 0f, -0.25f, 0f, 0.9f, 0.1f, 0.9f, pk).mesh(CYL, 0x66FFFFFF).unlit().noShadow()
        halo.script("Rotator.js", "x=0, y=90, z=0")
    }

    private fun fx(s: Scene, name: String, preset: String, tweak: ParticleEmitter.() -> Unit = {}): GameObject =
        obj3(s, name, 0f, -50f, 0f).particles { applyPreset(ParticleEmitter.PRESETS.indexOf(preset)); emitting = false; rate = 0f; texture = "SoftDot.png"; tweak() }.off()
}
