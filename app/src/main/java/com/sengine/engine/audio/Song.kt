package com.sengine.engine.audio

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * S Engine song (.song): a step-sequenced multi-track tune rendered by a built-in synthesizer.
 * Melodic notes are MIDI pitches; the Drum Kit instrument maps pitch rows to drum sounds.
 */
class Note(var step: Int, var pitch: Int, var length: Int = 1, var velocity: Float = 0.8f)

class Track(
    var name: String = "Lead",
    var instrument: Int = 0,
    var volume: Float = 0.7f,
    var muted: Boolean = false,
    var echo: Float = 0f,
    val notes: MutableList<Note> = ArrayList(),
) {
    val isDrums get() = instrument == Song.DRUMS
}

class Song(
    var name: String = "Song",
    var bpm: Int = 120,
    var steps: Int = 64,
    var stepsPerBeat: Int = 4,
    var swing: Float = 0f,
    val tracks: MutableList<Track> = ArrayList(),
) {
    val secondsPerStep get() = 60f / bpm / stepsPerBeat
    val durationSeconds get() = steps * secondsPerStep

    fun toJson(): JSONObject = JSONObject().put("format", "song").put("name", name).put("bpm", bpm).put("steps", steps)
        .put("stepsPerBeat", stepsPerBeat).put("swing", swing.toDouble())
        .put("tracks", JSONArray().also { a ->
            tracks.forEach { t ->
                a.put(JSONObject().put("name", t.name).put("instrument", t.instrument).put("volume", t.volume.toDouble())
                    .put("muted", t.muted).put("echo", t.echo.toDouble())
                    .put("notes", JSONArray().also { n -> t.notes.sortedWith(compareBy({ it.step }, { it.pitch })).forEach { n.put(JSONArray().put(it.step).put(it.pitch).put(it.length).put(Math.round(it.velocity * 100) / 100.0)) } }))
            }
        })

    fun copy(): Song = fromJson(toJson())

    // ------------------------------------------------------------------ synthesis
    /** Renders the song to mono float samples at [RATE] Hz. [loops] repeats the pattern. */
    fun render(loops: Int = 1, tail: Boolean = true): FloatArray {
        val sps = secondsPerStep
        val total = steps * loops
        val len = ((total * sps + if (tail) 1.2f else 0f) * RATE).toInt().coerceAtLeast(1)
        val out = FloatArray(len)
        for (t in tracks) {
            if (t.muted || t.volume <= 0f) continue
            val buf = FloatArray(len)
            for (loop in 0 until loops) for (n in t.notes) {
                if (n.step !in 0 until steps) continue
                val s = n.step + loop * steps
                val swingOff = if (s % 2 == 1) swing * sps * 0.5f else 0f
                val start = ((s * sps + swingOff) * RATE).toInt()
                val dur = n.length.coerceAtLeast(1) * sps
                if (t.isDrums) drum(buf, start, n.pitch, n.velocity) else voice(buf, start, dur, t.instrument, n.pitch, n.velocity)
            }
            if (t.echo > 0f) {
                val d = (sps * 3 * RATE).toInt().coerceAtLeast(1)
                for (i in d until len) buf[i] += buf[i - d] * t.echo * 0.5f
            }
            for (i in 0 until len) out[i] += buf[i] * t.volume
        }
        // soft clip / normalise
        var peak = 0f
        for (v in out) peak = maxOf(peak, abs(v))
        val g = if (peak > 0.95f) 0.95f / peak else 1f
        for (i in out.indices) out[i] = softClip(out[i] * g)
        return out
    }

    fun renderWav(loops: Int = 1): ByteArray = wav(render(loops, tail = loops == 1))

    companion object {
        const val RATE = 22050
        val INSTRUMENTS = listOf("Square Lead", "Soft Sine", "Triangle Bass", "Saw Pad", "Pluck", "Drum Kit", "Chip Arp", "Organ", "Brass", "Bell")
        const val DRUMS = 5
        /** Drum kit rows (pitch 0..7). */
        val DRUM_NAMES = listOf("Kick", "Snare", "Closed Hat", "Open Hat", "Clap", "Low Tom", "High Tom", "Crash")
        val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        fun noteName(p: Int) = NOTE_NAMES[((p % 12) + 12) % 12] + (p / 12 - 1)
        fun freq(p: Int) = 440f * 2f.pow((p - 69) / 12f)

        fun fromJson(o: JSONObject): Song {
            val s = Song(o.optString("name", "Song"), o.optInt("bpm", 120), o.optInt("steps", 64), o.optInt("stepsPerBeat", 4), o.optDouble("swing", 0.0).toFloat())
            val ts = o.optJSONArray("tracks") ?: JSONArray()
            for (i in 0 until ts.length()) {
                val t = ts.getJSONObject(i)
                val tr = Track(t.optString("name", "Track"), t.optInt("instrument", 0), t.optDouble("volume", 0.7).toFloat(), t.optBoolean("muted", false), t.optDouble("echo", 0.0).toFloat())
                val ns = t.optJSONArray("notes") ?: JSONArray()
                for (k in 0 until ns.length()) {
                    val n = ns.getJSONArray(k)
                    tr.notes.add(Note(n.getInt(0), n.getInt(1), n.optInt(2, 1), n.optDouble(3, 0.8).toFloat()))
                }
                s.tracks.add(tr)
            }
            return s
        }

        fun parse(text: String) = fromJson(JSONObject(text))

        private fun softClip(x: Float): Float = if (x > 1f) 1f else if (x < -1f) -1f else x * (1.5f - 0.5f * x * x)

        fun wav(samples: FloatArray): ByteArray {
            val o = ByteArrayOutputStream(44 + samples.size * 2)
            fun u32(v: Int) { o.write(v and 0xFF); o.write((v shr 8) and 0xFF); o.write((v shr 16) and 0xFF); o.write((v shr 24) and 0xFF) }
            fun u16(v: Int) { o.write(v and 0xFF); o.write((v shr 8) and 0xFF) }
            o.write("RIFF".toByteArray()); u32(36 + samples.size * 2); o.write("WAVE".toByteArray())
            o.write("fmt ".toByteArray()); u32(16); u16(1); u16(1); u32(RATE); u32(RATE * 2); u16(2); u16(16)
            o.write("data".toByteArray()); u32(samples.size * 2)
            for (s in samples) u16((s.coerceIn(-1f, 1f) * 32000).toInt())
            return o.toByteArray()
        }

        private fun voice(b: FloatArray, start: Int, dur: Float, inst: Int, pitch: Int, vel: Float) {
            val f = freq(pitch)
            val (a, d, sLevel, r) = when (inst) {
                1 -> floatArrayOf(0.01f, 0.1f, 0.7f, 0.25f)
                2 -> floatArrayOf(0.005f, 0.08f, 0.8f, 0.08f)
                3 -> floatArrayOf(0.12f, 0.2f, 0.75f, 0.5f)
                4 -> floatArrayOf(0.002f, 0.25f, 0f, 0.15f)
                6 -> floatArrayOf(0.002f, 0.05f, 0.6f, 0.05f)
                7 -> floatArrayOf(0.01f, 0.05f, 0.9f, 0.08f)
                8 -> floatArrayOf(0.04f, 0.1f, 0.8f, 0.15f)
                9 -> floatArrayOf(0.001f, 0.9f, 0f, 0.6f)
                else -> floatArrayOf(0.005f, 0.08f, 0.6f, 0.08f)
            }.let { arrayOf(it[0], it[1], it[2], it[3]) }
            val n = ((dur + r) * RATE).toInt()
            var ph = 0.0; var ph2 = 0.0; var lp = 0f
            for (i in 0 until n) {
                val idx = start + i; if (idx >= b.size) break; if (idx < 0) continue
                val t = i / RATE.toFloat()
                val env = when {
                    t < a -> t / a
                    t < a + d -> 1f - (1f - sLevel) * ((t - a) / d)
                    t < dur -> sLevel
                    else -> { val base = if (dur < a + d) (1f - (1f - sLevel) * ((dur - a).coerceAtLeast(0f) / d).coerceAtMost(1f)) else sLevel; base * (1f - (t - dur) / r).coerceAtLeast(0f) }
                }.let { if (inst == 4 || inst == 9) maxOf(it, 0f) else it }
                val vib = if (inst == 0 || inst == 8) 1.0 + 0.004 * sin(2 * PI * 5.5 * t) else 1.0
                ph += f * vib / RATE; ph2 += f * 1.005 / RATE
                val p1 = ph % 1.0
                val v = when (inst) {
                    1 -> sin(2 * PI * ph).toFloat()
                    2 -> (4 * abs(p1 - 0.5) - 1).toFloat()
                    3 -> ((p1 * 2 - 1) + ((ph2 % 1.0) * 2 - 1)).toFloat() * 0.5f
                    4 -> { val raw = ((p1 * 2 - 1)).toFloat(); lp += (raw - lp) * (0.35f * exp(-t * 6f) + 0.03f); lp }
                    6 -> { val arp = intArrayOf(0, 4, 7, 12)[((t * 24).toInt()) % 4]; val fa = f * 2f.pow(arp / 12f); if (((t * fa) % 1f) < 0.5f) 0.6f else -0.6f }
                    7 -> (sin(2 * PI * ph) * 0.6 + sin(4 * PI * ph) * 0.3 + sin(8 * PI * ph) * 0.15).toFloat()
                    8 -> { val raw = (p1 * 2 - 1).toFloat(); lp += (raw - lp) * (0.12f + 0.25f * minOf(1f, t * 8f)); lp * 1.2f }
                    9 -> (sin(2 * PI * ph) * 0.7 + sin(2 * PI * ph * 2.76) * 0.25 * exp(-t * 3.0) + sin(2 * PI * ph * 5.4) * 0.12 * exp(-t * 6.0)).toFloat()
                    else -> if (p1 < 0.5) 0.55f else -0.55f
                }
                b[idx] += v * env * vel * 0.5f
            }
        }

        private fun drum(b: FloatArray, start: Int, kind: Int, vel: Float) {
            val rnd = Random(kind * 7919 + start)
            val len = when (kind) { 0 -> 0.35f; 1 -> 0.22f; 2 -> 0.05f; 3 -> 0.3f; 4 -> 0.2f; 5, 6 -> 0.3f; else -> 1.2f }
            val n = (len * RATE).toInt()
            var ph = 0.0; var lp = 0f; var hp = 0f; var prev = 0f
            for (i in 0 until n) {
                val idx = start + i; if (idx >= b.size) break; if (idx < 0) continue
                val t = i / RATE.toFloat()
                val noise = rnd.nextFloat() * 2 - 1
                val v = when (kind) {
                    0 -> { val f = 50f + 120f * exp(-t * 30f); ph += f / RATE; (sin(2 * PI * ph) * exp(-t * 9.0)).toFloat() * 1.2f }
                    1 -> { val f = 180f; ph += f / RATE; ((sin(2 * PI * ph) * 0.4 * exp(-t * 20.0)) + noise * 0.7 * exp(-t * 14.0)).toFloat() }
                    2, 3 -> { hp = 0.9f * (hp + noise - prev); prev = noise; hp * exp(-t * (if (kind == 2) 60f else 9f)) * 0.5f }
                    4 -> { lp += (noise - lp) * 0.4f; val burst = if (t < 0.03f) (if ((t * 100).toInt() % 1 == 0) 1f else 0.5f) else 1f; lp * burst * exp(-t * 16f) }
                    5, 6 -> { val f = (if (kind == 5) 90f else 150f) + 60f * exp(-t * 20f); ph += f / RATE; (sin(2 * PI * ph) * exp(-t * 8.0)).toFloat() }
                    else -> { hp = 0.95f * (hp + noise - prev); prev = noise; hp * exp(-t * 2.5f) * 0.45f }
                }
                b[idx] += v * vel * 0.6f
            }
        }

        // ------------------------------------------------------------------ generators
        private val MAJOR = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        private val MINOR = intArrayOf(0, 2, 3, 5, 7, 8, 10)
        val STYLES = listOf("Chiptune Adventure", "Action Battle", "Chill Lo-Fi", "Racing Rush", "Spooky Night", "Victory Fanfare", "Menu Theme", "Block World")

        /**
         * Auto Compose: builds a full arrangement (drums, bass, chords, lead) in a style.
         * Deterministic for a given seed so results can be reproduced.
         */
        fun compose(style: Int, seed: Int = 1, root: Int = 57): Song {
            val r = Random(seed * 31 + style)
            val minor = style in listOf(1, 3, 4, 7)
            val scale = if (minor) MINOR else MAJOR
            val bpm = intArrayOf(140, 150, 84, 165, 96, 120, 108, 100)[style.coerceIn(0, 7)]
            val s = Song(STYLES.getOrElse(style) { "Song" }, bpm, if (style == 5) 32 else 64)
            val progMajor = listOf(intArrayOf(0, 4, 5, 3), intArrayOf(0, 5, 3, 4), intArrayOf(0, 3, 4, 4))
            val progMinor = listOf(intArrayOf(0, 5, 2, 6), intArrayOf(0, 3, 4, 0), intArrayOf(0, 6, 5, 4))
            val prog = (if (minor) progMinor else progMajor)[r.nextInt(3)]
            fun deg(d: Int, oct: Int = 0): Int { val dd = ((d % 7) + 7) % 7; return root + scale[dd] + 12 * (oct + Math.floorDiv(d, 7)) }
            val bars = s.steps / 16

            val drums = Track("Drums", DRUMS, 0.8f)
            for (bar in 0 until bars) {
                val o = bar * 16
                when (style) {
                    2 -> { drums.notes += Note(o, 0); drums.notes += Note(o + 10, 0, 1, 0.6f); drums.notes += Note(o + 4, 1, 1, 0.6f); drums.notes += Note(o + 12, 1, 1, 0.6f); for (k in 0 until 16 step 2) drums.notes += Note(o + k, 2, 1, 0.35f) }
                    4 -> { drums.notes += Note(o, 5, 1, 0.7f); drums.notes += Note(o + 8, 6, 1, 0.5f); if (bar % 2 == 1) drums.notes += Note(o + 14, 4, 1, 0.4f) }
                    5 -> { for (k in 0 until 16 step 4) drums.notes += Note(o + k, 0); drums.notes += Note(o + 12, 7, 1, 0.6f); for (k in 0 until 16 step 2) drums.notes += Note(o + k, 1, 1, 0.3f + (k % 4) * 0.1f) }
                    else -> {
                        drums.notes += Note(o, 0); drums.notes += Note(o + 8, 0)
                        if (style == 1 || style == 3) { drums.notes += Note(o + 6, 0, 1, 0.6f); drums.notes += Note(o + 11, 0, 1, 0.5f) }
                        drums.notes += Note(o + 4, 1); drums.notes += Note(o + 12, 1)
                        for (k in 0 until 16 step (if (style == 3) 1 else 2)) drums.notes += Note(o + k, if (k % 8 == 6 && style != 3) 3 else 2, 1, if (k % 4 == 0) 0.5f else 0.3f)
                        if (bar % 4 == 3) { drums.notes += Note(o + 14, 5, 1, 0.6f); drums.notes += Note(o + 15, 6, 1, 0.6f) }
                        if (bar == 0) drums.notes += Note(o, 7, 1, 0.5f)
                    }
                }
            }
            val bass = Track("Bass", 2, 0.75f)
            val pad = Track("Chords", if (style == 2 || style == 4) 3 else if (style == 6) 7 else 3, if (style == 4) 0.35f else 0.28f)
            val lead = Track("Lead", intArrayOf(0, 8, 1, 6, 9, 8, 4, 4)[style.coerceIn(0, 7)], 0.55f, echo = if (style == 2 || style == 4 || style == 6) 0.35f else 0.15f)
            for (bar in 0 until bars) {
                val o = bar * 16; val chord = prog[bar % prog.size]
                // bass pattern
                val pattern = when (style) { 1, 3 -> intArrayOf(0, 2, 4, 6, 8, 10, 12, 14); 2 -> intArrayOf(0, 7, 10); 4 -> intArrayOf(0, 8); else -> intArrayOf(0, 4, 8, 12) }
                for ((k, st) in pattern.withIndex()) bass.notes += Note(o + st, deg(chord, -2) + if (style == 0 && k % 2 == 1) 12 else 0, if (pattern.size <= 3) 4 else 2, 0.8f)
                // chord pad
                for (tone in intArrayOf(0, 2, 4)) pad.notes += Note(o, deg(chord + tone, -1), 16, 0.5f)
                // lead melody: motif per 2 bars with variation
                var pos = 0
                var d = chord + if (r.nextBoolean()) 2 else 4
                while (pos < 16) {
                    val len = if (style == 2 || style == 4) intArrayOf(2, 4, 4, 6)[r.nextInt(4)] else intArrayOf(1, 2, 2, 2, 3, 4)[r.nextInt(6)]
                    if (r.nextFloat() < (if (style == 3 || style == 1) 0.85f else 0.7f)) lead.notes += Note(o + pos, deg(d, 1), minOf(len, 16 - pos), 0.6f + r.nextFloat() * 0.3f)
                    d += intArrayOf(-2, -1, -1, 1, 1, 2, 0, 3)[r.nextInt(8)]
                    d = d.coerceIn(chord - 3, chord + 7)
                    pos += len
                }
                if (bar % 4 == 3) lead.notes.removeAll { it.step >= o + 12 && it.step < o + 16 }.also { lead.notes += Note(o + 12, deg(chord, 1), 4, 0.7f) }
            }
            s.tracks += listOf(drums, bass, pad, lead)
            return s
        }
    }
}
