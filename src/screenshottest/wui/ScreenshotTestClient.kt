package screenshottest.wui

import foundation.url.resolver.UrlResolver
import screenshottest.api.ScreenshotTestApi

/**
 * Singleton client for communicating with the `url://screenshottest/` service.
 *
 * Uses a typed proxy via [UrlResolver.openSandboxedConnection]: the service serves its
 * `ScreenshotTestServiceClientImpl` bytecode over the `__bytecode_request` channel, so the returned
 * proxy is a working [ScreenshotTestApi] that dispatches each call over RPC. The connection is
 * established lazily on first access so the WUI can start and answer `/health` before the P2P
 * network is up.
 */
object ScreenshotTestClient {
    private const val SERVICE_URL = "url://screenshottest/"

    private val resolver: UrlResolver by lazy {
        UrlResolver()
    }

    val api: ScreenshotTestApi by lazy {
        resolver.openSandboxedConnection(
            SERVICE_URL,
            ScreenshotTestApi::class,
            connectionTimeoutMs = 120_000,
            methodInvocationTimeoutSeconds = 120
        )
    }
}
