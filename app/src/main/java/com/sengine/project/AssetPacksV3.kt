package com.sengine.project

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.sengine.engine.audio.Song
import com.sengine.engine.model.ModelPresets
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Full Edition asset content: racing / shooter / zombie / voxel textures, top-down sprites,
 * a UI kit, weapon & vehicle sound effects, composed music (.song), editable 3D models
 * (.smodel with animations) and script recipes — plus themed packs.
 */
object AssetPacksV3 {
    private typealias Item = AssetLibrary.Item

    private fun png(p: Project, name: String, b: Bitmap) {
        p.assetsDir.mkdirs(); p.assetFile(name).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun tex(title: String, file: String, desc: String, cat: String, gen: () -> Bitmap) =
        Item(title, cat, desc, listOf(file), preview = gen) { p -> png(p, file, gen()) }
    private fun sfx(title: String, file: String, desc: String, gen: () -> ByteArray) =
        Item(title, "Sounds", desc, listOf(file), "♪", sound = gen) { p -> p.assetsDir.mkdirs(); p.assetFile(file).writeBytes(gen()) }

    fun items(existingFiles: Set<String>): List<Item> {
        val l = ArrayList<Item>()
        // ------------------------------------------------------------ textures
        l += tex("Asphalt", "Asphalt.png", "Tileable road asphalt, 128×128", "Textures") { asphalt(false) }
        l += tex("Road (lane lines)", "Road.png", "Asphalt with dashed centre line, 128×128", "Textures") { asphalt(true) }
        l += tex("Race Curb", "Curb.png", "Red/white kerb stripes, 128×32", "Textures") { curb() }
        l += tex("Desert Sand", "DesertSand.png", "Wind-swept dunes, 128×128", "Textures") { dunes() }
        l += tex("Grass Field", "GrassField.png", "Lush tileable grass for 3D terrain, 128×128", "Textures") { grassField() }
        l += tex("Concrete", "Concrete.png", "Grey concrete with cracks, 128×128", "Textures") { concrete() }
        l += tex("Floor Tiles", "Tiles.png", "Ceramic floor tiles, 128×128", "Textures") { tiles() }
        l += tex("Sci-Fi Panel", "SciFiPanel.png", "Hex tech panel with glow, 128×128", "Textures") { scifi() }
        l += tex("Snow", "Snow.png", "Soft snow, 64×64", "Textures") { noise(64, 0xFFF4F8FF.toInt(), 0xFFD6E2F2.toInt(), 5) }
        l += tex("Rusty Metal", "Rust.png", "Weathered metal, 128×128", "Textures") { rust() }
        l += tex("Marble", "Marble.png", "White marble veins, 128×128", "Textures") { marble() }
        l += tex("Camo", "Camo.png", "Military camouflage, 128×128", "Textures") { camo() }
        l += tex("Zombie Ground", "DeadGround.png", "Dark cracked earth for the zombie arena, 128×128", "Textures") { deadGround() }
        l += tex("Voxel Atlas Preview", "BlocksPreview.png", "All MiniCraft block tiles (4×4 atlas), 64×64", "Textures") { blocksPreview() }
        // ------------------------------------------------------------ top-down sprites
        l += tex("Soldier (top-down)", "Soldier.png", "Player soldier seen from above, 64×64", "Sprites") { soldier(0xFF2E7D32.toInt()) }
        l += tex("Zombie (top-down)", "Zombie.png", "Green shambling zombie, 64×64", "Sprites") { zombie(0xFF7CB342.toInt()) }
        l += tex("Zombie Brute", "ZombieBrute.png", "Big tough zombie, 64×64", "Sprites") { zombie(0xFF8D6E63.toInt()) }
        l += tex("Tank (top-down)", "Tank.png", "Tank body with turret, 64×64", "Sprites") { tank() }
        l += tex("Enemy Drone", "Drone.png", "Hovering attack drone, 64×64", "Sprites") { drone() }
        l += tex("Boss Ship", "BossShip.png", "Large boss spaceship, 128×128", "Sprites") { boss() }
        l += tex("Race Car (top-down)", "CarTop.png", "Sports car from above, 32×64", "Sprites") { carTop(0xFFE53935.toInt()) }
        l += tex("Glow Bullet", "Bullet.png", "Glowing round bullet, 16×16", "Sprites") { glow(16, 0xFFFFF176.toInt()) }
        l += tex("Muzzle Flash", "MuzzleFlash.png", "Weapon muzzle flash, 64×64", "Sprites") { muzzle() }
        l += tex("Blood Splat", "Splat.png", "Stylised splat decal, 64×64", "Sprites") { splat() }
        l += tex("Health Kit", "Medkit.png", "First-aid pickup, 32×32", "Sprites") { medkit() }
        l += tex("Ammo Box", "Ammo.png", "Ammunition crate, 32×32", "Sprites") { ammo() }
        l += tex("Shotgun Pickup", "Shotgun.png", "Weapon pickup, 64×32", "Sprites") { shotgun() }
        l += tex("Shield Bubble", "Shield.png", "Energy shield, 64×64", "Sprites") { shield() }
        l += tex("Star", "Star.png", "Golden star, 64×64", "Sprites") { star() }
        l += tex("Crosshair", "Crosshair.png", "Aim reticle, 64×64", "Sprites") { crosshair() }
        // ------------------------------------------------------------ UI kit
        l += tex("UI Panel (dark)", "UIPanelDark.png", "Rounded glassy panel, 256×160", "UI Kit") { uiPanel() }
        l += tex("UI Button (blue)", "UIButtonBlue.png", "Glossy button, 192×64", "UI Kit") { uiButton(0xFF5B7CFF.toInt(), 0xFF22D3EE.toInt()) }
        l += tex("UI Button (green)", "UIButtonGreen.png", "Glossy button, 192×64", "UI Kit") { uiButton(0xFF10B981.toInt(), 0xFF34D399.toInt()) }
        l += tex("UI Button (red)", "UIButtonRed.png", "Glossy button, 192×64", "UI Kit") { uiButton(0xFFE11D48.toInt(), 0xFFFF5C6C.toInt()) }
        l += tex("Health Bar Frame", "BarFrame.png", "Frame for UIProgress bars, 256×40", "UI Kit") { barFrame() }
        l += tex("Joystick Base", "JoyBase.png", "Controller joystick ring, 128×128", "UI Kit") { joyBase() }
        l += tex("Heart Icon", "IconHeart.png", "Lives icon, 64×64", "UI Kit") { heartIcon() }
        l += tex("Coin Icon", "IconCoin.png", "Currency icon, 64×64", "UI Kit") { coinIcon() }
        l += tex("Title Banner", "Banner.png", "Ribbon banner for titles, 512×128", "UI Kit") { banner() }
        // ------------------------------------------------------------ sounds
        l += sfx("UI Click (default)", "ui_click.wav", "Soft click — played automatically by UI buttons when present") { S.uiClick() }
        l += sfx("Menu Select", "select.wav", "Confirm chime") { S.select() }
        l += sfx("Pistol Shot", "pistol.wav", "Punchy gunshot") { S.pistol() }
        l += sfx("Shotgun Blast", "shotgun.wav", "Heavy boom with tail") { S.shotgun() }
        l += sfx("Reload", "reload.wav", "Mechanical click-clack") { S.reload() }
        l += sfx("Empty Click", "empty.wav", "Out of ammo") { S.empty() }
        l += sfx("Zombie Groan", "groan.wav", "Low growl") { S.groan() }
        l += sfx("Car Engine Loop", "engine.wav", "Seamless engine hum (use audio.loop + setPitch)") { S.engine() }
        l += sfx("Tire Screech", "screech.wav", "Skid squeal") { S.screech() }
        l += sfx("Countdown Beep", "beep.wav", "Race countdown tick") { S.beep(660f) }
        l += sfx("Race Start", "go.wav", "High GO! tone") { S.beep(1320f) }
        l += sfx("Nitro Whoosh", "whoosh.wav", "Air rush") { S.whoosh() }
        l += sfx("Footstep", "step.wav", "Soft step") { S.step() }
        l += sfx("Block Break", "break.wav", "Crumbly break") { S.blockBreak() }
        l += sfx("Block Place", "place.wav", "Solid thunk") { S.place() }
        l += sfx("Boss Roar", "roar.wav", "Menacing roar") { S.roar() }
        l += sfx("Shield Up", "shield.wav", "Sci-fi shimmer") { S.shieldUp() }
        // ------------------------------------------------------------ music (.song, editable in the Music Editor)
        val songFiles = listOf("Adventure.song", "Battle.song", "LoFi.song", "RacingRush.song", "SpookyNight.song", "Victory.song", "MenuTheme.song", "BlockWorld.song")
        Song.STYLES.forEachIndexed { i, style ->
            val file = songFiles[i]
            l += Item("$style (song)", "Music", "Composed ${Song.compose(i, 7).bpm} BPM track — open it in the Music Editor or play with audio.playMusic(\"$file\")",
                listOf(file), "♫", sound = { Song.compose(i, 7).renderWav() }) { p -> p.writeAsset(file, Song.compose(i, 7).also { it.name = style }.toJson().toString(2)) }
        }
        // ------------------------------------------------------------ editable 3D models
        val modelFiles = listOf("Car.smodel", "Character.smodel", "TreeModel.smodel", "HouseModel.smodel", "Sword.smodel", "RockModel.smodel", "Spaceship.smodel", "Turret.smodel")
        ModelPresets.NAMES.forEachIndexed { i, n ->
            val m = ModelPresets.build(i)
            val clips = if (m.clips.isEmpty()) "" else " • clips: " + m.clips.joinToString { it.name }
            l += Item("$n (editable)", "3D Models", "${m.parts.size} parts$clips — edit in the 3D Model Editor", listOf(modelFiles[i]), "3D") { p ->
                p.writeAsset(modelFiles[i], ModelPresets.build(i).toJson().toString())
            }
        }
        // ------------------------------------------------------------ script recipes
        for (r in ScriptRecipes.all) if (r.file !in existingFiles) l += Item(r.title, "Scripts", r.description, listOf(r.file), "JS") { p -> p.writeAsset(r.file, "// ${r.title} — ${r.description}\n" + r.code.trim() + "\n") }
        return l
    }

    /** Themed packs built from the full item list. */
    fun packs(all: List<Item>): List<Item> {
        fun pack(title: String, desc: String, names: List<String>): Item? {
            val parts = names.mapNotNull { n -> all.firstOrNull { it.title == n } }
            if (parts.isEmpty()) return null
            return Item(title, "Packs", desc, parts.flatMap { it.files }.distinct(), "📦", preview = parts.firstOrNull { it.preview != null }?.preview) { p -> parts.forEach { it.install(p) } }
        }
        return listOfNotNull(
            pack("Top-down Shooter Pack", "Soldier, drones, tank, boss, bullets, muzzle flash, pickups, sci-fi floor, weapons SFX and battle music",
                listOf("Soldier (top-down)", "Enemy Drone", "Tank (top-down)", "Boss Ship", "Glow Bullet", "Muzzle Flash", "Health Kit", "Shield Bubble", "Sci-Fi Panel", "Pistol Shot", "Explosion", "Shield Up", "Boss Roar", "Action Battle (song)")),
            pack("Zombie Survival Pack", "Zombies, soldier, ground, splats, medkits, ammo, shotgun, groans and spooky music",
                listOf("Zombie (top-down)", "Zombie Brute", "Soldier (top-down)", "Zombie Ground", "Blood Splat", "Health Kit", "Ammo Box", "Shotgun Pickup", "Crosshair", "Pistol Shot", "Shotgun Blast", "Reload", "Empty Click", "Zombie Groan", "Spooky Night (song)")),
            pack("Racing Pack", "Car model, asphalt/road/curb/desert/grass textures, engine loop, screech, countdown and racing music",
                listOf("Low-poly Car (editable)", "Asphalt", "Road (lane lines)", "Race Curb", "Desert Sand", "Grass Field", "Car Engine Loop", "Tire Screech", "Countdown Beep", "Race Start", "Nitro Whoosh", "Racing Rush (song)")),
            pack("MiniCraft Pack", "Block atlas preview, break/place/step sounds, block world music and a tree model",
                listOf("Voxel Atlas Preview", "Block Break", "Block Place", "Footstep", "Block World (song)", "Tree (editable)")),
            pack("UI Kit Pack", "Panels, buttons, bar frame, icons, banner and the default click sound",
                listOf("UI Panel (dark)", "UI Button (blue)", "UI Button (green)", "UI Button (red)", "Health Bar Frame", "Joystick Base", "Heart Icon", "Coin Icon", "Title Banner", "Star", "UI Click (default)", "Menu Select")),
            pack("Music Pack", "All 8 composed songs (editable)", Song.STYLES.map { "$it (song)" }),
            pack("3D Models Pack", "All editable models with animations", ModelPresets.NAMES.map { "$it (editable)" }),
        )
    }

    // ==================================================================== texture generators
    private fun bmp(w: Int, h: Int = w) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    private fun lerpC(a: Int, b: Int, t: Float) = AssetLibrary.lerp(a, b, t.coerceIn(0f, 1f))
    private fun paint(c: Int = Color.WHITE) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c }

    /** Smooth tileable value noise in [0,1]. */
    private fun vnoise(x: Float, y: Float, period: Int, seed: Int): Float {
        fun h(ix: Int, iy: Int): Float { val a = ((ix % period + period) % period) * 374761393 + ((iy % period + period) % period) * 668265263 + seed * 144269; var v = (a xor (a ushr 13)) * 1274126177; v = v xor (v ushr 16); return (v and 0xFFFF) / 65535f }
        val x0 = kotlin.math.floor(x).toInt(); val y0 = kotlin.math.floor(y).toInt()
        val fx = x - x0; val fy = y - y0
        val sx = fx * fx * (3 - 2 * fx); val sy = fy * fy * (3 - 2 * fy)
        val a = h(x0, y0) + (h(x0 + 1, y0) - h(x0, y0)) * sx
        val b = h(x0, y0 + 1) + (h(x0 + 1, y0 + 1) - h(x0, y0 + 1)) * sx
        return a + (b - a) * sy
    }
    private fun fbm(x: Float, y: Float, size: Int, seed: Int, oct: Int = 4): Float {
        var v = 0f; var amp = 0.5f; var f = 4
        for (o in 0 until oct) { v += vnoise(x * f / size, y * f / size, f, seed + o) * amp; amp *= 0.5f; f *= 2 }
        return v / 0.9375f
    }
    private fun field(size: Int, fn: (Int, Int) -> Int): Bitmap {
        val b = bmp(size); val px = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) px[y * size + x] = fn(x, y)
        b.setPixels(px, 0, size, 0, 0, size, size); return b
    }
    private fun noise(size: Int, a: Int, b: Int, seed: Int) = field(size) { x, y -> lerpC(a, b, fbm(x.toFloat(), y.toFloat(), size, seed)) }

    private fun asphalt(lines: Boolean): Bitmap {
        val r = Random(11)
        val b = field(128) { x, y -> val n = fbm(x.toFloat(), y.toFloat(), 128, 3, 3); val g = r.nextFloat() * 0.12f; lerpC(0xFF2B2D31.toInt(), 0xFF45484F.toInt(), n * 0.8f + g) }
        if (lines) {
            val c = Canvas(b); val p = paint(0xFFF5F5F5.toInt())
            c.drawRect(60f, 0f, 68f, 44f, p); c.drawRect(60f, 64f, 68f, 108f, p)
            p.color = 0xFFFBC02D.toInt(); c.drawRect(2f, 0f, 6f, 128f, p); c.drawRect(122f, 0f, 126f, 128f, p)
        }
        return b
    }
    private fun curb(): Bitmap {
        val b = bmp(128, 32); val c = Canvas(b); val p = paint()
        for (i in 0 until 8) { p.color = if (i % 2 == 0) 0xFFE53935.toInt() else 0xFFF5F5F5.toInt(); c.drawRect(i * 16f, 0f, i * 16f + 16, 32f, p) }
        p.color = 0x33000000; c.drawRect(0f, 26f, 128f, 32f, p); return b
    }
    private fun dunes() = field(128) { x, y ->
        val w = sin((y + fbm(x.toFloat(), y.toFloat(), 128, 9, 2) * 30f) / 128f * 2 * PI.toFloat() * 4) * 0.5f + 0.5f
        lerpC(0xFFD9A55B.toInt(), 0xFFF2CD8A.toInt(), w * 0.7f + fbm(x.toFloat(), y.toFloat(), 128, 4) * 0.3f)
    }
    private fun grassField(): Bitmap {
        val b = field(128) { x, y -> lerpC(0xFF2F7D32.toInt(), 0xFF5DAA3C.toInt(), fbm(x.toFloat(), y.toFloat(), 128, 21)) }
        val c = Canvas(b); val p = paint(); val r = Random(4)
        repeat(700) { val x = r.nextFloat() * 128; val y = r.nextFloat() * 128; p.color = if (r.nextBoolean()) 0x5584C75A else 0x44205E24; c.drawRect(x, y, x + 1, y + 3 + r.nextFloat() * 3, p) }
        return b
    }
    private fun concrete(): Bitmap {
        val b = noise(128, 0xFF8E9196.toInt(), 0xFFB5B8BD.toInt(), 31)
        val c = Canvas(b); val p = paint(0x66404348); p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f
        val path = Path(); path.moveTo(10f, 30f); path.lineTo(35f, 42f); path.lineTo(48f, 70f); path.lineTo(80f, 78f); c.drawPath(path, p)
        p.color = 0x33000000; c.drawLine(0f, 0f, 128f, 0f, p); c.drawLine(0f, 0f, 0f, 128f, p); return b
    }
    private fun tiles(): Bitmap {
        val b = bmp(128); val c = Canvas(b); val p = paint()
        for (i in 0 until 4) for (j in 0 until 4) {
            p.color = lerpC(0xFFE8E4DC.toInt(), 0xFFD5CFC3.toInt(), ((i * 7 + j * 3) % 5) / 5f)
            c.drawRect(i * 32f + 1, j * 32f + 1, i * 32f + 31, j * 32f + 31, p)
        }
        return b.also { Canvas(it).drawColor(0x00000000) }
    }
    private fun scifi(): Bitmap {
        val b = bmp(128); val c = Canvas(b); c.drawColor(0xFF1B2233.toInt())
        val p = paint(0xFF2A3550.toInt()); val s = paint(0xFF22D3EE.toInt()).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
        fun hex(cx: Float, cy: Float, r: Float) { val path = Path(); for (k in 0 until 6) { val a = PI / 3 * k + PI / 6; val x = cx + r * cos(a).toFloat(); val y = cy + r * sin(a).toFloat(); if (k == 0) path.moveTo(x, y) else path.lineTo(x, y) }; path.close(); c.drawPath(path, p); s.alpha = 120; c.drawPath(path, s) }
        for (row in -1..3) for (col in -1..3) hex(col * 36f + (if (row % 2 == 0) 0f else 18f), row * 31f, 17f)
        return b
    }
    private fun rust() = field(128) { x, y -> val n = fbm(x.toFloat(), y.toFloat(), 128, 51); val m = fbm(x.toFloat(), y.toFloat(), 128, 77, 2)
        if (m > 0.55f) lerpC(0xFF8D4A1F.toInt(), 0xFFB5652A.toInt(), n) else lerpC(0xFF6B7078.toInt(), 0xFF8A9098.toInt(), n) }
    private fun marble() = field(128) { x, y -> val t = sin((x + fbm(x.toFloat(), y.toFloat(), 128, 61) * 60f) / 128f * 2 * PI.toFloat() * 2); lerpC(0xFFF7F7F5.toInt(), 0xFFB9B9BE.toInt(), (1 - abs(t)).let { it * it * it * it }) }
    private fun camo() = field(128) { x, y -> val n = fbm(x.toFloat(), y.toFloat(), 128, 83, 3)
        when { n < 0.4f -> 0xFF3E4A2A.toInt(); n < 0.52f -> 0xFF5F6B3A.toInt(); n < 0.64f -> 0xFF8A7F56.toInt(); else -> 0xFF2B2A22.toInt() } }
    private fun deadGround(): Bitmap {
        val b = noise(128, 0xFF2A2621.toInt(), 0xFF4A4238.toInt(), 91)
        val c = Canvas(b); val p = paint(0x88161310.toInt()).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f }; val r = Random(2)
        repeat(6) { var x = r.nextFloat() * 128; var y = r.nextFloat() * 128; repeat(6) { val nx = x + r.nextFloat() * 24 - 12; val ny = y + r.nextFloat() * 24 - 12; c.drawLine(x, y, nx, ny, p); x = nx; y = ny } }
        return b
    }
    private fun blocksPreview(): Bitmap = try { com.sengine.engine.voxel.VoxelAtlas.create() } catch (_: Throwable) { noise(128, 0xFF6DAA45.toInt(), 0xFF8B5A2B.toInt(), 1) }

    // ------------------------------------------------------------ sprites
    private fun soldier(col: Int): Bitmap {
        val b = bmp(64); val c = Canvas(b); val p = paint()
        p.color = 0x44000000; c.drawCircle(33f, 34f, 20f, p)
        p.color = 0xFF37474F.toInt(); c.drawRoundRect(RectF(34f, 28f, 62f, 36f), 3f, 3f, p) // gun
        p.color = col; c.drawOval(RectF(14f, 14f, 50f, 50f), p) // shoulders
        p.color = shade(col, 0.75f); c.drawCircle(40f, 22f, 6f, p); c.drawCircle(40f, 42f, 6f, p) // hands
        p.color = 0xFFFFCC80.toInt(); c.drawCircle(32f, 32f, 10f, p)
        p.color = shade(col, 0.6f); c.drawArc(RectF(22f, 22f, 42f, 42f), 90f, 180f, true, p) // helmet
        return b
    }
    private fun zombie(col: Int): Bitmap {
        val b = bmp(64); val c = Canvas(b); val p = paint()
        p.color = 0x44000000; c.drawCircle(33f, 34f, 20f, p)
        p.color = shade(col, 0.8f); c.drawRoundRect(RectF(30f, 14f, 58f, 22f), 4f, 4f, p); c.drawRoundRect(RectF(30f, 42f, 58f, 50f), 4f, 4f, p) // arms forward
        p.color = 0xFF5D4037.toInt(); c.drawOval(RectF(12f, 16f, 46f, 48f), p) // torn shirt
        p.color = col; c.drawCircle(32f, 32f, 11f, p)
        p.color = 0xFFD32F2F.toInt(); c.drawCircle(38f, 28f, 2.2f, p); c.drawCircle(38f, 36f, 2.2f, p)
        return b
    }
    private fun tank(): Bitmap {
        val b = bmp(64); val c = Canvas(b); val p = paint()
        p.color = 0xFF263238.toInt(); c.drawRoundRect(RectF(8f, 10f, 56f, 18f), 3f, 3f, p); c.drawRoundRect(RectF(8f, 46f, 56f, 54f), 3f, 3f, p)
        p.color = 0xFF558B2F.toInt(); c.drawRoundRect(RectF(10f, 16f, 54f, 48f), 6f, 6f, p)
        p.color = 0xFF33691E.toInt(); c.drawCircle(30f, 32f, 11f, p); c.drawRect(30f, 29f, 62f, 35f, p)
        return b
    }
    private fun drone(): Bitmap {
        val b = bmp(64); val c = Canvas(b); val p = paint()
        p.color = 0xFF455A64.toInt(); p.strokeWidth = 5f; c.drawLine(14f, 14f, 50f, 50f, p); c.drawLine(50f, 14f, 14f, 50f, p)
        p.color = 0x66B0BEC5; for ((x, y) in listOf(14f to 14f, 50f to 14f, 14f to 50f, 50f to 50f)) c.drawCircle(x, y, 10f, p)
        p.color = 0xFF37474F.toInt(); c.drawCircle(32f, 32f, 12f, p)
        p.color = 0xFFFF1744.toInt(); c.drawCircle(32f, 32f, 5f, p); return b
    }
    private fun boss(): Bitmap {
        val b = bmp(128); val c = Canvas(b); val p = paint()
        val body = Path(); body.moveTo(64f, 120f); body.lineTo(120f, 60f); body.lineTo(100f, 12f); body.lineTo(64f, 30f); body.lineTo(28f, 12f); body.lineTo(8f, 60f); body.close()
        p.shader = LinearGradient(0f, 0f, 0f, 128f, 0xFF7E57C2.toInt(), 0xFF311B92.toInt(), Shader.TileMode.CLAMP); c.drawPath(body, p); p.shader = null
        p.color = 0xFFFF1744.toInt(); c.drawCircle(64f, 70f, 14f, p); p.color = 0xFFFFCDD2.toInt(); c.drawCircle(64f, 70f, 6f, p)
        p.color = 0xFF212121.toInt(); c.drawRect(20f, 40f, 30f, 70f, p); c.drawRect(98f, 40f, 108f, 70f, p)
        return b
    }
    private fun carTop(col: Int): Bitmap {
        val b = bmp(32, 64); val c = Canvas(b); val p = paint()
        p.color = 0xFF212121.toInt(); for ((x, y) in listOf(2f to 10f, 24f to 10f, 2f to 44f, 24f to 44f)) c.drawRoundRect(RectF(x, y, x + 6, y + 12), 2f, 2f, p)
        p.color = col; c.drawRoundRect(RectF(4f, 2f, 28f, 62f), 9f, 9f, p)
        p.color = 0xFF263238.toInt(); c.drawRoundRect(RectF(7f, 18f, 25f, 40f), 4f, 4f, p)
        p.color = 0xFFFFF59D.toInt(); c.drawRect(6f, 3f, 11f, 6f, p); c.drawRect(21f, 3f, 26f, 6f, p)
        p.color = 0xFFFFFFFF.toInt(); c.drawRect(14f, 2f, 18f, 62f, p.also { it.alpha = 140 })
        return b
    }
    private fun glow(size: Int, col: Int): Bitmap {
        val b = bmp(size); val c = Canvas(b); val p = paint()
        p.shader = RadialGradient(size / 2f, size / 2f, size / 2f, intArrayOf(Color.WHITE, col, col and 0x00FFFFFF), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(size / 2f, size / 2f, size / 2f, p); return b
    }
    private fun muzzle(): Bitmap {
        val b = bmp(64); val c = Canvas(b); val p = paint()
        val path = Path(); for (k in 0 until 12) { val a = 2 * PI * k / 12; val r = if (k % 2 == 0) 30f else 12f; val x = 32 + r * cos(a).toFloat(); val y = 32 + r * sin(a).toFloat(); if (k == 0) path.moveTo(x, y) else path.lineTo(x, y) }; path.close()
        p.shader = RadialGradient(32f, 32f, 30f, intArrayOf(Color.WHITE, 0xFFFFEB3B.toInt(), 0x00FF9800), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(path, p); return b
    }
    private fun splat(): Bitmap {
        val b = bmp(64); val c = Canvas(b); val p = paint(0xCC7F0000.toInt()); val r = Random(6)
        c.drawCircle(32f, 32f, 14f, p)
        repeat(14) { val a = r.nextFloat() * 2 * PI.toFloat(); val d = 12 + r.nextFloat() * 16; c.drawCircle(32 + cos(a) * d, 32 + sin(a) * d, 2 + r.nextFloat() * 5, p) }
        return b
    }
    private fun medkit(): Bitmap {
        val b = bmp(32); val c = Canvas(b); val p = paint(0xFFF5F5F5.toInt())
        c.drawRoundRect(RectF(2f, 5f, 30f, 29f), 5f, 5f, p); p.color = 0xFFE53935.toInt(); c.drawRect(13f, 9f, 19f, 25f, p); c.drawRect(8f, 14f, 24f, 20f, p); return b
    }
    private fun ammo(): Bitmap {
        val b = bmp(32); val c = Canvas(b); val p = paint(0xFF5D6B34.toInt())
        c.drawRoundRect(RectF(2f, 8f, 30f, 28f), 3f, 3f, p); p.color = 0xFFFBC02D.toInt()
        for (i in 0 until 4) c.drawRoundRect(RectF(6f + i * 6, 2f, 10f + i * 6, 14f), 2f, 2f, p)
        p.color = 0xFF3E4A20.toInt(); c.drawRect(2f, 16f, 30f, 19f, p); return b
    }
    private fun shotgun(): Bitmap {
        val b = bmp(64, 32); val c = Canvas(b); val p = paint(0xFF37474F.toInt())
        c.drawRect(14f, 12f, 62f, 17f, p); c.drawRect(14f, 17f, 50f, 20f, p); p.color = 0xFF6D4C41.toInt()
        val stock = Path(); stock.moveTo(2f, 12f); stock.lineTo(18f, 12f); stock.lineTo(18f, 22f); stock.lineTo(6f, 28f); stock.close(); c.drawPath(stock, p)
        c.drawRect(30f, 19f, 42f, 24f, p); return b
    }
    private fun shield(): Bitmap {
        val b = bmp(64); val c = Canvas(b); val p = paint()
        p.shader = RadialGradient(32f, 32f, 31f, intArrayOf(0x0022D3EE, 0x3322D3EE, 0xCC22D3EE.toInt()), floatArrayOf(0f, 0.75f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(32f, 32f, 31f, p); return b
    }
    private fun star(): Bitmap {
        val b = bmp(64); val c = Canvas(b); val p = paint()
        val path = Path(); for (k in 0 until 10) { val a = -PI / 2 + PI * k / 5; val r = if (k % 2 == 0) 30f else 13f; val x = 32 + r * cos(a).toFloat(); val y = 33 + r * sin(a).toFloat(); if (k == 0) path.moveTo(x, y) else path.lineTo(x, y) }; path.close()
        p.shader = LinearGradient(0f, 0f, 0f, 64f, 0xFFFFE082.toInt(), 0xFFFFA000.toInt(), Shader.TileMode.CLAMP); c.drawPath(path, p)
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = 0xFFFF6F00.toInt(); c.drawPath(path, p); return b
    }
    private fun crosshair(): Bitmap {
        val b = bmp(64); val c = Canvas(b); val p = paint(Color.WHITE).apply { style = Paint.Style.STROKE; strokeWidth = 3f }
        c.drawCircle(32f, 32f, 18f, p); c.drawLine(32f, 4f, 32f, 18f, p); c.drawLine(32f, 46f, 32f, 60f, p); c.drawLine(4f, 32f, 18f, 32f, p); c.drawLine(46f, 32f, 60f, 32f, p)
        p.style = Paint.Style.FILL; p.color = 0xFFFF5C6C.toInt(); c.drawCircle(32f, 32f, 3f, p); return b
    }

    // ------------------------------------------------------------ UI kit
    private fun uiPanel(): Bitmap {
        val b = bmp(256, 160); val c = Canvas(b); val p = paint()
        p.shader = LinearGradient(0f, 0f, 0f, 160f, 0xF0232A47.toInt(), 0xF0111427.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(2f, 2f, 254f, 158f), 22f, 22f, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = 0xFF5B7CFF.toInt(); c.drawRoundRect(RectF(3f, 3f, 253f, 157f), 21f, 21f, p); return b
    }
    private fun uiButton(a: Int, bCol: Int): Bitmap {
        val b = bmp(192, 64); val c = Canvas(b); val p = paint()
        p.color = shade(a, 0.6f); c.drawRoundRect(RectF(0f, 6f, 192f, 64f), 20f, 20f, p)
        p.shader = LinearGradient(0f, 0f, 192f, 58f, a, bCol, Shader.TileMode.CLAMP); c.drawRoundRect(RectF(0f, 0f, 192f, 58f), 20f, 20f, p)
        p.shader = LinearGradient(0f, 0f, 0f, 30f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP); c.drawRoundRect(RectF(6f, 4f, 186f, 30f), 14f, 14f, p); return b
    }
    private fun barFrame(): Bitmap {
        val b = bmp(256, 40); val c = Canvas(b); val p = paint(0xCC0B0E1A.toInt())
        c.drawRoundRect(RectF(0f, 0f, 256f, 40f), 20f, 20f, p); p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = 0xFFE8EAF6.toInt()
        c.drawRoundRect(RectF(2f, 2f, 254f, 38f), 18f, 18f, p); return b
    }
    private fun joyBase(): Bitmap {
        val b = bmp(128); val c = Canvas(b); val p = paint(0x33FFFFFF)
        c.drawCircle(64f, 64f, 62f, p); p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = 0xAAFFFFFF.toInt(); c.drawCircle(64f, 64f, 60f, p)
        p.style = Paint.Style.FILL; for (k in 0 until 4) { val a = PI / 2 * k; val path = Path(); val cx = 64 + 44 * cos(a).toFloat(); val cy = 64 + 44 * sin(a).toFloat()
            path.moveTo(cx + 8 * cos(a).toFloat(), cy + 8 * sin(a).toFloat()); path.lineTo(cx + 7 * cos(a + PI / 2).toFloat(), cy + 7 * sin(a + PI / 2).toFloat()); path.lineTo(cx - 7 * cos(a + PI / 2).toFloat(), cy - 7 * sin(a + PI / 2).toFloat()); path.close(); c.drawPath(path, p) }
        return b
    }
    private fun heartIcon(): Bitmap {
        val b = bmp(64); val c = Canvas(b); val p = paint()
        val path = Path(); path.moveTo(32f, 56f); path.cubicTo(-6f, 30f, 10f, 0f, 32f, 18f); path.cubicTo(54f, 0f, 70f, 30f, 32f, 56f); path.close()
        p.shader = LinearGradient(0f, 0f, 0f, 64f, 0xFFFF8A80.toInt(), 0xFFD50000.toInt(), Shader.TileMode.CLAMP); c.drawPath(path, p); return b
    }
    private fun coinIcon(): Bitmap {
        val b = bmp(64); val c = Canvas(b); val p = paint()
        p.shader = RadialGradient(26f, 24f, 34f, 0xFFFFF59D.toInt(), 0xFFF9A825.toInt(), Shader.TileMode.CLAMP); c.drawCircle(32f, 32f, 28f, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = 0xFFF57F17.toInt(); c.drawCircle(32f, 32f, 21f, p)
        p.style = Paint.Style.FILL; p.textSize = 26f; p.textAlign = Paint.Align.CENTER; p.isFakeBoldText = true; c.drawText("$", 32f, 41f, p); return b
    }
    private fun banner(): Bitmap {
        val b = bmp(512, 128); val c = Canvas(b); val p = paint()
        p.color = 0xFF3F51B5.toInt()
        val l = Path(); l.moveTo(0f, 40f); l.lineTo(70f, 40f); l.lineTo(70f, 110f); l.lineTo(0f, 110f); l.lineTo(24f, 75f); l.close(); c.drawPath(l, p)
        val r = Path(); r.moveTo(512f, 40f); r.lineTo(442f, 40f); r.lineTo(442f, 110f); r.lineTo(512f, 110f); r.lineTo(488f, 75f); r.close(); c.drawPath(r, p)
        p.shader = LinearGradient(0f, 20f, 0f, 100f, 0xFF5B7CFF.toInt(), 0xFF22D3EE.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(44f, 16f, 468f, 96f), 12f, 12f, p); return b
    }
    private fun shade(c: Int, f: Float): Int { fun ch(s: Int) = (((c shr s) and 0xFF) * f).toInt().coerceIn(0, 255); return (c and 0xFF000000.toInt()) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0) }

    // ==================================================================== sound generators
    private object S {
        private const val R = 22050
        private fun buf(sec: Float) = FloatArray((sec * R).toInt())
        private fun wav(b: FloatArray): ByteArray = AssetLibrary.Sfx.wav(b)
        private fun tone(b: FloatArray, start: Float, dur: Float, f0: Float, f1: Float, vol: Float, wave: Int = 0, decay: Float = 1f) {
            var ph = 0.0; val s0 = (start * R).toInt(); val n = (dur * R).toInt()
            for (i in 0 until n) {
                val idx = s0 + i; if (idx >= b.size) break
                val t = i / n.toFloat(); ph += (f0 + (f1 - f0) * t) / R
                val p1 = ph % 1.0
                val v = when (wave) { 1 -> if (p1 < 0.5) 0.6f else -0.6f; 2 -> (4 * abs(p1 - 0.5) - 1).toFloat(); 3 -> (p1 * 2 - 1).toFloat() * 0.7f; else -> sin(ph * 2 * PI).toFloat() }
                b[idx] += v * vol * minOf(1f, i / (0.003f * R)) * (1 - t).let { Math.pow(it.toDouble(), decay.toDouble()).toFloat() }
            }
        }
        private fun noise(b: FloatArray, start: Float, dur: Float, vol: Float, lp: Float, seed: Int = 1, decay: Float = 2f) {
            val r = Random(seed); var y = 0f; val s0 = (start * R).toInt(); val n = (dur * R).toInt()
            for (i in 0 until n) { val idx = s0 + i; if (idx >= b.size) break; val t = i / n.toFloat(); y += (r.nextFloat() * 2 - 1 - y) * lp; b[idx] += y * vol * Math.pow((1 - t).toDouble(), decay.toDouble()).toFloat() }
        }
        fun uiClick() = buf(0.06f).also { tone(it, 0f, 0.05f, 1400f, 900f, 0.35f, 2, 2f); noise(it, 0f, 0.015f, 0.2f, 0.8f) }.let(::wav)
        fun select() = buf(0.25f).also { tone(it, 0f, 0.09f, 880f, 880f, 0.35f, 2); tone(it, 0.08f, 0.16f, 1320f, 1320f, 0.35f, 2) }.let(::wav)
        fun pistol() = buf(0.35f).also { noise(it, 0f, 0.3f, 1.1f, 0.55f, 3, 4f); tone(it, 0f, 0.12f, 180f, 60f, 0.7f) }.let(::wav)
        fun shotgun() = buf(0.7f).also { noise(it, 0f, 0.65f, 1.3f, 0.35f, 5, 3f); tone(it, 0f, 0.25f, 110f, 40f, 0.9f) }.let(::wav)
        fun reload() = buf(0.5f).also { noise(it, 0f, 0.04f, 0.8f, 0.9f, 7); tone(it, 0f, 0.03f, 2500f, 2000f, 0.3f); noise(it, 0.25f, 0.05f, 0.9f, 0.9f, 8); tone(it, 0.25f, 0.04f, 1800f, 1400f, 0.35f) }.let(::wav)
        fun empty() = buf(0.1f).also { tone(it, 0f, 0.03f, 3000f, 2200f, 0.3f); noise(it, 0f, 0.02f, 0.4f, 0.9f) }.let(::wav)
        fun groan() = buf(1.2f).also { tone(it, 0f, 1.2f, 95f, 70f, 0.5f, 3, 0.6f); tone(it, 0.1f, 1f, 142f, 110f, 0.25f, 3, 0.8f); noise(it, 0f, 1.2f, 0.15f, 0.08f, 9, 0.7f) }.let(::wav)
        fun engine(): ByteArray {
            // exact number of cycles so it loops seamlessly
            val f = 55.0; val cycles = 44; val n = (cycles / f * R).toInt(); val b = FloatArray(n)
            for (i in 0 until n) { val ph = i * f / R; b[i] = (0.45 * ((ph % 1.0) * 2 - 1) + 0.25 * sin(ph * 2 * PI * 2) + 0.12 * sin(ph * 2 * PI * 0.5)).toFloat() }
            return wav(b)
        }
        fun screech() = buf(0.9f).also { for (k in 0 until 3) tone(it, 0f, 0.9f, 1850f + k * 90f, 1650f + k * 60f, 0.13f, 3, 0.7f); noise(it, 0f, 0.9f, 0.25f, 0.6f, 10, 1f) }.let(::wav)
        fun beep(f: Float) = buf(0.3f).also { tone(it, 0f, 0.28f, f, f, 0.45f, 1, 0.4f) }.let(::wav)
        fun whoosh() = buf(0.8f).also { noise(it, 0f, 0.8f, 0.9f, 0.12f, 11, 1.2f) }.let(::wav)
        fun step() = buf(0.12f).also { noise(it, 0f, 0.1f, 0.7f, 0.2f, 12, 3f); tone(it, 0f, 0.06f, 120f, 70f, 0.4f) }.let(::wav)
        fun blockBreak() = buf(0.35f).also { noise(it, 0f, 0.3f, 0.9f, 0.3f, 13, 2.5f); noise(it, 0.05f, 0.2f, 0.5f, 0.5f, 14, 3f) }.let(::wav)
        fun place() = buf(0.18f).also { tone(it, 0f, 0.14f, 160f, 90f, 0.7f); noise(it, 0f, 0.05f, 0.5f, 0.4f, 15) }.let(::wav)
        fun roar() = buf(1.6f).also { tone(it, 0f, 1.6f, 70f, 45f, 0.6f, 3, 0.5f); noise(it, 0f, 1.6f, 0.5f, 0.1f, 16, 0.6f); tone(it, 0.05f, 1.2f, 105f, 80f, 0.3f, 1, 0.7f) }.let(::wav)
        fun shieldUp() = buf(0.7f).also { for (k in 0 until 4) tone(it, k * 0.05f, 0.5f, 600f + k * 150f, 1400f + k * 200f, 0.12f, 0, 0.8f) }.let(::wav)
    }

}
