package studio.hypertext.atproto.oauth.client

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.time.Instant

internal const val ES256: String = "ES256"
internal const val CLIENT_ASSERTION_TYPE_JWT_BEARER: String = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"

private val base64Url: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

private val jwtJson: Json = Json { explicitNulls = false }

internal fun ByteArray.base64UrlEncode(): String = base64Url.encode(this)

internal fun OAuthCrypto.randomToken(byteCount: Int = 32): String = randomBytes(byteCount).base64UrlEncode()

/**
 * Builds a compact JWS signed with ES256.
 */
internal fun OAuthCrypto.signJwt(
    privateKey: JsonWebKey,
    header: JsonObject,
    claims: JsonObject,
): String {
    val encodedHeader = header.toString().encodeToByteArray().base64UrlEncode()
    val encodedClaims = claims.toString().encodeToByteArray().base64UrlEncode()
    val signingInput = "$encodedHeader.$encodedClaims"
    val signature = signEs256(privateKey, signingInput.encodeToByteArray())
    return "$signingInput.${signature.base64UrlEncode()}"
}

/**
 * Builds a DPoP proof JWT (RFC 9449 section 4.2).
 *
 * @param htu The request URL without its query and fragment.
 * @param accessToken When present, the proof binds to this access token through the `ath` claim.
 */
internal fun OAuthCrypto.createDpopProof(
    dpopKey: JsonWebKey,
    htm: String,
    htu: String,
    nonce: String?,
    accessToken: String?,
    now: Instant,
): String {
    val header =
        buildJsonObject {
            put("typ", "dpop+jwt")
            put("alg", ES256)
            put("jwk", jwtJson.encodeToJsonElement(dpopKey.toBarePublicJwk()))
        }
    val claims =
        buildJsonObject {
            put("jti", randomToken(16))
            put("htm", htm)
            put("htu", htu)
            put("iat", now.epochSeconds)
            if (nonce != null) put("nonce", nonce)
            if (accessToken != null) put("ath", sha256(accessToken.encodeToByteArray()).base64UrlEncode())
        }
    return signJwt(dpopKey, header, claims)
}

/**
 * Builds a `private_key_jwt` client assertion (RFC 7523 section 3).
 *
 * @param audience The authorization server's `issuer`, as the AT Protocol OAuth profile requires.
 */
internal fun OAuthCrypto.createClientAssertion(
    clientKey: OAuthClientKey,
    clientId: String,
    audience: String,
    now: Instant,
): String {
    val header =
        buildJsonObject {
            put("alg", ES256)
            put("kid", clientKey.kid)
        }
    val claims =
        buildJsonObject {
            put("iss", clientId)
            put("sub", clientId)
            put("aud", audience)
            put("jti", randomToken(16))
            put("iat", now.epochSeconds)
            put("exp", now.epochSeconds + CLIENT_ASSERTION_LIFETIME_SECONDS)
        }
    return signJwt(clientKey.privateJwk, header, claims)
}

/**
 * Returns the PKCE `S256` challenge for [verifier] (RFC 7636 section 4.2).
 */
internal fun OAuthCrypto.pkceChallenge(verifier: String): String = sha256(verifier.encodeToByteArray()).base64UrlEncode()

/**
 * Removes the query and fragment from [url], as the DPoP `htu` claim requires.
 */
internal fun dpopHtu(url: String): String {
    val end =
        listOf(url.indexOf('?'), url.indexOf('#'))
            .filter { it >= 0 }
            .minOrNull()
    return if (end == null) url else url.substring(0, end)
}

private const val CLIENT_ASSERTION_LIFETIME_SECONDS: Long = 60
