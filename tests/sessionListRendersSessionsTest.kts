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
 * The sessions landing page ("/") lists every session newest-first with its id (linking to the
 * detail page), label, mode, state, created timestamp, and renderer. This drives the whole
 * listSessions -> table rendering path against a fake service returning three sessions of different
 * modes/states.
 */
fun sessionListRendersSessionsTest() {
    val sessionsJson = """
        [
          {"sessionId":"sess-newest","label":"BuildTestWui goldens","mode":"compare","state":"COMPLETED","createdAt":1735689600000,"rendererVersion":"chromium-1228"},
          {"sessionId":"sess-middle","label":"Perf & <b>WUI</b> goldens","mode":"record","state":"RUNNING","createdAt":1735603200000,"rendererVersion":"chromium-1228"},
          {"sessionId":"sess-oldest","label":"SFS goldens","mode":"compare","state":"FAILED","createdAt":1735516800000,"rendererVersion":"chromium-1228"}
        ]
    """.trimIndent()

    val api: ScreenshotTestApi = object : ScreenshotTestApi {
        override fun getRendererVersion(): String = throw UnsupportedOperationException()
        override fun createSession(label: String, mode: String, mainClass: String, scenariosJson: String): String = throw UnsupportedOperationException()
        override fun uploadFileChunk(sessionId: String, role: String, fileName: String, chunkIndex: Int, chunk: ByteArray) = throw UnsupportedOperationException()
        override fun finalizeFile(sessionId: String, role: String, fileName: String, totalChunks: Int, sha256Hex: String) = throw UnsupportedOperationException()
        override fun startRender(sessionId: String) = throw UnsupportedOperationException()
        override fun getSessionStatus(sessionId: String): String = throw UnsupportedOperationException()
        override fun getResultsJson(sessionId: String): String = throw UnsupportedOperationException()
        override fun getImageChunk(sessionId: String, key: String, kind: String, offset: Long, length: Int): ByteArray? = throw UnsupportedOperationException()
        override fun listSessions(): String = sessionsJson
        override fun deleteSession(sessionId: String) {}
    }

    val server = createServer(0, api)
    server.start()
    try {
        val port = (server.connectors[0] as org.eclipse.jetty.server.ServerConnector).localPort
        val conn = URL("http://localhost:$port/").openConnection() as HttpURLConnection
        assertEquals(200, conn.responseCode, "Expected HTTP 200 for the sessions list; got ${conn.responseCode}")
        assertTrue(
            (conn.getHeaderField("Content-Type") ?: "").startsWith("text/html"),
            "Expected text/html Content-Type; got ${conn.getHeaderField("Content-Type")}"
        )
        val html = conn.inputStream.bufferedReader().readText()

        assertTrue(html.contains("3 sessions, newest first"), "Expected the subtitle to report 3 sessions; page was:\n$html")

        // Every session id renders as a link to its detail page.
        for (id in listOf("sess-newest", "sess-middle", "sess-oldest")) {
            assertTrue(html.contains("/session?id=$id"), "Expected a detail link for session '$id' but it was absent.")
        }
        // Labels render (and the injected markup in the middle label is HTML-escaped, not live).
        assertTrue(html.contains("BuildTestWui goldens"), "Expected the first session's label to render.")
        assertTrue(
            html.contains("Perf &amp; &lt;b&gt;WUI&lt;/b&gt; goldens"),
            "Expected the label with special characters to be HTML-escaped, not injected as live markup; page was:\n$html"
        )
        // Mode + state badges.
        assertTrue(html.contains(">compare<"), "Expected a 'compare' mode badge.")
        assertTrue(html.contains(">record<"), "Expected a 'record' mode badge.")
        assertTrue(html.contains(">COMPLETED<"), "Expected a COMPLETED state badge.")
        assertTrue(html.contains(">RUNNING<"), "Expected a RUNNING state badge.")
        assertTrue(html.contains(">FAILED<"), "Expected a FAILED state badge.")
        // createdAt surfaces as a date-cell carrying the epoch-ms timestamp.
        assertTrue(html.contains("data-timestamp=\"1735689600000\""), "Expected the newest session's createdAt as a date-cell timestamp.")

        // Newest first: the ids must appear in submission order down the page.
        val iNewest = html.indexOf("sess-newest")
        val iMiddle = html.indexOf("sess-middle")
        val iOldest = html.indexOf("sess-oldest")
        assertTrue(
            iNewest in 0 until iMiddle && iMiddle < iOldest,
            "Expected sessions newest-first (sess-newest before sess-middle before sess-oldest); indices were $iNewest, $iMiddle, $iOldest."
        )
    } finally {
        server.stop()
    }
}
