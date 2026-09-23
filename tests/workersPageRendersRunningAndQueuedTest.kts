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
import community.kotlin.clocks.simple.ManualClock
import kotlin.test.*
import java.net.HttpURLConnection
import java.net.URL
import screenshottest.api.ScreenshotTestApi

/**
 * /workers shows the pool's max / active / queued counts, the rendering sessions (link, label,
 * started, running-for against the injected clock) and the queued ones in service order (position,
 * link, label, created, waiting-for), each with a Cancel action returning to /workers; it reloads
 * every 15 s unless requested with ?refresh=0.
 */
fun workersPageRendersRunningAndQueuedTest() {
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
                "sess-c1" -> throw IllegalStateException("Session 'sess-c1' is COMPLETED; only RUNNING sessions can be cancelled.")
                "sess-boom" -> throw RuntimeException("RPC to url://screenshottest/ timed out after 120s")
                "sess-unknown" -> throw IllegalArgumentException("No screenshot session with id 'sess-unknown'.")
            }
            cancels.add(sessionId to reason)
        }
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
        val (code, html) = get(port, "/workers")
        assertEquals(200, code, "Expected HTTP 200 for /workers; body:\n$html")

        assertTrue(html.contains("""<meta http-equiv="refresh" content="15;url=/workers">"""), "Expected a 15 s auto-refresh to the clean /workers URL; page was:\n$html")
        assertTrue(html.contains("""<a href="/workers?refresh=0">Stop auto-refresh</a>"""), "Expected a link to the paused view.")

        assertTrue(
            html.contains("""<div class="info-card"><div class="info-label">Max workers<span class="info-icon" tabindex="0" title="The most sessions the service renders at the same time. Further sessions wait in the queue.">i</span></div><div class="info-value">3</div></div>"""),
            "Expected the Max workers card showing 3; page was:\n$html"
        )
        assertTrue(html.contains("""<div class="info-value">1 / 3</div>"""), "Expected Active workers '1 / 3'.")
        assertTrue(
            html.contains("""<div class="info-label">Queued sessions<span class="info-icon" tabindex="0" title="Sessions whose render was started but which are waiting for a free worker.">i</span></div><div class="info-value">2</div>"""),
            "Expected the Queued sessions card showing 2."
        )
        assertTrue(
            html.contains("""<input id="max-workers-input" class="text-input" type="text" inputmode="numeric" name="maxWorkers" size="4" value="3">"""),
            "Expected the max-workers form pre-filled with the current value 3; page was:\n$html"
        )
        assertTrue(html.contains("""<form method="post" action="/workers/max" class="form-row">"""), "Expected the form to POST to /workers/max.")

        val runningRow = """<tr><td class="nowrap mono"><a href="/session?id=sess-r1">sess-r1</a></td><td>rendering one</td><td class="date-cell nowrap" data-timestamp="1735689475000"></td><td class="nowrap"><span title="Duration: rendering since 2024-12-31 23:57:55 UTC; measured at 2025-01-01 00:00:00 UTC." tabindex="0">running for 2m 05s</span></td><td class="actions-cell"><form class="inline-action" method="post" action="/session/cancel"><input type="hidden" name="id" value="sess-r1"><input type="hidden" name="reason" value="Cancelled from the management UI"><input type="hidden" name="returnTo" value="workers"><button type="submit" class="btn btn-danger" title="Cancel session sess-r1: it stops (or never starts) rendering and is marked FAILED with the reason &quot;Cancelled from the management UI&quot;.">Cancel</button></form></td></tr>"""
        assertTrue(html.contains(runningRow), "Expected the full running-session row:\n$runningRow\npage was:\n$html")

        val firstQueued = """<tr><td class="nowrap" title="Served next." tabindex="0">#1</td><td class="nowrap mono"><a href="/session?id=sess-q1">sess-q1</a></td><td>queued first</td><td class="date-cell nowrap" data-timestamp="1735689480000"></td><td class="nowrap"><span title="Duration: waiting for a render worker since the session was created at 2024-12-31 23:58:00 UTC; measured at 2025-01-01 00:00:00 UTC." tabindex="0">waiting 2m 00s</span></td>"""
        val secondQueued = """<tr><td class="nowrap" title="Served after 1 other queued session." tabindex="0">#2</td><td class="nowrap mono"><a href="/session?id=sess-q2">sess-q2</a></td><td>queued second</td><td class="date-cell nowrap" data-timestamp="1735689540000"></td><td class="nowrap"><span title="Duration: waiting for a render worker since the session was created at 2024-12-31 23:59:00 UTC; measured at 2025-01-01 00:00:00 UTC." tabindex="0">waiting 1m 00s</span></td>"""
        assertTrue(html.contains(firstQueued), "Expected the first queued row:\n$firstQueued\npage was:\n$html")
        assertTrue(html.contains(secondQueued), "Expected the second queued row:\n$secondQueued\npage was:\n$html")
        assertTrue(html.indexOf(firstQueued) < html.indexOf(secondQueued), "Expected queued sessions in service order (sess-q1 before sess-q2).")
        assertTrue(html.contains("<h2>Rendering now <span class=\"text-gray\">(1)</span></h2>"), "Expected the rendering count heading.")
        assertTrue(html.contains("<h2>Queue <span class=\"text-gray\">(2)</span></h2>"), "Expected the queue count heading.")
        assertEquals(3, Regex("""name="returnTo" value="workers"""").findAll(html).count(), "Expected one Cancel form (returning to /workers) per running or queued session.")

        // The paused view is its own linkable URL without the refresh.
        val (pausedCode, paused) = get(port, "/workers?refresh=0")
        assertEquals(200, pausedCode)
        assertFalse(paused.contains("http-equiv=\"refresh\""), "Expected no auto-refresh with ?refresh=0; page was:\n$paused")
        assertTrue(paused.contains("""Auto-refresh is off. <a href="/workers">Turn auto-refresh on</a>"""), "Expected the resume link on the paused view.")
    } finally {
        server.stop()
    }
}
