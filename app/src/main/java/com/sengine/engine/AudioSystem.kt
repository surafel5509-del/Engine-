package com.sengine.engine

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import com.sengine.engine.core.AssetKind
import com.sengine.project.Project

class AudioSystem(private val project: Project) {
    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private val streams = ArrayList<Int>()
    private var tone: ToneGenerator? = null

    fun start() {
        stop()
        try { createPool() } catch (_: Throwable) { pool = null }
    }

    private fun createPool() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder().setMaxStreams(12).setAudioAttributes(attrs).build()
        pool = p
        for (name in project.listAssets(AssetKind.SOUND)) {
            if (project.assetFile(name).length() > 1_200_000) continue // long tracks stream via playMusic
            try {
                ids[name] = p.load(project.assetFile(name).absolutePath, 1)
            } catch (_: Exception) {
            }
        }
    }

    fun play(name: String, volume: Float = 1f, loop: Boolean = false, rate: Float = 1f): Int {
        if (name.endsWith(".song")) { playMusic(name, volume, loop); return 0 }
        val p = pool ?: return 0
        val id = ids[name] ?: return 0
        val s = try { p.play(id, volume * sfxVolume, volume * sfxVolume, 1, if (loop) -1 else 0, rate.coerceIn(0.5f, 2f)) } catch (_: Throwable) { 0 }
        if (s != 0) streams.add(s)
        return s
    }

    fun beep(durationMs: Int = 80) {
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, durationMs)
        } catch (_: Exception) {
        }
    }

    /** Master volumes (0..1) usable from scripts and game settings menus. */
    var sfxVolume = 1f
    var musicVolume = 1f
        set(v) { field = v.coerceIn(0f, 1f); try { music?.setVolume(musicBase * field, musicBase * field) } catch (_: Throwable) {} }
    private var music: android.media.MediaPlayer? = null
    private var musicName = ""
    private var musicBase = 1f

    /** Streams a long sound (wav/ogg/mp3) as background music. */
    fun playMusic(name: String, volume: Float = 1f, loop: Boolean = true) {
        if (name == musicName && music != null) { musicBase = volume; musicVolume = musicVolume; return }
        stopMusic()
        val f = if (name.endsWith(".song")) songWav(name, loop) ?: return else project.assetFile(name)
        if (!f.exists()) return
        try {
            val mp = android.media.MediaPlayer()
            mp.setDataSource(f.absolutePath)
            mp.isLooping = loop
            musicBase = volume
            mp.setVolume(volume * musicVolume, volume * musicVolume)
            mp.prepare(); mp.start()
            music = mp; musicName = name
        } catch (_: Throwable) { music = null; musicName = "" }
    }

    /** Renders a .song (S Engine music editor file) to a cached WAV, re-rendered when the song changes. */
    fun songWav(name: String, loop: Boolean = true): java.io.File? {
        val src = project.assetFile(name)
        if (!src.exists()) return null
        return try {
            val dir = java.io.File(project.dir, ".cache").apply { mkdirs() }
            val out = java.io.File(dir, name.replace('/', '_') + (if (loop) ".loop" else "") + ".wav")
            if (!out.exists() || out.lastModified() < src.lastModified()) {
                val song = com.sengine.engine.audio.Song.parse(src.readText())
                out.writeBytes(com.sengine.engine.audio.Song.wav(song.render(1, tail = !loop)))
            }
            out
        } catch (_: Throwable) { null }
    }

    fun stopMusic() {
        try { music?.stop(); music?.release() } catch (_: Throwable) {}
        music = null; musicName = ""
    }

    fun pauseMusic(paused: Boolean) { try { if (paused) music?.pause() else music?.start() } catch (_: Throwable) {} }

    fun stopStream(stream: Int) { try { pool?.stop(stream) } catch (_: Throwable) {}; streams.remove(stream) }

    fun setRate(stream: Int, rate: Float) { try { pool?.setRate(stream, rate.coerceIn(0.5f, 2f)) } catch (_: Throwable) {} }

    fun setVolume(stream: Int, v: Float) { try { pool?.setVolume(stream, v * sfxVolume, v * sfxVolume) } catch (_: Throwable) {} }

    fun stopAll() {
        val p = pool ?: return
        try { for (s in streams) p.stop(s) } catch (_: Throwable) {}
        streams.clear()
    }

    fun stop() {
        stopAll()
        stopMusic()
        try { pool?.release() } catch (_: Throwable) {}
        pool = null
        ids.clear()
        tone?.release()
        tone = null
    }
}
