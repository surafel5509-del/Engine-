package com.sengine.engine.texture

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/** Minimal pure-JVM PNG writer/reader (RGBA 8-bit) so textures can be generated off the UI thread and in unit tests. */
object PngEncoder {
    fun encode(w: Int, h: Int, argb: IntArray): ByteArray {
        val raw = ByteArray((w * 4 + 1) * h)
        var k = 0
        for (y in 0 until h) {
            raw[k++] = 0
            for (x in 0 until w) {
                val c = argb[y * w + x]
                raw[k++] = (c shr 16).toByte(); raw[k++] = (c shr 8).toByte(); raw[k++] = c.toByte(); raw[k++] = (c ushr 24).toByte()
            }
        }
        val def = Deflater(6); def.setInput(raw); def.finish()
        val z = ByteArrayOutputStream(); val buf = ByteArray(65536)
        while (!def.finished()) { val n = def.deflate(buf); z.write(buf, 0, n) }
        def.end()
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val ihdr = ByteArrayOutputStream().also { DataOutputStream(it).apply { writeInt(w); writeInt(h); writeByte(8); writeByte(6); writeByte(0); writeByte(0); writeByte(0) } }
        chunk(out, "IHDR", ihdr.toByteArray())
        chunk(out, "IDAT", z.toByteArray())
        chunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun chunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val d = DataOutputStream(out)
        d.writeInt(data.size)
        val t = type.toByteArray(Charsets.US_ASCII)
        d.write(t); d.write(data)
        val crc = CRC32(); crc.update(t); crc.update(data)
        d.writeInt(crc.value.toInt())
    }

    /** Decodes PNGs written by [encode] (8-bit RGBA or RGB, non-interlaced). Returns null for other formats. */
    fun decode(bytes: ByteArray): Triple<Int, Int, IntArray>? = decodeOrNull(bytes)

    private fun decodeOrNull(bytes: ByteArray): Triple<Int, Int, IntArray>? { try {
        val inp = java.io.DataInputStream(bytes.inputStream())
        inp.skipBytes(8)
        var w = 0; var h = 0; var ct = 6
        val idat = ByteArrayOutputStream()
        while (true) {
            val len = inp.readInt()
            val t = ByteArray(4); inp.readFully(t)
            val data = ByteArray(len); inp.readFully(data); inp.readInt()
            when (String(t, Charsets.US_ASCII)) {
                "IHDR" -> { val d = java.io.DataInputStream(data.inputStream()); w = d.readInt(); h = d.readInt(); val bd = d.readByte().toInt(); ct = d.readByte().toInt(); if (bd != 8 || (ct != 6 && ct != 2)) return null }
                "IDAT" -> idat.write(data)
                "IEND" -> break
            }
        }
        val bpp = if (ct == 6) 4 else 3
        val inf = java.util.zip.Inflater(); inf.setInput(idat.toByteArray())
        val raw = ByteArray((w * bpp + 1) * h); var off = 0
        while (off < raw.size && !inf.finished()) { val n = inf.inflate(raw, off, raw.size - off); if (n == 0 && inf.needsInput()) break; off += n }
        inf.end()
        val stride = w * bpp
        val cur = ByteArray(stride); val prev = ByteArray(stride)
        val px = IntArray(w * h)
        for (y in 0 until h) {
            val f = raw[y * (stride + 1)].toInt()
            System.arraycopy(raw, y * (stride + 1) + 1, cur, 0, stride)
            for (i in 0 until stride) {
                val a = if (i >= bpp) cur[i - bpp].toInt() and 0xFF else 0
                val b = prev[i].toInt() and 0xFF
                val c = if (i >= bpp) prev[i - bpp].toInt() and 0xFF else 0
                val x = cur[i].toInt() and 0xFF
                val v = when (f) {
                    1 -> x + a; 2 -> x + b; 3 -> x + (a + b) / 2
                    4 -> { val p = a + b - c; val pa = Math.abs(p - a); val pb = Math.abs(p - b); val pc = Math.abs(p - c); x + if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c }
                    else -> x
                }
                cur[i] = v.toByte()
            }
            for (x in 0 until w) {
                val o = x * bpp
                val r = cur[o].toInt() and 0xFF; val g = cur[o + 1].toInt() and 0xFF; val bl = cur[o + 2].toInt() and 0xFF
                val al = if (bpp == 4) cur[o + 3].toInt() and 0xFF else 255
                px[y * w + x] = (al shl 24) or (r shl 16) or (g shl 8) or bl
            }
            System.arraycopy(cur, 0, prev, 0, stride)
        }
        return Triple(w, h, px)
    } catch (_: Exception) { return null } }
}
