package app.logdate.server.oauth

import io.ktor.http.HttpStatusCode

/**
 * Base failure for OAuth request handling.
 */
sealed class OAuthException(
    val status: HttpStatusCode,
    val error: String,
    override val message: String,
) : IllegalArgumentException(message)

/**
 * Raised when a caller submits malformed OAuth request parameters.
 */
class OAuthInvalidRequestException(
    message: String,
) : OAuthException(HttpStatusCode.BadRequest, "invalid_request", message)

/**
 * Raised when the remote OAuth client metadata document is invalid.
 */
class OAuthInvalidClientException(
    message: String,
) : OAuthException(HttpStatusCode.BadRequest, "invalid_client", message)

/**
 * Raised when an authorization code or refresh token is invalid.
 */
class OAuthInvalidGrantException(
    message: String,
) : OAuthException(HttpStatusCode.BadRequest, "invalid_grant", message)

/**
 * Raised when a caller uses an unsupported OAuth grant type.
 */
class OAuthUnsupportedGrantTypeException(
    message: String,
) : OAuthException(HttpStatusCode.BadRequest, "unsupported_grant_type", message)

/**
 * Raised when a DPoP proof is malformed or does not match the request.
 */
class OAuthInvalidDpopProofException(
    message: String,
) : OAuthException(HttpStatusCode.BadRequest, "invalid_dpop_proof", message)

/**
 * Raised when the server requires the client to retry with the current DPoP nonce.
 */
class OAuthUseDpopNonceException(
    val nonce: String,
) : OAuthException(HttpStatusCode.BadRequest, "use_dpop_nonce", "The DPoP proof must include the current server nonce")
