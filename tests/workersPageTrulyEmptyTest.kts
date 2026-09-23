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

fun workersPageTrulyEmptyTest() {
    val api: ScreenshotTestApi = object : ScreenshotTestApi {
        override fun getRendererVersion(): String = throw UnsupportedOperationException()
        override fun createSession(label: String, mode: String, mainClass: String, scenariosJson: String): String = throw UnsupportedOperationException()
        override fun uploadFileChunk(sessionId: String, role: String, fileName: String, chunkIndex: Int, chunk: ByteArray) = throw UnsupportedOperationException()
        override fun finalizeFile(sessionId: String, role: String, fileName: String, totalChunks: Int, sha256Hex: String) = throw UnsupportedOperationException()
        override fun startRender(sessionId: String) = throw UnsupportedOperationException()
        override fun getResultsJson(sessionId: String): String = throw UnsupportedOperationException()
        override fun getImageChunk(sessionId: String, key: String, kind: String, offset: Long, length: Int): ByteArray? = throw UnsupportedOperationException()
        override fun getSessionStatus(sessionId: String): String = throw UnsupportedOperationException()
        override fun listSessions(): String = "[]"
        override fun getWorkerPoolStatus(): String = """{"maxWorkers":4,"activeWorkers":0,"queuedSessions":0,"running":[],"queued":[]}"""
        override fun deleteSession(sessionId: String) = throw UnsupportedOperationException()
        override fun setMaxWorkers(maxWorkers: Int) = throw UnsupportedOperationException()
        override fun cancelSession(sessionId: String, reason: String) = throw UnsupportedOperationException()
    }
    val server = createServer(0, api, ManualClock(1735689600000L))
    server.start()
    try {
        val port = (server.connectors[0] as org.eclipse.jetty.server.ServerConnector).localPort
        val conn = URL("http://localhost:$port/workers?refresh=0").openConnection() as HttpURLConnection
        val html = conn.inputStream.bufferedReader().readText()
        assertEquals(200, conn.responseCode)
        assertTrue(html.contains("No session is rendering right now."), "Expected the empty running state: $html")
        assertTrue(html.contains("No session is waiting for a worker."), "Expected the empty queue state: $html")
        assertTrue(html.contains("""<div class="info-value">0 / 4</div>"""), "Expected zero active workers out of four: $html")
        assertTrue(html.contains("""<div class="info-value">0</div>"""), "Expected zero queued sessions: $html")
        assertFalse(html.contains("id=\"running-table\""))
        assertFalse(html.contains("id=\"queued-table\""))
    } finally {
        server.stop()
    }
}
