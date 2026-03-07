package app.logdate.atproto.xrpc

/**
 * Authentication applied to an XRPC request.
 */
public sealed interface XrpcAuth

/**
 * Marker indicating that no authentication should be applied.
 */
public data object NoAuth : XrpcAuth

/**
 * Bearer token authentication for authenticated XRPC calls.
 *
 * @property token Access token value.
 */
public data class BearerToken(
    val token: String,
) : XrpcAuth
