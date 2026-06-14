package app.logdate.client.domain.account

import app.logdate.client.networking.PasskeyApiErrorCodes
import app.logdate.client.networking.PasskeyApiException

/**
 * Semantic outcome categories for Google sign-in / sign-up. The UI maps these to localized copy
 * rather than surfacing raw exception messages.
 */
sealed interface GoogleAuthError {
    /** The user dismissed the Google credential sheet. */
    data object Cancelled : GoogleAuthError

    /** No Google account is available on the device. */
    data object NoGoogleAccount : GoogleAuthError

    /** Google sign-in is not configured (no client ID, or server reports it unconfigured). */
    data object NotConfigured : GoogleAuthError

    /** The server rejected the Google ID token. */
    data object InvalidToken : GoogleAuthError

    /** The Google identity is already linked to a different account. */
    data object AccountLinkConflict : GoogleAuthError

    /** Server-side rate limit hit; the user should wait and retry. */
    data object RateLimited : GoogleAuthError

    /** Network failure reaching the server. */
    data object NetworkError : GoogleAuthError

    /** Server returned a 5xx; not user-fixable. */
    data object ServerError : GoogleAuthError

    data class Unknown(
        val message: String,
    ) : GoogleAuthError
}

/**
 * Maps a repository failure to a [GoogleAuthError]. Prefers the server's structured error code
 * ([PasskeyApiException]); falls back to message inspection for platform credential failures
 * (cancellation, missing account, not configured) raised by the Google sign-in manager.
 */
internal fun mapToGoogleAuthError(exception: Throwable?): GoogleAuthError {
    if (exception is PasskeyApiException) {
        return when (exception.errorCode) {
            PasskeyApiErrorCodes.GOOGLE_AUTH_NOT_CONFIGURED -> GoogleAuthError.NotConfigured
            PasskeyApiErrorCodes.GOOGLE_TOKEN_INVALID -> GoogleAuthError.InvalidToken
            PasskeyApiErrorCodes.ACCOUNT_LINK_CONFLICT -> GoogleAuthError.AccountLinkConflict
            PasskeyApiErrorCodes.RATE_LIMIT_EXCEEDED -> GoogleAuthError.RateLimited
            PasskeyApiErrorCodes.NETWORK_ERROR -> GoogleAuthError.NetworkError
            PasskeyApiErrorCodes.SERVER_ERROR -> GoogleAuthError.ServerError
            else -> GoogleAuthError.Unknown(exception.message ?: exception.errorCode)
        }
    }
    val message = exception?.message
    return when {
        message == null -> GoogleAuthError.Unknown("Unknown error")
        message.contains("cancel", ignoreCase = true) -> GoogleAuthError.Cancelled
        message.contains("no google account", ignoreCase = true) -> GoogleAuthError.NoGoogleAccount
        message.contains("not configured", ignoreCase = true) -> GoogleAuthError.NotConfigured
        message.contains("not available", ignoreCase = true) -> GoogleAuthError.NotConfigured
        else -> GoogleAuthError.Unknown(message)
    }
}
