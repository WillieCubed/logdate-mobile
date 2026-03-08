package app.logdate.server.oauth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.Signature
import java.util.Base64
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Issues and validates ES256-signed OAuth access tokens backed by the server JWKS.
 */
class OAuthAccessTokenService(
    private val config: OAuthConfig,
    private val keyService: OAuthKeyService,
    private val clock: Clock = Clock.System,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /**
     * Creates a DPoP-bound access token for [subjectDid].
     */
    fun issueAccessToken(
        subjectDid: String,
        clientId: String,
        scope: String,
        keyThumbprint: String,
    ): IssuedOAuthAccessToken {
        val now = clock.now()
        val payload =
            OAuthAccessTokenClaims(
                iss = config.normalizedIssuer,
                sub = subjectDid,
                aud = config.normalizedResource,
                exp = (now + ACCESS_TOKEN_TTL).epochSeconds,
                iat = now.epochSeconds,
                jti = UUID.randomUUID().toString(),
                scope = scope,
                clientId = clientId,
                cnf = OAuthTokenConfirmation(jkt = keyThumbprint),
            )
        val header =
            OAuthJwtHeader(
                alg = JWS_ALGORITHM,
                typ = JWT_TYPE,
                kid = keyService.currentJwk().kid,
            )
        val encodedHeader = base64UrlEncode(json.encodeToString(header).toByteArray(StandardCharsets.UTF_8))
        val encodedPayload = base64UrlEncode(json.encodeToString(payload).toByteArray(StandardCharsets.UTF_8))
        val signingInput = "$encodedHeader.$encodedPayload"
        val signature = keyService.sign(signingInput.toByteArray(StandardCharsets.UTF_8))
        val signedJwt = "$signingInput.${base64UrlEncode(signature)}"
        return IssuedOAuthAccessToken(
            token = signedJwt,
            expiresInSeconds = ACCESS_TOKEN_TTL.inWholeSeconds,
            subjectDid = subjectDid,
        )
    }

    /**
     * Validates [token] and returns the bound claims when valid.
     */
    fun validateAccessToken(token: String): ValidatedOAuthAccessToken? =
        runCatching {
            val parts = token.split('.')
            if (parts.size != JWT_SEGMENT_COUNT) {
                return null
            }

            val header = json.decodeFromString<OAuthJwtHeader>(decodeJson(parts[0]))
            val claims = json.decodeFromString<OAuthAccessTokenClaims>(decodeJson(parts[1]))
            if (header.alg != JWS_ALGORITHM || header.typ != JWT_TYPE || header.kid != keyService.currentJwk().kid) {
                return null
            }

            val verifier = Signature.getInstance(JWS_SIGNATURE_ALGORITHM)
            verifier.initVerify(keyService.publicKey())
            verifier.update("${parts[0]}.${parts[1]}".toByteArray(StandardCharsets.UTF_8))
            if (!verifier.verify(base64UrlDecode(parts[2]))) {
                return null
            }

            val now = clock.now().epochSeconds
            if (claims.iss != config.normalizedIssuer || claims.aud != config.normalizedResource || claims.exp <= now) {
                return null
            }

            ValidatedOAuthAccessToken(
                subjectDid = claims.sub,
                clientId = claims.clientId,
                scope = claims.scope,
                keyThumbprint = claims.cnf.jkt,
            )
        }.getOrNull()

    private fun decodeJson(segment: String): String = String(base64UrlDecode(segment), StandardCharsets.UTF_8)

    private fun base64UrlEncode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun base64UrlDecode(value: String): ByteArray {
        val padding = "=".repeat((4 - value.length % 4) % 4)
        return Base64.getUrlDecoder().decode(value + padding)
    }

    private companion object {
        private val ACCESS_TOKEN_TTL: Duration = 1.hours
        private const val JWT_SEGMENT_COUNT = 3
        private const val JWS_ALGORITHM = "ES256"
        private const val JWS_SIGNATURE_ALGORITHM = "SHA256withECDSAinP1363Format"
        private const val JWT_TYPE = "at+jwt"
    }
}

/**
 * Successful access-token issuance result.
 */
data class IssuedOAuthAccessToken(
    val token: String,
    val expiresInSeconds: Long,
    val subjectDid: String,
)

/**
 * Validated access-token claims needed by protected resource routes.
 */
data class ValidatedOAuthAccessToken(
    val subjectDid: String,
    val clientId: String,
    val scope: String,
    val keyThumbprint: String,
)

@Serializable
private data class OAuthJwtHeader(
    val alg: String,
    val typ: String,
    val kid: String,
)

@Serializable
private data class OAuthAccessTokenClaims(
    val iss: String,
    val sub: String,
    val aud: String,
    val exp: Long,
    val iat: Long,
    val jti: String,
    val scope: String,
    @SerialName("client_id")
    val clientId: String,
    val cnf: OAuthTokenConfirmation,
)

@Serializable
private data class OAuthTokenConfirmation(
    val jkt: String,
)
