package app.logdate.server.oauth

import kotlinx.serialization.Serializable
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Generates the ES256 public key material exposed by the OAuth JWKS endpoint.
 */
@OptIn(ExperimentalEncodingApi::class)
class OAuthKeyService(
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    private val keyPair: KeyPair by lazy(::generateKeyPair)
    private val currentJwk: JsonWebKey by lazy(::buildCurrentJwk)

    /**
     * Returns the current JSON Web Key Set for OAuth discovery.
     */
    fun jwks(): JsonWebKeySet = JsonWebKeySet(keys = listOf(currentJwk))

    /**
     * Returns the current ES256 public key as a JWK.
     */
    fun currentJwk(): JsonWebKey = currentJwk

    /**
     * Signs [payload] with the current ES256 private key.
     */
    fun sign(payload: ByteArray): ByteArray =
        Signature.getInstance(JWS_SIGNATURE_ALGORITHM).run {
            initSign(keyPair.private, secureRandom)
            update(payload)
            sign()
        }

    /**
     * Returns the current public key for server-side JWT verification.
     */
    fun publicKey(): PublicKey = keyPair.public

    private fun generateKeyPair(): KeyPair =
        KeyPairGenerator.getInstance(KEY_ALGORITHM).run {
            initialize(ECGenParameterSpec(P256_CURVE_NAME), secureRandom)
            generateKeyPair()
        }

    private fun buildCurrentJwk(): JsonWebKey {
        val publicKey = keyPair.public as ECPublicKey
        val x = base64Url(publicKey.w.affineX.toFixedWidth(P256_COORDINATE_BYTES))
        val y = base64Url(publicKey.w.affineY.toFixedWidth(P256_COORDINATE_BYTES))
        val kid = jwkThumbprint(x = x, y = y)

        return JsonWebKey(
            kty = KEY_ALGORITHM,
            use = KEY_USE,
            key_ops = KEY_OPERATIONS,
            alg = JWS_ALGORITHM,
            kid = kid,
            crv = JWK_CURVE_NAME,
            x = x,
            y = y,
        )
    }

    private fun jwkThumbprint(
        x: String,
        y: String,
    ): String {
        val canonicalJwk = """{"crv":"P-256","kty":"EC","x":"$x","y":"$y"}"""
        val digest = MessageDigest.getInstance(SHA_256_ALGORITHM).digest(canonicalJwk.toByteArray())
        return base64Url(digest)
    }

    private fun base64Url(bytes: ByteArray): String = Base64.UrlSafe.encode(bytes).trimEnd('=')

    private companion object {
        private const val KEY_ALGORITHM = "EC"
        private const val KEY_USE = "sig"
        private val KEY_OPERATIONS = listOf("verify")
        private const val JWS_ALGORITHM = "ES256"
        private const val JWS_SIGNATURE_ALGORITHM = "SHA256withECDSAinP1363Format"
        private const val JWK_CURVE_NAME = "P-256"
        private const val P256_CURVE_NAME = "secp256r1"
        private const val SHA_256_ALGORITHM = "SHA-256"
        private const val P256_COORDINATE_BYTES = 32
    }
}

/**
 * OAuth JWKS response payload.
 */
@Serializable
data class JsonWebKeySet(
    val keys: List<JsonWebKey>,
)

/**
 * Public JWK for the server's ES256 OAuth signing key.
 */
@Serializable
data class JsonWebKey(
    val kty: String,
    val use: String,
    val key_ops: List<String>,
    val alg: String,
    val kid: String,
    val crv: String,
    val x: String,
    val y: String,
)

private fun BigInteger.toFixedWidth(width: Int): ByteArray {
    val encoded = toByteArray()
    return when {
        encoded.size == width -> encoded
        encoded.size < width -> ByteArray(width - encoded.size) + encoded
        else -> encoded.copyOfRange(encoded.size - width, encoded.size)
    }
}
