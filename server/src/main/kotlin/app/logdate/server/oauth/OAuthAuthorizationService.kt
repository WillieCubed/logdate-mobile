package app.logdate.server.oauth

import studio.hypertext.atproto.pds.AuthorizationCodeTokenRequest
import studio.hypertext.atproto.pds.AuthorizationDecisionRequest
import studio.hypertext.atproto.pds.AuthorizationPrompt
import studio.hypertext.atproto.pds.OAuthRevokeRequest
import studio.hypertext.atproto.pds.OAuthTokenResponse
import studio.hypertext.atproto.pds.PdsOAuthService
import studio.hypertext.atproto.pds.PushedAuthorizationRequest
import studio.hypertext.atproto.pds.PushedAuthorizationResponse
import studio.hypertext.atproto.pds.RefreshTokenGrantRequest
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Stores PAR requests, authorization codes, and refresh tokens for the server OAuth flow.
 */
class OAuthAuthorizationService(
    private val clientMetadataResolver: OAuthClientMetadataResolver,
    private val dpopVerifier: OAuthDpopVerifier,
    private val accessTokenService: OAuthAccessTokenService,
    private val nonceService: OAuthNonceService,
    private val authorizationServerIssuer: String,
    private val clock: Clock = Clock.System,
    private val secureRandom: SecureRandom = SecureRandom(),
) : PdsOAuthService {
    private val requestUris = mutableMapOf<String, StoredAuthorizationRequest>()
    private val authorizationCodes = mutableMapOf<String, StoredAuthorizationCode>()
    private val refreshTokens = mutableMapOf<String, StoredRefreshToken>()

    /**
     * Validates and stores a pushed authorization request.
     */
    suspend fun createPushedAuthorizationRequest(
        clientId: String,
        redirectUri: String,
        scope: String,
        responseType: String,
        codeChallenge: String,
        codeChallengeMethod: String,
        state: String?,
        loginHint: String?,
        clientAssertionType: String? = null,
        clientAssertion: String? = null,
        dpopProof: String,
        htu: String,
    ): PushedAuthorizationResponse {
        if (responseType != "code") {
            throw OAuthInvalidRequestException("response_type must be code")
        }
        if (codeChallenge.isBlank()) {
            throw OAuthInvalidRequestException("code_challenge is required")
        }
        if (codeChallengeMethod != "S256") {
            throw OAuthInvalidRequestException("code_challenge_method must be S256")
        }
        val requestedScopes =
            scope
                .trim()
                .split(' ')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()
        if ("atproto" !in requestedScopes) {
            throw OAuthInvalidRequestException("scope must include atproto")
        }

        val authenticatedClient =
            clientMetadataResolver
                .authenticateClient(
                    clientId = clientId,
                    clientAssertionType = clientAssertionType,
                    clientAssertion = clientAssertion,
                    authorizationServerIssuer = authorizationServerIssuer,
                ).getOrElse { throw it }
        val clientMetadata = authenticatedClient.metadata
        if (!clientMetadata.supportsRedirect(redirectUri)) {
            throw OAuthInvalidClientException("redirect_uri is not declared by the client metadata")
        }
        val declaredScopes = clientMetadata.scopeSet()
        if (declaredScopes.isNotEmpty() && !declaredScopes.containsAll(requestedScopes)) {
            throw OAuthInvalidClientException("Requested scopes exceed the client metadata document scope")
        }

        val proof = dpopVerifier.verify(dpopProof, method = "POST", htu = htu).getOrElse { throw it }
        val requestUri = "urn:ietf:params:oauth:request_uri:${randomToken()}"
        requestUris[requestUri] =
            StoredAuthorizationRequest(
                requestUri = requestUri,
                clientId = clientId,
                clientName = clientMetadata.client_name ?: clientId,
                redirectUri = redirectUri,
                scope = requestedScopes.joinToString(" "),
                state = state?.trim()?.takeIf(String::isNotEmpty),
                loginHint = loginHint?.trim()?.takeIf(String::isNotEmpty),
                codeChallenge = codeChallenge.trim(),
                dpopKeyThumbprint = proof.keyThumbprint,
                clientAuthKeyId = authenticatedClient.clientKeyId,
                clientAuthKeyThumbprint = authenticatedClient.clientKeyThumbprint,
                expiresAt = clock.now() + REQUEST_URI_TTL,
            )
        return PushedAuthorizationResponse(
            requestUri = requestUri,
            expiresInSeconds = REQUEST_URI_TTL.inWholeSeconds,
            dpopNonce = nonceService.currentNonce(),
        )
    }

    /**
     * Returns prompt details for a stored authorization request.
     */
    fun describeAuthorizationRequest(requestUri: String): AuthorizationPrompt {
        val stored = requestUris.requireValidRequest(requestUri, clock)
        return AuthorizationPrompt(
            requestUri = stored.requestUri,
            clientId = stored.clientId,
            clientName = stored.clientName,
            redirectUri = stored.redirectUri,
            scope = stored.scope,
            state = stored.state,
            loginHint = stored.loginHint,
        )
    }

    /**
     * Approves or denies the stored authorization request.
     */
    fun completeAuthorization(
        requestUri: String,
        subjectDid: String,
        subjectHandle: String,
        approved: Boolean,
    ): String {
        val stored = requestUris.requireValidRequest(requestUri, clock)
        requestUris.remove(requestUri)

        if (stored.loginHint != null &&
            stored.loginHint.lowercase() != subjectHandle.lowercase() &&
            stored.loginHint.lowercase() != subjectDid.lowercase()
        ) {
            throw OAuthInvalidRequestException("login_hint does not match the authenticated account")
        }

        val queryParameters =
            if (approved) {
                val code = randomToken()
                authorizationCodes[code] =
                    StoredAuthorizationCode(
                        code = code,
                        clientId = stored.clientId,
                        redirectUri = stored.redirectUri,
                        subjectDid = subjectDid,
                        subjectHandle = subjectHandle,
                        scope = stored.scope,
                        codeChallenge = stored.codeChallenge,
                        dpopKeyThumbprint = stored.dpopKeyThumbprint,
                        clientAuthKeyId = stored.clientAuthKeyId,
                        clientAuthKeyThumbprint = stored.clientAuthKeyThumbprint,
                        expiresAt = clock.now() + AUTHORIZATION_CODE_TTL,
                    )
                buildMap {
                    put("code", code)
                    stored.state?.let { put("state", it) }
                }
            } else {
                buildMap {
                    put("error", "access_denied")
                    stored.state?.let { put("state", it) }
                }
            }
        return appendQuery(stored.redirectUri, queryParameters)
    }

    /**
     * Exchanges an authorization code for a DPoP-bound token pair.
     */
    suspend fun exchangeAuthorizationCode(
        code: String,
        redirectUri: String,
        clientId: String,
        codeVerifier: String,
        clientAssertionType: String? = null,
        clientAssertion: String? = null,
        dpopProof: String,
        htu: String,
    ): OAuthTokenResponse {
        val stored =
            authorizationCodes
                .remove(code)
                ?.takeIf { clock.now() < it.expiresAt }
                ?: throw OAuthInvalidGrantException("Authorization code is invalid or expired")
        if (stored.clientId != clientId || stored.redirectUri != redirectUri) {
            throw OAuthInvalidGrantException("Authorization code does not match the client or redirect_uri")
        }
        val authenticatedClient =
            clientMetadataResolver
                .authenticateClient(
                    clientId = clientId,
                    clientAssertionType = clientAssertionType,
                    clientAssertion = clientAssertion,
                    authorizationServerIssuer = authorizationServerIssuer,
                ).getOrElse { throw it }
        authenticatedClient.requireBinding(
            keyId = stored.clientAuthKeyId,
            keyThumbprint = stored.clientAuthKeyThumbprint,
        )
        if (pkceChallenge(codeVerifier) != stored.codeChallenge) {
            throw OAuthInvalidGrantException("code_verifier did not match the authorization request")
        }

        val expectedNonce = nonceService.currentNonce()
        val proof =
            dpopVerifier
                .verify(
                    proof = dpopProof,
                    method = "POST",
                    htu = htu,
                    expectedNonce = expectedNonce,
                ).getOrElse { throw it }
        if (proof.keyThumbprint != stored.dpopKeyThumbprint) {
            throw OAuthInvalidGrantException("DPoP key did not match the original pushed authorization request")
        }

        val issued =
            accessTokenService.issueAccessToken(
                subjectDid = stored.subjectDid,
                clientId = stored.clientId,
                scope = stored.scope,
                keyThumbprint = proof.keyThumbprint,
            )
        val refreshToken = randomToken()
        refreshTokens[refreshToken] =
            StoredRefreshToken(
                token = refreshToken,
                clientId = stored.clientId,
                subjectDid = stored.subjectDid,
                scope = stored.scope,
                dpopKeyThumbprint = proof.keyThumbprint,
                clientAuthKeyId = stored.clientAuthKeyId,
                clientAuthKeyThumbprint = stored.clientAuthKeyThumbprint,
                expiresAt = clock.now() + REFRESH_TOKEN_TTL,
            )
        return OAuthTokenResponse(
            access_token = issued.token,
            token_type = "DPoP",
            expires_in = issued.expiresInSeconds,
            refresh_token = refreshToken,
            sub = stored.subjectDid,
            scope = stored.scope,
        )
    }

    /**
     * Exchanges a refresh token for a new DPoP-bound token pair.
     */
    suspend fun exchangeRefreshToken(
        refreshToken: String,
        clientId: String,
        clientAssertionType: String? = null,
        clientAssertion: String? = null,
        dpopProof: String,
        htu: String,
    ): OAuthTokenResponse {
        val stored =
            refreshTokens[refreshToken]
                ?.takeIf { it.revokedAt == null && clock.now() < it.expiresAt }
                ?: throw OAuthInvalidGrantException("Refresh token is invalid or expired")
        if (stored.clientId != clientId) {
            throw OAuthInvalidGrantException("Refresh token does not belong to this client")
        }
        val authenticatedClient =
            clientMetadataResolver
                .authenticateClient(
                    clientId = clientId,
                    clientAssertionType = clientAssertionType,
                    clientAssertion = clientAssertion,
                    authorizationServerIssuer = authorizationServerIssuer,
                ).getOrElse { throw it }
        authenticatedClient.requireBinding(
            keyId = stored.clientAuthKeyId,
            keyThumbprint = stored.clientAuthKeyThumbprint,
        )

        val expectedNonce = nonceService.currentNonce()
        val proof =
            dpopVerifier
                .verify(
                    proof = dpopProof,
                    method = "POST",
                    htu = htu,
                    expectedNonce = expectedNonce,
                ).getOrElse { throw it }
        if (proof.keyThumbprint != stored.dpopKeyThumbprint) {
            throw OAuthInvalidGrantException("Refresh token DPoP key did not match the original grant")
        }

        val issued =
            accessTokenService.issueAccessToken(
                subjectDid = stored.subjectDid,
                clientId = stored.clientId,
                scope = stored.scope,
                keyThumbprint = stored.dpopKeyThumbprint,
            )
        val rotatedRefreshToken = randomToken()
        refreshTokens.remove(refreshToken)
        refreshTokens[rotatedRefreshToken] =
            stored.copy(
                token = rotatedRefreshToken,
                expiresAt = clock.now() + REFRESH_TOKEN_TTL,
            )
        return OAuthTokenResponse(
            access_token = issued.token,
            token_type = "DPoP",
            expires_in = issued.expiresInSeconds,
            refresh_token = rotatedRefreshToken,
            sub = stored.subjectDid,
            scope = stored.scope,
        )
    }

    /**
     * Revokes [refreshToken]. Repeated revocations are treated as success.
     */
    suspend fun revokeRefreshToken(
        refreshToken: String,
        clientId: String,
        clientAssertionType: String? = null,
        clientAssertion: String? = null,
        dpopProof: String,
        htu: String,
    ) {
        val stored = refreshTokens[refreshToken] ?: return
        if (stored.clientId != clientId) {
            throw OAuthInvalidGrantException("Refresh token does not belong to this client")
        }
        val authenticatedClient =
            clientMetadataResolver
                .authenticateClient(
                    clientId = clientId,
                    clientAssertionType = clientAssertionType,
                    clientAssertion = clientAssertion,
                    authorizationServerIssuer = authorizationServerIssuer,
                ).getOrElse { throw it }
        authenticatedClient.requireBinding(
            keyId = stored.clientAuthKeyId,
            keyThumbprint = stored.clientAuthKeyThumbprint,
        )

        val expectedNonce = nonceService.currentNonce()
        val proof =
            dpopVerifier
                .verify(
                    proof = dpopProof,
                    method = "POST",
                    htu = htu,
                    expectedNonce = expectedNonce,
                ).getOrElse { throw it }
        if (proof.keyThumbprint != stored.dpopKeyThumbprint) {
            throw OAuthInvalidGrantException("Refresh token DPoP key did not match the original grant")
        }

        refreshTokens[refreshToken] = stored.copy(revokedAt = clock.now())
    }

    /**
     * Returns the currently valid server nonce for DPoP responses.
     */
    override fun nonce(): String = nonceService.currentNonce()

    override suspend fun createPushedAuthorizationRequest(request: PushedAuthorizationRequest): Result<PushedAuthorizationResponse> =
        runCatching {
            createPushedAuthorizationRequest(
                clientId = request.clientId,
                redirectUri = request.redirectUri,
                scope = request.scope,
                responseType = request.responseType,
                codeChallenge = request.codeChallenge,
                codeChallengeMethod = request.codeChallengeMethod,
                state = request.state,
                loginHint = request.loginHint,
                clientAssertionType = request.clientAssertionType,
                clientAssertion = request.clientAssertion,
                dpopProof = request.dpopProof,
                htu = request.htu,
            )
        }

    override fun loadAuthorizationPrompt(requestUri: String): Result<AuthorizationPrompt> =
        runCatching { describeAuthorizationRequest(requestUri) }

    override fun completeAuthorization(request: AuthorizationDecisionRequest): Result<String> =
        runCatching {
            completeAuthorization(
                requestUri = request.requestUri,
                subjectDid = request.subjectDid,
                subjectHandle = request.subjectHandle,
                approved = request.approved,
            )
        }

    override suspend fun exchangeAuthorizationCode(request: AuthorizationCodeTokenRequest): Result<OAuthTokenResponse> =
        runCatching {
            exchangeAuthorizationCode(
                code = request.code,
                redirectUri = request.redirectUri,
                clientId = request.clientId,
                codeVerifier = request.codeVerifier,
                clientAssertionType = request.clientAssertionType,
                clientAssertion = request.clientAssertion,
                dpopProof = request.dpopProof,
                htu = request.htu,
            )
        }

    override suspend fun exchangeRefreshToken(request: RefreshTokenGrantRequest): Result<OAuthTokenResponse> =
        runCatching {
            exchangeRefreshToken(
                refreshToken = request.refreshToken,
                clientId = request.clientId,
                clientAssertionType = request.clientAssertionType,
                clientAssertion = request.clientAssertion,
                dpopProof = request.dpopProof,
                htu = request.htu,
            )
        }

    override suspend fun revokeRefreshToken(request: OAuthRevokeRequest): Result<Unit> =
        runCatching {
            revokeRefreshToken(
                refreshToken = request.refreshToken,
                clientId = request.clientId,
                clientAssertionType = request.clientAssertionType,
                clientAssertion = request.clientAssertion,
                dpopProof = request.dpopProof,
                htu = request.htu,
            )
        }

    private fun pkceChallenge(codeVerifier: String): String {
        if (codeVerifier.isBlank()) {
            throw OAuthInvalidGrantException("code_verifier is required")
        }
        val digest = MessageDigest.getInstance(SHA_256_ALGORITHM).digest(codeVerifier.toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun randomToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        private val REQUEST_URI_TTL = 5.minutes
        private val AUTHORIZATION_CODE_TTL = 1.minutes
        private val REFRESH_TOKEN_TTL = 30.days
        private const val TOKEN_BYTES = 32
        private const val SHA_256_ALGORITHM = "SHA-256"
    }
}

/**
 * Stored request metadata surfaced to the consent UI.
 */
private data class StoredAuthorizationRequest(
    val requestUri: String,
    val clientId: String,
    val clientName: String,
    val redirectUri: String,
    val scope: String,
    val state: String?,
    val loginHint: String?,
    val codeChallenge: String,
    val dpopKeyThumbprint: String,
    val clientAuthKeyId: String?,
    val clientAuthKeyThumbprint: String?,
    val expiresAt: kotlin.time.Instant,
)

private data class StoredAuthorizationCode(
    val code: String,
    val clientId: String,
    val redirectUri: String,
    val subjectDid: String,
    val subjectHandle: String,
    val scope: String,
    val codeChallenge: String,
    val dpopKeyThumbprint: String,
    val clientAuthKeyId: String?,
    val clientAuthKeyThumbprint: String?,
    val expiresAt: kotlin.time.Instant,
)

private data class StoredRefreshToken(
    val token: String,
    val clientId: String,
    val subjectDid: String,
    val scope: String,
    val dpopKeyThumbprint: String,
    val clientAuthKeyId: String?,
    val clientAuthKeyThumbprint: String?,
    val expiresAt: kotlin.time.Instant,
    val revokedAt: kotlin.time.Instant? = null,
)

private fun AuthenticatedOAuthClient.requireBinding(
    keyId: String?,
    keyThumbprint: String?,
) {
    if (clientKeyId != keyId || clientKeyThumbprint != keyThumbprint) {
        throw OAuthInvalidGrantException("Client authentication did not match the original grant")
    }
}

private fun MutableMap<String, StoredAuthorizationRequest>.requireValidRequest(
    requestUri: String,
    clock: Clock,
): StoredAuthorizationRequest {
    val stored = this[requestUri] ?: throw OAuthInvalidRequestException("request_uri is invalid or expired")
    if (clock.now() >= stored.expiresAt) {
        remove(requestUri)
        throw OAuthInvalidRequestException("request_uri is invalid or expired")
    }
    return stored
}

private fun appendQuery(
    redirectUri: String,
    parameters: Map<String, String>,
): String {
    val separator = if ('?' in redirectUri) '&' else '?'
    return buildString {
        append(redirectUri)
        append(separator)
        append(parameters.entries.joinToString("&") { (key, value) -> "${key.urlEncode()}=${value.urlEncode()}" })
    }
}

private fun String.urlEncode(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
