@file:WithArtifact("screenshottest.wui.buildMaven()")
@file:WithArtifact("screenshottest.api:screenshottest-api:0.0.2")
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
 * The sessions list derives each session's phase from state + queuePosition (Queued #n / Rendering /
 * Completed / Failed), shows its duration measured against the injected clock (finished - started,
 * "running for", or "waiting" for queued sessions) with the absolute instants in a tooltip, and
 * offers Cancel only for RUNNING sessions and Delete only for terminal ones.
 */
fun sessionListShowsPhasesDurationsAndActionsTest() {
    val sessionsJson = """
        [
          {"sessionId":"sess-q2","label":"queued second","mode":"compare","state":"RUNNING","createdAt":1735689540000,"rendererVersion":"chromium-1228","queuePosition":1,"startedAt":null,"finishedAt":null},
          {"sessionId":"sess-q1","label":"queued first","mode":"record","state":"RUNNING","createdAt":1735689480000,"rendererVersion":"chromium-1228","queuePosition":0,"startedAt":null,"finishedAt":null},
          {"sessionId":"sess-r1","label":"rendering one","mode":"compare","state":"RUNNING","createdAt":1735689000000,"rendererVersion":"chromium-1228","queuePosition":null,"startedAt":1735689475000,"finishedAt":null},
          {"sessionId":"sess-c1","label":"completed one","mode":"compare","state":"COMPLETED","createdAt":1735685900000,"rendererVersion":"chromium-1228","queuePosition":null,"startedAt":1735686000000,"finishedAt":1735686090000},
          {"sessionId":"sess-f1","label":"cancelled before start","mode":"compare","state":"FAILED","createdAt":1735682300000,"rendererVersion":"chromium-1228","queuePosition":null,"startedAt":null,"finishedAt":1735682400000}
        ]
    """.trimIndent()
    val api: ScreenshotTestApi = object : ScreenshotTestApi {
        override fun getRendererVersion(): String = throw UnsupportedOperationException()
        override fun createSession(label: String, mode: String, mainClass: String, scenariosJson: String): String = throw UnsupportedOperationException()
        override fun uploadFileChunk(sessionId: String, role: String, fileName: String, chunkIndex: Int, chunk: ByteArray) = throw UnsupportedOperationException()
        override fun finalizeFile(sessionId: String, role: String, fileName: String, totalChunks: Int, sha256Hex: String) = throw UnsupportedOperationException()
        override fun startRender(sessionId: String) = throw UnsupportedOperationException()
        override fun getResultsJson(sessionId: String): String = throw UnsupportedOperationException()
        override fun getImageChunk(sessionId: String, key: String, kind: String, offset: Long, length: Int): ByteArray? = throw UnsupportedOperationException()
        override fun getSessionStatus(sessionId: String): String = throw UnsupportedOperationException()
        override fun listSessions(): String = sessionsJson
        override fun deleteSession(sessionId: String) = throw IllegalStateException("deleteSession must not be called by a GET")
        override fun getWorkerPoolStatus(): String = throw UnsupportedOperationException()
        override fun setMaxWorkers(maxWorkers: Int) = throw IllegalStateException("setMaxWorkers must not be called by a GET")
        override fun cancelSession(sessionId: String, reason: String) = throw IllegalStateException("cancelSession must not be called by a GET")
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
        val (code, html) = get(port, "/")
        assertEquals(200, code, "Expected HTTP 200 for the sessions list; got $code with body:\n$html")

        val cancelQ2 = """<form class="inline-action" method="post" action="/session/cancel"><input type="hidden" name="id" value="sess-q2"><input type="hidden" name="reason" value="Cancelled from the management UI"><input type="hidden" name="returnTo" value="list"><button type="submit" class="btn btn-danger" title="Cancel session sess-q2: it stops (or never starts) rendering and is marked FAILED with the reason &quot;Cancelled from the management UI&quot;.">Cancel</button></form>"""
        val queuedRow = """<tr class="row-status-provisioning"><td class="nowrap mono"><a href="/session?id=sess-q2">sess-q2</a></td><td>queued second</td><td><span class="badge badge-provisioning">compare</span></td><td><span class="badge badge-running">RUNNING</span></td><td class="nowrap"><span class="badge badge-queued" title="Waiting for a free render worker: 2nd in the service&#39;s queue (1 session ahead of it)." tabindex="0">Queued #2</span></td><td class="date-cell nowrap" data-timestamp="1735689540000"></td><td class="nowrap"><span title="Duration: waiting for a render worker since the session was created at 2024-12-31 23:59:00 UTC; measured at 2025-01-01 00:00:00 UTC." tabindex="0">waiting 1m 00s</span></td><td class="mono" style="font-size:12px;color:var(--text-secondary);">chromium-1228</td><td class="actions-cell">$cancelQ2</td></tr>"""
        assertTrue(html.contains(queuedRow), "Expected the full queued-session row:\n$queuedRow\npage was:\n$html")

        assertTrue(
            html.contains("""<span class="badge badge-queued" title="Waiting for a free render worker: 1st in the service&#39;s queue (0 sessions ahead of it)." tabindex="0">Queued #1</span>"""),
            "Expected sess-q1 (queuePosition 0) to show 'Queued #1'; page was:\n$html"
        )

        // Rendering: phase badge, live "running for" against the injected clock, Cancel action.
        assertTrue(
            html.contains("""<td class="nowrap"><span class="badge badge-running" title="A render worker is capturing this session&#39;s screenshots now." tabindex="0">Rendering</span></td>"""),
            "Expected sess-r1 to show the Rendering phase; page was:\n$html"
        )
        assertTrue(
            html.contains("""<span title="Duration: rendering since 2024-12-31 23:57:55 UTC; measured at 2025-01-01 00:00:00 UTC." tabindex="0">running for 2m 05s</span>"""),
            "Expected sess-r1 to be 'running for 2m 05s' (clock 00:00:00, started 23:57:55); page was:\n$html"
        )
        assertTrue(html.contains("""<input type="hidden" name="id" value="sess-r1">"""), "Expected a Cancel form for the rendering session sess-r1.")

        // Completed: duration = finished - started, and a Delete action (no Cancel).
        val completedRow = """<tr class="row-status-completed"><td class="nowrap mono"><a href="/session?id=sess-c1">sess-c1</a></td><td>completed one</td><td><span class="badge badge-provisioning">compare</span></td><td><span class="badge badge-completed">COMPLETED</span></td><td class="nowrap"><span class="badge badge-completed" title="The render finished; per-key verdicts are available." tabindex="0">Completed</span></td><td class="date-cell nowrap" data-timestamp="1735685900000"></td><td class="nowrap"><span title="Duration: rendered for 1m 30s, from 2024-12-31 23:00:00 UTC to 2024-12-31 23:01:30 UTC." tabindex="0">1m 30s</span></td><td class="mono" style="font-size:12px;color:var(--text-secondary);">chromium-1228</td><td class="actions-cell"><form class="inline-action" method="post" action="/session/delete"><input type="hidden" name="id" value="sess-c1"><input type="hidden" name="returnTo" value="list"><button type="submit" class="btn" title="Delete session sess-c1 and its stored screenshots from the service.">Delete</button></form></td></tr>"""
        assertTrue(html.contains(completedRow), "Expected the full completed-session row:\n$completedRow\npage was:\n$html")

        // Failed without ever starting: phase Failed, "-" duration explained, Delete action.
        assertTrue(
            html.contains("""<span class="badge badge-failed" title="The session ended without results (it failed or was cancelled); see its error." tabindex="0">Failed</span>"""),
            "Expected sess-f1 to show the Failed phase; page was:\n$html"
        )
        assertTrue(
            html.contains("""<span title="Duration: this session ended at 2024-12-31 22:00:00 UTC without ever starting to render." tabindex="0">-</span>"""),
            "Expected sess-f1's duration to be '-' with an explanatory tooltip; page was:\n$html"
        )

        // Exactly one action per row: 3 Cancel forms (the RUNNING sessions) and 2 Delete forms.
        assertEquals(3, Regex("""action="/session/cancel"""").findAll(html).count(), "Expected a Cancel form for each of the 3 RUNNING sessions only.")
        assertEquals(2, Regex("""action="/session/delete"""").findAll(html).count(), "Expected a Delete form for each of the 2 terminal sessions only.")

        // Nav links between the sessions list and the worker pool.
        assertTrue(html.contains("""<a href="/">Sessions</a>"""), "Expected a nav link to the sessions list.")
        assertTrue(html.contains("""<a href="/workers">Workers</a>"""), "Expected a nav link to the worker pool page.")
        // No notice requested -> no banner.
        assertFalse(html.contains("class=\"banner "), "Expected no banner without a ?notice= selector; page was:\n$html")
    } finally {
        server.stop()
    }
}
