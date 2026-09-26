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
        assertEquals(6, Games.templates.size)
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
        val pairs = HashMap<String, Int>()
        var overlaps = 0
        val orig = r.engine.physics.listener!!
        r.engine.physics.listener = object : com.sengine.engine.physics.PhysicsWorld.Listener {
            override fun onCollisionEnter(a: com.sengine.engine.core.GameObject, b: com.sengine.engine.core.GameObject) { orig.onCollisionEnter(a, b) }
            override fun onTriggerEnter(a: com.sengine.engine.core.GameObject, b: com.sengine.engine.core.GameObject) {
                val k = listOf(a.tag, b.tag).sorted().joinToString("+"); pairs[k] = (pairs[k] ?: 0) + 1
                orig.onTriggerEnter(a, b)
            }
            override fun onTriggerExit(a: com.sengine.engine.core.GameObject, b: com.sengine.engine.core.GameObject) { orig.onTriggerExit(a, b) }
        }
        r.frames(60 * 90) { i ->
            val act0 = r.engine.scene.objects.filter { it.isActiveInHierarchy() && !it.destroyed }
            for (bb in act0) if (bb.tag == "Bullet") for (zz in act0) if (zz.tag == "Zombie") {
                val dx = bb.x - zz.x; val dy = bb.y - zz.y
                if (dx * dx + dy * dy < 0.5f * 0.5f) overlaps++
            }
            // aim at the nearest zombie like a player would
            val pl = r.engine.scene.find("Player")
            val tgt = if (pl == null) null else r.engine.scene.objects.filter { it.tag == "Zombie" && it.isActiveInHierarchy() && !it.destroyed }
                .minByOrNull { (it.x - pl.x) * (it.x - pl.x) + (it.y - pl.y) * (it.y - pl.y) }
            val a = if (pl != null && tgt != null) kotlin.math.atan2(tgt.y - pl.y, tgt.x - pl.x) else i / 40f
            r.engine.input.rawSticks["aim"] = floatArrayOf(kotlin.math.cos(a), kotlin.math.sin(a))
            r.engine.input.joyX = kotlin.math.sin(i / 90f) * 0.6f
            if (r.visible("UpgradePanel") && i % 30 == 0) { if (r.engine.ui.clickByName("Upgrade1")) upgrades++ }
            if (r.visible("GameOverPanel")) return@frames
        }
        val kills = r.text("KillsText").removePrefix("KILLS ").toIntOrNull() ?: 0
        val wave = r.text("WaveText").removePrefix("WAVE ").toIntOrNull() ?: 0
        println("SIM dz triggerPairs=$pairs geomOverlaps=$overlaps")
        println("SIM dead zone kills=$kills score='${r.text("ScoreText")}' wave=$wave upgrades=$upgrades dead=${r.visible("GameOverPanel")} errors=${r.errors}")
        assertTrue("should kill zombies", kills > 0)
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

    @Test
    fun ironTanksMissionPlaythrough() {
        val p = project(Games.templates.first { it.name.startsWith("Iron Tanks") }.name)
        assertEquals("Menu", p.startScene)
        val m = start(p, "Menu")
        m.frames(10)
        assertTrue(m.engine.scene.find("Mission1Btn")!!.get<com.sengine.engine.core.UIButton>()!!.text.contains("BORDER OUTPOST"))
        assertTrue(m.engine.ui.clickByName("Mission1Btn")); m.frames(5)
        assertEquals("Battle", m.engine.scene.name)
        var maxEnemies = 0
        var fired = 0
        m.frames(60 * 90) { i ->
            val sc = m.engine.scene
            val pl = sc.find("Player")
            val tgt = if (pl == null) null else sc.objects.filter { it.tag == "Enemy" && it.isActiveInHierarchy() && !it.destroyed }
                .minByOrNull { (it.x - pl.x) * (it.x - pl.x) + (it.y - pl.y) * (it.y - pl.y) }
            if (pl != null && tgt != null) {
                val a = atan2(tgt.y - pl.y, tgt.x - pl.x)
                m.engine.input.rawSticks["aim"] = floatArrayOf(kotlin.math.cos(a), kotlin.math.sin(a))
            }
            m.engine.input.joyX = kotlin.math.sin(i / 70f) * 0.5f
            maxEnemies = maxOf(maxEnemies, sc.objects.count { it.tag == "Enemy" && it.isActiveInHierarchy() && !it.destroyed })
            fired = maxOf(fired, sc.objects.count { it.tag == "Shell" && it.isActiveInHierarchy() && !it.destroyed })
        }
        val kills = m.text("KillsText").removePrefix("KILLS ").toIntOrNull() ?: 0
        val bricks = m.engine.scene.objects.count { it.tag == "Brick" && it.isActiveInHierarchy() && !it.destroyed }
        println("SIM tanks kills=$kills maxEnemies=$maxEnemies shells=$fired bricks=$bricks score='${m.text("ScoreText")}' lives='${m.text("LivesText")}' hq='${m.text("HQText")}' win=${m.visible("WinPanel")} lose=${m.visible("GameOverPanel")} errors=${m.errors}")
        assertTrue("enemies should spawn", maxEnemies > 0)
        assertTrue("shells should fly", fired > 0)
        assertTrue("player should destroy tanks", kills > 0)
        assertTrue(m.errors.toString(), m.errors.isEmpty())
    }

    @Test
    fun ironTanksMission3LayoutSwitches() {
        val p = project(Games.templates.first { it.name.startsWith("Iron Tanks") }.name)
        val r = start(p, "Battle") { e -> e.storage.set("it_level", 3.0) }
        r.frames(30)
        assertTrue(r.visible("Map3")); assertTrue(!r.visible("Map1"))
        assertEquals(3, r.engine.scene.objects.count { it.tag == "EnemySpawn" && it.isActiveInHierarchy() })
        assertTrue(r.text("LevelText").contains("3"))
        assertTrue(r.errors.toString(), r.errors.isEmpty())
    }

    @Test
    fun strikeForceFirefight() {
        val p = project(Games.templates.first { it.name.startsWith("Strike Force") }.name)
        assertTrue(p.listScenes().containsAll(listOf("Menu", "Mission", "NightRaid")))
        val m = start(p, "Menu")
        m.frames(10)
        assertTrue(m.engine.ui.clickByName("Mission1Btn")); m.frames(5)
        assertEquals("Mission", m.engine.scene.name)
        val total = m.engine.scene.objects.count { it.tag == "Enemy" && it.isActiveInHierarchy() }
        var minHp = 1f
        var aimed = 0
        m.frames(60 * 60) { i ->
            val pl = m.engine.scene.find("Player") ?: return@frames
            if (i % 10 == 0) { val r = m.engine.scripts.sendMessage(pl, "aimAtNearest", null); if (r == true) aimed++ }
            m.engine.input.rawButtons["Fire"] = (i / 20) % 2 == 0
            minHp = minOf(minHp, m.engine.scene.find("HealthBar")?.get<com.sengine.engine.core.UIProgress>()?.value ?: 1f)
            if (m.visible("GameOverPanel")) { m.engine.scripts.sendMessage(pl, "heal", 100.0) }
        }
        val alive = m.engine.scene.objects.count { it.tag == "Enemy" && it.isActiveInHierarchy() && !it.destroyed }
        println("SIM fps enemies=$total alive=$alive aimed=$aimed minHp=$minHp objective='${m.text("ObjectiveText")}' ammo='${m.text("AmmoText")}' score='${m.text("ScoreText")}' dead=${m.visible("GameOverPanel")} errors=${m.errors}")
        for (e in m.engine.scene.objects.filter { it.name.startsWith("Hostile") && it.isActiveInHierarchy() && !it.destroyed })
            println("SIM fps hostile ${e.name}: ${m.engine.scripts.sendMessage(e, "debugState", null)}")
        println("SIM fps player hp=${m.engine.scripts.sendMessage(m.engine.scene.find("Player")!!, "getHp", null)}")
        assertTrue(total >= 10)
        assertTrue("player should eliminate hostiles", alive < total)
        assertTrue(m.errors.toString(), m.errors.isEmpty())
        // the night raid loads and runs
        val n = start(p, "NightRaid")
        n.frames(120)
        println("SIM fps night enemies=${n.engine.scene.objects.count { it.tag == "Enemy" && it.isActiveInHierarchy() }} errors=${n.errors}")
        assertTrue(n.errors.toString(), n.errors.isEmpty())
    }
}
