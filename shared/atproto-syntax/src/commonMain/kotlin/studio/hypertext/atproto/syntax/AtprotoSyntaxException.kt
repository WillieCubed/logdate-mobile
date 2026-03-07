package studio.hypertext.atproto.syntax

/**
 * Base exception for AT Protocol syntax parsing failures.
 */
public sealed class AtprotoSyntaxException(
    message: String,
) : IllegalArgumentException(message)

/**
 * Thrown when a DID string does not match the supported generic DID syntax.
 */
public class InvalidDidException(
    value: String,
) : AtprotoSyntaxException("Invalid DID: $value")

/**
 * Thrown when a handle does not match AT Protocol hostname rules.
 */
public class InvalidHandleException(
    value: String,
) : AtprotoSyntaxException("Invalid handle: $value")

/**
 * Thrown when an NSID is malformed.
 */
public class InvalidNsidException(
    value: String,
) : AtprotoSyntaxException("Invalid NSID: $value")

/**
 * Thrown when a record key violates AT Protocol constraints.
 */
public class InvalidRecordKeyException(
    value: String,
) : AtprotoSyntaxException("Invalid record key: $value")

/**
 * Thrown when a TID is malformed.
 */
public class InvalidTidException(
    value: String,
) : AtprotoSyntaxException("Invalid TID: $value")

/**
 * Thrown when an AT URI is malformed.
 */
public class InvalidAtUriException(
    value: String,
) : AtprotoSyntaxException("Invalid AT URI: $value")
