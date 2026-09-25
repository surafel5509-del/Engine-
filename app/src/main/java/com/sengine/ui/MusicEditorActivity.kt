package com.sengine.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sengine.engine.audio.Note
import com.sengine.engine.audio.Song
import com.sengine.engine.audio.Track
import com.sengine.project.Project
import com.sengine.project.ProjectManager
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Game Music Editor: multi-track step sequencer + piano roll with a built-in synthesizer
 * (10 instruments incl. a drum kit), Auto Compose in 8 styles, live playback with playhead,
 * .song save (playable directly by the engine) and WAV export.
 */
class MusicEditorActivity : AppCompatActivity() {
    private lateinit var project: Project
    private var asset = "music.song"
    private var song = Song()
    private var savedJson = ""
    private var trackIdx = 0
    private lateinit var roll: PianoRoll
    private lateinit var trackList: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var bpmText: TextView
    private lateinit var stepsText: TextView
    private lateinit var playBtn: android.widget.ImageView
    private val handler = Handler(Looper.getMainLooper())
    private var player: AudioTrack? = null
    private var playStartFrame = 0
    private var playing = false
    private var loopPlayback = true

    private val undo = ArrayDeque<String>()
    private val redo = ArrayDeque<String>()

    companion object {
        val TRACK_COLORS = intArrayOf(0xFF5B7CFF.toInt(), 0xFF22D3EE.toInt(), 0xFF34D399.toInt(), 0xFFFBBF24.toInt(),
            0xFFFF5C6C.toInt(), 0xFFA78BFA.toInt(), 0xFFF472B6.toInt(), 0xFFFF9F43.toInt())

        fun newSongJson(name: String): String {
            val s = Song(name, 120, 64)
            s.tracks += Track("Drums", Song.DRUMS, 0.8f)
            s.tracks += Track("Bass", 2, 0.7f)
            s.tracks += Track("Lead", 0, 0.55f)
            return s.toJson().toString(2)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = ProjectManager.open(this, intent.getStringExtra("project")!!)
        asset = intent.getStringExtra("asset") ?: project.uniqueAssetName("music.song")
        song = try { project.readAsset(asset)?.let { Song.parse(it) } ?: Song(asset.substringBeforeLast('.')) } catch (e: Exception) {
            toast("Couldn't read song: ${e.message}"); Song(asset.substringBeforeLast('.'))
        }
        if (song.tracks.isEmpty()) song = Song.parse(newSongJson(song.name))
        savedJson = song.toJson().toString()

        val root = vbox().apply { setBackgroundColor(C.BG) }
        // ---------------------------------------------------------------- toolbar
        val bar = hbox().apply { setBackgroundColor(C.HEADER); setPadding(dp(6), dp(5), dp(6), dp(5)) }
        fun gap() = View(this).also { bar.addView(it, lp(dp(6), 1)) }
        bar.addView(iconButton("back", "Back", sizeDp = 38) { onBackPressedDispatcher.onBackPressed() })
        gap()
        val tb = vbox()
        titleText = label(asset, 14f, C.TEXT, true)
        tb.addView(titleText)
        tb.addView(label("Music Editor", 10f, C.ACCENT2))
        bar.addView(tb, lp(dp(130), WRAP))
        playBtn = iconButton("play", "Play / Stop", C.GREEN, sizeDp = 38) { togglePlay() }
        bar.addView(playBtn); gap()
        bar.addView(iconButton("refresh", "Loop playback on/off", C.ACCENT2, sizeDp = 38) { v ->
            loopPlayback = !loopPlayback; (v as android.widget.ImageView).alpha = if (loopPlayback) 1f else 0.4f
            toast(if (loopPlayback) "Loop on" else "Loop off")
        }); gap()
        bpmText = button("${song.bpm} BPM") { editBpm() }.apply { textSize = 12f }
        bar.addView(bpmText); gap()
        stepsText = button(stepsLabel()) { chooseSteps() }.apply { textSize = 12f }
        bar.addView(stepsText); gap()
        bar.addView(iconButton("undo", "Undo", sizeDp = 38) { undo() }); gap()
        bar.addView(iconButton("redo", "Redo", sizeDp = 38) { redo() }); gap()
        bar.addView(iconButton("search", "Zoom", sizeDp = 38) { roll.cycleZoom() }); gap()
        bar.addView(View(this), lp(0, 1, 1f))
        bar.addView(iconTextButton("wand", "Auto Compose", C.PURPLE, 0xFFFFFFFF.toInt()) { composeDialog() }); gap()
        bar.addView(iconButton("download", "Export WAV", sizeDp = 38) { exportWav() }); gap()
        bar.addView(iconButton("more", "More", sizeDp = 38) { moreMenu(it) }); gap()
        bar.addView(iconTextButton("save", "Save", C.ACCENT, 0xFFFFFFFF.toInt()) { save() })
        root.addView(android.widget.HorizontalScrollView(this).apply { addView(bar); isHorizontalScrollBarEnabled = false; setBackgroundColor(C.HEADER) }, lp(MATCH, WRAP))

        // ---------------------------------------------------------------- body
        val body = hbox().apply { gravity = Gravity.TOP }
        val left = vbox().apply { setBackgroundColor(C.PANEL) }
        val th = hbox().apply { setPadding(dp(8), dp(6), dp(4), dp(2)) }
        th.addView(sectionHeader("layers", "Tracks"), lp(0, WRAP, 1f))
        th.addView(iconButton("plus", "Add track", C.ACCENT2, sizeDp = 30) { addTrack() })
        left.addView(th)
        trackList = vbox().apply { setPadding(dp(6), 0, dp(6), dp(8)) }
        left.addView(ScrollView(this).apply { addView(trackList) }, lp(MATCH, 0, 1f))
        body.addView(left, lp(dp(220), MATCH))
        roll = PianoRoll(this)
        body.addView(roll, lp(0, MATCH, 1f))
        root.addView(body, lp(MATCH, 0, 1f))

        val hint = label("Tap empty cell = add note • drag = set length • tap note = delete • drag note start = move • two fingers = scroll • tap keys to preview",
            10f, C.DIM).apply { setPadding(dp(10), dp(3), dp(10), dp(3)); setBackgroundColor(C.HEADER); maxLines = 1 }
        root.addView(hint, lp(MATCH, WRAP))
        setContentView(root)
        rebuildTracks()
        selectTrack(0)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (song.toJson().toString() == savedJson) { finish(); return }
                MaterialAlertDialogBuilder(this@MusicEditorActivity).setTitle("Save changes to $asset?")
                    .setPositiveButton("Save") { _, _ -> save(); finish() }
                    .setNegativeButton("Discard") { _, _ -> finish() }
                    .setNeutralButton("Cancel", null).show()
            }
        })
    }

    private fun stepsLabel() = "${song.steps} steps • ${song.steps / (song.stepsPerBeat * 4)} bars"
    private val track get() = song.tracks.getOrNull(trackIdx)

    // ---------------------------------------------------------------- undo
    fun pushUndo() {
        undo.addLast(song.toJson().toString()); if (undo.size > 80) undo.removeFirst(); redo.clear()
    }
    private fun undo() {
        val s = undo.removeLastOrNull() ?: return
        redo.addLast(song.toJson().toString()); song = Song.parse(s); afterReplace()
    }
    private fun redo() {
        val s = redo.removeLastOrNull() ?: return
        undo.addLast(song.toJson().toString()); song = Song.parse(s); afterReplace()
    }
    private fun afterReplace() {
        trackIdx = trackIdx.coerceIn(0, max(0, song.tracks.size - 1))
        bpmText.text = "${song.bpm} BPM"; stepsText.text = stepsLabel()
        rebuildTracks(); selectTrack(trackIdx); songChanged()
    }

    /** Stops playback when the arrangement changes so the next play renders the new version. */
    fun songChanged() { if (playing) { stopPlayback(); startPlayback() } ; roll.invalidate() }

    // ---------------------------------------------------------------- tracks panel
    private fun rebuildTracks() {
        trackList.removeAllViews()
        song.tracks.forEachIndexed { i, t ->
            val color = TRACK_COLORS[i % TRACK_COLORS.size]
            val card = vbox().apply {
                background = round(if (i == trackIdx) C.SEL else C.PANEL2, dp(10).toFloat(), if (i == trackIdx) dp(2) else 0, color)
                setPadding(dp(8), dp(6), dp(6), dp(6))
                setOnClickListener { selectTrack(i) }
            }
            val r1 = hbox()
            r1.addView(View(this).apply { background = round(color, dp(5).toFloat()) }, lp(dp(10), dp(10)).margins(0, 0, dp(6), 0))
            r1.addView(label(t.name, 13f, C.TEXT, true).apply { maxLines = 1 }, lp(0, WRAP, 1f))
            r1.addView(iconButton(if (t.muted) "eye_off" else "speaker", if (t.muted) "Unmute" else "Mute", if (t.muted) C.RED else C.TEXT, C.PANEL, 28) {
                pushUndo(); t.muted = !t.muted; rebuildTracks(); songChanged()
            })
            r1.addView(iconButton("more", "Track options", C.DIM, C.PANEL, 28) { trackMenu(i, it) }, lp(dp(28), dp(28)).margins(dp(4), 0, 0, 0))
            card.addView(r1)
            card.addView(label("${Song.INSTRUMENTS[t.instrument.coerceIn(0, Song.INSTRUMENTS.size - 1)]} • ${t.notes.size} notes", 10f, C.DIM).apply {
                setOnClickListener { chooseInstrument(i) }
            })
            val vol = SeekBar(this).apply {
                max = 100; progress = (t.volume * 100).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) t.volume = p / 100f }
                    override fun onStartTrackingTouch(sb: SeekBar?) { pushUndo() }
                    override fun onStopTrackingTouch(sb: SeekBar?) { songChanged() }
                })
            }
            card.addView(vol, lp(MATCH, dp(26)))
            trackList.addView(card, lp(MATCH, WRAP).margins(0, dp(3), 0, dp(3)))
        }
    }

    private fun selectTrack(i: Int) {
        if (song.tracks.isEmpty()) return
        trackIdx = i.coerceIn(0, song.tracks.size - 1)
        roll.fitToTrack()
        rebuildTracks()
        roll.invalidate()
    }

    private fun addTrack() {
        val names = Song.INSTRUMENTS.toTypedArray()
        MaterialAlertDialogBuilder(this).setTitle("New track — instrument").setItems(names) { _, w ->
            pushUndo()
            song.tracks += Track(if (w == Song.DRUMS) "Drums" else names[w], w, if (w == Song.DRUMS) 0.8f else 0.55f)
            rebuildTracks(); selectTrack(song.tracks.size - 1)
        }.show()
    }

    private fun chooseInstrument(i: Int) {
        val t = song.tracks[i]
        MaterialAlertDialogBuilder(this).setTitle("Instrument for ${t.name}")
            .setSingleChoiceItems(Song.INSTRUMENTS.toTypedArray(), t.instrument) { d, w ->
                pushUndo(); t.instrument = w; d.dismiss(); rebuildTracks(); selectTrack(i); songChanged()
                previewNote(if (w == Song.DRUMS) 0 else 60)
            }.show()
    }

    private fun trackMenu(i: Int, anchor: View) {
        val t = song.tracks[i]
        val pm = android.widget.PopupMenu(this, anchor)
        listOf("Rename", "Instrument…", "Echo: ${(t.echo * 100).toInt()}%", "Solo", "Duplicate", "Clear notes", "Move up", "Move down", "Delete track").forEach { pm.menu.add(it) }
        pm.setOnMenuItemClickListener { item ->
            val s = item.title.toString()
            when {
                s == "Rename" -> {
                    val f = field(t.name)
                    MaterialAlertDialogBuilder(this).setTitle("Rename track").setView(f)
                        .setPositiveButton("OK") { _, _ -> pushUndo(); t.name = f.text.toString().ifBlank { t.name }; rebuildTracks() }
                        .setNegativeButton("Cancel", null).show()
                }
                s.startsWith("Instrument") -> chooseInstrument(i)
                s.startsWith("Echo") -> {
                    val opts = arrayOf("Off", "15%", "30%", "45%", "60%")
                    MaterialAlertDialogBuilder(this).setTitle("Echo / delay").setItems(opts) { _, w ->
                        pushUndo(); t.echo = w * 0.15f; rebuildTracks(); songChanged()
                    }.show()
                }
                s == "Solo" -> { pushUndo(); song.tracks.forEachIndexed { k, o -> o.muted = k != i }; rebuildTracks(); songChanged() }
                s == "Duplicate" -> {
                    pushUndo()
                    val c = Track(t.name + " 2", t.instrument, t.volume, t.muted, t.echo, t.notes.map { Note(it.step, it.pitch, it.length, it.velocity) }.toMutableList())
                    song.tracks.add(i + 1, c); rebuildTracks(); selectTrack(i + 1); songChanged()
                }
                s == "Clear notes" -> { pushUndo(); t.notes.clear(); rebuildTracks(); songChanged() }
                s == "Move up" && i > 0 -> { pushUndo(); song.tracks.add(i - 1, song.tracks.removeAt(i)); trackIdx = i - 1; rebuildTracks(); roll.invalidate() }
                s == "Move down" && i < song.tracks.size - 1 -> { pushUndo(); song.tracks.add(i + 1, song.tracks.removeAt(i)); trackIdx = i + 1; rebuildTracks(); roll.invalidate() }
                s == "Delete track" -> {
                    if (song.tracks.size <= 1) toast("A song needs at least one track")
                    else { pushUndo(); song.tracks.removeAt(i); trackIdx = trackIdx.coerceAtMost(song.tracks.size - 1); rebuildTracks(); selectTrack(trackIdx); songChanged() }
                }
            }
            true
        }
        pm.show()
    }

    // ---------------------------------------------------------------- song settings
    private fun editBpm() {
        val f = field(song.bpm.toString(), numeric = true)
        val sw = field(fmt(song.swing * 100), numeric = true)
        val box = vbox().apply { setPadding(dp(20), dp(8), dp(20), 0) }
        box.addView(label("Tempo (BPM, 40–240)", 12f, C.DIM)); box.addView(f)
        box.addView(label("Swing (0–60 %)", 12f, C.DIM).apply { setPadding(0, dp(10), 0, 0) }); box.addView(sw)
        MaterialAlertDialogBuilder(this).setTitle("Tempo & feel").setView(box)
            .setPositiveButton("OK") { _, _ ->
                pushUndo()
                song.bpm = (f.text.toString().toFloatOrNull()?.toInt() ?: song.bpm).coerceIn(40, 240)
                song.swing = ((sw.text.toString().toFloatOrNull() ?: 0f) / 100f).coerceIn(0f, 0.6f)
                bpmText.text = "${song.bpm} BPM"; songChanged()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun chooseSteps() {
        val opts = intArrayOf(16, 32, 64, 128, 256)
        MaterialAlertDialogBuilder(this).setTitle("Song length")
            .setItems(opts.map { "$it steps (${it / 16} bars)" }.toTypedArray()) { _, w ->
                pushUndo()
                val n = opts[w]
                if (n > song.steps) {
                    // extend by repeating the existing pattern
                    val old = song.steps
                    for (t in song.tracks) {
                        val base = t.notes.toList()
                        var off = old
                        while (off < n) { for (b in base) if (b.step + off < n) t.notes += Note(b.step + off, b.pitch, b.length, b.velocity); off += old }
                    }
                } else for (t in song.tracks) t.notes.removeAll { it.step >= n }
                song.steps = n; stepsText.text = stepsLabel(); roll.invalidate(); rebuildTracks(); songChanged()
            }.show()
    }

    private fun composeDialog() {
        val box = vbox().apply { setPadding(dp(20), dp(6), dp(20), 0) }
        box.addView(label("Generates a complete arrangement (drums, bass, chords, lead melody). Replace the current song or pick another seed for a new variation.", 12f, C.DIM))
        var style = 0
        val group = android.widget.RadioGroup(this)
        Song.STYLES.forEachIndexed { i, s -> group.addView(android.widget.RadioButton(this).apply { id = 100 + i; text = s; setTextColor(C.TEXT) }) }
        group.check(100); group.setOnCheckedChangeListener { _, id -> style = id - 100 }
        box.addView(group)
        val seed = field((1..9999).random().toString(), numeric = true)
        box.addView(label("Seed (variation)", 12f, C.DIM)); box.addView(seed)
        val keys = arrayOf("A minor / C major (57)", "C (60)", "D (62)", "E (64)", "F (65)", "G (67)")
        val roots = intArrayOf(57, 60, 62, 64, 65, 67)
        var rootSel = 0
        val keySpin = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@MusicEditorActivity, android.R.layout.simple_spinner_dropdown_item, keys)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) { rootSel = pos }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
        }
        box.addView(label("Key", 12f, C.DIM)); box.addView(keySpin)
        MaterialAlertDialogBuilder(this).setTitle("Auto Compose").setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Compose") { _, _ ->
                pushUndo()
                val name = song.name
                song = Song.compose(style, seed.text.toString().toIntOrNull() ?: 1, roots[rootSel]).also { it.name = name }
                afterReplace(); startPlaybackIfIdle()
                toast("Composed: ${Song.STYLES[style]}")
            }.setNegativeButton("Cancel", null).show()
    }

    private fun moreMenu(anchor: View) {
        val pm = android.widget.PopupMenu(this, anchor)
        listOf("Rename song", "Transpose track +1", "Transpose track −1", "Octave up (track)", "Octave down (track)", "Humanize velocities", "Double speed (track)", "New empty song").forEach { pm.menu.add(it) }
        pm.setOnMenuItemClickListener { item ->
            val t = track
            when (item.title.toString()) {
                "Rename song" -> {
                    val f = field(song.name)
                    MaterialAlertDialogBuilder(this).setTitle("Song name").setView(f)
                        .setPositiveButton("OK") { _, _ -> pushUndo(); song.name = f.text.toString() }.setNegativeButton("Cancel", null).show()
                }
                "Transpose track +1" -> if (t != null && !t.isDrums) { pushUndo(); t.notes.forEach { it.pitch = (it.pitch + 1).coerceAtMost(108) }; songChanged() }
                "Transpose track −1" -> if (t != null && !t.isDrums) { pushUndo(); t.notes.forEach { it.pitch = (it.pitch - 1).coerceAtLeast(12) }; songChanged() }
                "Octave up (track)" -> if (t != null && !t.isDrums) { pushUndo(); t.notes.forEach { it.pitch = (it.pitch + 12).coerceAtMost(108) }; roll.fitToTrack(); songChanged() }
                "Octave down (track)" -> if (t != null && !t.isDrums) { pushUndo(); t.notes.forEach { it.pitch = (it.pitch - 12).coerceAtLeast(12) }; roll.fitToTrack(); songChanged() }
                "Humanize velocities" -> if (t != null) { pushUndo(); t.notes.forEach { it.velocity = (it.velocity + (Math.random().toFloat() - 0.5f) * 0.25f).coerceIn(0.2f, 1f) }; songChanged() }
                "Double speed (track)" -> if (t != null) { pushUndo(); t.notes.forEach { it.step /= 2; it.length = max(1, it.length / 2) }; songChanged() }
                "New empty song" -> { pushUndo(); val n = song.name; song = Song.parse(newSongJson(n)); afterReplace() }
            }
            true
        }
        pm.show()
    }

    // ---------------------------------------------------------------- save/export
    private fun save() {
        try {
            project.writeAsset(asset, song.toJson().toString(2))
            savedJson = song.toJson().toString()
            toast("Saved $asset")
        } catch (e: Exception) { toast("Save failed: ${e.message}") }
    }

    private fun exportWav() {
        val name = project.uniqueAssetName(asset.substringBeforeLast('.') + ".wav")
        val snap = song.copy()
        toast("Rendering…")
        thread(name = "wav-export") {
            try {
                val bytes = snap.renderWav(1)
                project.assetFile(name).apply { parentFile?.mkdirs() }.writeBytes(bytes)
                handler.post { toast("Exported $name (${bytes.size / 1024} KB) to Assets") }
            } catch (e: Exception) { handler.post { toast("Export failed: ${e.message}") } }
        }
    }

    // ---------------------------------------------------------------- playback
    private fun togglePlay() { if (playing) stopPlayback() else startPlayback() }
    private fun startPlaybackIfIdle() { if (!playing) startPlayback() }

    private fun startPlayback() {
        val snap = song.copy()
        playing = true
        playBtn.setIconTint("stop", C.RED, 21)
        thread(name = "song-render") {
            val samples = try { snap.render(1, tail = !loopPlayback) } catch (e: Throwable) { null }
            handler.post {
                if (!playing || samples == null) return@post
                val pcm = ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * 32767).toInt().toShort() }
                try {
                    val t = AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(Song.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
                    t.write(pcm, 0, pcm.size)
                    if (loopPlayback) t.setLoopPoints(0, pcm.size, -1)
                    t.play()
                    player = t
                    playStartFrame = 0
                    handler.post(playheadTick)
                } catch (e: Exception) { toast("Audio error: ${e.message}"); stopPlayback() }
            }
        }
    }

    private val playheadTick = object : Runnable {
        override fun run() {
            val t = player ?: return
            val frames = try { t.playbackHeadPosition } catch (_: Exception) { 0 }
            val stepF = frames / (song.secondsPerStep * Song.RATE)
            roll.playhead = if (loopPlayback) stepF % song.steps else stepF
            if (!loopPlayback && stepF > song.steps + 4) { stopPlayback(); return }
            roll.invalidate()
            handler.postDelayed(this, 30)
        }
    }

    private fun stopPlayback() {
        playing = false
        handler.removeCallbacks(playheadTick)
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        player = null
        roll.playhead = -1f; roll.invalidate()
        playBtn.setIconTint("play", C.GREEN, 21)
    }

    private var previewTrack: AudioTrack? = null
    fun previewNote(pitch: Int) {
        val t = track ?: return
        val s = Song("p", song.bpm, 4)
        s.tracks += Track("p", t.instrument, 0.8f, notes = mutableListOf(Note(0, pitch, 2, 0.8f)))
        thread(name = "note-preview") {
            val samples = try { s.render(1, true) } catch (_: Throwable) { return@thread }
            val n = min(samples.size, Song.RATE)
            val pcm = ShortArray(n) { (samples[it].coerceIn(-1f, 1f) * 30000).toInt().toShort() }
            handler.post {
                try {
                    previewTrack?.release()
                    val a = AudioTrack.Builder()
                        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(Song.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
                    a.write(pcm, 0, pcm.size); a.play(); previewTrack = a
                } catch (_: Exception) {}
            }
        }
    }

    override fun onPause() { super.onPause(); stopPlayback() }
    override fun onDestroy() { super.onDestroy(); stopPlayback(); try { previewTrack?.release() } catch (_: Exception) {} }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ================================================================ piano roll
    @SuppressLint("ViewConstructor")
    inner class PianoRoll(ctx: Context) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(10).toFloat(); color = C.DIM }
        private val keyW = dp(52).toFloat()
        private val headerH = dp(20).toFloat()
        private var cellW = dp(26).toFloat()
        private var cellH = dp(20).toFloat()
        private var scrollX = 0f
        private var scrollY = 0f
        var lowPitch = 48
        var playhead = -1f
        private val rows get() = if (track?.isDrums == true) Song.DRUM_NAMES.size else 36
        private val rect = RectF()

        fun cycleZoom() {
            cellW = when { cellW < dp(20) -> dp(26).toFloat(); cellW < dp(30) -> dp(38).toFloat(); else -> dp(16).toFloat() }
            clampScroll(); invalidate()
        }

        fun fitToTrack() {
            val t = track ?: return
            scrollY = 0f
            if (t.isDrums) { cellH = dp(30).toFloat(); return }
            cellH = dp(20).toFloat()
            if (t.notes.isEmpty()) { lowPitch = if (t.instrument == 2) 36 else 48; scrollToPitch(lowPitch + 24); return }
            val lo = t.notes.minOf { it.pitch }; val hi = t.notes.maxOf { it.pitch }
            lowPitch = ((lo / 12) * 12).coerceIn(12, 72)
            if (hi >= lowPitch + 36) lowPitch = (hi - 35).coerceAtLeast(12)
            post { scrollToPitch(hi) }
        }

        private fun scrollToPitch(p: Int) {
            val row = (lowPitch + rows - 1 - p)
            scrollY = row * cellH - dp(20)
            clampScroll(); invalidate()
        }

        private fun clampScroll() {
            val maxX = max(0f, song.steps * cellW - (width - keyW))
            val maxY = max(0f, rows * cellH - (height - headerH))
            scrollX = scrollX.coerceIn(0f, maxX); scrollY = scrollY.coerceIn(0f, maxY)
        }

        private fun rowPitch(row: Int): Int = if (track?.isDrums == true) row else lowPitch + rows - 1 - row
        private fun pitchRow(p: Int): Int = if (track?.isDrums == true) p else lowPitch + rows - 1 - p
        private fun isBlack(p: Int) = ((p % 12) + 12) % 12 in intArrayOf(1, 3, 6, 8, 10)

        override fun onDraw(c: Canvas) {
            val t = track ?: return
            c.drawColor(C.BG)
            val gridL = keyW; val gridT = headerH
            val w = width.toFloat(); val h = height.toFloat()
            val firstRow = (scrollY / cellH).toInt(); val lastRow = min(rows - 1, ((scrollY + h - gridT) / cellH).toInt() + 1)
            val firstStep = (scrollX / cellW).toInt(); val lastStep = min(song.steps - 1, ((scrollX + w - gridL) / cellW).toInt() + 1)
            // rows
            for (r in firstRow..lastRow) {
                val y = gridT + r * cellH - scrollY
                val p = rowPitch(r)
                paint.color = if (!t.isDrums && isBlack(p)) 0xFF10142A.toInt() else if (r % 2 == 0) 0xFF151A30.toInt() else 0xFF131729.toInt()
                c.drawRect(gridL, y, w, y + cellH, paint)
                if (!t.isDrums && ((p % 12) + 12) % 12 == 0) { paint.color = 0xFF2A3154.toInt(); c.drawRect(gridL, y + cellH - 1, w, y + cellH, paint) }
            }
            // columns
            for (s in firstStep..lastStep + 1) {
                val x = gridL + s * cellW - scrollX
                paint.color = when { s % (song.stepsPerBeat * 4) == 0 -> 0xFF4A558A.toInt(); s % song.stepsPerBeat == 0 -> 0xFF2C3458.toInt(); else -> 0xFF1C2240.toInt() }
                c.drawRect(x, gridT, x + (if (s % (song.stepsPerBeat * 4) == 0) 2 else 1), h, paint)
            }
            // ghost notes of other tracks (melodic only, same pitch space)
            if (!t.isDrums) for ((ti, o) in song.tracks.withIndex()) {
                if (ti == trackIdx || o.isDrums) continue
                paint.color = (TRACK_COLORS[ti % TRACK_COLORS.size] and 0x00FFFFFF) or 0x30000000
                for (n in o.notes) {
                    val r = pitchRow(n.pitch); if (r < firstRow || r > lastRow) continue
                    val x = gridL + n.step * cellW - scrollX; val y = gridT + r * cellH - scrollY
                    rect.set(x + 1, y + 2, x + n.length * cellW - 1, y + cellH - 2); c.drawRoundRect(rect, dp(3).toFloat(), dp(3).toFloat(), paint)
                }
            }
            // notes of the selected track
            val col = TRACK_COLORS[trackIdx % TRACK_COLORS.size]
            for (n in t.notes) {
                val r = pitchRow(n.pitch); if (r < firstRow || r > lastRow) continue
                val x = gridL + n.step * cellW - scrollX; val y = gridT + r * cellH - scrollY
                if (x > w || x + n.length * cellW < gridL) continue
                rect.set(x + 1, y + 1, x + n.length * cellW - 1, y + cellH - 1)
                paint.color = col; paint.alpha = (110 + 145 * n.velocity).toInt().coerceIn(60, 255)
                c.drawRoundRect(rect, dp(4).toFloat(), dp(4).toFloat(), paint)
                paint.alpha = 255; paint.color = 0x55FFFFFF
                c.drawRect(rect.right - dp(4), rect.top + dp(3), rect.right - dp(2), rect.bottom - dp(3), paint)
                if (t.muted) { paint.color = 0x88000000.toInt(); c.drawRoundRect(rect, dp(4).toFloat(), dp(4).toFloat(), paint) }
            }
            // header (bar numbers)
            paint.color = C.HEADER; c.drawRect(0f, 0f, w, gridT, paint)
            text.color = C.DIM
            val bar = song.stepsPerBeat * 4
            for (s in firstStep..lastStep) if (s % bar == 0) c.drawText("${s / bar + 1}", gridL + s * cellW - scrollX + dp(3), gridT - dp(6), text)
            // keys column
            paint.color = C.PANEL; c.drawRect(0f, gridT, keyW, h, paint)
            for (r in firstRow..lastRow) {
                val y = gridT + r * cellH - scrollY
                val p = rowPitch(r)
                if (t.isDrums) {
                    paint.color = if (r % 2 == 0) C.PANEL2 else C.PANEL
                    c.drawRect(0f, y, keyW, y + cellH, paint)
                    text.color = C.TEXT; c.drawText(Song.DRUM_NAMES[r], dp(4).toFloat(), y + cellH / 2 + dp(4), text)
                } else {
                    paint.color = if (isBlack(p)) 0xFF1A1D2B.toInt() else 0xFFD8DCF0.toInt()
                    c.drawRect(0f, y + 0.5f, keyW - 1, y + cellH - 0.5f, paint)
                    text.color = if (isBlack(p)) C.DIM else 0xFF1A1D2B.toInt()
                    if (!isBlack(p) || cellH > dp(18)) c.drawText(Song.noteName(p), dp(4).toFloat(), y + cellH / 2 + dp(4), text)
                }
            }
            paint.color = C.HEADER; c.drawRect(0f, 0f, keyW, gridT, paint)
            // playhead
            if (playhead >= 0) {
                val x = gridL + playhead * cellW - scrollX
                if (x >= gridL) { paint.color = C.ACCENT2; c.drawRect(x - 1, 0f, x + 1.5f, h, paint) }
            }
        }

        // ---------------------------------------------------------------- touch
        private var mode = 0 // 0 none, 1 new note/resize, 2 move, 3 pan, 4 tapped existing note, 5 key
        private var active: Note? = null
        private var downX = 0f; private var downY = 0f
        private var lastPanX = 0f; private var lastPanY = 0f
        private var moved = false
        private var grabOffset = 0

        private fun cellAt(x: Float, y: Float): Pair<Int, Int> =
            ((x - keyW + scrollX) / cellW).toInt() to ((y - headerH + scrollY) / cellH).toInt()

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            val t = track ?: return true
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x; downY = e.y; moved = false; active = null; mode = 0
                    if (e.y < headerH) { mode = 3; lastPanX = e.x; lastPanY = e.y; return true }
                    val (s, r) = cellAt(e.x, e.y)
                    if (r !in 0 until rows) return true
                    val p = rowPitch(r)
                    if (e.x < keyW) { mode = 5; previewNote(p); return true }
                    if (s !in 0 until song.steps) return true
                    val hit = t.notes.firstOrNull { it.pitch == p && s >= it.step && s < it.step + it.length }
                    if (hit != null) {
                        active = hit
                        val nearEnd = (e.x - keyW + scrollX) > (hit.step + hit.length) * cellW - max(cellW * 0.6f, dp(14).toFloat())
                        mode = if (nearEnd && hit.length >= 1) 1 else 4
                        grabOffset = s - hit.step
                        pushUndo()
                    } else {
                        pushUndo()
                        val n = Note(s, p, if (t.isDrums) 1 else defaultLen(), 0.8f)
                        t.notes += n; active = n; mode = 1
                        previewNote(p)
                        invalidate()
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // second finger: switch to panning; cancel the note we may have just created
                    if (mode == 1 && !moved && active != null && active!!.length == (if (t.isDrums) 1 else defaultLen())) {
                        if (undo.isNotEmpty()) { song = Song.parse(undo.removeLast()) }
                    }
                    mode = 3; active = null
                    lastPanX = avgX(e); lastPanY = avgY(e); invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(e.x - downX) > dp(6) || abs(e.y - downY) > dp(6)) moved = true
                    val n = active
                    when (mode) {
                        1 -> if (n != null && moved) {
                            val (s, _) = cellAt(e.x, e.y)
                            n.length = (s - n.step + 1).coerceIn(1, song.steps - n.step)
                            invalidate()
                        }
                        4, 2 -> if (n != null && moved) {
                            mode = 2
                            val (s, r) = cellAt(e.x, e.y)
                            n.step = (s - grabOffset).coerceIn(0, song.steps - n.length)
                            if (r in 0 until rows) { val np = rowPitch(r); if (np != n.pitch) { n.pitch = np; previewNote(np) } }
                            invalidate()
                        }
                        3 -> {
                            val ax = avgX(e); val ay = avgY(e)
                            scrollX -= ax - lastPanX; scrollY -= ay - lastPanY
                            lastPanX = ax; lastPanY = ay
                            clampScroll(); invalidate()
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val n = active
                    if (mode == 4 && !moved && n != null) { t.notes.remove(n); invalidate() }
                    if (mode == 3 && !moved && e.y < headerH) {
                        // tap on the ruler: jump playhead start (preview from bar)
                    }
                    if (mode in 1..4 && mode != 3) { dedupe(t); rebuildTracksLater(); songChanged() }
                    mode = 0; active = null
                }
                MotionEvent.ACTION_CANCEL -> { mode = 0; active = null }
            }
            return true
        }

        private fun avgX(e: MotionEvent): Float { var s = 0f; for (i in 0 until e.pointerCount) s += e.getX(i); return s / e.pointerCount }
        private fun avgY(e: MotionEvent): Float { var s = 0f; for (i in 0 until e.pointerCount) s += e.getY(i); return s / e.pointerCount }
        private fun defaultLen() = if (track?.instrument == 3 || track?.instrument == 7) 4 else 2

        private fun dedupe(t: Track) {
            val seen = HashSet<Long>()
            t.notes.removeAll { n -> !seen.add(n.step.toLong() * 1000 + n.pitch) }
            t.notes.sortWith(compareBy({ it.step }, { it.pitch }))
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); clampScroll() }
    }

    private fun rebuildTracksLater() = handler.post { rebuildTracks() }
}
