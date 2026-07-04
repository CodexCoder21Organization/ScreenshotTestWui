package screenshottest.wui

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.json.JSONObject

/**
 * Renders one session's detail page (`/session?id=<id>`): its status, the per-key verdict table, and
 * — for each captured key — inline `actual` / `golden` / `diff` thumbnails served by [ImageServlet].
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
        val api = servletContext.getScreenshotTestApi()
        val clock = servletContext.getScreenshotTestClock()

        val status: JSONObject = try {
            JSONObject(api.getSessionStatus(id))
        } catch (e: Exception) {
            resp.status = HttpServletResponse.SC_NOT_FOUND
            resp.writer.write(errorPage("Failed to load session \"${escapeHtml(id)}\": ${escapeHtml(e.message ?: e.javaClass.name)}"))
            return
        }

        val state = status.optString("state", "")
        val rendererVersion = status.optString("rendererVersion", "")
        val error = if (status.has("error") && !status.isNull("error")) status.optString("error", "") else ""

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
            append("<p><a href=\"/\">&larr; All sessions</a></p>")
            append("<h1 class=\"mono\">${escapeHtml(id)}</h1>")

            // Status cards.
            append("<div class=\"info-grid\">")
            append(infoCard("State", stateBadgeHtml(state)))
            if (mode.isNotBlank()) append(infoCard("Mode", modeBadgeHtml(mode)))
            append(infoCard("Renderer", "<span class=\"mono\" style=\"font-size:13px;\">${escapeHtml(rendererVersion)}</span>"))
            append("</div>")

            if (error.isNotBlank()) {
                append("<div class=\"info-card error-card\"><div class=\"info-label\">Error</div>")
                append("<div class=\"info-value text-red\">${escapeHtml(error)}</div></div>")
            }

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
            append(pageFooter(clock.currentTimeMillis()))
        }
        resp.writer.write(html)
    }
}

private fun infoCard(label: String, valueHtml: String): String =
    "<div class=\"info-card\"><div class=\"info-label\">${escapeHtml(label)}</div><div class=\"info-value\">$valueHtml</div></div>"

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
        // Which images exist for this key (per the getImageChunk contract):
        //  - "actual" always exists.
        //  - "golden"/"diff" exist only for a compare against a submitted golden (MATCH / DIFF);
        //    they are absent for RECORDED (record mode) and MISSING_GOLDEN keys.
        val hasGoldenAndDiff = verdict == "MATCH" || verdict == "DIFF"
        sb.append("<div class=\"shot-block\">")
        sb.append("<div class=\"shot-key\">${escapeHtml(key)} ${verdictBadgeHtml(verdict)}</div>")
        sb.append("<div class=\"shot-gallery\">")
        sb.append(shot(sessionId, key, "actual", "Actual"))
        if (hasGoldenAndDiff) {
            sb.append(shot(sessionId, key, "golden", "Golden"))
            sb.append(shot(sessionId, key, "diff", "Diff"))
        }
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
