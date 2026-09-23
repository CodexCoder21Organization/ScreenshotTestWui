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
import screenshottest.api.ScreenshotTestApi

/**
 * A management POST that the browser labels as coming from another site (Sec-Fetch-Site: cross-site
 * or same-site) is refused with 403 before the backend is touched, so another page cannot make a
 * visitor's browser cancel or delete sessions. Same-origin submissions proceed.
 */
fun crossSitePostIsRejectedTest() {
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
    // HttpURLConnection silently drops browser-only request headers such as Sec-Fetch-Site and
    // Origin (JDK "restricted headers"), so requests that must carry them are written over a plain
    // socket. Returns (status code, response body).
    fun rawPost(port: Int, path: String, form: String, headers: Map<String, String>): Pair<Int, String> {
        java.net.Socket("localhost", port).use { socket ->
            val body = form.toByteArray(Charsets.UTF_8)
            val head = StringBuilder()
            head.append("POST $path HTTP/1.0\r\nHost: localhost:$port\r\n")
            head.append("Content-Type: application/x-www-form-urlencoded\r\nContent-Length: ${body.size}\r\n")
            for ((k, v) in headers) head.append("$k: $v\r\n")
            head.append("\r\n")
            val out = socket.getOutputStream()
            out.write(head.toString().toByteArray(Charsets.UTF_8))
            out.write(body)
            out.flush()
            val response = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
            val status = response.substringBefore("\r\n").split(" ")[1].toInt()
            val rawBody = response.substringAfter("\r\n\r\n")
            // HTTP/1.0: the body is delimited by the connection closing, never chunked.
            return status to rawBody
        }
    }
    val server = createServer(0, api, ManualClock(1735689600000L)) // 2025-01-01T00:00:00Z
    server.start()
    try {
        val port = (server.connectors[0] as org.eclipse.jetty.server.ServerConnector).localPort
        val (crossCode, crossBody) = rawPost(port, "/session/cancel", "id=sess-q1", mapOf("Sec-Fetch-Site" to "cross-site", "Origin" to "https://evil.example"))
        assertEquals(403, crossCode, "Expected 403 for a cross-site POST; body:\n$crossBody")
        assertEquals(
            "Refusing POST /session/cancel: the browser reported it as a cross-site request (Sec-Fetch-Site: cross-site, Origin: https://evil.example). Management actions must be submitted from this WUI's own pages.",
            crossBody
        )
        val (sameSiteCode, sameSiteBody) = rawPost(port, "/session/delete", "id=sess-c1", mapOf("Sec-Fetch-Site" to "same-site"))
        assertEquals(403, sameSiteCode, "Expected 403 for a same-site (different subdomain) POST; body:\n$sameSiteBody")
        assertEquals(
            "Refusing POST /session/delete: the browser reported it as a same-site request (Sec-Fetch-Site: same-site, Origin: absent). Management actions must be submitted from this WUI's own pages.",
            sameSiteBody,
        )
        val (maxCode, maxBody) = rawPost(port, "/workers/max", "maxWorkers=2", mapOf("Sec-Fetch-Site" to "cross-site"))
        assertEquals(403, maxCode, "Expected 403 for a cross-site max-workers POST; body:\n$maxBody")
        assertEquals(
            "Refusing POST /workers/max: the browser reported it as a cross-site request (Sec-Fetch-Site: cross-site, Origin: absent). Management actions must be submitted from this WUI's own pages.",
            maxBody,
        )
        assertTrue(cancels.isEmpty() && deletes.isEmpty() && maxWorkerCalls.isEmpty(), "Rejected POSTs must not reach the backend.")

        val (sameOriginCode, sameOriginBody) = rawPost(port, "/session/cancel", "id=sess-q1", mapOf("Sec-Fetch-Site" to "same-origin"))
        assertEquals(303, sameOriginCode, "Expected a same-origin POST to proceed; body:\n$sameOriginBody")
        assertEquals(listOf("sess-q1" to "Cancelled from the management UI"), cancels.toList())
        val (noneCode, noneBody) = rawPost(port, "/workers/max", "maxWorkers=2", mapOf("Sec-Fetch-Site" to "none"))
        assertEquals(303, noneCode, "A user-initiated POST labelled none must proceed; body:\n$noneBody")
        val (missingCode, missingBody) = rawPost(port, "/session/delete", "id=sess-c1", emptyMap())
        assertEquals(303, missingCode, "A client without Sec-Fetch-Site must proceed; body:\n$missingBody")
        assertEquals(listOf(2), maxWorkerCalls.toList())
        assertEquals(listOf("sess-c1"), deletes.toList())
    } finally {
        server.stop()
    }
}
