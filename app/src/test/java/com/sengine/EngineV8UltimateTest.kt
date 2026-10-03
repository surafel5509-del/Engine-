package com.sengine

import com.sengine.project.AssetLibrary
import com.sengine.project.Project
import com.sengine.project.Templates
import com.sengine.project.games.CyberRunnerGame
import com.sengine.project.games.DungeonCrawlerGame
import com.sengine.project.games.ApexDriftGame
import com.sengine.project.games.GalaxyDefenderGame
import com.sengine.project.games.VoxelSurvivorGame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EngineV8UltimateTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private lateinit var rootDir: File

    @Before
    fun setUp() {
        rootDir = tempDir.newFolder("v8_test_projects")
    }

    @Test
    fun testAssetLibraryPacksExpanded() {
        val categories = AssetLibrary.categories
        assertTrue("Categories should contain Packs", categories.contains("Packs"))
        val items = AssetLibrary.items
        assertTrue("AssetLibrary should have multiple items", items.size > 20)

        val packTitles = items.filter { it.category == "Packs" }.map { it.title }
        assertTrue("Cyberpunk Pack should be in AssetLibrary", packTitles.contains("Cyberpunk Pack"))
        assertTrue("Dungeon RPG Pack should be in AssetLibrary", packTitles.contains("Dungeon RPG Pack"))
        assertTrue("Bullet Hell Pack should be in AssetLibrary", packTitles.contains("Bullet Hell Pack"))
    }

    @Test
    fun testSampleGamesRegistrationAndGeneration() {
        val templates = Templates.all
        val templateNames = templates.map { it.name }

        assertTrue("Cyber Runner should be registered", templateNames.any { it.contains("Cyber Runner") })
        assertTrue("Dungeon Crawler should be registered", templateNames.any { it.contains("Dungeon Crawler") })
        assertTrue("Apex Drift should be registered", templateNames.any { it.contains("Apex Drift") })
        assertTrue("Galaxy Defender should be registered", templateNames.any { it.contains("Galaxy Defender") })
        assertTrue("Voxel Survivor should be registered", templateNames.any { it.contains("Voxel Survivor") })

        val projDir = File(rootDir, "CyberRunnerTest").apply { mkdirs() }
        val proj = Project(projDir).apply { saveMeta() }
        CyberRunnerGame.build(proj)
        assertTrue("Start scene should exist", proj.sceneExists(proj.startScene))
        val scene = proj.loadScene(proj.startScene)
        assertNotNull("Scene should load", scene)
        assertTrue("Scene should have objects", scene.objects.size > 0)
    }

    @Test
    fun testDungeonAndApexGamesGeneration() {
        val p1Dir = File(rootDir, "DungeonTest").apply { mkdirs() }
        val p1 = Project(p1Dir).apply { saveMeta() }
        DungeonCrawlerGame.build(p1)
        assertTrue(p1.sceneExists(p1.startScene))

        val p2Dir = File(rootDir, "ApexTest").apply { mkdirs() }
        val p2 = Project(p2Dir).apply { saveMeta() }
        ApexDriftGame.build(p2)
        assertTrue(p2.sceneExists(p2.startScene))

        val p3Dir = File(rootDir, "GalaxyTest").apply { mkdirs() }
        val p3 = Project(p3Dir).apply { saveMeta() }
        GalaxyDefenderGame.build(p3)
        assertTrue(p3.sceneExists(p3.startScene))

        val p4Dir = File(rootDir, "VoxelTest").apply { mkdirs() }
        val p4 = Project(p4Dir).apply { saveMeta() }
        VoxelSurvivorGame.build(p4)
        assertTrue(p4.sceneExists(p4.startScene))
    }
}
