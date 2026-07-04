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
 * A FAILED session has no results. Its detail page must surface the failure message from
 * getSessionStatus and must NOT render a results table (there is nothing to compare).
 */
fun sessionDetailFailedSessionShowsErrorTest() {
    val errorText = "Worker provisioning failed: droplet did not become reachable within 300s."
    val statusJson = """{"sessionId":"sess-fail","state":"FAILED","error":${org.json.JSONObject.quote(errorText)},"rendererVersion":"chromium-1228"}"""

    val api: ScreenshotTestApi = object : ScreenshotTestApi {
        override fun getRendererVersion(): String = "chromium-1228"
        override fun createSession(label: String, mode: String, mainClass: String, scenariosJson: String): String = throw UnsupportedOperationException()
        override fun uploadFileChunk(sessionId: String, role: String, fileName: String, chunkIndex: Int, chunk: ByteArray) = throw UnsupportedOperationException()
        override fun finalizeFile(sessionId: String, role: String, fileName: String, totalChunks: Int, sha256Hex: String) = throw UnsupportedOperationException()
        override fun startRender(sessionId: String) = throw UnsupportedOperationException()
        override fun getSessionStatus(sessionId: String): String = statusJson
        // Should never be called for a FAILED session; fail loudly if it is.
        override fun getResultsJson(sessionId: String): String = throw IllegalStateException("getResultsJson must not be called for a FAILED session")
        override fun getImageChunk(sessionId: String, key: String, kind: String, offset: Long, length: Int): ByteArray? = null
        override fun listSessions(): String = throw UnsupportedOperationException()
        override fun deleteSession(sessionId: String) {}
    }

    val server = createServer(0, api)
    server.start()
    try {
        val port = (server.connectors[0] as org.eclipse.jetty.server.ServerConnector).localPort
        val conn = URL("http://localhost:$port/session?id=sess-fail").openConnection() as HttpURLConnection
        assertEquals(200, conn.responseCode, "Expected HTTP 200 for a FAILED session detail; got ${conn.responseCode}")
        val html = conn.inputStream.bufferedReader().readText()

        assertTrue(html.contains(">FAILED<"), "Expected a FAILED state badge.")
        assertTrue(
            html.contains(escapeHtml(errorText)),
            "Expected the full failure message to render; page was:\n$html"
        )
        assertTrue(
            html.contains("This session failed before producing results."),
            "Expected the failed-session results placeholder; page was:\n$html"
        )
        assertFalse(html.contains("id=\"results-table\""), "A FAILED session must not render a results table.")
    } finally {
        server.stop()
    }
}
