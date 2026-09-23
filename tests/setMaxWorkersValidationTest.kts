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
 * POST /workers/max: a valid value is forwarded to setMaxWorkers and redirects 303 with a notice the
 * workers page composes; a value the service rejects is 400 with the service's full message and the
 * rejected input kept in the field; a non-numeric or missing value is 400 without calling the backend.
 */
fun setMaxWorkersValidationTest() {
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
        val ok = post(port, "/workers/max", "maxWorkers=6")
        assertEquals(303, ok.responseCode, "Expected 303 after setting max workers; body:\n${bodyOf(ok)}")
        assertEquals("/workers?notice=maxWorkersSet&noticeId=6", ok.getHeaderField("Location"))
        assertEquals(listOf(6), maxWorkerCalls.toList())
        val (code, html) = get(port, "/workers?notice=maxWorkersSet&noticeId=6")
        assertEquals(200, code)
        assertTrue(
            html.contains("""<span class="banner-text">Set the render worker pool to at most 6 concurrent sessions.</span>"""),
            "Expected the composed max-workers notice; page was:\n$html"
        )

        val outOfRange = post(port, "/workers/max", "maxWorkers=40")
        assertEquals(400, outOfRange.responseCode, "Expected 400 when the service rejects the value.")
        val outOfRangeHtml = bodyOf(outOfRange)
        assertTrue(
            outOfRangeHtml.contains("""<div class="banner banner-error" role="alert"><span class="banner-label">Error</span><span class="banner-text">The screenshot service rejected max workers 40: maxWorkers must be between 1 and 16, but was 40.</span>"""),
            "Expected the service's full validation message in an error banner; page was:\n$outOfRangeHtml"
        )
        assertTrue(outOfRangeHtml.contains("""name="maxWorkers" size="4" value="40">"""), "Expected the rejected input kept in the field for correction.")
        assertFalse(outOfRangeHtml.contains("http-equiv=\"refresh\""), "An error page must not auto-refresh the error away.")

        val nonNumeric = post(port, "/workers/max", "maxWorkers=four")
        assertEquals(400, nonNumeric.responseCode, "Expected 400 for a non-numeric value.")
        val nonNumericHtml = bodyOf(nonNumeric)
        assertTrue(
            nonNumericHtml.contains("""<span class="banner-text">Cannot set max workers: &quot;maxWorkers&quot; must be a whole number, but was &quot;four&quot;.</span>"""),
            "Expected the descriptive non-numeric message; page was:\n$nonNumericHtml"
        )
        assertTrue(nonNumericHtml.contains("""name="maxWorkers" size="4" value="four">"""), "Expected the rejected input kept in the field.")

        val missing = post(port, "/workers/max", "")
        assertEquals(400, missing.responseCode, "Expected 400 for a missing value.")
        assertTrue(
            bodyOf(missing).contains("""<span class="banner-text">Cannot set max workers: the form is missing the required field &quot;maxWorkers&quot;.</span>"""),
            "Expected the descriptive missing-field message."
        )

        assertEquals(listOf(6), maxWorkerCalls.toList(), "Only the valid value may reach the backend.")
    } finally {
        server.stop()
    }
}
