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

/**
 * /session?id= shows the phase, queue position, started / finished instants and duration, and the
 * action the session's state allows: Cancel (with an editable reason, returning to this page) while
 * RUNNING, Delete (returning to the list) once terminal.
 */
fun sessionDetailShowsPhaseTimingAndActionsTest() {
    val api: ScreenshotTestApi = object : ScreenshotTestApi {
        override fun getRendererVersion(): String = throw UnsupportedOperationException()
        override fun createSession(label: String, mode: String, mainClass: String, scenariosJson: String): String = throw UnsupportedOperationException()
        override fun uploadFileChunk(sessionId: String, role: String, fileName: String, chunkIndex: Int, chunk: ByteArray) = throw UnsupportedOperationException()
        override fun finalizeFile(sessionId: String, role: String, fileName: String, totalChunks: Int, sha256Hex: String) = throw UnsupportedOperationException()
        override fun startRender(sessionId: String) = throw UnsupportedOperationException()
        override fun getImageChunk(sessionId: String, key: String, kind: String, offset: Long, length: Int): ByteArray? = throw UnsupportedOperationException()
        override fun getResultsJson(sessionId: String): String = """{"rendererVersion":"chromium-1228","mode":"compare","results":[]}"""
        override fun getSessionStatus(sessionId: String): String = when (sessionId) {
            "sess-q" -> """{"sessionId":"sess-q","state":"RUNNING","error":null,"rendererVersion":"chromium-1228","queuePosition":2,"startedAt":null,"finishedAt":null}"""
            "sess-c" -> """{"sessionId":"sess-c","state":"COMPLETED","error":null,"rendererVersion":"chromium-1228","queuePosition":null,"startedAt":1735686000000,"finishedAt":1735686090000}"""
            else -> throw IllegalArgumentException("No screenshot session with id '$sessionId'.")
        }
        override fun listSessions(): String = throw UnsupportedOperationException()
        override fun deleteSession(sessionId: String) = throw UnsupportedOperationException()
        override fun getWorkerPoolStatus(): String = throw UnsupportedOperationException()
        override fun setMaxWorkers(maxWorkers: Int) = throw UnsupportedOperationException()
        override fun cancelSession(sessionId: String, reason: String) = throw UnsupportedOperationException()
    }
    fun get(port: Int, path: String): Pair<Int, String> {
        val conn = URL("http://localhost:$port$path").openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        val code = conn.responseCode
        val body = (if (code < 400) conn.inputStream else conn.errorStream).bufferedReader().readText()
        return code to body
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
        val (qCode, queued) = get(port, "/session?id=sess-q")
        assertEquals(200, qCode, "Expected 200 for a queued session; body:\n$queued")
        assertTrue(
            queued.contains("""<div class="info-card"><div class="info-label">Phase</div><div class="info-value"><span class="badge badge-queued" title="Waiting for a free render worker: 3rd in the service&#39;s queue (2 sessions ahead of it)." tabindex="0">Queued #3</span></div></div>"""),
            "Expected the Queued #3 phase card; page was:\n$queued"
        )
        assertTrue(
            queued.contains("""<div class="info-label">Queue position</div><div class="info-value"><span title="0-based queuePosition 2: 2 sessions will be given a worker before this one." tabindex="0">#3</span></div>"""),
            "Expected the queue position card; page was:\n$queued"
        )
        assertTrue(queued.contains("""<div class="info-label">Started</div><div class="info-value text-gray">Not started yet</div>"""), "Expected 'Not started yet'.")
        assertTrue(queued.contains("""<div class="info-label">Finished</div><div class="info-value text-gray">Not finished yet</div>"""), "Expected 'Not finished yet'.")
        val cancelPanel = """<div class="panel" id="session-actions"><h2>Cancel this session</h2><form method="post" action="/session/cancel" class="form-row"><input type="hidden" name="id" value="sess-q"><input type="hidden" name="returnTo" value="session"><label for="cancel-reason">Reason</label><input id="cancel-reason" class="text-input" type="text" name="reason" size="48" value="Cancelled from the management UI"><button type="submit" class="btn btn-danger">Cancel session</button></form>"""
        assertTrue(queued.contains(cancelPanel), "Expected the Cancel panel:\n$cancelPanel\npage was:\n$queued")
        assertFalse(queued.contains("/session/delete"), "A RUNNING session must not offer Delete.")

        val (cCode, completed) = get(port, "/session?id=sess-c")
        assertEquals(200, cCode, "Expected 200 for a completed session; body:\n$completed")
        assertTrue(completed.contains("""<span class="badge badge-completed" title="The render finished; per-key verdicts are available." tabindex="0">Completed</span>"""), "Expected the Completed phase.")
        assertTrue(completed.contains("""<div class="info-label">Queue position</div><div class="info-value"><span class="text-gray" title="This session is not waiting for a render worker." tabindex="0">-</span></div>"""), "Expected no queue position.")
        assertTrue(completed.contains("""<div class="info-label">Started</div><div class="info-value date-cell" data-timestamp="1735686000000"></div>"""), "Expected the started instant as a stacked date cell.")
        assertTrue(completed.contains("""<div class="info-label">Finished</div><div class="info-value date-cell" data-timestamp="1735686090000"></div>"""), "Expected the finished instant as a stacked date cell.")
        assertTrue(
            completed.contains("""<div class="info-label">Duration</div><div class="info-value"><span title="Duration: rendered for 1m 30s, from 2024-12-31 23:00:00 UTC to 2024-12-31 23:01:30 UTC." tabindex="0">1m 30s</span></div>"""),
            "Expected the 1m 30s duration; page was:\n$completed"
        )
        assertTrue(
            completed.contains("""<div class="panel" id="session-actions"><h2>Delete this session</h2><div class="form-row"><form class="inline-action" method="post" action="/session/delete"><input type="hidden" name="id" value="sess-c"><input type="hidden" name="returnTo" value="list">"""),
            "Expected the Delete panel returning to the list; page was:\n$completed"
        )
        assertFalse(completed.contains("/session/cancel"), "A COMPLETED session must not offer Cancel.")
    } finally {
        server.stop()
    }
}
