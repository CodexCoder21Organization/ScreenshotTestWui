@file:WithArtifact("screenshottest.wui.buildMaven()")
@file:WithArtifact("screenshottest.api:screenshottest-api:0.0.3")
@file:WithArtifact("foundation.url:protocol:0.0.532")
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
import community.kotlin.clocks.simple.ManualClock
import foundation.url.protocol.sandbox.SandboxException
import screenshottest.api.ScreenshotTestApi
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A reported IllegalArgumentException must be a 404, and only its service message is shown. */
fun sandboxedReportedArgumentMapsTo404Test() {
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
        override fun deleteSession(sessionId: String) = throw UnsupportedOperationException()
        override fun getWorkerPoolStatus(): String = throw UnsupportedOperationException()
        override fun setMaxWorkers(maxWorkers: Int) = throw UnsupportedOperationException()
        override fun cancelSession(sessionId: String, reason: String) {
            assertEquals("sess-unknown", sessionId)
            assertEquals("Cancelled from the management UI", reason)
            throw SandboxException("Sandboxed code threw an exception: java.lang.InternalError: wrapper text", RuntimeException("wrapper cause"), "java.lang.IllegalArgumentException", "No screenshot session with id 'sess-unknown'.")
        }
    }
    val server = createServer(0, api, ManualClock(1735689600000L))
    try {
        server.start()
        val port = (server.connectors[0] as org.eclipse.jetty.server.ServerConnector).localPort
        val conn = URL("http://localhost:$port/session/cancel").openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write("id=sess-unknown&returnTo=list".toByteArray(Charsets.UTF_8)) }
            val responseStatus = conn.responseCode
            val html = (if (responseStatus < 400) conn.inputStream else conn.errorStream)
                .bufferedReader().use { it.readText() }
            assertEquals(404, responseStatus, "The reported java.lang.IllegalArgumentException must be classified correctly; page was:\n$html")
            assertEquals(null, conn.getHeaderField("Location"), "An error response must not redirect.")
            assertTrue(
                html.contains("""<span class="banner-text">Could not cancel session &#39;sess-unknown&#39;: No screenshot session with id &#39;sess-unknown&#39;.</span>"""),
                "Expected the complete service message in the banner; page was:\n$html",
            )
            assertTrue(html.contains("<h1>Render Sessions</h1>"), "Expected the sessions page under the banner.")
            assertFalse(html.contains("Sandboxed code threw an exception:"), "The sandbox wrapper text must not appear in the banner.")
        } finally {
            conn.disconnect()
        }
    } finally {
        server.stop()
    }
}
