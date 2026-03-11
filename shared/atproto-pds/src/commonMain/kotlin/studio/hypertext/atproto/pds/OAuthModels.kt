package studio.hypertext.atproto.pds

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Pushed authorization request parameters.
 */
@Serializable
public data class PushedAuthorizationRequest(
    val clientId: String,
    val redirectUri: String,
    val scope: String,
    val responseType: String,
    val codeChallenge: String,
    val codeChallengeMethod: String,
    val state: String? = null,
    val loginHint: String? = null,
    val clientAssertionType: String? = null,
    val clientAssertion: String? = null,
    val dpopProof: String,
    val htu: String,
)

/**
 * Stored request metadata surfaced to the consent UI.
 */
@Serializable
public data class AuthorizationPrompt(
    val requestUri: String,
    val clientId: String,
    val clientName: String,
    val redirectUri: String,
    val scope: String,
    val state: String?,
    val loginHint: String?,
)

/**
 * Approve or deny a stored OAuth authorization request.
 */
@Serializable
public data class AuthorizationDecisionRequest(
    val requestUri: String,
    val subjectDid: String,
    val subjectHandle: String,
    val approved: Boolean,
)

/**
 * Successful PAR response plus the current DPoP nonce header value.
 */
@Serializable
public data class PushedAuthorizationResponse(
    val requestUri: String,
    val expiresInSeconds: Long,
    val dpopNonce: String,
)

/**
 * Wire response body returned by the PAR endpoint.
 */
@Serializable
public data class PushedAuthorizationBody(
    @SerialName("request_uri")
    val requestUri: String,
    @SerialName("expires_in")
    val expiresInSeconds: Long,
)

/**
 * Authorization-code token exchange parameters.
 */
@Serializable
public data class AuthorizationCodeTokenRequest(
    val code: String,
    val redirectUri: String,
    val clientId: String,
    val codeVerifier: String,
    val clientAssertionType: String? = null,
    val clientAssertion: String? = null,
    val dpopProof: String,
    val htu: String,
)

/**
 * Refresh-token grant parameters.
 */
@Serializable
public data class RefreshTokenGrantRequest(
    val refreshToken: String,
    val clientId: String,
    val clientAssertionType: String? = null,
    val clientAssertion: String? = null,
    val dpopProof: String,
    val htu: String,
)

/**
 * Token revocation parameters.
 */
@Serializable
public data class OAuthRevokeRequest(
    val refreshToken: String,
    val clientId: String,
    val clientAssertionType: String? = null,
    val clientAssertion: String? = null,
    val dpopProof: String,
    val htu: String,
)

/**
 * AT Protocol OAuth token response payload.
 */
@Serializable
public data class OAuthTokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Long,
    val refresh_token: String,
    val sub: String,
    val scope: String,
)

/**
 * Consent-screen response returned by the authorize endpoint.
 */
@Serializable
public data class AuthorizationPromptResponse(
    @SerialName("client_id")
    val clientId: String,
    @SerialName("client_name")
    val clientName: String,
    @SerialName("redirect_uri")
    val redirectUri: String,
    val scope: String,
    val state: String?,
    @SerialName("login_hint")
    val loginHint: String?,
    val did: String,
    val handle: String,
)

/**
 * OAuth error response body.
 */
@Serializable
public data class OAuthErrorResponse(
    val error: String,
    @SerialName("error_description")
    val errorDescription: String,
)
