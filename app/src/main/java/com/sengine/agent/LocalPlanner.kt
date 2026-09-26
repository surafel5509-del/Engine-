package com.sengine.agent

import com.sengine.project.Templates
import org.json.JSONArray
import org.json.JSONObject

/**
 * Offline "model" that follows the agent protocol with deterministic rules. It picks the closest
 * complete game template for the prompt, re-skins it with generated textures and music, then
 * self-tests and requests a build. Works with no API key; LLM providers produce far richer games.
 */
class LocalPlanner : ChatModel {
    override val label = "S Engine Offline Planner"

    class Genre(val keys: List<String>, val template: String, val song: String, val controls: String, val textures: List<Pair<String, String>>, val tasks: List<String>)

    private val genres = listOf(
        Genre(listOf("tank", "armor", "artillery"), "Iron Tanks", "Action Battle", "Twin-Stick", listOf("Ground" to "Camo", "Walls" to "Concrete"),
            listOf("Top-down tank combat with turret aiming", "Enemy tanks with AI and waves")),
        Genre(listOf("fps", "call of duty", "first person", "first-person", "3d shooter", "soldier", "gun", "sniper", "war"), "Strike Force", "Action Battle", "First Person",
            listOf("Ground" to "Asphalt", "Walls" to "Concrete"), listOf("First-person shooting with weapons and reload", "Enemy soldier AI and missions")),
        Genre(listOf("zombie", "survival", "horror", "undead"), "Dead Zone", "Spooky Night", "Twin-Stick", listOf("Ground" to "Dirt", "Walls" to "Concrete"),
            listOf("Twin-stick survival combat", "Zombie waves and upgrades")),
        Genre(listOf("car", "race", "racing", "drift", "kart", "drive", "rally"), "Turbo Rally", "Racing Rush", "Racing", listOf("Road" to "Asphalt", "Grass" to "Grass"),
            listOf("Car physics with nitro", "AI opponents, laps and results")),
        Genre(listOf("craft", "minecraft", "voxel", "block", "build", "mine", "sandbox 3d"), "MiniCraft", "Block World", "First Person/Builder",
            listOf("Stone" to "Cobblestone", "Planks" to "Planks"), listOf("Voxel world digging and building", "Day/night and inventory")),
        Genre(listOf("space", "shooter", "shmup", "plane", "jet", "sky", "alien"), "Sky Strike", "Action Battle", "Action", listOf("Space" to "Clouds", "Metal" to "Metal"),
            listOf("Vertical shooter with power-ups", "Enemy waves and boss")),
        Genre(listOf("platform", "jump", "mario", "runner", "adventure"), "Animated Platformer", "Chiptune Adventure", "Platformer", listOf("Ground" to "Grass", "Bricks" to "Bricks"),
            listOf("Platforming with animated hero", "Coins, hazards and goal")),
        Genre(listOf("3d", "world", "explore"), "3D Demo", "Menu Theme", "First Person", listOf("Ground" to "Grass", "Stone" to "Stone"),
            listOf("3D world with lighting", "Player movement and camera")),
        Genre(listOf("physics", "puzzle", "ball"), "Sandbox", "Chill Lo-Fi", "Touch Only", listOf("Wood" to "Wood", "Metal" to "Metal"),
            listOf("Physics playground", "Spawning objects")),
    )

    private fun genreFor(prompt: String): Genre {
        val p = prompt.lowercase()
        return genres.maxByOrNull { g -> g.keys.count { p.contains(it) } }?.takeIf { g -> g.keys.any { p.contains(it) } } ?: genres[6]
    }

    private fun nameFor(prompt: String): String {
        Regex("(?:called|named|titled)\\s+\"?([A-Za-z0-9 ]{2,30})\"?", RegexOption.IGNORE_CASE).find(prompt)?.let { return it.groupValues[1].trim() }
        val words = prompt.replace(Regex("[^A-Za-z0-9 ]"), " ").split(' ').filter { it.length > 2 && it.lowercase() !in STOP }.take(3)
        return words.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }.ifBlank { "AI Game" }
    }

    override fun complete(system: String, messages: List<ChatMessage>): String {
        val prompt = messages.first().content.substringAfter("GAME REQUEST:").substringBefore("\n\nStart by").trim()
        val step = messages.count { it.role == "assistant" }
        val g = genreFor(prompt)
        val tpl = Templates.all.firstOrNull { it.name.equals(g.template, true) }?.name ?: Templates.all.first { it.name == "Platformer" }.name
        fun reply(thought: String, vararg actions: JSONObject) =
            JSONObject().put("thought", thought).put("actions", JSONArray().also { a -> actions.forEach { a.put(it) } }).toString()
        fun act(tool: String, args: JSONObject = JSONObject()) = JSONObject().put("tool", tool).put("args", args)
        val colors = extractColors(prompt)
        return when (step) {
            0 -> reply("The request is a ${g.template}-style game. I will start from the complete '$tpl' template, re-skin it to match the prompt, then self-test and build.",
                act("plan", JSONObject().put("tasks", JSONArray(listOf(
                    "Create the project from the '$tpl' template",
                    g.tasks[0], g.tasks[1],
                    "Generate custom textures for the look described in the prompt",
                    "Compose an original soundtrack",
                    "Configure touch controls (${g.controls})",
                    "Run the Game Doctor and fix problems",
                    "Play-test the start scene headlessly",
                    "Build the installable APK",
                ))))
            )
            1 -> reply("Creating the project.",
                act("create_project", JSONObject().put("name", nameFor(prompt)).put("template", tpl).put("description", prompt.take(200))),
                act("task_done", JSONObject().put("id", 1)), act("task_done", JSONObject().put("id", 2)), act("task_done", JSONObject().put("id", 3)))
            2 -> reply("Generating art and music.",
                *g.textures.mapIndexed { i, (n, style) ->
                    act("generate_texture", JSONObject().put("name", "AI_$n").put("style", style).put("size", 128).put("seed", prompt.length + i)
                        .also { if (colors.size > i) it.put("colorB", colors[i]) })
                }.toTypedArray(),
                act("generate_sprite", JSONObject().put("name", "AI_Icon").put("shape", if (g.template == "Iron Tanks") "tank" else "star").put("color", colors.firstOrNull() ?: "#FFFFFFFF").put("size", 128)),
                act("compose_song", JSONObject().put("name", "AI Theme").put("style", g.song).put("seed", prompt.hashCode() and 0xFFFF)),
                act("set_controls", JSONObject().put("preset", g.controls)),
                act("task_done", JSONObject().put("id", 4)), act("task_done", JSONObject().put("id", 5)), act("task_done", JSONObject().put("id", 6)))
            3 -> reply("Self-inspection: doctor and play-test.",
                act("run_doctor"), act("play_test", JSONObject().put("seconds", 8)),
                act("task_done", JSONObject().put("id", 7)), act("task_done", JSONObject().put("id", 8)))
            4 -> reply("Requesting the APK build and finishing.",
                act("build_apk"), act("task_done", JSONObject().put("id", 9)),
                act("finish", JSONObject().put("summary", "Built '${nameFor(prompt)}' from the $tpl game with custom textures (AI_*.png), an original ${g.song} soundtrack and ${g.controls} controls. " +
                    "Open it in the editor to keep customising, or connect an AI model in Agent Settings for fully custom games.")))
            else -> reply("Done.", act("finish", JSONObject().put("summary", "Finished.")))
        }
    }

    private fun extractColors(p: String): List<String> {
        val names = listOf("red", "green", "blue", "yellow", "orange", "purple", "pink", "cyan", "gold", "white", "black", "brown", "gray")
        return p.lowercase().split(Regex("[^a-z]+")).filter { it in names }.distinct()
    }

    companion object {
        private val STOP = setOf("make", "create", "build", "game", "the", "and", "with", "for", "that", "want", "please", "me", "full", "like", "where", "you", "can", "have", "has", "from", "into", "some")
    }
}
