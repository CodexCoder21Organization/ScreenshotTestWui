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
 * A `kind` outside {actual, golden, diff} is a malformed request: the /image endpoint must answer 400
 * with a message naming the offending value and the allowed set, WITHOUT calling the backend.
 */
fun imageEndpointInvalidKindReturns400Test() {
    val api: ScreenshotTestApi = object : ScreenshotTestApi {
        override fun getRendererVersion(): String = "chromium-1228"
        override fun createSession(label: String, mode: String, mainClass: String, scenariosJson: String): String = throw UnsupportedOperationException()
        override fun uploadFileChunk(sessionId: String, role: String, fileName: String, chunkIndex: Int, chunk: ByteArray) = throw UnsupportedOperationException()
        override fun finalizeFile(sessionId: String, role: String, fileName: String, totalChunks: Int, sha256Hex: String) = throw UnsupportedOperationException()
        override fun startRender(sessionId: String) = throw UnsupportedOperationException()
        override fun getSessionStatus(sessionId: String): String = throw UnsupportedOperationException()
        override fun getResultsJson(sessionId: String): String = throw UnsupportedOperationException()
        // Must not be reached — an invalid kind is rejected before any backend call.
        override fun getImageChunk(sessionId: String, key: String, kind: String, offset: Long, length: Int): ByteArray? =
            throw IllegalStateException("getImageChunk must not be called for an invalid kind")
        override fun listSessions(): String = throw UnsupportedOperationException()
        override fun deleteSession(sessionId: String) {}
    }

    val server = createServer(0, api)
    server.start()
    try {
        val port = (server.connectors[0] as org.eclipse.jetty.server.ServerConnector).localPort
        val conn = URL("http://localhost:$port/image?id=sess-img&key=runs-list&kind=thumbnail").openConnection() as HttpURLConnection
        assertEquals(400, conn.responseCode, "Expected HTTP 400 for an invalid image kind; got ${conn.responseCode}")
        val body = (conn.errorStream ?: conn.inputStream).bufferedReader().readText()
        assertEquals(
            "Invalid image kind \"thumbnail\"; kind must be one of: actual, golden, diff.",
            body,
            "Expected the full descriptive invalid-kind 400 body; got:\n$body"
        )
    } finally {
        server.stop()
    }
}
