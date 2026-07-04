package screenshottest.wui

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.json.JSONArray

/**
 * Renders the session gallery landing page (`/`): every render session the service knows about,
 * newest first, as reported by [screenshottest.api.ScreenshotTestApi.listSessions].
 *
 * Columns: session id (links to the detail page), label, mode, state, created (relative), renderer.
 */
class SessionListServlet : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        resp.contentType = "text/html; charset=UTF-8"
        val api = servletContext.getScreenshotTestApi()
        val clock = servletContext.getScreenshotTestClock()

        val sessions: JSONArray = try {
            JSONArray(api.listSessions())
        } catch (e: Exception) {
            // A backend/RPC failure is an upstream problem, not a client error — surface it as 502 so
            // health checks and monitors see a non-200, rather than a misleading 200 with an error body.
            resp.status = HttpServletResponse.SC_BAD_GATEWAY
            resp.writer.write(errorPage("Failed to load sessions: ${escapeHtml(e.message ?: e.javaClass.name)}"))
            return
        }

        val html = buildString {
            append(pageHeader("ScreenshotTest - Sessions"))
            append("<div class=\"container\">")
            append("<h1>Render Sessions</h1>")
            append("<p class=\"subtitle\">${sessions.length()} session${if (sessions.length() == 1) "" else "s"}, newest first</p>")

            if (sessions.length() == 0) {
                append("<div class=\"empty-state\">No render sessions yet. Submit a WUI to url://screenshottest/ to capture golden screenshots.</div>")
            } else {
                append("<table>")
                append("<thead><tr>")
                append("<th>Session</th><th>Label</th><th>Mode</th><th>State</th><th>Created</th><th>Renderer</th>")
                append("</tr></thead><tbody>")
                for (i in 0 until sessions.length()) {
                    val s = sessions.getJSONObject(i)
                    val sessionId = s.optString("sessionId", "")
                    val label = s.optString("label", "")
                    val mode = s.optString("mode", "")
                    val state = s.optString("state", "")
                    val createdAt = s.optLong("createdAt", 0L)
                    val renderer = s.optString("rendererVersion", "")
                    append("<tr class=\"${stateRowCssClass(state)}\">")
                    append("<td class=\"nowrap mono\"><a href=\"/session?id=${urlEncode(sessionId)}\">${escapeHtml(sessionId)}</a></td>")
                    append("<td>${escapeHtml(label)}</td>")
                    append("<td>${modeBadgeHtml(mode)}</td>")
                    append("<td>${stateBadgeHtml(state)}</td>")
                    append("<td class=\"date-cell nowrap\" data-timestamp=\"$createdAt\"></td>")
                    append("<td class=\"mono\" style=\"font-size:12px;color:var(--text-secondary);\">${escapeHtml(renderer)}</td>")
                    append("</tr>")
                }
                append("</tbody></table>")
            }
            append("</div>")
            append(pageFooter(clock.currentTimeMillis()))
        }
        resp.writer.write(html)
    }
}
