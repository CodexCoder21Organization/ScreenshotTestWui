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
import kotlin.test.*
import java.net.HttpURLConnection
import java.net.URL
import screenshottest.api.ScreenshotTestApi
import foundation.url.protocol.sandbox.SandboxException

/**
 * The url:// client delivers every service failure as a SandboxException; given such exceptions
 * (built here directly; managementActionsThroughRealSandboxedProviderTest drives the real sandbox),
 * the WUI must classify by the service-reported remoteExceptionClassName (IllegalStateException ->
 * 409, IllegalArgumentException -> 404, none -> 502) and show the service's own message, never the
 * sandbox's diagnostic wrapper text.
 */
fun sandboxedBackendFailuresMapToStatusesTest() {
    val sessionsJson = """
        [
          {"sessionId":"sess-q2","label":"queued second","mode":"compare","state":"RUNNING","createdAt":1735689540000,"rendererVersion":"chromium-1228","queuePosition":1,"startedAt":null,"finishedAt":null},
          {"sessionId":"sess-q1","label":"queued first","mode":"record","state":"RUNNING","createdAt":1735689480000,"rendererVersion":"chromium-1228","queuePosition":0,"startedAt":null,"finishedAt":null},
          {"sessionId":"sess-r1","label":"rendering one","mode":"compare","state":"RUNNING","createdAt":1735689000000,"rendererVersion":"chromium-1228","queuePosition":null,"startedAt":1735689475000,"finishedAt":null},
          {"sessionId":"sess-c1","label":"completed one","mode":"compare","state":"COMPLETED","createdAt":1735685900000,"rendererVersion":"chromium-1228","queuePosition":null,"startedAt":1735686000000,"finishedAt":1735686090000},
          {"sessionId":"sess-f1","label":"cancelled before start","mode":"compare","state":"FAILED","createdAt":1735682300000,"rendererVersion":"chromium-1228","queuePosition":null,"startedAt":null,"finishedAt":1735682400000}
        ]
    """.trimIndent()
    val poolJson = """{"maxWorkers":3,"activeWorkers":1,"queuedSessions":2,"running":["sess-r1"],"queued":["sess-q1","sess-q2"]}"""
    val cancels = java.util.Collections.synchronizedList(mutableListOf<Pair<String, String>>())
    val deletes = java.util.Collections.synchronizedList(mutableListOf<String>())
    val maxWorkerCalls = java.util.Collections.synchronizedList(mutableListOf<Int>())
    val api: ScreenshotTestApi = object : ScreenshotTestApi {
        override fun getRendererVersion(): String = throw UnsupportedOperationException()
        override fun createSession(label: String, mode: String, mainClass: String, scenariosJson: String): String = throw UnsupportedOperationException()
        override fun uploadFileChunk(sessionId: String, role: String, fileName: String, chunkIndex: Int, chunk: ByteArray) = throw UnsupportedOperationException()
        override fun finalizeFile(sessionId: String, role: String, fileName: String, totalChunks: Int, sha256Hex: String) = throw UnsupportedOperationException()
        override fun startRender(sessionId: String) = throw UnsupportedOperationException()
        override fun getResultsJson(sessionId: String): String = throw UnsupportedOperationException()
        override fun getImageChunk(sessionId: String, key: String, kind: String, offset: Long, length: Int): ByteArray? = throw UnsupportedOperationException()
        override fun getSessionStatus(sessionId: String): String = when (sessionId) {
            "sess-q1" -> """{"sessionId":"sess-q1","state":"RUNNING","error":null,"rendererVersion":"chromium-1228","queuePosition":0,"startedAt":null,"finishedAt":null}"""
            else -> throw IllegalArgumentException("No screenshot session with id '$sessionId'.")
        }
        override fun listSessions(): String = sessionsJson
        override fun deleteSession(sessionId: String) {
            if (sessionId == "sess-r1") throw IllegalStateException("Session 'sess-r1' is RUNNING; cancel it before deleting it.")
            deletes.add(sessionId)
        }
        override fun getWorkerPoolStatus(): String = poolJson
        override fun setMaxWorkers(maxWorkers: Int) {
            require(maxWorkers in 1..16) { "maxWorkers must be between 1 and 16, but was $maxWorkers." }
            maxWorkerCalls.add(maxWorkers)
        }
        override fun cancelSession(sessionId: String, reason: String) {
            when (sessionId) {
                "sess-c1" -> throw SandboxException("Sandboxed code threw an exception: java.lang.InternalError: wrapper text", RuntimeException("wrapper cause"), "java.lang.IllegalStateException", "Session 'sess-c1' is COMPLETED; only RUNNING sessions can be cancelled.")
                "sess-boom" -> throw SandboxException("RPC to url://screenshottest/ timed out after 120s")
                "sess-unknown" -> throw SandboxException("Sandboxed code threw an exception: java.lang.InternalError: wrapper text", RuntimeException("wrapper cause"), "java.lang.IllegalArgumentException", "No screenshot session with id 'sess-unknown'.")
            }
            cancels.add(sessionId to reason)
        }
    }
    fun post(port: Int, path: String, form: String, headers: Map<String, String> = emptyMap()): HttpURLConnection {
        val conn = URL("http://localhost:$port$path").openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        for ((k, v) in headers) conn.setRequestProperty(k, v)
        conn.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
        return conn
    }
    fun bodyOf(conn: HttpURLConnection): String =
        (if (conn.responseCode < 400) conn.inputStream else conn.errorStream).bufferedReader().readText()
    val server = createServer(0, api, ManualClock(1735689600000L)) // 2025-01-01T00:00:00Z
    server.start()
    try {
        val port = (server.connectors[0] as org.eclipse.jetty.server.ServerConnector).localPort
        val conflict = post(port, "/session/cancel", "id=sess-c1&returnTo=list")
        assertEquals(409, conflict.responseCode, "Expected 409 Conflict for cancelling a COMPLETED session.")
        val conflictHtml = bodyOf(conflict)
        val conflictBanner = """<div class="banner banner-error" role="alert"><span class="banner-label">Error</span><span class="banner-text">Could not cancel session &#39;sess-c1&#39;: Session &#39;sess-c1&#39; is COMPLETED; only RUNNING sessions can be cancelled.</span>"""
        assertTrue(conflictHtml.contains(conflictBanner), "Expected the backend's full message in an error banner:\n$conflictBanner\npage was:\n$conflictHtml")
        assertTrue(conflictHtml.contains("<h1>Render Sessions</h1>"), "Expected the sessions list (the returnTo page) to be re-rendered under the banner.")

        val unknown = post(port, "/session/cancel", "id=sess-unknown&returnTo=workers")
        assertEquals(404, unknown.responseCode, "Expected 404 for cancelling an unknown session.")
        val unknownHtml = bodyOf(unknown)
        assertTrue(
            unknownHtml.contains("""<span class="banner-text">Could not cancel session &#39;sess-unknown&#39;: No screenshot session with id &#39;sess-unknown&#39;.</span>"""),
            "Expected the unknown-session message in a banner; page was:\n$unknownHtml"
        )
        assertTrue(unknownHtml.contains("<h1>Render Workers</h1>"), "Expected the workers page (the returnTo page) under the banner.")
        assertFalse(unknownHtml.contains("http-equiv=\"refresh\""), "An error page must not auto-refresh the error away.")

        val boom = post(port, "/session/cancel", "id=sess-boom&returnTo=list")
        assertEquals(502, boom.responseCode, "Expected 502 when the backend call itself fails.")
        assertTrue(
            bodyOf(boom).contains("""<span class="banner-text">The screenshot service failed to cancel session &#39;sess-boom&#39;: RPC to url://screenshottest/ timed out after 120s</span>"""),
            "Expected the backend failure message in a banner."
        )
        assertTrue(cancels.isEmpty(), "No cancel should have been recorded; got $cancels")
        assertTrue(deletes.isEmpty(), "No delete was requested; got $deletes")
        assertTrue(maxWorkerCalls.isEmpty(), "No max-workers change was requested; got $maxWorkerCalls")
    } finally {
        server.stop()
    }
}
