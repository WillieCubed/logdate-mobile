package studio.hypertext.atproto.oauth.client

import studio.hypertext.atproto.identity.AtprotoDid

/**
 * Base class for every failure the OAuth client reports.
 *
 * Each subclass names one specific problem, so callers can decide what to tell the person signing
 * in. The families are:
 * - [OAuthResolutionException]: the account or its servers could not be found or are not usable
 * - [OAuthAuthorizationException]: the sign-in redirect came back without a usable grant
 * - [OAuthServerResponseException]: an authorization server rejected a request
 * - [OAuthVerificationException]: a server answered, but its answer failed a security check
 * - [OAuthSessionException]: a stored session is missing, expired, or revoked
 */
public sealed class OAuthClientException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * The account identifier or server could not be resolved to a usable authorization server.
 */
public sealed class OAuthResolutionException(
    message: String,
    cause: Throwable? = null,
) : OAuthClientException(message, cause)

/**
 * The handle or DID could not be resolved to a DID document.
 *
 * @property input The handle, DID, or server URL that was being resolved.
 */
public class OAuthIdentityResolutionException(
    public val input: String,
    message: String,
    cause: Throwable? = null,
) : OAuthResolutionException("Could not resolve $input: $message", cause)

/**
 * The handle resolved to a DID whose DID document does not claim that handle, so the handle cannot
 * be trusted (the AT Protocol requires bidirectional handle verification).
 */
public class OAuthHandleMismatchException(
    public val handle: String,
    public val did: AtprotoDid,
) : OAuthResolutionException("The DID document for $did does not claim the handle $handle")

/**
 * The DID document has no usable `#atproto_pds` service entry.
 */
public class OAuthPdsNotFoundException(
    public val did: AtprotoDid,
    message: String,
) : OAuthResolutionException("No usable data server for $did: $message")

/**
 * The resource server or authorization server metadata could not be fetched, or does not meet the
 * AT Protocol OAuth profile.
 *
 * @property url The metadata URL or server origin involved.
 */
public class OAuthServerMetadataException(
    public val url: String,
    message: String,
    cause: Throwable? = null,
) : OAuthResolutionException("Unusable OAuth server metadata at $url: $message", cause)

/**
 * The authorization redirect did not produce a usable authorization code.
 */
public sealed class OAuthAuthorizationException(
    message: String,
    cause: Throwable? = null,
) : OAuthClientException(message, cause)

/**
 * The person declined the authorization request (`error=access_denied`).
 *
 * @property appState The application state passed to [AtprotoOAuthClient.authorize], if any.
 */
public class OAuthConsentDeniedException(
    public val errorDescription: String?,
    public val appState: String?,
) : OAuthAuthorizationException("The authorization request was declined" + (errorDescription?.let { ": $it" } ?: ""))

/**
 * The authorization server returned an OAuth error other than a refusal.
 *
 * @property error The OAuth `error` code, for example `server_error`.
 */
public class OAuthAuthorizationErrorException(
    public val error: String,
    public val errorDescription: String?,
    public val appState: String?,
) : OAuthAuthorizationException("The authorization server returned $error" + (errorDescription?.let { ": $it" } ?: ""))

/**
 * The callback parameters are malformed or do not match a pending authorization request.
 */
public class OAuthCallbackException(
    public val reason: Reason,
    message: String,
) : OAuthAuthorizationException(message) {
    /**
     * What was wrong with the callback.
     */
    public enum class Reason {
        /** The `state` parameter is missing. */
        MISSING_STATE,

        /** No pending authorization request has this `state`, or it was already used. */
        UNKNOWN_STATE,

        /** The pending authorization request is older than the configured lifetime. */
        EXPIRED_STATE,

        /** The `code` parameter is missing. */
        MISSING_CODE,

        /** The `iss` parameter is missing although the server promised to send it. */
        MISSING_ISSUER,

        /** The response uses JWT-secured responses (JARM), which this client does not request. */
        UNSUPPORTED_RESPONSE,
    }
}

/**
 * An authorization server answered a pushed authorization, token, or revocation request with an
 * error.
 *
 * @property endpoint Which endpoint failed, for example `token`.
 * @property statusCode The HTTP status, or `null` if the request never got an HTTP response.
 * @property error The OAuth `error` code, when the server sent one.
 */
public class OAuthServerResponseException(
    public val endpoint: String,
    public val statusCode: Int?,
    public val error: String?,
    public val errorDescription: String?,
    cause: Throwable? = null,
) : OAuthClientException(
        buildString {
            append("The authorization server's $endpoint endpoint failed")
            statusCode?.let { append(" with HTTP $it") }
            error?.let { append(" [$it]") }
            errorDescription?.let { append(": $it") }
        },
        cause,
    )

/**
 * A server answered, but the answer failed one of the identity checks the AT Protocol requires.
 * Never use tokens or identities from a response that failed verification.
 */
public sealed class OAuthVerificationException(
    message: String,
) : OAuthClientException(message)

/**
 * The `iss` in the callback, or the authorization server now responsible for the account, is not
 * the authorization server the flow started with.
 */
public class OAuthIssuerMismatchException(
    public val expectedIssuer: String,
    public val actualIssuer: String,
) : OAuthVerificationException("Expected authorization server $expectedIssuer but got $actualIssuer")

/**
 * The token response's `sub` is not the account the sign-in started with.
 */
public class OAuthSubjectMismatchException(
    public val expectedDid: AtprotoDid,
    public val actualSubject: String,
) : OAuthVerificationException("Signed in as $actualSubject, but the sign-in started for $expectedDid")

/**
 * The token response is missing a required field or has an invalid value.
 */
public class OAuthInvalidTokenResponseException(
    message: String,
) : OAuthVerificationException("Invalid token response: $message")

/**
 * A stored session cannot be used.
 *
 * @property did The account the session belongs to.
 */
public sealed class OAuthSessionException(
    public val did: AtprotoDid,
    message: String,
    cause: Throwable? = null,
) : OAuthClientException(message, cause)

/**
 * No session is stored for [did]. The person needs to sign in.
 */
public class OAuthSessionNotFoundException(
    did: AtprotoDid,
) : OAuthSessionException(did, "No OAuth session is stored for $did")

/**
 * The session has ended and was removed from the session store. The person needs to sign in
 * again.
 */
public sealed class OAuthSessionEndedException(
    did: AtprotoDid,
    message: String,
    cause: Throwable? = null,
) : OAuthSessionException(did, message, cause)

/**
 * The session reached the end of its lifetime: the access token expired and it cannot be renewed.
 */
public class OAuthSessionExpiredException(
    did: AtprotoDid,
    detail: String,
    cause: Throwable? = null,
) : OAuthSessionEndedException(did, "The OAuth session for $did has expired: $detail", cause)

/**
 * The session was revoked, by the person, the server, or a security check, before it expired.
 */
public class OAuthSessionRevokedException(
    did: AtprotoDid,
    detail: String,
    cause: Throwable? = null,
) : OAuthSessionEndedException(did, "The OAuth session for $did was revoked: $detail", cause)

/**
 * Signing out removed the local session, but the authorization server did not confirm that it
 * revoked the tokens. The tokens stop working when they expire.
 */
public class OAuthRevocationException(
    did: AtprotoDid,
    cause: Throwable,
) : OAuthSessionException(did, "The session for $did was removed locally, but the server did not confirm revocation", cause)
