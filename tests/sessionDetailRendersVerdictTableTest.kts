@file:WithArtifact("screenshottest.wui.buildMaven()")
@file:WithArtifact("screenshottest.api:screenshottest-api:0.0.3")
@file:WithArtifact("org.eclipse.jetty:jetty-server:11.0.20")
@file:WithArtifact("org.eclipse.jetty:jetty-servlet:11.0.20")
@file:WithArtifact("org.eclipse.jetty:jetty-http:11.0.20")
@file:WithArtifact("org.eclipse.jetty:jetty-io:11.0.20")
@file:WithArtifact("org.eclipse.jetty:jetty-util:11.0.20")
@file:WithArtifact("org.eclipse.jetty:jetty-security:11.0.20")
@file:WithArtifact("jakarta.servlet:jakarta.servlet-api:5.0.0")
@file:WithArtifact("org.json:json:20250517")
@file:WithArtifact("community.kotlin.clocks.simple:community-kotlin-clocks-simple:0.0.11")
@file:WithArtifact("community.kotlin.clocks.hierarchical:community-kotlin-clocks-hierarchical:0.0.6")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-test:1.9.22")
@file:WithArtifact("org.slf4j:slf4j-api:1.7.36")
@file:WithArtifact("org.slf4j:slf4j-simple:2.0.9")
package screenshottest.wui

import build.kotlin.withartifact.WithArtifact
import kotlin.test.*
import java.net.HttpURLConnection
import java.net.URL
import screenshottest.api.ScreenshotTestApi

/**
 * A COMPLETED compare session's detail page must render the per-key verdict table (key, verdict,
 * diff pixels / total, max channel delta, golden & actual dimensions) and inline thumbnails pointing
 * at the /image endpoint: actual + golden for a MATCH key, actual + golden + diff for a DIFF key. The
 * service writes a diff heatmap only for DIFF keys, so a diff <img> on a MATCH key could only fail.
 */
fun sessionDetailRendersVerdictTableTest() {
    val statusJson = """{"sessionId":"sess-cmp","state":"COMPLETED","error":null,"rendererVersion":"chromium-1228"}"""
    val resultsJson = """
        {"rendererVersion":"chromium-1228","mode":"compare","results":[
          {"key":"runs-list","verdict":"MATCH","diffPixelCount":0,"totalPixels":156400,"maxChannelDelta":0,"goldenWidth":460,"goldenHeight":340,"actualWidth":460,"actualHeight":340},
          {"key":"run-detail","verdict":"DIFF","diffPixelCount":8640,"totalPixels":138000,"maxChannelDelta":96,"goldenWidth":460,"goldenHeight":300,"actualWidth":460,"actualHeight":300}
        ]}
    """.trimIndent()

    val api: ScreenshotTestApi = object : ScreenshotTestApi {
        override fun getRendererVersion(): String = "chromium-1228"
        override fun createSession(label: String, mode: String, mainClass: String, scenariosJson: String): String = throw UnsupportedOperationException()
        override fun uploadFileChunk(sessionId: String, role: String, fileName: String, chunkIndex: Int, chunk: ByteArray) = throw UnsupportedOperationException()
        override fun finalizeFile(sessionId: String, role: String, fileName: String, totalChunks: Int, sha256Hex: String) = throw UnsupportedOperationException()
        override fun startRender(sessionId: String) = throw UnsupportedOperationException()
        override fun getSessionStatus(sessionId: String): String = if (sessionId == "sess-cmp") statusJson else throw IllegalArgumentException("No screenshot session with id '$sessionId'.")
        override fun getResultsJson(sessionId: String): String = if (sessionId == "sess-cmp") resultsJson else throw IllegalArgumentException("No screenshot session with id '$sessionId'.")
        override fun getImageChunk(sessionId: String, key: String, kind: String, offset: Long, length: Int): ByteArray? = if (offset == 0L) byteArrayOf(1, 2, 3) else null
        override fun listSessions(): String = throw UnsupportedOperationException()
        override fun deleteSession(sessionId: String) {}
        override fun getWorkerPoolStatus(): String = throw UnsupportedOperationException()
        override fun setMaxWorkers(maxWorkers: Int) = throw UnsupportedOperationException()
        override fun cancelSession(sessionId: String, reason: String) = throw UnsupportedOperationException()
    }

    val server = createServer(0, api)
    server.start()
    try {
        val port = (server.connectors[0] as org.eclipse.jetty.server.ServerConnector).localPort
        val conn = URL("http://localhost:$port/session?id=sess-cmp").openConnection() as HttpURLConnection
        assertEquals(200, conn.responseCode, "Expected HTTP 200 for a COMPLETED session detail; got ${conn.responseCode}")
        val html = conn.inputStream.bufferedReader().readText()

        assertTrue(html.contains("id=\"results-table\""), "Expected a results table with id=results-table.")
        // Keys.
        assertTrue(html.contains(">runs-list<"), "Expected the 'runs-list' key in the results table.")
        assertTrue(html.contains(">run-detail<"), "Expected the 'run-detail' key in the results table.")
        // Verdict badges.
        assertTrue(html.contains(">MATCH<"), "Expected a MATCH verdict badge.")
        assertTrue(html.contains(">DIFF<"), "Expected a DIFF verdict badge.")
        // Diff-pixel / total formatting (thousands separators).
        assertTrue(html.contains("0 / 156,400"), "Expected the MATCH row's '0 / 156,400' diff-pixel figure; page was:\n$html")
        assertTrue(html.contains("8,640 / 138,000"), "Expected the DIFF row's '8,640 / 138,000' diff-pixel figure; page was:\n$html")
        // Max channel delta.
        assertTrue(html.contains(">96<"), "Expected the DIFF row's maxChannelDelta of 96.")
        // Dimensions.
        assertTrue(html.contains("460&times;340"), "Expected the runs-list 460x340 dimensions.")
        assertTrue(html.contains("460&times;300"), "Expected the run-detail 460x300 dimensions.")

        // Inline thumbnails: exactly the images the service produces for each verdict.
        val expectedKinds = mapOf(
            "runs-list" to listOf("actual", "golden"),          // MATCH: no diff heatmap exists
            "run-detail" to listOf("actual", "golden", "diff"), // DIFF
        )
        for ((key, kinds) in expectedKinds) {
            val rendered = Regex("<img src=\"/image\\?id=sess-cmp&amp;key=${Regex.escape(key)}&amp;kind=([a-z]+)\"")
                .findAll(html).map { it.groupValues[1] }.toList()
            assertEquals(
                kinds, rendered,
                "Expected exactly the $kinds thumbnails for key '$key', in that order; page was:\n$html"
            )
        }
    } finally {
        server.stop()
    }
}
