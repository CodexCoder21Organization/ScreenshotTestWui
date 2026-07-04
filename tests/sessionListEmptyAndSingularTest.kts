@file:WithArtifact("screenshottest.wui.buildMaven()")
@file:WithArtifact("screenshottest.api:screenshottest-api:0.0.1")
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
import kotlin.test.*
import java.net.HttpURLConnection
import java.net.URL
import screenshottest.api.ScreenshotTestApi

/**
 * The landing page's subtitle pluralizes correctly and, when there are no sessions, shows the empty
 * state instead of an empty table. Exercises the zero-session and single-session branches that the
 * multi-session test never reaches.
 */
fun sessionListEmptyAndSingularTest() {
    fun listApi(sessionsJson: String): ScreenshotTestApi = object : ScreenshotTestApi {
        override fun getRendererVersion(): String = "chromium-1228"
        override fun createSession(label: String, mode: String, mainClass: String, scenariosJson: String): String = throw UnsupportedOperationException()
        override fun uploadFileChunk(sessionId: String, role: String, fileName: String, chunkIndex: Int, chunk: ByteArray) = throw UnsupportedOperationException()
        override fun finalizeFile(sessionId: String, role: String, fileName: String, totalChunks: Int, sha256Hex: String) = throw UnsupportedOperationException()
        override fun startRender(sessionId: String) = throw UnsupportedOperationException()
        override fun getSessionStatus(sessionId: String): String = throw UnsupportedOperationException()
        override fun getResultsJson(sessionId: String): String = throw UnsupportedOperationException()
        override fun getImageChunk(sessionId: String, key: String, kind: String, offset: Long, length: Int): ByteArray? = null
        override fun listSessions(): String = sessionsJson
        override fun deleteSession(sessionId: String) {}
    }

    fun fetch(api: ScreenshotTestApi): String {
        val server = createServer(0, api)
        server.start()
        try {
            val port = (server.connectors[0] as org.eclipse.jetty.server.ServerConnector).localPort
            val conn = URL("http://localhost:$port/").openConnection() as HttpURLConnection
            assertEquals(200, conn.responseCode, "Expected HTTP 200 for the sessions list; got ${conn.responseCode}")
            return conn.inputStream.bufferedReader().readText()
        } finally {
            server.stop()
        }
    }

    // Zero sessions: singular-vs-plural subtitle uses "sessions", and the empty state replaces the table.
    val empty = fetch(listApi("[]"))
    assertTrue(empty.contains("0 sessions, newest first"), "Expected '0 sessions, newest first'; page was:\n$empty")
    assertTrue(
        empty.contains("No render sessions yet. Submit a WUI to url://screenshottest/ to capture golden screenshots."),
        "Expected the empty-state message; page was:\n$empty"
    )
    assertFalse(empty.contains("<table>"), "Expected no table when there are no sessions.")

    // Exactly one session: subtitle is singular.
    val single = fetch(listApi("""[{"sessionId":"sess-1","label":"only one","mode":"compare","state":"COMPLETED","createdAt":1735689600000,"rendererVersion":"chromium-1228"}]"""))
    assertTrue(single.contains("1 session, newest first"), "Expected singular '1 session, newest first'; page was:\n$single")
    assertFalse(single.contains("1 sessions"), "Subtitle must be singular for a single session.")
}
