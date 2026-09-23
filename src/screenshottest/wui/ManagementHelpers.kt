package screenshottest.wui

import org.json.JSONArray
import org.json.JSONObject
import screenshottest.api.ScreenshotTestApi
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Shared pieces of the management UI: the session *phase* derived from `state` + `queuePosition`,
 * duration formatting against the injected clock, outcome banners, the Cancel / Delete action forms,
 * and the enumerated post-action notices.
 */

/** The reason recorded when a session is cancelled without the operator typing one. */
const val DEFAULT_CANCEL_REASON = "Cancelled from the management UI"

/** Maximum number of characters accepted from the editable cancellation reason field. */
const val MAX_CANCEL_REASON_LENGTH = 512

/** An outcome banner shown at the top of a page: a success notice or an error. */
class Banner(val kind: Kind, val text: String) {
    enum class Kind { NOTICE, ERROR }
}

/** Renders [banner] with a keyboard-reachable dismiss button. [Banner.text] is escaped here. */
fun bannerHtml(banner: Banner): String {
    val (cls, label, role) = when (banner.kind) {
        Banner.Kind.NOTICE -> Triple("banner-notice", "Done", "status")
        Banner.Kind.ERROR -> Triple("banner-error", "Error", "alert")
    }
    return "<div class=\"banner $cls\" role=\"$role\">" +
        "<span class=\"banner-label\">$label</span>" +
        "<span class=\"banner-text\">${escapeHtml(banner.text)}</span>" +
        "<button type=\"button\" class=\"banner-dismiss\" aria-label=\"Dismiss\" title=\"Dismiss\" " +
        "onclick=\"this.parentNode.parentNode.removeChild(this.parentNode)\">&times;</button>" +
        "</div>"
}

/**
 * A session's user-facing phase. The service's state enum has no QUEUED value: a session waiting for
 * a render worker is `RUNNING` with a non-null `queuePosition`, so the phase is derived from both.
 */
class SessionPhase(val label: String, val cssClass: String, val tooltip: String)

/**
 * Derives the phase of a session from its [state] and its 0-based [queuePosition] (null unless the
 * session is waiting for a worker).
 */
fun sessionPhase(state: String, queuePosition: Int?): SessionPhase = when {
    state == "RUNNING" && queuePosition != null -> SessionPhase(
        "Queued #${queuePosition + 1}",
        "badge-queued",
        "Waiting for a free render worker: ${ordinal(queuePosition + 1)} in the service's queue " +
            "(${queuePosition} session${if (queuePosition == 1) "" else "s"} ahead of it).",
    )
    state == "RUNNING" -> SessionPhase("Rendering", "badge-running", "A render worker is capturing this session's screenshots now.")
    state == "COMPLETED" -> SessionPhase("Completed", "badge-completed", "The render finished; per-key verdicts are available.")
    state == "FAILED" -> SessionPhase("Failed", "badge-failed", "The session ended without results (it failed or was cancelled); see its error.")
    state == "UPLOADING" -> SessionPhase("Uploading", "badge-provisioning", "The client is still uploading the session's classpath and goldens.")
    state == "PENDING" -> SessionPhase("Pending", "badge-pending", "The session was created but its render has not been started yet.")
    else -> SessionPhase(state, "badge-pending", "Unrecognised session state \"$state\".")
}

/** Inline badge (with a data-cell tooltip) for [phase]. */
fun phaseBadgeHtml(phase: SessionPhase): String =
    "<span class=\"badge ${phase.cssClass}\" title=\"${escapeHtml(phase.tooltip)}\" tabindex=\"0\">${escapeHtml(phase.label)}</span>"

/** True for the states the service treats as final (only these sessions may be deleted). */
fun isTerminalState(state: String): Boolean = state == "COMPLETED" || state == "FAILED"

/** Reads an optional nullable integer field (absent or JSON null -> null). */
fun JSONObject.optNullableInt(key: String): Int? = if (!has(key) || isNull(key)) null else getInt(key)

/** Reads an optional nullable epoch-millisecond field (absent, JSON null, or <= 0 -> null). */
fun JSONObject.optNullableEpochMs(key: String): Long? =
    if (!has(key) || isNull(key)) null else getLong(key).takeIf { it > 0 }

private val UTC_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC)

/** Formats an epoch-ms instant as an absolute UTC timestamp (`2026-01-01 00:00:00 UTC`). */
fun formatUtc(epochMs: Long): String = UTC_FORMAT.format(Instant.ofEpochMilli(epochMs))

/**
 * Formats a non-negative millisecond duration at two-unit precision: `42s`, `3m 05s`, `2h 07m`,
 * `1d 03h`. Negative inputs (clock skew between the service and the WUI) render as `0s`.
 */
fun formatDuration(ms: Long): String {
    val totalSeconds = maxOf(0L, ms) / 1000
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return when {
        days > 0 -> "${days}d %02dh".format(hours)
        hours > 0 -> "${hours}h %02dm".format(minutes)
        minutes > 0 -> "${minutes}m %02ds".format(seconds)
        else -> "${seconds}s"
    }
}

/**
 * The duration cell for one session: how long it rendered (finished), has been rendering (started,
 * not finished), or has been waiting (queued) — measured against [nowMs], the injected clock — with
 * a data-cell tooltip carrying the absolute instants it was computed from.
 */
fun durationCellHtml(createdAt: Long, startedAt: Long?, finishedAt: Long?, queued: Boolean, nowMs: Long): String {
    val (text, tooltip) = when {
        startedAt != null && finishedAt != null ->
            formatDuration(finishedAt - startedAt) to
                "Duration: rendered for ${formatDuration(finishedAt - startedAt)}, from ${formatUtc(startedAt)} to ${formatUtc(finishedAt)}."
        startedAt != null ->
            "running for ${formatDuration(nowMs - startedAt)}" to
                "Duration: rendering since ${formatUtc(startedAt)}; measured at ${formatUtc(nowMs)}."
        queued && createdAt > 0 ->
            "waiting ${formatDuration(nowMs - createdAt)}" to
                "Duration: waiting for a render worker since the session was created at ${formatUtc(createdAt)}; measured at ${formatUtc(nowMs)}."
        finishedAt != null ->
            "-" to "Duration: this session ended at ${formatUtc(finishedAt)} without ever starting to render."
        else -> "-" to "Duration: this session has not started rendering."
    }
    return "<span title=\"${escapeHtml(tooltip)}\" tabindex=\"0\">${escapeHtml(text)}</span>"
}

/** Where an action redirects back to. Chosen from a hidden form field, never from the Referer header. */
enum class ReturnTarget(val formValue: String) {
    LIST("list"), WORKERS("workers"), SESSION("session");

    companion object {
        /** Parses the `returnTo` form field; anything missing or unrecognised returns to the sessions list. */
        fun parse(value: String?): ReturnTarget = values().firstOrNull { it.formValue == value } ?: LIST
    }
}

/** The Cancel button for a RUNNING session, POSTing to `/session/cancel` with the default reason. */
fun cancelFormHtml(sessionId: String, returnTo: ReturnTarget): String =
    "<form class=\"inline-action\" method=\"post\" action=\"/session/cancel\">" +
        "<input type=\"hidden\" name=\"id\" value=\"${escapeHtml(sessionId)}\">" +
        "<input type=\"hidden\" name=\"reason\" value=\"${escapeHtml(DEFAULT_CANCEL_REASON)}\">" +
        "<input type=\"hidden\" name=\"returnTo\" value=\"${returnTo.formValue}\">" +
        "<button type=\"submit\" class=\"btn btn-danger\" title=\"Cancel session ${escapeHtml(sessionId)}: it stops (or never starts) rendering and is marked FAILED with the reason &quot;${escapeHtml(DEFAULT_CANCEL_REASON)}&quot;.\">Cancel</button>" +
        "</form>"

/** The Delete button for a terminal session, POSTing to `/session/delete`. */
fun deleteFormHtml(sessionId: String, returnTo: ReturnTarget): String =
    "<form class=\"inline-action\" method=\"post\" action=\"/session/delete\">" +
        "<input type=\"hidden\" name=\"id\" value=\"${escapeHtml(sessionId)}\">" +
        "<input type=\"hidden\" name=\"returnTo\" value=\"${returnTo.formValue}\">" +
        "<button type=\"submit\" class=\"btn\" title=\"Delete session ${escapeHtml(sessionId)} and its stored screenshots from the service.\">Delete</button>" +
        "</form>"

/** The actions cell of a sessions-table row: Cancel while RUNNING, Delete once terminal. */
fun sessionActionsHtml(sessionId: String, state: String, returnTo: ReturnTarget): String = when {
    state == "RUNNING" -> cancelFormHtml(sessionId, returnTo)
    isTerminalState(state) -> deleteFormHtml(sessionId, returnTo)
    else -> ""
}

/**
 * Session ids the notice templates will echo back. A notice only ever *selects* a fixed template via
 * an enumerated code; the only request-supplied text it may include is an id-shaped token matching
 * this pattern, so a crafted link cannot make a page display arbitrary words (GOOD_DESIGN.md, "Never
 * Encode User-Facing Messages in URLs").
 */
private val NOTICE_ID_PATTERN = Regex("[A-Za-z0-9._:-]{1,128}")

/** Enumerated post-action notice codes carried in `?notice=`; the page composes the text itself. */
const val NOTICE_CANCELLED = "cancelled"
const val NOTICE_DELETED = "deleted"
const val NOTICE_MAX_WORKERS_SET = "maxWorkersSet"

/**
 * Composes a status banner only when the selector agrees with current service data. The URL alone
 * cannot establish that an action succeeded, so every sentence describes the verified live state.
 */
fun noticeBanner(
    code: String?, noticeId: String?, poolSize: Int? = null,
    sessionStatus: JSONObject? = null, isListed: Boolean? = null,
): Banner? {
    return when (code) {
        NOTICE_CANCELLED -> noticeId?.takeIf { NOTICE_ID_PATTERN.matches(it) }
            ?.takeIf { isListed == true && sessionStatus?.optString("sessionId") == it && sessionStatus?.optString("state") == "FAILED" }
            ?.let { id ->
                val error = sessionStatus?.optString("error", "") ?: ""
                if (error.startsWith("Cancelled ")) Banner(Banner.Kind.NOTICE, "Session $id is FAILED: $error") else null
            }
        NOTICE_DELETED -> noticeId?.takeIf { NOTICE_ID_PATTERN.matches(it) }
            ?.takeIf { isListed == false }
            ?.let { Banner(Banner.Kind.NOTICE, "Session $it is no longer listed by the service.") }
        NOTICE_MAX_WORKERS_SET -> noticeId?.toIntOrNull()?.takeIf { it > 0 }
            ?.takeIf { it == poolSize }
            ?.let { Banner(Banner.Kind.NOTICE, "Pool size is now $it.") }
        else -> null
    }
}

/** Resolves a session notice against the current summary list and, for cancellation, its status. */
internal fun sessionNoticeBanner(api: ScreenshotTestApi, sessions: JSONArray, code: String?, noticeId: String?): Banner? {
    if (code != NOTICE_CANCELLED && code != NOTICE_DELETED) return null
    if (noticeId == null || !NOTICE_ID_PATTERN.matches(noticeId)) return null
    var listed = false
    for (i in 0 until sessions.length()) {
        if (sessions.getJSONObject(i).optString("sessionId") == noticeId) {
            listed = true
            break
        }
    }
    val status = if (code == NOTICE_CANCELLED && listed) JSONObject(api.getSessionStatus(noticeId)) else null
    return noticeBanner(code, noticeId, sessionStatus = status, isListed = listed)
}

private fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}
