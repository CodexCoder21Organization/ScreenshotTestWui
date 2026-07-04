package screenshottest.wui

/**
 * Shared HTML helper functions for the ScreenshotTest WUI servlets.
 *
 * The visual language is the org-standard dark GitHub theme (see BuildTestWui). Everything here is
 * pure string production so it can be exercised from the hermetic e2e tests without a browser.
 */

fun escapeHtml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

/**
 * URL-encodes [value] for use in a query-string parameter (e.g. a session id or image key that may
 * contain characters like `.` or spaces). Uses `application/x-www-form-urlencoded` then upgrades the
 * space encoding from `+` to `%20` so the value is safe in a full URL, not just a form body.
 */
fun urlEncode(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

/** CSS class for a session lifecycle state (`PENDING|UPLOADING|RUNNING|COMPLETED|FAILED`). */
fun stateBadgeCssClass(state: String): String = when (state) {
    "PENDING" -> "badge-pending"
    "UPLOADING" -> "badge-provisioning"
    "RUNNING" -> "badge-running"
    "COMPLETED" -> "badge-completed"
    "FAILED" -> "badge-failed"
    else -> "badge-pending"
}

/** Inline badge for a session lifecycle state. */
fun stateBadgeHtml(state: String): String =
    "<span class=\"badge ${stateBadgeCssClass(state)}\">${escapeHtml(state)}</span>"

/** CSS class tinting a session row so its state is legible without scanning to the state chip. */
fun stateRowCssClass(state: String): String = when (state) {
    "PENDING" -> "row-status-pending"
    "UPLOADING" -> "row-status-provisioning"
    "RUNNING" -> "row-status-provisioning"
    "COMPLETED" -> "row-status-completed"
    "FAILED" -> "row-status-failed"
    else -> "row-status-pending"
}

/** CSS class for a per-key screenshot verdict (`MATCH|DIFF|MISSING_GOLDEN|RECORDED`). */
fun verdictBadgeCssClass(verdict: String): String = when (verdict) {
    "MATCH" -> "badge-completed"
    "DIFF" -> "badge-failed"
    "MISSING_GOLDEN" -> "badge-destroying"
    "RECORDED" -> "badge-building"
    else -> "badge-pending"
}

/** Inline badge for a per-key verdict. Underscores render as spaces (`MISSING_GOLDEN` -> "MISSING GOLDEN"). */
fun verdictBadgeHtml(verdict: String): String =
    "<span class=\"badge ${verdictBadgeCssClass(verdict)}\">${escapeHtml(verdict.replace('_', ' '))}</span>"

/** Inline badge for a session mode (`compare|record`). */
fun modeBadgeHtml(mode: String): String {
    val cls = if (mode == "record") "badge-building" else "badge-provisioning"
    return "<span class=\"badge $cls\">${escapeHtml(mode)}</span>"
}

fun pageHeader(title: String): String {
    return """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>${escapeHtml(title)}</title>
<style>
:root {
    --bg-primary: #0d1117;
    --bg-secondary: #161b22;
    --bg-tertiary: #21262d;
    --border: #30363d;
    --text-primary: #e6edf3;
    --text-secondary: #8b949e;
    --text-link: #58a6ff;
    --green: #3fb950;
    --red: #f85149;
    --yellow: #d29922;
    --blue: #58a6ff;
    --gray: #8b949e;
}
* { margin: 0; padding: 0; box-sizing: border-box; }
body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif;
    background: var(--bg-primary);
    color: var(--text-primary);
    line-height: 1.5;
}
.container { max-width: 1200px; margin: 0 auto; padding: 24px 16px; }
h1 { font-size: 24px; margin-bottom: 8px; }
h2 { font-size: 20px; margin: 24px 0 12px; }
.subtitle { color: var(--text-secondary); margin-bottom: 16px; }
a { color: var(--text-link); text-decoration: none; }
a:hover { text-decoration: underline; }
table {
    width: 100%;
    border-collapse: collapse;
    background: var(--bg-secondary);
    border: 1px solid var(--border);
    border-radius: 6px;
    overflow: hidden;
    margin-bottom: 16px;
}
thead { background: var(--bg-tertiary); }
th, td { padding: 8px 16px; text-align: left; border-bottom: 1px solid var(--border); font-size: 14px; }
th { font-weight: 600; color: var(--text-secondary); }
tr:last-child td { border-bottom: none; }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace; }
.nowrap { white-space: nowrap; }
.badge {
    display: inline-block;
    padding: 2px 8px;
    border-radius: 12px;
    font-size: 12px;
    font-weight: 600;
    text-transform: uppercase;
}
.badge-pending { background: #30363d; color: var(--gray); }
.badge-provisioning { background: #1f2d40; color: var(--blue); }
.badge-building { background: #1f2d40; color: var(--blue); }
.badge-destroying { background: #2d2207; color: var(--yellow); }
.badge-completed { background: #12261e; color: var(--green); }
.badge-failed { background: #2d1215; color: var(--red); }
.badge-running { background: #1f2d40; color: var(--blue); animation: pulse-running 1.5s ease-in-out infinite; }
@keyframes pulse-running { 0%, 100% { opacity: 1; } 50% { opacity: 0.6; } }
.row-status-pending td { background: #1d2229; }
.row-status-provisioning td { background: #18202a; }
.row-status-completed td { background: #151e21; }
.row-status-failed td { background: #1c191f; }
.info-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 12px;
    margin: 16px 0;
}
.info-card { background: var(--bg-secondary); border: 1px solid var(--border); border-radius: 6px; padding: 12px 16px; }
.error-card { border-color: var(--red); }
.info-label { font-size: 12px; color: var(--text-secondary); margin-bottom: 4px; text-transform: uppercase; letter-spacing: 0.05em; }
.info-value { font-size: 16px; font-weight: 600; word-break: break-word; }
.text-green { color: var(--green); }
.text-red { color: var(--red); }
.text-gray { color: var(--gray); }
.empty-state {
    text-align: center;
    padding: 48px;
    color: var(--text-secondary);
    background: var(--bg-secondary);
    border: 1px solid var(--border);
    border-radius: 6px;
}
nav { background: var(--bg-secondary); border-bottom: 1px solid var(--border); padding: 12px 16px; }
nav a { margin-right: 16px; font-weight: 600; }
.date-cell .date-relative { font-weight: 600; }
.date-cell .date-local, .date-cell .date-utc { font-size: 12px; color: var(--text-secondary); }
/* Per-key screenshot gallery: the actual / golden / diff thumbnails for one image key,
 * laid out side by side under the results row. Each image is streamed by /image and is at
 * most the viewport-wide capture, so it is capped and scrolls rather than blowing up the row. */
.shot-block { margin: 0 0 20px; }
.shot-key { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; font-size: 13px; font-weight: 600; margin-bottom: 8px; }
.shot-gallery { display: flex; flex-wrap: wrap; gap: 16px; }
.shot {
    background: var(--bg-secondary);
    border: 1px solid var(--border);
    border-radius: 6px;
    padding: 8px;
    max-width: 380px;
}
.shot-label { font-size: 12px; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 6px; }
.shot img {
    display: block;
    max-width: 360px;
    max-height: 360px;
    width: auto;
    height: auto;
    border: 1px solid var(--border);
    border-radius: 4px;
    background:
        linear-gradient(45deg, #1c2128 25%, transparent 25%),
        linear-gradient(-45deg, #1c2128 25%, transparent 25%),
        linear-gradient(45deg, transparent 75%, #1c2128 75%),
        linear-gradient(-45deg, transparent 75%, #1c2128 75%);
    background-size: 16px 16px;
    background-position: 0 0, 0 8px, 8px -8px, -8px 0;
}
</style>
</head>
<body>
<nav>
    <a href="/">ScreenshotTest</a>
    <a href="/health">Health</a>
</nav>
"""
}

/**
 * Footer + the relative-time script. Every `.date-cell` carries a `data-timestamp` (epoch ms); the
 * script renders "N ... ago" plus the local/UTC datetime, anchored to [nowMs] (the server's injected
 * [community.kotlin.clocks.simple.Clock] "now") rather than the raw browser clock — so a fixed-clock
 * render (the golden-screenshot fixture) produces byte-stable relative text run after run.
 */
fun pageFooter(nowMs: Long = System.currentTimeMillis()): String {
    return """
<script>
(function() {
    var serverNowMs = $nowMs;
    var nowOffset = serverNowMs - Date.now();
    function relativeTime(ts) {
        var now = Date.now() + nowOffset;
        var diff = now - ts;
        if (diff < 0) return 'just now';
        var seconds = Math.floor(diff / 1000);
        if (seconds < 60) return seconds + ' second' + (seconds !== 1 ? 's' : '') + ' ago';
        var minutes = Math.floor(seconds / 60);
        if (minutes < 60) return minutes + ' minute' + (minutes !== 1 ? 's' : '') + ' ago';
        var hours = Math.floor(minutes / 60);
        if (hours < 24) return hours + ' hour' + (hours !== 1 ? 's' : '') + ' ago';
        var days = Math.floor(hours / 24);
        return days + ' day' + (days !== 1 ? 's' : '') + ' ago';
    }
    function formatLocal(d) {
        return d.toLocaleString(undefined, {year:'numeric',month:'short',day:'numeric',hour:'2-digit',minute:'2-digit',second:'2-digit',timeZoneName:'short'});
    }
    function formatUTC(d) {
        return d.toLocaleString(undefined, {year:'numeric',month:'short',day:'numeric',hour:'2-digit',minute:'2-digit',second:'2-digit',timeZone:'UTC'}) + ' UTC';
    }
    function isUTC() { return new Date().getTimezoneOffset() === 0; }
    var cells = document.querySelectorAll('.date-cell');
    for (var i = 0; i < cells.length; i++) {
        var ts = parseInt(cells[i].getAttribute('data-timestamp'), 10);
        if (!ts || ts <= 0) { cells[i].textContent = '-'; continue; }
        var d = new Date(ts);
        var html = '<div class="date-relative">' + relativeTime(ts) + '</div>';
        html += '<div class="date-local">' + formatLocal(d) + '</div>';
        if (!isUTC()) { html += '<div class="date-utc">' + formatUTC(d) + '</div>'; }
        cells[i].innerHTML = html;
    }
    function refreshRelativeTimes() {
        var cs = document.querySelectorAll('.date-cell');
        for (var k = 0; k < cs.length; k++) {
            var t = parseInt(cs[k].getAttribute('data-timestamp'), 10);
            if (!t || t <= 0) continue;
            var rel = cs[k].querySelector('.date-relative');
            if (rel) rel.textContent = relativeTime(t);
        }
    }
    setInterval(refreshRelativeTimes, 1000);
})();
</script>
</body>
</html>
"""
}

/** Renders a standalone error page with [message] already HTML-escaped by the caller when needed. */
fun errorPage(message: String): String {
    return buildString {
        append(pageHeader("ScreenshotTest - Error"))
        append("<div class=\"container\">")
        append("<h1>Error</h1>")
        append("<div class=\"info-card error-card\">")
        append("<div class=\"info-value text-red\">$message</div>")
        append("</div>")
        append("<p><a href=\"/\">&larr; Back to all sessions</a></p>")
        append("</div>")
        append(pageFooter())
    }
}
