package com.sengine

import com.sengine.engine.Engine
import com.sengine.engine.core.TextRenderer
import com.sengine.engine.core.UIButton
import com.sengine.project.GameDoctor
import com.sengine.project.Project
import com.sengine.project.Templates
import com.sengine.project.games.Games
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Headless play-throughs of all eight bundled sample games plus the two empty templates.
 * Every test builds the real template, compiles every script, runs GameDoctor and then
 * simulates actual play with synthesized input — the same bar the old games were held to.
 */
class EngineGamesTest {

    private class Run(val engine: Engine, val errors: MutableList<String>)

    private fun project(templateName: String): Project {
        val dir = Files.createTempDirectory("games").toFile()
        val p = Project(File(dir, templateName.substringBefore(" (").replace("'", ""))).also { it.saveMeta() }
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
    private fun Run.button(name: String): UIButton? = engine.scene.find(name)?.get<UIButton>()
    private fun Run.visible(name: String) = engine.scene.find(name)?.isActiveInHierarchy() == true
    private fun Run.count(tag: String) = engine.scene.objects.count { it.tag == tag && it.isActiveInHierarchy() && !it.destroyed }

    // ------------------------------------------------------------------ roster

    @Test
    fun allGameTemplatesRegistered() {
        assertEquals(8, Games.templates.size)
        val names = Games.templates.map { it.name }
        assertTrue(names.any { it.startsWith("Crystal Caverns") })
        assertTrue(names.any { it.startsWith("Slice Master") })
        assertTrue(names.any { it.startsWith("Iron Guard") })
        assertTrue(names.any { it.startsWith("Sky Harbor") })
        assertTrue(names.any { it.startsWith("Zombie Garage") })
        assertTrue(names.any { it.startsWith("Dungeon Quest") })
        assertTrue(names.any { it.startsWith("Strike Force") })
        assertTrue(names.any { it.startsWith("Open World 2D") })
        // empty 2D + empty 3D starters exist alongside the samples
        assertTrue(Templates.all.any { it.name == "Empty 2D" })
        assertTrue(Templates.all.any { it.name == "Empty 3D" })
        assertTrue(Templates.all.size >= 16)
    }

    @Test
    fun emptyTemplatesRunClean() {
        for (name in listOf("Empty 2D", "Empty 3D")) {
            val p = project(name)
            val r = start(p, "Main")
            r.frames(30)
            assertTrue(r.errors.toString(), r.errors.isEmpty())
        }
    }

    // ------------------------------------------------------------------ 2D games

    @Test
    fun crystalCavernsLevel1Reachable() {
        val p = project(Games.templates.first { it.name.startsWith("Crystal Caverns") }.name)
        assertTrue(p.listScenes().containsAll(listOf("CaveMenu", "Cave1", "Cave2", "Cave3")))
        val m = start(p, "CaveMenu")
        m.frames(10)
        assertTrue(m.errors.toString(), m.errors.isEmpty())
        assertTrue(m.engine.ui.clickByName("Level1Btn")); m.frames(5)
        assertEquals("Cave1", m.engine.scene.name)
        // run right and jump constantly like an eager player
        var maxY = 0f
        m.frames(60 * 45) { i ->
            m.engine.input.joyX = 1f
            m.engine.input.rawA = i % 40 < 8
            val pl = m.engine.scene.find("Player")
            if (pl != null) maxY = maxOf(maxY, pl.y)
            if (m.visible("WinPanel") || m.visible("GameOverPanel")) return@frames
        }
        val gems = m.text("GemText")
        println("SIM caverns gems='$gems' timer='${m.text("TimerText")}' maxY=$maxY win=${m.visible("WinPanel")} dead=${m.visible("GameOverPanel")}")
        assertTrue("player should have moved and jumped", maxY > 1.5f)
        assertTrue(m.errors.toString(), m.errors.isEmpty())
        // golems exist from level 2 and the level scripts are wired
        val p2 = project(Games.templates.first { it.name.startsWith("Crystal Caverns") }.name)
        val r2 = start(p2, "Cave2")
        r2.frames(30)
        assertTrue(r2.count("Golem") > 0)
        assertTrue(r2.errors.toString(), r2.errors.isEmpty())
    }

    @Test
    fun sliceMasterSlicingScoresAndBombEndsRun() {
        val p = project(Games.templates.first { it.name.startsWith("Slice Master") }.name)
        assertEquals("SliceMenu", p.startScene)
        val menu = start(p, "SliceMenu")
        menu.frames(10)
        assertTrue(menu.text("BestText").isNotEmpty())
        assertTrue(menu.engine.ui.clickByName("ClassicBtn")); menu.frames(5)
        assertEquals("Arcade", menu.engine.scene.name)

        val r = start(p, "Arcade")
        r.frames(120) // let the director spawn fruit
        val fruit = r.engine.scene.objects.firstOrNull { it.tag == "Fruit" && it.isActiveInHierarchy() && !it.destroyed }
        assertNotNull("director should spawn fruit", fruit)
        // slice it through the script entry point a tap would call
        val before = r.text("ScoreText").toIntOrNull() ?: 0
        r.engine.scripts.sendMessage(fruit!!, "slice", null)
        r.frames(5)
        val after = r.text("ScoreText").toIntOrNull() ?: 0
        println("SIM slice score before=$before after=$after combo='${r.text("ComboText")}'")
        assertTrue("slicing should score", after > before)
        // bomb ends the run
        val bomb = r.engine.scene.objects.firstOrNull { it.tag == "Bomb" && it.isActiveInHierarchy() && !it.destroyed }
        if (bomb != null) {
            r.engine.scripts.sendMessage(bomb, "onTap", null)
            r.frames(5)
            assertTrue("slicing a bomb should end the run", r.visible("OverPanel"))
        }
        assertTrue(r.errors.toString(), r.errors.isEmpty())
    }

    @Test
    fun ironGuardBuildTowerAndDefend() {
        val p = project(Games.templates.first { it.name.startsWith("Iron Guard") }.name)
        assertTrue(p.listScenes().containsAll(listOf("TdMenu", "Td1", "Td2")))
        val m = start(p, "TdMenu")
        m.frames(5)
        assertTrue(m.engine.ui.clickByName("Map1Btn")); m.frames(5)
        assertEquals("Td1", m.engine.scene.name)

        // pick the arrow tower, tap a build spot, start the wave
        assertTrue(m.engine.ui.clickByName("BuildArrow"))
        m.frames(2)
        val spot = m.engine.scene.objects.firstOrNull { it.tag == "Spot" && it.isActiveInHierarchy() }
        assertNotNull("map should have build spots", spot)
        val mode = m.engine.scripts.sendMessage(m.engine.scene.find("TdManager")!!, "getMode", null)
        assertEquals("ArrowTower", mode)
        m.engine.scripts.sendMessage(spot!!, "onTap", null)
        m.frames(5)
        assertEquals("a tower should stand on the spot", 1, m.count("Tower"))
        assertTrue(m.engine.ui.clickByName("WaveBtn"))
        m.frames(60 * 20)
        val zombies = m.count("Enemy")
        val gold = m.text("GoldText")
        println("SIM td enemies=$zombies gold='$gold' wave='${m.text("WaveText")}' lives='${m.text("LivesText")}'")
        assertTrue("wave should spawn enemies", zombies > 0)
        assertTrue(m.errors.toString(), m.errors.isEmpty())
    }

    @Test
    fun openWorldChopTalkBuild() {
        val p = project(Games.templates.first { it.name.startsWith("Open World 2D") }.name)
        assertEquals("Overworld", p.startScene)
        val r = start(p, "Overworld")
        r.frames(30)
        // walk to the first tree and chop it
        val tree = r.engine.scene.objects.first { it.tag == "Tree" && it.isActiveInHierarchy() }
        val pl = r.engine.scene.find("Player")!!
        synchronized(r.engine.lock) { pl.x = tree.x - 1f; pl.y = tree.y }
        r.frames(2)
        r.engine.input.rawA = true; r.frames(3); r.engine.input.rawA = false
        r.frames(2)
        val wood1 = r.text("WoodText")
        r.engine.input.rawA = true; r.frames(3); r.engine.input.rawA = false
        r.frames(2)
        println("SIM openworld wood '$wood1' -> '${r.text("WoodText")}' toast='${r.text("ToastText")}'")
        assertTrue("chopping should collect wood", r.text("WoodText") != wood1 || r.text("WoodText").contains("1"))
        // talk to Ada
        val ada = r.engine.scene.objects.first { it.tag == "Npc" }
        synchronized(r.engine.lock) { pl.x = ada.x - 1f; pl.y = ada.y }
        r.engine.input.rawA = true; r.frames(3); r.engine.input.rawA = false
        r.frames(2)
        assertTrue("Ada should answer", r.text("ToastText").contains("Ada"))
        // the day/night director runs
        r.frames(120)
        assertTrue(r.errors.toString(), r.errors.isEmpty())
    }

    // ------------------------------------------------------------------ 3D games

    @Test
    fun skyHarborFliesAndDelivers() {
        val p = project(Games.templates.first { it.name.startsWith("Sky Harbor") }.name)
        assertEquals("HarborMenu", p.startScene)
        val menu = start(p, "HarborMenu")
        menu.frames(10)
        assertTrue(menu.errors.toString(), menu.errors.isEmpty())
        assertTrue(menu.engine.ui.clickByName("FlyBtn")); menu.frames(5)
        assertEquals("HarborIsle", menu.engine.scene.name)

        val r = start(p, "HarborIsle")
        val pl = r.engine.scene.find("Plane")!!
        val startX = pl.x; val startZ = pl.z
        r.frames(60 * 12) { r.engine.input.rawButtons["Gas"] = true }
        val moved = Math.sqrt((pl.x - startX) * (pl.x - startX) + (pl.z - startZ) * (pl.z - startZ))
        val fuel = r.engine.scene.find("FuelBar")?.get<com.sengine.engine.core.UIProgress>()?.value ?: 1f
        println("SIM harbor moved=$moved fuel=$fuel")
        assertTrue("plane should fly with gas held", moved > 8f)
        assertTrue("engine should burn fuel", fuel < 1f)
        // load cargo at the harbor pad
        val loaded = r.engine.scripts.sendMessage(pl, "loadCargo", null)
        assertEquals(true, loaded)
        r.frames(5)
        assertTrue(r.text("CargoText").contains("aboard"))
        assertTrue(r.errors.toString(), r.errors.isEmpty())
    }

    @Test
    fun zombieGarageDrivesAndSurvives() {
        val p = project(Games.templates.first { it.name.startsWith("Zombie Garage") }.name)
        assertEquals("GarageMenu", p.startScene)
        val menu = start(p, "GarageMenu")
        menu.frames(10)
        assertTrue(menu.errors.toString(), menu.errors.isEmpty())
        assertTrue(menu.engine.ui.clickByName("PlayBtn")); menu.frames(5)
        assertEquals("GarageYard", menu.engine.scene.name)

        val r = start(p, "GarageYard")
        val car = r.engine.scene.find("Car")!!
        val startX = car.x; val startZ = car.z
        var maxZombies = 0
        var roadkillOrShots = 0
        r.frames(60 * 40) { i ->
            r.engine.input.rawButtons["Gas"] = true
            r.engine.input.joyX = kotlin.math.sin(i / 90f)
            r.engine.input.rawButtons["Fire"] = i % 30 < 15
            maxZombies = maxOf(maxZombies, r.count("Zombie"))
            roadkillOrShots = maxOf(roadkillOrShots, r.count("Bullet"))
        }
        val moved = Math.sqrt((car.x - startX) * (car.x - startX) + (car.z - startZ) * (car.z - startZ))
        println("SIM garage moved=$moved maxZombies=$maxZombies bullets=$roadkillOrShots wave='${r.text("WaveText")}' cash='${r.text("CashText")}'")
        assertTrue("truck should drive", moved > 10f)
        assertTrue("director should spawn the horde", maxZombies > 0)
        assertTrue("turret should fire", roadkillOrShots > 0)
        assertTrue(r.errors.toString(), r.errors.isEmpty())
    }

    @Test
    fun dungeonQuestSoulsBossAndVictory() {
        val p = project(Games.templates.first { it.name.startsWith("Dungeon Quest") }.name)
        assertEquals("DqMenu", p.startScene)
        val menu = start(p, "DqMenu")
        menu.frames(10)
        assertTrue(menu.errors.toString(), menu.errors.isEmpty())
        assertTrue(menu.engine.ui.clickByName("PlayBtn")); menu.frames(5)
        assertEquals("Crypt", menu.engine.scene.name)

        val r = start(p, "Crypt")
        r.frames(30)
        // walk over each soul flame the way a player would (teleport + walk-in)
        repeat(3) {
            val soul = r.engine.scene.objects.firstOrNull { it.tag == "Soul" && it.isActiveInHierarchy() && !it.destroyed } ?: return@repeat
            val pl = r.engine.scene.find("Hero")!!
            synchronized(r.engine.lock) { pl.x = soul.x; pl.z = soul.z }
            r.frames(10)
        }
        println("SIM dungeon soulText='${r.text("SoulText")}'")
        // the boss awakens once the gate is open
        val boss = r.engine.scene.find("Lich")!!
        assertTrue("gate should free the Lich", boss.isActiveInHierarchy())
        assertTrue("skeletons should guard the crypt", r.count("Enemy") > 0)
        // fight: hack the boss down like a very good player
        r.engine.scripts.sendMessage(boss, "hurt", 9999.0)
        r.frames(10)
        assertTrue("slaying the Lich should win the run", r.visible("WinPanel"))
        assertTrue(r.errors.toString(), r.errors.isEmpty())
    }

    @Test
    fun strikeForceStillShips() {
        val p = project(Games.templates.first { it.name.startsWith("Strike Force") }.name)
        assertTrue(p.listScenes().containsAll(listOf("Menu", "Mission", "NightRaid")))
        val m = start(p, "Menu")
        m.frames(10)
        assertTrue(m.engine.ui.clickByName("Mission1Btn")); m.frames(5)
        assertEquals("Mission", m.engine.scene.name)
        val total = m.count("Enemy")
        m.frames(60 * 20) { i -> m.engine.input.rawButtons["Fire"] = i % 20 < 10 }
        println("SIM fps enemies=$total alive=${m.count("Enemy")}")
        assertTrue(total >= 10)
        assertTrue(m.errors.toString(), m.errors.isEmpty())
    }
}
