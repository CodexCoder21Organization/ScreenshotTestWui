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

fun actionProviderFailureReturns502Test() {
    val server = createServer(0, ManualClock(1735689600000L)) {
        throw IllegalStateException("connection failed")
    }
    server.start()
    try {
        val port = (server.connectors[0] as org.eclipse.jetty.server.ServerConnector).localPort
        for ((path, form) in listOf(
            "/session/cancel" to "id=sess-1",
            "/session/delete" to "id=sess-1",
            "/workers/max" to "maxWorkers=3",
            "/session/cancel" to "",
            "/session/delete" to "",
            "/workers/max" to "maxWorkers=invalid",
        )) {
            val conn = URL("http://localhost:$port$path").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(form.toByteArray()) }
            assertEquals(502, conn.responseCode, "A provider failure for $path must return 502")
            val body = conn.errorStream.bufferedReader().readText()
            assertTrue(body.contains("connection failed"), "The provider's full message must appear in the page: $body")
        }
        for ((path, expectedError) in listOf(
            "/" to "Failed to load sessions: connection failed",
            "/workers" to "Failed to load the render worker pool: connection failed",
            "/session?id=sess-1" to "Failed to load session \"sess-1\": connection failed",
        )) {
            val conn = URL("http://localhost:$port$path").openConnection() as HttpURLConnection
            assertEquals(502, conn.responseCode, "A provider failure while loading $path must return 502")
            val body = conn.errorStream.bufferedReader().readText()
            val displayedError = Regex("""<div class="info-value text-red">([^<]*)</div>""")
                .find(body)?.groupValues?.get(1)
            assertEquals(expectedError, displayedError, "The page for $path must show the complete provider error")
        }
    } finally {
        server.stop()
    }
}
