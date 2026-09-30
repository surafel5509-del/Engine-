package android.media

import java.io.FileDescriptor

class AudioAttributes {
    class Builder {
        fun setUsage(usage: Int): Builder = this
        fun setContentType(contentType: Int): Builder = this
        fun setLegacyStreamType(streamType: Int): Builder = this
        fun build(): AudioAttributes = AudioAttributes()
    }
    companion object {
        const val USAGE_GAME = 14
        const val USAGE_MEDIA = 1
        const val CONTENT_TYPE_SONIFICATION = 4
        const val CONTENT_TYPE_MUSIC = 2
        const val CONTENT_TYPE_UNKNOWN = 0
    }
}

class SoundPool(maxStreams: Int, streamType: Int, srcQuality: Int) {
    constructor(maxStreams: Int, attrs: AudioAttributes, srcQuality: Int) : this(maxStreams, 3, 0)
    class Builder {
        fun setMaxStreams(max: Int): Builder = this
        fun setAudioAttributes(attrs: AudioAttributes): Builder = this
        fun setLegacyStreamType(streamType: Int): Builder = this
        fun build(): SoundPool = SoundPool(4, 3, 0)
    }
    var onLoadCompleteListener: ((Int, Int, Int) -> Unit)? = null
    fun setOnLoadCompleteListener(l: Any?) {}
    fun load(context: Any?, resId: Int): Int = 1
    fun load(context: Any?, path: String?): Int = 1
    fun load(path: String?): Int = 1
    fun load(fd: FileDescriptor?, offset: Long, length: Long): Int = 1
    fun play(soundID: Int, leftVolume: Float, rightVolume: Float, priority: Int, loop: Int, rate: Float): Int = 1
    fun pause(streamID: Int) {}
    fun resume(streamID: Int) {}
    fun stop(streamID: Int) {}
    fun setVolume(streamID: Int, leftVolume: Float, rightVolume: Float) {}
    fun setVolume(streamID: Int, volume: Float) {}
    fun setRate(streamID: Int, rate: Float) {}
    fun setLoop(streamID: Int, loop: Int) {}
    fun setPriority(streamID: Int, priority: Int) {}
    fun release() {}
    fun unload(soundID: Int) {}
}

class ToneGenerator(streamType: Int, volume: Int) {
    companion object {
        const val STREAM_MUSIC = 3
        const val PROP_BEEP = 40
        const val PROP_BEEP2 = 41
        const val PROP_ERROR = 44
        const val PROP_ACK = 45
    }
    fun startTone(toneType: Int): Boolean = true
    fun startTone(toneType: Int, durationMs: Int): Boolean = true
    fun stopTone() {}
    fun release() {}
}

class MediaPlayer {
    companion object {
        @JvmStatic fun create(context: Any?, resId: Int): MediaPlayer = MediaPlayer()
        @JvmStatic fun create(context: Any?, uri: Any?): MediaPlayer = MediaPlayer()
    }
    var onPreparedListener: ((Any?) -> Unit)? = null
    var onCompletionListener: ((Any?) -> Unit)? = null
    var onErrorListener: ((Any?, Int, Int) -> Boolean)? = null
    fun setDataSource(path: String?) {}
    fun setDataSource(fd: FileDescriptor?, offset: Long = 0, length: Long = 0) {}
    fun setAudioStreamType(streamType: Int) {}
    fun setLooping(loop: Boolean) {}
    fun isLooping(): Boolean = false
    fun prepare() {}
    fun prepareAsync() {}
    fun start() {}
    fun pause() {}
    fun stop() {}
    fun isPlaying(): Boolean = false
    fun seekTo(msec: Int) {}
    fun getDuration(): Int = 0
    fun getCurrentPosition(): Int = 0
    fun setVolume(left: Float, right: Float) {}
    fun setOnPreparedListener(l: Any?) {}
    fun setOnCompletionListener(l: Any?) {}
    fun setOnErrorListener(l: Any?) {}
    fun release() {}
    fun reset() {}
}

class AudioManager {
    companion object {
        const val STREAM_MUSIC = 3
        const val STREAM_SYSTEM = 1
        const val STREAM_ALARM = 4
        const val RINGER_MODE_NORMAL = 2
        const val RINGER_MODE_SILENT = 0
        const val RINGER_MODE_VIBRATE = 1
    }
    fun getStreamVolume(streamType: Int): Int = 8
    fun getStreamMaxVolume(streamType: Int): Int = 15
    fun setStreamVolume(streamType: Int, index: Int, flags: Int) {}
    fun getRingerMode(): Int = RINGER_MODE_NORMAL
    fun setRingerMode(mode: Int) {}
    fun playSoundEffect(effectType: Int) {}
    fun adjustStreamVolume(streamType: Int, direction: Int, flags: Int) {}
}
