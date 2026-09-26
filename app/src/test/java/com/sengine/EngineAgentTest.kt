package com.sengine

import com.sengine.agent.AgentEvent
import com.sengine.agent.AgentHost
import com.sengine.agent.AgentProtocol
import com.sengine.agent.AgentTools
import com.sengine.agent.ChatMessage
import com.sengine.agent.ChatModel
import com.sengine.agent.GameAgent
import com.sengine.agent.LocalPlanner
import com.sengine.engine.texture.PngEncoder
import com.sengine.engine.texture.TextureGen
import com.sengine.project.GameDoctor
import com.sengine.project.Project
import com.sengine.project.Templates
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class EngineAgentTest {
    private val root: File = Files.createTempDirectory("agent").toFile()

    private val host = object : AgentHost {
        override fun createProject(name: String, template: Templates.Template): Project =
            Project(File(root, name)).also { it.saveMeta(); template.build(it); it.saveMeta() }
        override fun openProject(name: String): Project? = File(root, name).takeIf { it.exists() }?.let { Project(it) }
    }

    @Test
    fun protocolParsesFencedAndBareJson() {
        val p = AgentProtocol.parse("Sure!\n```json\n{\"thought\":\"x {y}\",\"actions\":[{\"tool\":\"plan\",\"args\":{\"tasks\":[\"a\",\"b\"]}}]}\n```")
        assertNotNull(p); assertEquals("plan", p!!.actions[0].tool); assertEquals("x {y}", p.thought)
        val single = AgentProtocol.parse("{\"tool\":\"run_doctor\",\"args\":{}}")
        assertEquals("run_doctor", single!!.actions[0].tool)
        assertEquals(null, AgentProtocol.parse("no json here"))
    }

    @Test
    fun textureGeneratorAllStylesAndPngRoundTrip() {
        for (st in TextureGen.STYLES) {
            val d = TextureGen.defaults(st)
            val px = TextureGen.generate(TextureGen.Params(st, 32, d.first, d.second, 4f, 3))
            assertEquals(32 * 32, px.size)
            assertTrue("$st should not be flat", px.toSet().size > 1)
        }
        val px = TextureGen.generate(TextureGen.Params("Bricks", 64))
        val png = PngEncoder.encode(64, 64, px)
        val back = PngEncoder.decode(png)!!
        assertEquals(64, back.first); assertArrayEquals(px, back.third)
        val nm = TextureGen.normalMap(px, 64)
        assertEquals(64 * 64, nm.size)
        println("SIM texture styles=${TextureGen.STYLES.size} png=${png.size} bytes")
    }

    @Test
    fun referencesAreGenerated() {
        val prompt = com.sengine.agent.AgentPrompt.system()
        assertTrue(prompt.contains("SpriteRenderer")); assertTrue(prompt.contains("play_test")); assertTrue(prompt.contains("Sky Strike"))
        println("SIM agent system prompt ${prompt.length} chars, ${AgentTools.TOOLS.size} tools")
    }

    @Test
    fun offlinePlannerBuildsAGameEndToEnd() {
        val events = ArrayList<AgentEvent>()
        val tools = AgentTools(host)
        val agent = GameAgent(LocalPlanner(), tools, 20) { events += it }
        val ok = agent.run("Make a zombie survival game called Night Siege with red blood and shotguns")
        events.filterIsInstance<AgentEvent.Error>().forEach { println("SIM agent error: ${it.text}") }
        events.filterIsInstance<AgentEvent.ToolOutput>().filter { !it.ok }.forEach { println("SIM agent failed tool ${it.tool}: ${it.text.take(400)}") }
        assertTrue("agent should finish", ok)
        val p = tools.project!!
        assertEquals("Night Siege", p.name)
        assertTrue(p.assetFile("AI Theme.song").exists())
        assertTrue(p.listAssets().any { it.startsWith("AI_") && it.endsWith(".png") })
        assertTrue(tools.plan.all { it.done })
        assertTrue(tools.buildRequested)
        val errors = GameDoctor.check(p).filter { it.severity >= 2 && !it.title.startsWith("Missing texture") }
        assertTrue(errors.joinToString { it.title }, errors.isEmpty())
        println("SIM agent offline: project=${p.name} steps=${events.count { it is AgentEvent.ToolCall }} plan=${tools.plan.size}")
    }

    /** A scripted "LLM" that builds a small game from scratch using only primitive tools. */
    private class ScriptedModel : ChatModel {
        override val label = "scripted"
        var turn = 0
        override fun complete(system: String, messages: List<ChatMessage>): String {
            turn++
            fun act(tool: String, args: JSONObject) = JSONObject().put("tool", tool).put("args", args)
            val acts = JSONArray()
            when (turn) {
                1 -> acts.put(act("plan", JSONObject().put("tasks", JSONArray(listOf("Project", "Scene", "Player", "Coins", "HUD", "Test")))))
                2 -> {
                    acts.put(act("create_project", JSONObject().put("name", "Coin Dash").put("template", "Empty")))
                    acts.put(act("new_scene", JSONObject().put("name", "Level").put("kind", "2d").put("gravity", 0).put("start", true)))
                    acts.put(act("generate_texture", JSONObject().put("name", "Floor").put("style", "Tiles")))
                    acts.put(act("generate_sprite", JSONObject().put("name", "Hero").put("shape", "character").put("color", "#FF3B82F6")))
                    acts.put(act("generate_sprite", JSONObject().put("name", "Coin").put("shape", "coin").put("color", "gold")))
                    acts.put(act("compose_song", JSONObject().put("name", "Theme").put("style", "Chiptune")))
                }
                3 -> {
                    acts.put(act("write_file", JSONObject().put("path", "Hero.js").put("content",
                        "var speed = 6, score = 0;\nfunction start() { log('hero ready'); }\nfunction update(dt) { self.vx = input.axisX * speed; self.vy = input.axisY * speed; }\n" +
                            "function onTrigger(other) { if (other.tag == 'Coin') { score++; ui.setText('Score', 'Coins ' + score); other.destroy(); } }")))
                    acts.put(act("write_file", JSONObject().put("path", "Spawner.js").put("content",
                        "function start() { every(0.5, function () { if (scene.count('Coin') < 12) scene.spawn('Coin', random(-3, 3), random(-2, 2)); }); }")))
                    acts.put(act("add_object", JSONObject().put("scene", "Level").put("name", "Floor").put("sx", 20).put("sy", 12).put("order", -10)
                        .put("components", JSONObject().put("SpriteRenderer", JSONObject().put("Texture", "Floor.png")))))
                    acts.put(act("add_object", JSONObject().put("scene", "Level").put("name", "Hero").put("tag", "Player")
                        .put("components", JSONObject()
                            .put("SpriteRenderer", JSONObject().put("Texture", "Hero.png"))
                            .put("Collider2D", JSONObject().put("Shape", "Circle").put("Radius", 0.45))
                            .put("Rigidbody2D", JSONObject().put("Body Type", "Dynamic").put("Gravity Scale", 0))
                            .put("Script", JSONObject().put("Script", "Hero.js")))))
                    acts.put(act("add_object", JSONObject().put("scene", "Level").put("name", "Coin").put("tag", "Coin").put("active", false).put("scale", 0.6)
                        .put("components", JSONObject().put("SpriteRenderer", JSONObject().put("Texture", "Coin.png")).put("Collider2D", JSONObject().put("Shape", "Circle").put("Is Trigger", true)))))
                    acts.put(act("add_object", JSONObject().put("scene", "Level").put("name", "Director").put("components", JSONObject().put("Script", JSONObject().put("Script", "Spawner.js")))))
                    acts.put(act("add_object", JSONObject().put("scene", "Level").put("name", "Score")
                        .put("components", JSONObject().put("TextRenderer", JSONObject().put("Text", "Coins 0").put("Screen Space", true).put("Size", 0.5)))))
                    acts.put(act("set_props", JSONObject().put("scene", "Level").put("object", "Main Camera").put("component", "Camera").put("props", JSONObject().put("Follow Target", "Hero"))))
                    acts.put(act("set_controls", JSONObject().put("preset", "Classic")))
                }
                4 -> { acts.put(act("finish", JSONObject().put("summary", "too early"))) } // gate runs doctor + play-test first
                5 -> {
                    acts.put(act("inspect_scene", JSONObject().put("scene", "Level")))
                    acts.put(act("play_test", JSONObject().put("seconds", 6)))
                    for (i in 1..6) acts.put(act("task_done", JSONObject().put("id", i)))
                }
                else -> acts.put(act("finish", JSONObject().put("summary", "Coin Dash: collect coins.")))
            }
            return JSONObject().put("thought", "turn $turn").put("actions", acts).toString()
        }
    }

    @Test
    fun scriptedModelBuildsGameFromScratch() {
        val events = ArrayList<AgentEvent>()
        val tools = AgentTools(host)
        val ok = GameAgent(ScriptedModel(), tools, 12) { events += it }.run("coin game")
        events.filterIsInstance<AgentEvent.ToolOutput>().forEach { println("SIM agent ${if (it.ok) "ok" else "FAIL"} ${it.tool}: ${it.text.take(300).replace('\n', ' ')}") }
        assertTrue(ok)
        val p = tools.project!!
        assertEquals("Level", p.startScene)
        val s = p.loadScene("Level")
        val hero = s.objects.first { it.name == "Hero" }
        println("SIM agent hero components=${hero.components.map { it.type }}")
        assertTrue(hero.components.map { it.type }.containsAll(listOf("Script", "Collider2D", "Rigidbody2D", "SpriteRenderer")))
        val play = events.filterIsInstance<AgentEvent.ToolOutput>().last { it.tool == "play_test" }
        assertTrue(play.text, play.ok)
        assertTrue("script log should appear", play.text.contains("hero ready"))
        assertTrue(tools.execute("write_file", JSONObject().put("path", "Bad.js").put("content", "function f( { var x = ; }")).text.contains("SYNTAX"))
    }
}
