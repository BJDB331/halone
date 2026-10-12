package dev.halone.shell

import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.URLEncoder
import org.json.JSONObject

data class FileRoot(val kind: String, val name: String, val path: String) {
    fun toJson(): JSONObject = JSONObject().put("kind", kind).put("name", name).put("path", path)
}

/** Whether halone may read shared storage, and how to ask the wearer for it. */
interface StorageAccess {
    val granted: Boolean
    fun request()
}

/**
 * The phone's shared storage as `halone.files` sees it: real paths, only under
 * one of [candidates] that exists, and only once [access] is granted. Bytes are not carried
 * over the bridge; each file has a URL that a [FileServer] answers, ranges
 * included, so `<img>` and `<video>` read them directly.
 */
class SharedFiles(
    private val candidates: () -> List<FileRoot>,
    private val access: StorageAccess,
) {
    private var lastGranted: Boolean? = null

    /** Where file URLs start: a [FileServer]'s base on the phone. */
    @Volatile
    var urlBase: String = Shell.ORIGIN + URL_PREFIX

    /** Whether whoever serves [urlBase] answers `?thumbnail` for pictures, videos and the cover art of songs. */
    @Volatile
    var thumbnails: Boolean = false

    val granted: Boolean get() = access.granted

    fun requestAccess() = access.request()

    /** True when access changed since the last call, for a poller to turn into an event. */
    fun accessChanged(): Boolean {
        val now = access.granted
        val changed = lastGranted != null && lastGranted != now
        lastGranted = now
        return changed
    }

    fun roots(): List<FileRoot> {
        requireAccess()
        return candidates().filter { File(it.path).isDirectory }
    }

    fun list(path: String): List<JSONObject> {
        val directory = resolve(path)
        if (!directory.isDirectory) throw HaloneException("not-a-directory", "not a directory: $path")
        val children = directory.listFiles() ?: throw HaloneException("permission-denied", "cannot read $path")
        return children.filterNot { it.name.startsWith(".") }.map { entry(it) }
    }

    fun stat(path: String): JSONObject = entry(resolve(path))

    /** Told the paths that now hold new or changed files, so the phone's media index can catch up. */
    var onWritten: (List<String>) -> Unit = {}

    /** Copies each of [paths] into the folder [to]; a name already taken there gets a number. */
    fun copy(paths: List<String>, to: String): List<JSONObject> {
        val target = folder(to)
        val sources = paths.map(::resolve)
        sources.forEach { refuseInsideItself(it, target) }
        val copies = sources.map { source ->
            val destination = freeName(target, source.name)
            if (!source.copyRecursively(destination)) throw HaloneException("io", "could not copy ${source.path}")
            destination
        }
        onWritten(copies.flatMap(::allFiles))
        return copies.map(::entry)
    }

    /** Moves each of [paths] into the folder [to]; a name already taken there gets a number. */
    fun move(paths: List<String>, to: String): List<JSONObject> {
        val target = folder(to)
        val sources = paths.map(::resolve)
        sources.forEach { refuseInsideItself(it, target) }
        val moved = sources.map { source ->
            if (source.parentFile?.canonicalFile == target) return@map source
            val destination = freeName(target, source.name)
            val before = allFiles(source)
            if (!source.renameTo(destination)) {
                if (!source.copyRecursively(destination) || !source.deleteRecursively()) {
                    throw HaloneException("io", "could not move ${source.path}")
                }
            }
            onWritten(before)
            destination
        }
        onWritten(moved.flatMap(::allFiles))
        return moved.map(::entry)
    }

    fun rename(path: String, name: String): JSONObject {
        val source = resolve(path)
        if (roots().any { File(it.path).canonicalFile == source }) throw HaloneException("permission-denied", "a storage root cannot be renamed")
        checkName(name)
        if (name == source.name) return entry(source)
        val destination = File(source.parentFile, name)
        if (destination.exists() && !destination.path.equals(source.path, ignoreCase = true)) {
            throw HaloneException("exists", "there is already something called $name")
        }
        val before = allFiles(source)
        if (!source.renameTo(destination)) throw HaloneException("io", "could not rename ${source.path}")
        onWritten(before + allFiles(destination))
        return entry(destination)
    }

    fun delete(paths: List<String>) {
        val files = paths.map(::resolve)
        val roots = roots().map { File(it.path).canonicalFile }
        if (files.any { it in roots }) throw HaloneException("permission-denied", "a storage root cannot be deleted")
        files.forEach { file ->
            val gone = allFiles(file)
            if (!file.deleteRecursively()) throw HaloneException("io", "could not delete ${file.path}")
            onWritten(gone)
        }
    }

    fun createDirectory(parent: String, name: String): JSONObject {
        val target = folder(parent)
        checkName(name)
        val directory = File(target, name)
        if (directory.exists()) throw HaloneException("exists", "there is already something called $name")
        if (!directory.mkdir()) throw HaloneException("io", "could not create $name")
        return entry(directory)
    }

    private fun folder(path: String): File =
        resolve(path).also { if (!it.isDirectory) throw HaloneException("not-a-directory", "not a directory: $path") }

    private fun refuseInsideItself(source: File, target: File) {
        if (source.isDirectory && (target == source || target.path.startsWith(source.path + File.separator))) {
            throw HaloneException("invalid-params", "a folder cannot go inside itself: ${source.path}")
        }
    }

    private fun allFiles(file: File): List<String> =
        if (file.isDirectory) file.walkTopDown().filter { it.isFile }.map { it.path }.toList() else listOf(file.path)

    /** The file at [path], which must exist and lie under a root. */
    fun resolve(path: String): File {
        requireAccess()
        if (!path.startsWith("/")) throw HaloneException("invalid-params", "not an absolute path: $path")
        val file = File(path).canonicalFile
        val inside = roots().any { root ->
            val base = File(root.path).canonicalFile
            file == base || file.path.startsWith(base.path + File.separator)
        }
        if (!inside || !file.exists()) throw HaloneException("no-such-file", "no such file: $path")
        return file
    }

    fun entry(file: File): JSONObject {
        val directory = file.isDirectory
        return JSONObject()
            .put("path", file.path)
            .put("name", file.name)
            .put("kind", if (directory) "directory" else "file")
            .put("sizeBytes", if (directory) 0 else file.length())
            .put("modifiedAt", file.lastModified())
            .put("mimeType", if (directory) JSONObject.NULL else FileTypes.mimeTypeOf(file.name) ?: JSONObject.NULL)
            .put("url", if (directory) JSONObject.NULL else urlFor(file.path))
            .put("thumbnailUrl", thumbnailUrlFor(file) ?: JSONObject.NULL)
    }

    private fun requireAccess() {
        if (!access.granted) throw HaloneException("permission-denied", "halone has not been allowed to read shared storage")
    }

    /** Versioned by the file's modification time, so a page may cache it for as long as the file is unchanged. */
    private fun thumbnailUrlFor(file: File): String? {
        if (!thumbnails || file.isDirectory || !hasThumbnail(file.name)) return null
        return urlFor(file.path) + "?thumbnail&v=" + file.lastModified()
    }

    fun urlFor(path: String): String =
        urlBase + path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    companion object {
        const val URL_PREFIX = "/_files"

        fun checkName(name: String) {
            if (name.isBlank() || name == "." || name == ".." || '/' in name || '\u0000' in name || name.length > 255) {
                throw HaloneException("invalid-params", "not a valid name: $name")
            }
        }

        /** [name] in [directory], or the first of `name (2)`, `name (3)`… that is free, keeping the extension. */
        fun freeName(directory: File, name: String): File {
            File(directory, name).takeIf { !it.exists() }?.let { return it }
            val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
            val base = name.substring(0, dot)
            val extension = name.substring(dot)
            return generateSequence(2) { it + 1 }.map { File(directory, "$base ($it)$extension") }.first { !it.exists() }
        }

        fun hasThumbnail(name: String): Boolean =
            FileTypes.mimeTypeOf(name)?.let { it.startsWith("image/") || it.startsWith("video/") || it.startsWith("audio/") } == true

        fun respond(file: File, rangeHeader: String?): FileResponse {
            val size = file.length()
            val type = FileTypes.mimeTypeOf(file.name) ?: "application/octet-stream"
            val headers = mutableMapOf("Accept-Ranges" to "bytes")
            val range = Ranges.parse(rangeHeader, size)
            if (range == Ranges.UNSATISFIABLE) {
                headers["Content-Range"] = "bytes */$size"
                return FileResponse(416, "Range Not Satisfiable", type, headers, null)
            }
            val (start, end) = range ?: (0L to size - 1)
            val length = (end - start + 1).coerceAtLeast(0)
            headers["Content-Length"] = length.toString()
            if (range != null) headers["Content-Range"] = "bytes $start-$end/$size"
            val body = BoundedStream(FileInputStream(file).apply { skipFully(start) }, length)
            return FileResponse(
                if (range != null) 206 else 200,
                if (range != null) "Partial Content" else "OK",
                type,
                headers,
                body,
            )
        }

        private fun InputStream.skipFully(count: Long) {
            var left = count
            while (left > 0) {
                val skipped = skip(left)
                if (skipped <= 0) break
                left -= skipped
            }
        }
    }
}

data class FileResponse(
    val status: Int,
    val reason: String,
    val mimeType: String,
    val headers: Map<String, String>,
    val body: InputStream?,
)

/** One `bytes=start-end` range, which is all a media element asks for. */
object Ranges {
    val UNSATISFIABLE = -1L to -1L

    fun parse(header: String?, size: Long): Pair<Long, Long>? {
        val match = header?.trim()?.let { Regex("""^bytes=(\d*)-(\d*)$""").matchEntire(it) } ?: return null
        val (from, to) = match.destructured
        if (from.isEmpty() && to.isEmpty()) return null
        val start: Long
        val end: Long
        if (from.isEmpty()) {
            start = (size - to.toLong()).coerceAtLeast(0)
            end = size - 1
        } else {
            start = from.toLong()
            end = if (to.isEmpty()) size - 1 else minOf(to.toLong(), size - 1)
        }
        return if (start >= size || start > end) UNSATISFIABLE else start to end
    }
}

private class BoundedStream(input: InputStream, private var left: Long) : FilterInputStream(input) {
    override fun read(): Int {
        if (left <= 0) return -1
        val value = super.read()
        if (value >= 0) left--
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (left <= 0) return -1
        val count = super.read(buffer, offset, minOf(length.toLong(), left).toInt())
        if (count > 0) left -= count
        return count
    }

    override fun available(): Int = minOf(super.available().toLong(), left).toInt()
}
