package com.sengine.project.games

import com.sengine.project.Templates

/** The complete sample games shipped with S Engine v7 Ultimate Studio. */
object Games {
    /** Reads a bundled game script from the Java resources (works on Android and in JVM unit tests). */
    fun res(path: String): String =
        (Games::class.java.getResourceAsStream("/games/$path") ?: Games::class.java.classLoader?.getResourceAsStream("games/$path"))
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Missing bundled game resource games/$path")

    val templates: List<Templates.Template> by lazy {
        listOf(
            Templates.Template("Crystal Caverns (2D Platformer)", "Complete cave platformer adventure: hero with run/jump animation, gems, 3 enemy types (bat, slime, golem), spikes, moving platforms, checkpoints, 3 levels with bosses' gates, menu, HUD, pause and saves.") { CavernsGame.build(it) },
            Templates.Template("Slice Master (2D Arcade)", "Complete fruit-slicing arcade: swipe to slice, physics fruit arcs, juicy splashes, combos, bombs, lives, classic + frenzy modes, menu, HUD and records.") { SliceGame.build(it) },
            Templates.Template("Iron Guard (2D Tower Defense)", "Complete tower defense: build/upgrade/sell 3 tower types, 2 enemy types + bosses, 10 escalating waves, gold economy, lives, fast-forward, menu and victory screen.") { TowerGame.build(it) },
            Templates.Template("Sky Harbor (3D Flight)", "Complete 3D flying game: a plane with a spinning propeller, fly through checkpoint rings over islands, throttle + boost, crash & respawn, day and dusk maps, timer, HUD and minimap radar.") { HarborGame.build(it) },
            Templates.Template("Zombie Garage (3D Vehicle Combat)", "Complete drive-and-survive: a car with spinning wheels, auto-animated zombies that ragdoll when hit, waves, fuel, repairs, upgrades in the garage menu, HUD and game over.") { GarageGame.build(it) },
            Templates.Template("Dungeon Quest (3D Action RPG)", "Complete third-person action RPG: auto-animated hero (walk/attack/celebrate/death), skeleton guards, treasure chests, potions, portal objectives, minimap radar, 2 dungeon floors, menu and HUD.") { DungeonGame.build(it) },
            Templates.Template("Strike Force (3D FPS)", "Complete first-person shooter: desert compound + night raid, patrolling soldiers with line-of-sight AI, rifle/shotgun/pistol with ADS and headshots, grenades, exploding barrels, extraction.") { FpsGame.build(it) },
            Templates.Template("Open World 2D (Open Sample)", "The open sample game: a small explorable world with a town, NPCs and quests, day/night cycle, house interiors and fully commented scripts — the best place to learn how S Engine games are made.") { OpenWorldGame.build(it) },
        )
    }
}
