package screenshottest.wui

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.json.JSONObject
import org.json.JSONArray
import screenshottest.api.ScreenshotTestApi

/**
 * Renders one session's detail page (`/session?id=<id>`): its status, the per-key verdict table, and
 * — for each captured key — inline `actual` thumbnails plus, where the service produced them, `golden`
 * (MATCH / DIFF keys) and `diff` (DIFF keys) thumbnails, all served by [ImageServlet].
 *
 * Status comes from [screenshottest.api.ScreenshotTestApi.getSessionStatus]; the verdicts and pixel
 * measurements come from [screenshottest.api.ScreenshotTestApi.getResultsJson], which only exists
 * once the session is `COMPLETED`.
 */
class SessionDetailServlet : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        resp.contentType = "text/html; charset=UTF-8"
        val id = req.getParameter("id")
        if (id.isNullOrBlank()) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            resp.writer.write(errorPage("Missing required query parameter \"id\" (the session id to display)."))
            return
        }
        val api = try {
            servletContext.getScreenshotTestApi()
        } catch (e: Exception) {
            resp.status = HttpServletResponse.SC_BAD_GATEWAY
            resp.writer.write(errorPage("Failed to load session \"${escapeHtml(id)}\": ${escapeHtml(e.message ?: e.javaClass.name)}"))
            return
        }
        val clock = servletContext.getScreenshotTestClock()
        val page = renderSessionDetailPage(
            api, clock.currentTimeMillis(), id, null,
            req.getParameter("notice"), req.getParameter("noticeId"),
        )
        resp.status = page.status
        resp.writer.write(page.html)
    }
}

/**
 * Renders session [id]'s detail page with an optional [banner]. A session the service cannot load
 * yields a `404` error page that still carries [banner].
 */
fun renderSessionDetailPage(
    api: ScreenshotTestApi, nowMs: Long, id: String, banner: Banner?,
    noticeCode: String? = null, noticeId: String? = null,
): PageResult {
    val status: JSONObject = try {
        JSONObject(api.getSessionStatus(id))
    } catch (e: Exception) {
        return PageResult(
            HttpServletResponse.SC_NOT_FOUND,
            errorPage("Failed to load session \"${escapeHtml(id)}\": ${escapeHtml(e.message ?: e.javaClass.name)}", banner),
        )
    }

    val state = status.optString("state", "")
    val rendererVersion = status.optString("rendererVersion", "")
    val error = if (status.has("error") && !status.isNull("error")) status.optString("error", "") else ""
    val queuePosition = status.optNullableInt("queuePosition")
    val startedAt = status.optNullableEpochMs("startedAt")
    val finishedAt = status.optNullableEpochMs("finishedAt")
    val phase = sessionPhase(state, queuePosition)
    val createdAt = if (queuePosition != null) try {
        val recent = JSONArray(api.listSessions())
        var found = 0L
        for (i in 0 until recent.length()) {
            val summary = recent.getJSONObject(i)
            if (summary.optString("sessionId") == id) {
                found = summary.optLong("createdAt", 0L)
                break
            }
        }
        found
    } catch (e: Exception) {
        return PageResult(HttpServletResponse.SC_BAD_GATEWAY,
            errorPage("Failed to load the creation time for queued session \"${escapeHtml(id)}\": ${escapeHtml(e.message ?: e.javaClass.name)}", banner))
    } else 0L
    val liveBanner = banner ?: noticeBanner(noticeCode, noticeId, sessionStatus = status, isListed = noticeId == id)
    val durationHtml = if (queuePosition != null && createdAt <= 0L) {
        "<span title=\"Duration: waiting for a render worker; the creation time is unavailable.\" tabindex=\"0\">-</span>"
    } else durationCellHtml(createdAt, startedAt, finishedAt, queuePosition != null, nowMs)

    // Results (verdicts + measurements + images) only exist once the render is COMPLETED.
    var results: JSONObject? = null
    var resultsError: String? = null
    if (state == "COMPLETED") {
        try {
            results = JSONObject(api.getResultsJson(id))
        } catch (e: Exception) {
            resultsError = e.message ?: e.javaClass.name
        }
    }
    val mode = results?.optString("mode", "") ?: ""

    val html = buildString {
        // pageHeader escapes the whole title, so pass the raw id (escaping it here would double-escape).
        append(pageHeader("ScreenshotTest - Session $id"))
        append("<div class=\"container\">")
        if (liveBanner != null) append(bannerHtml(liveBanner))
        append("<p><a href=\"/\">&larr; All sessions</a></p>")
        append("<h1 class=\"mono\">${escapeHtml(id)}</h1>")

        // Status cards.
        append("<div class=\"info-grid\">")
        append(infoCard("State", stateBadgeHtml(state)))
        append(infoCard("Phase", phaseBadgeHtml(phase)))
        append(infoCard("Queue position", queuePositionHtml(queuePosition)))
        if (mode.isNotBlank()) append(infoCard("Mode", modeBadgeHtml(mode)))
        append(infoCard("Renderer", "<span class=\"mono\" style=\"font-size:13px;\">${escapeHtml(rendererVersion)}</span>"))
        append("</div>")
        append("<div class=\"info-grid\" id=\"timing\">")
        append(infoCardRaw("Started", timestampHtml(startedAt, "Not started yet")))
        append(infoCardRaw("Finished", timestampHtml(finishedAt, "Not finished yet")))
        append(infoCard("Duration", durationHtml))
        append("</div>")

        if (error.isNotBlank()) {
            append("<div class=\"info-card error-card\"><div class=\"info-label\">Error</div>")
            append("<div class=\"info-value text-red\">${escapeHtml(error)}</div></div>")
        }

        appendSessionActions(this, id, state)

        when {
            results != null -> appendResults(this, id, results)
            resultsError != null ->
                append("<div class=\"empty-state\">Results are unavailable for this session: ${escapeHtml(resultsError)}</div>")
            state == "FAILED" ->
                append("<div class=\"empty-state\">This session failed before producing results.</div>")
            else ->
                append("<div class=\"empty-state\">Results will appear here once the session reaches the COMPLETED state (currently ${escapeHtml(state)}).</div>")
        }

        append("</div>")
        append(pageFooter(nowMs))
    }
    return PageResult(HttpServletResponse.SC_OK, html)
}

private fun queuePositionHtml(queuePosition: Int?): String =
    if (queuePosition == null) {
        "<span class=\"text-gray\" title=\"This session is not waiting for a render worker.\" tabindex=\"0\">-</span>"
    } else {
        "<span title=\"0-based queuePosition $queuePosition: $queuePosition session${if (queuePosition == 1) "" else "s"} will be given a worker before this one.\" tabindex=\"0\">#${queuePosition + 1}</span>"
    }

/** A stacked date-cell (relative / local / UTC, rendered by the footer script) or a muted placeholder. */
private fun timestampHtml(epochMs: Long?, absentText: String): String =
    if (epochMs == null) "<div class=\"info-value text-gray\">${escapeHtml(absentText)}</div>"
    else "<div class=\"info-value date-cell\" data-timestamp=\"$epochMs\"></div>"

/**
 * The page-level action panel: Cancel (with an editable reason) while RUNNING; Delete once terminal.
 * Cancelling returns to this page; deleting returns to the sessions list (this page stops existing).
 */
private fun appendSessionActions(sb: StringBuilder, id: String, state: String) {
    if (state == "RUNNING") {
        sb.append("<div class=\"panel\" id=\"session-actions\"><h2>Cancel this session</h2>")
        sb.append("<form method=\"post\" action=\"/session/cancel\" class=\"form-row\">")
        sb.append("<input type=\"hidden\" name=\"id\" value=\"${escapeHtml(id)}\">")
        sb.append("<input type=\"hidden\" name=\"returnTo\" value=\"${ReturnTarget.SESSION.formValue}\">")
        sb.append("<label for=\"cancel-reason\">Reason</label>")
        sb.append("<input id=\"cancel-reason\" class=\"text-input\" type=\"text\" name=\"reason\" size=\"48\" maxlength=\"$MAX_CANCEL_REASON_LENGTH\" value=\"${escapeHtml(DEFAULT_CANCEL_REASON)}\">")
        sb.append("<button type=\"submit\" class=\"btn btn-danger\">Cancel session</button>")
        sb.append("</form>")
        sb.append("<div class=\"form-caption\">A queued session is dropped before it starts; a rendering one is stopped. Either way it becomes FAILED with this reason recorded as its error.</div>")
        sb.append("</div>")
    } else if (isTerminalState(state)) {
        sb.append("<div class=\"panel\" id=\"session-actions\"><h2>Delete this session</h2>")
        sb.append("<div class=\"form-row\">${deleteFormHtml(id, ReturnTarget.LIST)}")
        sb.append("<span class=\"form-caption\">Removes the session and its stored screenshots from the service. This cannot be undone.</span></div>")
        sb.append("</div>")
    }
}

private fun infoCard(label: String, valueHtml: String): String =
    "<div class=\"info-card\"><div class=\"info-label\">${escapeHtml(label)}</div><div class=\"info-value\">$valueHtml</div></div>"

/** Like [infoCard] but [valueBlockHtml] supplies its own `info-value` block. */
private fun infoCardRaw(label: String, valueBlockHtml: String): String =
    "<div class=\"info-card\"><div class=\"info-label\">${escapeHtml(label)}</div>$valueBlockHtml</div>"

/** Appends the per-key verdict table and the inline actual/golden/diff gallery for each key. */
private fun appendResults(sb: StringBuilder, sessionId: String, results: JSONObject) {
    val rows = results.optJSONArray("results")
    if (rows == null || rows.length() == 0) {
        sb.append("<div class=\"empty-state\">This session produced no image keys.</div>")
        return
    }

    sb.append("<h2>Results</h2>")
    sb.append("<table id=\"results-table\">")
    sb.append("<thead><tr>")
    sb.append("<th>Key</th><th>Verdict</th><th>Diff pixels</th><th>Max &Delta;</th><th>Golden</th><th>Actual</th>")
    sb.append("</tr></thead><tbody>")
    for (i in 0 until rows.length()) {
        val r = rows.getJSONObject(i)
        val key = r.optString("key", "")
        val verdict = r.optString("verdict", "")
        val diffPixels = r.optLong("diffPixelCount", 0L)
        val totalPixels = r.optLong("totalPixels", 0L)
        val maxDelta = r.optInt("maxChannelDelta", 0)
        val gw = r.optInt("goldenWidth", 0)
        val gh = r.optInt("goldenHeight", 0)
        val aw = r.optInt("actualWidth", 0)
        val ah = r.optInt("actualHeight", 0)
        val diffText = if (totalPixels > 0) "${formatCount(diffPixels)} / ${formatCount(totalPixels)}" else "-"
        val goldenDim = if (gw > 0 && gh > 0) "$gw&times;$gh" else "-"
        val actualDim = if (aw > 0 && ah > 0) "$aw&times;$ah" else "-"
        sb.append("<tr>")
        sb.append("<td class=\"mono\">${escapeHtml(key)}</td>")
        sb.append("<td>${verdictBadgeHtml(verdict)}</td>")
        sb.append("<td class=\"nowrap\">$diffText</td>")
        sb.append("<td>$maxDelta</td>")
        sb.append("<td class=\"nowrap\">$goldenDim</td>")
        sb.append("<td class=\"nowrap\">$actualDim</td>")
        sb.append("</tr>")
    }
    sb.append("</tbody></table>")

    sb.append("<h2>Screenshots</h2>")
    for (i in 0 until rows.length()) {
        val r = rows.getJSONObject(i)
        val key = r.optString("key", "")
        val verdict = r.optString("verdict", "")
        // Which images exist for this key:
        //  - "actual" always exists.
        //  - "golden" exists only for a compare against a submitted golden (MATCH / DIFF); it is
        //    absent for RECORDED (record mode) and MISSING_GOLDEN keys.
        //  - "diff" exists only for DIFF: the service writes a diff heatmap only when pixels differ,
        //    so a MATCH key has none and an <img> for it could only fail.
        val hasGolden = verdict == "MATCH" || verdict == "DIFF"
        val hasDiff = verdict == "DIFF"
        sb.append("<div class=\"shot-block\">")
        sb.append("<div class=\"shot-key\">${escapeHtml(key)} ${verdictBadgeHtml(verdict)}</div>")
        sb.append("<div class=\"shot-gallery\">")
        sb.append(shot(sessionId, key, "actual", "Actual"))
        if (hasGolden) sb.append(shot(sessionId, key, "golden", "Golden"))
        if (hasDiff) sb.append(shot(sessionId, key, "diff", "Diff"))
        sb.append("</div></div>")
    }
}

private fun shot(sessionId: String, key: String, kind: String, label: String): String {
    // HTML-attribute-safe query separators (&amp;); the browser decodes them back to & when it
    // requests the image, so /image sees id/key/kind as normal query parameters.
    val src = "/image?id=${urlEncode(sessionId)}&amp;key=${urlEncode(key)}&amp;kind=${urlEncode(kind)}"
    // Eager loading (no loading="lazy"): a full-page golden capture must include every thumbnail even
    // when it sits below the fold, and there are only a handful of images per page.
    return "<div class=\"shot\"><div class=\"shot-label\">${escapeHtml(label)}</div>" +
        "<a href=\"$src\" target=\"_blank\" rel=\"noopener\"><img src=\"$src\" alt=\"${escapeHtml(label)} screenshot for ${escapeHtml(key)}\"></a></div>"
}

/** Formats a non-negative count with thousands separators (e.g. 1152000 -> "1,152,000"). */
private fun formatCount(n: Long): String = "%,d".format(n)
