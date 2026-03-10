package app.logdate.server.identity

/**
 * Raised when an identity lifecycle operation cannot proceed without risking DID drift or
 * inconsistent published identity state.
 */
class IdentityLifecycleConflictException(
    message: String,
) : IllegalStateException(message)

/**
 * Raised when a user-supplied identity lifecycle payload is invalid.
 */
class IdentityLifecycleValidationException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
