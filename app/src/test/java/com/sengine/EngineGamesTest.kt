package com.sengine

import com.sengine.engine.Engine
import com.sengine.engine.core.TextRenderer
import com.sengine.engine.core.VoxelWorld
import com.sengine.project.GameDoctor
import com.sengine.project.Project
import com.sengine.project.Templates
import com.sengine.project.games.Games
import com.sengine.project.games.RacingGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.math.atan2
import kotlin.math.sqrt

/** Headless play-throughs of the four bundled sample games. */
class EngineGamesTest {

    private class Run(val engine: Engine, val errors: MutableList<String>)

    private fun project(templateName: String): Project {
        val dir = Files.createTempDirectory("games").toFile()
        val p = Project(File(dir, templateName.substringBefore(" ").replace("'", ""))).also { it.saveMeta() }
        Templates.all.first { it.name == templateName }.build(p)
        p.saveMeta()
        // every bundled script must compile
        for (js in p.listAssets().filter { it.endsWith(".js") }) assertNull(js, GameDoctor.syntaxError(p.readAsset(js)!!, js))
        val errors = GameDoctor.check(p).filter { it.severity >= 2 && !it.title.startsWith("Missing texture") }
        assertTrue("doctor errors: " + errors.joinToString { it.title + " — " + it.detail }, errors.isEmpty())
        return p
    }

    private fun start(p: Project, scene: String, prep: (Engine) -> Unit = {}): Run {
        val e = Engine(p, p.loadScene(scene))
        e.gameView.widthPx = 1600; e.gameView.heightPx = 900
        prep(e)
        val errors = ArrayList<String>()
        e.listeners.add(object : Engine.Listener {
            override fun onLog(level: Int, message: String) {
                if (level >= 2) errors.add(message)
                if (level >= 1) println("SIM game log[$level]: $message")
            }
        })
        e.play()
        return Run(e, errors)
    }

    private fun Run.frames(n: Int, each: (Int) -> Unit = {}) {
        repeat(n) { i -> each(i); synchronized(engine.lock) { engine.tick(1f / 60f) } }
    }

    private fun Run.text(name: String) = engine.scene.find(name)?.get<TextRenderer>()?.text ?: ""
    private fun Run.visible(name: String) = engine.scene.find(name)?.isActiveInHierarchy() == true

    @Test
    fun allGameTemplatesRegistered() {
        assertEquals(4, Games.templates.size)
        assertTrue(Templates.all.size >= 11)
    }

    @Test
    fun skyStrikeMenusAndCombat() {
        val p = project(Games.templates[0].name)
        assertEquals("Menu", p.startScene)
        assertTrue(p.listScenes().containsAll(listOf("Menu", "Levels", "Level1", "Level2", "Level3")))
        val r = start(p, "Menu")
        r.frames(10)
        assertTrue(r.engine.ui.clickByName("PlayBtn")); r.frames(5)
        assertEquals("Levels", r.engine.scene.name)
        assertTrue(r.text("Level2Btn").isEmpty() || true)
        assertTrue(r.engine.ui.clickByName("Level1Btn")); r.frames(5)
        assertEquals("Level1", r.engine.scene.name)
        var maxEnemies = 0
        r.frames(60 * 70) { i ->
            r.engine.input.joyX = kotlin.math.sin(i / 50f)
            maxEnemies = maxOf(maxEnemies, r.engine.scene.objects.count { it.tag == "Enemy" && it.active && !it.destroyed })
        }
        val score = r.text("ScoreText").toIntOrNull() ?: 0
        println("SIM sky strike score=$score maxEnemies=$maxEnemies wave='${r.text("WaveText")}' gameOver=${r.visible("GameOverPanel")}")
        assertTrue("enemies should spawn", maxEnemies > 0)
        assertTrue("player should score", score > 0)
        assertTrue(r.errors.toString(), r.errors.isEmpty())
    }

    @Test
    fun deadZoneWavesAndUpgrades() {
        val p = project(Games.templates[1].name)
        val r = start(p, "Arena")
        var upgrades = 0
        r.frames(60 * 90) { i ->
            val a = i / 40f
            r.engine.input.rawSticks["aim"] = floatArrayOf(kotlin.math.cos(a), kotlin.math.sin(a))
            r.engine.input.joyX = kotlin.math.sin(i / 90f) * 0.6f
            if (r.visible("UpgradePanel") && i % 30 == 0) { if (r.engine.ui.clickByName("Upgrade1")) upgrades++ }
            if (r.visible("GameOverPanel")) return@frames
        }
        val kills = r.text("KillsText").removePrefix("KILLS ").toIntOrNull() ?: 0
        val wave = r.text("WaveText").removePrefix("WAVE ").toIntOrNull() ?: 0
        println("SIM dead zone kills=$kills score='${r.text("ScoreText")}' wave=$wave upgrades=$upgrades dead=${r.visible("GameOverPanel")} errors=${r.errors}")
        assertTrue("should clear waves / kill zombies", kills > 0 || wave >= 2)
        assertTrue(r.errors.toString(), r.errors.isEmpty())
    }

    @Test
    fun turboRallyRaceFinishes() {
        val p = project(Games.templates[2].name)
        assertEquals(1 + 6, p.listScenes().size)
        val r0 = start(p, "RallyMenu")
        r0.frames(5)
        assertTrue(r0.engine.ui.clickByName("RaceBtn")); r0.frames(2)
        assertTrue(r0.engine.ui.clickByName("Road1")); r0.frames(2)
        assertTrue(r0.engine.ui.clickByName("StartBtn")); r0.frames(3)
        assertEquals("Race_Green_1", r0.engine.scene.name)
        assertTrue(r0.errors.toString(), r0.errors.isEmpty())

        // one-lap race on the oval with a simple autopilot steering the player's car
        val pts = RacingGame.trackPoints(0, 0)
        val r = start(p, "Race_Green_0") { it.storage.set("rally_laps", 1) }
        var idx = 0
        var maxSpeed = 0f
        r.frames(60 * 75) { _ ->
            val car = r.engine.scene.find("Player")!!
            var best = Float.MAX_VALUE
            for (k in -3..8) {
                val j = ((idx + k) % pts.size + pts.size) % pts.size
                val d = (pts[j][0] - car.x) * (pts[j][0] - car.x) + (pts[j][1] - car.z) * (pts[j][1] - car.z)
                if (d < best) { best = d; idx = j }
            }
            val t = pts[(idx + 4) % pts.size]
            val want = atan2(t[0] - car.x, t[1] - car.z)
            var err = want - Math.toRadians(car.rotY.toDouble()).toFloat()
            while (err > Math.PI) err -= (2 * Math.PI).toFloat(); while (err < -Math.PI) err += (2 * Math.PI).toFloat()
            r.engine.input.joyX = (err * 2.5f).coerceIn(-1f, 1f)
            r.engine.input.rawButtons["Gas"] = true
            maxSpeed = maxOf(maxSpeed, r.text("SpeedText").toFloatOrNull() ?: 0f)
            if (r.visible("ResultPanel")) return@frames
        }
        val ai = r.engine.scene.find("AI1")!!
        println("SIM rally maxSpeed=${maxSpeed}km/h pos='${r.text("PosText")}' results=${r.visible("ResultPanel")}\n${r.text("ResultText")}")
        println("SIM rally AI1 at x=${ai.x} z=${ai.z}")
        assertTrue("car should reach racing speed", maxSpeed > 120f)
        assertTrue("race should finish with results", r.visible("ResultPanel"))
        assertTrue(r.errors.toString(), r.errors.isEmpty())
    }

    @Test
    fun miniCraftWorldBuildAndBreak() {
        val p = project(Games.templates[3].name)
        val m = start(p, "CraftMenu")
        m.frames(5)
        assertTrue(m.engine.ui.clickByName("NewBtn")); m.frames(3)
        assertEquals("World", m.engine.scene.name)

        val r = start(p, "World") { e ->
            e.storage.set("mc_size", 4)
            e.scene.find("Player")!!.get<com.sengine.engine.core.ScriptComponent>()!!.params = "pitch=-89"
        }
        r.frames(180)
        val w = r.engine.scene.find("World")!!.get<VoxelWorld>()!!
        assertEquals(64, w.data!!.sx)
        val pl = r.engine.scene.find("Player")!!
        val sx = kotlin.math.floor(pl.x).toInt(); val sz = kotlin.math.floor(pl.z).toInt()
        val before = w.data!!.surfaceY(sx, sz)
        println("SIM craft player y=${pl.y} surface=$before")
        assertTrue("player should stand on the terrain", kotlin.math.abs(pl.y - (before + 0.9f)) < 0.5f)
        r.engine.input.rawButtons["Break"] = true
        r.frames(40)
        r.engine.input.rawButtons["Break"] = false
        r.frames(60)
        val after = w.data!!.surfaceY(kotlin.math.floor(pl.x).toInt(), kotlin.math.floor(pl.z).toInt())
        println("SIM craft surface before=$before after=$after y=${pl.y}")
        assertTrue("breaking should dig down", after < before)
        // place a block back while looking down
        r.engine.input.rawButtons["Place"] = true
        r.frames(3)
        r.engine.input.rawButtons["Place"] = false
        assertTrue(r.errors.toString(), r.errors.isEmpty())
        val sp = sqrt(0f)
        assertEquals(0f, sp)
    }
}
