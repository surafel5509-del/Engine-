package com.sengine.project

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

/** Procedural Studio Pack: a large offline-safe starter library for prototyping. */
object MegaAssetLibrary {
    val items: List<AssetLibrary.Item> by lazy {
        val out = ArrayList<AssetLibrary.Item>()
        val textureNames = listOf("Concrete", "Asphalt", "Cobble", "Marble", "Granite", "Ceramic", "Tiles", "Hex Tiles", "SciFi Panel", "Carbon Fiber", "Brushed Steel", "Rust", "Copper", "Gold", "Silver", "Plastic", "Rubber", "Leather", "Denim", "Fabric", "Paper", "Cardboard", "Bark", "Pine", "Oak", "Moss", "Mud", "Clay", "Snow", "Pebbles", "Volcanic Rock", "Coral", "Ocean", "Deep Water", "Swamp", "Neon Blue", "Neon Pink", "Neon Green", "Energy Grid", "Circuit Board", "Hologram", "Warning Stripes", "Hazard Yellow", "Military Camo", "Urban Camo", "Clouds", "Sunset", "Aurora", "Stars", "Galaxy", "Void", "Dungeon Wall", "Dungeon Floor", "Castle Stone", "Roof Tile", "Plaster", "Painted Wall", "Wood Floor", "Parquet", "Carpet", "Grass 2", "Flower Meadow")
        textureNames.forEachIndexed { i, n -> out += texture(n, "MegaTex_${i + 1}.png", i) }
        val spriteNames = listOf("Knight", "Wizard", "Rogue", "Archer", "Robot", "Alien", "Droid", "Soldier", "Pilot", "Scientist", "Merchant", "Villager", "Skeleton", "Goblin", "Orc", "Dragon", "Wolf", "Bear", "Fox", "Bird", "Fish", "Spider", "Bat", "Slime Blue", "Slime Red", "Turret", "Drone", "Tank", "Jeep", "Truck", "Motorbike", "Helicopter", "Rocket", "UFO", "Cannon", "Sword Icon", "Shield Icon", "Potion Red", "Potion Blue", "Potion Green", "Star Icon", "Gem Blue", "Gem Red", "Gem Green", "Coin Silver", "Coin Bronze", "Chest", "Door", "Torch", "Flag", "Tree Pine", "Tree Autumn", "Bush", "Rock Small", "Rock Large", "Cloud Small", "Cloud Large", "Explosion Blue", "Explosion Ice", "Sparkle", "Smoke Puff", "Heart Full", "Heart Empty", "Crosshair", "Joystick", "Arrow Up", "Arrow Down", "Arrow Left", "Arrow Right", "Check", "X Mark", "Plus", "Minus", "Lock", "Key Icon")
        spriteNames.forEachIndexed { i, n -> out += sprite(n, "MegaSprite_${i + 1}.png", i + 100) }
        val modelNames = listOf("Modular Wall", "Door", "Window", "Fence", "Bridge", "Stairs", "Pillar", "Column", "Arch", "Rock Formation", "Pine Tree", "Oak Tree", "Palm Tree", "Street Lamp", "Bench", "Table", "Chair", "Crate", "Barrel", "Chest", "Sack", "Sword", "Shield", "Axe", "Hammer", "Lantern", "Cannon", "Turret", "SciFi Door", "SciFi Crate", "Robot", "Drone", "Spaceship", "Asteroid", "Low Poly Car", "Race Gate", "Track Barrier", "Tree House", "Cabin", "Castle Tower", "Windmill", "Watchtower", "Fountain", "Well", "Campfire", "Tent", "Boat", "Dock", "Airship", "Train", "Mine Cart", "Dungeon Gate", "Statue", "Crystal Cluster", "Planet", "Moon", "Satellite", "Solar Panel", "Generator", "Terminal", "Computer", "Medical Kit", "Ammo Box", "Treasure Chest", "Portal", "Magic Shrine", "Ancient Ruin")
        modelNames.forEachIndexed { i, n -> out += model(n, "MegaModel_${i + 1}.obj", i + 200) }
        out
    }

    private fun texture(title: String, file: String, seed: Int) = AssetLibrary.Item(title, "Mega Textures", "Procedural tileable starter material", listOf(file), preview = { bitmap(seed, false) }) { p -> writePng(p, file, bitmap(seed, false)) }
    private fun sprite(title: String, file: String, seed: Int) = AssetLibrary.Item(title, "Mega Sprites", "Procedural game-ready sprite", listOf(file), preview = { bitmap(seed, true) }) { p -> writePng(p, file, bitmap(seed, true)) }
    private fun model(title: String, file: String, seed: Int) = AssetLibrary.Item(title, "Mega 3D Models", "Low-poly editable OBJ starter model", listOf(file), glyph = "3D") { p -> p.writeAsset(file, obj(seed)) }

    private fun writePng(p: Project, name: String, b: Bitmap) { p.assetsDir.mkdirs(); p.assetFile(name).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }

    private fun bitmap(seed: Int, sprite: Boolean): Bitmap {
        val size = if (sprite) 96 else 128
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val hue = (seed * 47 % 360).toFloat(); val base = Color.HSVToColor(floatArrayOf(hue, if (sprite) .65f else .35f, .72f))
        c.drawColor(if (sprite) Color.TRANSPARENT else base)
        paint.color = if (sprite) base else Color.argb(90, 255, 255, 255); paint.style = Paint.Style.FILL
        if (sprite) {
            val path = Path(); val cx = size / 2f; val cy = size / 2f
            for (i in 0 until 8) { val a = i * Math.PI / 4 - Math.PI / 2; val r = if (i % 2 == 0) size * .37f else size * .18f; val x = cx + cos(a) * r; val y = cy + sin(a) * r; if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat()) }
            path.close(); c.drawPath(path, paint); paint.color = Color.WHITE; c.drawCircle(cx - size * .1f, cy - size * .1f, size * .07f, paint)
        } else {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
            for (i in 0..size step 16) { c.drawLine(i.toFloat(), 0f, i.toFloat(), size.toFloat(), paint); c.drawLine(0f, i.toFloat(), size.toFloat(), i.toFloat(), paint) }
            paint.style = Paint.Style.FILL
        }
        return b
    }

    private fun obj(seed: Int): String {
        val s = 0.35f + (seed % 5) * .08f
        return "# S Engine Mega 3D Pack\nv -$s -$s -$s\nv $s -$s -$s\nv $s $s -$s\nv -$s $s -$s\nv -$s -$s $s\nv $s -$s $s\nv $s $s $s\nv -$s $s $s\nf 1 2 3 4\nf 5 8 7 6\nf 1 5 6 2\nf 2 6 7 3\nf 3 7 8 4\nf 5 1 4 8\n"
    }
}
