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
import kotlin.test.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * The hosted screenshottest runner launches the fixture's `main` with `PORT=0` and renders only against
 * the endpoint the child announces on stdout: exactly one
 * `SCREENSHOTTEST_ENDPOINT {"host":"127.0.0.1","port":<bound port>}` line followed by exactly one
 * `SCREENSHOTTEST_ENDPOINTS_COMPLETE` line. This launches the real fixture main in a child JVM the
 * same way and checks the announcement names a port that actually serves the sessions list.
 */
fun fixtureMainAnnouncesBoundEndpointTest() {
    val javaBin = File(System.getProperty("java.home"), "bin/java").absolutePath
    val process = ProcessBuilder(
        javaBin, "-cp", System.getProperty("java.class.path"), "screenshottest.wui.ScreenshotFixtureServerKt",
    ).apply {
        environment()["PORT"] = "0"
        redirectError(ProcessBuilder.Redirect.DISCARD)
    }.start()
    try {
        val reader = process.inputStream.bufferedReader()
        val stdout = mutableListOf<String>()
        while (true) {
            val line = reader.readLine()
                ?: fail("The fixture main exited (or closed stdout) before reporting that it is running; stdout was:\n${stdout.joinToString("\n")}")
            stdout += line
            // The fixture's last startup line; everything the runner reads is printed before it.
            if (line.startsWith("Fixture WUI running at ")) break
        }
        assertEquals(
            1, stdout.count { it == "SCREENSHOTTEST_ENDPOINTS_COMPLETE" },
            "The fixture main must print exactly one SCREENSHOTTEST_ENDPOINTS_COMPLETE line, but stdout was:\n${stdout.joinToString("\n")}",
        )
        assertTrue(
            stdout.indexOfFirst { it.startsWith("SCREENSHOTTEST_ENDPOINT ") } < stdout.indexOf("SCREENSHOTTEST_ENDPOINTS_COMPLETE"),
            "The endpoint announcement must precede SCREENSHOTTEST_ENDPOINTS_COMPLETE, but stdout was:\n${stdout.joinToString("\n")}",
        )
        val endpointLines = stdout.filter { it.startsWith("SCREENSHOTTEST_ENDPOINT ") }
        assertEquals(
            1, endpointLines.size,
            "The fixture main must print exactly one SCREENSHOTTEST_ENDPOINT line, but stdout was:\n${stdout.joinToString("\n")}",
        )
        assertEquals(0, stdout.indexOf(endpointLines.single()), "The bound endpoint announcement must be the first stdout line: $stdout")
        assertEquals(1, stdout.indexOf("SCREENSHOTTEST_ENDPOINTS_COMPLETE"), "The completion marker must immediately follow the endpoint announcement: $stdout")
        val match = assertNotNull(
            Regex("""SCREENSHOTTEST_ENDPOINT \{"host":"127\.0\.0\.1","port":([1-9][0-9]*)}""").matchEntire(endpointLines.single()),
            "The fixture main must announce its bound IPv4 loopback endpoint, but printed '${endpointLines.single()}'.",
        )
        val port = match.groupValues[1].toInt()
        assertTrue(port in 1..65535, "The announced port must be a real assigned port, but was $port")

        val conn = URL("http://127.0.0.1:$port/").openConnection() as HttpURLConnection
        val body = conn.inputStream.bufferedReader().readText()
        assertEquals(200, conn.responseCode, "Expected HTTP 200 from the announced endpoint http://127.0.0.1:$port/ but got ${conn.responseCode}")
        assertTrue(
            body.contains("sess-a1b2c3d4"),
            "The announced endpoint must serve the fixture's sessions list, but the body was:\n$body",
        )
    } finally {
        process.destroy()
        if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly().waitFor()
    }
}
