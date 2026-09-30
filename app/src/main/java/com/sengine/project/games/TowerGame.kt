package com.sengine.project.games

import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SpriteRenderer
import com.sengine.engine.core.TextRenderer
import com.sengine.project.Project
import com.sengine.project.games.GameKit.box
import com.sengine.project.games.GameKit.circle
import com.sengine.project.games.GameKit.install
import com.sengine.project.games.GameKit.label
import com.sengine.project.games.GameKit.obj
import com.sengine.project.games.GameKit.script
import com.sengine.project.games.GameKit.sprite

/**
 * "Iron Guard" — complete 2D tower defense:
 *  - Two maps with winding enemy roads, 10 escalating waves, bosses every 5th wave.
 *  - Build / upgrade / sell three tower types: Arrow (fast), Cannon (splash), Frost (slows).
 *  - Gold economy, 20 lives, fast-forward, sell refunds, victory & defeat screens, records.
 *  - A [com.sengine.engine.core.UIRadar] minimap tracks incoming enemies in the HUD corner.
 */
internal object TowerGame {
    val TOWERS = listOf("Arrow Tower", "Cannon Tower", "Frost Tower")

    fun build(p: Project) {
        GameKit.install(p, "Grass Tile", "Dirt", "Stone", "Brick Wall", "Metal Plate", "Wood Planks", "Wooden Crate", "Soft Particle", "Spark",
            "Laser", "Explosion", "Hit", "Power Up", "Coin Pickup", "Win Jingle", "Game Over", "UI Click", "Night Sky",
            "Action Battle (song)", "Menu Theme (song)", "Rotator", "Timed Spawner (Blueprint)")
        p.writeAsset("TdMenu.js", Games.res("tower/TdMenu.js"))
        p.writeAsset("TdGame.js", Games.res("tower/TdGame.js"))
        p.writeAsset("TdTower.js", Games.res("tower/TdTower.js"))
        p.writeAsset("TdEnemy.js", Games.res("tower/TdEnemy.js"))
        p.writeAsset("TdBullet.js", Games.res("tower/TdBullet.js"))
        p.saveScene(menu())
        p.saveScene(map("Meadow", "Td1", false))
        p.saveScene(map("Crossroads", "Td2", true))
        p.saveControls(com.sengine.engine.controls.ControlLayout.presets.first { it.name == "Touch Only" }.copy())
        p.startScene = "TdMenu"
        p.orientation = 0
    }

    private fun menu(): Scene {
        val s = Scene("TdMenu")
        obj(s, "Main Camera").add(Camera2D().also { it.size = 6f; it.background = 0xFF10182B.toInt() })
        val sky = obj(s, "Sky", 0f, 0f, 20f, 12f).also { it.order = -100 }
        sky.add(SpriteRenderer().also { it.texture = "NightSky.png" })
        GameKit.text(s, "Title", "IRON GUARD", 0f, -0.6f, 1f, 0xFF57D16A, anchor = GameKit.TOP)
        GameKit.text(s, "Sub", "tower defense", 0f, 0.2f, 0.34f, 0xFF9FB6D8, anchor = GameKit.TOP)
        GameKit.button(s, "Map1Btn", "MEADOW", -2.2f, 0.7f, 3.9f, 1.05f, 0xFF22C55E, "scene:Td1", anchor = GameKit.CENTER, textSize = 0.44f)
        GameKit.button(s, "Map2Btn", "CROSSROADS", 2.2f, 0.7f, 3.9f, 1.05f, 0xFF4C6FFF, "scene:Td2", anchor = GameKit.CENTER, textSize = 0.4f)
        GameKit.text(s, "Record", "BEST WAVES  —  Meadow 0  •  Crossroads 0", 0f, 3.4f, 0.3f, 0xFFB9C4D8, anchor = GameKit.BOTTOM)
        GameKit.button(s, "HowBtn", "How to Play", 0f, 2.6f, 3.6f, 0.8f, 0xFF334155, "show:HelpPanel", anchor = GameKit.BOTTOM)
        val help = GameKit.panel(s, "HelpPanel", 0f, -0.3f, 9.8f, 6.4f).off()
        GameKit.text(s, "HelpTitle", "HOW TO PLAY", 0f, 2.7f, 0.5f, parent = help)
        GameKit.text(s, "HelpBody", "1. Tap a tower button (bottom) to pick it — 50g / 90g / 70g.\n2. Tap a glowing build spot on the grass to place it.\n3. Tap your tower to upgrade (+80g, +damage) or sell (60% back).\n4. Start the wave — enemies march along the road to your castle.\nArrow: fast single target.  Cannon: slow splash damage.\nFrost: chills enemies, slowing them by half.\nSurvive all 10 waves to win. Bosses arrive on waves 5 and 10!", 0f, 0.4f, 0.27f, 0xFFE2E8F0, parent = help)
        GameKit.button(s, "HelpOkBtn", "OK", 0f, -2.6f, 3f, 0.85f, 0xFF22C55E, "hide:HelpPanel", parent = help)
        obj(s, "MenuManager").script("TdMenu.js")
        return s
    }

    /** Road waypoints per map (world units). */
    private fun road(map2: Boolean): List<Pair<Float, Float>> = if (!map2) listOf(
        -9f to 2.5f, -4f to 2.5f, -4f to -1.5f, 1f to -1.5f, 1f to 2.5f, 6f to 2.5f, 6f to -1f, 9f to -1f
    ) else listOf(
        -9f to -2f, -5f to -2f, -5f to 2.5f, 0f to 2.5f, 0f to -2.5f, 5f to -2.5f, 5f to 1.5f, 9f to 1.5f
    )

    private fun map(title: String, sceneName: String, map2: Boolean): Scene {
        val s = Scene(sceneName)
        obj(s, "Main Camera").add(Camera2D().also { it.size = 6f; it.background = 0xFF16211A.toInt() })
        val pts = road(map2)

        // grass field
        val field = obj(s, "Field", 0f, 0f, 22f, 12f).also { it.order = -50 }
        field.add(SpriteRenderer().also { it.texture = "Grass.png"; it.tileX = 8f; it.tileY = 4.4f })

        // road segments (dirt strips + castle + spawn portal)
        for (i in 0 until pts.size - 1) {
            val (x0, y0) = pts[i]; val (x1, y1) = pts[i + 1]
            val cx = (x0 + x1) / 2; val cy = (y0 + y1) / 2
            val len = kotlin.math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))
            val road = obj(s, "Road", cx, cy, if (y0 == y1) len else 1.6f, if (y0 == y1) 1.6f else len).also { it.order = -40 }
            road.add(SpriteRenderer().also { it.texture = "Dirt.png"; it.tileX = if (y0 == y1) len / 1.6f else 1f; it.tileY = if (y0 == y1) 1f else len / 1.6f })
        }
        // build spots along the road (simple grid, not on the road)
        for (gx in -8..8 step_ 2) for (gy in -4..4 step_ 2) {
            val x = gx.toFloat(); val y = gy.toFloat()
            if (onRoad(x, y, pts)) continue
            val spot = obj(s, "Spot", x, y, 0.7f, 0.7f).also { it.order = 1; it.tag = "Spot" }
            spot.sprite(0x33FFFFFF, 1)
            spot.circle(true)
            val glow = obj(s, "SpotGlow", 0f, 0f, 1.15f, 1.15f, parent = spot).off().also { it.order = 2 }
            glow.sprite(0x9957D16A, 1)
            spot.add(Collider2D().also { it.isTrigger = true; it.radius = 0.5f })
            spot.script("TdGame.js", "spot=true")
        }
        // castle + portal decorations
        val castle = obj(s, "Castle", pts.last().first + 1.2f, pts.last().second, 1.6f, 2.2f).also { it.order = 2 }
        castle.sprite(0xFF8D6E63).box()
        castle.label("", 0.4f)
        val portal = obj(s, "Portal", pts.first().first - 1f, pts.first().second, 1.2f, 2.2f).also { it.order = 2 }
        portal.sprite(0xFF8B5CF6, 0)
        val swirl = obj(s, "PortalSpin", 0f, 0f, 0.6f, 0.6f, parent = portal).also { it.order = 3 }
        swirl.sprite(0xFFD8B9FF, 1)
        swirl.script("Rotator.js", "x=0, y=180, z=0")

        // tower + enemy templates
        towerTemplate(s, "ArrowTower", 0xFF9CCC65, 1)
        towerTemplate(s, "CannonTower", 0xFFFF8A65, 0)
        towerTemplate(s, "FrostTower", 0xFF4FC3F7, 1)
        enemyTemplate(s, "Grunt", 0xFFE53935, 0.62f)
        enemyTemplate(s, "Runner", 0xFFFFB300, 0.5f)
        enemyTemplate(s, "Boss", 0xFF7C1F1F, 1.25f)
        val bullet = obj(s, "Bullet", 0f, 0f, 0.22f, 0.22f).off().also { it.order = 15 }
        bullet.sprite(0xFFFFF176, 1)
        bullet.add(Collider2D().also { it.isTrigger = true; it.radius = 0.15f })
        bullet.script("TdBullet.js")
        val boom = obj(s, "Boom", 0f, 0f).off().also { it.order = 30 }
        boom.add(ParticleEmitter().also { it.emitting = false; it.rate = 0f; it.spread = 360f; it.speed = 5f; it.lifetime = 0.5f; it.gravity = -2f; it.startColor = 0xFFFFCC80.toInt(); it.endColor = 0x00FF6F00; it.texture = "SoftDot.png"; it.additive = true })

        hud(s, map2)
        val roadParam = pts.joinToString("|") { "${it.first}|${it.second}" }
        obj(s, "TdManager").script("TdGame.js", "map=${if (map2) "Td2" else "Td1"}, road=$roadParam")
        return s
    }

    private fun onRoad(x: Float, y: Float, pts: List<Pair<Float, Float>>): Boolean {
        for (i in 0 until pts.size - 1) {
            val (x0, y0) = pts[i]; val (x1, y1) = pts[i + 1]
            if (y0 == y1 && y == y0 && x >= minOf(x0, x1) - 0.5f && x <= maxOf(x0, x1) + 0.5f) return true
            if (x0 == x1 && x == x0 && y >= minOf(y0, y1) - 0.5f && y <= maxOf(y0, y1) + 0.5f) return true
        }
        return false
    }

    private fun towerTemplate(s: Scene, name: String, color: Int, shape: Int): GameObject {
        val t = obj(s, name, 0f, 20f, 0.9f, 0.9f).off().also { it.tag = "Tower"; it.order = 5 }
        t.sprite(color, shape, if (shape == 0) "Metal.png" else "Wood.png").box()
        val head = obj(s, "Turret", 0f, 0.3f, 0.5f, 0.5f, parent = t).also { it.order = 6 }
        head.sprite(0xFFECEFF1, 1)
        t.script("TdTower.js")
        return t
    }

    private fun enemyTemplate(s: Scene, name: String, color: Int, size: Float): GameObject {
        val e = obj(s, name, 0f, 20f, size, size).off().also { it.tag = "Enemy"; it.order = 8 }
        e.sprite(color, 1)
        e.circle(false, 0.45f)
        e.label("", size * 0.5f).also { it.get<TextRenderer>()!!.color = 0xFFFFFFFF.toInt() }
        e.script("TdEnemy.js")
        return e
    }

    private fun hud(s: Scene, map2: Boolean) {
        GameKit.panel(s, "TopBar", 0f, 4.5f, 10f, 1f, 0xCC0E1420, anchor = GameKit.TOP).also { it.order = 150 }
        GameKit.text(s, "GoldText", "150g", -3.4f, 0.2f, 0.4f, 0xFFFFD166, anchor = GameKit.TOP)
        GameKit.text(s, "LivesText", "20", 0f, 0.2f, 0.4f, 0xFFFF5C6C, anchor = GameKit.TOP)
        GameKit.text(s, "WaveText", "Wave 0/10", 2.6f, 0.2f, 0.38f, 0xFFB9C4D8, anchor = GameKit.TOP)
        // build bar
        GameKit.button(s, "BuildArrow", "Arrow\n50g", -3.2f, -4.4f, 1.9f, 1f, 0xFF33691E, "call:pickArrow", anchor = GameKit.BOTTOM, textSize = 0.24f)
        GameKit.button(s, "BuildCannon", "Cannon\n90g", -1.1f, -4.4f, 1.9f, 1f, 0xFFBF360C, "call:pickCannon", anchor = GameKit.BOTTOM, textSize = 0.24f)
        GameKit.button(s, "BuildFrost", "Frost\n70g", 1f, -4.4f, 1.9f, 1f, 0xFF01579B, "call:pickFrost", anchor = GameKit.BOTTOM, textSize = 0.24f)
        GameKit.button(s, "WaveBtn", "START WAVE", 3.3f, -4.4f, 2.6f, 1f, 0xFF22C55E, "call:startWave", anchor = GameKit.BOTTOM, textSize = 0.3f)
        GameKit.button(s, "SpeedBtn", "x1", 4.7f, -3.2f, 0.8f, 0.8f, 0xFF334155, "call:speed", anchor = GameKit.BOTTOM, textSize = 0.34f)
        // radar minimap (v7)
        GameKit.radar(s, "Radar", 0f, 0f, 1.9f, "Enemy", 24f, anchor = GameKit.TR, dot = 0xFFFF5C6C)
        // tower popup (upgrade / sell)
        val pop = GameKit.panel(s, "TowerPanel", 0f, 2.2f, 5.4f, 2.6f, 0xEE101820).off()
        GameKit.text(s, "TowerName", "Tower", 0f, 0.9f, 0.36f, 0xFFFFFFFF, parent = pop)
        GameKit.text(s, "TowerInfo", "", 0f, 0.35f, 0.26f, 0xFF9FB6D8, parent = pop)
        GameKit.button(s, "UpgradeBtn", "Upgrade 80g", -1.3f, -0.4f, 2.4f, 0.8f, 0xFF22C55E, "call:upgradeTower", pop, textSize = 0.26f)
        GameKit.button(s, "SellBtn", "Sell", 1.3f, -0.4f, 2f, 0.8f, 0xFFB71C1C, "call:sellTower", pop, textSize = 0.28f)
        GameKit.button(s, "PopCloseBtn", "X", 2.3f, 0.9f, 0.6f, 0.6f, 0xFF334155, "hide:TowerPanel", pop, textSize = 0.3f)
        // end screens
        val win = GameKit.panel(s, "WinPanel", 0f, 0f, 7.5f, 5.4f).off()
        GameKit.text(s, "WinTitle", "CASTLE DEFENDED!", 0f, 1.6f, 0.65f, 0xFFFFD166, parent = win)
        GameKit.text(s, "WinStats", "", 0f, 0.6f, 0.36f, 0xFFE2E8F0, parent = win)
        GameKit.button(s, "WinAgainBtn", "Play Again", 0f, -0.5f, 4.4f, 0.9f, 0xFF22C55E, "resume;reload", win)
        GameKit.button(s, "WinMenuBtn", "Menu", 0f, -1.6f, 4.4f, 0.9f, 0xFF3A4566, "resume;scene:TdMenu", win)
        val lose = GameKit.panel(s, "LosePanel", 0f, 0f, 7.5f, 5.4f).off()
        GameKit.text(s, "LoseTitle", "CASTLE FELL", 0f, 1.6f, 0.65f, 0xFFFF5C6C, parent = lose)
        GameKit.text(s, "LoseStats", "", 0f, 0.6f, 0.36f, 0xFFE2E8F0, parent = lose)
        GameKit.button(s, "LoseAgainBtn", "Try Again", 0f, -0.5f, 4.4f, 0.9f, 0xFF22C55E, "resume;reload", lose)
        GameKit.button(s, "LoseMenuBtn", "Menu", 0f, -1.6f, 4.4f, 0.9f, 0xFF3A4566, "resume;scene:TdMenu", lose)
    }
}
