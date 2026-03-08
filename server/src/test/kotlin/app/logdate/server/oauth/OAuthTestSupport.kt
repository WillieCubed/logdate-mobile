package app.logdate.server.oauth

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Instant

internal class MutableClock(
    initial: Instant,
) : Clock {
    var nowValue: Instant = initial

    override fun now(): Instant = nowValue
}

internal fun generateP256KeyPair(): KeyPair =
    KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

internal fun createDpopProof(
    keyPair: KeyPair,
    method: String,
    htu: String,
    nonce: String? = null,
    ath: String? = null,
    iat: Long = Clock.System.now().epochSeconds,
    jti: String = "proof-$iat",
    typ: String = "dpop+jwt",
    alg: String = "ES256",
    jwk: DpopPublicJwk = publicJwk(keyPair),
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
    val signature =
        java.security.Signature.getInstance("SHA256withECDSAinP1363Format").run {
            initSign(keyPair.private)
            update(signingInput.toByteArray(StandardCharsets.UTF_8))
            sign()
        }
    return "$signingInput.${base64UrlEncode(signature)}"
}

internal fun publicJwk(keyPair: KeyPair): DpopPublicJwk {
    val publicKey = keyPair.public as ECPublicKey
    return DpopPublicJwk(
        kty = "EC",
        crv = "P-256",
        x = base64UrlEncode(publicKey.w.affineX.toFixedWidth(32)),
        y = base64UrlEncode(publicKey.w.affineY.toFixedWidth(32)),
    )
}

internal fun clientMetadataJson(
    clientId: String,
    redirectUri: String,
    extraRedirectUris: List<String> = emptyList(),
    tokenEndpointAuthMethod: String = "none",
    dpopBoundAccessTokens: Boolean = true,
    scope: String = "atproto",
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
        appendLine("  \"dpop_bound_access_tokens\": $dpopBoundAccessTokens,")
        appendLine("  \"client_name\": \"Journal Viewer\"")
        append('}')
    }

internal fun tamperJwtPayload(token: String): String {
    val parts = token.split('.')
    val payload = String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8).replace("atproto", "tampered")
    val encodedPayload = base64UrlEncode(payload.toByteArray(StandardCharsets.UTF_8))
    return "${parts[0]}.$encodedPayload.${parts[2]}"
}

private fun publicJwkJson(jwk: DpopPublicJwk): String = """{"kty":"${jwk.kty}","crv":"${jwk.crv}","x":"${jwk.x}","y":"${jwk.y}"}"""

private fun base64UrlEncode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

private fun base64UrlDecode(value: String): ByteArray {
    val padding = "=".repeat((4 - value.length % 4) % 4)
    return Base64.getUrlDecoder().decode(value + padding)
}

private fun BigInteger.toFixedWidth(width: Int): ByteArray {
    val encoded = toByteArray()
    return when {
        encoded.size == width -> encoded
        encoded.size < width -> ByteArray(width - encoded.size) + encoded
        else -> encoded.copyOfRange(encoded.size - width, encoded.size)
    }
}
