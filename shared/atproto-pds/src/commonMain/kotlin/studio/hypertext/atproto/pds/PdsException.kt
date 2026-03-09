package studio.hypertext.atproto.pds

/**
 * Base protocol failure surfaced by the standalone PDS contract layer.
 */
public sealed class PdsException(
    public val statusCode: Int,
    public val error: String,
    message: String,
) : IllegalArgumentException(message)

/**
 * Raised when a request is malformed.
 */
public class PdsInvalidRequestException(
    message: String,
) : PdsException(statusCode = 400, error = "invalid_request", message = message)

/**
 * Raised when a route is unsupported by the active server configuration.
 */
public class PdsUnsupportedException(
    message: String,
) : PdsException(statusCode = 501, error = "unsupported", message = message)
