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
    try {
        server.start()
        val port = (server.connectors[0] as org.eclipse.jetty.server.ServerConnector).localPort
        for ((path, form, expectedStatus) in listOf(
            Triple("/session/cancel", "id=sess-1", 502),
            Triple("/session/delete", "id=sess-1", 502),
            Triple("/workers/max", "maxWorkers=3", 502),
            Triple("/session/cancel", "", 400),
            Triple("/session/delete", "", 400),
            Triple("/workers/max", "maxWorkers=invalid", 400),
        )) {
            val conn = URL("http://localhost:$port$path").openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(form.toByteArray()) }
                assertEquals(expectedStatus, conn.responseCode, "The action status for $path must survive a provider failure")
                assertNull(conn.getHeaderField("Location"))
                val body = conn.errorStream.bufferedReader().use { it.readText() }
                val expectedBanner = when {
                    form.isEmpty() && path.endsWith("cancel") -> "Cannot cancel: the form is missing the required field &quot;id&quot; (the session id to cancel)."
                    form.isEmpty() -> "Cannot delete: the form is missing the required field &quot;id&quot; (the session id to delete)."
                    form == "maxWorkers=invalid" -> "Cannot set max workers: &quot;maxWorkers&quot; must be a whole number, but was &quot;invalid&quot;."
                    path.endsWith("cancel") -> "The screenshot service failed to cancel session &#39;sess-1&#39;: connection failed"
                    path.endsWith("delete") -> "The screenshot service failed to delete session &#39;sess-1&#39;: connection failed"
                    else -> "The screenshot service failed to set max workers to 3: connection failed"
                }
                assertEquals(expectedBanner, Regex("""<span class="banner-text">([^<]*)</span>""").find(body)?.groupValues?.get(1), body)
            } finally { conn.disconnect() }
        }
        for ((path, expectedError) in listOf(
            "/" to "Failed to load sessions: connection failed",
            "/workers" to "Failed to load the render worker pool: connection failed",
            "/session?id=sess-1" to "Failed to load session \"sess-1\": connection failed",
        )) {
            val conn = URL("http://localhost:$port$path").openConnection() as HttpURLConnection
            try {
            assertEquals(502, conn.responseCode, "A provider failure while loading $path must return 502")
            val body = conn.errorStream.bufferedReader().use { it.readText() }
            val displayedError = Regex("""<div class="info-value text-red">([^<]*)</div>""")
                .find(body)?.groupValues?.get(1)
            assertEquals(expectedError, displayedError, "The page for $path must show the complete provider error")
            } finally { conn.disconnect() }
        }
    } finally {
        server.stop()
    }
}
