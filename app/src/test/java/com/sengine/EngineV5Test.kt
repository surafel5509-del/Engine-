package com.sengine

import com.sengine.engine.Engine
import com.sengine.engine.core.Collider3D
import com.sengine.engine.core.Landscape
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SceneSerializer
import com.sengine.engine.core.ScriptComponent
import com.sengine.engine.core.TextRenderer
import com.sengine.engine.script.NativeScripts
import com.sengine.project.GameDoctor
import com.sengine.project.Project
import com.sengine.project.Templates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.math.abs

/** v5: native C++ layer (script VM + landscape) driven through JNI, exactly as on device. */
class EngineV5Test {

    private class Run(val engine: Engine, val errors: MutableList<String>, val logs: MutableList<String>)

    private fun nativeRequired() {
        // CI builds a host libsengine.so and exports SENGINE_NATIVE_LIB; there the native layer is mandatory.
        if (System.getenv("SENGINE_NATIVE_LIB") != null) assertTrue("native library failed to load", NativeScripts.available)
        assumeTrue("native library not available", NativeScripts.available)
    }

    private fun start(p: Project, scene: String): Run {
        val e = Engine(p, p.loadScene(scene))
        e.gameView.widthPx = 1600; e.gameView.heightPx = 900
        val errors = ArrayList<String>(); val logs = ArrayList<String>()
        e.listeners.add(object : Engine.Listener {
            override fun onLog(level: Int, message: String) {
                logs.add(message)
                if (level >= 2) errors.add(message)
                if (level >= 1) println("SIM v5 log[$level]: $message")
            }
        })
        e.play()
        return Run(e, errors, logs)
    }

    private fun Run.frames(n: Int) = repeat(n) { synchronized(engine.lock) { engine.tick(1f / 60f) } }

    private fun tempProject(name: String) = Project(File(Files.createTempDirectory("sengine5").toFile(), name)).also { it.saveMeta() }

    @Test
    fun nativeVersionAndCompileErrors() {
        nativeRequired()
        val v = NativeScripts.nVersion()
        println("SIM native version=$v")
        assertTrue(v.isNotBlank())
        assertNull(NativeScripts.check("Ok.cpp", Templates.newCppScript("Ok")))
        val err = NativeScripts.check("Bad.cpp", "class Bad : public Behaviour {\n public:\n void Update(float dt) override { int x = 1\n } };")
        println("SIM native compile error: $err")
        assertNotNull(err); assertTrue(err!!.contains("line"))
    }

    @Test
    fun cppBehaviourDrivesGameObjects() {
        nativeRequired()
        val p = tempProject("Cpp")
        p.writeAsset("Mover.cpp", """
            #include "SEngine.h"
            class Mover : public Behaviour {
            public:
                float speed = 1.0f;
                int ticks = 0;
                int hits = 0;
                void Start() override { Log("mover start", gameObject.name); }
                void Update(float dt) override {
                    ticks++;
                    gameObject.x += speed * dt;
                    Vec3 p = gameObject.position;
                    if (ticks == 30) printf("pos %.1f ticks %d\n", p.x, ticks);
                    if (ticks == 40) Scene::Find("Target").SendMessage("Hit", 5);
                }
            };
        """.trimIndent())
        p.writeAsset("Target.cpp", """
            class Target : public Behaviour {
            public:
                int hp = 20;
                int Hit(int dmg) { hp -= dmg; UE_LOG(LogTemp, Log, TEXT("hit! hp=%d"), hp); return hp; }
            };
        """.trimIndent())
        val s = Scene("Main")
        s.create("Box").add(ScriptComponent().also { it.script = "Mover.cpp"; it.params = "speed=6" })
        s.create("Target").add(ScriptComponent().also { it.script = "Target.cpp" })
        p.saveScene(s); p.startScene = "Main"; p.saveMeta()
        val r = start(p, "Main")
        r.frames(60)
        val box = r.engine.scene.find("Box")!!
        println("SIM cpp box x=${box.x} logs=${r.logs.takeLast(4)}")
        assertTrue(r.errors.toString(), r.errors.isEmpty())
        assertTrue("C++ Update moved the object (params applied): ${box.x}", abs(box.x - 6f) < 0.3f)
        assertTrue(r.logs.any { it.contains("mover start Box") })
        assertTrue(r.logs.any { it.startsWith("pos 3.") })
        assertTrue(r.logs.any { it.contains("hit! hp=15") })
        // Kotlin → C++ messaging returns the native method's value
        val back = r.engine.scripts.sendMessage(r.engine.scene.find("Target")!!, "Hit", 5.0)
        assertEquals(10L, (back as Number).toLong())
        // runtime errors are reported with file + line, not crashes
        p.writeAsset("Crash.cpp", "class Crash : public Behaviour {\npublic:\n void Update(float dt) override {\n std::vector<int> v;\n int x = v[2];\n }\n};")
        val s2 = Scene("Crash"); s2.create("C").add(ScriptComponent().also { it.script = "Crash.cpp" }); p.saveScene(s2)
        val r2 = start(p, "Crash"); r2.frames(3)
        println("SIM cpp runtime error: ${r2.errors.firstOrNull()}")
        assertTrue(r2.errors.any { it.contains("Crash.cpp") && it.contains("5") })
    }

    @Test
    fun landscapeGeneratesCollidesAndRaycasts() {
        val land = Landscape().apply { resolution = 65; size = 64f; height = 12f; seed = 3; erosion = 4000 }
        val h = land.ensure()
        assertEquals(65 * 65, h.size)
        val lo = h.min(); val hi = h.max()
        println("SIM landscape native=${land.generatedNatively} min=$lo max=$hi mesh=${land.mesh!!.size / 8} verts")
        if (System.getenv("SENGINE_NATIVE_LIB") != null) assertTrue(land.generatedNatively)
        assertTrue("terrain has relief", hi - lo > 4f && hi <= 12.5f)
        assertEquals(64 * 64 * 6 * 8, land.mesh!!.size)
        val v0 = land.version
        land.ensure(); assertEquals("no regeneration without changes", v0, land.version)
        land.seed = 4; land.ensure(); assertEquals(v0 + 1, land.version)
        land.seed = 3; land.ensure()

        val s = Scene("Land")
        s.create("Landscape").also { it.y = -2f; it.add(land) }
        val ball = s.create("Ball").also { it.x = 5f; it.y = 30f; it.z = -7f; it.add(Collider3D().apply { shape = 1 }); it.add(Rigidbody3D()) }
        val p = tempProject("Land"); p.saveScene(s); p.startScene = "Land"; p.saveMeta()
        val r = start(p, "Land")
        r.frames(240)
        val b = r.engine.scene.find("Ball")!!
        val ground = land.heightAt(b.x, b.z) - 2f
        println("SIM landscape ball=(${b.x}, ${b.y}, ${b.z}) ground=$ground grounded=${b.get<Rigidbody3D>()!!.grounded}")
        assertTrue("ball rests on the terrain", b.y > ground && b.y < ground + 1.2f)
        val hit = r.engine.physics3D.raycastHit(r.engine.scene, -10f, 40f, 10f, 0f, -1f, 0f, 100f, ignore = b)
        assertNotNull(hit)
        println("SIM landscape ray y=${hit!!.y} expected=${land.heightAt(-10f, 10f) - 2f} n=(${hit.nx},${hit.ny},${hit.nz})")
        assertEquals("Landscape", hit.go?.name)
        assertEquals(land.heightAt(-10f, 10f) - 2f, hit.y, 0.1f)
        assertTrue(hit.ny > 0.3f)
        // serializes like every other component
        val json = SceneSerializer.toJson(r.engine.scene).toString()
        assertTrue(json.contains("Landscape"))
        val back = SceneSerializer.fromJson(org.json.JSONObject(json))
        assertEquals(3, back.find("Landscape")!!.get<Landscape>()!!.seed)
    }

    @Test
    fun nativeWorldTemplatePlays() {
        nativeRequired()
        val t = Templates.all.first { it.name.startsWith("Native World") }
        val p = tempProject("NativeWorld")
        t.build(p); p.saveMeta()
        val issues = GameDoctor.check(p).filter { it.severity >= 2 && !it.title.startsWith("Missing texture") }
        assertTrue(issues.joinToString { it.title + " — " + it.detail }, issues.isEmpty())
        for (cpp in p.listAssets().filter { it.endsWith(".cpp") }) assertNull(cpp, NativeScripts.check(cpp, p.readAsset(cpp)!!))
        val r = start(p, p.startScene)
        r.frames(120)
        val scene = r.engine.scene
        val crystals = scene.objects.filter { it.name.startsWith("Crystal (") && it.isActiveInHierarchy() }
        val trees = scene.objects.count { it.name.startsWith("Tree (") && it.isActiveInHierarchy() }
        val player = scene.find("Player")!!
        val land = scene.find("Landscape")!!.get<Landscape>()!!
        val hud = { scene.find("HUD")!!.get<TextRenderer>()!!.text }
        println("SIM native world crystals=${crystals.size} trees=$trees player=(${player.x}, ${player.y}, ${player.z}) ground=${land.heightAt(player.x, player.z)} hud='${hud()}' errors=${r.errors}")
        assertTrue(r.errors.toString(), r.errors.isEmpty())
        assertEquals(10, crystals.size)
        assertEquals(45, trees)
        assertTrue(hud().startsWith("Crystals 0 / 10"))
        assertTrue("player stands on the landscape", player.y > land.heightAt(player.x, player.z) - 0.2f)
        // walk right with the joystick
        val x0 = player.x
        r.engine.input.joyX = 1f
        r.frames(60)
        r.engine.input.joyX = 0f
        println("SIM native world walked ${player.x - x0}")
        assertTrue("C++ controller moves the player", player.x - x0 > 3f)
        // collect crystals by teleporting onto them
        for ((i, c) in crystals.withIndex()) {
            player.x = c.x; player.y = c.y; player.z = c.z
            player.get<Rigidbody3D>()!!.let { it.vx = 0f; it.vy = 0f; it.vz = 0f }
            r.frames(3)
            if (i == 0) { println("SIM native world after 1: '${hud()}'"); assertTrue(hud().startsWith("Crystals 1 / 10")) }
        }
        r.frames(2)
        println("SIM native world end hud='${hud()}' errors=${r.errors}")
        assertTrue(hud().startsWith("All 10 crystals"))
        assertTrue(r.errors.toString(), r.errors.isEmpty())
    }
}
