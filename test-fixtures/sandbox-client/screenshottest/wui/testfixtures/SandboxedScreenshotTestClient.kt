package screenshottest.wui.testfixtures

import foundation.url.sjvm.intrinsics.ServiceBridge
import screenshottest.api.ScreenshotTestApi

/**
 * Test-only client bytecode that a test's in-process `url://` provider serves to the WUI's SJVM
 * sandbox, so the WUI talks to it exactly the way it talks to `url://screenshottest/` in production.
 *
 * [getImageChunk] mirrors the production `ScreenshotTestServiceClientImpl`
 * (https://github.com/CodexCoder21Organization/ScreenshotTestServerService/blob/main/src-client/screenshottest/server/ScreenshotTestServiceClientImpl.kt):
 * it issues a `ServiceBridge.rpc("getImageChunk", ...)` with `encoding = "bytes"` and returns the
 * natively marshaled `ByteArray`. Several of these run in parallel when a session page loads its
 * thumbnails, which is the load the WUI must sustain.
 *
 * The management actions ([cancelSession], [deleteSession], [setMaxWorkers]) and the pages they
 * re-render ([listSessions], [getWorkerPoolStatus], [getSessionStatus], [getResultsJson]) are
 * forwarded with the same argument maps and result keys as the production client, so provider
 * responses and exceptions reach the WUI through the real sandbox exactly as in production. The
 * remaining calls throw.
 */
class SandboxedScreenshotTestClient : ScreenshotTestApi {
    override fun getImageChunk(sessionId: String, key: String, kind: String, offset: Long, length: Int): ByteArray? {
        val result = ServiceBridge.rpc("getImageChunk", mapOf(
            "sessionId" to sessionId,
            "key" to key,
            "kind" to kind,
            "offset" to offset,
            "length" to length,
            "encoding" to "bytes",
        ))
        return result["data"] as? ByteArray
    }

    override fun getRendererVersion(): String = unsupported("getRendererVersion")
    override fun getWorkerPoolStatus(): String {
        val result = ServiceBridge.rpc("getWorkerPoolStatus", emptyMap())
        return result["poolStatusJson"]?.toString()
            ?: throw IllegalStateException("Server did not return poolStatusJson")
    }
    override fun setMaxWorkers(maxWorkers: Int) {
        ServiceBridge.rpc("setMaxWorkers", mapOf("maxWorkers" to maxWorkers))
    }
    override fun cancelSession(sessionId: String, reason: String) {
        ServiceBridge.rpc("cancelSession", mapOf("sessionId" to sessionId, "reason" to reason))
    }
    override fun createSession(label: String, mode: String, mainClass: String, scenariosJson: String): String =
        unsupported("createSession")
    override fun uploadFileChunk(sessionId: String, role: String, fileName: String, chunkIndex: Int, chunk: ByteArray): Unit =
        unsupported("uploadFileChunk")
    override fun finalizeFile(sessionId: String, role: String, fileName: String, totalChunks: Int, sha256Hex: String): Unit =
        unsupported("finalizeFile")
    override fun startRender(sessionId: String): Unit = unsupported("startRender")
    override fun getSessionStatus(sessionId: String): String {
        val result = ServiceBridge.rpc("getSessionStatus", mapOf("sessionId" to sessionId))
        return result["statusJson"]?.toString()
            ?: throw IllegalStateException("Server did not return statusJson for session '$sessionId'")
    }
    override fun getResultsJson(sessionId: String): String {
        val result = ServiceBridge.rpc("getResultsJson", mapOf("sessionId" to sessionId))
        return result["resultsJson"]?.toString()
            ?: throw IllegalStateException("Server did not return resultsJson for session '$sessionId'")
    }
    override fun listSessions(): String {
        val result = ServiceBridge.rpc("listSessions", emptyMap())
        return result["sessionsJson"]?.toString()
            ?: throw IllegalStateException("Server did not return sessionsJson")
    }
    override fun deleteSession(sessionId: String) {
        ServiceBridge.rpc("deleteSession", mapOf("sessionId" to sessionId))
    }

    private fun unsupported(method: String): Nothing =
        throw UnsupportedOperationException(
            "SandboxedScreenshotTestClient does not implement $method; only the image and management calls are forwarded."
        )
}
