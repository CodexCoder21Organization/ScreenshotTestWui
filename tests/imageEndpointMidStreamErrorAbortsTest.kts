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
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import screenshottest.api.ScreenshotTestApi

/**
 * If getImageChunk fails AFTER the first slice has been written (response already committed as a
 * chunked 200), the servlet must abort the in-flight response rather than terminate it cleanly — a
 * clean termination would hand the client a truncated-but-complete-looking PNG it cannot detect as
 * corrupt. The client must instead observe a broken stream (premature end / reset).
 */
fun imageEndpointMidStreamErrorAbortsTest() {
    val chunk = 1024 * 1024
    val firstSlice = ByteArray(chunk) { (it % 251).toByte() }
    val api: ScreenshotTestApi = object : ScreenshotTestApi {
        override fun getRendererVersion(): String = "chromium-1228"
        override fun createSession(label: String, mode: String, mainClass: String, scenariosJson: String): String = throw UnsupportedOperationException()
        override fun uploadFileChunk(sessionId: String, role: String, fileName: String, chunkIndex: Int, chunk: ByteArray) = throw UnsupportedOperationException()
        override fun finalizeFile(sessionId: String, role: String, fileName: String, totalChunks: Int, sha256Hex: String) = throw UnsupportedOperationException()
        override fun startRender(sessionId: String) = throw UnsupportedOperationException()
        override fun getSessionStatus(sessionId: String): String = throw UnsupportedOperationException()
        override fun getResultsJson(sessionId: String): String = throw UnsupportedOperationException()
        override fun getImageChunk(sessionId: String, key: String, kind: String, offset: Long, length: Int): ByteArray? {
            if (offset == 0L) return firstSlice
            // A second read (offset = 1 MiB) fails mid-stream after the first slice is on the wire.
            throw RuntimeException("backend dropped the connection mid-image")
        }
        override fun listSessions(): String = throw UnsupportedOperationException()
        override fun deleteSession(sessionId: String) {}
    }

    val server = createServer(0, api)
    server.start()
    try {
        val port = (server.connectors[0] as org.eclipse.jetty.server.ServerConnector).localPort
        val conn = URL("http://localhost:$port/image?id=sess-x&key=runs-list&kind=actual").openConnection() as HttpURLConnection
        // The response commits as 200 before the mid-stream failure, so the status is 200; the failure
        // shows up only as a broken body read.
        assertEquals(200, conn.responseCode, "Expected the response to have already committed as 200; got ${conn.responseCode}")
        var threw = false
        var received = 0
        try {
            val input = conn.inputStream
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                received += n
            }
        } catch (e: IOException) {
            threw = true
        }
        assertTrue(
            threw,
            "Expected reading the body to fail with an IOException (premature end of the aborted chunked " +
                "stream), proving the truncation is client-detectable. Instead the read completed cleanly " +
                "with $received bytes — the servlet finished the stream normally, silently truncating the image."
        )
    } finally {
        server.stop()
    }
}
