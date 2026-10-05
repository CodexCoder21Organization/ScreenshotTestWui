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

fun imageApiUnobtainableReturnsDescriptive500Test() {
    val server = createServer(0, ManualClock(1735689600000L)) {
        throw IllegalStateException("Cannot open screenshot API for url://missing-screenshot-service/: no provider is registered.")
    }
    try {
        server.start()
        val port = (server.connectors[0] as org.eclipse.jetty.server.ServerConnector).localPort
        val conn = URL("http://localhost:$port/image?id=sess-1&key=page&kind=actual").openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false
            assertEquals(500, conn.responseCode)
            assertNull(conn.getHeaderField("Location"))
            assertEquals("text/plain;charset=utf-8", conn.contentType.lowercase().replace(" ", ""))
            val body = conn.errorStream.bufferedReader().use { it.readText() }
            assertEquals("Failed to load image for session \"sess-1\", key \"page\", kind \"actual\": Cannot open screenshot API for url://missing-screenshot-service/: no provider is registered.", body)
        } finally { conn.disconnect() }
    } finally { server.stop() }
}
