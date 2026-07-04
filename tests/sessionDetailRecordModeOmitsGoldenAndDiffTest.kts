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
 * A record-mode session's keys are RECORDED — no golden was submitted, so no golden or diff image
 * exists. The detail page must show the RECORDED verdict and the freshly captured `actual` thumbnail
 * ONLY, never a golden or diff <img> (which would 404 on the /image endpoint).
 */
fun sessionDetailRecordModeOmitsGoldenAndDiffTest() {
    val statusJson = """{"sessionId":"sess-rec","state":"COMPLETED","error":null,"rendererVersion":"chromium-1228"}"""
    val resultsJson = """
        {"rendererVersion":"chromium-1228","mode":"record","results":[
          {"key":"summary","verdict":"RECORDED","diffPixelCount":0,"totalPixels":0,"maxChannelDelta":0,"goldenWidth":0,"goldenHeight":0,"actualWidth":460,"actualHeight":300}
        ]}
    """.trimIndent()

    val api: ScreenshotTestApi = object : ScreenshotTestApi {
        override fun getRendererVersion(): String = "chromium-1228"
        override fun createSession(label: String, mode: String, mainClass: String, scenariosJson: String): String = throw UnsupportedOperationException()
        override fun uploadFileChunk(sessionId: String, role: String, fileName: String, chunkIndex: Int, chunk: ByteArray) = throw UnsupportedOperationException()
        override fun finalizeFile(sessionId: String, role: String, fileName: String, totalChunks: Int, sha256Hex: String) = throw UnsupportedOperationException()
        override fun startRender(sessionId: String) = throw UnsupportedOperationException()
        override fun getSessionStatus(sessionId: String): String = statusJson
        override fun getResultsJson(sessionId: String): String = resultsJson
        override fun getImageChunk(sessionId: String, key: String, kind: String, offset: Long, length: Int): ByteArray? = if (offset == 0L) byteArrayOf(1, 2, 3) else null
        override fun listSessions(): String = throw UnsupportedOperationException()
        override fun deleteSession(sessionId: String) {}
    }

    val server = createServer(0, api)
    server.start()
    try {
        val port = (server.connectors[0] as org.eclipse.jetty.server.ServerConnector).localPort
        val conn = URL("http://localhost:$port/session?id=sess-rec").openConnection() as HttpURLConnection
        assertEquals(200, conn.responseCode, "Expected HTTP 200 for the record-mode session detail; got ${conn.responseCode}")
        val html = conn.inputStream.bufferedReader().readText()

        assertTrue(html.contains(">RECORDED<"), "Expected a RECORDED verdict badge.")
        assertTrue(html.contains(">summary<"), "Expected the 'summary' key.")
        // Only the actual thumbnail is present.
        assertTrue(
            html.contains("<img src=\"/image?id=sess-rec&amp;key=summary&amp;kind=actual\""),
            "Expected the RECORDED key's 'actual' thumbnail; page was:\n$html"
        )
        assertFalse(
            html.contains("kind=golden"),
            "A record-mode key has no golden — no golden <img> must be rendered; page was:\n$html"
        )
        assertFalse(
            html.contains("kind=diff"),
            "A record-mode key has no diff — no diff <img> must be rendered; page was:\n$html"
        )
    } finally {
        server.stop()
    }
}
