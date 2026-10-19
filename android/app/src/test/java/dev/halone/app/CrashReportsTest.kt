package dev.halone.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The privacy policy promises a crash report holds no browsing data and no files. */
class CrashReportsTest {

    @Test
    fun `web addresses are removed, halone's own pages kept`() {
        val scrubbed = CrashReports.scrub(
            "  gecko: tab 3 location -> https://example.com/account?id=42 (userGesture=true)\n" +
                "  browser: could not open about:reader?url=https://news.example\n" +
                "  webview: page finished https://halone.local/settings/index.html"
        )
        assertFalse(scrubbed, scrubbed.contains("example"))
        assertTrue(scrubbed.contains("location -> <url> (userGesture=true)"))
        assertTrue(scrubbed.contains("https://halone.local/settings/index.html"))
    }

    @Test
    fun `downloaded file names and storage paths are removed`() {
        val scrubbed = CrashReports.scrub("  downloads: saved Tax return 2026.pdf (51234 bytes) to /storage/emulated/0/Download/Tax return 2026.pdf")
        assertEquals("  downloads: saved <file> (51234 bytes) to <path>", scrubbed)
    }

    @Test
    fun `glasses serial numbers are removed`() {
        assertEquals(
            "calibration from device (unit <removed>, calibrated 2024-08-31) serial=<removed>",
            CrashReports.scrub("calibration from device (unit E434X00248, calibrated 2024-08-31) serial=A00015:28:15"),
        )
    }

    @Test
    fun `a stack trace passes through untouched`() {
        val trace = "java.lang.IllegalStateException: boom\n\tat dev.halone.app.MainActivity.connect(MainActivity.kt:600)"
        assertEquals(trace, CrashReports.scrub(trace))
    }
}
