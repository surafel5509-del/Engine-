package com.sengine.project.importer

import com.sengine.engine.model.SPart
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.math.abs

/**
 * v7 universal 3D model import — turns the industry-standard mesh formats into S Engine `.smodel`
 * parts so any imported model is immediately editable in the Model Editor and renderable by MeshRenderer.
 *
 * Supported: Wavefront OBJ (+MTL colours), STL (binary + ASCII), PLY (ASCII + binary little endian),
 * glTF 2.0 (.gltf + embedded or side-car .bin buffers), GLB (binary glTF), Collada .dae,
 * Autodesk FBX ASCII. `.blend`, `.3ds`, `.max` etc. are imported as raw project assets with a note.
 */
object ModelImporters {

    class MPart(val name: String, val verts: ArrayList<FloatArray> = ArrayList(), val faces: ArrayList<IntArray> = ArrayList(),
                val faceColors: ArrayList<Int> = ArrayList(), val uvs: ArrayList<FloatArray?> = ArrayList()) {
        var color = 0xFFB0B8C8.toInt()
        var smooth = false
    }

    class Result(val parts: List<MPart>, val warnings: List<String>, val format: String) {
        val triangles: Int get() = parts.sumOf { p -> p.faces.sumOf { (it.size - 2).coerceAtLeast(0) } }
    }

    /** Sniffs the format from the file name + first bytes. Returns null when it is not a 3D model. */
    fun sniff(name: String, head: ByteArray): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "obj" -> "obj"
            "stl" -> "stl"
            "ply" -> "ply"
            "gltf" -> "gltf"
            "glb" -> "glb"
            "dae" -> "dae"
            "fbx" -> "fbx"
            "blend", "3ds", "max", "ma", "mb", "usd", "usda", "usdc", "usdz", "abc" -> ext
            else -> null
        }
    }

    val SUPPORTED = listOf("OBJ (+MTL)", "STL", "PLY", "glTF 2.0 (.gltf)", "GLB", "Collada (.dae)", "FBX (ASCII)", ".smodel")
    val KNOWN_EXTRAS = listOf("blend", "3ds", "max", "ma", "mb", "usd", "usda", "usdc", "usdz", "abc")

    /**
     * Converts model bytes into S Engine parts. [sidecar] loads sibling files (MTL, glTF .bin, textures)
     * from the same folder; return null when missing.
     */
    fun import(fileName: String, bytes: ByteArray, sidecar: (String) -> ByteArray? = { null }): Result {
        val head = bytes.copyOfRange(0, minOf(bytes.size, 512))
        val fmt = sniff(fileName, head) ?: throw IllegalArgumentException("'$fileName' is not a recognised 3D model file")
        val warnings = ArrayList<String>()
        val text = { String(bytes, StandardCharsets.UTF_8) }
        val parts = when (fmt) {
            "obj" -> obj(text(), fileName.substringBeforeLast('.') + ".mtl", sidecar, warnings)
            "stl" -> stl(bytes, warnings)
            "ply" -> ply(bytes, warnings)
            "gltf" -> gltf(JSONObject(text()), fileName, sidecar, warnings)
            "glb" -> glb(bytes, fileName, sidecar, warnings)
            "dae" -> dae(text(), warnings)
            "fbx" -> fbx(text(), warnings)
            else -> {
                warnings += ".$fmt models can't be converted directly — exported as a raw project asset. " +
                    "Re-export from your 3D app as OBJ, glTF (.glb) or FBX ASCII for full editing support."
                return Result(emptyList(), warnings, fmt)
            }
        }
        val kept = parts.filter { it.faces.isNotEmpty() && it.verts.isNotEmpty() }
        if (kept.isEmpty()) throw IllegalArgumentException("'$fileName' contains no polygon geometry")
        return Result(kept, warnings, fmt)
    }

    /** Builds S Engine parts (name-safe, compacted, finite-checked). */
    fun toParts(result: Result): List<SPart> = result.parts.mapIndexed { i, mp ->
        val p = SPart(if (mp.name.isBlank()) "Part ${i + 1}" else mp.name)
        p.color = mp.color; p.smooth = mp.smooth
        for (v in mp.verts) if (v.all { it.isFinite() }) p.verts.add(floatArrayOf(v[0], v[1], v[2]))
        for ((fi, f) in mp.faces.withIndex()) {
            val idx = f.filter { it in p.verts.indices }.toSortedSet().toIntArray()
            if (idx.size >= 3) { p.faces.add(idx); p.faceColors.add(mp.faceColors.getOrElse(fi) { 0 }); p.uvs.add(mp.uvs.getOrElse(fi) { null }) }
        }
        p.fixColors()
        p
    }.filter { it.faces.isNotEmpty() }

    // ====================================================================== OBJ
    private fun obj(text: String, mtlName: String, sidecar: (String) -> ByteArray?, warnings: ArrayList<String>): ArrayList<MPart> {
        val parts = ArrayList<MPart>()
        var cur = MPart("Imported")
        fun push() { if (cur.faces.isNotEmpty()) parts.add(cur) }
        val verts = ArrayList<FloatArray>(); val uvs = ArrayList<FloatArray>()
        val materials = HashMap<String, Int>()
        var useMtl = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("v ") -> {
                    val t = line.split(Regex("\\s+"))
                    if (t.size >= 4) verts.add(floatArrayOf(t[1].toFloatOrNull() ?: 0f, t[2].toFloatOrNull() ?: 0f, t[3].toFloatOrNull() ?: 0f))
                }
                line.startsWith("vt ") -> {
                    val t = line.split(Regex("\\s+"))
                    if (t.size >= 3) uvs.add(floatArrayOf(t[1].toFloatOrNull() ?: 0f, t[2].toFloatOrNull() ?: 1f))
                }
                line.startsWith("usemtl ") -> {
                    useMtl = line.substring(7).trim()
                    if (materials.containsKey(useMtl)) { push(); cur = MPart(useMtl).also { it.color = materials[useMtl]!! } }
                    else { cur.name = useMtl.ifBlank { cur.name } }
                }
                line.startsWith("mtllib") -> {
                    val lib = line.split(Regex("\\s+")).getOrNull(1) ?: continue
                    val bytes = sidecar(lib) ?: sidecar(lib.substringAfterLast('/')) ?: continue
                    parseMtl(String(bytes, StandardCharsets.UTF_8), materials, warnings)
                    materials[useMtl]?.let { c -> push(); cur = MPart(useMtl).also { it.color = c } }
                }
                line.startsWith("o ") || line.startsWith("g ") -> {
                    val n = line.substring(2).trim().ifBlank { "Part" }
                    if (cur.faces.isNotEmpty() && cur.name != n) { push(); cur = MPart(n).also { if (useMtl.isNotBlank()) it.color = materials[useMtl] ?: it.color } }
                    else cur.name = n
                }
                line.startsWith("f ") -> {
                    val t = line.split(Regex("\\s+")).drop(1)
                    val face = ArrayList<Int>(); val fu = ArrayList<FloatArray?>()
                    for (tok in t) {
                        val seg = tok.split('/')
                        val vi = seg[0].toIntOrNull() ?: continue
                        face.add(if (vi < 0) verts.size + vi else vi - 1)
                        fu.add(seg.getOrNull(1)?.toIntOrNull()?.let { if (it < 0) uvs.getOrNull(uvs.size + it) else uvs.getOrNull(it - 1) })
                    }
                    if (face.size >= 3) { cur.faces.add(face.toIntArray()); cur.faceColors.add(0); cur.uvs.add(if (fu.any { it != null }) flattenUv(face.size, fu) else null) }
                }
            }
        }
        push()
        if (parts.isEmpty()) throw IllegalArgumentException("OBJ has no faces")
        if (parts.size > 1) parts.forEachIndexed { i, p -> if (p.name.isBlank() || parts.count { it.name == p.name } > 1) p.name = "${p.name.ifBlank { "Part" }} ${i + 1}" }
        if (mtlName.isNotBlank() && materials.isEmpty()) warnings += "No .mtl material file found next to the OBJ — parts use the default colour. Import the .mtl together with the .obj (folder import) for colours."
        for (p in parts) { p.fixUvs() }
        return parts
    }

    private fun flattenUv(n: Int, fu: List<FloatArray?>): FloatArray {
        val out = FloatArray(n * 2)
        for (i in 0 until n) { val u = fu[i]; out[i * 2] = u?.get(0) ?: 0f; out[i * 2 + 1] = u?.get(1) ?: 0f }
        return out
    }

    private fun parseMtl(text: String, out: HashMap<String, Int>, warnings: ArrayList<String>) {
        var name = ""
        var r = 0.8f; var g = 0.8f; var b = 0.8f
        fun commit() { if (name.isNotBlank()) out[name] = rgb(r, g, b) }
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("newmtl ") -> { commit(); name = line.substring(7).trim(); r = 0.8f; g = 0.8f; b = 0.8f }
                line.startsWith("Kd ") -> {
                    val t = line.split(Regex("\\s+"))
                    if (t.size >= 4) { r = t[1].toFloatOrNull() ?: r; g = t[2].toFloatOrNull() ?: g; b = t[3].toFloatOrNull() ?: b }
                }
            }
        }
        commit()
    }

    private fun rgb(r: Float, g: Float, b: Float): Int {
        fun ch(v: Float) = (v.coerceIn(0f, 1f) * 255f).toInt()
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    private fun MPart.fixUvs() {
        if (!uvs.any { it != null }) return
        for (i in uvs.indices) if (uvs[i] != null && uvs[i]!!.size != faces[i].size * 2) uvs[i] = null
    }

    // ====================================================================== STL
    private fun stl(bytes: ByteArray, warnings: ArrayList<String>): ArrayList<MPart> {
        val tris = ArrayList<Tri>()
        val head = String(bytes, 0, minOf(bytes.size, 84), StandardCharsets.US_ASCII)
        if (head.trim().startsWith("solid") && !isBinaryStl(bytes)) {
            var v = ArrayList<FloatArray>(3)
            for (raw in String(bytes, StandardCharsets.UTF_8).lineSequence()) {
                val line = raw.trim()
                if (line.startsWith("vertex")) {
                    val t = line.split(Regex("\\s+"))
                    if (t.size >= 4) v.add(floatArrayOf(t[1].toFloatOrNull() ?: 0f, t[2].toFloatOrNull() ?: 0f, t[3].toFloatOrNull() ?: 0f))
                    if (v.size == 3) { tris.add(Tri(v[0], v[1], v[2])); v = ArrayList(3) }
                }
            }
        } else {
            val d = DataInputStream(ByteArrayInputStream(bytes))
            d.skipBytes(80)
            val n = readLeInt(d)
            if (n <= 0 || 84L + 50L * n > bytes.size) throw IllegalArgumentException("Broken binary STL (claims $n triangles)")
            repeat(n) {
                d.skipBytes(12)
                val a = floatArrayOf(readLeFloat(d), readLeFloat(d), readLeFloat(d))
                val b = floatArrayOf(readLeFloat(d), readLeFloat(d), readLeFloat(d))
                val c = floatArrayOf(readLeFloat(d), readLeFloat(d), readLeFloat(d))
                tris.add(Tri(a, b, c))
                d.skipBytes(2)
            }
        }
        if (tris.isEmpty()) throw IllegalArgumentException("STL contains no triangles")
        val p = MPart("STL Mesh")
        weld(p, tris.size * 3, tris.map { listOf(it.a, it.b, it.c) })
        return arrayListOf(p)
    }

    private fun isBinaryStl(bytes: ByteArray): Boolean {
        if (bytes.size < 84) return false
        val n = ((bytes[84 - 4].toInt() and 0xFF)) or ((bytes[84 - 3].toInt() and 0xFF) shl 8) or
            ((bytes[84 - 2].toInt() and 0xFF) shl 16) or ((bytes[84 - 1].toInt() and 0xFF) shl 24)
        return 84 + 50L * n == bytes.size.toLong()
    }

    private fun readLeFloat(d: DataInputStream): Float = Float.fromBits(readLeInt(d))
    private fun readLeInt(d: DataInputStream): Int {
        val b1 = d.read(); val b2 = d.read(); val b3 = d.read(); val b4 = d.read()
        if (b4 < 0) throw IllegalArgumentException("Unexpected end of file")
        return b1 or (b2 shl 8) or (b3 shl 16) or (b4 shl 24)
    }

    private class Tri(val a: FloatArray, val b: FloatArray, val c: FloatArray)

    /** Welds a soup of triangles into shared vertices (fast quantised hash) and appends faces to [p]. */
    private fun weld(p: MPart, approxVerts: Int, soup: List<List<FloatArray>>) {
        val map = HashMap<Long, Int>(approxVerts)
        fun vert(v: FloatArray): Int {
            val key = (Math.round(v[0] * 100000f).toLong() * 73856093L) xor (Math.round(v[1] * 100000f).toLong() * 19349663L) xor (Math.round(v[2] * 100000f).toLong() * 83492791L)
            val got = map[key]
            if (got != null && got in p.verts.indices && near(p.verts[got], v)) return got
            p.verts.add(floatArrayOf(v[0], v[1], v[2]))
            map[key] = p.verts.size - 1
            return p.verts.size - 1
        }
        for (t in soup) p.faces.add(t.map { vert(it) }.toIntArray()).also { p.faceColors.add(0); p.uvs.add(null) }
    }

    private fun near(a: FloatArray, b: FloatArray) = abs(a[0] - b[0]) < 1e-5f && abs(a[1] - b[1]) < 1e-5f && abs(a[2] - b[2]) < 1e-5f

    // ====================================================================== PLY
    private fun ply(bytes: ByteArray, warnings: ArrayList<String>): ArrayList<MPart> {
        val text = String(bytes, 0, minOf(bytes.size, 65536), StandardCharsets.US_ASCII)
        val headerEnd = text.indexOf("end_header")
        require(headerEnd > 0 && text.contains("ply")) { "Not a valid PLY file" }
        val header = text.substring(0, headerEnd)
        val binary = header.contains("binary_little_endian")
        val bigEndian = header.contains("binary_big_endian")
        val vProps = ArrayList<Pair<String, Int>>(); var vCount = 0
        val fProps = ArrayList<Pair<String, Int>>(); var fCount = 0
        var curElement = ""
        for (line in header.lines()) {
            val t = line.trim().split(Regex("\\s+"))
            when (t.getOrNull(0)) {
                "element" -> { curElement = t.getOrElse(1) { "" }; if (curElement == "vertex") vCount = t.getOrElse(2) { "0" }.toIntOrNull() ?: 0; if (curElement == "face") fCount = t.getOrElse(2) { "0" }.toIntOrNull() ?: 0 }
                "property" -> {
                    if (curElement == "vertex" && t.getOrNull(1) != "list") vProps.add((t.getOrElse(2) { "" }) to typeSize(t[1]))
                    if (curElement == "face" && t.getOrNull(1) == "list") fProps.add((t.getOrElse(3) { "" }) to -1)
                }
            }
        }
        val p = MPart("PLY Mesh")
        val soup = ArrayList<List<FloatArray>>()
        val colors = ArrayList<Int?>()
        if (binary) {
            val binStart = text.indexOf('\n', headerEnd).let { if (it < 0) headerEnd + 10 else it + 1 }
            val d = DataInputStream(ByteArrayInputStream(bytes, binStart.coerceAtMost(bytes.size), bytes.size))
            repeat(vCount) {
                var x = 0f; var y = 0f; var z = 0f; var col: Int? = null
                for ((name, size) in vProps) {
                    when (name) {
                        "x" -> x = readPlyFloat(d, size, bigEndian); "y" -> y = readPlyFloat(d, size, bigEndian); "z" -> z = readPlyFloat(d, size, bigEndian)
                        "red" -> { val r = d.read(); val g = d.read(); val b = d.read(); col = (0xFF shl 24) or (r shl 16) or (g shl 8) or b }
                        else -> if (size > 0) repeat(size) { d.read() }
                    }
                }
                colors.add(col)
                soup.add(listOf(floatArrayOf(x, y, z)))
            }
            repeat(fCount) {
                for ((name, size) in fProps) {
                    val cnt = d.read()
                    if (name == "vertex_indices" || name == "vertex_index") {
                        val idx = IntArray(cnt) { if (cnt >= 0) readPlyIndex(d, bigEndian) else 0 }
                        if (idx.size >= 3) { p.faces.add(idx.toList()); p.faceColors.add(0); p.uvs.add(null) }
                    } else repeat(cnt.coerceAtLeast(0) * 1) { d.read() }
                }
            }
        } else {
            val lines = text.substring(headerEnd + 10).lines().iterator()
            var vi = 0
            while (vi < vCount && lines.hasNext()) {
                val t = lines.next().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                if (t.isEmpty()) continue
                var x = 0f; var y = 0f; var z = 0f; var col: Int? = null
                for ((j, prop) in vProps.withIndex()) {
                    val v = t.getOrNull(j)?.toFloatOrNull()
                    when (prop.first) {
                        "x" -> x = v ?: 0f; "y" -> y = v ?: 0f; "z" -> z = v ?: 0f
                        "red" -> {
                            val r = (t.getOrNull(j)?.toFloatOrNull() ?: 0f).toInt(); val g = (t.getOrNull(j + 1)?.toFloatOrNull() ?: 0f).toInt(); val b = (t.getOrNull(j + 2)?.toFloatOrNull() ?: 0f).toInt()
                            col = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
                        }
                    }
                }
                colors.add(col); soup.add(listOf(floatArrayOf(x, y, z))); vi++
            }
            while (lines.hasNext() && p.faces.size < fCount.coerceAtLeast(0)) {
                val t = lines.next().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                if (t.size < 4) continue
                val n = t[0].toIntOrNull() ?: continue
                if (n >= 3) { p.faces.add((1..n).mapNotNull { t.getOrNull(it)?.toIntOrNull() }); p.faceColors.add(0); p.uvs.add(null) }
            }
        }
        if (p.faces.isNotEmpty()) {
            // rebuild vertices only from used indices
            val used = p.faces.flatten().distinct().sorted()
            val remap = HashMap<Int, Int>()
            for (u in used) remap[u] = p.verts.size.also { p.verts.add(soup.getOrNull(u)?.get(0) ?: floatArrayOf(0f, 0f, 0f)) }
            p.faces = ArrayList(p.faces.map { f -> f.mapNotNull { remap[it] }.toIntArray() })
            p.fixColors()
            if (colors.any { it != null }) {
                val perVert = HashMap<Int, Int>()
                for (f in p.faces) {
                    val c = colors.getOrNull(f.getOrElse(0) { 0 }) ?: colors.getOrNull(f.getOrElse(1) { 0 })
                    if (c != null) perVert[f[0]] = c
                }
                if (perVert.isNotEmpty()) {
                    for ((i, f) in p.faces.withIndex()) p.faceColors[i] = f.firstNotNullOfOrNull { perVert[it] } ?: 0
                }
            }
            return arrayListOf(p)
        }
        throw IllegalArgumentException("PLY contains no faces")
    }

    private fun typeSize(t: String) = when (t) {
        "char", "int8", "uchar", "uint8" -> 1
        "short", "int16", "ushort", "uint16" -> 2
        "int", "int32", "uint", "uint32", "float", "float32" -> 4
        "double", "float64" -> 8
        else -> 0
    }

    private fun readPlyFloat(d: DataInputStream, size: Int, big: Boolean): Float = when (size) {
        4 -> if (big) java.lang.Float.intBitsToFloat(d.readInt()) else readLeFloat(d)
        8 -> d.readDouble().toFloat()
        else -> d.read() / 255f
    }

    private fun readPlyIndex(d: DataInputStream, big: Boolean): Int = when {
        big -> d.readInt()
        else -> readLeInt(d)
    }

    // ====================================================================== glTF / GLB
    private fun glb(bytes: ByteArray, fileName: String, sidecar: (String) -> ByteArray?, warnings: ArrayList<String>): JSONObject {
        require(bytes.size > 20 && String(bytes, 0, 4, StandardCharsets.US_ASCII) == "glTF") { "Not a GLB file" }
        var off = 12
        var json: JSONObject? = null
        var bin: ByteArray? = null
        while (off + 8 <= bytes.size) {
            val len = ((bytes[off].toInt() and 0xFF)) or ((bytes[off + 1].toInt() and 0xFF) shl 8) or
                ((bytes[off + 2].toInt() and 0xFF) shl 16) or ((bytes[off + 3].toInt() and 0xFF) shl 24)
            val type = String(bytes, off + 4, 4, StandardCharsets.US_ASCII)
            val end = minOf(bytes.size, off + 8 + len)
            if (type == "JSON") json = JSONObject(String(bytes, off + 8, len.coerceAtMost(bytes.size - off - 8), StandardCharsets.UTF_8))
            else if (type == "BIN\u0000\u0000" || type == "BIN") bin = bytes.copyOfRange(off + 8, end)
            off = end
        }
        require(json != null) { "GLB has no JSON chunk" }
        return gltf(json!!, fileName, sidecar, warnings, bin)
    }

    private fun gltf(root: JSONObject, fileName: String, sidecar: (String) -> ByteArray?, warnings: ArrayList<String>, binChunk: ByteArray? = null): ArrayList<MPart> {
        val buffers = ArrayList<ByteArray>()
        for (b in root.optJSONArray("buffers") ?: JSONArray()) {
            val o = b as JSONObject
            val uri = o.optString("uri", "")
            buffers.add(when {
                uri.startsWith("data:") -> Base64.getDecoder().decode(uri.substringAfter("base64,"))
                uri.isNotBlank() -> sidecar(uri) ?: sidecar(uri.substringAfterLast('/')) ?: throw IllegalArgumentException("glTF buffer '$uri' is missing — import the whole folder")
                binChunk != null -> binChunk
                else -> ByteArray(0)
            })
        }
        fun accessor(accI: Int): Pair<Int, Int> {
            val a = (root.optJSONArray("accessors") ?: JSONArray()).optJSONObject(accI) ?: return 0 to 0
            val bv = (root.optJSONArray("bufferViews") ?: JSONArray()).optJSONObject(a.optInt("bufferView", 0)) ?: return 0 to 0
            val off = bv.optInt("byteOffset", 0) + a.optInt("byteOffset", 0)
            return off to a.optInt("count", 0)
        }
        val parts = ArrayList<MPart>()
        val meshes = root.optJSONArray("meshes") ?: JSONArray()
        val nodes = root.optJSONArray("nodes") ?: JSONArray()
        val scene = root.optJSONArray("scenes")?.optJSONObject(root.optInt("scene", 0))
        val roots = (scene?.optJSONArray("nodes") ?: JSONArray()).let { a -> (0 until a.length()).map { a.getInt(it) } }
        val visited = HashSet<Int>()
        fun nodeMatrix(i: Int, parent: FloatArray): FloatArray {
            val n = nodes.optJSONObject(i) ?: return parent
            val local = FloatArray(16)
            if (n.has("matrix")) { val m = n.getJSONArray("matrix"); for (k in 0 until 16) local[k] = m.optDouble(k).toFloat() }
            else {
                val t = n.optJSONArray("translation"); val r = n.optJSONArray("rotation"); val s = n.optJSONArray("scale")
                trs(local, t?.optDouble(0)?.toFloat() ?: 0f, t?.optDouble(1)?.toFloat() ?: 0f, t?.optDouble(2)?.toFloat() ?: 0f,
                    r?.optDouble(0)?.toFloat() ?: 0f, r?.optDouble(1)?.toFloat() ?: 0f, r?.optDouble(2)?.toFloat() ?: 0f, r?.optDouble(3)?.toFloat() ?: 1f,
                    s?.optDouble(0)?.toFloat() ?: 1f, s?.optDouble(1)?.toFloat() ?: 1f, s?.optDouble(2)?.toFloat() ?: 1f)
            }
            return mul16(parent, local)
        }
        fun walk(i: Int, parent: FloatArray) {
            if (!visited.add(i)) return
            val n = nodes.optJSONObject(i) ?: return
            val m = nodeMatrix(i, parent)
            val mi = n.optInt("mesh", -1)
            if (mi >= 0) {
                val mesh = meshes.optJSONObject(mi) ?: return
                val prims = mesh.optJSONArray("primitives") ?: return
                for (pi in 0 until prims.length()) {
                    val prim = prims.optJSONObject(pi) ?: continue
                    val attrs = prim.optJSONObject("attributes") ?: continue
                    val (pOff, pCount) = accessor(attrs.optInt("POSITION", -1))
                    if (pCount <= 0) continue
                    val buf = buffers.getOrElse(bvBuffer(root, attrs.optInt("POSITION", -1))) { ByteArray(0) }
                    val mp = MPart(mesh.optString("name", "Mesh").ifBlank { "Mesh ${parts.size + 1}" } + if (prims.length() > 1) " ${pi + 1}" else "")
                    // material colour
                    val matI = prim.optInt("material", -1)
                    if (matI >= 0) {
                        val mat = (root.optJSONArray("materials") ?: JSONArray()).optJSONObject(matI)
                        val c = mat?.optJSONObject("pbrMetallicRoughness")?.optJSONArray("baseColorFactor")
                        if (c != null && c.length() >= 3) mp.color = rgb(c.optDouble(0).toFloat(), c.optDouble(1).toFloat(), c.optDouble(2).toFloat())
                    }
                    var x = 0f; var y = 0f; var z = 0f
                    val pos = ArrayList<FloatArray>(pCount)
                    for (k in 0 until pCount) {
                        val o = pOff + k * 12
                        x = f32(buf, o); y = f32(buf, o + 4); z = f32(buf, o + 8)
                        pos.add(transform(m, x, y, z))
                    }
                    val idxAcc = prim.optInt("indices", -1)
                    if (idxAcc >= 0) {
                        val (iOff, iCount) = accessor(idxAcc)
                        val ibuf = buffers.getOrElse(bvBuffer(root, idxAcc)) { ByteArray(0) }
                        val comp = (root.optJSONArray("accessors") ?: JSONArray()).optJSONObject(idxAcc)?.optString("componentType", "5123") ?: "5123"
                        val step = if (comp == "5125") 4 else if (comp == "5123") 2 else 1
                        var face = ArrayList<Int>()
                        for (k in 0 until iCount) {
                            val v = when (step) { 4 -> i32(ibuf, iOff + k * 4); 2 -> u16(ibuf, iOff + k * 2); else -> ibuf.getOrElse(iOff + k) { 0 }.toInt() and 0xFF }
                            face.add(v)
                            if (face.size == 3) { mp.faces.add(face.toIntArray()); mp.faceColors.add(0); mp.uvs.add(null); face = ArrayList() }
                        }
                    } else {
                        var face = ArrayList<Int>()
                        for (k in pos.indices) {
                            face.add(k)
                            if (face.size == 3) { mp.faces.add(face.toIntArray()); mp.faceColors.add(0); mp.uvs.add(null); face = ArrayList() }
                        }
                    }
                    mp.verts.addAll(pos)
                    parts.add(mp)
                }
            }
            for (k in n.optJSONArray("children") ?: JSONArray()) walk(k as Int, m)
        }
        for (r in roots) walk(r, identity())
        if (parts.isEmpty()) { // meshes not referenced by a scene
            for (mi in 0 until meshes.length()) {
                val mesh = meshes.optJSONObject(mi) ?: continue
                val prims = mesh.optJSONArray("primitives") ?: continue
                for (pi in 0 until prims.length()) {
                    val prim = prims.optJSONObject(pi) ?: continue
                    val attrs = prim.optJSONObject("attributes") ?: continue
                    val (pOff, pCount) = accessor(attrs.optInt("POSITION", -1))
                    if (pCount <= 0) continue
                    val buf = buffers.getOrElse(bvBuffer(root, attrs.optInt("POSITION", -1))) { ByteArray(0) }
                    val mp = MPart(mesh.optString("name", "Mesh ${mi + 1}"))
                    for (k in 0 until pCount) mp.verts.add(floatArrayOf(f32(buf, pOff + k * 12), f32(buf, pOff + k * 12 + 4), f32(buf, pOff + k * 12 + 8)))
                    val idxAcc = prim.optInt("indices", -1)
                    if (idxAcc >= 0) {
                        val (iOff, iCount) = accessor(idxAcc)
                        val ibuf = buffers.getOrElse(bvBuffer(root, idxAcc)) { ByteArray(0) }
                        val comp = (root.optJSONArray("accessors") ?: JSONArray()).optJSONObject(idxAcc)?.optString("componentType", "5123") ?: "5123"
                        val step = if (comp == "5125") 4 else if (comp == "5123") 2 else 1
                        var face = ArrayList<Int>()
                        for (k in 0 until iCount) {
                            val v = when (step) { 4 -> i32(ibuf, iOff + k * 4); 2 -> u16(ibuf, iOff + k * 2); else -> ibuf.getOrElse(iOff + k) { 0 }.toInt() and 0xFF }
                            face.add(v)
                            if (face.size == 3) { mp.faces.add(face.toIntArray()); mp.faceColors.add(0); mp.uvs.add(null); face = ArrayList() }
                        }
                    }
                    parts.add(mp)
                }
            }
            warnings += "glTF has no scene graph — meshes were imported at their raw positions."
        }
        return parts
    }

    private fun bvBuffer(root: JSONObject, accI: Int): Int {
        val a = (root.optJSONArray("accessors") ?: JSONArray()).optJSONObject(accI) ?: return 0
        val bv = (root.optJSONArray("bufferViews") ?: JSONArray()).optJSONObject(a.optInt("bufferView", 0)) ?: return 0
        return bv.optInt("buffer", 0)
    }

    private fun f32(b: ByteArray, o: Int): Float = if (o + 4 > b.size) 0f else Float.fromBits(
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24))

    private fun i32(b: ByteArray, o: Int): Int = if (o + 4 > b.size) 0 else
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun u16(b: ByteArray, o: Int): Int = if (o + 2 > b.size) 0 else (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    private fun mul16(a: FloatArray, b: FloatArray): FloatArray {
        val o = FloatArray(16)
        for (col in 0 until 4) for (row in 0 until 4)
            o[col * 4 + row] = a[row] * b[col * 4] + a[4 + row] * b[col * 4 + 1] + a[8 + row] * b[col * 4 + 2] + a[12 + row] * b[col * 4 + 3]
        return o
    }

    private fun transform(m: FloatArray, x: Float, y: Float, z: Float): FloatArray = floatArrayOf(
        m[0] * x + m[4] * y + m[8] * z + m[12],
        m[1] * x + m[5] * y + m[9] * z + m[13],
        m[2] * x + m[6] * y + m[10] * z + m[14])

    private fun trs(o: FloatArray, tx: Float, ty: Float, tz: Float, qx: Float, qy: Float, qz: Float, qw: Float, sx: Float, sy: Float, sz: Float) {
        val x2 = qx + qx; val y2 = qy + qy; val z2 = qz + qz
        val xx = qx * x2; val xy = qx * y2; val xz = qx * z2
        val yy = qy * y2; val yz = qy * z2; val zz = qz * z2
        val wx = qw * x2; val wy = qw * y2; val wz = qw * z2
        o[0] = (1 - (yy + zz)) * sx; o[1] = (xy + wz) * sx; o[2] = (xz - wy) * sx; o[3] = 0f
        o[4] = (xy - wz) * sy; o[5] = (1 - (xx + zz)) * sy; o[6] = (yz + wx) * sy; o[7] = 0f
        o[8] = (xz + wy) * sz; o[9] = (yz - wx) * sz; o[10] = (1 - (xx + yy)) * sz; o[11] = 0f
        o[12] = tx; o[13] = ty; o[14] = tz; o[15] = 1f
    }

    // ====================================================================== Collada (.dae)
    private fun dae(text: String, warnings: ArrayList<String>): ArrayList<MPart> {
        val doc = TinyXml.parse(text)
        val sources = HashMap<String, FloatArray>()
        for (g in doc.all("library_geometries", "geometry")) {
            for (src in g.all("mesh", "source")) {
                val id = src.attr("id") ?: continue
                val arr = src.child("float_array")?.text ?: continue
                sources[id] = FloatArray(arr.trim().split(Regex("\\s+")).size) { i ->
                    arr.trim().split(Regex("\\s+")).getOrNull(i)?.toFloatOrNull() ?: 0f
                }
            }
        }
        val parts = ArrayList<MPart>()
        val zUp = doc.child("asset")?.child("up_axis")?.text?.contains("Z_UP") == true
        for (g in doc.all("library_geometries", "geometry")) {
            val mesh = g.child("mesh") ?: continue
            val vsrc = mesh.child("vertices")?.inputs()?.firstOrNull { it.semantic == "POSITION" }?.source ?: continue
            val vertsRaw = sources[vsrc] ?: continue
            fun tris(el: TinyXml.El, semanticOffsets: Map<String, Int>, p: List<Int>): List<List<Int>> {
                val stride = (semanticOffsets.values.maxOrNull() ?: -1) + 1
                val vOff = semanticOffsets["VERTEX"] ?: 0
                val out = ArrayList<List<Int>>()
                if (el.name == "triangles") {
                    var k = 0
                    while (k + stride * 3 <= p.size) {
                        out.add(listOf(p[k + vOff], p[k + stride + vOff], p[k + stride * 2 + vOff]))
                        k += stride * 3
                    }
                } else {
                    val vcount = el.child("vcount")?.text?.trim()?.split(Regex("\\s+"))?.map { it.toIntOrNull() ?: 0 } ?: emptyList()
                    var idx = 0
                    for (n in vcount) {
                        if (n >= 3) out.add((0 until n).map { p[idx + it * stride + vOff] })
                        idx += n * stride
                    }
                }
                return out
            }
            for (primName in listOf("triangles", "polylist")) {
                for (prim in mesh.all(primName)) {
                    val inputs = prim.inputs()
                    val offsets = inputs.associate { it.semantic to it.offset }
                    val pArr = prim.child("p")?.text?.trim()?.split(Regex("\\s+"))?.map { it.toIntOrNull() ?: 0 } ?: continue
                    val faces = tris(prim, offsets, pArr)
                    val mp = MPart(g.attr("name")?.substringAfter('#')?.ifBlank { null } ?: g.attr("id")?.ifBlank { null } ?: "Mesh ${parts.size + 1}")
                    for (f in faces) {
                        val idx = IntArray(f.size) { k -> mp.verts.size.also {
                            val vi = f[k]
                            var x = vertsRaw.getOrElse(vi * 3) { 0f }; var y = vertsRaw.getOrElse(vi * 3 + 1) { 0f }; var z = vertsRaw.getOrElse(vi * 3 + 2) { 0f }
                            if (zUp) { val t = y; y = z; z = -t }
                            mp.verts.add(floatArrayOf(x, y, z))
                        } }
                        mp.faces.add(idx); mp.faceColors.add(0); mp.uvs.add(null)
                    }
                    parts.add(mp)
                }
            }
        }
        if (parts.isEmpty()) throw IllegalArgumentException("Collada file has no triangle/polygon geometry")
        warnings += "Collada imported (materials/animations are not converted — geometry only)."
        return parts
    }

    // ====================================================================== FBX (ASCII)
    private fun fbx(text: String, warnings: ArrayList<String>): ArrayList<MPart> {
        if (text.contains("Kaydara FBX Binary")) throw IllegalArgumentException("This is a binary FBX — in Blender/Maya export as 'FBX ASCII' or as OBJ/glTF to import it.")
        val parts = ArrayList<MPart>()
        val geoBlocks = splitFbxGeometries(text)
        for ((i, block) in geoBlocks.withIndex()) {
            val verts = parseFbxArray(block, "Vertices") ?: continue
            val pvIdx = parseFbxArray(block, "PolygonVertexIndex") ?: continue
            val mp = MPart("FBX Mesh ${i + 1}")
            var face = ArrayList<Int>()
            for (v in pvIdx) {
                if (v < 0) { face.add(-v - 1); if (face.size >= 3) { mp.faces.add(face.toIntArray()); mp.faceColors.add(0); mp.uvs.add(null) }; face = ArrayList() }
                else face.add(v)
            }
            var k = 0
            while (k + 2 < verts.size) { mp.verts.add(floatArrayOf(verts[k], verts[k + 1], verts[k + 2])); k += 3 }
            if (mp.faces.isNotEmpty()) parts.add(mp)
        }
        if (parts.isEmpty()) throw IllegalArgumentException("FBX has no mesh geometry (cameras/lights only?)")
        warnings += "FBX ASCII imported (geometry only — no skeletons/materials)."
        return parts
    }

    private fun splitFbxGeometries(text: String): List<String> {
        val out = ArrayList<String>()
        val keys = Regex("(Geometry:)\\s*\\d+").findAll(text).map { it.range.first }.toList()
        for ((i, start) in keys.withIndex()) {
            val end = keys.getOrNull(i + 1) ?: text.length
            out.add(text.substring(start, end))
        }
        return out
    }

    private fun parseFbxArray(block: String, key: String): FloatArray? {
        val m = Regex("$key:\\s*\\*(\\d+)\\s*\\{\\s*a:\\s*([0-9eE+\\-.,_\\s]+)", RegexOption.DOT_MATCHES_ALL).find(block) ?: return null
        val body = m.groupValues[2].replace(",", " ")
        val parts = body.trim().split(Regex("\\s+"))
        return FloatArray(parts.size) { parts[it].toFloatOrNull() ?: 0f }
    }
}

/** Minimal dependency-free XML reader — just enough for Collada files (elements, attributes, text). */
object TinyXml {
    class El(val name: String) {
        val attrs = HashMap<String, String>()
        val children = ArrayList<El>()
        var text = ""
        fun child(name: String): El? = children.firstOrNull { it.name == name }
        fun all(vararg path: String): List<El> {
            if (path.isEmpty()) return listOf(this)
            val out = ArrayList<El>()
            for (c in children) if (c.name == path[0]) out.addAll(c.all(*path.copyOfRange(1, path.size)))
            return out
        }
        fun attr(k: String): String? = attrs[k]
        fun inputs(): List<Input> {
            val out = ArrayList<Input>()
            var offset = 0
            for (c in children) when {
                c.name == "input" -> {
                    val off = c.attr("offset")?.toIntOrNull() ?: offset
                    out.add(Input(c.attr("semantic") ?: "", (c.attr("source") ?: "").removePrefix("#"), off, c.attr("set")?.toIntOrNull() ?: 0))
                    offset = off + 1
                }
            }
            return out
        }
    }
    class Input(val semantic: String, val source: String, val offset: Int, val set: Int) {
        val sourceId get() = source.substringAfterLast('/')
    }

    fun parse(text: String): El {
        val root = El("root"); val stack = ArrayDeque<El>(); stack.addLast(root)
        var i = 0
        val n = text.length
        val buf = StringBuilder()
        fun flushText() {
            if (buf.isNotBlank()) stack.lastOrNull()?.let { if (it !== root) it.text = it.text + buf.toString().trim() }
            buf.clear()
        }
        while (i < n) {
            val c = text[i]
            if (c == '<') {
                flushText()
                val end = text.indexOf('>', i)
                if (end < 0) break
                var tag = text.substring(i + 1, end)
                i = end + 1
                if (tag.startsWith("!--")) { i = text.indexOf("-->", i).let { if (it < 0) n else it + 3 }; continue }
                if (tag.startsWith("![CDATA[")) { val e = text.indexOf("]]>", i); if (e >= 0) { buf.append(text, i, e); i = e + 3 }; continue }
                if (tag.startsWith("?")) continue
                val closing = tag.startsWith("/")
                if (closing) { if (stack.size > 1) stack.removeLast(); continue }
                if (tag.endsWith("/")) { tag = tag.removeSuffix("/"); val el = parseTag(tag); stack.lastOrNull()?.children?.add(el) }
                else { val el = parseTag(tag); stack.lastOrNull()?.children?.add(el); stack.addLast(el) }
            } else { buf.append(c); i++ }
        }
        return root
    }

    private fun parseTag(tag: String): El {
        val parts = splitAttrs(tag)
        val el = El(parts.first)
        for (a in parts.second) {
            val eq = a.indexOf('=')
            if (eq <= 0) continue
            var v = a.substring(eq + 1).trim()
            if (v.length >= 2 && (v.startsWith("\"") || v.startsWith("'"))) v = v.substring(1, v.length - 1)
            el.attrs[a.substring(0, eq).trim()] = v
        }
        return el
    }

    private fun splitAttrs(tag: String): Pair<String, List<String>> {
        val out = ArrayList<String>()
        var cur = StringBuilder(); var quote = ' '
        for (c in tag) {
            when {
                quote != ' ' -> { cur.append(c); if (c == quote) quote = ' ' }
                c == '"' || c == '\'' -> { quote = c; cur.append(c) }
                c.isWhitespace() -> { if (cur.isNotBlank()) out.add(cur.toString()); cur = StringBuilder() }
                else -> cur.append(c)
            }
        }
        if (cur.isNotBlank()) out.add(cur.toString())
        return (out.firstOrNull() ?: "") to out.drop(1)
    }
}
