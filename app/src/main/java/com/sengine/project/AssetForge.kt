package com.sengine.project

import com.sengine.engine.texture.PngEncoder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ultimate Edition Pro content forge: ~150 additional store items (pixel sprites, animated sprite
 * sheets + .anim clips, SFX, UI kit, tiles and music) generated as pure Kotlin so the entire
 * catalogue is validated by the JVM test suite. PNGs are encoded with the engine's own PngEncoder.
 */
object AssetForge {

    // ==================================================================== pixel canvas (pure Kotlin)

    /** Tiny ARGB raster with drawing primitives. Pure Kotlin — unit-testable without Android. */
    class Px(val w: Int, val h: Int) {
        val a = IntArray(w * h)

        operator fun get(x: Int, y: Int): Int = if (x in 0 until w && y in 0 until h) a[y * w + x] else 0
        operator fun set(x: Int, y: Int, c: Int) { if (x in 0 until w && y in 0 until h) a[y * w + x] = c }

        fun fill(c: Int) { for (i in a.indices) a[i] = c }
        fun rect(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
            for (y in y0..y1) for (x in x0..x1) set(x, y, c)
        }
        fun disc(cx: Float, cy: Float, r: Float, c: Int) {
            val r2 = r * r
            for (y in (cy - r - 1).toInt()..(cy + r + 1).toInt())
                for (x in (cx - r - 1).toInt()..(cx + r + 1).toInt()) {
                    val dx = x + 0.5f - cx; val dy = y + 0.5f - cy
                    if (dx * dx + dy * dy <= r2) set(x, y, c)
                }
        }
        fun ring(cx: Float, cy: Float, r: Float, thick: Float, c: Int) {
            val r2 = r * r; val ri = (r - thick) * (r - thick)
            for (y in (cy - r - 1).toInt()..(cy + r + 1).toInt())
                for (x in (cx - r - 1).toInt()..(cx + r + 1).toInt()) {
                    val dx = x + 0.5f - cx; val dy = y + 0.5f - cy
                    val d = dx * dx + dy * dy
                    if (d <= r2 && d >= ri) set(x, y, c)
                }
        }
        fun line(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
            val n = max(abs(x1 - x0), abs(y1 - y0)) + 1
            for (i in 0 until n) set(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n, c)
        }

        /** Character-art rows ('.' and ' ' transparent) coloured with [pal]. */
        fun art(rows: List<String>, pal: Map<Char, Int>) {
            for (y in 0 until min(h, rows.size)) {
                val row = rows[y]
                for (x in 0 until min(w, row.length)) {
                    val ch = row[x]
                    if (ch == '.' || ch == ' ') continue
                    set(x, y, pal[ch] ?: pal['a'] ?: -0x1000000)
                }
            }
        }

        /** Nearest-neighbour scale. */
        fun scaled(s: Int): Px {
            val o = Px(w * s, h * s)
            for (y in 0 until o.h) for (x in 0 until o.w) o.set(x, y, get(x / s, y / s))
            return o
        }

        fun rotatedQuarter(): Px {
            val o = Px(h, w)
            for (y in 0 until h) for (x in 0 until w) o.set(h - 1 - y, x, get(x, y))
            return o
        }

        /** Multiplies opaque pixel brightness by [f] (keeps alpha). */
        fun tint(f: Float): Px {
            val o = Px(w, h)
            for (i in a.indices) {
                val c = a[i]
                if (c == 0) continue
                val r = min(255, ((c shr 16 and 0xFF) * f).toInt())
                val g = min(255, ((c shr 8 and 0xFF) * f).toInt())
                val b = min(255, ((c and 0xFF) * f).toInt())
                o.a[i] = (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
            return o
        }

        fun shifted(dx: Int, dy: Int): Px {
            val o = Px(w, h)
            for (y in 0 until h) for (x in 0 until w) o.set(x + dx, y + dy, get(x, y))
            return o
        }

        fun opaque(): Int { var n = 0; for (c in a) if (c != 0) n++; return n }

        fun png(): ByteArray = PngEncoder.encode(w, h, a.copyOf())
    }

    // ==================================================================== palettes

    private val O = ForgeArt.OUTLINE
    private val W = ForgeArt.WHITE
    private val G = ForgeArt.GLOW

    private val GOLD = 0xFFFFC93C.toInt() to 0xFFE09A22.toInt()
    private val SILVER = 0xFFE8EDF2.toInt() to 0xFFA9B4C0.toInt()
    private val BRONZE = 0xFFE0A15C.toInt() to 0xFF9A622F.toInt()
    private val WOODP = 0xFFC08A4A.toInt() to 0xFF8A5A2B.toInt()
    private val STEEL = 0xFFC9D4Df.toInt() to 0xFF7E8B99.toInt()

    private fun cAB(a: Int, b: Int) = ForgeArt.pal('a' to a, 'A' to b)

    // ==================================================================== catalogue assembly

    class AnimSpec(
        val title: String, val texFile: String, val animFile: String,
        val columns: Int, val rows: Int, val frameCount: Int, val fps: Float, val loop: Boolean,
        val desc: String, val frames: () -> List<Px>,
    )

    private val items0 = ArrayList<AssetLibrary.Item>()
    private val anims0 = ArrayList<AnimSpec>()
    val spriteCount get() = items0.count { it.category == "Sprites" }
    val animCount get() = anims0.size

    fun items(): List<AssetLibrary.Item> {
        if (items0.isEmpty()) build()
        return items0
    }

    fun animSpecs(): List<AnimSpec> {
        if (items0.isEmpty()) build()
        return anims0
    }

    private fun px16(rows: List<String>, pal: Map<Char, Int>): Px {
        val p = Px(16, 16); p.art(rows, pal); return p
    }

    private fun sprite(title: String, file: String, desc: String, rows: List<String>, pal: Map<Char, Int>) {
        items0 += AssetLibrary.Item(title, "Sprites", desc, listOf(file), "🖼",
            preview = { val b = px16(rows, pal).png(); android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size) },
            install = { p -> writePng(p, file, px16(rows, pal)) })
    }

    private fun writePng(p: Project, name: String, px: Px) {
        p.assetsDir.mkdirs()
        p.assetFile(name).writeBytes(px.png())
    }

    private fun texItem(title: String, file: String, desc: String, cat: String, gen: () -> Px) {
        items0 += AssetLibrary.Item(title, cat, desc, listOf(file), "🖼",
            preview = { val b = gen().png(); android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size) },
            install = { p -> writePng(p, file, gen()) })
    }

    private fun sfx(title: String, file: String, desc: String, gen: () -> ByteArray) {
        items0 += AssetLibrary.Item(title, "Sounds", desc, listOf(file), "♪", sound = gen) { p ->
            p.assetsDir.mkdirs(); p.assetFile(file).writeBytes(gen())
        }
    }

    private fun music(title: String, file: String, desc: String, style: Int, seed: Int) {
        items0 += AssetLibrary.Item(title, "Music", desc, listOf(file), "♫",
            sound = { Song.compose(style, seed).renderWav() }) { p ->
            p.writeAsset(file, Song.compose(style, seed).toJson().toString(2))
        }
    }

    private fun anim(spec: AnimSpec) {
        anims0 += spec
        items0 += AssetLibrary.Item(spec.title, "Sprite Sheets", spec.desc, listOf(spec.texFile, spec.animFile), "🎞",
            preview = { val b = sheet(spec).png(); android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size) },
            install = { p ->
                writePng(p, spec.texFile, sheet(spec))
                p.writeAsset(spec.animFile, clipJson(spec).toString(2))
            })
    }

    private fun sheet(spec: AnimSpec): Px {
        val fs = spec.frames()
        val cw = fs.maxOf { it.w }; val ch = fs.maxOf { it.h }
        val sheet = Px(cw * spec.columns, ch * spec.rows)
        for ((i, f) in fs.withIndex()) {
            val cx = (i % spec.columns) * cw; val cy = (i / spec.columns) * ch
            for (y in 0 until f.h) for (x in 0 until f.w) {
                val c = f[x, y]
                if (c != 0) sheet.set(cx + x, cy + y, c)
            }
        }
        return sheet
    }

    private fun clipJson(spec: AnimSpec) = com.sengine.engine.anim.AnimationClip(
        spec.texFile, spec.columns, spec.rows,
        (0 until spec.frameCount).toMutableList(), spec.fps, spec.loop).toJson()

    // ==================================================================== content

    private fun build() {
        // ------------------------------------------------------------ RPG items (palette families)
        val gemColors = listOf("Ruby" to (0xFFE23B4E.toInt() to 0xFF8C1220), "Sapphire" to (0xFF3B7BE2.toInt() to 0xFF16408C),
            "Emerald" to (0xFF3BE26B.toInt() to 0xFF128C36), "Topaz" to (0xFFE2B23B.toInt() to 0xFF8C6E12),
            "Amethyst" to (0xFFA64CE2.toInt() to 0xFF5E128C), "Citrine" to (0xFFF0E44C.toInt() to 0xFF9C8E14),
            "Obsidian" to (0xFF5A5F6E.toInt() to 0xFF262A33), "Pearl" to (0xFFF6F0FF.toInt() to 0xFFB9AED4))
        for ((name, cols) in gemColors)
            sprite("$name Gem", "$name Gem.png", "$name gemstone, 16×16 pixel art", ForgeArt.arts["gem"]!!, cAB(cols.first, cols.second))

        val potions = listOf("Health" to (0xFFE23B4E.toInt() to 0xFF8C1220), "Mana" to (0xFF3B6FE2.toInt() to 0xFF163A8C),
            "Stamina" to (0xFF3BE2B0.toInt() to 0xFF128C6E), "Poison" to (0xFF7BE23B.toInt() to 0xFF3A8C12),
            "Strength" to (0xFFE2823B.toInt() to 0xFF8C4A12), "Invisibility" to (0xFFB8B8D8.toInt() to 0xFF6E6E96))
        for ((name, cols) in potions)
            sprite("$name Potion", "$name Potion.png", "$name potion bottle, 16×16", ForgeArt.arts["potion"]!!, ForgeArt.pal('a' to cols.first.toInt(), 'A' to cols.second.toInt(), 'w' to 0x88FFFFFF.toInt()))

        val orbs = listOf("Fire Orb" to 0xFFFF7A3BL, "Frost Orb" to 0xFF7AD6FF, "Nature Orb" to 0xFF7AFF8A,
            "Shadow Orb" to 0xFF9A6EFF, "Holy Orb" to 0xFFFFE88A, "Storm Orb" to 0xFFB7C7FF)
        for ((name, col) in orbs)
            sprite(name, "$name.png", "Glowing $name for magic effects, 16×16", ForgeArt.arts["orb"]!!,
                ForgeArt.pal('a' to col, 'A' to darken(col), 'w' to 0xFFFFFFFF.toInt()))

        val metals = listOf("Gold" to GOLD, "Silver" to SILVER, "Bronze" to BRONZE, "Iron" to STEEL)
        for ((name, cols) in metals)
            sprite("$name Key", "$name Key.png", "$name key, 16×16", ForgeArt.arts["key"]!!, cAB(cols.first, cols.second))

        for ((name, cols) in listOf("Gold" to GOLD, "Silver" to SILVER, "Bronze" to BRONZE))
            sprite("$name Coin", "$name Coin.png", "$name currency coin, 16×16", ForgeArt.arts["gem"]!!, cAB(cols.first, cols.second))
        for ((name, cols) in listOf("Heart" to (0xFFE23B4E.toInt() to 0xFF8C1220), "Life Heart" to (0xFFFF5E7A.toInt() to 0xFFB01E36), "Dark Heart" to (0xFF8A3B6E.toInt() to 0xFF4A1030)))
            sprite(name, "$name.png", "Pixel heart, 16×16", ForgeArt.arts["heart"]!!, cAB(cols.first, cols.second))
        for ((name, cols) in listOf("Gold Star" to GOLD, "Magic Star" to (0xFFB07AFF.toInt() to 0xFF5E2E9C)))
            sprite(name, "$name.png", "Pixel star, 16×16", ForgeArt.arts["star"]!!, cAB(cols.first, cols.second))

        // weapons & armour
        val blades = listOf("Steel" to STEEL, "Flame" to (0xFFFF9A4C.toInt() to 0xFFC24A12), "Ice" to (0xFF9ADFFF.toInt() to 0xFF4E93C2), "Venom" to (0xFF9AFF6E.toInt() to 0xFF4E9C2A))
        for ((name, cols) in blades)
            sprite("$name Sword", "$name Sword.png", "$name sword, 16×16", ForgeArt.arts["sword"]!!, ForgeArt.pal('a' to cols.first.toInt(), 'A' to cols.second.toInt(), 'w' to 0xFFFFFFFF.toInt(), 'M' to 0xFF6E4A2AL.toInt()))
        for ((name, cols) in listOf("Wooden Shield" to WOODP, "Iron Shield" to STEEL, "Royal Shield" to (0xFF3B6FE2.toInt() to 0xFF163A8C)))
            sprite(name, "$name.png", "$name, 16×16", ForgeArt.arts["shield"]!!, ForgeArt.pal('a' to cols.first.toInt(), 'A' to cols.second.toInt(), 'c' to GOLD.first.toInt(), 'h' to cols.first.toInt()))
        sprite("Battle Axe", "Battle Axe.png", "Two-handed axe, 16×16", ForgeArt.arts["axe"]!!, ForgeArt.pal('a' to WOODP.first, 'b' to STEEL.first, 'B' to STEEL.second, 'M' to 0xFF6E4A2AL.toInt()))
        sprite("Shortbow", "Shortbow.png", "Wooden bow, 16×16", ForgeArt.arts["bow"]!!, ForgeArt.pal('a' to WOODP.first, 'b' to WOODP.second, 'c' to 0xFFE8E0C8L.toInt()))
        sprite("War Hammer", "War Hammer.png", "Heavy hammer, 16×16", ForgeArt.arts["hammer"]!!, ForgeArt.pal('a' to WOODP.first, 'm' to STEEL.first, 'M' to 0xFF6E4A2AL.toInt()))
        sprite("Magic Wand", "Magic Wand.png", "Glow-tipped wand, 16×16", ForgeArt.arts["wand"]!!, ForgeArt.pal('a' to WOODP.first, 'A' to WOODP.second.toInt()))
        sprite("Elder Staff", "Elder Staff.png", "Caster staff, 16×16", ForgeArt.arts["staff"]!!, ForgeArt.pal('a' to WOODP.first, 'A' to WOODP.second.toInt()))
        sprite("Hunting Spear", "Hunting Spear.png", "Throwing spear, 16×16", ForgeArt.arts["spear"]!!, ForgeArt.pal('a' to STEEL.first, 'M' to 0xFF6E4A2AL.toInt()))
        for ((name, cols) in listOf("Iron Helmet" to STEEL, "Gold Crown Helm" to GOLD))
            sprite(name, "$name.png", "$name armour piece, 16×16", ForgeArt.arts["helmet"]!!, cAB(cols.first, cols.second))
        sprite("Knight Armor", "Knight Armor.png", "Chest armour, 16×16", ForgeArt.arts["armor"]!!, cAB(STEEL.first, STEEL.second.toInt()))
        sprite("Leather Boots", "Leather Boots.png", "Adventurer boots, 16×16", ForgeArt.arts["boots"]!!, cAB(WOODP.first, WOODP.second.toInt()))
        sprite("Signet Ring", "Signet Ring.png", "Jewelled ring, 16×16", ForgeArt.arts["ring"]!!, ForgeArt.pal('c' to GOLD.first, 'g' to 0xFF6EE2FFL.toInt()))
        sprite("Amulet", "Amulet.png", "Pendant amulet, 16×16", ForgeArt.arts["amulet"]!!, cAB(GOLD.first, GOLD.second.toInt()))
        sprite("Royal Crown", "Royal Crown.png", "King's crown, 16×16", ForgeArt.arts["crown"]!!, ForgeArt.pal('a' to GOLD.first, 'e' to GOLD.second.toInt(), 'g' to 0xFF6EE2FFL.toInt()))
        sprite("Wizard Hat", "Wizard Hat.png", "Pointy hat, 16×16", ForgeArt.arts["hat"]!!, cAB(0xFF6E4AC2L, 0xFF3E2472L))
        sprite("Red Book", "Red Book.png", "Tome, 16×16", ForgeArt.arts["book"]!!, ForgeArt.pal('a' to 0xFFB03B4EL.toInt(), 'b' to 0xFFF2EAD8L.toInt(), 'w' to 0xFF8A2E3EL.toInt()))
        sprite("Blue Book", "Blue Book.png", "Spellbook, 16×16", ForgeArt.arts["book"]!!, ForgeArt.pal('a' to 0xFF3B5EB0L.toInt(), 'b' to 0xFFF2EAD8L.toInt(), 'w' to 0xFF2E4488L.toInt()))
        sprite("Ancient Scroll", "Ancient Scroll.png", "Rolled parchment, 16×16", ForgeArt.arts["scroll"]!!, ForgeArt.pal('w' to 0xFFF2EAD8L.toInt()))

        // world props
        sprite("Round Bomb", "Round Bomb.png", "Classic bomb, 16×16", ForgeArt.arts["bomb"]!!, cAB(0xFF3A3F4EL, 0xFF1E222C))
        sprite("Dynamite", "Dynamite.png", "Red stick bundle, 16×16", ForgeArt.arts["dynamite"]!!, ForgeArt.pal('r' to 0xFFD84040L.toInt(), 'O' to 0xFF8C1E1EL.toInt()))
        sprite("Wood Barrel", "Wood Barrel.png", "Storage barrel, 16×16", ForgeArt.arts["barrel"]!!, ForgeArt.pal('m' to WOODP.first, 'M' to WOODP.second, 'v' to 0xFF6E4A2AL.toInt()))
        sprite("Oak Crate", "Oak Crate.png", "Cargo crate, 16×16", ForgeArt.arts["crate"]!!, ForgeArt.pal('a' to WOODP.first, 'd' to WOODP.second, 'o' to 0xFF5E3A17L.toInt()))
        sprite("Treasure Chest", "Treasure Chest.png", "Closed chest, 16×16", ForgeArt.arts["chest"]!!, ForgeArt.pal('a' to WOODP.first, 'A' to WOODP.second, 'm' to GOLD.first, 'g' to GOLD.first.toInt()))
        sprite("Open Chest", "Open Chest.png", "Emptied chest, 16×16", ForgeArt.arts["chest"]!!, ForgeArt.pal('a' to WOODP.second, 'A' to 0xFF5E3A17L.toInt(), 'm' to SILVER.first, 'g' to SILVER.first))
        sprite("Dungeon Door", "Dungeon Door.png", "Locked door, 16×16", ForgeArt.arts["door"]!!, cAB(0xFF8A5A2BL, 0xFF5E3A17))
        sprite("Ladder", "Ladder.png", "Climbable ladder, 16×16", ForgeArt.arts["ladder"]!!, cAB(WOODP.first, WOODP.second.toInt()))
        sprite("Wooden Sign", "Wooden Sign.png", "Text sign, 16×16", ForgeArt.arts["sign"]!!, ForgeArt.pal('a' to WOODP.first, 'w' to 0xFFE8DCC0L.toInt(), 'M' to 0xFF6E4A2AL.toInt()))
        sprite("Red Flag", "Red Flag.png", "Waving flag, 16×16", ForgeArt.arts["flag"]!!, ForgeArt.pal('a' to 0xFFD84040L.toInt(), 'A' to 0xFF8C1E1EL.toInt(), 'M' to 0xFF6E4A2AL.toInt()))
        sprite("Torch", "Torch.png", "Wall torch, 16×16", ForgeArt.arts["torch"]!!, cAB(WOODP.first, WOODP.second.toInt()))
        sprite("Lantern", "Lantern.png", "Hand lantern, 16×16", ForgeArt.arts["lantern"]!!, ForgeArt.pal('a' to 0xFF4E5A6EL.toInt(), 'A' to 0xFF2E3642L.toInt()))
        sprite("Blacksmith Anvil", "Blacksmith Anvil.png", "Forging anvil, 16×16", ForgeArt.arts["anvil"]!!, cAB(STEEL.first, STEEL.second.toInt()))
        sprite("Brass Bell", "Brass Bell.png", "Ringing bell, 16×16", ForgeArt.arts["bell"]!!, cAB(BRONZE.first, BRONZE.second))
        sprite("Loot Bag", "Loot Bag.png", "Drop bag, 16×16", ForgeArt.arts["bag"]!!, cAB(0xFFB08A5AL, 0xFF7A562E))
        sprite("Bone", "Bone.png", "Pickup bone, 16×16", ForgeArt.arts["bone"]!!, ForgeArt.pal('w' to 0xFFF2ECDFL.toInt()))
        sprite("Skull", "Skull.png", "Dungeon skull, 16×16", ForgeArt.arts["skull"]!!, ForgeArt.pal('w' to 0xFFF2ECDFL.toInt()))

        // nature
        for ((name, cols) in listOf("Red Cap" to (0xFFE23B4E.toInt() to 0xFF8C1220), "Brown Cap" to (0xFFB0793F.toInt() to 0xFF6E4A22), "Blue Cap" to (0xFF4E7AE2.toInt() to 0xFF2A4A96)))
            sprite("$name Mushroom", "$name Mushroom.png", "$name mushroom, 16×16", ForgeArt.arts["mushroom"]!!, ForgeArt.pal('a' to cols.first.toInt(), 'A' to cols.second.toInt(), 'w' to 0xFFF2ECDFL.toInt()))
        for ((name, col) in listOf("Tulip" to 0xFFE2445E, "Daisy" to 0xFFF6F2E8, "Rose" to 0xFFC22E5E, "Bluebell" to 0xFF5E7AE2))
            sprite(name, "$name.png", "Wildflower, 16×16", ForgeArt.arts["flower"]!!, ForgeArt.pal('c' to col, 'C' to darken(col), 'a' to 0xFF4E9C3EL.toInt(), 'A' to 0xFF2E6E22L.toInt(), 'w' to 0xFF9CD48AL.toInt()))
        sprite("Leafy Bush", "Leafy Bush.png", "Rounded bush, 16×16", ForgeArt.arts["bush"]!!, cAB(0xFF4E9C3EL, 0xFF2E6E22))
        sprite("Desert Cactus", "Desert Cactus.png", "Saguaro, 16×16", ForgeArt.arts["cactus"]!!, ForgeArt.pal('a' to 0xFF4E9C3EL.toInt(), 'b' to 0xFF3E8C2EL.toInt(), 'w' to 0xFF7ACC5EL.toInt()))
        sprite("Pine Tree", "Pine Tree.png", "Conifer, 16×16", ForgeArt.arts["pine"]!!, cAB(0xFF2E7E42L, 0xFF1C5A2EL))
        sprite("Oak Tree", "Oak Tree.png", "Round canopy tree, 16×16", ForgeArt.arts["tree"]!!, ForgeArt.pal('a' to 0xFF4E9C3EL.toInt(), 'A' to 0xFF2E6E22L.toInt(), 'w' to 0xFF8AD47AL.toInt(), 'M' to WOODP.second.toInt()))
        sprite("Palm Tree", "Palm Tree.png", "Beach palm, 16×16", ForgeArt.arts["palm"]!!, ForgeArt.pal('a' to 0xFF54B24EL.toInt(), 'c' to 0xFFC09A4EL.toInt(), 'M' to 0xFF8A6A3EL.toInt()))
        sprite("Boulder", "Boulder.png", "Big rock, 16×16", ForgeArt.arts["rock"]!!, cAB(0xFF9AA2AEL, 0xFF6E7682))
        sprite("Gravel Patch", "Gravel Patch.png", "Loose stones, 16×16", ForgeArt.arts["gravel"]!!, cAB(0xFF9AA2AEL, 0xFF7E8B99))

        // creatures
        for ((name, cols) in listOf("Green Slime" to (0xFF5ED44E.toInt() to 0xFF2E8C22), "Blue Slime" to (0xFF5EB0D4.toInt() to 0xFF2A6E96), "Lava Slime" to (0xFFE2703B.toInt() to 0xFF9C3A12)))
            sprite(name, "$name.png", "Bouncy slime enemy, 16×16", ForgeArt.arts["slime"]!!, ForgeArt.pal('a' to cols.first.toInt(), 'A' to cols.second.toInt(), 'w' to 0xFFFFFFFF.toInt()))
        sprite("Cave Bat", "Cave Bat.png", "Flappy bat, 16×16", ForgeArt.arts["bat"]!!, cAB(0xFF6E5A8CL, 0xFF463861))
        sprite("Friendly Ghost", "Friendly Ghost.png", "Spooky but cute, 16×16", ForgeArt.arts["ghost"]!!, ForgeArt.pal('a' to 0xFFE8F0FFL.toInt(), 'w' to 0xFF2E3642L.toInt()))
        sprite("Web Spider", "Web Spider.png", "Eight-legged, 16×16", ForgeArt.arts["spider"]!!, cAB(0xFF3A3F4EL, 0xFF1E222C))
        sprite("Sewer Rat", "Sewer Rat.png", "Fast rodent, 16×16", ForgeArt.arts["rat"]!!, cAB(0xFF9A8A72L, 0xFF6E604E))
        sprite("Garden Snake", "Garden Snake.png", "Slithering snake, 16×16", ForgeArt.arts["snake"]!!, cAB(0xFF5EB24EL, 0xFF2E7E2A))
        sprite("Beach Crab", "Beach Crab.png", "Sideways walker, 16×16", ForgeArt.arts["crab"]!!, cAB(0xFFE26E3BL, 0xFF9C3A12))
        sprite("Honey Bee", "Honey Bee.png", "Buzzing bee, 16×16", ForgeArt.arts["bee"]!!, ForgeArt.pal('a' to 0xFFE2B23BL.toInt(), 'y' to 0xFF3A2E1EL.toInt(), 'w' to 0xFFFFFFFF.toInt()))
        for ((name, cols) in listOf("Goldfish" to (0xFFE2913B.toInt() to 0xFF9C5A12), "Bluegill" to (0xFF5E9AE2.toInt() to 0xFF2E5E9C), "Koi" to (0xFFF2F2F2.toInt() to 0xFFC22E2E)))
            sprite(name, "$name.png", "Swimming fish, 16×16", ForgeArt.arts["fish"]!!, cAB(cols.first, cols.second))
        sprite("Reef Shark", "Reef Shark.png", "Predator fish, 16×16", ForgeArt.arts["shark"]!!, ForgeArt.pal('a' to 0xFF7E96AEL.toInt(), 'w' to 0xFFE8F0F6L.toInt()))
        for ((name, cols) in listOf("Bluebird" to (0xFF5E8AE2.toInt() to 0xFF2E569C), "Canary" to (0xFFF2D43B.toInt() to 0xFFC29A12)))
            sprite(name, "$name.png", "Small bird, 16×16", ForgeArt.arts["bird"]!!, cAB(cols.first, cols.second))
        sprite("Butterfly", "Butterfly.png", "Fluttering wings, 16×16", ForgeArt.arts["butterfly"]!!, ForgeArt.pal('a' to 0xFFE28AC2L.toInt(), 'w' to 0xFF6E3A5EL.toInt()))
        sprite("Forest Owl", "Forest Owl.png", "Wise bird, 16×16", ForgeArt.arts["owl"]!!, cAB(0xFFB08A5AL, 0xFF7A562E))

        // heroes
        sprite("Knight", "Knight.png", "Armoured hero, 16×16", ForgeArt.arts["knight"]!!, ForgeArt.pal('a' to STEEL.first, 's' to 0xFFE8B88AL.toInt(), 'w' to 0xFFFFFFFF.toInt(), 'A' to STEEL.second.toInt()))
        sprite("Court Mage", "Court Mage.png", "Robed caster, 16×16", ForgeArt.arts["mage"]!!, ForgeArt.pal('a' to 0xFF4E5A9EL.toInt(), 'b' to 0xFF7A6EC2L.toInt(), 's' to 0xFFE8B88AL.toInt(), 'w' to 0xFFB0A8E8L.toInt(), 'g' to G))
        sprite("Ranger", "Ranger.png", "Bow hero, 16×16", ForgeArt.arts["archer"]!!, ForgeArt.pal('a' to 0xFF4E7E3EL.toInt(), 's' to 0xFFE8B88AL.toInt(), 'w' to 0xFF7ACC5EL.toInt(), 'b' to WOODP.first.toInt()))
        sprite("Shadow Rogue", "Shadow Rogue.png", "Sneaky hero, 16×16", ForgeArt.arts["rogue"]!!, ForgeArt.pal('a' to 0xFF3A3F4EL.toInt(), 's' to 0xFFE8B88AL.toInt(), 'w' to 0xFF6E7682L.toInt(), 'm' to SILVER.first))
        sprite("Barbarian", "Barbarian.png", "Wild warrior, 16×16", ForgeArt.arts["barbarian"]!!, ForgeArt.pal('a' to 0xFF8A5A2BL.toInt(), 's' to 0xFFE0A87EL.toInt(), 'w' to 0xFF6E3A17L.toInt(), 'b' to WOODP.first.toInt()))
        sprite("Necromancer", "Necromancer.png", "Dark caster, 16×16", ForgeArt.arts["necromancer"]!!, ForgeArt.pal('h' to 0xFF2A2E3EL.toInt(), 'w' to 0xFF9AE2FFL.toInt(), 'm' to SILVER.first, 'g' to G))
        sprite("Sea Pirate", "Sea Pirate.png", "Captain, 16×16", ForgeArt.arts["pirate"]!!, ForgeArt.pal('h' to 0xFF2A2E3EL.toInt(), 'r' to 0xFFB03434L.toInt(), 's' to 0xFFE8B88AL.toInt(), 'm' to SILVER.first))
        // monsters
        sprite("Orc Warrior", "Orc Warrior.png", "Green brute, 16×16", ForgeArt.arts["orc"]!!, ForgeArt.pal('g' to 0xFF6E9C4EL.toInt(), 's' to 0xFF4E7A2EL.toInt(), 'r' to 0xFFB03434L.toInt()))
        sprite("Goblin Scout", "Goblin Scout.png", "Sneaky green, 16×16", ForgeArt.arts["goblin"]!!, ForgeArt.pal('g' to 0xFF8AB84EL.toInt(), 's' to 0xFF5E8C2EL.toInt(), 'r' to 0xFFC2442EL.toInt()))
        sprite("Cave Troll", "Cave Troll.png", "Huge brute, 16×16", ForgeArt.arts["troll"]!!, ForgeArt.pal('g' to 0xFF8A9A6EL.toInt(), 's' to 0xFF5E6E42L.toInt(), 'r' to 0xFFE2E2E2L.toInt()))
        sprite("Skeleton Warrior", "Skeleton Warrior.png", "Bony foe, 16×16", ForgeArt.arts["skeleton"]!!, ForgeArt.pal('w' to 0xFFF2ECDFL.toInt()))
        sprite("Rotting Zombie", "Rotting Zombie.png", "Undead shambler, 16×16", ForgeArt.arts["zombie"]!!, ForgeArt.pal('g' to 0xFF7A9A5EL.toInt(), 'o' to 0xFF2A3E1EL.toInt(), 'w' to 0xFFE2E2C2L.toInt()))
        sprite("Royal Mummy", "Royal Mummy.png", "Wrapped undead, 16×16", ForgeArt.arts["mummy"]!!, ForgeArt.pal('w' to 0xFFE8E0C8L.toInt(), 's' to 0xFFC2B08AL.toInt()))
        sprite("Fire Imp", "Fire Imp.png", "Little devil, 16×16", ForgeArt.arts["imp"]!!, ForgeArt.pal('r' to 0xFFE25E3BL.toInt(), 'w' to 0xFFF2D43BL.toInt()))
        sprite("Abyss Demon", "Abyss Demon.png", "Boss demon, 16×16", ForgeArt.arts["demon"]!!, ForgeArt.pal('r' to 0xFFB02A2AL.toInt(), 'w' to 0xFFF2D43BL.toInt()))
        sprite("Dragon Whelp", "Dragon Whelp.png", "Baby dragon, 16×16", ForgeArt.arts["dragonling"]!!, ForgeArt.pal('r' to 0xFFC2442EL.toInt(), 'w' to 0xFFF2D43BL.toInt()))
        sprite("Stone Golem", "Stone Golem.png", "Rock guardian, 16×16", ForgeArt.arts["golem"]!!, ForgeArt.pal('m' to 0xFF9AA2AEL.toInt(), 'M' to 0xFF6E7682L.toInt(), 'w' to 0xFFC9D4DFL.toInt()))
        // sci-fi
        sprite("Service Robot", "Service Robot.png", "Boxy droid, 16×16", ForgeArt.arts["robot"]!!, ForgeArt.pal('m' to 0xFFB8C2CCL.toInt(), 'M' to 0xFF7E8B99L.toInt(), 'g' to 0xFF6EE2FFL.toInt()))
        sprite("Hover Droid", "Hover Droid.png", "Floating bot, 16×16", ForgeArt.arts["droid"]!!, ForgeArt.pal('m' to 0xFFB8C2CCL.toInt(), 'g' to 0xFF6EE2FFL.toInt()))
        sprite("Assault Mech", "Assault Mech.png", "War machine, 16×16", ForgeArt.arts["mech"]!!, ForgeArt.pal('m' to 0xFF8A94A2L.toInt(), 'M' to 0xFF5A6472L.toInt(), 'g' to 0xFFFF6E4EL.toInt(), 'w' to 0xFFC9D4DFL.toInt()))
        sprite("Grey Alien", "Grey Alien.png", "Visiting friend, 16×16", ForgeArt.arts["alien"]!!, ForgeArt.pal('a' to 0xFF9AC2AEL.toInt(), 'g' to 0xFF4E5A6EL.toInt(), 'w' to 0xFF1A1C22L.toInt()))
        sprite("Flying Saucer", "Flying Saucer.png", "UFO, 16×16", ForgeArt.arts["ufo"]!!, ForgeArt.pal('b' to 0xFF9AA2AEL.toInt(), 'g' to 0xFF6EE2FFL.toInt()))
        sprite("Rocket", "Rocket.png", "Retro rocket, 16×16", ForgeArt.arts["rocket"]!!, ForgeArt.pal('a' to 0xFFE8ECF2L.toInt(), 'b' to 0xFFD84040L.toInt(), 'g' to 0xFFFFB03BL.toInt(), 'w' to 0xFF6E7682L.toInt()))
        sprite("Satellite", "Satellite.png", "Orbiter, 16×16", ForgeArt.arts["satellite"]!!, ForgeArt.pal('a' to 0xFF4E7AE2L.toInt(), 'm' to 0xFFB8C2CCL.toInt(), 'g' to 0xFF6EE2FFL.toInt()))
        for ((name, cols) in listOf("Ocean World" to (0xFF3B7BE2.toInt() to 0xFF2A56A8), "Jungle World" to (0xFF3BBE62.toInt() to 0xFF1F7E3E), "Red Planet" to (0xFFE2703B.toInt() to 0xFFA8481F)))
            sprite(name, "$name.png", "Planet with atmosphere, 16×16", ForgeArt.arts["planet"]!!, cAB(cols.first, cols.second))
        sprite("Crescent Moon", "Crescent Moon.png", "Night moon, 16×16", ForgeArt.arts["moon"]!!, ForgeArt.pal('a' to 0xFFE8ECF2L.toInt(), 'w' to 0xFFB8C2CCL.toInt()))
        sprite("Cartoon Sun", "Cartoon Sun.png", "Sunny day, 16×16", ForgeArt.arts["sun"]!!, ForgeArt.pal('g' to 0xFFFFC93CL.toInt(), 'w' to 0xFFFFE88AL.toInt()))
        sprite("Comet", "Comet.png", "Shooting star, 16×16", ForgeArt.arts["comet"]!!, ForgeArt.pal('a' to 0xFFE8ECF2L.toInt(), 'g' to 0xFF6EC2FFL.toInt(), 'w' to 0xFFFFFFFF.toInt()))

        // food
        sprite("Apple", "Apple.png", "Fresh fruit, 16×16", ForgeArt.arts["apple"]!!, cAB(0xFFE23B4EL, 0xFF9C1E2E))
        sprite("Banana", "Banana.png", "Peel included, 16×16", ForgeArt.arts["banana"]!!, cAB(0xFFF2D43BL, 0xFFC29A12))
        sprite("Carrot", "Carrot.png", "Crunchy root, 16×16", ForgeArt.arts["carrot"]!!, ForgeArt.pal('a' to 0xFFE27A2EL.toInt(), 'w' to 0xFFC25E12L.toInt(), 'g' to 0xFF4E9C3EL.toInt()))
        sprite("Bread Loaf", "Bread Loaf.png", "Bakery bread, 16×16", ForgeArt.arts["bread"]!!, ForgeArt.pal('a' to 0xFFC2904EL.toInt(), 'b' to 0xFFE8C88AL.toInt(), 'w' to 0xFF9A6A2EL.toInt()))
        sprite("Cheese Wedge", "Cheese Wedge.png", "Holey cheese, 16×16", ForgeArt.arts["cheese"]!!, cAB(0xFFF2C84EL, 0xFFC29A2A))
        sprite("Roast Meat", "Roast Meat.png", "Hearty meal, 16×16", ForgeArt.arts["meat"]!!, cAB(0xFFB05E3EL, 0xFF7E3A22))
        sprite("Egg", "Egg.png", "Breakfast, 16×16", ForgeArt.arts["egg"]!!, ForgeArt.pal('a' to 0xFFF2ECDFL.toInt(), 'w' to 0xFFC2B8A2L.toInt()))
        sprite("Milk Bottle", "Milk Bottle.png", "Dairy, 16×16", ForgeArt.arts["milk"]!!, ForgeArt.pal('a' to 0xFF5E9AE2L.toInt(), 'w' to 0xFFF2F6FAL.toInt(), 'g' to 0xFF8AC2FFL.toInt()))
        sprite("Hot Coffee", "Hot Coffee.png", "Morning fuel, 16×16", ForgeArt.arts["coffee"]!!, cAB(0xFFE8ECF2L, 0xFFB8C2CC))
        sprite("Birthday Cake", "Birthday Cake.png", "Party cake, 16×16", ForgeArt.arts["cake"]!!, ForgeArt.pal('a' to 0xFFF2EAD8L.toInt(), 'r' to 0xFFF2A8C2L.toInt(), 'R' to 0xFFD8789EL.toInt(), 'w' to 0xFFE23B5EL.toInt()))
        sprite("Corn Cob", "Corn Cob.png", "Golden corn, 16×16", ForgeArt.arts["corn"]!!, ForgeArt.pal('y' to 0xFFF2C84EL.toInt(), 'w' to 0xFFC29A2AL.toInt(), 'g' to 0xFF4E9C3EL.toInt()))
        sprite("Pumpkin", "Pumpkin.png", "Harvest pumpkin, 16×16", ForgeArt.arts["pumpkin"]!!, cAB(0xFFE27A2EL, 0xFFA84E12))
        sprite("Wild Berries", "Wild Berries.png", "Forest snack, 16×16", ForgeArt.arts["berry"]!!, cAB(0xFF7A3E8CL, 0xFF4E1E5E))

        // tools & misc
        sprite("Explorer Compass", "Explorer Compass.png", "Never lost, 16×16", ForgeArt.arts["compass"]!!, ForgeArt.pal('a' to 0xFFC2A84EL.toInt(), 'w' to 0xFF3A2E1EL.toInt()))
        sprite("Treasure Map", "Treasure Map.png", "X marks it, 16×16", ForgeArt.arts["map"]!!, ForgeArt.pal('w' to 0xFFE8DCC0L.toInt()))
        sprite("Treasure Pile", "Treasure Pile.png", "Loot pile, 16×16", ForgeArt.arts["treasure"]!!, ForgeArt.pal('e' to GOLD.first, 'g' to GOLD.second, 'w' to 0xFF6EE2FFL.toInt()))
        sprite("Iron Gear", "Iron Gear.png", "Machine part, 16×16", ForgeArt.arts["gear"]!!, cAB(STEEL.first, STEEL.second.toInt()))
        sprite("Power Cell", "Power Cell.png", "Sci-fi battery, 16×16", ForgeArt.arts["battery"]!!, ForgeArt.pal('a' to 0xFF3A3F4EL.toInt(), 'w' to 0xFF6EE2FFL.toInt(), 'g' to 0xFF6EE2FFL.toInt()))
        sprite("Logic Chip", "Logic Chip.png", "Circuit brain, 16×16", ForgeArt.arts["chip"]!!, ForgeArt.pal('a' to 0xFF2E5E3EL.toInt(), 'w' to 0xFF6EE2A8L.toInt()))
        for ((name, cols) in listOf("Gold Ore" to GOLD, "Silver Ore" to SILVER, "Crystal Ore" to (0xFF7AC2E2.toInt() to 0xFF3E7A9C)))
            sprite(name, "$name.png", "Mineable rock, 16×16", ForgeArt.arts["ore"]!!, ForgeArt.pal('a' to 0xFF9AA2AEL.toInt(), 'w' to 0xFFC9D4DFL.toInt(), 'g' to cols.first.toInt()))
        sprite("Ship Anchor", "Ship Anchor.png", "Nautical, 16×16", ForgeArt.arts["anchor"]!!, cAB(BRONZE.first, BRONZE.second))
        sprite("Wood Bucket", "Wood Bucket.png", "Handy pail, 16×16", ForgeArt.arts["bucket"]!!, cAB(WOODP.first, WOODP.second.toInt()))
        sprite("Coil Rope", "Coil Rope.png", "Climbing rope, 16×16", ForgeArt.arts["rope"]!!, cAB(0xFFC2A86EL, 0xFF8A7442))
        sprite("Quill Feather", "Quill Feather.png", "Writing feather, 16×16", ForgeArt.arts["feather"]!!, cAB(0xFFF2F6FAL, 0xFFB8C2CC))
        sprite("Ice Crystal", "Ice Crystal.png", "Frozen spike, 16×16", ForgeArt.arts["ice"]!!, ForgeArt.pal('w' to 0xFF9ADFFF.toInt(), 'g' to 0xFFD6F2FFL.toInt()))
        sprite("Autumn Leaf", "Autumn Leaf.png", "Falling leaf, 16×16", ForgeArt.arts["leaf"]!!, cAB(0xFFE2913BL, 0xFFB05E1E))
        sprite("Soap Bubble", "Soap Bubble.png", "Iridescent bubble, 16×16", ForgeArt.arts["bubble"]!!, ForgeArt.pal('w' to 0xFFB8E2FFL.toInt()))
        sprite("Lightning Bolt", "Lightning Bolt.png", "Storm strike, 16×16", ForgeArt.arts["bolt"]!!, ForgeArt.pal('w' to 0xFFB7C7FFL.toInt(), 'a' to 0xFF6E8AF2L.toInt()))
        sprite("Ice Shard", "Ice Shard.png", "Sharp crystal, 16×16", ForgeArt.arts["shard"]!!, ForgeArt.pal('w' to 0xFF9ADFFFL.toInt(), 'a' to 0xFF5E9AE2L.toInt()))
        sprite("Magic Portal", "Magic Portal.png", "Warp gate, 16×16", ForgeArt.arts["portal"]!!, ForgeArt.pal('a' to 0xFF5E2E9CL.toInt(), 'A' to 0xFF3E1E6EL.toInt(), 'g' to 0xFFB07AFFL.toInt(), 'w' to 0xFFE8DFFF.toInt()))

        // ------------------------------------------------------------ UI kit (pixel)
        for ((name, cols) in listOf("Green" to (0xFF4EC24E.toInt() to 0xFF2E8C2E), "Red" to (0xFFE24E4E.toInt() to 0xFF9C2A2A),
                "Blue" to (0xFF4E7AE2.toInt() to 0xFF2A4E9C), "Gold" to (0xFFE2B23B.toInt() to 0xFF9C7A12)))
            sprite("$name UI Button", "$name UI Button.png", "$name 9-slice style button, 16×16", ForgeArt.arts["button"]!!, cAB(cols.first, cols.second))
        sprite("Dark Panel", "Dark Panel.png", "Dark UI panel, 16×16", ForgeArt.arts["panel"]!!, ForgeArt.pal('a' to 0xFF2E3238L.toInt(), 'w' to 0xFF4E5A6EL.toInt()))
        sprite("Light Panel", "Light Panel.png", "Light UI panel, 16×16", ForgeArt.arts["panel"]!!, ForgeArt.pal('a' to 0xFFE8E4D8L.toInt(), 'w' to 0xFFF8F6F0L.toInt()))
        sprite("Empty Heart", "Empty Heart.png", "Missing-health heart, 16×16", ForgeArt.arts["heart"]!!, ForgeArt.pal('a' to 0xFF4E5A6EL.toInt(), 'w' to 0xFF6E7682L.toInt()))
        sprite("Empty Star", "Empty Star.png", "Unfilled star, 16×16", ForgeArt.arts["star"]!!, ForgeArt.pal('a' to 0xFF4E5A6EL.toInt(), 'w' to 0xFF6E7682L.toInt()))
        sprite("Checkbox Off", "Checkbox Off.png", "Unchecked box, 16×16", ForgeArt.arts["check"]!!, cAB(0xFF4E5A6EL, 0xFF323A46))
        sprite("Checkbox On", "Checkbox On.png", "Checked box, 16×16", ForgeArt.arts["checkOn"]!!, cAB(0xFF4E9C3EL, 0xFF2E7E2A))
        sprite("Slider Track", "Slider Track.png", "Slider background, 16×16", ForgeArt.arts["sliderTrack"]!!, cAB(0xFF323A46L, 0xFF232830))
        sprite("Slider Thumb", "Slider Thumb.png", "Slider handle, 16×16", ForgeArt.arts["sliderThumb"]!!, cAB(0xFFE8ECF2L, 0xFF9AA6B2))
        sprite("Golden Frame", "Golden Frame.png", "Decorative frame, 16×16", ForgeArt.arts["frame"]!!, cAB(GOLD.first, GOLD.second.toInt()))

        // ------------------------------------------------------------ tiles & textures (64×64)
        texItem("Bookshelf", "Bookshelf.png", "Library shelf with books, 64×64", "Textures") { texBookshelf() }
        texItem("Circuit Board", "Circuit Board.png", "Tech traces, 64×64", "Textures") { texCircuit() }
        texItem("Hull Plate", "Hull Plate.png", "Riveted sci-fi metal, 64×64", "Textures") { texHull() }
        texItem("Vertical Planks", "Vertical Planks.png", "Fence-style planks, 64×64", "Textures") { texPlanksV() }
        texItem("Moss Block", "Moss Block.png", "Overgrown stone, 64×64", "Textures") { texMoss() }
        texItem("Target Range", "Target Range.png", "Bullseye panel, 64×64", "Textures") { texTarget() }

        // ------------------------------------------------------------ sprite sheets + animations
        anim(AnimSpec("Gem Pulse (6 frames)", "ForgeGemPulse.png", "ForgeGemPulse.anim", 6, 1, 6, 8f, true,
            "Ruby gem pulsing + clip") { (0 until 6).map { i -> pulse(gemBase(), 0.85f + i * 0.09f) } })
        anim(AnimSpec("Heart Beat (4 frames)", "ForgeHeartBeat.png", "ForgeHeartBeat.anim", 4, 1, 4, 6f, true,
            "Pulsing heart + clip") { listOf(0, 1, 0, 2).map { beat(heartBase(), it) } })
        anim(AnimSpec("Star Twinkle (4 frames)", "ForgeStarTwinkle.png", "ForgeStarTwinkle.anim", 4, 1, 4, 8f, true,
            "Sparkling star + clip") { (0 until 4).map { twinkle(it) } })
        anim(AnimSpec("Smoke Puff (6 frames)", "ForgeSmoke.png", "ForgeSmoke.anim", 6, 1, 6, 10f, false,
            "Expanding smoke + clip") { (0 until 6).map { smoke(it) } })
        anim(AnimSpec("Spark Burst (5 frames)", "ForgeSparkBurst.png", "ForgeSparkBurst.anim", 5, 1, 5, 14f, false,
            "Radial sparks + clip") { (0 until 5).map { sparkBurst(it) } })
        anim(AnimSpec("Water Splash (6 frames)", "ForgeSplash.png", "ForgeSplash.anim", 6, 1, 6, 12f, false,
            "Droplet splash + clip") { (0 until 6).map { splash(it) } })
        anim(AnimSpec("Portal Swirl (8 frames)", "ForgePortalSwirl.png", "ForgePortalSwirl.anim", 8, 1, 8, 10f, true,
            "Rotating portal + clip") { (0 until 8).map { swirl(it) } })
        anim(AnimSpec("Warp In (6 frames)", "ForgeWarp.png", "ForgeWarp.anim", 6, 1, 6, 14f, false,
            "Teleport column + clip") { (0 until 6).map { warp(it) } })
        anim(AnimSpec("Water Ripple (4 frames)", "ForgeRipple.png", "ForgeRipple.anim", 4, 1, 4, 6f, true,
            "Concentric ripple + clip") { (0 until 4).map { ripple(it) } })
        anim(AnimSpec("Lava Bubble (5 frames)", "ForgeLavaBubble.png", "ForgeLavaBubble.anim", 5, 1, 5, 6f, true,
            "Bubbling lava + clip") { (0 until 5).map { lavaBubble(it) } })
        anim(AnimSpec("Torch Flicker (4 frames)", "ForgeTorchFlicker.png", "ForgeTorchFlicker.anim", 4, 1, 4, 10f, true,
            "Flickering flame + clip") { (0 until 4).map { flicker(it) } })
        anim(AnimSpec("Ghost Float (4 frames)", "ForgeGhostFloat.png", "ForgeGhostFloat.anim", 4, 1, 4, 5f, true,
            "Hovering ghost + clip") { (0 until 4).map { ghostFloat(it) } })
        anim(AnimSpec("Bat Wings (4 frames)", "ForgeBatWings.png", "ForgeBatWings.anim", 4, 1, 4, 10f, true,
            "Flying bat + clip") { (0 until 4).map { batWings(it) } })
        anim(AnimSpec("Bird Flight (4 frames)", "ForgeBirdFlight.png", "ForgeBirdFlight.anim", 4, 1, 4, 8f, true,
            "Flapping bird + clip") { (0 until 4).map { birdFlight(it) } })
        anim(AnimSpec("Butterfly Flutter (4 frames)", "ForgeButterfly.png", "ForgeButterfly.anim", 4, 1, 4, 8f, true,
            "Wing flutter + clip") { (0 until 4).map { butterfly(it) } })
        anim(AnimSpec("Fish Swim (4 frames)", "ForgeFishSwim.png", "ForgeFishSwim.anim", 4, 1, 4, 8f, true,
            "Swimming goldfish + clip") { (0 until 4).map { fishSwim(it) } })
        anim(AnimSpec("Slime Jump (4 frames)", "ForgeSlimeJump.png", "ForgeSlimeJump.anim", 4, 1, 4, 8f, true,
            "Squash & stretch hop + clip") { (0 until 4).map { slimeJump(it) } })
        anim(AnimSpec("Mushroom Bounce (4 frames)", "ForgeMushroomBounce.png", "ForgeMushroomBounce.anim", 4, 1, 4, 8f, true,
            "Bouncy mushroom + clip") { (0 until 4).map { i -> pulse(mushroomBase(), if (i == 1) 1.15f else 1f) } })
        anim(AnimSpec("Flag Wave (4 frames)", "ForgeFlagWave.png", "ForgeFlagWave.anim", 4, 1, 4, 8f, true,
            "Waving banner + clip") { (0 until 4).map { flagWave(it) } })
        anim(AnimSpec("Bubble Rise (4 frames)", "ForgeBubbleRise.png", "ForgeBubbleRise.anim", 4, 1, 4, 8f, true,
            "Rising bubbles + clip") { (0 until 4).map { bubbleRise(it) } })
        anim(AnimSpec("Muzzle Flash (3 frames)", "ForgeMuzzle.png", "ForgeMuzzle.anim", 3, 1, 3, 18f, false,
            "Gun flash + clip") { (0 until 3).map { muzzle(it) } })
        anim(AnimSpec("Laser Charge (4 frames)", "ForgeLaserCharge.png", "ForgeLaserCharge.anim", 4, 1, 4, 10f, false,
            "Charging beam + clip") { (0 until 4).map { laserCharge(it) } })
        anim(AnimSpec("Shockwave (6 frames)", "ForgeShockwave.png", "ForgeShockwave.anim", 6, 1, 6, 14f, false,
            "Expanding ring + clip") { (0 until 6).map { shockwave(it) } })
        anim(AnimSpec("Dust Puff (4 frames)", "ForgeDust.png", "ForgeDust.anim", 4, 1, 4, 12f, false,
            "Landing dust + clip") { (0 until 4).map { dust(it) } })
        anim(AnimSpec("Lightning Flash (3 frames)", "ForgeLightning.png", "ForgeLightning.anim", 3, 1, 3, 12f, false,
            "Bolt flicker + clip") { (0 until 3).map { lightning(it) } })
        anim(AnimSpec("Ice Shatter (5 frames)", "ForgeIceShatter.png", "ForgeIceShatter.anim", 5, 1, 5, 12f, false,
            "Shattering ice + clip") { (0 until 5).map { iceShatter(it) } })
        anim(AnimSpec("Leaves Fall (4 frames)", "ForgeLeaves.png", "ForgeLeaves.anim", 4, 1, 4, 6f, true,
            "Autumn leaves + clip") { (0 until 4).map { leavesFall(it) } })
        anim(AnimSpec("Gear Turn (4 frames)", "ForgeGearTurn.png", "ForgeGearTurn.anim", 4, 1, 4, 8f, true,
            "Rotating gear + clip") { gearFrames() })
        anim(AnimSpec("Robot Walk (4 frames)", "ForgeRobotWalk.png", "ForgeRobotWalk.anim", 4, 1, 4, 8f, true,
            "Marching robot + clip") { (0 until 4).map { robotWalk(it) } })
        anim(AnimSpec("Shield Flash (4 frames)", "ForgeShieldFlash.png", "ForgeShieldFlash.anim", 4, 1, 4, 10f, true,
            "Shield glow + clip") { (0 until 4).map { shieldFlash(it) } })

        // ------------------------------------------------------------ sounds
        sfx("Coin Double", "forge_coin2.wav", "Two-coin pickup chime") { S.run { wav { tone(0f, .07f, 1047f, 1047f, .45f, 1); tone(.06f, .09f, 1319f, 1319f, .45f, 1); tone(.14f, .22f, 1568f, 1568f, .45f, 1) } } }
        sfx("Gem Collect", "forge_gem.wav", "Crystal sparkle") { S.run { wav { tone(0f, .3f, 1976f, 2637f, .35f); tone(.02f, .25f, 2637f, 3136f, .2f) } } }
        sfx("Extra Life", "forge_life.wav", "Happy rising jingle") { S.run { arp(0f, .09f, .4f, 1, 523f, 659f, 784f, 1047f) } }
        sfx("Level Up", "forge_level.wav", "Fanfare run") { S.run { arp(0f, .11f, .5f, 1, 392f, 523f, 659f, 784f, 1047f) } }
        sfx("Quest Complete", "forge_quest.wav", "Golden resolve") { S.run { arp(0f, .13f, .45f, 0, 523f, 659f, 784f, 659f, 1047f) } }
        sfx("Achievement", "forge_achieve.wav", "Badge unlock") { S.run { wav { tone(0f, .08f, 880f, 880f, .4f, 2); tone(.1f, .3f, 1760f, 1760f, .4f, 0) } } }
        sfx("Warp Out", "forge_warp.wav", "Descending teleport") { S.run { wav { tone(0f, .4f, 1200f, 180f, .45f, 2); noise(.05f, .3f, .18f, .5f) } } }
        sfx("Teleport In", "forge_tele.wav", "Ascending teleport") { S.run { wav { tone(0f, .35f, 200f, 1400f, .45f, 2); tone(.1f, .2f, 2000f, 2600f, .2f) } } }
        sfx("Phase Shift", "forge_phase.wav", "Ghostly pass-through") { S.run { wav { tone(0f, .3f, 600f, 900f, .3f, 0); tone(.05f, .3f, 610f, 890f, .2f, 2) } } }
        sfx("Heal", "forge_heal.wav", "Warm recovery") { S.run { arp(0f, .08f, .35f, 0, 659f, 784f, 988f) } }
        sfx("Player Hurt", "forge_hurt.wav", "Pained grunt-ish drop") { S.run { wav { tone(0f, .2f, 440f, 160f, .5f, 1); noise(0f, .12f, .2f, .4f) } } }
        sfx("Critical Hit", "forge_crit.wav", "Sharp impact") { S.run { wav { tone(0f, .12f, 220f, 90f, .6f, 1); noise(0f, .1f, .35f, .7f); tone(.02f, .25f, 1568f, 392f, .3f) } } }
        sfx("Dodge", "forge_dodge.wav", "Quick swish") { S.run { wav { noise(0f, .18f, .3f, .9f); tone(0f, .12f, 900f, 1400f, .18f) } } }
        sfx("Parry", "forge_parry.wav", "Metal ping") { S.run { wav { tone(0f, .05f, 2500f, 2500f, .4f, 2); tone(.02f, .25f, 3400f, 2600f, .25f) } } }
        sfx("Spell Cast", "forge_cast.wav", "Arcane swell") { S.run { wav { tone(0f, .35f, 300f, 1200f, .35f, 0); tone(.08f, .25f, 800f, 1600f, .25f, 2) } } }
        sfx("Fireball", "forge_fireball.wav", "Whoosh + flare") { S.run { wav { noise(0f, .35f, .4f, .5f); tone(0f, .3f, 200f, 90f, .35f, 1) } } }
        sfx("Ice Bolt", "forge_ice.wav", "Crystalline shot") { S.run { wav { tone(0f, .22f, 1800f, 2600f, .3f, 2); noise(.05f, .15f, .12f, .9f) } } }
        sfx("Thunder", "forge_thunder.wav", "Distant rumble") { S.run { wav { noise(0f, .7f, .5f, .18f); tone(0f, .5f, 90f, 40f, .4f, 1) } } }
        sfx("Poison Hit", "forge_poison.wav", "Toxic bubble") { S.run { wav { tone(0f, .08f, 300f, 500f, .3f, 2); tone(.1f, .08f, 350f, 550f, .3f, 2); tone(.2f, .08f, 400f, 600f, .3f, 2) } } }
        sfx("Door Open", "forge_door.wav", "Slow creak") { S.run { wav { tone(0f, .4f, 180f, 240f, .3f, 3); noise(.1f, .3f, .08f, .3f) } } }
        sfx("Door Slam", "forge_slam.wav", "Heavy close") { S.run { wav { tone(0f, .15f, 140f, 60f, .6f, 1); noise(0f, .12f, .4f, .5f) } } }
        sfx("Unlock", "forge_unlock.wav", "Key turn click") { S.run { wav { tone(0f, .05f, 900f, 700f, .35f, 2); tone(.08f, .05f, 1200f, 900f, .35f, 2); tone(.16f, .12f, 1600f, 1300f, .3f) } } }
        sfx("Trap Trigger", "forge_trap.wav", "Snap + spring") { S.run { wav { tone(0f, .06f, 200f, 120f, .5f, 1); tone(.06f, .25f, 300f, 1400f, .35f, 2) } } }
        sfx("Alarm", "forge_alarm.wav", "Rising alert") { S.run { wav { tone(0f, .18f, 700f, 1000f, .4f, 1); tone(.2f, .18f, 700f, 1000f, .4f, 1) } } }
        sfx("Heart Monitor", "forge_pulse.wav", "Medical beep") { S.run { wav { tone(0f, .09f, 1200f, 1200f, .4f, 0); tone(.35f, .09f, 1200f, 1200f, .35f, 0) } } }
        sfx("Magnet", "forge_magnet.wav", "Attract hum") { S.run { wav { tone(0f, .3f, 200f, 700f, .3f, 3); tone(.1f, .2f, 400f, 900f, .2f, 0) } } }
        sfx("Bounce Pad", "forge_spring.wav", "Boing!") { S.run { wav { tone(0f, .3f, 150f, 900f, .5f, 1); tone(0f, .18f, 300f, 1500f, .2f) } } }
        sfx("Big Explosion", "forge_bigboom.wav", "Screen-shaking boom") { S.run { wav { noise(0f, .8f, .6f, .22f); tone(0f, .6f, 120f, 30f, .55f, 1); noise(.05f, .4f, .3f, .5f) } } }

        // ------------------------------------------------------------ music
        music("Adventure Awaits", "forge_adventure.song", "Heroic chiptune overworld theme", 0, 11)
        music("Boss Rush", "forge_boss.song", "Intense battle arrangement", 1, 7)
        music("Lo-Fi Café", "forge_lofi.song", "Relaxed study loop", 2, 5)
        music("Neon Circuit", "forge_neon.song", "High-speed racing theme", 3, 9)
        music("Haunted Halls", "forge_haunted.song", "Eerie dungeon ambience", 4, 3)
        music("Champion's March", "forge_champion.song", "Victory fanfare suite", 5, 2)
        music("Pause Menu", "forge_menu.song", "Calm menu theme", 6, 4)
        music("Block World", "forge_block.song", "Mining sandbox tune", 7, 6)
    }

        private fun darken(c: Long): Int {
        val r = ((c shr 16) and 0xFF) * 55 / 100
        val g = ((c shr 8) and 0xFF) * 55 / 100
        val b = (c and 0xFF) * 55 / 100
        return (0xFF000000L or (r shl 16) or (g shl 8) or b).toInt()
    }

    private fun artOf(name: String) = ForgeArt.arts.getValue(name)

    private fun base16(name: String, pal: Map<Char, Int>): Px = Px(16, 16).also { it.art(artOf(name), pal) }

    // bases for sheet generators
    private fun gemBase(): Px = base16("gem", cAB(0xFFE23B4EL, 0xFF8C1220))
    private fun heartBase(): Px = base16("heart", cAB(0xFFE23B4EL, 0xFF8C1220))
    private fun mushroomBase(): Px = base16("mushroom", ForgeArt.pal('a' to 0xFFE23B4E.toInt(), 'A' to 0xFF8C1220.toInt(), 'w' to 0xFFF2ECDF.toInt()))

    // ==================================================================== frame generators

    private fun pulse(src: Px, f: Float): Px = src.tint(f)

    private fun beat(src: Px, step: Int): Px = when (step) { 1 -> src.tint(1.25f); 2 -> src.tint(0.85f); else -> src }

    private fun twinkle(step: Int): Px {
        val p = base16("star", ForgeArt.pal('a' to GOLD.first, 'A' to GOLD.second, 'w' to W))
        if (step % 2 == 1) p.line(0, 0, 15, 15, G) else if (step == 2) p.line(15, 0, 0, 15, G)
        return p
    }

    private fun smoke(step: Int): Px {
        val p = Px(16, 16)
        val r = 2f + step * 1.4f
        val cy = 12f - step * 1.6f
        val shade = 0xFF787E88L.toInt() - step * 0x080808
        p.disc(8f, cy, r, shade)
        p.disc(6f, cy + 1, r * 0.55f, 0xFF9AA2AEL.toInt() - step * 0x060606)
        return p
    }

    private fun sparkBurst(step: Int): Px {
        val p = Px(16, 16)
        val n = 3 + step * 2
        for (k in 0 until 8) {
            val a = PI / 4 * k + 0.3
            val r1 = 1 + step; val r2 = 2 + step * 1.8f
            p.line((8 + cos(a) * r1).toInt(), (8 + sin(a) * r1).toInt(), (8 + cos(a) * r2).toInt(), (8 + sin(a) * r2).toInt(), if (k % 2 == 0) G else GOLD.first.toInt())
        }
        if (step < 3) p.disc(8f, 8f, 1.6f, W)
        return p
    }

    private fun splash(step: Int): Px {
        val p = Px(16, 16)
        val h = step * 2
        val col = 0xFF6EC2FFL.toInt()
        for (k in -2..2) p.disc(8f + k * 2.4f, (13 - h - abs(k)).toFloat(), 1.1f, col)
        if (step in 2..3) p.disc(8f, 13f, 2.2f + step, 0x66246A8C)
        return p
    }

    private fun swirl(step: Int): Px {
        val p = Px(16, 16)
        p.disc(8f, 8f, 6.2f, 0xFF3E1E6EL.toInt())
        for (k in 0 until 3) {
            for (t in 0 until 22) {
                val a = PI * 2 * t / 22 + PI * 2 * k / 3 + step * PI / 4
                val r = 1.4f + t * 0.22f
                p.set((8 + cos(a) * r).toInt(), (8 + sin(a) * r).toInt(), if (k == 0) 0xFFB07AFF.toInt() else if (k == 1) 0xFF8A5EC2L.toInt() else 0xFF5E2E9CL.toInt())
            }
        }
        p.disc(8f, 8f, 1.5f, W)
        return p
    }

    private fun warp(step: Int): Px {
        val p = Px(16, 16)
        val w = if (step < 3) 1 + step * 2 else max(1, 7 - (step - 3) * 2)
        val hgt = 4 + step * 2
        for (y in 15 downTo (16 - hgt).coerceAtLeast(0))
            for (x in (8 - w)..(8 + w - 1)) {
                val a = 0xFFB07AFF.toInt() - (15 - y) * 0x0A0A0A
                p.set(x, y, if (x == 8 - w || x == 8 + w - 1) 0xFF6E3ABEL.toInt() else a)
            }
        return p
    }

    private fun ripple(step: Int): Px {
        val p = Px(16, 16)
        p.ring(8f, 8f, 3f + step * 2.2f, 1.2f, 0xFF6EC2FFL.toInt() - step * 0x141414)
        if (step == 0) p.disc(8f, 8f, 2.2f, 0xFF9ADFFFL.toInt())
        return p
    }

    private fun lavaBubble(step: Int): Px {
        val p = Px(16, 16)
        p.rect(0, 11, 15, 15, 0xFFE2703BL.toInt())
        p.rect(0, 12, 15, 15, 0xFFC24A12L.toInt())
        val h = intArrayOf(2, 4, 6, 3, 1)[step.coerceIn(0, 4)]
        p.disc(8f, (11 - h).toFloat(), 1.2f + step * 0.15f, 0xFFFFC93CL.toInt())
        if (h > 4) { p.disc(5f, 4f, 0.9f, 0xFFFFC93CL.toInt()); p.disc(11f, 3f, 0.8f, 0xFFFFC93CL.toInt()) }
        return p
    }

    private fun flicker(step: Int): Px {
        val p = base16("torch", ForgeArt.pal('a' to WOODP.first, 'A' to WOODP.second.toInt()))
        val dx = intArrayOf(0, 1, 0, -1)[step]
        val flame = Px(16, 16)
        flame.disc(8f + dx, 5.5f, 2.4f, 0xFFFF9A3BL.toInt())
        flame.disc(8f + dx, 5f, 1.3f, G)
        for (y in 0 until 16) for (x in 0 until 16) if (flame[x, y] != 0) p.set(x, y, flame[x, y])
        return p
    }

    private fun ghostFloat(step: Int): Px {
        val dy = intArrayOf(0, -1, -1, 0)[step]
        return base16("ghost", ForgeArt.pal('a' to 0xFFE8F0FF.toInt(), 'w' to 0xFF2E3642.toInt())).shifted(0, dy)
    }

    private fun batWings(step: Int): Px {
        val p = Px(16, 16)
        val rows = ForgeArt.arts.getValue("bat")
        val pal = ForgeArt.pal('a' to 0xFF6E5A8C.toInt(), 'b' to 0xFF463861.toInt())
        p.art(rows, pal)
        val dy = intArrayOf(-1, 0, 1, 0)[step]
        for (x in 0 until 6) { // shear the wings
            val off = (dy * (6 - x) / 6)
            val col = IntArray(16) { y -> p[x, y] }
            for (y in 0 until 16) p.set(x, y, if (y + off in 0 until 16) col[y + off] else 0)
            val x2 = 15 - x
            val col2 = IntArray(16) { y -> p[x2, y] }
            for (y in 0 until 16) p.set(x2, y, if (y + off in 0 until 16) col2[y + off] else 0)
        }
        return p
    }

    private fun birdFlight(step: Int): Px {
        val p = base16("bird", ForgeArt.pal('a' to 0xFF5E8AE2.toInt(), 'A' to 0xFF2E569C.toInt(), 'w' to W))
        // wing line up / mid / down
        val wy = intArrayOf(6, 8, 10, 8)[step]
        p.line(6, wy, 3, wy + intArrayOf(-2, 0, 2, 0)[step], W)
        return p
    }

    private fun butterfly(step: Int): Px {
        val squeeze = intArrayOf(0, 1, 3, 1)[step] // columns to pull in
        val p = Px(16, 16)
        val rows = ForgeArt.arts.getValue("butterfly")
        val pal = ForgeArt.pal('a' to 0xFFE28AC2.toInt(), 'w' to 0xFF6E3A5E.toInt())
        val src = Px(16, 16); src.art(rows, pal)
        for (y in 0 until 16) for (x in 0 until 16) {
            val c = src[x, y]
            if (c == 0) continue
            val nx = if (x < 8) x + squeeze else x - squeeze
            p.set(nx, y, c)
        }
        return p
    }

    private fun fishSwim(step: Int): Px {
        val p = base16("fish", ForgeArt.pal('a' to 0xFFE2913B.toInt(), 'A' to 0xFF9C5A12.toInt(), 'w' to W))
        val dx = if (step < 2) -1 else 1
        for (y in 0 until 16) { val c = p[13, y]; if (c != 0) { p.set(13, y, 0); p.set(13 + dx, y, c) } }
        return p
    }

    private fun slimeJump(step: Int): Px {
        val squashed = intArrayOf(0, 1, 0, 2)[step] // 0 normal, 1 stretch, 2 squash
        val p = Px(16, 16)
        val src = base16("slime", ForgeArt.pal('a' to 0xFF5ED44E.toInt(), 'A' to 0xFF2E8C22.toInt(), 'w' to W))
        for (y in 0 until 16) for (x in 0 until 16) {
            var c = src[x, y]
            if (c == 0) continue
            var yy = y
            if (squashed == 1) yy = 2 + ((y - 2) * 13) / 13
            if (squashed == 2) yy = 4 + ((y - 4) * 11) / 13
            if (squashed == 2) { val xx = 8 + (x - 8) * 11 / 10; p.set(xx, yy, c) } else p.set(x, yy, c)
        }
        return p
    }

    private fun flagWave(step: Int): Px {
        val p = base16("flag", ForgeArt.pal('a' to 0xFFD84040.toInt(), 'A' to 0xFF8C1E1E.toInt(), 'M' to 0xFF6E4A2A.toInt()))
        for (y in 0 until 16) for (x in 6 until 16) {
            val c = p[x, y]
            if (c == 0) continue
            val dy = sin((x + step * 2) * 0.9).toInt()
            p.set(x, y, 0); p.set(x, (y + dy).coerceIn(0, 15), c)
        }
        return p
    }

    private fun bubbleRise(step: Int): Px {
        val p = Px(16, 16)
        val col = 0xFFB8E2FFL.toInt()
        p.disc(6f, (13 - step * 2).toFloat(), 1.6f, 0); p.ring(6f, (13 - step * 2).toFloat(), 1.8f, 1f, col)
        p.disc(11f, (14 - ((step + 2) % 4) * 2).toFloat(), 1.1f, 0); p.ring(11f, (14 - ((step + 2) % 4) * 2).toFloat(), 1.3f, 1f, col)
        return p
    }

    private fun muzzle(step: Int): Px {
        val p = Px(16, 16)
        if (step == 0) p.disc(11f, 8f, 2.4f, G)
        else if (step == 1) {
            for (k in 0 until 6) { val a = PI / 3 * k; p.line(11, 8, (11 + cos(a) * 4.5f).toInt(), (8 + sin(a) * 4.5f).toInt(), GOLD.first.toInt()) }
            p.disc(11f, 8f, 1.4f, W)
        }
        return p
    }

    private fun laserCharge(step: Int): Px {
        val p = Px(16, 16)
        val r = 0.8f + step * 1.1f
        p.disc(8f, 8f, r, 0xFFFF5E5EL.toInt())
        p.disc(8f, 8f, r * 0.5f, W)
        if (step == 3) { p.ring(8f, 8f, 5.5f, 1f, 0xFFB02020L.toInt()) }
        return p
    }

    private fun shockwave(step: Int): Px {
        val p = Px(16, 16)
        val r = 1.5f + step * 2.1f
        p.ring(8f, 8f, r, 1.4f, 0xFFB7C7FF.toInt() - step * 0x101010)
        return p
    }

    private fun dust(step: Int): Px {
        val p = Px(16, 16)
        val spread = 1 + step * 1.6f
        for (k in -3..3) p.disc(8f + k * spread, 12.5f - abs(k) * 0.8f, 1.2f + step * 0.2f, 0xFFC9BBA2L.toInt() - step * 0x0C0C0C)
        return p
    }

    private fun lightning(step: Int): Px {
        val p = Px(16, 16)
        if (step == 1) {
            val rows = ForgeArt.arts.getValue("bolt")
            p.art(rows, ForgeArt.pal('w' to 0xFFE2EAFF.toInt(), 'a' to 0xFF8AA2FFL.toInt()))
        } else if (step == 2) {
            for (y in 0 until 16) for (x in 0 until 16) if (base16("bolt", ForgeArt.pal('w' to W.toInt(), 'a' to W.toInt()))[x, y] != 0 && (x + y) % 2 == 0) p.set(x, y, 0xFF8AA2FF.toInt())
        }
        return p
    }

    private fun iceShatter(step: Int): Px {
        val p = Px(16, 16)
        if (step == 0) { p.disc(8f, 8f, 3.2f, 0xFF9ADFFFL.toInt()); p.disc(8f, 8f, 1.6f, W); return p }
        val n = step * 2
        for (k in 0 until 6) {
            val a = PI / 3 * k + 0.4
            val r = 2f + step * 2f
            p.disc((8 + cos(a) * r).toFloat(), (8 + sin(a) * r).toFloat(), max(0.8f, 1.6f - step * 0.25f), 0xFF9ADFFF.toInt() - n * 0x080808)
        }
        return p
    }

    private fun leavesFall(step: Int): Px {
        val p = Px(16, 16)
        val leaf = base16("leaf", ForgeArt.pal('a' to 0xFFE2913B.toInt(), 'A' to 0xFFB05E1E.toInt(), 'w' to W))
        val positions = arrayOf(floatArrayOf(3f, 1f), floatArrayOf(9f, 5f), floatArrayOf(4f, 9f), floatArrayOf(11f, 12f))
        val (lx, ly) = positions[step]
        for (y in 0 until 16) for (x in 0 until 16) if (leaf[x, y] != 0) p.set(x + lx.toInt(), y + ly.toInt(), leaf[x, y])
        return p
    }

    private fun gearFrames(): List<Px> {
        val g = base16("gear", ForgeArt.pal('a' to STEEL.first, 'A' to STEEL.second, 'w' to W))
        var cur = g
        val out = ArrayList<Px>(4)
        for (i in 0 until 4) { out.add(cur); cur = cur.rotatedQuarter() }
        return out
    }

    private fun robotWalk(step: Int): Px {
        val p = Px(16, 16)
        val rows = ForgeArt.arts.getValue("robot")
        p.art(rows, ForgeArt.pal('m' to 0xFFB8C2CC.toInt(), 'M' to 0xFF7E8B99.toInt(), 'g' to 0xFF6EE2FF.toInt()))
        val bob = intArrayOf(0, -1, 0, 0)[step]
        if (bob != 0) for (y in 0 until 16) for (x in 0 until 16) { val c = p[x, y]; if (c != 0) { p.set(x, y, 0); p.set(x, y + bob, c) } }
        val legs = arrayOf(2 to 0, 0 to 2, 2 to 0, 0 to 2)[step]
        p.rect(4, 12, 4, 12 + legs.first, 0xFF7E8B99.toInt())
        p.rect(11, 12, 11, 12 + legs.second, 0xFF7E8B99.toInt())
        return p
    }

    private fun shieldFlash(step: Int): Px {
        val p = base16("shield", ForgeArt.pal('a' to 0xFF4E7AE2.toInt(), 'A' to 0xFF2A4E9C.toInt(), 'c' to GOLD.first.toInt(), 'h' to 0xFF4E7AE2.toInt()))
        if (step in 1..2) {
            val col = if (step == 1) 0xCCFFFFFF.toInt() else 0x669ADFFFL.toInt()
            p.ring(8f, 7f, 6.5f, 1.1f, col)
        }
        return p
    }

    // ==================================================================== texture generators (64×64)

    private fun grid64(seed: Int): Pair<Px, Random> = Px(64, 64) to Random(seed)

    private fun texBookshelf(): Px {
        val (p, r) = grid64(11)
        p.rect(0, 0, 63, 63, 0xFF5E3A17L.toInt())
        p.rect(2, 2, 61, 61, 0xFF3A2410L.toInt())
        for (row in 0 until 3) {
            val y0 = 3 + row * 20
            p.rect(3, y0, 60, y0 + 16, 0xFF2A1A0AL.toInt())
            var x = 4
            while (x < 58) {
                val w = 3 + r.nextInt(4)
                val col = intArrayOf(0xFFB03B4E, 0xFF3B5EB0, 0xFF4E9C3E, 0xFFC2904E, 0xFF7A4EA2)[r.nextInt(5)].toInt()
                p.rect(x, y0 + 1 + r.nextInt(2), x + w, y0 + 15, col)
                p.rect(x, y0 + 1 + r.nextInt(2), x + w, y0 + 15, darken(col.toLong()))
                x += w + 1
            }
        }
        return p
    }

    private fun texCircuit(): Px {
        val (p, r) = grid64(22)
        p.fill(0xFF10322AL.toInt())
        for (k in 0 until 26) {
            var x = r.nextInt(64); var y = r.nextInt(64)
            val col = intArrayOf(0xFF2E8C6E, 0xFF3EBE8E, 0xFF1E5E4A)[r.nextInt(3)].toInt()
            repeat(5 + r.nextInt(8)) {
                if (r.nextBoolean()) x = (x + (if (r.nextBoolean()) 4 else -4)).coerceIn(0, 63)
                else y = (y + (if (r.nextBoolean()) 4 else -4)).coerceIn(0, 63)
                p.set(x, y, col); p.set((x + 1).coerceAtMost(63), y, col)
            }
            p.disc(x + 0.5f, y + 0.5f, 1.4f, 0xFF6EE2B0L.toInt())
        }
        return p
    }

    private fun texHull(): Px {
        val (p, r) = grid64(33)
        p.fill(0xFF5A6472L.toInt())
        for (i in 0 until 200) p.set(r.nextInt(64), r.nextInt(64), 0xFF4E5A66L.toInt())
        for (gy in 0 until 2) for (gx in 0 until 2) {
            val x0 = 2 + gx * 32; val y0 = 2 + gy * 32
            p.rect(x0, y0, x0 + 28, y0 + 28, 0xFF6E7A88L.toInt())
            p.rect(x0, y0, x0 + 28, y0, 0xFF8A96A2L.toInt())
            p.rect(x0, y0 + 28, x0 + 28, y0 + 28, 0xFF4A545EL.toInt())
            for (d in listOf(2 to 2, 26 to 2, 2 to 26, 26 to 26)) p.disc((x0 + d.first).toFloat(), (y0 + d.second).toFloat(), 1.3f, 0xFF3A424AL.toInt())
        }
        return p
    }

    private fun texPlanksV(): Px {
        val (p, r) = grid64(44)
        for (col in 0 until 4) {
            val base = if (col % 2 == 0) 0xFFB0854AL.toInt() else 0xFFA0763EL.toInt()
            p.rect(col * 16, 0, col * 16 + 14, 63, base)
            for (k in 0 until 14) p.set(col * 16 + 1 + r.nextInt(13), r.nextInt(64), darken(base.toLong()))
            p.rect(col * 16 + 15, 0, col * 16 + 15, 63, 0xFF5E3A17L.toInt())
        }
        return p
    }

    private fun texMoss(): Px {
        val (p, r) = grid64(55)
        p.fill(0xFF7E868EL.toInt())
        for (i in 0 until 500) {
            val x = r.nextInt(64); val y = r.nextInt(64)
            p.set(x, y, if (r.nextBoolean()) 0xFF4E9C3EL.toInt() else 0xFF3E7E2EL.toInt())
        }
        for (k in 0 until 10) p.disc(r.nextInt(64).toFloat(), r.nextInt(64).toFloat(), 2f + r.nextInt(3), 0xFF5E4A32L.toInt())
        return p
    }

    private fun texTarget(): Px {
        val p = Px(64, 64)
        p.fill(0xFFE8E4D8L.toInt())
        val cols = intArrayOf(0xFFE8E4D8, 0xFFD84040, 0xFFE8E4D8, 0xFFD84040, 0xFFB02020)
        for (i in 0 until 5) p.disc(32f, 32f, (28 - i * 5.6f), cols[i].toInt())
        p.disc(32f, 32f, 3f, 0xFFB02020L.toInt())
        return p
    }

    // ==================================================================== SFX synth

    private object S {
        private const val RATE = 22050

        fun wav(block: Syn.() -> Unit): ByteArray {
            val s = Syn(FloatArray(RATE * 1))
            s.block()
            return AssetLibrary.Sfx.wav(s.b)
        }

        class Syn(val b: FloatArray) {
            fun tone(start: Float, dur: Float, f0: Float, f1: Float, vol: Float, wave: Int = 0, decay: Float = 2f) {
                var ph = 0.0
                val s0 = (start * RATE).toInt(); val n = (dur * RATE).toInt()
                for (i in 0 until n) {
                    val idx = s0 + i; if (idx >= b.size) break
                    val t = i / n.toFloat()
                    val f = f0 + (f1 - f0) * t
                    ph += f / RATE
                    val p1 = ph % 1.0
                    val v = when (wave) {
                        1 -> if (p1 < 0.5) 0.6f else -0.6f
                        2 -> (4 * abs(p1 - 0.5) - 1).toFloat()
                        3 -> sin(ph * 2 * PI * 0.5).toFloat() * 0.7f
                        else -> sin(ph * 2 * PI).toFloat()
                    }
                    val env = minOf(1f, i / (0.004f * RATE)) * (1 - t).let { Math.pow(it.toDouble(), decay.toDouble()).toFloat() }
                    b[idx] += v * env * vol
                }
            }

            fun noise(start: Float, dur: Float, vol: Float, lp: Float, seed: Int = 7) {
                val r = Random(seed); var y = 0f
                val s0 = (start * RATE).toInt(); val n = (dur * RATE).toInt()
                for (i in 0 until n) {
                    val idx = s0 + i; if (idx >= b.size) break
                    val t = i / n.toFloat()
                    y += (r.nextFloat() * 2 - 1 - y) * lp * (1 - t * 0.8f)
                    b[idx] += y * vol * (1 - t) * (1 - t)
                }
            }

            fun arp(start: Float, step: Float, vol: Float, wave: Int, vararg freqs: Float) {
                freqs.forEachIndexed { i, f -> tone(start + i * step, step * 1.4f, f, f, vol, wave) }
            }
        }
    }
}
