@file:WithArtifact("screenshottest.wui.buildMaven()")
@file:WithArtifact("screenshottest.api:screenshottest-api:0.0.3")
@file:WithArtifact("org.eclipse.jetty:jetty-server:11.0.20")
@file:WithArtifact("org.eclipse.jetty:jetty-servlet:11.0.20")
@file:WithArtifact("org.eclipse.jetty:jetty-http:11.0.20")
@file:WithArtifact("org.eclipse.jetty:jetty-io:11.0.20")
@file:WithArtifact("org.eclipse.jetty:jetty-util:11.0.20")
@file:WithArtifact("org.eclipse.jetty:jetty-security:11.0.20")
@file:WithArtifact("jakarta.servlet:jakarta.servlet-api:5.0.0")
@file:WithArtifact("community.kotlin.clocks.simple:community-kotlin-clocks-simple:0.0.11")
@file:WithArtifact("community.kotlin.clocks.hierarchical:community-kotlin-clocks-hierarchical:0.0.6")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-test:1.9.22")
package screenshottest.wui

import build.kotlin.withartifact.WithArtifact
import community.kotlin.clocks.simple.ManualClock
import kotlin.test.*
import java.net.HttpURLConnection
import java.net.URL
import screenshottest.api.ScreenshotTestApi

fun cancelReasonLengthIsBoundedTest() {
    val cancellations = java.util.concurrent.atomic.AtomicInteger()
    val api = object : ScreenshotTestApi {
        override fun getRendererVersion() = "renderer"
        override fun createSession(label: String, mode: String, mainClass: String, scenariosJson: String) = "unused"
        override fun uploadFileChunk(sessionId: String, role: String, fileName: String, chunkIndex: Int, chunk: ByteArray) = Unit
        override fun finalizeFile(sessionId: String, role: String, fileName: String, totalChunks: Int, sha256Hex: String) = Unit
        override fun startRender(sessionId: String) = Unit
        override fun getSessionStatus(sessionId: String) = """{"sessionId":"sess-1","state":"RUNNING"}"""
        override fun getResultsJson(sessionId: String) = "{}"
        override fun getImageChunk(sessionId: String, key: String, kind: String, offset: Long, length: Int): ByteArray? = null
        override fun listSessions() = "[]"
        override fun deleteSession(sessionId: String) = Unit
        override fun getWorkerPoolStatus() = """{"maxWorkers":3,"activeWorkers":0,"queuedSessions":0,"running":[],"queued":[]}"""
        override fun setMaxWorkers(maxWorkers: Int) = Unit
        override fun cancelSession(sessionId: String, reason: String) { cancellations.incrementAndGet() }
    }
    val server = createServer(0, api, ManualClock(1735689600000L))
    server.start()
    try {
        val port = (server.connectors[0] as org.eclipse.jetty.server.ServerConnector).localPort
        val conn = URL("http://localhost:$port/session/cancel").openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.outputStream.use { it.write("id=sess-1&reason=${"x".repeat(513)}".toByteArray()) }
        assertEquals(400, conn.responseCode)
        val body = conn.errorStream.bufferedReader().readText()
        assertTrue(body.contains("""<span class="banner-text">Cancel reason has 513 characters; the limit is 512.</span>"""), "The complete length error must be visible: $body")
        assertEquals(0, cancellations.get(), "An overlong reason must not reach the service")
        val boundary = URL("http://localhost:$port/session/cancel").openConnection() as HttpURLConnection
        boundary.instanceFollowRedirects = false
        boundary.requestMethod = "POST"
        boundary.doOutput = true
        boundary.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        boundary.outputStream.use { it.write("id=sess-1&reason=${"x".repeat(512)}".toByteArray()) }
        assertEquals(303, boundary.responseCode, "A 512-character reason must be accepted")
        assertEquals(1, cancellations.get())
    } finally {
        server.stop()
    }
}
