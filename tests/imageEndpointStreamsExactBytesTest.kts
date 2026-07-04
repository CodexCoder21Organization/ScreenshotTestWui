@file:WithArtifact("screenshottest.wui.buildMaven()")
@file:WithArtifact("screenshottest.api:screenshottest-api:0.0.1")
@file:WithArtifact("org.eclipse.jetty:jetty-server:11.0.20")
@file:WithArtifact("org.eclipse.jetty:jetty-servlet:11.0.20")
@file:WithArtifact("org.eclipse.jetty:jetty-http:11.0.20")
@file:WithArtifact("org.eclipse.jetty:jetty-io:11.0.20")
@file:WithArtifact("org.eclipse.jetty:jetty-util:11.0.20")
@file:WithArtifact("org.eclipse.jetty:jetty-security:11.0.20")
@file:WithArtifact("jakarta.servlet:jakarta.servlet-api:5.0.0")
@file:WithArtifact("org.json:json:20250517")
@file:WithArtifact("community.kotlin.clocks.simple:community-kotlin-clocks-simple:0.0.3")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-test:1.9.22")
@file:WithArtifact("org.slf4j:slf4j-api:1.7.36")
@file:WithArtifact("org.slf4j:slf4j-simple:1.7.36")
package screenshottest.wui

import build.kotlin.withartifact.WithArtifact
import kotlin.test.*
import java.net.HttpURLConnection
import java.net.URL
import screenshottest.api.ScreenshotTestApi

/**
 * The /image endpoint must stream a produced PNG out byte-for-byte, even when it is larger than the
 * service's 1 MiB per-chunk cap — the servlet must issue multiple chunked getImageChunk reads and
 * concatenate them on the wire, holding at most one chunk in heap at a time (the WUI runs -Xmx128m).
 */
fun imageEndpointStreamsExactBytesTest() {
    // 2.5 MiB of deterministic bytes — forces at least three 1 MiB reads.
    val totalBytes = (2.5 * 1024 * 1024).toInt()
    val imageBytes = ByteArray(totalBytes) { (it % 251).toByte() }
    val cap = 1024 * 1024

    val api: ScreenshotTestApi = object : ScreenshotTestApi {
        override fun getRendererVersion(): String = "chromium-1228"
        override fun createSession(label: String, mode: String, mainClass: String, scenariosJson: String): String = throw UnsupportedOperationException()
        override fun uploadFileChunk(sessionId: String, role: String, fileName: String, chunkIndex: Int, chunk: ByteArray) = throw UnsupportedOperationException()
        override fun finalizeFile(sessionId: String, role: String, fileName: String, totalChunks: Int, sha256Hex: String) = throw UnsupportedOperationException()
        override fun startRender(sessionId: String) = throw UnsupportedOperationException()
        override fun getSessionStatus(sessionId: String): String = throw UnsupportedOperationException()
        override fun getResultsJson(sessionId: String): String = throw UnsupportedOperationException()
        override fun getImageChunk(sessionId: String, key: String, kind: String, offset: Long, length: Int): ByteArray? {
            if (sessionId != "sess-img" || key != "runs-list" || kind != "actual") return null
            if (offset >= imageBytes.size) return null
            // Honor the documented 1 MiB per-chunk cap so the test catches any assumption that a
            // single huge chunk is returned.
            val effective = if (length > cap) cap else length
            val start = offset.toInt()
            val end = minOf(start.toLong() + effective, imageBytes.size.toLong()).toInt()
            return imageBytes.copyOfRange(start, end)
        }
        override fun listSessions(): String = throw UnsupportedOperationException()
        override fun deleteSession(sessionId: String) {}
    }

    val server = createServer(0, api)
    server.start()
    try {
        val port = (server.connectors[0] as org.eclipse.jetty.server.ServerConnector).localPort
        val conn = URL("http://localhost:$port/image?id=sess-img&key=runs-list&kind=actual").openConnection() as HttpURLConnection
        assertEquals(200, conn.responseCode, "Expected HTTP 200 for a known image; got ${conn.responseCode}")
        assertEquals("image/png", conn.getHeaderField("Content-Type"), "Expected Content-Type image/png.")
        val disposition = conn.getHeaderField("Content-Disposition") ?: ""
        assertTrue(
            disposition.contains("inline") && disposition.contains("runs-list-actual.png"),
            "Expected an inline Content-Disposition naming the key+kind; got \"$disposition\"."
        )
        val received = conn.inputStream.readBytes()
        assertEquals(
            imageBytes.size, received.size,
            "Expected /image to stream all ${imageBytes.size} bytes, but got ${received.size}. Did it stop at the first chunk?"
        )
        var firstMismatch = -1
        for (i in imageBytes.indices) {
            if (received[i] != imageBytes[i]) { firstMismatch = i; break }
        }
        assertTrue(
            received.contentEquals(imageBytes),
            "Expected the streamed image to match the source byte-for-byte. First mismatch at index $firstMismatch of ${imageBytes.size}."
        )
    } finally {
        server.stop()
    }
}
