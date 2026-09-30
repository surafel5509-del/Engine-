package android.content

open class Context {
    companion object {
        const val MODE_PRIVATE = 0
        const val MODE_WORLD_READABLE = 1
        const val MODE_WORLD_WRITEABLE = 2
        const val AUDIO_SERVICE = "audio"
        const val VIBRATOR_SERVICE = "vibrator"
        const val WINDOW_SERVICE = "window"
        const val LAYOUT_INFLATER_SERVICE = "layout_inflater"
        const val ALARM_SERVICE = "alarm"
        const val NOTIFICATION_SERVICE = "notification"
    }
    val assets: android.content.res.AssetManager get() = android.content.res.AssetManager()
    val resources: Any? get() = null
    val filesDir: java.io.File get() = java.io.File("/tmp/sengine-files")
    val cacheDir: java.io.File get() = java.io.File("/tmp/sengine-cache")
    fun getExternalFilesDir(type: String?): java.io.File = java.io.File("/tmp/sengine-external")
    val packageName: String get() = "com.sengine"
    fun getSharedPreferences(name: String?, mode: Int): Any? = null
    fun getSystemService(name: String?): Any? = null
    val applicationContext: Context get() = this
}
