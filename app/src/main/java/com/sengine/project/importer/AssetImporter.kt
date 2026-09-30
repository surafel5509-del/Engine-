package com.sengine.project.importer

import com.sengine.engine.core.AssetKind
import com.sengine.engine.model.SModel
import com.sengine.project.Project
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * v7 universal import system. Imports **any** file or whole folder into a project:
 *
 *  - 3D models (OBJ+MTL, STL, PLY, glTF, GLB, DAE, FBX-ASCII) → converted to editable `.smodel`
 *  - archives (ZIP, TAR, TAR.GZ, GZ, RAR5-stored) → extracted, folder structure preserved
 *  - images, sounds, shaders, scripts, songs, data — imported into their asset slots
 *  - everything else (.blend, fonts, video, anything) → stored as a project asset
 *
 * Files are streamed with a 256 KB buffer so huge multi-GB files import fine on a phone.
 */
object AssetImporter {

    class Progress { @Volatile var done = 0; @Volatile var total = 0; @Volatile var current = "" }

    class FileResult(val name: String, val ok: Boolean, val note: String)

    /** Formats listed in the import dialog help text. */
    val FORMAT_SUMMARY = listOf(
        "3D Models — OBJ (+MTL), STL, PLY, glTF / GLB, Collada (.dae), FBX ASCII → editable .smodel",
        "Archives — ZIP, TAR, TAR.GZ, GZ, RAR (store) → extracted with folder structure",
        "Images — PNG, JPG, WebP, BMP   •   Sounds — WAV, OGG, MP3, M4A, FLAC",
        "Anything else — .blend, .3ds, .7z, fonts, video, docs — saved as project assets",
        "Any size — files are streamed, there is no size limit (big files just take longer)",
    )

    /** Human-readable size. */
    fun humanSize(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "%.1f GB".format(bytes / 1073741824.0)
        bytes >= 1L shl 20 -> "%.1f MB".format(bytes / 1048576.0)
        bytes >= 1L shl 10 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    /** Streams [input] into the project under (possibly nested) [name]. Returns the stored asset path. */
    fun storeRaw(p: Project, name: String, input: InputStream, sizeHint: Long = -1): String {
        val safe = name.replace('\\', '/').split('/').filter { it.isNotBlank() && it != ".." && !it.contains(':') }.joinToString("/")
        val f = p.assetFile(safe)
        f.parentFile?.mkdirs()
        val buf = ByteArray(1 shl 18)
        var n = 0L
        f.outputStream().use { o ->
            while (true) {
                val r = input.read(buf)
                if (r < 0) break
                o.write(buf, 0, r); n += r
            }
        }
        return safe
    }

    /**
     * Imports one file into the project. [openSibling] resolves files next to the imported one
     * (used for MTL colours and glTF .bin buffers during folder imports).
     */
    fun importFile(
        p: Project,
        fileName: String,
        input: InputStream,
        sizeHint: Long = -1,
        folder: String = "",
        openSibling: (String) -> InputStream? = { null },
        progress: Progress? = null,
    ): FileResult {
        val base = fileName.substringAfterLast('/')
        val ext = base.substringAfterLast('.', "").lowercase()
        progress?.let { it.current = base; it.done++ }
        val head = try { val b = ByteArray(64); input.mark(64); val r = input.read(b); input.reset(); if (r <= 0) ByteArray(0) else b.copyOf(r) } catch (_: Exception) { ByteArray(0) }

        // ---------------------------------------------------------------- archives → extract
        if (Archives.isArchive(base)) {
            val bytes = input.readBytes()
            val target = folder.ifBlank { base.substringBeforeLast('.') }
            val dest = File(p.assetsDir, target)
            return try {
                val res = Archives.extract(base, bytes, dest)
                for (note in res.notes) p.writeAsset("import_$target.log", (p.readAsset("import_$target.log") ?: "") + note + "\n")
                FileResult(base, true, "Extracted ${res.files.size} file(s)" + res.notes.joinToString(" ") { " — $it" })
            } catch (e: Exception) {
                FileResult(base, false, "Archive failed: ${e.message}")
            }
        }

        // ---------------------------------------------------------------- 3D models → .smodel
        val fmt = ModelImporters.sniff(base, head)
        if (fmt != null) {
            return try {
                val bytes = input.readBytes()
                val res = ModelImporters.import(base, bytes) { sib ->
                    try { openSibling(sib)?.readBytes() } catch (_: Exception) { null }
                }
                val model = SModel()
                for (part in ModelImporters.toParts(res)) model.parts.add(part)
                val stem = base.substringBeforeLast('.')
                val out = p.uniqueAssetName("$folder$stem.smodel")
                val f = p.assetFile(out); f.parentFile?.mkdirs()
                f.writeText(model.toJson().toString())
                val notes = res.warnings.joinToString(" ")
                val sizeNote = sizeHint.let { if (it > 0) " (${humanSize(it)})" else "" }
                FileResult(base, true, "$stem.${res.format} → ${out.substringAfter('/')} — ${model.parts.size} part(s), ${res.triangles} tris$sizeNote" + notes.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty())
            } catch (e: Exception) {
                // keep the raw file anyway so nothing is ever lost
                val raw = File(p.assetsDir, p.uniqueAssetName("$folder$fileName"))
                raw.parentFile?.mkdirs()
                raw.writeBytes(bytes)
                FileResult(base, false, "Could not convert (${e.message}); saved as raw asset '${raw.relativeTo(p.assetsDir).path}'")
            }
        }

        // ---------------------------------------------------------------- everything else → raw asset
        val kind = AssetKind.of(base)
        val note = when (kind) {
            AssetKind.TEXTURE -> "Image imported"
            AssetKind.SOUND -> "Sound imported"
            AssetKind.SCRIPT, AssetKind.SHADER, AssetKind.ANIMATION, AssetKind.SONG -> "Imported"
            AssetKind.DATA -> "Data imported"
            null -> when (ext) {
                "flac", "aac", "opus", "wma" -> "Audio imported (play it with audio.play)"
                "mp4", "mov", "avi", "mkv", "webm" -> "Video saved as asset (not playable by the engine — use a texture/sprite instead)"
                "blend" -> "Blender file saved as asset — re-export as .glb or OBJ from Blender to edit it in the Model Editor"
                else -> "Imported as project asset"
            }
        }
        val stored = storeRaw(p, "$folder$fileName", input, sizeHint)
        return FileResult(base, true, "$note → $stored" + (sizeHint.takeIf { it > 0 }?.let { " (${humanSize(it)})" } ?: ""))
    }

    /** Imports a whole folder tree. [files] are relative path + opener pairs. */
    fun importFolder(p: Project, rootName: String, files: List<Pair<String, () -> InputStream>>, progress: Progress? = null): List<FileResult> {
        val results = ArrayList<FileResult>()
        progress?.total = files.size
        // sort so side-car files (.mtl/.bin) land before their models when possible
        val ordered = files.sortedWith(compareBy({ !(it.first.endsWith(".mtl") || it.first.endsWith(".bin")) }, { it.first }))
        for ((rel, open) in ordered) {
            try {
                val dir = rel.substringBeforeLast('/', "")
                val sib = rel.substringAfterLast('/')
                results.add(importFile(p, sib, open(), folder = "$rootName/${if (dir.isBlank()) "" else "$dir/"}",
                    openSibling = { name -> openSiblingIn(ordered, dir, name) }, progress = progress))
            } catch (e: Exception) {
                results.add(FileResult(rel, false, e.message ?: "failed"))
            }
        }
        return results
    }

    private fun openSiblingIn(files: List<Pair<String, () -> InputStream>>, dir: String, name: String): java.io.InputStream? {
        val short = name.substringAfterLast('/')
        for ((rel, open) in files) {
            val d = rel.substringBeforeLast('/', "")
            val n = rel.substringAfterLast('/')
            if (n == short && (d == dir || n.endsWith(".mtl"))) return try { open() } catch (_: Exception) { null }
        }
        return null
    }
}
