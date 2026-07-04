package screenshottest.wui

import community.kotlin.clocks.simple.Clock
import community.kotlin.clocks.simple.ManualClock
import org.json.JSONArray
import org.json.JSONObject
import screenshottest.api.ScreenshotTestApi
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.Random
import javax.imageio.ImageIO

/**
 * Launches the ScreenshotTest WUI backed by a **fully deterministic, time-stable** fixture instead of
 * the live `url://screenshottest/` backend, for golden-screenshot capture (see
 * `tests/goldenScreenshots.kts` and the ScreenshotTest workstream:
 * https://github.com/CodexCoder21Organization/PlanRepository/blob/main/workstreams/ScreenshotTest.md).
 *
 * This is the WUI dogfooding its own service: the `url://screenshottest/` runner launches this class's
 * [main] on a worker with `PORT` set, drives the pinned Chromium against `http://127.0.0.1:$PORT`, and
 * compares the captured pixels against the goldens committed under `screenshots/`. For that comparison
 * to stay green day after day the rendered bytes must not depend on the wall clock, so:
 *
 *  1. **Fixed data.** Every session, verdict, and pixel measurement is a constant, and every produced
 *     image is drawn deterministically (no timestamps, no randomness beyond a fixed seed).
 *  2. **A fixed clock.** The WUI renders relative "created N ... ago" times; [createServer] takes a
 *     [Clock] and this fixture passes a frozen [ManualClock] pinned to [FIXED_NOW_MS], dating every
 *     session a fixed distance before it, so "2 hours ago" never becomes "3 hours ago" tomorrow.
 */

/** The frozen wall clock the fixture renders against: 2026-01-01T00:00:00Z, in epoch milliseconds. */
private val FIXED_NOW_MS: Long = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()

private const val MINUTE = 60_000L
private const val HOUR = 3_600_000L
private const val DAY = 86_400_000L

/** The pinned renderer identity every fixture session reports (mirrors the API KDoc's example). */
private const val RENDERER = "do-img-226128685+playwright-core-1.61.1+chromium-1228"

private class FixtureResult(
    val key: String,
    val verdict: String,
    val diffPixelCount: Long,
    val maxChannelDelta: Int,
    val width: Int,
    val height: Int,
    /** True when a golden was submitted (compare-mode MATCH/DIFF): `golden`/`diff` images exist. */
    val hasGolden: Boolean,
) {
    val totalPixels: Long get() = width.toLong() * height.toLong()
}

private class FixtureSession(
    val sessionId: String,
    val label: String,
    val mode: String,
    val state: String,
    val createdAt: Long,
    val error: String?,
    val results: List<FixtureResult>,
)

/** The compare session whose detail page the golden scenarios capture: one MATCH and one DIFF key. */
private val COMPARE_SESSION = FixtureSession(
    sessionId = "sess-a1b2c3d4",
    label = "BuildTestWui golden screenshots",
    mode = "compare",
    state = "COMPLETED",
    createdAt = FIXED_NOW_MS - (2 * HOUR + 12 * MINUTE),
    error = null,
    results = listOf(
        FixtureResult("runs-list", "MATCH", diffPixelCount = 0, maxChannelDelta = 0, width = 460, height = 340, hasGolden = true),
        FixtureResult("run-detail", "DIFF", diffPixelCount = 8_640, maxChannelDelta = 96, width = 460, height = 300, hasGolden = true),
    ),
)

private val RECORD_SESSION = FixtureSession(
    sessionId = "sess-e5f6a7b8",
    label = "PerformanceTestWui golden screenshots",
    mode = "record",
    state = "COMPLETED",
    createdAt = FIXED_NOW_MS - (1 * DAY + 3 * HOUR),
    error = null,
    results = listOf(
        FixtureResult("summary", "RECORDED", diffPixelCount = 0, maxChannelDelta = 0, width = 460, height = 300, hasGolden = false),
        FixtureResult("detail", "RECORDED", diffPixelCount = 0, maxChannelDelta = 0, width = 460, height = 320, hasGolden = false),
    ),
)

private val FAILED_SESSION = FixtureSession(
    sessionId = "sess-c9d0e1f2",
    label = "SimpleFileSystem WUI golden screenshots",
    mode = "compare",
    state = "FAILED",
    createdAt = FIXED_NOW_MS - (3 * DAY + 5 * HOUR),
    error = "Worker provisioning failed: droplet did not become reachable within 300s.",
    results = emptyList(),
)

/** All sessions, newest first (the order [ScreenshotTestApi.listSessions] must return). */
private val SESSIONS: List<FixtureSession> = listOf(COMPARE_SESSION, RECORD_SESSION, FAILED_SESSION)

/**
 * Every produced image, keyed `"<sessionId>|<key>|<kind>"`. Built once, deterministically. Each is a
 * small mock "page" (actual/golden) or a diff heatmap so the detail page's inline thumbnails render as
 * plausible screenshots rather than blank boxes.
 */
private val IMAGES: Map<String, ByteArray> = buildImages()

private fun buildImages(): Map<String, ByteArray> {
    val map = LinkedHashMap<String, ByteArray>()
    for (session in SESSIONS) {
        for (r in session.results) {
            map["${session.sessionId}|${r.key}|actual"] = mockPage(r.width, r.height, accent = Color(0x3f, 0xb9, 0x50))
            if (r.hasGolden) {
                map["${session.sessionId}|${r.key}|golden"] = mockPage(r.width, r.height, accent = Color(0x58, 0xa6, 0xff))
                map["${session.sessionId}|${r.key}|diff"] = mockDiff(r.width, r.height, r.verdict == "DIFF")
            }
        }
    }
    return map
}

/** Draws a deterministic dark "web page" mock: nav bar, title, and alternating table rows. */
private fun mockPage(w: Int, h: Int, accent: Color): ByteArray {
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
    g.color = Color(0x0d, 0x11, 0x17); g.fillRect(0, 0, w, h)          // page background
    g.color = Color(0x16, 0x1b, 0x22); g.fillRect(0, 0, w, 26)         // nav bar
    g.color = accent; g.fillRect(12, 9, 90, 8)                          // nav brand
    g.color = Color(0xe6, 0xed, 0xf3); g.fillRect(24, 44, (w * 0.45).toInt(), 12) // h1
    g.color = Color(0x8b, 0x94, 0x9e); g.fillRect(24, 64, (w * 0.30).toInt(), 8)  // subtitle
    // Table: header + alternating rows.
    val tableTop = 84
    g.color = Color(0x21, 0x26, 0x2d); g.fillRect(24, tableTop, w - 48, 22)       // thead
    var y = tableTop + 22
    var i = 0
    while (y < h - 16) {
        g.color = if (i % 2 == 0) Color(0x16, 0x1b, 0x22) else Color(0x1b, 0x21, 0x2a)
        g.fillRect(24, y, w - 48, 20)
        // a "badge" chip on each row
        g.color = if (i % 3 == 0) Color(0x12, 0x26, 0x1e) else Color(0x1f, 0x2d, 0x40)
        g.fillRect(w - 120, y + 5, 40, 10)
        g.color = Color(0x30, 0x36, 0x3d); g.fillRect(40, y + 7, (w * 0.35).toInt(), 6)
        y += 20; i++
    }
    g.color = Color(0x30, 0x36, 0x3d); g.stroke = BasicStroke(1f); g.drawRect(24, tableTop, w - 48, y - tableTop)
    g.dispose()
    return toPng(img)
}

/** Draws a deterministic diff heatmap: near-black with scattered red difference blocks. */
private fun mockDiff(w: Int, h: Int, hasDifferences: Boolean): ByteArray {
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.color = Color(0x0a, 0x0a, 0x0a); g.fillRect(0, 0, w, h)
    if (hasDifferences) {
        val rnd = Random(42L)  // fixed seed -> byte-stable heatmap
        g.color = Color(0xf8, 0x51, 0x49)
        repeat(60) {
            val bx = rnd.nextInt(w - 12)
            val by = rnd.nextInt(h - 12)
            g.fillRect(bx, by, 6 + rnd.nextInt(8), 4 + rnd.nextInt(6))
        }
    }
    g.dispose()
    return toPng(img)
}

private fun toPng(img: BufferedImage): ByteArray {
    val out = ByteArrayOutputStream()
    ImageIO.write(img, "png", out)
    return out.toByteArray()
}

/** Builds the read-only, deterministic [ScreenshotTestApi] the fixture WUI serves. */
private fun buildFixtureApi(): ScreenshotTestApi = object : ScreenshotTestApi {
    override fun getRendererVersion(): String = RENDERER

    override fun createSession(label: String, mode: String, mainClass: String, scenariosJson: String): String =
        throw UnsupportedOperationException("ScreenshotFixtureServer is read-only")

    override fun uploadFileChunk(sessionId: String, role: String, fileName: String, chunkIndex: Int, chunk: ByteArray) =
        throw UnsupportedOperationException("ScreenshotFixtureServer is read-only")

    override fun finalizeFile(sessionId: String, role: String, fileName: String, totalChunks: Int, sha256Hex: String) =
        throw UnsupportedOperationException("ScreenshotFixtureServer is read-only")

    override fun startRender(sessionId: String) =
        throw UnsupportedOperationException("ScreenshotFixtureServer is read-only")

    override fun getSessionStatus(sessionId: String): String {
        val s = session(sessionId)
        return JSONObject()
            .put("sessionId", s.sessionId)
            .put("state", s.state)
            .put("error", s.error ?: JSONObject.NULL)
            .put("rendererVersion", RENDERER)
            .toString()
    }

    override fun getResultsJson(sessionId: String): String {
        val s = session(sessionId)
        check(s.state == "COMPLETED") {
            "Results for session '$sessionId' are not available in state ${s.state}; results exist only once COMPLETED."
        }
        val arr = JSONArray()
        for (r in s.results) {
            arr.put(
                JSONObject()
                    .put("key", r.key)
                    .put("verdict", r.verdict)
                    .put("diffPixelCount", r.diffPixelCount)
                    .put("totalPixels", r.totalPixels)
                    .put("maxChannelDelta", r.maxChannelDelta)
                    .put("goldenWidth", if (r.hasGolden) r.width else 0)
                    .put("goldenHeight", if (r.hasGolden) r.height else 0)
                    .put("actualWidth", r.width)
                    .put("actualHeight", r.height)
            )
        }
        return JSONObject().put("rendererVersion", RENDERER).put("mode", s.mode).put("results", arr).toString()
    }

    override fun getImageChunk(sessionId: String, key: String, kind: String, offset: Long, length: Int): ByteArray? {
        session(sessionId) // validates the id per contract
        require(kind == "actual" || kind == "golden" || kind == "diff") {
            "kind must be \"actual\", \"diff\", or \"golden\", but was \"$kind\"."
        }
        val bytes = IMAGES["$sessionId|$key|$kind"] ?: return null
        if (offset >= bytes.size) return null
        val end = minOf(offset + length, bytes.size.toLong()).toInt()
        return bytes.copyOfRange(offset.toInt(), end)
    }

    override fun listSessions(): String {
        val arr = JSONArray()
        for (s in SESSIONS) {
            arr.put(
                JSONObject()
                    .put("sessionId", s.sessionId)
                    .put("label", s.label)
                    .put("mode", s.mode)
                    .put("state", s.state)
                    .put("createdAt", s.createdAt)
                    .put("rendererVersion", RENDERER)
            )
        }
        return arr.toString()
    }

    override fun deleteSession(sessionId: String) {
        // No-op: the fixture is read-only. Idempotent deletion is a valid contract behavior.
    }

    private fun session(sessionId: String): FixtureSession =
        SESSIONS.firstOrNull { it.sessionId == sessionId }
            ?: throw IllegalArgumentException("No screenshot session with id '$sessionId'.")
}

/**
 * Entry point launched on the ScreenshotTest worker. Reads the port from `PORT` (defaulting to 8080
 * for local inspection) and serves the deterministic fixture WUI against a frozen clock.
 */
fun main() {
    System.setProperty("java.awt.headless", "true")
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val clock: Clock = ManualClock(FIXED_NOW_MS)
    println("Starting ScreenshotTest WUI screenshot fixture on port $port (frozen clock @ $FIXED_NOW_MS)...")
    val server = createServer(port, buildFixtureApi(), clock)
    server.start()
    println("Fixture WUI running at http://0.0.0.0:$port/")
    server.join()
}
