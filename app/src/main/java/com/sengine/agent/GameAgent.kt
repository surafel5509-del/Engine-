package com.sengine.agent

import org.json.JSONArray
import org.json.JSONObject

/** Events streamed to the UI while the agent works. */
sealed class AgentEvent {
    class Phase(val name: String) : AgentEvent()
    class Thought(val text: String) : AgentEvent()
    class PlanUpdated(val tasks: List<AgentTools.PlanTask>) : AgentEvent()
    class ToolCall(val tool: String, val args: String) : AgentEvent()
    class ToolOutput(val tool: String, val ok: Boolean, val text: String) : AgentEvent()
    class Error(val text: String) : AgentEvent()
    class Done(val summary: String, val project: String?, val buildRequested: Boolean) : AgentEvent()
}

/**
 * The S Engine game-making agent. Model-agnostic: any [ChatModel] that follows the JSON action
 * protocol can drive it. Loop: think → plan → act with tools → doctor + play-test → build → finish.
 */
class GameAgent(
    private val model: ChatModel,
    val tools: AgentTools,
    private val maxSteps: Int = 60,
    private val onEvent: (AgentEvent) -> Unit = {},
) {
    @Volatile var cancelled = false
    private val history = ArrayList<ChatMessage>()

    fun run(prompt: String): Boolean {
        onEvent(AgentEvent.Phase("Thinking"))
        history += ChatMessage("user", "GAME REQUEST:\n$prompt\n\nStart by thinking about the design, then call `plan` with the full task list, then build the game step by step. Reply ONLY with the JSON action object.")
        var badReplies = 0
        var testedAfterLastEdit = false
        for (step in 1..maxSteps) {
            if (cancelled) { onEvent(AgentEvent.Error("Stopped by user")); return false }
            val reply = try { model.complete(AgentPrompt.system(), compacted()) } catch (e: Exception) {
                onEvent(AgentEvent.Error("Model error: ${e.message}")); return false
            }
            history += ChatMessage("assistant", reply)
            val parsed = AgentProtocol.parse(reply)
            if (parsed == null) {
                badReplies++
                if (badReplies > 3) { onEvent(AgentEvent.Error("The model did not return valid JSON actions.")); return false }
                history += ChatMessage("user", "Your reply was not valid JSON. Reply ONLY with {\"thought\":\"...\",\"actions\":[{\"tool\":\"...\",\"args\":{...}}]}")
                continue
            }
            if (parsed.thought.isNotBlank()) onEvent(AgentEvent.Thought(parsed.thought))
            if (parsed.actions.isEmpty()) {
                history += ChatMessage("user", "No actions given. Continue with the next unfinished task, or call finish when everything is done and tested.\nPLAN:\n${tools.planText()}")
                continue
            }
            val results = StringBuilder()
            for (act in parsed.actions.take(12)) {
                if (cancelled) break
                if (act.tool == "finish" && tools.project != null && !testedAfterLastEdit) {
                    // Self-inspection gate: never finish without a clean doctor + play-test.
                    onEvent(AgentEvent.Phase("Self-inspection"))
                    val doc = exec("run_doctor", JSONObject())
                    val play = exec("play_test", JSONObject().put("seconds", 6))
                    testedAfterLastEdit = true
                    if (!doc.ok || !play.ok) {
                        results.append("finish REFUSED: fix these problems first.\n[run_doctor] ").append(doc.text).append("\n[play_test] ").append(play.text).append('\n')
                        break
                    }
                }
                val r = exec(act.tool, act.args)
                if (act.tool in EDIT_TOOLS) testedAfterLastEdit = false
                if (act.tool == "play_test" && r.ok) testedAfterLastEdit = true
                results.append("[").append(act.tool).append("] ").append(if (r.ok) "OK " else "FAILED ").append(r.text).append('\n')
                if (act.tool == "finish") {
                    onEvent(AgentEvent.Done(tools.finalSummary, tools.project?.name, tools.buildRequested))
                    return true
                }
            }
            val done = tools.plan.count { it.done }
            history += ChatMessage("user", "TOOL RESULTS (step $step/$maxSteps):\n$results\nPLAN ($done/${tools.plan.size} done):\n${tools.planText()}\nContinue.")
        }
        onEvent(AgentEvent.Error("Step limit reached. The project so far is saved${tools.project?.let { " as '${it.name}'" } ?: ""}."))
        return false
    }

    private fun exec(tool: String, args: JSONObject): ToolResult {
        onEvent(AgentEvent.ToolCall(tool, args.toString().let { if (it.length > 300) it.take(300) + "…" else it }))
        when (tool) {
            "create_project" -> onEvent(AgentEvent.Phase("Creating project"))
            "run_doctor", "play_test" -> onEvent(AgentEvent.Phase("Testing"))
            "build_apk" -> onEvent(AgentEvent.Phase("Building"))
        }
        val r = tools.execute(tool, args)
        onEvent(AgentEvent.ToolOutput(tool, r.ok, r.text))
        if (tool == "plan" || tool == "task_done") onEvent(AgentEvent.PlanUpdated(tools.plan.map { AgentTools.PlanTask(it.id, it.title, it.done, it.note) }))
        if (tool == "plan") onEvent(AgentEvent.Phase("Building the game"))
        return r
    }

    /** Keeps the context small: first request + recent turns (roles stay strictly alternating). */
    private fun compacted(): List<ChatMessage> {
        if (history.size <= 24) return history
        var tail = history.takeLast(20)
        if (tail.first().role != "user") tail = tail.drop(1)
        val out = ArrayList<ChatMessage>()
        out += ChatMessage("user", history.first().content + "\n\n(Earlier steps omitted to save space.) Project: ${tools.project?.name ?: "none"}\nPLAN:\n${tools.planText()}")
        out += ChatMessage("assistant", "{\"thought\":\"continuing the plan\",\"actions\":[]}")
        out += tail
        return out
    }

    companion object {
        val EDIT_TOOLS = setOf("write_file", "add_object", "set_props", "add_component", "delete_object", "new_scene", "scene_settings", "create_project", "install_asset")
    }
}

/** JSON action protocol: {"thought": "...", "actions": [{"tool": "name", "args": {...}}]} */
object AgentProtocol {
    class Action(val tool: String, val args: JSONObject)
    class Parsed(val thought: String, val actions: List<Action>)

    fun parse(text: String): Parsed? {
        val json = extractJson(text) ?: return null
        return try {
            val o = JSONObject(json)
            val acts = ArrayList<Action>()
            val arr = o.optJSONArray("actions") ?: o.optJSONObject("action")?.let { JSONArray().put(it) }
                ?: if (o.has("tool")) JSONArray().put(o) else JSONArray()
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                val tool = a.optString("tool", a.optString("name"))
                if (tool.isBlank()) continue
                acts += Action(tool, a.optJSONObject("args") ?: a.optJSONObject("arguments") ?: JSONObject())
            }
            Parsed(o.optString("thought", o.optString("thinking")), acts)
        } catch (_: Exception) { null }
    }

    /** Finds the outermost JSON object, tolerating ```json fences and prose around it. */
    fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0; var inStr = false; var esc = false
        for (i in start until text.length) {
            val c = text[i]
            if (inStr) { if (esc) esc = false else if (c == '\\') esc = true else if (c == '"') inStr = false; continue }
            when (c) { '"' -> inStr = true; '{' -> depth++; '}' -> { depth--; if (depth == 0) return text.substring(start, i + 1) } }
        }
        return null
    }
}

object AgentPrompt {
    @Volatile private var cached: String? = null

    fun system(): String = cached ?: build().also { cached = it }

    private fun build(): String = """
You are the S Engine Game Agent, an expert game designer and developer. You fully control S Engine (a Unity-like 2D/3D engine on Android)
through tools and you build COMPLETE, polished, playable games from a prompt, end to end, without asking the user questions.

WORKFLOW (follow strictly):
1. THINK: decide genre, 2D or 3D, core loop, controls, win/lose, scenes (menu, game, game over), art style, music.
2. PLAN: call `plan` with 8-20 concrete tasks (project, art, player, enemies/obstacles, UI/HUD, menus, audio, scripts, polish, test, build).
3. EXECUTE tasks in order. After each task call `task_done`. Prefer starting from the closest template, then customise heavily.
4. SELF-INSPECT: call `run_doctor` and `play_test` and fix every error you find. Read scripts back if unsure.
5. Call `build_apk`, then `finish` with a summary of the game, controls and features.

REPLY FORMAT — ONLY one JSON object, no markdown:
{"thought":"short reasoning","actions":[{"tool":"name","args":{...}}, ...]}
Up to 12 actions per reply. You receive tool results after every reply.

TOOLS:
${AgentTools.toolReference()}

TEMPLATES (create_project template names):
${AgentTools.templateReference()}

COMPONENTS (add_object components / set_props; prop names are case-insensitive):
${AgentTools.componentReference()}
Mesh kinds for MeshRenderer "Mesh": Cube, Sphere, Plane, Cylinder, Cone, Torus, Capsule, Pyramid, Custom (+ Model=file.smodel).
Rigidbody2D Body Type: Dynamic, Kinematic, Static. Colors are "#AARRGGBB". 2D units: camera Size = half the visible height.
UI objects (UIPanel/UIButton/UIProgress/TextRenderer with Screen Space) live on a canvas 10 units tall centred at 0; Anchor places them at screen edges.
UIButton Action strings (joined by ;): scene:Name, call:functionName, show:Obj, hide:Obj, toggle:Obj, pause, resume, reload, quit.

SCRIPTING (JavaScript, Mozilla Rhino — ES5 only: use var and function(){}, NO arrow functions, classes, let/const, template strings):
Lifecycle: start(), update(dt), onCollision(other), onTrigger(other), onTriggerExit(other), onTap(), onDestroy(), onUIClick(name).
self/transform: x, y, z, rotation, rotX, rotY, scaleX, scaleY, vx, vy, vz, name, tag, active, color, text, destroy(), send(fn,arg),
  distanceTo(o), setProp(Component,Prop,value), getProp(Component,Prop), burst(n), playAnim(name), playModelAnim(name).
input: axisX, axisY, axis2X, axis2Y (aim stick), lookX, lookY, a, b, aDown, bDown, button(id), buttonDown(id), touching, tapped, touchX, touchY,
  setControls(preset), showControls(bool).
scene: find(name), findAll(tag), count(tag), nearest(tag,x,y), spawn(templateName,x,y[,z]), load(sceneName), shake(amount), raycast(ox,oy,oz,dx,dy,dz,max).
ui: setText(name,text), show(name), hide(name), setProgress(name,0..1). audio: play(file.wav,vol,pitch), playMusic(file.song,vol), stopMusic().
storage: get(key,default), getNumber(key,default), set(key,value). time: time, deltaTime. Helpers: log, random(a,b), randomInt, clamp, lerp,
  distance, angleTo, chance(p), pick(arr), after(sec,fn), every(sec,fn), formatTime(s).
Spawning: make an INACTIVE template object (active:false) with its components/script; scene.spawn("Name",x,y) clones it active.
  Spawned clones keep the template's tag — find them with findAll/count/nearest by tag.
Physics: 2D objects need Collider2D (+Rigidbody2D to move). Triggers (Is Trigger) call onTrigger on both objects.
  3D: Collider3D + Rigidbody3D; Camera3D "Follow Target" follows an object. VoxelWorld gives Minecraft-like terrain.

QUALITY BAR: real menus (title, play, help), HUD (score, health, wave/level), game over + restart, sounds, music, textures on everything,
touch controls preset matching the game, difficulty progression, juice (particles, shake). Keep scripts robust (null checks).
""".trimIndent()
}
