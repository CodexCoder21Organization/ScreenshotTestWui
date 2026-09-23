package screenshottest.wui

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.json.JSONArray
import org.json.JSONObject
import screenshottest.api.ScreenshotTestApi

/** Seconds between automatic reloads of `/workers` (disabled with `?refresh=0`). */
const val WORKERS_REFRESH_SECONDS = 15

/**
 * The render worker pool page (`/workers`): the pool's capacity and load from
 * [ScreenshotTestApi.getWorkerPoolStatus], the sessions rendering now and those waiting in service
 * order (labels and timestamps joined from [ScreenshotTestApi.listSessions]), a form to change the
 * pool's maximum size, and a Cancel action per session.
 *
 * The page reloads itself every [WORKERS_REFRESH_SECONDS] seconds unless requested with `?refresh=0`
 * (so the paused view is itself linkable).
 */
class WorkersServlet : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val api = servletContext.getScreenshotTestApi()
        val clock = servletContext.getScreenshotTestClock()
        val page = renderWorkersPage(
            api, clock.currentTimeMillis(), null,
            autoRefresh = req.getParameter("refresh") != "0",
            noticeCode = req.getParameter("notice"), noticeId = req.getParameter("noticeId"),
        )
        resp.contentType = "text/html; charset=UTF-8"
        resp.status = page.status
        resp.writer.write(page.html)
    }
}

/**
 * Renders `/workers` with an optional [banner]. A backend failure yields a `502` error page that
 * still carries [banner].
 *
 * @param maxWorkersInput When non-null, pre-fills the max-workers field with the operator's rejected
 *   input so a validation error can be corrected in place rather than retyped.
 */
fun renderWorkersPage(
    api: ScreenshotTestApi,
    nowMs: Long,
    banner: Banner?,
    autoRefresh: Boolean,
    maxWorkersInput: String? = null,
    noticeCode: String? = null,
    noticeId: String? = null,
): PageResult {
    val pool: JSONObject
    val sessions: JSONArray
    val sessionsById = HashMap<String, JSONObject>()
    try {
        pool = JSONObject(api.getWorkerPoolStatus())
        val neededIds = HashSet<String>()
        for (field in listOf("running", "queued")) {
            val ids = pool.optJSONArray(field) ?: JSONArray()
            for (i in 0 until ids.length()) neededIds.add(ids.getString(i))
        }
        sessions = JSONArray(api.listSessions())
        for (i in 0 until sessions.length()) {
            val s = sessions.getJSONObject(i)
            val id = s.optString("sessionId", "")
            if (id in neededIds) sessionsById[id] = s
        }
    } catch (e: Exception) {
        return PageResult(
            HttpServletResponse.SC_BAD_GATEWAY,
            errorPage("Failed to load the render worker pool: ${escapeHtml(e.message ?: e.javaClass.name)}", banner),
        )
    }

    val maxWorkers = pool.optInt("maxWorkers", 0)
    val activeWorkers = pool.optInt("activeWorkers", 0)
    val queuedSessions = pool.optInt("queuedSessions", 0)
    val running = pool.optJSONArray("running") ?: JSONArray()
    val queued = pool.optJSONArray("queued") ?: JSONArray()
    val liveBanner = if (banner != null) banner else try {
        when (noticeCode) {
            NOTICE_MAX_WORKERS_SET -> noticeBanner(noticeCode, noticeId, poolSize = maxWorkers)
            else -> sessionNoticeBanner(api, sessions, noticeCode, noticeId)
        }
    } catch (e: Exception) {
        return PageResult(HttpServletResponse.SC_BAD_GATEWAY,
            errorPage("Failed to verify the worker pool notice: ${escapeHtml(e.message ?: e.javaClass.name)}"))
    }

    // Meta refresh targets the clean URL, so a post-action notice is shown once, not on every reload.
    val extraHead = if (autoRefresh) "\n<meta http-equiv=\"refresh\" content=\"$WORKERS_REFRESH_SECONDS;url=/workers\">" else ""

    val html = buildString {
        append(pageHeader("ScreenshotTest - Workers", extraHead))
        append("<div class=\"container\">")
        if (liveBanner != null) append(bannerHtml(liveBanner))
        append("<h1>Render Workers</h1>")
        append("<p class=\"subtitle\">Sessions render in parallel on a bounded pool of workers; the rest wait in the queue below in the order they will be served.</p>")
        if (autoRefresh) {
            append("<p class=\"refresh-note\">This page reloads every $WORKERS_REFRESH_SECONDS seconds. <a href=\"/workers?refresh=0\">Stop auto-refresh</a></p>")
        } else {
            append("<p class=\"refresh-note\">Auto-refresh is off. <a href=\"/workers\">Turn auto-refresh on</a></p>")
        }

        append("<div class=\"info-grid\" id=\"pool-summary\">")
        append(poolCard("Max workers", "$maxWorkers", "The most sessions the service renders at the same time. Further sessions wait in the queue."))
        append(poolCard("Active workers", "$activeWorkers / $maxWorkers", "Workers rendering a session right now, out of the maximum."))
        append(poolCard("Queued sessions", "$queuedSessions", "Sessions whose render was started but which are waiting for a free worker."))
        append("</div>")

        append("<div class=\"panel\" id=\"max-workers\"><h2>Pool size</h2>")
        append("<form method=\"post\" action=\"/workers/max\" class=\"form-row\">")
        append("<label for=\"max-workers-input\">Max workers</label>")
        append("<input id=\"max-workers-input\" class=\"text-input\" type=\"text\" inputmode=\"numeric\" name=\"maxWorkers\" size=\"4\" value=\"${escapeHtml(maxWorkersInput ?: maxWorkers.toString())}\">")
        append("<button type=\"submit\" class=\"btn btn-primary\">Set</button>")
        append("</form>")
        append("<div class=\"form-caption\">Takes effect immediately: raising it starts queued sessions at once; lowering it lets in-flight renders finish. The service enforces the allowed range and explains any value it rejects.</div>")
        append("</div>")

        append("<h2>Rendering now <span class=\"text-gray\">(${running.length()})</span></h2>")
        if (running.length() == 0) {
            append("<div class=\"empty-state\">No session is rendering right now.</div>")
        } else {
            append("<table id=\"running-table\"><thead><tr>")
            append("<th>Session</th><th>Label</th><th>Started</th><th>Running for</th><th>Actions</th>")
            append("</tr></thead><tbody>")
            for (i in 0 until running.length()) {
                val id = running.optString(i, "")
                val s = sessionsById[id]
                val startedAt = s?.optNullableEpochMs("startedAt")
                append("<tr>")
                append("<td class=\"nowrap mono\"><a href=\"/session?id=${urlEncode(id)}\">${escapeHtml(id)}</a></td>")
                append("<td>${labelHtml(s)}</td>")
                append(if (startedAt != null) "<td class=\"date-cell nowrap\" data-timestamp=\"$startedAt\"></td>" else "<td class=\"text-gray\">-</td>")
                append("<td class=\"nowrap\">${durationCellHtml(0L, startedAt, null, false, nowMs)}</td>")
                append("<td class=\"actions-cell\">${cancelFormHtml(id, ReturnTarget.WORKERS)}</td>")
                append("</tr>")
            }
            append("</tbody></table>")
        }

        append("<h2>Queue <span class=\"text-gray\">(${queued.length()})</span></h2>")
        if (queued.length() == 0) {
            append("<div class=\"empty-state\">No session is waiting for a worker.</div>")
        } else {
            append("<table id=\"queued-table\"><thead><tr>")
            append("<th>Position</th><th>Session</th><th>Label</th><th>Created</th><th>Waiting for</th><th>Actions</th>")
            append("</tr></thead><tbody>")
            for (i in 0 until queued.length()) {
                val id = queued.optString(i, "")
                val s = sessionsById[id]
                val createdAt = s?.optLong("createdAt", 0L) ?: 0L
                append("<tr>")
                append("<td class=\"nowrap\" title=\"Served ${if (i == 0) "next" else "after $i other queued session${if (i == 1) "" else "s"}"}.\" tabindex=\"0\">#${i + 1}</td>")
                append("<td class=\"nowrap mono\"><a href=\"/session?id=${urlEncode(id)}\">${escapeHtml(id)}</a></td>")
                append("<td>${labelHtml(s)}</td>")
                append(if (createdAt > 0) "<td class=\"date-cell nowrap\" data-timestamp=\"$createdAt\"></td>" else "<td class=\"text-gray\">-</td>")
                append("<td class=\"nowrap\">${durationCellHtml(createdAt, null, null, true, nowMs)}</td>")
                append("<td class=\"actions-cell\">${cancelFormHtml(id, ReturnTarget.WORKERS)}</td>")
                append("</tr>")
            }
            append("</tbody></table>")
        }

        append("</div>")
        append(pageFooter(nowMs))
    }
    return PageResult(HttpServletResponse.SC_OK, html)
}

private fun poolCard(label: String, value: String, explanation: String): String =
    "<div class=\"info-card\"><div class=\"info-label\">${escapeHtml(label)}" +
        "<span class=\"info-icon\" tabindex=\"0\" title=\"${escapeHtml(explanation)}\">i</span></div>" +
        "<div class=\"info-value\">${escapeHtml(value)}</div></div>"

/** The session's label, or a muted note when the session is no longer in the service's session list. */
private fun labelHtml(session: JSONObject?): String =
    if (session == null) "<span class=\"text-gray\">(no longer listed by the service)</span>"
    else escapeHtml(session.optString("label", ""))
