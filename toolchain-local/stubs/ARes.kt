package android.content.res

import java.io.InputStream

class AssetManager {
    fun list(path: String?): Array<String> = emptyArray()
    fun open(fileName: String?): InputStream = java.io.ByteArrayInputStream(ByteArray(0))
    fun close() {}
}
