package com.sengine.project

import com.sengine.engine.core.AssetKind
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Professional asset system for a project: scans `assets/` into a typed, searchable, favouritable
 * database with per-asset metadata, imports multi-file selections and whole ZIP archives
 * (auto-building sprite sheets + .anim clips from numbered frame sequences) and exports asset
 * selections as ZIPs. Pure java.util.zip + org.json — fully unit-testable.
 */
class AssetHub(private val project: Project) {

    class Info(
        val file: String,      // path relative to assets/ ('/'-separated, may include subfolders)
        val kind: AssetKind?,
        val category: String,
        val bytes: Long,
        val folder: String,    // "" for root, otherwise the parent folder path
        var favorite: Boolean,
        var addedAt: Long,
        val tags: MutableList<String>,
    )

    class Summary {
        var added = 0
        val byKind = LinkedHashMap<String, Int>()
        var sheetsBuilt = 0
        var skipped = 0
        val names = ArrayList<String>()
        fun kind(k: String) = byKind.merge(k, 1, Int::plus)
        override fun toString() = "$added files" + (if (sheetsBuilt > 0) ", $sheetsBuilt sheet(s)" else "") + (if (skipped > 0) ", $skipped skipped" else "")
    }

    companion object {
        val CATEGORIES = listOf("All", "Favorites", "Sprites", "Sprite Sheets", "Textures", "Animations",
            "Sounds", "Music", "Scripts & Blueprints", "Shaders", "3D Models", "Prefabs", "Data", "In folders")

        private val SHEET_HINTS = listOf("sheet", "strip", "atlas")

        fun categoryFor(file: String, kind: AssetKind?): String {
            val lower = file.lowercase()
            return when (kind) {
                AssetKind.TEXTURE -> when {
                    SHEET_HINTS.any { it in lower } -> "Sprite Sheets"
                    else -> "Sprites"
                }
                AssetKind.ANIMATION -> "Animations"
                AssetKind.SOUND -> "Sounds"
                AssetKind.SONG -> "Music"
                AssetKind.SCRIPT -> if (lower.endsWith(".bp")) "Scripts & Blueprints" else "Scripts & Blueprints"
                AssetKind.SHADER -> "Shaders"
                AssetKind.MODEL -> "3D Models"
                AssetKind.PREFAB -> "Prefabs"
                else -> "Data"
            }
        }

        /** Groups numbered frame files: "run_1.png","run_2.png",… → ("run", sorted list). Groups need ≥ 3 frames. */
        fun detectFrameGroups(names: List<String>): Map<String, List<String>> {
            val rx = Regex("^(.*?)[_ \\-]?([0-9]+)$")
            val groups = LinkedHashMap<String, MutableList<Pair<Int, String>>>()
            for (n in names) {
                val stem = n.substringBeforeLast('.')
                val m = rx.matchEntire(stem) ?: continue
                val num = m.groupValues[2].toIntOrNull() ?: continue
                if (m.groupValues[1].isBlank()) continue
                groups.getOrPut(m.groupValues[1]) { mutableListOf() }.add(num to n)
            }
            val out = LinkedHashMap<String, List<String>>()
            for ((k, v) in groups) if (v.size >= 3) out[k] = v.sortedBy { it.first }.map { it.second }
            return out
        }

        /** Blocks zip-slip paths ("../x", "/etc/x", "C:\x") and returns a clean relative name. */
        fun sanitizeEntry(raw: String): String? {
            var n = raw.replace('\\', '/')
            while (n.startsWith("/")) n = n.removePrefix("/")
            if (n.contains("..") || n.contains(':') || n.isBlank()) return null
            val parts = n.split('/').filter { it.isNotEmpty() && it != "." }
            if (parts.isEmpty()) return null
            return parts.joinToString("/")
        }
    }

    private val metaFile = File(project.dir, "assets.index.json")

    // ------------------------------------------------------------------ meta (favorites/tags/added)

    private var favorites = HashMap<String, Boolean>()
    private var tags = HashMap<String, MutableList<String>>()
    private var addedAt = HashMap<String, Long>()

    init { loadMeta() }

    private fun loadMeta() {
        try {
            if (!metaFile.exists()) return
            val o = JSONObject(metaFile.readText())
            val f = o.optJSONObject("favorites")
            f?.let { val ks = it.keys(); while (ks.hasNext()) { val k = ks.next(); favorites[k] = it.getBoolean(k) } }
            val t = o.optJSONObject("tags")
            t?.let {
                val ks = it.keys()
                while (ks.hasNext()) {
                    val k = ks.next(); val arr = it.getJSONArray(k)
                    tags[k] = (0 until arr.length()).map { j -> arr.getString(j) }.toMutableList()
                }
            }
            val a = o.optJSONObject("added")
            a?.let { val ks = it.keys(); while (ks.hasNext()) { val k = ks.next(); addedAt[k] = it.getLong(k) } }
        } catch (_: Exception) {
        }
    }

    fun saveMeta() {
        val o = JSONObject()
        o.put("favorites", JSONObject(favorites))
        val t = JSONObject()
        for ((k, v) in tags) t.put(k, JSONArray(v))
        o.put("tags", t)
        o.put("added", JSONObject(addedAt))
        project.dir.mkdirs()
        metaFile.writeText(o.toString(1))
    }

    // ------------------------------------------------------------------ scanning & querying

    fun scan(): List<Info> {
        val out = ArrayList<Info>()
        val root = project.assetsDir
        fun walk(dir: File, folder: String) {
            for (f in dir.listFiles() ?: emptyArray()) {
                if (f.isDirectory) { walk(f, if (folder.isEmpty()) f.name else "$folder/${f.name}"); continue }
                if (!f.isFile) continue
                val rel = if (folder.isEmpty()) f.name else "$folder/${f.name}"
                if (rel == "assets.index.json") continue
                val kind = AssetKind.of(f.name)
                out += Info(rel, kind, categoryFor(rel, kind), f.length(), folder,
                    favorites[rel] ?: false, addedAt[rel] ?: 0L, tags[rel] ?: mutableListOf())
            }
        }
        walk(root, "")
        return out.sortedWith(compareBy({ CATEGORIES.indexOf(it.category).let { i -> if (i < 0) 99 else i } }, { it.file.lowercase() }))
    }

    fun query(all: List<Info>, category: String, search: String): List<Info> {
        val q = search.trim().lowercase()
        return all.filter { info ->
            val catOk = when (category) {
                "All" -> true
                "Favorites" -> info.favorite
                "In folders" -> info.folder.isNotEmpty()
                else -> info.category == category
            }
            val qOk = q.isEmpty() || info.file.lowercase().contains(q) || info.tags.any { it.lowercase().contains(q) }
            catOk && qOk
        }
    }

    fun toggleFavorite(file: String): Boolean {
        val v = !(favorites[file] ?: false)
        if (v) favorites[file] = true else favorites.remove(file)
        saveMeta()
        return v
    }

    fun addTag(file: String, tag: String) {
        tags.getOrPut(file) { mutableListOf() }.add(tag)
        saveMeta()
    }

    // ------------------------------------------------------------------ file operations

    fun delete(file: String): Boolean {
        val f = File(project.assetsDir, file)
        val ok = f.delete()
        favorites.remove(file); tags.remove(file); addedAt.remove(file)
        if (ok) saveMeta()
        return ok
    }

    fun rename(file: String, newName: String): String? {
        val src = File(project.assetsDir, file)
        if (!src.exists()) return null
        val clean = newName.replace(Regex("[^A-Za-z0-9_.\\-/ ]"), "_").trim()
        if (clean.isEmpty()) return null
        val dst = project.uniqueAssetName(clean)
        val f2 = File(project.assetsDir, dst)
        File(f2.parent).mkdirs()
        if (!src.renameTo(f2)) return null
        if (favorites.remove(file) == true) favorites[dst] = true
        tags[file]?.let { tags[dst] = it; tags.remove(file) }
        saveMeta()
        return dst
    }

    // ------------------------------------------------------------------ import

    /** Imports a multi-file selection (names paired with openers). Returns a summary. */
    fun importFiles(files: List<Pair<String, InputStream>>): Summary {
        val s = Summary()
        for ((rawName, stream) in files) {
            try {
                if (rawName.lowercase().endsWith(".zip")) {
                    val bytes = stream.use { it.readBytes() }
                    importZipBytes(bytes, s)
                    continue
                }
                val clean = sanitizeEntry(rawName) ?: continue
                val base = clean.substringAfterLast('/')
                if (AssetKind.of(base) == null) { s.skipped++; continue }
                val n = project.uniqueAssetName(base)
                project.assetsDir.mkdirs()
                project.assetFile(n).outputStream().use { stream.copyTo(it) }
                addedAt[n] = System.currentTimeMillis()
                s.added++; s.kind(AssetKind.of(n)!!.name); s.names.add(n)
            } catch (_: Exception) {
                s.skipped++
            } finally {
                try { stream.close() } catch (_: Exception) {}
            }
        }
        saveMeta()
        return s
    }

    /** Imports a ZIP archive from bytes. Preserves folder structure; sanitises entry names. */
    fun importZipBytes(bytes: ByteArray, s: Summary = Summary()): Summary {
        val zi = ZipInputStream(ByteArrayInputStream(bytes))
        val textures = ArrayList<String>()
        try {
            while (true) {
                val e = zi.nextEntry ?: break
                val name = sanitizeEntry(e.name)
                if (name == null || e.isDirectory) { if (!e.isDirectory) s.skipped++; continue }
                val data = try { zi.readBytes() } catch (_: Exception) { s.skipped++; continue }
                val base = name.substringAfterLast('/')
                val folder = name.substringBeforeLast('/', "")
                val kind = AssetKind.of(base)
                if (kind == null) { s.skipped++; continue }
                var rel = if (folder.isEmpty()) base else "$folder/$base"
                if (File(project.assetsDir, rel).exists()) {
                    rel = if (folder.isEmpty()) project.uniqueAssetName(base)
                    else "$folder/" + uniqueInFolder(folder, base)
                }
                val f = File(project.assetsDir, rel)
                f.parentFile?.mkdirs()
                f.writeBytes(data)
                addedAt[rel] = System.currentTimeMillis()
                s.added++; s.kind(kind.name); s.names.add(rel)
                if (kind == AssetKind.TEXTURE) textures.add(rel)
            }
        } catch (_: Exception) {
        } finally {
            try { zi.close() } catch (_: Exception) {}
        }
        // numbered frame sequences inside the zip become a sprite sheet + .anim automatically
        val groups = detectFrameGroups(textures)
        for ((stem, files) in groups) try {
            if (buildSheetFromFiles(stem, files)) s.sheetsBuilt++
        } catch (_: Exception) { }
        saveMeta()
        return s
    }

    private fun uniqueInFolder(folder: String, base: String): String {
        val dir = File(project.assetsDir, folder)
        if (!File(dir, base).exists()) return base
        val stem = base.substringBeforeLast('.'); val ext = base.substringAfterLast('.', "")
        var i = 1
        while (File(dir, "${stem}_$i.$ext").exists()) i++
        return "${stem}_$i.$ext"
    }

    /**
     * Builds "Group Sheet.png" + "Group.anim" from numbered PNG frames. Uses android decoding on
     * device; returns false (no crash) when frames cannot be decoded (e.g. headless tests).
     */
    fun buildSheetFromFiles(group: String, frames: List<String>): Boolean {
        val bmps = ArrayList<android.graphics.Bitmap>()
        for (f in frames) {
            val file = File(project.assetsDir, f)
            if (!file.exists()) return false
            val b = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return false
            bmps.add(b)
        }
        if (bmps.size < 3) return false
        val cell = bmps.maxOf { maxOf(it.width, it.height) }.coerceAtLeast(4)
        val cols = kotlin.math.ceil(kotlin.math.sqrt(bmps.size.toDouble())).toInt().coerceIn(1, 8)
        val rows = kotlin.math.ceil(bmps.size / cols.toDouble()).toInt().coerceAtLeast(1)
        val sheet = android.graphics.Bitmap.createBitmap(cols * cell, rows * cell, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(sheet)
        for ((i, b) in bmps.withIndex()) c.drawBitmap(b, (i % cols) * cell.toFloat(), (i / cols) * cell.toFloat(), null)
        project.assetsDir.mkdirs()
        val png = File(project.assetsDir, "${group} Sheet.png")
        png.outputStream().use { sheet.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val clip = com.sengine.engine.anim.AnimationClip("${group} Sheet.png", cols, rows,
            (0 until bmps.size).toMutableList(), 8f, true)
        project.writeAsset("${group}.anim", clip.toJson().toString(2))
        addedAt["${group} Sheet.png"] = System.currentTimeMillis()
        addedAt["${group}.anim"] = System.currentTimeMillis()
        return true
    }

    // ------------------------------------------------------------------ export

    /** Writes the selected assets (flat, by base name) into a zip on [out]. Returns entry count. */
    fun exportZip(files: List<String>, out: OutputStream): Int {
        val zos = ZipOutputStream(out)
        var n = 0
        val used = HashSet<String>()
        for (rel in files) {
            val f = File(project.assetsDir, rel)
            if (!f.isFile) continue
            var entry = rel.substringAfterLast('/')
            while (!used.add(entry)) entry = entry.substringBeforeLast('.') + "_." + entry.substringAfterLast('.', "")
            zos.putNextEntry(ZipEntry(entry))
            f.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
            n++
        }
        zos.finish(); zos.flush()
        zos.close()
        return n
    }

    /** Convenience: zip bytes for a selection. */
    fun exportZipBytes(files: List<String>): ByteArray {
        val b = ByteArrayOutputStream()
        exportZip(files, b)
        return b.toByteArray()
    }
}
