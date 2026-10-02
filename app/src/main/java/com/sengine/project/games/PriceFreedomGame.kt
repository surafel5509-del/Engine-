package com.sengine.project.games

import com.sengine.engine.anim.AnimationClip
import com.sengine.engine.controls.ControlDef
import com.sengine.engine.controls.ControlLayout
import com.sengine.engine.core.Animator
import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SpriteRenderer
import com.sengine.project.Project
import com.sengine.project.games.GameKit.body
import com.sengine.project.games.GameKit.box
import com.sengine.project.games.GameKit.circle
import com.sengine.project.games.GameKit.off
import com.sengine.project.games.GameKit.label
import com.sengine.project.games.GameKit.particles
import com.sengine.project.games.GameKit.script
import com.sengine.project.games.GameKit.sprite

/**
 * Price of Freedom — a systemic top-down prison escape immersive sim.  The project intentionally
 * ships as an editable S Engine game: scenes, custom controls, texture shader, UI screens,
 * animation clips and JavaScript behaviours are all regular project assets after creation.
 */
internal object PriceFreedomGame {
    private val scripts = listOf("PFMenu.js", "PFGame.js", "PFPlayer.js", "PFGuard.js", "PFPrisoner.js", "PFItem.js")

    fun build(p: Project) {
        GameKit.install(p,
            "Concrete", "Floor Tiles", "Dirt", "Wood Planks", "Rusty Metal", "Wooden Crate", "Prisoner (top-down)", "Prison Guard (top-down)",
            "Security Camera", "Evidence File", "Rope Coil", "Prison Gate", "Prison Map", "Hero Run (4 frames)",
            "Key", "Health Kit", "Soft Particle", "Spark", "UI Panel (dark)", "Coin Pickup", "Menu Select", "Footstep", "Hit", "Power Up", "Block Break", "Win Jingle", "Game Over", "UI Click (default)",
            "Menu Theme (song)", "Spooky Night (song)", "Victory Fanfare (song)")
        for (s in scripts) p.writeAsset(s, Games.res("freedom/$s"))
        p.writeAsset("PrisonPulse.glsl", PRISON_PULSE_SHADER)
        p.writeAsset("PrisonNoir.glsl", PRISON_NOIR_SHADER)
        p.writeAsset("PrisonerWalk.anim", AnimationClip("HeroRun.png", 4, 1, mutableListOf(0, 1, 2, 3, 0, 1, 2, 3), 10f, true).toJson().toString(2))
        p.writeAsset("PrisonerRun.anim", AnimationClip("HeroRun.png", 4, 1, mutableListOf(0, 1, 2, 3, 2, 1), 15f, true).toJson().toString(2))
        p.writeAsset("CaseNotes.txt", CASE_NOTES)
        p.saveControls(prisonControls())
        p.saveScene(menu())
        p.saveScene(prison())
        p.startScene = "Menu"
        p.orientation = 0
        p.description = "Price of Freedom — a realistic 2D prison escape sandbox / immersive sim. Learn routines, build trust, collect evidence, then choose an escape route."
        p.accent = 0xFFB89245.toInt()
    }

    private fun prisonControls() = ControlLayout("Price of Freedom", mutableListOf(
        ControlDef("joystick", "move", "", 0.14f, 0.77f, 0.32f, 0xFF607D8B.toInt()),
        ControlDef("button", "Interact", "E", 0.90f, 0.74f, 0.18f, 0xFFB89245.toInt(), "search"),
        ControlDef("button", "Search", "F", 0.75f, 0.86f, 0.14f, 0xFF7C8B5A.toInt(), "search"),
        ControlDef("button", "Hide", "C", 0.74f, 0.60f, 0.13f, 0xFF566878.toInt(), "eye"),
        ControlDef("button", "Run", "RUN", 0.90f, 0.52f, 0.13f, 0xFFC05A4D.toInt(), "bolt"),
        ControlDef("button", "Use", "R", 0.61f, 0.72f, 0.11f, 0xFF3F8F6A.toInt(), "heart"),
    ))

    private fun menu(): Scene {
        val s = Scene("Menu"); s.gravityY = 0f
        GameKit.camera2D(s, 6f, 0xFF101516).also { it.get<Camera2D>()!!.postFx = 3; it.get<Camera2D>()!!.postIntensity = 0.7f }
        GameKit.obj(s, "MenuFloor", 0f, 0f, 30f, 16f).sprite(0xFF59615D, 0, "Concrete.png").also {
            it.get<SpriteRenderer>()!!.tileX = 7f; it.get<SpriteRenderer>()!!.tileY = 4f; it.order = -100
        }
        GameKit.obj(s, "MenuManager").script("PFMenu.js")
        GameKit.obj(s, "Gate", 7.1f, -0.2f, 4.2f, 4.2f).sprite(0xFFFFFFFF, 0, "PrisonGate.png").also { it.order = -12 }
        GameKit.obj(s, "Escapee", -6.7f, -1.7f, 2.4f, 2.4f).sprite(0xFFFFFFFF, 0, "Prisoner.png").also { it.rotation = -15f; it.order = 2 }
        GameKit.obj(s, "Warden", 6.2f, -2.2f, 2.2f, 2.2f).sprite(0xFFFFFFFF, 0, "PrisonGuard.png").also { it.rotation = 160f; it.order = 3 }
        GameKit.obj(s, "Searchlight", 6.7f, 2.5f).particles {
            applyPreset(ParticleEmitter.PRESETS.indexOf("Steam")); rate = 7f; direction = 205f; spread = 10f; startColor = 0x33E8F4FF; endColor = 0x00E8F4FF; startSize = 0.7f; endSize = 2.8f; texture = "SoftDot.png"
        }.also { it.order = -2 }
        GameKit.text(s, "Title", "PRICE OF FREEDOM", 0f, 3.45f, 0.98f, 0xFFF0E5CA)
        GameKit.text(s, "Subtitle", "An immersive prison escape sandbox", 0f, 2.62f, 0.34f, 0xFFBBC2BC)
        GameKit.text(s, "Tagline", "Every favor has a cost. Every minute is remembered.", 0f, 2.05f, 0.25f, 0xFF929991)
        val main = GameKit.panel(s, "MainPanel", 0f, -1.05f, 6.0f, 5.7f, 0xE80E1213, border = 0x889B7A42, corner = 0.18f, texture = "UIPanelDark.png")
        GameKit.button(s, "NewCampaignBtn", "BEGIN YOUR CASE", 0f, 1.65f, 5.15f, 0.9f, 0xFFB07D31, "call:newCampaign", main, textSize = 0.37f)
        GameKit.button(s, "ContinueBtn", "Continue", 0f, 0.58f, 5.15f, 0.78f, 0xFF3B4B4B, "scene:Prison", main)
        GameKit.button(s, "BriefingBtn", "Case briefing", 0f, -0.43f, 5.15f, 0.78f, 0xFF303C3E, "show:BriefingPanel;hide:MainPanel", main)
        GameKit.button(s, "SettingsBtn", "Settings", -1.3f, -1.52f, 2.45f, 0.72f, 0xFF263436, "show:SettingsPanel;hide:MainPanel", main, textSize = 0.3f)
        GameKit.button(s, "ControlsBtn", "Controls", 1.3f, -1.52f, 2.45f, 0.72f, 0xFF263436, "show:ControlsPanel;hide:MainPanel", main, textSize = 0.3f)
        GameKit.text(s, "RecordText", "", 0f, 0.42f, 0.27f, 0xFFE2BF70, anchor = GameKit.BOTTOM)

        val briefing = GameKit.panel(s, "BriefingPanel", 0f, -0.15f, 9.8f, 7.1f, 0xF00E1213, border = 0x889B7A42, corner = 0.18f).off()
        GameKit.text(s, "BriefingTitle", "THE CASE", 0f, 2.85f, 0.65f, 0xFFE2BF70, briefing)
        GameKit.text(s, "BriefingBody", "You were imprisoned under a state of emergency for a crime you did not commit.\n\nLearn who controls the prison. Trade favors, gather tools and find the case evidence. Every guard remembers how you act.\n\nFive routes exist: Main Gate, Tunnel, Wall, Helicopter and Sewer. The sewer is the quietest route — not the safest.", 0f, 0.62f, 0.31f, 0xFFD5D9D3, briefing)
        GameKit.button(s, "BriefingBackBtn", "I understand", 0f, -2.65f, 3.4f, 0.8f, 0xFFB07D31, "hide:BriefingPanel;show:MainPanel", briefing)

        val settings = GameKit.panel(s, "SettingsPanel", 0f, -0.2f, 6.4f, 5.3f, 0xF00E1213, border = 0x889B7A42, corner = 0.18f).off()
        GameKit.text(s, "SettingsTitle", "SETTINGS", 0f, 2.0f, 0.6f, 0xFFE2BF70, settings)
        GameKit.button(s, "MusicBtn", "Music: ON", 0f, 0.85f, 5f, 0.82f, 0xFF303C3E, "call:toggleMusic", settings)
        GameKit.button(s, "SfxBtn", "Sound FX: 100%", 0f, -0.18f, 5f, 0.82f, 0xFF303C3E, "call:cycleSfx", settings)
        GameKit.button(s, "ResetBtn", "Clear records", 0f, -1.2f, 5f, 0.82f, 0xFF773332, "call:resetRecords", settings)
        GameKit.button(s, "SettingsBackBtn", "Back", 0f, -2.15f, 3f, 0.72f, 0xFFB07D31, "hide:SettingsPanel;show:MainPanel", settings)

        val controls = GameKit.panel(s, "ControlsPanel", 0f, -0.15f, 9.5f, 7.1f, 0xF00E1213, border = 0x889B7A42, corner = 0.18f).off()
        GameKit.text(s, "ControlsTitle", "FIELD CONTROLS", 0f, 2.85f, 0.6f, 0xFFE2BF70, controls)
        GameKit.text(s, "ControlsBody", "WASD / joystick  Move       Shift / RUN  Sprint\nE / tap  Interact            F  Search\nR  Use food or medicine       C  Hide\nTab  Inventory     M  Map     J  Journal     T  Time\n\nMobile: the custom S Engine controller exposes Move, Interact, Search, Hide, Run and Use. HUD buttons open your inventory, map and journal.", 0f, 0.48f, 0.29f, 0xFFD5D9D3, controls)
        GameKit.button(s, "ControlsBackBtn", "Back", 0f, -2.72f, 3f, 0.72f, 0xFFB07D31, "hide:ControlsPanel;show:MainPanel", controls)
        return s
    }

    private fun prison(): Scene {
        val s = Scene("Prison"); s.gravityY = 0f
        GameKit.camera2D(s, 7f, 0xFF151918, follow = "Player").also {
            it.get<Camera2D>()!!.postFx = 9; it.get<Camera2D>()!!.postIntensity = 0.72f; it.get<Camera2D>()!!.postShader = "PrisonNoir.glsl"
        }
        GameKit.obj(s, "Ground", 0f, 0f, 40f, 29f).sprite(0xFF59594C, 0, "Concrete.png").also {
            it.get<SpriteRenderer>()!!.tileX = 10f; it.get<SpriteRenderer>()!!.tileY = 7f; it.order = -100
        }
        GameKit.obj(s, "Game").script("PFGame.js")

        // The readable prison plan: rooms are visual zones; concrete walls, checkpoints and props give physical cover.
        fun zone(name: String, x: Float, y: Float, w: Float, h: Float, color: Long, texture: String = "Tiles.png") {
            GameKit.obj(s, name, x, y, w, h).sprite(color, 0, texture).also {
                it.get<SpriteRenderer>()!!.tileX = maxOf(1f, w / 2.2f); it.get<SpriteRenderer>()!!.tileY = maxOf(1f, h / 2.2f); it.order = -70
            }
            GameKit.obj(s, "${name}Label", x, y + h / 2 - 0.45f).label(name.uppercase(), 0.24f, 0xFFCCCEC4, screen = false).also { it.order = -60 }
        }
        zone("Block A", -11f, 7.1f, 8f, 5.3f, 0xFF6C706B)
        zone("Block B", 0f, 7.1f, 8f, 5.3f, 0xFF676C68)
        zone("Block C", 11f, 7.1f, 8f, 5.3f, 0xFF565E5D)
        zone("Kitchen", -11f, 0.8f, 8f, 4.2f, 0xFFD2D0C7)
        zone("Hospital", 0f, 0.8f, 8f, 4.2f, 0xFF8AA394)
        zone("Bathroom", 11f, 0.8f, 8f, 4.2f, 0xFF7D9EAB)
        zone("Chapel", -11f, -5f, 8f, 4.4f, 0xFF796F66, "Wood.png")
        zone("Workshop", 0f, -5f, 8f, 4.4f, 0xFF76634D, "Rust.png")
        zone("Library", 11f, -5f, 8f, 4.4f, 0xFF6B6257, "Wood.png")
        zone("Guard Quarters", 0f, -9.6f, 18f, 2.8f, 0xFF4B5356, "Rust.png")

        fun wall(name: String, x: Float, y: Float, w: Float, h: Float, color: Long = 0xFF4C5453) {
            val o = GameKit.obj(s, name, x, y, w, h).sprite(color, 0, "Concrete.png").box(false, w, h).body(type = 2)
            o.order = -40; o.tag = "Wall"; o.get<SpriteRenderer>()!!.tileX = maxOf(1f, w / 1.5f); o.get<SpriteRenderer>()!!.tileY = maxOf(1f, h / 1.5f)
        }
        wall("OuterWallNorth", 0f, 12.1f, 38f, 1f); wall("OuterWallSouth", 0f, -12.1f, 38f, 1f)
        wall("OuterWallWest", -18.5f, 0f, 1f, 24f); wall("OuterWallEast", 18.5f, 0f, 1f, 24f)
        wall("QuartersLeft", -9.1f, -9.6f, 0.5f, 3.5f); wall("QuartersRight", 9.1f, -9.6f, 0.5f, 3.5f)
        wall("QuartersNorth", 0f, -8.15f, 18.5f, 0.45f)
        // small cover / sight blockers in the yard
        for ((i, p) in listOf(-5f to 3.8f, 5f to 3.8f, -5f to -2.2f, 5f to -2.2f, -15f to -7.2f, 15f to -7.2f).withIndex()) {
            GameKit.obj(s, "CoverCrate$i", p.first, p.second, 1.35f, 1.35f).sprite(0xFFFFFFFF, 0, "Crate.png").box().body(type = 2).also { it.order = -10; it.tag = "Wall" }
        }
        // Cells and bars — visual detail beside the player start.
        for (i in 0 until 4) {
            val x = -14.1f + i * 1.9f
            GameKit.obj(s, "CellBar$i", x, 8.0f, 0.13f, 3.8f).sprite(0xFF3D4649, 0, "Rust.png").also { it.order = -20 }
        }
        GameKit.obj(s, "MainGate", 0f, -11.15f, 3.2f, 2.2f).sprite(0xFFFFFFFF, 0, "PrisonGate.png").also { it.order = -15; it.tag = "Point" }
        GameKit.obj(s, "SewerHatch", 13.7f, -8.7f, 1.2f, 1.2f).sprite(0xFF455A64, 1, "Rust.png").also { it.order = -8; it.tag = "Point" }
        GameKit.obj(s, "TunnelSpot", -15.5f, -9.3f, 1.1f, 1.1f).sprite(0xFF72563A, 1, "Dirt.png").also { it.order = -8; it.tag = "Point" }
        GameKit.obj(s, "WallClimb", 17.1f, 5.4f, 1.3f, 2.6f).sprite(0xFFFFFFFF, 0, "PrisonGate.png").also { it.order = -15; it.tag = "Point" }
        GameKit.obj(s, "Helipad", -15.2f, -10f, 2.4f, 1.1f).sprite(0xFF5D686A, 0, "Rust.png").also { it.order = -12; it.tag = "Point" }

        fun point(name: String, x: Float, y: Float, scale: Float = 0.85f, texture: String = "Key.png", tint: Long = 0xFFFFFFFF) {
            GameKit.obj(s, name, x, y, scale, scale).sprite(tint, 0, texture).also { it.tag = "Point"; it.order = 4 }
        }
        point("CellBed", -13.3f, 7.0f, 0.9f, "Crate.png", 0xFF8B8171)
        point("KitchenCache", -12.8f, 0.4f, 0.7f, "Crate.png", 0xFFDED3B2)
        point("HospitalCabinet", -1.5f, 0.6f, 0.72f, "Medkit.png")
        point("BathroomVent", 13.5f, 0.4f, 0.7f, "SecurityCamera.png", 0xFFB8D7E5)
        point("WorkshopBench", 1.7f, -5.2f, 0.8f, "Crate.png", 0xFFC39259)
        point("LibraryDesk", 12.7f, -5.3f, 0.8f, "PrisonMap.png")
        point("SecurityTerminal", 4.5f, -9.2f, 0.86f, "SecurityCamera.png")
        point("HideLocker", -8.1f, 7f, 0.9f, "PrisonGate.png", 0xFF5D6662)
        point("HideCrate", -5f, -2.2f, 0.9f, "Crate.png", 0xFF625745)
        point("HidePews", -11f, -5.1f, 0.85f, "Wood.png", 0xFF80694A)

        fun item(name: String, kind: String, x: Float, y: Float, texture: String, tint: Long = 0xFFFFFFFF, scale: Float = 0.68f) {
            val o = GameKit.obj(s, name, x, y, scale, scale).sprite(tint, 0, texture).circle(true, 0.38f).body(type = 1, gravity = 0f).script("PFItem.js", "kind=$kind").also { it.tag = "Item"; it.order = 6 }
            o.get<SpriteRenderer>()!!.shader = "PrisonPulse.glsl"
        }
        item("RopeLaundry", "rope", 14.1f, 1.45f, "Rope.png")
        item("RopeChapel", "rope", -14.2f, -5.7f, "Rope.png")
        item("RopeWorkshop", "rope", -2.9f, -5.3f, "Rope.png")
        item("Gloves", "gloves", -9.1f, -0.2f, "Prisoner.png", 0xFFBFCBC3)
        item("Shoes", "shoes", -12.5f, -7.2f, "Prisoner.png", 0xFF554C43)
        item("Flashlight", "flashlight", 6.2f, 7.5f, "SecurityCamera.png", 0xFFE4D277)
        item("Mask", "mask", 10.0f, 0.2f, "Medkit.png", 0xFFCED8D4)
        item("CaseEvidence", "evidence", 13.7f, -5.0f, "Evidence.png", scale = 0.78f)
        item("SpareCoffee", "coffee", -10.0f, 1.1f, "Key.png", 0xFF6F4931)
        item("HiddenSpoon", "spoon", -13.5f, 0.1f, "Key.png", 0xFFD9D7CA)
        item("GrapplingHook", "hook", 2.7f, -5.8f, "Key.png", 0xFF9EA7A3)

        fun prisoner(id: String, x: Float, y: Float, tint: Long = 0xFFFFFFFF) {
            val o = GameKit.obj(s, id, x, y, 1.05f, 1.05f).sprite(tint, 0, "Prisoner.png").circle(false, 0.30f).body(type = 1, gravity = 0f).script("PFPrisoner.js", "id=$id")
            o.tag = "Prisoner"; o.order = 7
        }
        prisoner("DoctorYosef", -1.0f, 1.2f, 0xFFB8D8C0)
        prisoner("EngineerSamuel", 0.2f, -5.9f, 0xFFD1AD79)
        prisoner("MapMakerMulu", 10.2f, -4.5f, 0xFFB6BAC8)
        prisoner("ThiefBenjamin", -12.0f, -5.6f, 0xFFC2A7A0)
        prisoner("ConmanGirma", 5.8f, 1.0f, 0xFFC7B58B)
        prisoner("KillerTemesgen", -7.0f, -4.8f, 0xFF9C7771)

        fun guard(id: String, route: String, x: Float, y: Float, vision: Float = 3.2f) {
            val o = GameKit.obj(s, id, x, y, 1.15f, 1.15f).sprite(0xFFFFFFFF, 0, "PrisonGuard.png").circle(false, 0.34f).body(type = 1, gravity = 0f).script("PFGuard.js", "id=$id,route=$route,vision=$vision")
            o.tag = "Guard"; o.order = 8
        }
        guard("SergeantTesfaye", "yard", -1.5f, 3.4f, 3.7f)
        guard("GuardDawit", "kitchen", -12f, 2.6f, 3.1f)
        guard("GuardSolomon", "library", 14.2f, -3.5f, 2.6f)
        guard("SergeantMarta", "quarters", -7.2f, -9.4f, 4.0f)
        guard("ChiefAbebe", "gate", 0f, -10.1f, 4.4f)
        // Cameras reuse the guard perception script with a stationary camera route.
        fun camera(name: String, x: Float, y: Float) {
            val c = GameKit.obj(s, name, x, y, 0.85f, 0.85f).sprite(0xFFFFFFFF, 0, "SecurityCamera.png").script("PFGuard.js", "id=$name,route=camera,vision=4.4")
            c.tag = "Guard"; c.order = 9; c.get<SpriteRenderer>()!!.shader = "PrisonPulse.glsl"
        }
        camera("CameraYard", 6.3f, 4.6f); camera("CameraGate", 8f, -9.3f)

        // Ambient steam, dust and lamps make the spaces feel occupied while remaining cheap on mobile.
        GameKit.obj(s, "KitchenSteam", -10f, 1.2f).particles { applyPreset(ParticleEmitter.PRESETS.indexOf("Steam")); texture = "SoftDot.png"; rate = 10f }.also { it.order = 1 }
        GameKit.obj(s, "YardDust", 0f, 0f).particles { applyPreset(ParticleEmitter.PRESETS.indexOf("Dust")); texture = "SoftDot.png"; rate = 3f; wind = 0.35f; startSize = 0.22f; endSize = 0.8f }.also { it.order = 1 }
        GameKit.obj(s, "AlarmSparks").particles { applyPreset(ParticleEmitter.PRESETS.indexOf("Sparks")); texture = "Spark.png"; emitting = false; rate = 0f }.off().also { it.order = 20 }

        val player = GameKit.obj(s, "Player", -13.2f, 7.0f, 1.12f, 1.12f).sprite(0xFFFFFFFF, 0, "HeroRun.png").circle(false, 0.31f).body(type = 0, gravity = 0f, friction = 0f).script("PFPlayer.js")
        player.tag = "Player"; player.order = 12; player.get<Rigidbody2D>()!!.drag = 0f
        player.add(Animator().also { it.clip = "PrisonerWalk.anim"; it.playOnStart = true })
        player.get<SpriteRenderer>()!!.shader = "PrisonPulse.glsl"

        // HUD created with S Engine UI components (editable in UI Creator).
        val leftHud = GameKit.panel(s, "StatusPanel", 3.15f, -0.86f, 6.05f, 1.65f, 0xD9111616, anchor = GameKit.TL, border = 0x667B8077, corner = 0.12f)
        GameKit.text(s, "HealthLabel", "HEALTH", -2.35f, 0.40f, 0.20f, 0xFFE78C86, leftHud)
        GameKit.bar(s, "HealthBar", 0.45f, 0.40f, 4.6f, 0.23f, 0xFFBF4A42, leftHud)
        GameKit.text(s, "EnergyLabel", "ENERGY", -2.35f, -0.12f, 0.20f, 0xFFB9C778, leftHud)
        GameKit.bar(s, "EnergyBar", 0.45f, -0.12f, 4.6f, 0.23f, 0xFF8BA34A, leftHud)
        GameKit.text(s, "SuspicionLabel", "HEAT", -2.35f, -0.62f, 0.20f, 0xFFE0B167, leftHud)
        GameKit.bar(s, "SuspicionBar", 0.45f, -0.62f, 4.6f, 0.20f, 0xFFD06D3B, leftHud, value = 0.05f)
        GameKit.text(s, "TimeText", "DAY 1  •  06:45", -0.65f, -0.55f, 0.34f, 0xFFF0E5CA, anchor = GameKit.TR, align = 2)
        GameKit.text(s, "KnowledgeText", "KNOWLEDGE 0", -0.65f, -1.0f, 0.23f, 0xFFB8CDD3, anchor = GameKit.TR, align = 2)
        GameKit.text(s, "ObjectiveText", "OBJECTIVE: Learn the prison routine.", 0f, 1.0f, 0.30f, 0xFFF0E5CA, anchor = GameKit.TOP)
        GameKit.text(s, "PromptText", "", 0f, -1.12f, 0.30f, 0xFFD8DDCF, anchor = GameKit.BOTTOM)
        GameKit.text(s, "AlertText", "", 0f, 0.55f, 0.50f, 0xFFE76A5D).off()
        GameKit.button(s, "InventoryBtn", "INV", -2.1f, 0.72f, 1.18f, 0.55f, 0xFF35494B, "call:toggleInventory", anchor = GameKit.BR, textSize = 0.22f)
        GameKit.button(s, "MapBtn", "MAP", -0.87f, 0.72f, 1.05f, 0.55f, 0xFF35494B, "call:toggleMap", anchor = GameKit.BR, textSize = 0.22f)
        GameKit.button(s, "JournalBtn", "JNL", -0.30f, 1.34f, 1.05f, 0.55f, 0xFF35494B, "call:toggleJournal", anchor = GameKit.BR, textSize = 0.22f)
        GameKit.button(s, "PauseBtn", "II", -0.65f, -0.66f, 0.78f, 0.66f, 0xCC263436, "pause;show:PausePanel", anchor = GameKit.TR, textSize = 0.30f)

        val intro = GameKit.panel(s, "IntroPanel", 0f, 0f, 8.5f, 5.4f, 0xF00B1010, border = 0x889B7A42, corner = 0.18f)
        GameKit.text(s, "IntroTitle", "DAY ONE", 0f, 1.8f, 0.72f, 0xFFE2BF70, intro)
        GameKit.text(s, "IntroBody", "You are innocent. The prison is not.\n\nObserve routines, search quietly and build trust.\nFind your case evidence before choosing an escape route.\n\nMove with WASD / joystick. Press E / INTERACT near people and objects.", 0f, 0.22f, 0.31f, 0xFFD5D9D3, intro)
        GameKit.button(s, "StartDayBtn", "Enter the yard", 0f, -1.95f, 4.4f, 0.83f, 0xFFB07D31, "hide:IntroPanel", intro)

        fun modalPanel(name: String, title: String, w: Float = 8.8f, h: Float = 6.2f): Pair<com.sengine.engine.core.GameObject, com.sengine.engine.core.GameObject> {
            val panel = GameKit.panel(s, name, 0f, 0f, w, h, 0xF00B1010, border = 0x889B7A42, corner = 0.18f).off()
            val heading = GameKit.text(s, "${name}Title", title, 0f, h / 2 - 0.65f, 0.58f, 0xFFE2BF70, panel)
            return panel to heading
        }
        val inv = modalPanel("InventoryPanel", "INVENTORY").first
        GameKit.text(s, "InventoryText", "", 0f, 0.35f, 0.32f, 0xFFD5D9D3, inv)
        GameKit.button(s, "InventoryCloseBtn", "Close", 0f, -2.35f, 3.2f, 0.75f, 0xFF35494B, "call:closeModal", inv)
        val map = modalPanel("MapPanel", "PRISON MAP", 9.8f, 6.8f).first
        GameKit.text(s, "MapText", "BLOCK A      BLOCK B      BLOCK C\n\nKITCHEN      HOSPITAL     BATHROOM\n\nCHAPEL       WORKSHOP     LIBRARY\n\n          GUARD QUARTERS  [RESTRICTED]\n\nOuter wall: cameras • electric fence • gate south\nSewer hatch: east of Guard Quarters", 0f, 0.35f, 0.33f, 0xFFD5D9D3, map)
        GameKit.button(s, "MapCloseBtn", "Close map", 0f, -2.8f, 3.2f, 0.75f, 0xFF35494B, "call:closeModal", map)
        val journal = modalPanel("JournalPanel", "CASE JOURNAL", 9.4f, 6.8f).first
        GameKit.text(s, "JournalText", "", 0f, 0.28f, 0.30f, 0xFFD5D9D3, journal)
        GameKit.button(s, "JournalCloseBtn", "Close journal", 0f, -2.8f, 3.2f, 0.75f, 0xFF35494B, "call:closeModal", journal)
        val dialog = modalPanel("DialogPanel", "CONVERSATION", 8.4f, 5.6f).first
        GameKit.text(s, "DialogText", "", 0f, 0.54f, 0.32f, 0xFFD5D9D3, dialog)
        GameKit.button(s, "DialogHelpBtn", "Offer help", -1.45f, -1.85f, 2.7f, 0.76f, 0xFF527A5B, "call:dialogHelp", dialog, textSize = 0.28f)
        GameKit.button(s, "DialogCloseBtn", "Leave", 1.45f, -1.85f, 2.7f, 0.76f, 0xFF35494B, "call:closeModal", dialog, textSize = 0.28f)

        val pause = GameKit.panel(s, "PausePanel", 0f, 0f, 6.3f, 5.0f, 0xF00B1010, border = 0x889B7A42, corner = 0.18f).off()
        GameKit.text(s, "PauseTitle", "PAUSED", 0f, 1.65f, 0.68f, 0xFFE2BF70, pause)
        GameKit.button(s, "ResumeBtn", "Resume", 0f, 0.45f, 4.0f, 0.8f, 0xFFB07D31, "resume;hide:PausePanel", pause)
        GameKit.button(s, "RestartBtn", "Restart day", 0f, -0.62f, 4.0f, 0.8f, 0xFF35494B, "resume;reload", pause)
        GameKit.button(s, "QuitToMenuBtn", "Main menu", 0f, -1.68f, 4.0f, 0.8f, 0xFF35494B, "resume;scene:Menu", pause)

        val failure = GameKit.panel(s, "FailurePanel", 0f, 0f, 7.8f, 5.3f, 0xF00B1010, border = 0x99B94E45, corner = 0.18f).off()
        GameKit.text(s, "FailureTitle", "PLAN INTERRUPTED", 0f, 1.8f, 0.58f, 0xFFE76A5D, failure)
        GameKit.text(s, "FailureText", "", 0f, 0.55f, 0.33f, 0xFFD5D9D3, failure)
        GameKit.button(s, "FailureRetryBtn", "Try again", 0f, -1.15f, 3.8f, 0.82f, 0xFFB07D31, "reload", failure)
        GameKit.button(s, "FailureMenuBtn", "Main menu", 0f, -2.1f, 3.8f, 0.72f, 0xFF35494B, "scene:Menu", failure)
        val victory = GameKit.panel(s, "VictoryPanel", 0f, 0f, 8.5f, 5.8f, 0xF00B1010, border = 0x99D7B454, corner = 0.18f).off()
        GameKit.text(s, "VictoryTitle", "FREEDOM HAS A PRICE", 0f, 1.96f, 0.58f, 0xFFF0CD71, victory)
        GameKit.text(s, "VictoryText", "", 0f, 0.65f, 0.33f, 0xFFD5D9D3, victory)
        GameKit.button(s, "VictoryMenuBtn", "Return to main menu", 0f, -1.8f, 4.4f, 0.82f, 0xFFB07D31, "scene:Menu", victory)
        return s
    }

    private const val PRISON_PULSE_SHADER = """// Price of Freedom — subtle surveillance glow for evidence, cameras and player outline.
vec4 effect(vec4 color, vec2 uv) {
    float scan = 0.92 + 0.08 * sin((uv.y * 48.0) + uTime * 3.0);
    float edge = smoothstep(0.0, 0.18, uv.x) * smoothstep(0.0, 0.18, 1.0 - uv.x);
    return vec4(color.rgb * scan * (0.88 + edge * 0.12), color.a);
}
"""

    private const val PRISON_NOIR_SHADER = """// Price of Freedom — restrained cold grade and vignette post process.
vec4 effect(vec4 color, vec2 uv) {
    float d = distance(uv, vec2(0.5));
    float vignette = smoothstep(0.86, 0.24, d);
    vec3 cold = vec3(color.r * 0.90, color.g * 0.97, color.b * 1.04);
    return vec4(cold * mix(0.70, 1.0, vignette), color.a);
}
"""

    private const val CASE_NOTES = """PRICE OF FREEDOM — CASE NOTES

Daily schedule: wake 06:00, roll call 07:00, work 08:00, lunch 12:00, dinner 18:00, sleep 22:00.

People remember. Build relationships with six prisoners to unlock the helicopter option. Sergeant Tesfaye accepts a coffee bribe. Guard Dawit loses focus when he is alone.

Every escape needs your case evidence. The evidence is kept around the Library.

Routes:
• Main Gate — key, keycard, code, machine and evidence.
• Tunnel — spoon, shovel, hammer, rope, map and darkness.
• Wall — 3 rope, hook, gloves, shoes, an ally and evidence.
• Helicopter — five allies, tools, map and evidence.
• Sewer — flashlight, mask, rope, gloves, an ally and evidence.
"""
}
