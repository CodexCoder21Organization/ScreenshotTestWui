package screenshottest.wui

import foundation.url.protocol.sandbox.SandboxException

/** How the screenshot service's failure of an operation should be presented. */
internal enum class BackendFailureKind {
    /** The service rejected an argument (its documented IllegalArgumentException). */
    REJECTED_ARGUMENT,

    /** The target is in a state that does not allow the operation (IllegalStateException). */
    CONFLICTING_STATE,

    /** Any other failure, including transport failures and failures the service did not report. */
    UPSTREAM,
}

/**
 * Classifies [failure] from a [screenshottest.api.ScreenshotTestApi] call. An in-process API throws
 * the service's own exception; the sandboxed `url://` client throws a [SandboxException] whose
 * `remoteExceptionClassName` names the exception the service reported (null when the service
 * reported none, e.g. a transport failure).
 */
internal fun classifyBackendFailureKind(failure: Throwable): BackendFailureKind =
    if (failure is SandboxException) {
        when (failure.remoteExceptionClassName) {
            "java.lang.IllegalArgumentException" -> BackendFailureKind.REJECTED_ARGUMENT
            "java.lang.IllegalStateException" -> BackendFailureKind.CONFLICTING_STATE
            else -> BackendFailureKind.UPSTREAM
        }
    } else {
        when (failure) {
            is IllegalArgumentException -> BackendFailureKind.REJECTED_ARGUMENT
            is IllegalStateException -> BackendFailureKind.CONFLICTING_STATE
            else -> BackendFailureKind.UPSTREAM
        }
    }

/** The message to show for [failure]: the service's own message when it reported one. */
internal fun backendFailureMessage(failure: Throwable): String =
    (failure as? SandboxException)?.takeIf { it.remoteExceptionClassName != null }?.let {
        it.remoteExceptionMessage ?: it.remoteExceptionClassName!!
    } ?: failure.message ?: failure.javaClass.name
