package com.sengine

import com.sengine.agent.AgentTools
import com.sengine.engine.Engine
import com.sengine.engine.core.ComponentRegistry
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SceneSerializer
import com.sengine.engine.core.ScriptComponent
import com.sengine.engine.core.Tilemap
import com.sengine.engine.script.GdScriptTranspiler
import com.sengine.engine.script.LuaScriptTranspiler
import com.sengine.project.Project
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EngineV7Test {
    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun tilemapGridAndSerialization() {
        val tm = Tilemap()
        tm.cols = 4
        tm.rows = 3
        tm.setTile(1, 1, 5)
        tm.setTile(2, 2, 8)

        assertEquals(5, tm.getTile(1, 1))
        assertEquals(8, tm.getTile(2, 2))
        assertEquals(0, tm.getTile(0, 0))

        val json = tm.toJson()
        val restored = ComponentRegistry.create("Tilemap") as Tilemap
        restored.fromJson(json)

        assertEquals(4, restored.cols)
        assertEquals(3, restored.rows)
        assertEquals(5, restored.getTile(1, 1))
        assertEquals(8, restored.getTile(2, 2))
    }

    @Test
    fun layersAndGroupsQueryAndSerialization() {
        val scene = Scene("Main")
        val go1 = scene.create("Enemy1")
        go1.layer = 2
        go1.group = "enemies"

        val go2 = scene.create("Enemy2")
        go2.layer = 2
        go2.group = "enemies"

        val go3 = scene.create("Player")
        go3.layer = 1
        go3.group = "player"

        val inEnemies = scene.findInGroup("enemies")
        assertEquals(2, inEnemies.size)

        val inLayer2 = scene.findInLayer(2)
        assertEquals(2, inLayer2.size)

        val sceneJson = SceneSerializer.toJson(scene)
        val loaded = SceneSerializer.fromJson(sceneJson)

        val loadedPlayer = loaded.find("Player")
        assertNotNull(loadedPlayer)
        assertEquals(1, loadedPlayer!!.layer)
        assertEquals("player", loadedPlayer.group)
    }

    @Test
    fun gdScriptTranspilationAndExecution() {
        val dir = tempDir.newFolder("proj_gd")
        val proj = Project(dir).apply { saveMeta(); com.sengine.project.Templates.all.first { it.name.startsWith("Empty") }.build(this); saveMeta() }
        val gdCode = """
extends Node2D

var speed = 10

func _ready():
    print("GDScript ready")

func _process(delta):
    self.x = self.x + speed * delta
"""
        val jsCode = GdScriptTranspiler.transpile(gdCode)
        assertTrue(jsCode.contains("function start()"))
        assertTrue(jsCode.contains("function update(dt)"))

        proj.writeAsset("PlayerController.gd", gdCode)

        val scene = Scene("Main")
        val player = scene.create("Player")
        val sc = ScriptComponent().apply { script = "PlayerController.gd" }
        player.add(sc)

        val engine = Engine(proj, scene)
        engine.play()
        engine.tick(0.1f)

        assertTrue(player.x > 0f)
        engine.stop()
    }

    @Test
    fun luaTranspilationAndExecution() {
        val dir = tempDir.newFolder("proj_lua")
        val proj = Project(dir).apply { saveMeta(); com.sengine.project.Templates.all.first { it.name.startsWith("Empty") }.build(this); saveMeta() }
        val luaCode = """
local speed = 20

function start()
    print("Lua ready")
end

function update(dt)
    self.y = self.y + speed * dt
end
"""
        val jsCode = LuaScriptTranspiler.transpile(luaCode)
        println("LUA TRANSPILED JS:\n$jsCode")
        assertTrue(jsCode.contains("function start()"))
        assertTrue(jsCode.contains("function update(dt)"))

        proj.writeAsset("Mover.lua", luaCode)

        val scene = Scene("Main")
        val obj = scene.create("MoverObj")
        val sc = ScriptComponent().apply { script = "Mover.lua" }
        obj.add(sc)

        val engine = Engine(proj, scene)
        engine.play()
        engine.tick(0.1f)

        assertTrue(obj.y > 0f)
        engine.stop()
    }

    @Test
    fun aiAgentSearchExplainAndFixCode() {
        val dir = tempDir.newFolder("proj_agent")
        val proj = Project(dir).apply { saveMeta(); com.sengine.project.Templates.all.first { it.name.startsWith("Empty") }.build(this); saveMeta() }
        proj.writeAsset("bad.js", "const speed = 10;\nlet y = 5;")

        val host = object : com.sengine.agent.AgentHost {
            override fun createProject(name: String, template: com.sengine.project.Templates.Template): Project = proj
            override fun openProject(name: String): Project? = proj
        }

        val tools = AgentTools(host)
        tools.execute("open_project", JSONObject().put("name", "Test"))

        val searchRes = tools.execute("search_project", JSONObject().put("query", "speed"))
        assertTrue(searchRes.ok)
        assertTrue(searchRes.text.contains("bad.js"))

        val explainRes = tools.execute("explain_error", JSONObject().put("error", "ReferenceError: foo is not defined"))
        assertTrue(explainRes.ok)

        val fixRes = tools.execute("fix_code", JSONObject().put("path", "bad.js"))
        assertTrue(fixRes.ok)
        val fixedContent = proj.readAsset("bad.js") ?: ""
        assertTrue(fixedContent.contains("var speed"))
    }
}
