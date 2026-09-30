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
        Genre(listOf("fps", "call of duty", "first person", "first-person", "3d shooter", "soldier", "gun", "sniper", "war"), "Strike Force", "Action Battle", "First Person",
            listOf("Ground" to "Asphalt", "Walls" to "Concrete"), listOf("First-person shooting with weapons and reload", "Enemy soldier AI and missions")),
        Genre(listOf("tower", "defense", "defend", "wave defense", "guard"), "Iron Guard", "Action Battle", "Touch Only",
            listOf("Ground" to "Grass", "Walls" to "Stone"), listOf("Tower building, upgrading and selling", "Escalating enemy waves with a gold economy")),
        Genre(listOf("zombie", "undead", "horror"), "Zombie Garage", "Spooky Night", "Racing",
            listOf("Ground" to "Dirt", "Walls" to "Concrete"), listOf("Drive and smash zombies with vehicle physics", "Waves, ragdolls and upgrades")),
        Genre(listOf("car", "race", "racing", "drift", "kart", "drive", "garage"), "Zombie Garage", "Racing Rush", "Racing",
            listOf("Road" to "Asphalt", "Grass" to "Grass"), listOf("Car physics with nitro and drifting", "Enemy waves and score")),
        Genre(listOf("fly", "flight", "plane", "jet", "airplane", "sky", "rings", "pilot"), "Sky Harbor", "Menu Theme", "Racing",
            listOf("Space" to "Clouds", "Ground" to "Grass Field"), listOf("Fly a plane through checkpoint rings", "Throttle, boost and crash physics")),
        Genre(listOf("dungeon", "rpg", "sword", "hero", "skeleton", "quest", "chest", "potion"), "Dungeon Quest", "Chiptune Adventure", "First Person",
            listOf("Ground" to "Stone", "Walls" to "Brick Wall"), listOf("Third-person hero combat with animations", "Chests, potions and objectives")),
        Genre(listOf("fruit", "slice", "ninja", "arcade", "swipe", "combo"), "Slice Master", "Chill Lo-Fi", "Touch Only",
            listOf("Space" to "Sky Gradient"), listOf("Swipe slicing with physics fruit arcs", "Combos, bombs and high scores")),
        Genre(listOf("platform", "jump", "mario", "runner", "adventure", "cave", "gem"), "Crystal Caverns", "Chiptune Adventure", "Platformer",
            listOf("Ground" to "Grass", "Bricks" to "Bricks"), listOf("Platforming with an animated hero", "Enemies, spikes and collectibles")),
        Genre(listOf("open world", "town", "npc", "dialog", "village", "explore 2d"), "Open World 2D", "Menu Theme", "Platformer",
            listOf("Ground" to "Grass", "Walls" to "Wood Planks"), listOf("Explore a town and talk to NPCs", "Quests, items and day/night")),
        Genre(listOf("craft", "minecraft", "voxel", "block", "build", "mine", "sandbox 3d"), "3D Demo", "Block World", "First Person/Builder",
            listOf("Stone" to "Cobblestone", "Planks" to "Planks"), listOf("Voxel world digging and building", "Day/night and inventory")),
        Genre(listOf("3d", "world", "explore"), "3D Demo", "Menu Theme", "First Person", listOf("Ground" to "Grass", "Stone" to "Stone"),
            listOf("3D world with lighting", "Player movement and camera")),
        Genre(listOf("physics", "puzzle", "ball"), "Physics Sandbox", "Chill Lo-Fi", "Touch Only", listOf("Wood" to "Wood", "Metal" to "Metal"),
            listOf("Physics playground", "Spawning objects")),
    )

    private fun genreFor(prompt: String): Genre {
        val p = prompt.lowercase()
        return genres.maxByOrNull { g -> g.keys.count { p.contains(it) } }?.takeIf { g -> g.keys.any { p.contains(it) } } ?: genres[6]
    }

    private fun nameFor(prompt: String): String {
        Regex("(?:called|named|titled)\\s+\"?([A-Za-z0-9 ]{2,30})\"?", RegexOption.IGNORE_CASE).find(prompt)?.let { m ->
            val quoted = Regex("\"([^\"]{2,30})\"").find(prompt)?.groupValues?.get(1)
            if (quoted != null) return quoted.trim()
            val t = m.groupValues[1].split(Regex("\\s+(?i:with|and|where|that|which|in|for|featuring|using|set|about|on|where)\\b")).first().trim()
            if (t.isNotBlank()) return t
        }
        val words = prompt.replace(Regex("[^A-Za-z0-9 ]"), " ").split(' ').filter { it.length > 2 && it.lowercase() !in STOP }.take(3)
        return words.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }.ifBlank { "AI Game" }
    }

    override fun complete(system: String, messages: List<ChatMessage>): String {
        val prompt = messages.first().content.substringAfter("GAME REQUEST:").substringBefore("\n\nStart by").trim()
        val step = messages.count { it.role == "assistant" }
        val g = genreFor(prompt)
        val tpl = (Templates.all.firstOrNull { it.name.startsWith(g.template, true) } ?: Templates.all.firstOrNull { it.name.startsWith("Crystal Caverns") } ?: Templates.all[0]).name
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
                act("generate_sprite", JSONObject().put("name", "AI_Icon").put("shape", if (g.template == "Iron Guard") "shield" else "star").put("color", colors.firstOrNull() ?: "#FFFFFFFF").put("size", 128)),
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
