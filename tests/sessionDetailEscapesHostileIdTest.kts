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
import java.net.URLEncoder
import java.net.URL
import screenshottest.api.ScreenshotTestApi

/**
 * A hostile session id (containing script markup) must be neutralized everywhere it reaches the page:
 * HTML-escaped in the heading/title text, and percent-encoded in the image <img> src attributes —
 * never emitted as live markup.
 */
fun sessionDetailEscapesHostileIdTest() {
    val hostile = "<script>alert('x')</script>"
    val resultsJson = """
        {"rendererVersion":"chromium-1228","mode":"compare","results":[
          {"key":"runs-list","verdict":"MATCH","diffPixelCount":0,"totalPixels":100,"maxChannelDelta":0,"goldenWidth":10,"goldenHeight":10,"actualWidth":10,"actualHeight":10}
        ]}
    """.trimIndent()
    val api: ScreenshotTestApi = object : ScreenshotTestApi {
        override fun getRendererVersion(): String = "chromium-1228"
        override fun createSession(label: String, mode: String, mainClass: String, scenariosJson: String): String = throw UnsupportedOperationException()
        override fun uploadFileChunk(sessionId: String, role: String, fileName: String, chunkIndex: Int, chunk: ByteArray) = throw UnsupportedOperationException()
        override fun finalizeFile(sessionId: String, role: String, fileName: String, totalChunks: Int, sha256Hex: String) = throw UnsupportedOperationException()
        override fun startRender(sessionId: String) = throw UnsupportedOperationException()
        override fun getSessionStatus(sessionId: String): String =
            """{"sessionId":${org.json.JSONObject.quote(sessionId)},"state":"COMPLETED","error":null,"rendererVersion":"chromium-1228"}"""
        override fun getResultsJson(sessionId: String): String = resultsJson
        override fun getImageChunk(sessionId: String, key: String, kind: String, offset: Long, length: Int): ByteArray? = if (offset == 0L) byteArrayOf(1) else null
        override fun listSessions(): String = throw UnsupportedOperationException()
        override fun deleteSession(sessionId: String) {}
    }

    val server = createServer(0, api)
    server.start()
    try {
        val port = (server.connectors[0] as org.eclipse.jetty.server.ServerConnector).localPort
        val encodedId = URLEncoder.encode(hostile, "UTF-8").replace("+", "%20")
        val conn = URL("http://localhost:$port/session?id=$encodedId").openConnection() as HttpURLConnection
        assertEquals(200, conn.responseCode, "Expected HTTP 200; got ${conn.responseCode}")
        val html = conn.inputStream.bufferedReader().readText()

        assertFalse(
            html.contains(hostile),
            "The raw hostile id must never appear unescaped in the page (would be live <script>); page was:\n$html"
        )
        assertTrue(
            html.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;"),
            "Expected the hostile id HTML-escaped in the heading; page was:\n$html"
        )
        // In the image src the id is percent-encoded, so the raw angle brackets never reach the attribute.
        assertTrue(
            html.contains("/image?id=%3Cscript%3E"),
            "Expected the image src to percent-encode the hostile id; page was:\n$html"
        )
    } finally {
        server.stop()
    }
}
