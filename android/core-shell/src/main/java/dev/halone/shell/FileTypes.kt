package dev.halone.shell

/** Kept in step with `files.ts` in `@halone/sdk`, so both hosts type a file the same way. */
object FileTypes {
    private val TYPES = mapOf(
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "jps" to "image/jpeg",
        "mpo" to "image/mpo",
        "png" to "image/png",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "avif" to "image/avif",
        "bmp" to "image/bmp",
        "heic" to "image/heic",
        "heif" to "image/heif",
        "svg" to "image/svg+xml",
        "mp4" to "video/mp4",
        "m4v" to "video/mp4",
        "webm" to "video/webm",
        "mkv" to "video/x-matroska",
        "mov" to "video/quicktime",
        "3gp" to "video/3gpp",
        "ts" to "video/mp2t",
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "aac" to "audio/aac",
        "ogg" to "audio/ogg",
        "opus" to "audio/opus",
        "wav" to "audio/wav",
        "flac" to "audio/flac",
        "pdf" to "application/pdf",
        "zip" to "application/zip",
        HalonePackage.EXTENSION to HalonePackage.MIME_TYPE,
        "apk" to "application/vnd.android.package-archive",
        "json" to "application/json",
        "txt" to "text/plain",
        "md" to "text/markdown",
        "csv" to "text/csv",
        "html" to "text/html",
        "htm" to "text/html",
    )

    fun mimeTypeOf(name: String): String? {
        val dot = name.lastIndexOf('.')
        return if (dot < 0) null else TYPES[name.substring(dot + 1).lowercase()]
    }

    /** Whether [mimeType] matches a pattern: an exact type, or one with a wildcard subtype, as in `opens`. */
    fun matches(pattern: String, mimeType: String?): Boolean = when {
        mimeType == null -> false
        pattern == "*/*" -> true
        pattern.endsWith("/*") -> mimeType.startsWith(pattern.dropLast(1))
        else -> pattern == mimeType
    }
}
