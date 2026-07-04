package screenshottest.wui

import community.kotlin.clocks.simple.Clock
import community.kotlin.clocks.simple.SystemClock
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import screenshottest.api.ScreenshotTestApi

/**
 * Convenience overload that accepts a pre-built [ScreenshotTestApi] instance and wraps it in a
 * provider lambda for the primary [createServer].
 *
 * @param clock The [Clock] the WUI reads "now" from. Defaults to a real [SystemClock]; pass a fixed
 *   clock (see [ScreenshotFixtureServer]) to render fully time-stable pages, so relative "created N
 *   ... ago" times no longer drift with the wall clock — the property golden screenshots rely on.
 */
fun createServer(port: Int, api: ScreenshotTestApi, clock: Clock = SystemClock()): Server =
    createServer(port, clock) { api }

/**
 * Creates a Jetty server with all WUI servlets registered.
 *
 * The [apiProvider] is called lazily on first request so the server can start and report healthy
 * while the P2P connection to `url://screenshottest/` is still being established.
 *
 * @param port The port to listen on (use 0 for a random free port).
 * @param clock The [Clock] the WUI reads "now" from (defaults to a real [SystemClock]). Threaded into
 *   every page footer so the browser-side relative-time math is anchored to the server's render
 *   instant.
 * @param apiProvider Provider that returns the [ScreenshotTestApi] (may block on the P2P connection).
 * @return A configured but not-yet-started Jetty [Server].
 */
fun createServer(port: Int, clock: Clock = SystemClock(), apiProvider: () -> ScreenshotTestApi): Server {
    val server = Server(port)
    val context = ServletContextHandler(ServletContextHandler.SESSIONS)
    context.contextPath = "/"
    context.setAttribute("screenshottest.api.provider", apiProvider)
    context.setAttribute("screenshottest.clock", clock)

    context.addServlet(ServletHolder(HealthServlet()), "/health")
    // Use "" (context-root mapping) rather than "/" (default-servlet catch-all) so unmapped paths
    // like /favicon.ico get a 404 instead of triggering a listSessions RPC on every browser request.
    context.addServlet(ServletHolder(SessionListServlet()), "")
    context.addServlet(ServletHolder(SessionListServlet()), "/sessions")
    context.addServlet(ServletHolder(SessionDetailServlet()), "/session")
    context.addServlet(ServletHolder(ImageServlet()), "/image")

    server.handler = context
    return server
}

/**
 * Retrieves the [ScreenshotTestApi] from the servlet context, connecting on first access. The
 * provider is called once and the result cached in the servlet context.
 */
fun jakarta.servlet.ServletContext.getScreenshotTestApi(): ScreenshotTestApi {
    val cached = getAttribute("screenshottest.api")
    if (cached is ScreenshotTestApi) return cached
    @Suppress("UNCHECKED_CAST")
    val provider = getAttribute("screenshottest.api.provider") as () -> ScreenshotTestApi
    val api = provider()
    setAttribute("screenshottest.api", api)
    return api
}

/**
 * The [Clock] the WUI reads "now" from, stashed in the servlet context by [createServer]. Defaults to
 * a real [SystemClock] when absent so a servlet exercised outside [createServer] still behaves.
 */
fun jakarta.servlet.ServletContext.getScreenshotTestClock(): Clock {
    val cached = getAttribute("screenshottest.clock")
    return if (cached is Clock) cached else SystemClock()
}

/**
 * ScreenshotTest Web UI — browses render sessions and their per-key verdicts / diff heatmaps.
 *
 * Connects to `url://screenshottest/` via UrlResolver RPC. The P2P connection is established lazily
 * on first request, not during startup, so the server starts quickly and responds to health checks.
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    println("Starting ScreenshotTest WUI on port $port...")

    val server = createServer(port) { ScreenshotTestClient.api }
    server.start()
    println("ScreenshotTest WUI running at http://0.0.0.0:$port/")
    server.join()
}
