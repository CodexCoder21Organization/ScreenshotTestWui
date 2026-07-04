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
import community.kotlin.clocks.simple.ManualClock
import kotlin.test.*
import java.net.HttpURLConnection
import java.net.URL
import screenshottest.api.ScreenshotTestApi

/**
 * The createServer(port, api, clock) seam threads an injected Clock into every page footer so the
 * browser-side relative-time math is anchored to a fixed instant. With a frozen ManualClock the page
 * is byte-stable across renders — the property golden-screenshot capture depends on.
 */
fun frozenClockRendersTimeStableHtmlTest() {
    val fixedNow = 1735689600000L // 2025-01-01T00:00:00Z
    val sessionsJson = """[{"sessionId":"sess-1","label":"goldens","mode":"compare","state":"COMPLETED","createdAt":1735603200000,"rendererVersion":"chromium-1228"}]"""

    val api: ScreenshotTestApi = object : ScreenshotTestApi {
        override fun getRendererVersion(): String = "chromium-1228"
        override fun createSession(label: String, mode: String, mainClass: String, scenariosJson: String): String = throw UnsupportedOperationException()
        override fun uploadFileChunk(sessionId: String, role: String, fileName: String, chunkIndex: Int, chunk: ByteArray) = throw UnsupportedOperationException()
        override fun finalizeFile(sessionId: String, role: String, fileName: String, totalChunks: Int, sha256Hex: String) = throw UnsupportedOperationException()
        override fun startRender(sessionId: String) = throw UnsupportedOperationException()
        override fun getSessionStatus(sessionId: String): String = throw UnsupportedOperationException()
        override fun getResultsJson(sessionId: String): String = throw UnsupportedOperationException()
        override fun getImageChunk(sessionId: String, key: String, kind: String, offset: Long, length: Int): ByteArray? = null
        override fun listSessions(): String = sessionsJson
        override fun deleteSession(sessionId: String) {}
    }

    val server = createServer(0, api, ManualClock(fixedNow))
    server.start()
    try {
        val port = (server.connectors[0] as org.eclipse.jetty.server.ServerConnector).localPort
        fun fetch(): String {
            val conn = URL("http://localhost:$port/").openConnection() as HttpURLConnection
            assertEquals(200, conn.responseCode, "Expected HTTP 200 for the sessions list; got ${conn.responseCode}")
            return conn.inputStream.bufferedReader().readText()
        }
        val first = fetch()
        assertTrue(
            first.contains("var serverNowMs = $fixedNow;"),
            "Expected the footer to anchor relative times to the injected clock's fixed now ($fixedNow); page was:\n$first"
        )
        val second = fetch()
        assertEquals(
            first, second,
            "Expected two renders under a frozen clock to be byte-identical (time-stable), but they differed."
        )
    } finally {
        server.stop()
    }
}
