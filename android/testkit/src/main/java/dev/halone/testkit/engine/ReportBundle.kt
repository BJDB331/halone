package dev.halone.testkit.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes a [Session] as the single zip a tester sends back. */
object ReportBundle {

    const val FORMAT_VERSION = 1

    fun write(session: Session, kitVersion: String, out: OutputStream) {
        val secrets = session.secrets.map { it.toByteArray(Charsets.UTF_8) }
        ZipOutputStream(out).use { zip ->
            zip.entry("manifest.json", redact(manifest(session, kitVersion).toString(2).toByteArray(), secrets))
            for ((name, content) in session.files) {
                val bytes = when (content) {
                    is ByteArray -> content
                    is File -> if (content.exists()) content.readBytes() else continue
                    else -> continue
                }
                zip.entry(name, redact(bytes, secrets))
            }
        }
    }

    fun manifest(session: Session, kitVersion: String): JSONObject = JSONObject().apply {
        put("format", FORMAT_VERSION)
        put("kit_version", kitVersion)
        put("profile", session.profileId)
        put("started_at_wall_ms", session.startedAtWallMs)
        put("started_at_host_ns", session.startedAtNs)
        put("outcomes", JSONObject(session.outcomes as Map<*, *>))
        put("answers", JSONArray().apply {
            session.answers.forEach { a ->
                put(JSONObject().put("id", a.id).put("question", a.question).put("choice", a.choice).put("attempt", a.attempt))
            }
        })
        put("markers", JSONArray().apply {
            session.markers.forEach { m -> put(JSONObject().put("label", m.label).put("host_ns", m.hostNs)) }
        })
        put("notes", JSONArray(session.notes))
        put("files", JSONArray(session.files.keys.toList()))
        put("capture_format", CAPTURE_FORMAT)
    }

    /** Replaces each secret with `X`s of the same length, so offsets in binary captures stay valid. */
    fun redact(bytes: ByteArray, secrets: List<ByteArray>): ByteArray {
        if (secrets.isEmpty()) return bytes
        val out = bytes.copyOf()
        for (secret in secrets) {
            if (secret.isEmpty()) continue
            var i = 0
            while (i <= out.size - secret.size) {
                if (out[i] == secret[0] && matches(out, i, secret)) {
                    for (j in secret.indices) out[i + j] = 'X'.code.toByte()
                    i += secret.size
                } else {
                    i++
                }
            }
        }
        return out
    }

    private fun matches(haystack: ByteArray, at: Int, needle: ByteArray): Boolean {
        for (j in needle.indices) if (haystack[at + j] != needle[j]) return false
        return true
    }

    private fun ZipOutputStream.entry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private const val CAPTURE_FORMAT =
        "*.cap: repeated [i64 BE host_ns][u8 direction 0=in 1=out][i32 BE length][bytes]; " +
            "host_ns shares a clock with markers; concatenating direction-0 payloads gives the raw stream"
}
