package com.sengine.project.games

import com.sengine.engine.core.Camera3D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Light
import com.sengine.engine.core.MeshRenderer
import com.sengine.engine.core.Scene
import com.sengine.engine.core.UIPanel
import com.sengine.engine.model.ModelPresets
import com.sengine.project.Project
import com.sengine.project.games.GameKit.mesh
import com.sengine.project.games.GameKit.off
import com.sengine.project.games.GameKit.particles
import com.sengine.project.games.GameKit.script
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * "Turbo Rally 3D" — complete 3D racing game: showroom menu with map/road selection and settings,
 * 2 maps × 3 roads (6 race scenes generated from spline tracks), 5 AI drivers with racing lines,
 * overtaking and rubber-banding, arcade car physics with grip/drift/off-road/nitro, laps,
 * positions, minimap, countdown, chase/far/hood cameras, pause and results with records.
 */
internal object RacingGame {
    const val WIDTH = 14f
    val MAPS = listOf("Green", "Desert")
    val MAP_TITLES = listOf("GREEN VALLEY", "DESERT CANYON")
    val ROADS = listOf("OVAL SPEEDWAY", "TWISTY CIRCUIT", "GRAND PRIX")
    private val carColors = listOf(0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt(), 0xFF8E24AA.toInt(), 0xFFFF7043.toInt())
    private val carFiles = listOf("CarRed.smodel", "CarBlue.smodel", "CarYellow.smodel", "CarGreen.smodel", "CarPurple.smodel", "CarOrange.smodel")

    fun build(p: Project) {
        GameKit.install(p, "Asphalt", "Road (lane lines)", "Race Curb", "Desert Sand", "Grass Field", "Checker", "Concrete", "Water",
            "Car Engine Loop", "Tire Screech", "Countdown Beep", "Race Start", "Nitro Whoosh", "Hit", "Power Up", "Win Jingle", "UI Click (default)",
            "Racing Rush (song)", "Soft Particle")
        for (s in listOf("RaceManager.js", "RallyMenu.js", "Turntable.js")) p.writeAsset(s, Games.res("racing/$s"))
        // car models in 6 colours (body part recoloured), plus scenery models
        for (i in carFiles.indices) {
            val m = ModelPresets.car()
            m.parts.firstOrNull { it.name == "Body" }?.color = carColors[i]
            p.writeAsset(carFiles[i], m.toJson().toString())
        }
        p.writeAsset("TreeModel.smodel", ModelPresets.build(2).toJson().toString())
        p.writeAsset("HouseModel.smodel", ModelPresets.build(3).toJson().toString())
        p.writeAsset("RockModel.smodel", ModelPresets.build(5).toJson().toString())
        p.saveScene(menu())
        for (m in MAPS.indices) for (r in ROADS.indices) {
            val pts = trackPoints(r, m)
            val name = "${MAP_TITLES[m]} - ${ROADS[r]}"
            val file = "Track_${MAPS[m]}_$r.json"
            val (cx, cz, sc) = minimap(pts)
            val json = JSONObject().put("name", name).put("width", WIDTH.toDouble())
                .put("points", JSONArray().also { a -> pts.forEach { a.put(JSONArray().put(it[0].toDouble()).put(it[1].toDouble())) } })
                .put("minimap", JSONArray().put(cx.toDouble()).put(cz.toDouble()).put(sc.toDouble()))
            p.writeAsset(file, json.toString())
            p.saveScene(race(m, r, pts, file, name))
        }
        p.saveControls(com.sengine.engine.controls.ControlLayout.presets.first { it.name == "Racing" }.copy())
        p.startScene = "RallyMenu"
        p.orientation = 0
    }

    // ------------------------------------------------------------------ track geometry
    private val controls = listOf(
        emptyList(), // oval is generated analytically
        listOf(0f to 0f, 0f to 80f, 30f to 130f, 90f to 140f, 130f to 100f, 110f to 50f, 150f to 10f, 140f to -60f, 80f to -80f, 40f to -50f, 10f to -70f),
        listOf(0f to 0f, 0f to 150f, 20f to 190f, 70f to 200f, 110f to 170f, 100f to 120f, 60f to 100f, 60f to 60f, 120f to 40f, 200f to 60f,
            230f to 20f, 210f to -50f, 150f to -70f, 80f to -40f, 40f to -80f, 0f to -60f),
    )

    /** Closed centre line sampled every ~7 m. Desert variants are mirrored and slightly larger. */
    fun trackPoints(road: Int, map: Int): List<FloatArray> {
        val raw = ArrayList<FloatArray>()
        if (road == 0) {
            val r = 60f; val half = 90f; val step = 7f
            var z = 0f
            while (z < half) { raw += floatArrayOf(r, z); z += step }
            val arc = (PI * r / step).toInt()
            for (i in 0..arc) { val a = PI * i / arc; raw += floatArrayOf((r * cos(a)).toFloat(), (half + r * sin(a)).toFloat()) }
            z = half - step
            while (z > -half) { raw += floatArrayOf(-r, z); z -= step }
            for (i in 0..arc) { val a = PI + PI * i / arc; raw += floatArrayOf((r * cos(a)).toFloat(), (-half + r * sin(a)).toFloat()) }
            z = -half + step
            while (z < -step / 2) { raw += floatArrayOf(r, z); z += step }
        } else {
            val c = controls[road]
            val n = c.size
            for (i in 0 until n) {
                val p0 = c[(i - 1 + n) % n]; val p1 = c[i]; val p2 = c[(i + 1) % n]; val p3 = c[(i + 2) % n]
                val len = sqrt((p2.first - p1.first) * (p2.first - p1.first) + (p2.second - p1.second) * (p2.second - p1.second))
                val steps = ceil(len / 7f).toInt().coerceAtLeast(2)
                for (s in 0 until steps) {
                    val t = s.toFloat() / steps
                    raw += floatArrayOf(cr(p0.first, p1.first, p2.first, p3.first, t), cr(p0.second, p1.second, p2.second, p3.second, t))
                }
            }
        }
        // remove near-duplicates (the oval joins)
        val out = ArrayList<FloatArray>()
        for (p in raw) if (out.isEmpty() || dist(out.last(), p) > 2f) out += p
        if (dist(out.first(), out.last()) < 2f) out.removeAt(out.size - 1)
        if (map == 1) for (p in out) { p[0] = -p[0] * 1.1f; p[1] = p[1] * 1.1f }
        return out
    }

    private fun cr(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val t2 = t * t; val t3 = t2 * t
        return 0.5f * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)
    }
    private fun dist(a: FloatArray, b: FloatArray) = sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]))

    fun minimap(pts: List<FloatArray>): Triple<Float, Float, Float> {
        val minX = pts.minOf { it[0] }; val maxX = pts.maxOf { it[0] }; val minZ = pts.minOf { it[1] }; val maxZ = pts.maxOf { it[1] }
        return Triple((minX + maxX) / 2, (minZ + maxZ) / 2, 1.9f / maxOf(maxX - minX, maxZ - minZ))
    }

    private fun distToTrack(pts: List<FloatArray>, x: Float, z: Float): Float {
        var best = Float.MAX_VALUE
        for (i in pts.indices) {
            val a = pts[i]; val b = pts[(i + 1) % pts.size]
            val dx = b[0] - a[0]; val dz = b[1] - a[1]; val l2 = dx * dx + dz * dz
            val t = (((x - a[0]) * dx + (z - a[1]) * dz) / l2).coerceIn(0f, 1f)
            val px = a[0] + dx * t - x; val pz = a[1] + dz * t - z
            best = minOf(best, sqrt(px * px + pz * pz))
        }
        return best
    }

    private fun turnAt(pts: List<FloatArray>, i: Int): Float {
        val n = pts.size
        fun ang(k: Int): Float { val a = pts[(k + n) % n]; val b = pts[(k + 1 + n) % n]; return atan2(b[0] - a[0], b[1] - a[1]) }
        var d = ang(i + 1) - ang(i - 1)
        while (d > PI) d -= (2 * PI).toFloat(); while (d < -PI) d += (2 * PI).toFloat()
        return d
    }

    // ------------------------------------------------------------------ scenes
    private fun camera3D(s: Scene, skyTop: Int, skyHorizon: Int): GameObject =
        GameKit.obj3(s, "Main Camera", 0f, 6f, 12f).also {
            it.add(Camera3D().also { c -> c.skyTop = skyTop; c.skyHorizon = skyHorizon; c.far = 700f; c.fov = 65f; c.follow = ""; c.shadowDistance = 70f; c.postFx = 0; c.quality = 2 })
        }

    private fun menu(): Scene {
        val s = Scene("RallyMenu")
        s.fog = true; s.fogStart = 60f; s.fogEnd = 200f; s.fogColor = 0xFF1B2440.toInt(); s.ambient = 0xFF505870.toInt()
        val cam = camera3D(s, 0xFF0B1024.toInt(), 0xFF3B2A5C.toInt())
        cam.x = 0f; cam.y = 2.6f; cam.z = 8.5f; cam.rotX = -12f
        GameKit.obj3(s, "Sun", 0f, 10f, 0f).also { it.rotX = -55f; it.rotY = 35f }.add(Light().also { it.kind = 0; it.intensity = 1.1f })
        GameKit.obj3(s, "Neon1", -4f, 3f, 2f).add(Light().also { it.kind = 1; it.color = 0xFF22D3EE.toInt(); it.intensity = 2.2f; it.range = 12f })
        GameKit.obj3(s, "Neon2", 4f, 3f, 2f).add(Light().also { it.kind = 1; it.color = 0xFFF472B6.toInt(); it.intensity = 2.2f; it.range = 12f })
        GameKit.obj3(s, "Floor", 0f, -0.05f, 0f, 60f, 0.1f, 60f).mesh(0, 0xFF2A2F45, "Concrete.png", 12f)
        GameKit.obj3(s, "Turntable", 0f, 0f, 0f, 7f, 0.12f, 7f).mesh(3, 0xFF3A4260)
        GameKit.obj3(s, "ShowCar", 0f, 0.12f, 0f).mesh(8, 0xFFFFFFFF, model = carFiles[0]).script("Turntable.js", "speed=25").also { it.get<MeshRenderer>()!!.specular = 0.9f }
        GameKit.obj(s, "MenuManager").script("RallyMenu.js")

        GameKit.text(s, "Title", "TURBO RALLY", 0f, -0.8f, 1.1f, 0xFFFACC15, anchor = GameKit.TOP)
        GameKit.text(s, "TrophyText", "TROPHIES 0", -1.6f, -0.6f, 0.32f, 0xFFFFD166, anchor = GameKit.TR, align = 2)
        val main = GameKit.panel(s, "MainPanel", 3.2f, -0.6f, 5f, 5f, 0xCC0F1528, anchor = GameKit.LEFT)
        GameKit.button(s, "RaceBtn", "RACE", 0f, 1.5f, 4.2f, 1f, 0xFF22C55E, "show:TrackPanel;hide:MainPanel", main, textSize = 0.45f)
        GameKit.button(s, "SettingsBtn", "Settings", 0f, 0.35f, 4.2f, 0.85f, 0xFF4C6FFF, "show:SettingsPanel;hide:MainPanel", main)
        GameKit.button(s, "HelpBtn", "Controls", 0f, -0.7f, 4.2f, 0.85f, 0xFF4C6FFF, "show:HelpPanel;hide:MainPanel", main)
        GameKit.button(s, "QuitBtn", "Quit", 0f, -1.75f, 4.2f, 0.85f, 0xFF334155, "quit", main)

        val tp = GameKit.panel(s, "TrackPanel", 0f, -0.4f, 11f, 7.4f, 0xE60F1528).off()
        GameKit.text(s, "TPTitle", "CHOOSE TRACK", 0f, 3.1f, 0.55f, parent = tp)
        GameKit.text(s, "MapLabel", "MAP", -4.2f, 2.1f, 0.32f, 0xFF94A3B8, tp)
        for (m in MAPS.indices) GameKit.button(s, "Map$m", MAP_TITLES[m], -1.5f + m * 4.6f, 2.1f, 4.3f, 0.85f, 0xFF334155, "call:selectMap", tp)
        GameKit.text(s, "RoadLabel", "ROAD", -4.2f, 0.9f, 0.32f, 0xFF94A3B8, tp)
        for (r in ROADS.indices) GameKit.button(s, "Road$r", ROADS[r], -2.9f + r * 3.5f, 0.9f, 3.3f, 0.85f, 0xFF334155, "call:selectRoad", tp, textSize = 0.3f)
        GameKit.text(s, "TrackInfo", "", 0f, -0.5f, 0.34f, 0xFFE2E8F0, tp)
        GameKit.button(s, "StartBtn", "START RACE", 1.6f, -2.6f, 4.4f, 1f, 0xFF22C55E, "call:startRace", tp, textSize = 0.42f)
        GameKit.button(s, "TPBackBtn", "Back", -2.8f, -2.6f, 2.6f, 0.9f, 0xFF334155, "hide:TrackPanel;show:MainPanel", tp)

        val set = GameKit.panel(s, "SettingsPanel", 0f, -0.4f, 6.4f, 6.6f).off()
        GameKit.text(s, "SetTitle", "SETTINGS", 0f, 2.7f, 0.55f, parent = set)
        GameKit.button(s, "LapsBtn", "Laps: 3", 0f, 1.7f, 5f, 0.8f, 0xFF4C6FFF, "call:cycleLaps", set)
        GameKit.button(s, "DiffBtn", "AI: Normal", 0f, 0.75f, 5f, 0.8f, 0xFF4C6FFF, "call:cycleDifficulty", set)
        GameKit.button(s, "QualityBtn", "Graphics: High", 0f, -0.2f, 5f, 0.8f, 0xFF4C6FFF, "call:cycleQuality", set)
        GameKit.button(s, "MusicBtn", "Music: ON", 0f, -1.15f, 5f, 0.8f, 0xFF4C6FFF, "call:toggleMusic", set)
        GameKit.button(s, "SetBackBtn", "Back", 0f, -2.4f, 3f, 0.8f, 0xFF334155, "hide:SettingsPanel;show:MainPanel", set)

        val help = GameKit.panel(s, "HelpPanel", 0f, -0.4f, 9.6f, 6f).off()
        GameKit.text(s, "HelpTitle", "HOW TO DRIVE", 0f, 2.4f, 0.55f, parent = help)
        GameKit.text(s, "HelpBody", "Arrows (left pad): steer.   GAS: accelerate.   BRAKE: brake / reverse.\nHold GAS + BRAKE while steering to drift through corners.\nNITRO gives a boost — it recharges slowly.\nGrass and sand slow you down; barriers bounce you back.\nCAM (top) switches chase / far / hood camera.\nFinish first to earn a trophy!", 0f, 0.2f, 0.28f, 0xFFE2E8F0, help)
        GameKit.button(s, "HelpBackBtn", "OK", 0f, -2.4f, 3f, 0.8f, 0xFF22C55E, "hide:HelpPanel;show:MainPanel", help)
        return s
    }

    private fun race(map: Int, road: Int, pts: List<FloatArray>, file: String, name: String): Scene {
        val desert = map == 1
        val s = Scene("Race_${MAPS[map]}_$road")
        s.fog = true; s.fogStart = 140f; s.fogEnd = 480f
        s.fogColor = if (desert) 0xFFE8B98A.toInt() else 0xFFB9D4EE.toInt()
        s.ambient = if (desert) 0xFF6A5448.toInt() else 0xFF4E5968.toInt()
        camera3D(s, if (desert) 0xFF3D6FB0.toInt() else 0xFF2F6FD0.toInt(), if (desert) 0xFFF6C58E.toInt() else 0xFFCFE4F7.toInt())
        GameKit.obj3(s, "Sun", 0f, 50f, 0f).also { it.rotX = if (desert) -32f else -52f; it.rotY = 40f }
            .add(Light().also { it.kind = 0; it.intensity = if (desert) 1.15f else 1.05f; it.color = if (desert) 0xFFFFE0B8.toInt() else 0xFFFFF6E8.toInt() })
        GameKit.obj(s, "Race").script("RaceManager.js", "trackFile=$file, trackName=$name")

        val minX = pts.minOf { it[0] }; val maxX = pts.maxOf { it[0] }; val minZ = pts.minOf { it[1] }; val maxZ = pts.maxOf { it[1] }
        val gcx = (minX + maxX) / 2; val gcz = (minZ + maxZ) / 2
        val size = maxOf(maxX - minX, maxZ - minZ) + 400f
        GameKit.obj3(s, "Ground", gcx, -0.06f, gcz, size, 0.1f, size)
            .mesh(0, if (desert) 0xFFE0B27A else 0xFF7FB069, if (desert) "DesertSand.png" else "GrassField.png", size / 12f)
            .also { it.get<MeshRenderer>()!!.specular = 0.05f }

        // road, curbs and barriers
        val n = pts.size
        val road3 = GameKit.obj3(s, "Track", 0f, 0f, 0f)
        for (i in 0 until n) {
            val a = pts[i]; val b = pts[(i + 1) % n]
            val dx = b[0] - a[0]; val dz = b[1] - a[1]; val len = sqrt(dx * dx + dz * dz)
            val ang = Math.toDegrees(atan2(dx, dz).toDouble()).toFloat()
            val mx = (a[0] + b[0]) / 2; val mz = (a[1] + b[1]) / 2
            GameKit.obj3(s, "Road$i", mx, -0.0f, mz, WIDTH, 0.1f, len + 0.6f, road3).mesh(0, if (desert) 0xFFD8CFC4 else 0xFFFFFFFF, "Road.png")
                .also { it.rotY = ang; it.get<MeshRenderer>()!!.specular = 0.15f; it.get<MeshRenderer>()!!.castShadows = false }
            val nx = dz / len; val nz = -dx / len
            val turn = turnAt(pts, i)
            if (kotlin.math.abs(turn) > 0.08f) {
                // curb on the inside of the corner
                val side = if (turn > 0) -1f else 1f
                val off = WIDTH / 2 + 0.6f
                GameKit.obj3(s, "Curb$i", mx + nx * off * side, 0.02f, mz + nz * off * side, 1.2f, 0.12f, len + 0.4f, road3)
                    .mesh(0, 0xFFFFFFFF, "Curb.png").also { it.rotY = ang; it.get<MeshRenderer>()!!.castShadows = false }
            }
            if (i % 2 == 0) {
                val c = pts[(i + 2) % n]
                val dx2 = c[0] - a[0]; val dz2 = c[1] - a[1]; val len2 = sqrt(dx2 * dx2 + dz2 * dz2)
                val ang2 = Math.toDegrees(atan2(dx2, dz2).toDouble()).toFloat()
                val nx2 = dz2 / len2; val nz2 = -dx2 / len2
                val bx = (a[0] + c[0]) / 2; val bz = (a[1] + c[1]) / 2
                for (side in listOf(-1f, 1f)) {
                    val off = WIDTH / 2 + 5f
                    val color = if (desert) 0xFFB07A4F else if ((i / 2) % 2 == 0) 0xFFE53935 else 0xFFF5F5F5
                    GameKit.obj3(s, "Barrier$i${if (side > 0) "R" else "L"}", bx + nx2 * off * side, if (desert) 0.7f else 0.45f, bz + nz2 * off * side,
                        if (desert) 1.2f else 0.6f, if (desert) 1.4f else 0.9f, len2 + 0.3f, road3)
                        .mesh(0, color, if (desert) "Concrete.png" else "").also { it.rotY = ang2 }
                }
            }
        }
        // start line + gantry
        val s0 = pts[0]; val s1 = pts[1]
        val sAng = atan2(s1[0] - s0[0], s1[1] - s0[1])
        val sDeg = Math.toDegrees(sAng.toDouble()).toFloat()
        val snx = cos(sAng); val snz = -sin(sAng)
        GameKit.obj3(s, "StartLine", s0[0], 0.01f, s0[1], WIDTH, 0.1f, 2f).mesh(0, 0xFFFFFFFF, "Checker.png", 4f).also { it.rotY = sDeg }
        for (side in listOf(-1f, 1f)) GameKit.obj3(s, "GantryPost${if (side > 0) "R" else "L"}", s0[0] + snx * (WIDTH / 2 + 2.5f) * side, 3.5f, s0[1] + snz * (WIDTH / 2 + 2.5f) * side, 0.8f, 7f, 0.8f).mesh(0, 0xFF37474F)
        GameKit.obj3(s, "GantryBeam", s0[0], 7.2f, s0[1], WIDTH + 6f, 1.4f, 1f).mesh(0, 0xFF111827).also { it.rotY = sDeg; it.get<MeshRenderer>()!!.emission = 0.2f }
        GameKit.obj3(s, "GantryLights", s0[0] - sin(sAng) * 0.55f, 7.2f, s0[1] - cos(sAng) * 0.55f, WIDTH, 0.5f, 0.1f).mesh(0, 0xFF22C55E).also { it.rotY = sDeg; it.get<MeshRenderer>()!!.emission = 1f }

        // grandstand near the start
        val gx = s0[0] + snx * (WIDTH / 2 + 16f); val gz = s0[1] + snz * (WIDTH / 2 + 16f)
        for (k in 0 until 4) GameKit.obj3(s, "Stand$k", gx + snx * k * 2f, 0.8f + k * 1.2f, gz + snz * k * 2f, 2.2f, 1.6f + k * 2.4f, 40f)
            .mesh(0, if (k % 2 == 0) 0xFF546E7A else 0xFF78909C).also { it.rotY = sDeg; it.scaleY = 1.6f + k * 0.2f; it.y = 0.8f + k * 1.2f }

        // scenery
        val rnd = Random(1000 + map * 10 + road)
        var placed = 0; var tries = 0
        while (placed < 70 && tries < 2000) {
            tries++
            val x = rnd.nextFloat() * (maxX - minX + 160f) + minX - 80f
            val z = rnd.nextFloat() * (maxZ - minZ + 160f) + minZ - 80f
            val d = distToTrack(pts, x, z)
            if (d < WIDTH / 2 + 12f) continue
            if (kotlin.math.abs(x - gx) < 20f && kotlin.math.abs(z - gz) < 30f) continue
            placed++
            val sc = 1.2f + rnd.nextFloat() * 1.4f
            if (desert) {
                if (rnd.nextFloat() < 0.55f) {
                    val cactus = GameKit.obj3(s, "Cactus$placed", x, 1.6f * sc, z, 0.6f * sc, 3.2f * sc, 0.6f * sc).mesh(3, 0xFF4E8A3E)
                    GameKit.obj3(s, "Arm", 0.9f, 0.1f, 0f, 0.8f, 0.3f, 0.8f, cactus).mesh(3, 0xFF4E8A3E).also { it.rotation = 90f }
                } else GameKit.obj3(s, "Rock$placed", x, 0f, z, sc * 1.4f, sc, sc * 1.4f).mesh(8, 0xFFC08A5A, model = "RockModel.smodel").also { it.rotY = rnd.nextFloat() * 360f }
            } else {
                if (rnd.nextFloat() < 0.85f) GameKit.obj3(s, "Tree$placed", x, 0f, z, sc, sc, sc).mesh(8, 0xFFFFFFFF, model = "TreeModel.smodel").also { it.rotY = rnd.nextFloat() * 360f }
                else GameKit.obj3(s, "House$placed", x, 0f, z, 1.5f, 1.5f, 1.5f).mesh(8, 0xFFFFFFFF, model = "HouseModel.smodel").also { it.rotY = rnd.nextFloat() * 360f }
            }
        }
        // distant mountains / mesas ring
        val ring = size / 2 - 60f
        for (k in 0 until 14) {
            val a = k / 14.0 * 2 * PI
            val h = 40f + (k * 37 % 30)
            val x = gcx + (ring * cos(a)).toFloat(); val z = gcz + (ring * sin(a)).toFloat()
            if (desert) GameKit.obj3(s, "Mesa$k", x, h / 2, z, 70f, h, 55f).mesh(3, 0xFFB5653A).also { it.get<MeshRenderer>()!!.castShadows = false }
            else GameKit.obj3(s, "Mountain$k", x, h / 2, z, 90f, h * 1.4f, 90f).mesh(4, 0xFF5E7D62).also { it.get<MeshRenderer>()!!.castShadows = false }
        }
        if (!desert) GameKit.obj3(s, "Lake", gcx, -0.03f, gcz, 30f, 0.05f, 30f).mesh(3, 0xFF4FA3E0, "Water.png", 3f).also {
            // only if the lake would not cover the road
            if (distToTrack(pts, gcx, gcz) < 30f) it.active = false
            it.get<MeshRenderer>()!!.specular = 0.9f
        }

        // cars on the starting grid (behind the line)
        val names = listOf("Player", "AI1", "AI2", "AI3", "AI4", "AI5")
        for (k in names.indices) {
            val back = 7f + k * 5.5f
            val side = if (k % 2 == 0) 3.2f else -3.2f
            val x = s0[0] - sin(sAng) * back + snx * side
            val z = s0[1] - cos(sAng) * back + snz * side
            val car = GameKit.obj3(s, names[k], x, 0.05f, z).mesh(8, 0xFFFFFFFF, model = carFiles[k])
            car.rotY = sDeg; car.tag = if (k == 0) "Player" else "AI"
            car.get<MeshRenderer>()!!.specular = 0.8f; car.get<MeshRenderer>()!!.shininess = 64f
            if (k == 0) GameKit.obj3(s, "NitroFx", 0f, 0.5f, -1.9f, parent = car).particles {
                emitting = false; rate = 80f; lifetime = 0.25f; speed = 3f; direction = -90f; spread = 25f; startSize = 0.45f; endSize = 0.05f
                startColor = 0xFF60A5FA.toInt(); endColor = 0x00F97316; additive = true; texture = "SoftDot.png"
            }
        }

        // HUD
        GameKit.text(s, "TrackText", name, 0.2f, -0.4f, 0.26f, 0xFFCBD5E1, anchor = GameKit.TL, align = 0)
        val left = GameKit.panel(s, "InfoPanel", 1.9f, -1.5f, 3.4f, 1.6f, 0x99101828, anchor = GameKit.TL, border = 0x00000000)
        GameKit.text(s, "PosText", "1/6", -0.7f, 0.25f, 0.7f, 0xFFFACC15, left)
        GameKit.text(s, "LapText", "LAP 1/3", 0.95f, 0.4f, 0.28f, parent = left)
        GameKit.text(s, "TimeText", "0:00.00", 0.95f, 0f, 0.26f, 0xFFCBD5E1, left)
        GameKit.text(s, "LastLapText", "", 0f, -0.5f, 0.22f, 0xFF94A3B8, left)
        GameKit.text(s, "BestText", "", 0.2f, -2.6f, 0.22f, 0xFF94A3B8, anchor = GameKit.TL, align = 0)
        val spd = GameKit.panel(s, "SpeedPanel", 0f, 1.35f, 3.2f, 1.6f, 0x99101828, anchor = GameKit.BOTTOM, border = 0x00000000)
        GameKit.text(s, "SpeedText", "0", -0.35f, 0.25f, 0.75f, parent = spd)
        GameKit.text(s, "KmhText", "km/h", 0.95f, 0.1f, 0.22f, 0xFF94A3B8, spd)
        GameKit.text(s, "GearText", "1", 0.95f, 0.45f, 0.3f, 0xFFFACC15, spd)
        GameKit.bar(s, "SpeedBar", 0f, -0.35f, 2.8f, 0.14f, 0xFF22D3EE, spd, value = 0f)
        GameKit.text(s, "NitroLabel", "NITRO", -1.05f, -0.6f, 0.18f, 0xFF60A5FA, spd)
        GameKit.bar(s, "NitroBar", 0.35f, -0.6f, 2.1f, 0.12f, 0xFF60A5FA, spd)
        val mm = GameKit.panel(s, "MiniMap", -1.6f, -2.5f, 2.3f, 2.3f, 0x88101828, anchor = GameKit.TR, border = 0x44FFFFFF)
        val (cx, cz, sc) = minimap(pts)
        for (i in 0 until n step maxOf(1, n / 48)) {
            val d = GameKit.panel(s, "MapTrack$i", (pts[i][0] - cx) * sc, -(pts[i][1] - cz) * sc, 0.08f, 0.08f, 0xCCE2E8F0, mm, border = 0x00000000, corner = 0.04f)
            d.order = 201
        }
        for (k in names.indices) {
            val d = GameKit.panel(s, "Dot$k", 0f, 0f, if (k == 0) 0.2f else 0.15f, if (k == 0) 0.2f else 0.15f, (carColors[k].toLong() and 0xFFFFFFFFL), mm, border = if (k == 0) 0xFFFFFFFFL else 0x00000000L, corner = 0.1f)
            d.order = 205 - k
            d.get<UIPanel>()!!.border = if (k == 0) 0.03f else 0f
        }
        GameKit.button(s, "CamBtn", "CAM", -2.9f, -0.6f, 1.2f, 0.8f, 0x99101828, "call:toggleCam", anchor = GameKit.TR, textSize = 0.28f)
        GameKit.text(s, "CountText", "", 0f, 1f, 1.8f, 0xFFFACC15).off()
        GameKit.text(s, "BannerText", "", 0f, 2.2f, 0.7f, 0xFFFFFFFF).off()
        GameKit.text(s, "WrongText", "", 0f, 0.6f, 0.6f, 0xFFEF4444)
        GameKit.pauseMenu(s, "RallyMenu", 0xFF22C55E)
        val res = GameKit.panel(s, "ResultPanel", 0f, 0f, 8f, 7.4f).off()
        GameKit.text(s, "ResultTitle", "RACE RESULTS", 0f, 3f, 0.6f, 0xFFFACC15, res)
        GameKit.text(s, "ResultText", "", 0f, 0.4f, 0.3f, parent = res)
        GameKit.button(s, "ResRestartBtn", "Race Again", -1.9f, -2.9f, 3.4f, 0.9f, 0xFF22C55E, "reload", res)
        GameKit.button(s, "ResMenuBtn", "Menu", 1.9f, -2.9f, 3.4f, 0.9f, 0xFF334155, "scene:RallyMenu", res)
        return s
    }
}
