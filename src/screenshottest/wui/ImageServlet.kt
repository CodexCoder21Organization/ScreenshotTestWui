package screenshottest.wui

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * Streams a produced PNG for one image key (`/image?id=<id>&key=<key>&kind=<actual|golden|diff>`).
 *
 * The bytes are pulled from [screenshottest.api.ScreenshotTestApi.getImageChunk] in <=1 MiB slices
 * and written straight into the servlet output stream, one slice at a time. Peak WUI heap per
 * concurrent request is therefore bounded by [CHUNK_SIZE], independent of the image size — essential
 * because the WUI runs under `-Xmx128m` on ContainerNursery and full-page captures can be several MiB.
 *
 * Errors are surfaced descriptively:
 *  - a missing/blank required parameter, or a `kind` outside `{actual, golden, diff}`, is `400`;
 *  - a session/key the service does not know, or a key/kind pair with no image (e.g. `golden` for a
 *    record-mode key), is `404`.
 */
class ImageServlet : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val id = req.getParameter("id")
        val key = req.getParameter("key")
        val kind = req.getParameter("kind")

        if (id.isNullOrBlank()) { badRequest(resp, "Missing required query parameter \"id\" (the session id)."); return }
        if (key.isNullOrBlank()) { badRequest(resp, "Missing required query parameter \"key\" (the image key)."); return }
        if (kind.isNullOrBlank()) { badRequest(resp, "Missing required query parameter \"kind\" (one of: actual, golden, diff)."); return }
        if (kind !in VALID_KINDS) {
            badRequest(resp, "Invalid image kind \"$kind\"; kind must be one of: actual, golden, diff.")
            return
        }

        val api = servletContext.getScreenshotTestApi()

        // Probe the first slice. An IllegalArgumentException here means the service rejected the
        // session id, key, or kind (its message names the offending value); a null/empty first slice
        // means the key/kind pair produced no image (e.g. golden requested in record mode).
        val first = try {
            api.getImageChunk(id, key, kind, 0L, CHUNK_SIZE)
        } catch (e: IllegalArgumentException) {
            notFound(resp, "No image for session \"$id\", key \"$key\", kind \"$kind\": ${e.message}")
            return
        } catch (e: Exception) {
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.contentType = "text/plain; charset=UTF-8"
            resp.writer.write("Failed to load image for session \"$id\", key \"$key\", kind \"$kind\": ${e.message ?: e.javaClass.name}")
            return
        }
        if (first == null || first.isEmpty()) {
            notFound(resp, "No $kind image found for session \"$id\", key \"$key\".")
            return
        }

        resp.status = HttpServletResponse.SC_OK
        resp.contentType = "image/png"
        resp.setHeader("Content-Disposition", "inline; filename=\"${sanitizeForFilename(key)}-$kind.png\"")

        val out = resp.outputStream
        out.write(first)
        out.flush()
        var offset = first.size.toLong()
        while (true) {
            val slice = try {
                api.getImageChunk(id, key, kind, offset, CHUNK_SIZE)
            } catch (e: Exception) {
                // Headers/body are already on the wire, so we cannot switch to an error status.
                // Log and stop; the client sees a short read rather than corrupt data.
                System.err.println("[ImageServlet] Error streaming image id=$id key=$key kind=$kind at offset=$offset: ${e.javaClass.name}: ${e.message}")
                return
            } ?: break
            if (slice.isEmpty()) break
            out.write(slice)
            out.flush()
            offset += slice.size.toLong()
        }
        resp.flushBuffer()
    }

    private fun badRequest(resp: HttpServletResponse, message: String) {
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "text/plain; charset=UTF-8"
        resp.writer.write(message)
    }

    private fun notFound(resp: HttpServletResponse, message: String) {
        resp.status = HttpServletResponse.SC_NOT_FOUND
        resp.contentType = "text/plain; charset=UTF-8"
        resp.writer.write(message)
    }

    companion object {
        /** Per-slice request size, matching the service's 1 MiB per-chunk cap. Bounds WUI heap. */
        const val CHUNK_SIZE = 1024 * 1024
        private val VALID_KINDS = setOf("actual", "golden", "diff")
    }
}

private fun sanitizeForFilename(s: String): String =
    s.map { c -> if (c.isLetterOrDigit() || c == '-' || c == '_' || c == '.') c else '_' }.joinToString("")
