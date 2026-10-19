package app.logdate.server.identity

/**
 * Raised when an identity lifecycle operation cannot proceed without risking DID drift or
 * inconsistent published identity state.
 */
class IdentityLifecycleConflictException(
    message: String,
    /** A specific error code for the response, or `null` to use the route's general conflict code. */
    val code: String? = null,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * Raised when an account was deleted before its identity could be provisioned. Nothing was written,
 * so the deleted account stays deleted.
 */
class IdentityAccountMissingException : IllegalStateException("The account no longer exists")

/**
 * Raised when a user-supplied identity lifecycle payload is invalid.
 */
class IdentityLifecycleValidationException(
    message: String,
    cause: Throwable? = null,
    /** A specific error code for the response, or `null` to use the route's general validation code. */
    val code: String? = null,
) : IllegalArgumentException(message, cause)
