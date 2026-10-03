package com.sengine.project.games

import com.sengine.project.Templates

/** The complete sample games shipped with S Engine Full Edition. */
object Games {
    /** Reads a bundled game script from the Java resources (works on Android and in JVM unit tests). */
    fun res(path: String): String =
        (Games::class.java.getResourceAsStream("/games/$path") ?: Games::class.java.classLoader?.getResourceAsStream("games/$path"))
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Missing bundled game resource games/$path")

    val templates: List<Templates.Template> by lazy {
        listOf(
            Templates.Template("Cyber Runner (2D Cyberpunk)", "Fast-paced 2D Cyberpunk platformer with wall jumping, lasers, tilemaps and GDScript control.") { CyberRunnerGame.build(it) },
            Templates.Template("Dungeon Crawler (2D RPG)", "Top-down 2D dungeon RPG with slimes, keys, health items and Lua player scripts.") { DungeonCrawlerGame.build(it) },
            Templates.Template("Apex Drift (3D Racing)", "3D arcade racing game with sports car physics, asphalt tracks, and drifting.") { ApexDriftGame.build(it) },
            Templates.Template("Galaxy Defender (2D Bullet Hell)", "2D space bullet-hell shooter with boss encounters, particle trails, and laser weapons.") { GalaxyDefenderGame.build(it) },
            Templates.Template("Voxel Survivor (3D Voxel)", "3D voxel survival sandbox with block terrain, craftable resources and day/night survival.") { VoxelSurvivorGame.build(it) },
            Templates.Template("Sky Strike (2D Shooter)", "Complete top-down shoot 'em up: menu, settings, level select with stars, 3 levels with waves, bosses, power-ups and bombs.") { ShooterGame.build(it) },
            Templates.Template("Dead Zone (2D Zombie)", "Complete zombie survival: twin-stick aiming, 3 weapons with reloading, 3 zombie types, endless waves, loot and upgrades.") { ZombieGame.build(it) },
            Templates.Template("Turbo Rally (3D Racing)", "Complete 3D racing: 2 maps × 3 roads, 5 AI drivers, drift & nitro, laps, positions, minimap, 3 cameras, settings and records.") { RacingGame.build(it) },
            Templates.Template("MiniCraft (3D Voxel)", "Complete voxel sandbox: infinite-feeling worlds, first-person building, hotbar, fly mode, day/night, animals and saved worlds.") { CraftGame.build(it) },
            Templates.Template("Strike Force (3D FPS)", "Complete first-person shooter: desert compound + night raid, patrolling soldiers with line-of-sight AI, rifle/shotgun/pistol with ADS and headshots, grenades, exploding barrels, extraction.") { FpsGame.build(it) },
            Templates.Template("Iron Tanks (2D Tank Battle)", "Complete top-down tank war: aiming turret, destructible walls, rivers and bushes, 3 enemy tank types, HQ defence, HE shells, power-ups, 3 missions with stars.") { TankGame.build(it) },
        )
    }
}
