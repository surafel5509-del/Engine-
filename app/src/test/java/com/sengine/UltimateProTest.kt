package com.sengine

import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SpriteRenderer
import com.sengine.project.AssetForge
import com.sengine.project.AssetHub
import com.sengine.project.AssetLibrary
import com.sengine.project.PrefabIO
import com.sengine.project.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Ultimate Edition Pro (v8) tests: the asset forge catalogue, the AssetHub zip import/export
 * database, frame-sequence detection and Unity-style prefabs. Everything runs headless — the
 * forge produces real PNG bytes through the engine's own pure-Kotlin PngEncoder.
 */
class UltimateProTest {

    private fun newProject(name: String = "UltiTest"): Project {
        val dir = Files.createTempDirectory("sengine-ulti").toFile()
        val p = Project(File(dir, name))
        p.saveMeta()
        return p
    }

    // ---------------------------------------------------------------- catalogue

    @Test
    fun catalogueExceeds200UniqueAssets() {
        val items = AssetLibrary.items
        assertTrue("catalogue should exceed 200 items, was ${items.size}", items.size >= 200)
        val titles = items.map { it.title }
        assertEquals("titles must be unique", titles.size, titles.distinct().size)
        // pack items re-list their members' files by design, so uniqueness is checked leaf-to-leaf
        val files = ArrayList<String>()
        for (i in items) {
            assertTrue("item ${i.title} has no files", i.files.isNotEmpty())
            if (i.category != "Packs") files.addAll(i.files)
        }
        assertEquals("asset file names must be unique across the store", files.size, files.distinct().size)
        val packs = items.filter { it.category == "Packs" }
        assertTrue("expected themed packs, was ${packs.size}", packs.size >= 8)
        for (p in packs) assertTrue("pack ${p.title} resolved to nothing", p.files.isNotEmpty())
    }

    @Test
    fun forgeInstallsEveryItemHeadlessly() {
        val p = newProject("Forge")
        val items = AssetForge.items()
        assertTrue("forge should add 130+ items, was ${items.size}", items.size >= 130)
        var pngs = 0
        for (item in items) {
            item.install(p)
            for (f in item.files) {
                val file = p.assetFile(f)
                assertTrue("missing installed file $f for ${item.title}", file.exists() && file.length() > 0)
                if (f.endsWith(".png")) {
                    pngs++
                    val head = file.inputStream().use { it.readNBytes(8) }
                    assertEquals("bad PNG magic in $f", 0x89, head[0].toInt() and 0xFF)
                    assertEquals(0x50L, head[1].toLong() and 0xFF)
                }
            }
        }
        assertTrue("expected 100+ generated PNGs, was $pngs", pngs >= 100)
    }

    @Test
    fun animSpecsProduceValidClipsAndFrames() {
        val specs = AssetForge.animSpecs()
        assertTrue("expected 25+ animated sheets, was ${specs.size}", specs.size >= 25)
        for (spec in specs) {
            assertTrue("${spec.title} needs ≥3 frames", spec.frameCount >= 3)
            assertTrue("${spec.title} fps must be positive", spec.fps > 0f)
            assertTrue("${spec.title} grid too small", spec.columns * spec.rows >= spec.frameCount)
            val clip = spec.let { com.sengine.engine.anim.AnimationClip.fromJson(it.let { s -> com.sengine.engine.anim.AnimationClip(s.texFile, s.columns, s.rows, (0 until s.frameCount).toMutableList(), s.fps, s.loop) }.toJson()) }
            assertEquals(spec.frameCount, clip.frames.size)
            assertEquals(spec.texFile, clip.texture)
            val frames = spec.frames()
            assertEquals("${spec.title} generator frame count", spec.frameCount, frames.size)
            for ((i, f) in frames.withIndex()) {
                assertTrue("${spec.title} frame $i is empty", f.opaque() > 0)
            }
        }
    }

    @Test
    fun forgeSoundsAreValidWavs() {
        val p = newProject("Sfx")
        var wavs = 0
        for (item in AssetForge.items().filter { it.sound != null && it.category == "Sounds" }) {
            val bytes = item.sound!!.invoke()
            assertTrue("${item.title} too short", bytes.size > 2000)
            assertEquals("${item.title} not RIFF", "RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
            assertEquals("${item.title} not WAVE", "WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
            item.install(p)
            assertTrue(p.assetFile(item.files[0]).exists())
            wavs++
        }
        assertTrue("expected 20+ new sounds, was $wavs", wavs >= 20)
    }

    @Test
    fun forgeMusicComposes() {
        val p = newProject("Music")
        val songs = AssetForge.items().filter { it.category == "Music" }
        assertTrue("expected 6+ new songs, was ${songs.size}", songs.size >= 6)
        for (item in songs) {
            item.install(p)
            val text = p.readAsset(item.files[0])
            assertNotNull("song json missing for ${item.title}", text)
            val o = org.json.JSONObject(text!!)
            assertEquals("song", o.optString("format"))
        }
    }

    // ---------------------------------------------------------------- AssetHub / zip

    private fun zipBytesOf(entries: List<Pair<String, ByteArray>>): ByteArray {
        val bos = ByteArrayOutputStream()
        val zos = ZipOutputStream(bos)
        for ((name, data) in entries) {
            zos.putNextEntry(ZipEntry(name))
            zos.write(data)
            zos.closeEntry()
        }
        zos.finish(); zos.close()
        return bos.toByteArray()
    }

    @Test
    fun zipImportSanitizesAndClassifies() {
        val p = newProject("Zip")
        val hub = AssetHub(p)
        val zip = zipBytesOf(listOf(
            "hero.js" to "function update() {}".toByteArray(),
            "sprites/readme.txt" to "docs".toByteArray(),
            "deep/nested/level.json" to "{}".toByteArray(),
            "../evil.txt" to "bad".toByteArray(),
            "sound.ogg" to byteArrayOf(0x4F, 0x67, 0x67, 0x53),
            "unknown.xyz" to "??".toByteArray(),
        ))
        val s = hub.importZipBytes(zip)
        assertEquals(4, s.added)
        assertEquals(2, s.skipped)
        assertEquals(0, s.sheetsBuilt)
        assertTrue(p.assetFile("hero.js").exists())
        assertTrue(p.assetFile("sprites/readme.txt").exists())
        assertTrue(p.assetFile("deep/nested/level.json").exists())
        assertTrue(p.assetFile("sound.ogg").exists())
        assertFalse("zip-slip entry must not escape", File(p.assetsDir.parentFile, "evil.txt").exists())
        val all = hub.scan()
        assertTrue(all.any { it.file == "hero.js" && it.category.contains("Scripts") })
        assertTrue(all.any { it.file == "sound.ogg" && it.category == "Sounds" })
        assertTrue(all.any { it.file == "sprites/readme.txt" && it.folder == "sprites" })
    }

    @Test
    fun zipExportImportRoundTrip() {
        val p1 = newProject("A")
        val hub1 = AssetHub(p1)
        val payload = "pixel-data-${System.nanoTime()}".toByteArray()
        hub1.importFiles(listOf(
            "one.js" to ByteArrayInputStream("let a = 1;".toByteArray()),
            "two.json" to ByteArrayInputStream(payload),
        ))
        val bytes = hub1.exportZipBytes(listOf("one.js", "two.json"))
        assertTrue(bytes.size > 100)

        val p2 = newProject("B")
        val hub2 = AssetHub(p2)
        val s = hub2.importZipBytes(bytes)
        assertEquals(2, s.added)
        assertEquals(String(payload), String(p2.assetFile("two.json").readBytes()))
        assertEquals("let a = 1;", p2.assetFile("one.js").readText())
    }

    @Test
    fun importFilesDedupesNames() {
        val p = newProject("Dedupe")
        val hub = AssetHub(p)
        hub.importFiles(listOf("note.txt" to ByteArrayInputStream("1".toByteArray())))
        val s = hub.importFiles(listOf("note.txt" to ByteArrayInputStream("2".toByteArray())))
        assertEquals(1, s.added)
        assertTrue(p.assetFile("note_1.txt").exists())
        assertEquals("2", p.assetFile("note_1.txt").readText())
    }

    @Test
    fun frameGroupsDetectedAndSorted() {
        val groups = AssetHub.detectFrameGroups(
            listOf("run_1.png", "run_02.png", "run_003.png", "walk1.png", "walk2.png", "hero.png", "coin-1.png", "coin-2.png", "coin-3.png"))
        assertEquals(2, groups.size)
        assertEquals(listOf("run_1.png", "run_02.png", "run_003.png"), groups["run"])
        assertEquals(listOf("coin-1.png", "coin-2.png", "coin-3.png"), groups["coin"])
    }

    @Test
    fun sanitizeBlocksTraversal() {
        assertNull(AssetHub.sanitizeEntry("../evil.txt"))
        assertNull(AssetHub.sanitizeEntry("..\\evil.txt"))
        assertNull(AssetHub.sanitizeEntry("a/../../evil"))
        assertNull(AssetHub.sanitizeEntry("C:\\temp\\x.png"))
        assertNull(AssetHub.sanitizeEntry("   "))
        assertEquals("abs/path/x.png", AssetHub.sanitizeEntry("/abs/path/x.png"))
        assertEquals("a/b/c.png", AssetHub.sanitizeEntry("a/b/c.png"))
    }

    @Test
    fun favoritesAndMetaPersist() {
        val p = newProject("Fav")
        p.writeAsset("a.js", "1")
        p.writeAsset("b.js", "2")
        val hub = AssetHub(p)
        assertTrue(hub.toggleFavorite("a.js"))
        assertTrue(hub.toggleFavorite("b.js"))
        assertFalse(hub.toggleFavorite("b.js")) // off again
        assertTrue(hub.toggleFavorite("b.js")) // toggled back on
        val hub2 = AssetHub(p) // fresh instance reads persisted meta
        val a = hub2.scan().first { it.file == "a.js" }
        val b = hub2.scan().first { it.file == "b.js" }
        assertTrue(a.favorite)
        assertTrue(b.favorite)
        val favs = hub2.query(hub2.scan(), "Favorites", "")
        assertEquals(2, favs.size)
    }

    // ---------------------------------------------------------------- prefabs

    @Test
    fun prefabCaptureInstantiateRoundTrip() {
        val scene = Scene("PrefabScene")
        val root = scene.create("Hero", null)
        root.x = 5f; root.y = 7f
        root.add(SpriteRenderer().also { it.texture = "Hero.png"; it.color = 0xFF00FF00.toInt() })
        val child = scene.create("Sword", root)
        child.x = 1.5f; child.y = -0.5f
        child.add(SpriteRenderer().also { it.texture = "Sword.png" })

        val json = PrefabIO.capture(scene, root)
        assertEquals("prefab", json.optString("format"))
        val project = newProject("Prefabs")
        val file = PrefabIO.save(project, "Hero", json)
        assertTrue(file.endsWith(".prefab"))
        val loaded = PrefabIO.load(project, file)
        assertNotNull(loaded)

        val before = scene.objects.size
        val newRoot = PrefabIO.instantiate(scene, loaded!!, 100f, 200f, 0f)
        assertNotNull(newRoot)
        assertTrue(newRoot!!.id != root.id)
        assertEquals("Hero (1)", newRoot.name)
        assertEquals(100f, newRoot.x, 0.001f)
        assertEquals(200f, newRoot.y, 0.001f)
        assertEquals(before + 2, scene.objects.size)
        val newChild = scene.childrenOf(newRoot).single()
        assertEquals("Sword (1)", newChild.name)
        // captured transforms are parent-local: the child keeps its local offset under the moved root
        assertEquals(1.5f, newChild.x, 0.001f)
        assertEquals(-0.5f, newChild.y, 0.001f)
        assertEquals(newRoot.id, newChild.parent!!.id)
        assertEquals("Hero.png", newRoot.get<SpriteRenderer>()!!.texture)
        assertEquals("Sword.png", newChild.get<SpriteRenderer>()!!.texture)
        assertTrue(PrefabIO.listPrefabs(project).isNotEmpty())
    }

    // ---------------------------------------------------------------- GameObject get<T> sanity for assign flow

    @Test
    fun catalogCountsPerCategory() {
        val items = AssetLibrary.items
        val sprites = items.count { it.category == "Sprites" }
        val sheets = items.count { it.category == "Sprite Sheets" }
        val sounds = items.count { it.category == "Sounds" }
        assertTrue("sprites: $sprites", sprites >= 150)
        assertTrue("sheets: $sheets", sheets >= 30)
        assertTrue("sounds: $sounds", sounds >= 40)
    }
}
