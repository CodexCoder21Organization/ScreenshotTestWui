package screenshottest.wui

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.json.JSONArray
import screenshottest.api.ScreenshotTestApi

/** A rendered page and the HTTP status it should be served with. */
class PageResult(val status: Int, val html: String)

/**
 * Renders the session gallery landing page (`/`): every render session the service knows about,
 * newest first, as reported by [screenshottest.api.ScreenshotTestApi.listSessions].
 *
 * Columns: session id (links to the detail page), label, mode, state (plus the phase — Queued #n or
 * Rendering — while RUNNING), created, duration, and the row's action (Cancel while RUNNING, Delete
 * once terminal). The renderer identity is the session link's tooltip and a card on the detail page.
 */
class SessionListServlet : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val api = servletContext.getScreenshotTestApi()
        val clock = servletContext.getScreenshotTestClock()
        val page = renderSessionListPage(api, clock.currentTimeMillis(), noticeBanner(req.getParameter("notice"), req.getParameter("noticeId")))
        resp.contentType = "text/html; charset=UTF-8"
        resp.status = page.status
        resp.writer.write(page.html)
    }
}

/**
 * Renders the sessions list with an optional [banner] above the table. A backend failure yields a
 * `502` error page that still carries [banner], so an action's outcome is never lost.
 */
fun renderSessionListPage(api: ScreenshotTestApi, nowMs: Long, banner: Banner?): PageResult {
    val sessions: JSONArray = try {
        JSONArray(api.listSessions())
    } catch (e: Exception) {
        // A backend/RPC failure is an upstream problem, not a client error — surface it as 502 so
        // health checks and monitors see a non-200, rather than a misleading 200 with an error body.
        return PageResult(
            HttpServletResponse.SC_BAD_GATEWAY,
            errorPage("Failed to load sessions: ${escapeHtml(e.message ?: e.javaClass.name)}", banner),
        )
    }

    val html = buildString {
        append(pageHeader("ScreenshotTest - Sessions"))
        append("<div class=\"container\">")
        if (banner != null) append(bannerHtml(banner))
        append("<h1>Render Sessions</h1>")
        append("<p class=\"subtitle\">${sessions.length()} session${if (sessions.length() == 1) "" else "s"}, newest first &middot; <a href=\"/workers\">worker pool</a></p>")

        if (sessions.length() == 0) {
            append("<div class=\"empty-state\">No render sessions yet. Submit a WUI to url://screenshottest/ to capture golden screenshots.</div>")
        } else {
            append("<div class=\"table-scroll\"><table>")
            append("<thead><tr>")
            append("<th>Session</th><th>Label</th><th>Mode</th>")
            append("<th>State<span class=\"info-icon\" tabindex=\"0\" title=\"A RUNNING session also shows its phase: Queued #n (waiting for a free render worker, n = place in the service queue) or Rendering (a worker is capturing it now). A cancelled session is FAILED.\">i</span></th>")
            append("<th>Created</th>")
            append("<th>Duration<span class=\"info-icon\" tabindex=\"0\" title=\"Finished sessions: how long the render took (finished minus started). Rendering sessions: how long they have been running. Queued sessions: how long they have waited since creation.\">i</span></th>")
            append("<th>Actions</th>")
            append("</tr></thead><tbody>")
            for (i in 0 until sessions.length()) {
                val s = sessions.getJSONObject(i)
                val sessionId = s.optString("sessionId", "")
                val label = s.optString("label", "")
                val mode = s.optString("mode", "")
                val state = s.optString("state", "")
                val createdAt = s.optLong("createdAt", 0L)
                val renderer = s.optString("rendererVersion", "")
                val queuePosition = s.optNullableInt("queuePosition")
                val startedAt = s.optNullableEpochMs("startedAt")
                val finishedAt = s.optNullableEpochMs("finishedAt")
                val phase = sessionPhase(state, queuePosition)
                append("<tr class=\"${stateRowCssClass(state)}\">")
                append("<td class=\"nowrap mono\"><a href=\"/session?id=${urlEncode(sessionId)}\" title=\"Rendered by ${escapeHtml(renderer)}\">${escapeHtml(sessionId)}</a></td>")
                append("<td>${escapeHtml(label)}</td>")
                append("<td>${modeBadgeHtml(mode)}</td>")
                // The phase only adds information while RUNNING (queued vs rendering); for every other
                // state it would repeat the state badge.
                append("<td class=\"nowrap\">${stateBadgeHtml(state)}${if (state == "RUNNING") " " + phaseBadgeHtml(phase) else ""}</td>")
                append("<td class=\"date-cell nowrap\" data-timestamp=\"$createdAt\"></td>")
                append("<td class=\"nowrap\">${durationCellHtml(createdAt, startedAt, finishedAt, queuePosition != null, nowMs)}</td>")
                append("<td class=\"actions-cell\">${sessionActionsHtml(sessionId, state, ReturnTarget.LIST)}</td>")
                append("</tr>")
            }
            append("</tbody></table></div>")
        }
        append("</div>")
        append(pageFooter(nowMs))
    }
    return PageResult(HttpServletResponse.SC_OK, html)
}
