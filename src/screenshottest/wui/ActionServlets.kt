package screenshottest.wui

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * The management actions. Each is an HTML form POST that, on success, redirects (`303 See Other`)
 * back to the page it came from with an enumerated `?notice=` code, and on failure renders that page
 * directly with an error banner carrying the backend's full message and a matching status:
 *
 *  - `400` — a missing / malformed form field, or a value the service rejected
 *    ([IllegalArgumentException] from `setMaxWorkers`);
 *  - `404` — the service does not know the session ([IllegalArgumentException] from `cancelSession`
 *    / `deleteSession`);
 *  - `409` — the session is in a state that does not allow the action ([IllegalStateException]);
 *  - `502` — any other backend / RPC failure.
 *
 * The page to return to comes from the hidden `returnTo` form field (`list`, `workers`, `session`),
 * never from the Referer header, so a redirect can only land on one of this WUI's own pages.
 */

/** `POST /session/cancel` — fields `id`, `reason` (defaults to [DEFAULT_CANCEL_REASON]), `returnTo`. */
class CancelSessionServlet : HttpServlet() {
    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        if (rejectCrossSitePost(req, resp)) return
        val target = ReturnTarget.parse(req.getParameter("returnTo"))
        val id = req.getParameter("id")
        if (id.isNullOrBlank()) {
            renderActionError(resp, target, null, HttpServletResponse.SC_BAD_REQUEST,
                "Cannot cancel: the form is missing the required field \"id\" (the session id to cancel).")
            return
        }
        val reason = req.getParameter("reason")?.trim().takeUnless { it.isNullOrEmpty() } ?: DEFAULT_CANCEL_REASON
        val api = servletContext.getScreenshotTestApi()
        try {
            api.cancelSession(id, reason)
        } catch (e: Exception) {
            val (status, prefix) = classifyBackendFailure(e, "cancel session '$id'")
            renderActionError(resp, target, id, status, "$prefix: ${e.message ?: e.javaClass.name}")
            return
        }
        redirectWithNotice(resp, target, id, NOTICE_CANCELLED, id)
    }
}

/** `POST /session/delete` — fields `id`, `returnTo`. Always returns to the list or workers page. */
class DeleteSessionServlet : HttpServlet() {
    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        if (rejectCrossSitePost(req, resp)) return
        // The deleted session's own page no longer exists, so "session" returns to the list.
        val target = ReturnTarget.parse(req.getParameter("returnTo")).let { if (it == ReturnTarget.SESSION) ReturnTarget.LIST else it }
        val id = req.getParameter("id")
        if (id.isNullOrBlank()) {
            renderActionError(resp, target, null, HttpServletResponse.SC_BAD_REQUEST,
                "Cannot delete: the form is missing the required field \"id\" (the session id to delete).")
            return
        }
        val api = servletContext.getScreenshotTestApi()
        try {
            api.deleteSession(id)
        } catch (e: Exception) {
            val (status, prefix) = classifyBackendFailure(e, "delete session '$id'")
            renderActionError(resp, target, id, status, "$prefix: ${e.message ?: e.javaClass.name}")
            return
        }
        redirectWithNotice(resp, target, id, NOTICE_DELETED, id)
    }
}

/** `POST /workers/max` — field `maxWorkers`. Always returns to `/workers`. */
class SetMaxWorkersServlet : HttpServlet() {
    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        if (rejectCrossSitePost(req, resp)) return
        val raw = req.getParameter("maxWorkers")
        if (raw.isNullOrBlank()) {
            renderWorkersError(resp, HttpServletResponse.SC_BAD_REQUEST, raw,
                "Cannot set max workers: the form is missing the required field \"maxWorkers\".")
            return
        }
        val value = raw.trim().toIntOrNull()
        if (value == null) {
            renderWorkersError(resp, HttpServletResponse.SC_BAD_REQUEST, raw,
                "Cannot set max workers: \"maxWorkers\" must be a whole number, but was \"${raw.trim()}\".")
            return
        }
        val api = servletContext.getScreenshotTestApi()
        try {
            api.setMaxWorkers(value)
        } catch (e: IllegalArgumentException) {
            renderWorkersError(resp, HttpServletResponse.SC_BAD_REQUEST, raw,
                "The screenshot service rejected max workers $value: ${e.message ?: e.javaClass.name}")
            return
        } catch (e: Exception) {
            renderWorkersError(resp, HttpServletResponse.SC_BAD_GATEWAY, raw,
                "The screenshot service failed to set max workers to $value: ${e.message ?: e.javaClass.name}")
            return
        }
        resp.status = HttpServletResponse.SC_SEE_OTHER
        resp.setHeader("Location", "/workers?notice=$NOTICE_MAX_WORKERS_SET&noticeId=$value")
    }

    private fun renderWorkersError(resp: HttpServletResponse, status: Int, rawInput: String?, message: String) {
        val api = servletContext.getScreenshotTestApi()
        val clock = servletContext.getScreenshotTestClock()
        val page = renderWorkersPage(api, clock.currentTimeMillis(), Banner(Banner.Kind.ERROR, message), autoRefresh = false, maxWorkersInput = rawInput)
        writePage(resp, page, status)
    }
}

/**
 * Maps a backend exception from a session action to an HTTP status and a message prefix: the
 * service's documented [IllegalArgumentException] (unknown session) and [IllegalStateException]
 * (wrong state) are the operator's to act on; anything else is an upstream failure.
 */
private fun classifyBackendFailure(e: Exception, action: String): Pair<Int, String> = when (e) {
    is IllegalArgumentException -> HttpServletResponse.SC_NOT_FOUND to "Could not $action"
    is IllegalStateException -> HttpServletResponse.SC_CONFLICT to "Could not $action"
    else -> HttpServletResponse.SC_BAD_GATEWAY to "The screenshot service failed to $action"
}

private fun HttpServlet.renderActionError(
    resp: HttpServletResponse,
    target: ReturnTarget,
    sessionId: String?,
    status: Int,
    message: String,
) {
    val api = servletContext.getScreenshotTestApi()
    val nowMs = servletContext.getScreenshotTestClock().currentTimeMillis()
    val banner = Banner(Banner.Kind.ERROR, message)
    val page = when {
        target == ReturnTarget.WORKERS -> renderWorkersPage(api, nowMs, banner, autoRefresh = false)
        target == ReturnTarget.SESSION && !sessionId.isNullOrBlank() -> renderSessionDetailPage(api, nowMs, sessionId, banner)
        else -> renderSessionListPage(api, nowMs, banner)
    }
    writePage(resp, page, status)
}

/**
 * Writes [page] with the action's [actionStatus] — unless re-rendering the page itself failed (e.g.
 * the backend is down, `502`), in which case that failure's status wins; the page carries the
 * action's banner either way.
 */
private fun writePage(resp: HttpServletResponse, page: PageResult, actionStatus: Int) {
    resp.status = if (page.status == HttpServletResponse.SC_OK) actionStatus else page.status
    resp.contentType = "text/html; charset=UTF-8"
    resp.writer.write(page.html)
}

private fun redirectWithNotice(resp: HttpServletResponse, target: ReturnTarget, sessionId: String, notice: String, noticeId: String) {
    val base = when (target) {
        ReturnTarget.LIST -> "/?"
        ReturnTarget.WORKERS -> "/workers?"
        ReturnTarget.SESSION -> "/session?id=${urlEncode(sessionId)}&"
    }
    resp.status = HttpServletResponse.SC_SEE_OTHER
    resp.setHeader("Location", "${base}notice=$notice&noticeId=${urlEncode(noticeId)}")
}

/**
 * Refuses a form POST that a browser reports as coming from another site (`Sec-Fetch-Site:
 * cross-site` or `same-site`), so a page elsewhere — including another app under the same parent
 * domain — cannot make a visitor's browser cancel or delete sessions. Requests without the header
 * (non-browser clients, older browsers) and same-origin / user-initiated ones are allowed.
 *
 * @return true when the request was rejected and the response already written.
 */
private fun rejectCrossSitePost(req: HttpServletRequest, resp: HttpServletResponse): Boolean {
    val site = req.getHeader("Sec-Fetch-Site") ?: return false
    if (site == "same-origin" || site == "none") return false
    resp.status = HttpServletResponse.SC_FORBIDDEN
    resp.contentType = "text/plain; charset=UTF-8"
    resp.writer.write(
        "Refusing ${req.method} ${req.requestURI}: the browser reported it as a $site request " +
            "(Sec-Fetch-Site: $site, Origin: ${req.getHeader("Origin") ?: "absent"}). " +
            "Management actions must be submitted from this WUI's own pages."
    )
    return true
}
