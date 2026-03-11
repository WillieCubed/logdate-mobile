package app.logdate.server.oauth

import studio.hypertext.atproto.crypto.EcCurve
import studio.hypertext.atproto.crypto.EcKeySupport
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.interfaces.ECPublicKey
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Instant

internal class MutableClock(
    initial: Instant,
) : Clock {
    var nowValue: Instant = initial

    override fun now(): Instant = nowValue
}

internal fun generateP256KeyPair(): KeyPair = EcKeySupport.generateKeyPair(EcCurve.P256)

internal fun generateK256KeyPair(): KeyPair = EcKeySupport.generateKeyPair(EcCurve.K256)

internal fun createDpopProof(
    keyPair: KeyPair,
    method: String,
    htu: String,
    nonce: String? = null,
    ath: String? = null,
    iat: Long = Clock.System.now().epochSeconds,
    jti: String = "proof-$iat",
    typ: String = "dpop+jwt",
    alg: String = jwkAlgorithm(EcCurve.P256),
    jwk: DpopPublicJwk = publicJwk(keyPair),
    curve: EcCurve = EcCurve.P256,
): String {
    val header =
        buildString {
            append("""{"typ":"$typ","alg":"$alg","jwk":${publicJwkJson(jwk)}}""")
        }
    val claims =
        buildString {
            append("""{"jti":"$jti","htm":"${method.uppercase()}","htu":"$htu","iat":$iat""")
            nonce?.let { append(",\"nonce\":\"$it\"") }
            ath?.let { append(",\"ath\":\"$it\"") }
            append('}')
        }
    val encodedHeader = base64UrlEncode(header.toByteArray(StandardCharsets.UTF_8))
    val encodedClaims = base64UrlEncode(claims.toByteArray(StandardCharsets.UTF_8))
    val signingInput = "$encodedHeader.$encodedClaims"
    val signature = EcKeySupport.signSha256(keyPair.private, curve, signingInput.toByteArray(StandardCharsets.UTF_8))
    return "$signingInput.${base64UrlEncode(signature)}"
}

internal fun publicJwk(
    keyPair: KeyPair,
    curve: EcCurve = EcCurve.P256,
): DpopPublicJwk {
    val publicKey = keyPair.public as ECPublicKey
    return DpopPublicJwk(
        kty = "EC",
        crv = curve.jwkCurveName,
        x = base64UrlEncode(publicKey.w.affineX.toFixedWidth(ECDSA_COORDINATE_BYTES)),
        y = base64UrlEncode(publicKey.w.affineY.toFixedWidth(ECDSA_COORDINATE_BYTES)),
    )
}

internal fun clientMetadataJson(
    clientId: String,
    redirectUri: String,
    extraRedirectUris: List<String> = emptyList(),
    tokenEndpointAuthMethod: String = "none",
    tokenEndpointAuthSigningAlg: String? = null,
    dpopBoundAccessTokens: Boolean = true,
    scope: String = "atproto",
    jwksJson: String? = null,
    jwksUri: String? = null,
): String =
    buildString {
        val redirectUris =
            (listOf(redirectUri) + extraRedirectUris)
                .joinToString(separator = ", ") { "\"$it\"" }
        appendLine("{")
        appendLine("  \"client_id\": \"$clientId\",")
        appendLine("  \"redirect_uris\": [$redirectUris],")
        appendLine("  \"grant_types\": [\"authorization_code\", \"refresh_token\"],")
        appendLine("  \"response_types\": [\"code\"],")
        appendLine("  \"scope\": \"$scope\",")
        appendLine("  \"token_endpoint_auth_method\": \"$tokenEndpointAuthMethod\",")
        tokenEndpointAuthSigningAlg?.let { appendLine("  \"token_endpoint_auth_signing_alg\": \"$it\",") }
        appendLine("  \"dpop_bound_access_tokens\": $dpopBoundAccessTokens,")
        jwksJson?.let { appendLine("  \"jwks\": $it,") }
        jwksUri?.let { appendLine("  \"jwks_uri\": \"$it\",") }
        appendLine("  \"client_name\": \"Journal Viewer\"")
        append('}')
    }

internal fun createClientAssertion(
    keyPair: KeyPair,
    clientId: String,
    audience: String,
    kid: String = "client-key-1",
    iat: Long = Clock.System.now().epochSeconds,
    exp: Long = iat + 300,
    jti: String = "assertion-$iat",
    alg: String = jwkAlgorithm(EcCurve.P256),
    curve: EcCurve = EcCurve.P256,
): String {
    val header = """{"alg":"$alg","kid":"$kid"}"""
    val claims =
        """
        {
          "iss":"$clientId",
          "sub":"$clientId",
          "aud":"$audience",
          "iat":$iat,
          "exp":$exp,
          "jti":"$jti"
        }
        """.trimIndent().replace("\n", "")
    val encodedHeader = base64UrlEncode(header.toByteArray(StandardCharsets.UTF_8))
    val encodedClaims = base64UrlEncode(claims.toByteArray(StandardCharsets.UTF_8))
    val signingInput = "$encodedHeader.$encodedClaims"
    val signature = EcKeySupport.signSha256(keyPair.private, curve, signingInput.toByteArray(StandardCharsets.UTF_8))
    return "$signingInput.${base64UrlEncode(signature)}"
}

internal fun clientJwksJson(
    keyPair: KeyPair,
    kid: String = "client-key-1",
    alg: String = jwkAlgorithm(EcCurve.P256),
    curve: EcCurve = EcCurve.P256,
): String = """{"keys":[${clientJwkJson(keyPair, kid, alg, curve)}]}"""

internal fun tamperJwtPayload(token: String): String {
    val parts = token.split('.')
    val payload = String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8).replace("atproto", "tampered")
    val encodedPayload = base64UrlEncode(payload.toByteArray(StandardCharsets.UTF_8))
    return "${parts[0]}.$encodedPayload.${parts[2]}"
}

private fun publicJwkJson(jwk: DpopPublicJwk): String = """{"kty":"${jwk.kty}","crv":"${jwk.crv}","x":"${jwk.x}","y":"${jwk.y}"}"""

private fun clientJwkJson(
    keyPair: KeyPair,
    kid: String,
    alg: String,
    curve: EcCurve,
): String {
    val jwk = publicJwk(keyPair, curve)
    return """{"kty":"${jwk.kty}","crv":"${jwk.crv}","x":"${jwk.x}","y":"${jwk.y}","kid":"$kid","alg":"$alg","use":"sig"}"""
}

private fun jwkAlgorithm(curve: EcCurve): String = curve.jwsAlgorithm

private fun base64UrlEncode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

private fun base64UrlDecode(value: String): ByteArray {
    val padding = "=".repeat((4 - value.length % 4) % 4)
    return Base64.getUrlDecoder().decode(value + padding)
}

private const val ECDSA_COORDINATE_BYTES: Int = 32

private fun BigInteger.toFixedWidth(width: Int): ByteArray {
    val encoded = toByteArray()
    return when {
        encoded.size == width -> encoded
        encoded.size < width -> ByteArray(width - encoded.size) + encoded
        else -> encoded.copyOfRange(encoded.size - width, encoded.size)
    }
}
