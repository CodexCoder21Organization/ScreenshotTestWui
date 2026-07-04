package screenshottest.wui

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * Liveness probe. Returns `200 OK` with body `OK` without touching the backend, so ContainerNursery
 * can confirm the WUI is up before the (lazy) `url://screenshottest/` connection is established.
 */
class HealthServlet : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        resp.contentType = "text/plain"
        resp.writer.write("OK")
    }
}
