package app.logdate.atproto.identity

/**
 * Base exception for AT Protocol identity failures.
 */
public sealed class IdentityException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * Thrown when a DID is valid in the generic sense but not acceptable for AT Protocol identity.
 */
public class InvalidAtprotoDidException(
    value: String,
) : IdentityException("Invalid AT Protocol DID: $value")

/**
 * Thrown when DID document resolution fails.
 */
public class DidResolutionException(
    did: String,
    message: String,
    cause: Throwable? = null,
) : IdentityException("Failed to resolve DID $did: $message", cause)

/**
 * Thrown when handle resolution fails.
 */
public class HandleResolutionException(
    handle: String,
    message: String,
    cause: Throwable? = null,
) : IdentityException("Failed to resolve handle $handle: $message", cause)

/**
 * Thrown when a resolved DID document does not match the DID that was requested.
 */
public class DidDocumentMismatchException(
    expectedDid: String,
    actualDid: String,
) : IdentityException("Resolved DID document mismatch: expected $expectedDid but got $actualDid")
