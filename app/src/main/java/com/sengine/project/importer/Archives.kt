package com.sengine.project.importer

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * v7 archive support for the import system. Extracts ZIP (any size, streamed), TAR, .tar.gz / .gz
 * and RAR5 archives with stored entries; other compressions report a clear, actionable message.
 */
object Archives {

    class Entry(val path: String, val size: Long)
    class ExtractResult(val files: List<String>, val notes: List<String>)

    fun isArchive(name: String): Boolean {
        val e = name.substringAfterLast('.', "").lowercase()
        return e in listOf("zip", "tar", "gz", "tgz", "rar", "7z")
    }

    /** Kind label for the UI. */
    fun kindOf(name: String, head: ByteArray): String {
        val e = name.substringAfterLast('.', "").lowercase()
        if (head.size >= 7 && head[0] == 'R'.code.toByte() && head[1] == 'a'.code.toByte() && head[2] == 'r'.code.toByte() && head[3] == '!'.code.toByte())
            return if (head.size >= 8 && head[6].toInt() == 1 && head[7].toInt() == 0) "rar5" else "rar4"
        if (head.size >= 6 && head[0] == 0x37.toByte() && head[1] == 0x7A.toByte() && head[2] == 0xBC.toByte() && head[3] == 0xAF.toByte()) return "7z"
        return when (e) { "zip" -> "zip"; "tar" -> "tar"; "gz", "tgz" -> "gz"; "rar" -> "rar"; "7z" -> "7z"; else -> "" }
    }

    /**
     * Extracts [bytes] into [dest] (a directory that will be created). Paths are sanitised
     * against zip-slip. Unknown / unsupported members are skipped with a note.
     */
    fun extract(name: String, bytes: ByteArray, dest: File, onEntry: (Entry) -> Unit = {}): ExtractResult {
        val head = bytes.copyOfRange(0, minOf(bytes.size, 16))
        dest.mkdirs()
        val files = ArrayList<String>(); val notes = ArrayList<String>()
        when (kindOf(name, head)) {
            "zip" -> extractZip(bytes, dest, files, notes, onEntry)
            "tar" -> extractTar(ByteArrayInputStream(bytes), dest, files, notes, onEntry)
            "gz" -> {
                val inner = name.removeSuffix(".gz").removeSuffix(".tgz").removeSuffix(".GZ")
                if (inner.endsWith(".tar") || name.endsWith(".tgz")) extractTar(GZIPInputStream(ByteArrayInputStream(bytes)), dest, files, notes, onEntry)
                else {
                    val out = File(dest, safePath(name.removeSuffix(".gz").ifBlank { "data.bin" }))
                    out.parentFile?.mkdirs()
                    GZIPInputStream(ByteArrayInputStream(bytes)).use { i -> out.outputStream().use { i.copyTo(it) } }
                    files.add(out.relativeTo(dest).path)
                }
            }
            "rar5" -> extractRar5(bytes, dest, files, notes, onEntry)
            "rar4" -> notes.add("Legacy RAR (v4) archives can't be extracted on-device. Re-pack as ZIP, or re-save with WinRAR using 'RAR5' + 'Store' — then import again.")
            "7z" -> notes.add("7z archives can't be extracted on-device (LZMA). Re-pack the folder as ZIP and import that — everything else works the same.")
            else -> notes.add("'$name' is not a recognised archive — imported as a single file.")
        }
        return ExtractResult(files, notes)
    }

    private fun safePath(p: String): String {
        val parts = p.replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." && it != ".." && !it.contains(':') }
        return parts.joinToString("/").ifBlank { "file" }
    }

    private fun writeTo(dest: File, path: String, input: InputStream, size: Long, files: ArrayList<String>, onEntry: (Entry) -> Unit) {
        val safe = safePath(path)
        val out = File(dest, safe)
        if (safe.endsWith("/")) { out.mkdirs(); return }
        out.parentFile?.mkdirs()
        var n = 0L
        out.outputStream().use { o -> n = copyLimited(input, o, size) }
        if (n > 0 || size == 0L) { files.add(safe); onEntry(Entry(safe, n)) }
    }

    /** Copies at most [size] bytes (guards against corrupt headers producing huge files). */
    private fun copyLimited(input: InputStream, out: OutputStream, size: Long): Long {
        val buf = ByteArray(1 shl 18)
        var total = 0L
        while (total < size) {
            val want = minOf(buf.size.toLong(), size - total).toInt()
            val r = input.read(buf, 0, want)
            if (r < 0) break
            out.write(buf, 0, r); total += r
        }
        return total
    }

    private fun extractZip(bytes: ByteArray, dest: File, files: ArrayList<String>, notes: ArrayList<String>, onEntry: (Entry) -> Unit) {
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) { File(dest, safePath(e.name)).mkdirs(); continue }
                val size = if (e.size >= 0) e.size else Long.MAX_VALUE
                writeTo(dest, e.name, z, size, files, onEntry)
                z.closeEntry()
            }
        }
    }

    private fun extractTar(input: InputStream, dest: File, files: ArrayList<String>, notes: ArrayList<String>, onEntry: (Entry) -> Unit) {
        val head = ByteArray(512)
        while (true) {
            if (readFully(input, head) < 512) break
            if (head[0].toInt() == 0) break
            val name = String(head, 0, 100, StandardCharsets.US_ASCII).trimEnd('\u0000')
            var size = 0L
            for (c in String(head, 124, 12, StandardCharsets.US_ASCII).trim('\u0000', ' ')) size = size * 8 + (c - '0')
            val type = head[156].toInt()
            if (type == 'L'.code) { // GNU long name
                val longBuf = ByteArray(((size + 511) / 512 * 512).toInt().coerceAtMost(1 shl 20))
                readFully(input, longBuf)
                val longName = String(longBuf, 0, size.toInt().coerceAtMost(longBuf.size), StandardCharsets.UTF_8).trimEnd('\u0000')
                if (readFully(input, head) < 512) break
                var size2 = 0L
                for (c in String(head, 124, 12, StandardCharsets.US_ASCII).trim('\u0000', ' ')) size2 = size2 * 8 + (c - '0')
                writeTo(dest, longName, input, size2, files, onEntry)
                skip(input, (size2 + 511) / 512 * 512 - size2)
                continue
            }
            if (name.isBlank()) { skip(input, (size + 511) / 512 * 512); continue }
            if (type == '5'.code || name.endsWith("/")) { File(dest, safePath(name)).mkdirs(); skip(input, (size + 511) / 512 * 512); continue }
            if (type != '0'.code && type != 0) { notes.add("Skipped special TAR entry '$name'"); skip(input, (size + 511) / 512 * 512); continue }
            writeTo(dest, name, input, size, files, onEntry)
            skip(input, (size + 511) / 512 * 512 - size)
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val r = input.read(buf, off, buf.size - off)
            if (r < 0) break
            off += r
        }
        return off
    }

    private fun skip(input: InputStream, n: Long) { var left = n; val buf = ByteArray(1 shl 16); while (left > 0) { val r = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt()); if (r < 0) break; left -= r } }

    /**
     * RAR5 reader: lists and extracts STORED (uncompressed) entries — the common case for
     * asset packs. Compressed entries are skipped with an actionable note.
     */
    private fun extractRar5(bytes: ByteArray, dest: File, files: ArrayList<String>, notes: ArrayList<String>, onEntry: (Entry) -> Unit) {
        val sig = String(bytes, 0, 7, StandardCharsets.US_ASCII)
        require(sig.startsWith("Rar!")) { "Not a RAR file" }
        val isRar5 = bytes.size >= 8 && bytes[7].toInt() == 0
        if (!isRar5) { notes.add("Legacy RAR (v4) archives can't be extracted on-device. Re-pack as ZIP and import again."); return }
        val cur = intArrayOf(8) // read cursor
        var compressed = 0; var stored = 0
        fun need(n: Int): Boolean = cur[0] + n <= bytes.size
        fun u8(): Int { val b = bytes[cur[0]].toInt() and 0xFF; cur[0] += 1; return b }
        fun vint(): Long { var value = 0L; var shift = 0; while (need(1)) { val b = u8(); value = value or ((b and 0x7F).toLong() shl shift); if (b and 0x80 == 0) break; shift += 7; if (shift > 56) break }; return value }
        fun skip(n: Int) { cur[0] += n }
        fun i32(): Int { val v = (bytes[cur[0]].toInt() and 0xFF) or ((bytes[cur[0] + 1].toInt() and 0xFF) shl 8) or ((bytes[cur[0] + 2].toInt() and 0xFF) shl 16) or ((bytes[cur[0] + 3].toInt() and 0xFF) shl 24); cur[0] += 4; return v }

        while (need(4)) {
            val blockStart = cur[0]
            i32() // header CRC32 (not verified)
            if (!need(1)) break
            val headerSize = vint().toInt()
            if (headerSize <= 0 || blockStart + 4 + headerSize > bytes.size) break
            val typePos = cur[0]
            val type = vint().toInt()
            val flags = vint().toInt()
            val extraSize = if (flags and 0x0001 != 0) vint().toInt() else 0
            val dataSize = if (flags and 0x0002 != 0) vint().toInt() else 0
            val bodyStart = typePos + headerSize
            val next = bodyStart + dataSize
            if (next > bytes.size) break
            if (type == 3 && need(1)) { // file header
                val fileFlags = vint().toInt()
                val unpacked = vint().toInt()
                vint() // attributes
                if (fileFlags and 0x0002 != 0) skip(4) // mtime
                if (fileFlags and 0x0004 != 0) skip(4) // data crc
                val compInfo = vint().toInt()
                val method = (compInfo shr 7) and 0x07
                val nameSize = vint().toInt()
                if (need(nameSize)) {
                    val name = String(bytes, cur[0], nameSize, StandardCharsets.UTF_8); skip(nameSize)
                    if (fileFlags and 0x0001 != 0) { // directory
                        File(dest, name.replace('\\', '/')).mkdirs()
                    } else if (method == 0) { // stored
                        stored++
                        if (bodyStart + unpacked <= bytes.size) {
                            val out = File(dest, name.replace('\\', '/').split('/').filter { it != ".." }.joinToString("/"))
                            out.parentFile?.mkdirs()
                            File(out.parentFile, out.name).writeBytes(bytes.copyOfRange(bodyStart, bodyStart + unpacked))
                            files.add(out.relativeTo(dest).path)
                            onEntry(Entry(out.relativeTo(dest).path, unpacked.toLong()))
                        }
                    } else compressed++
                }
            }
            cur[0] = next
        }
        if (stored == 0 && compressed > 0) notes.add("This RAR uses compression, which can't be unpacked on-device. Re-pack as ZIP (or create the RAR with 'Store' instead of compressing) and import again — everything else in the import system handles it.")
        else if (compressed > 0) notes.add("$compressed compressed file(s) were skipped — re-pack those as ZIP. $stored file(s) extracted.")
        if (stored == 0 && compressed == 0) notes.add("No files found in the RAR archive.")
    }
}
