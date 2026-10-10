package app.logdate.server.oauth

import app.logdate.server.config.AtprotoSigningKeyKek
import app.logdate.server.identity.AtRestKeyCipher
import kotlinx.serialization.Serializable
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock

/**
 * Holds the ES256 key that signs OAuth access tokens and is published by the OAuth JWKS endpoint.
 *
 * The key lives in [repository], encrypted under the deployment's key-encryption key, so every
 * server instance and every restart signs with the same key and tokens stay valid across them.
 * The first instance that finds no stored key creates one.
 */
@OptIn(ExperimentalEncodingApi::class)
class OAuthKeyService(
    private val repository: OAuthSigningKeyRepository = InMemoryOAuthSigningKeyRepository(),
    encryptionKeySeed: String = AtprotoSigningKeyKek.developmentValue,
    private val secureRandom: SecureRandom = SecureRandom(),
    private val clock: Clock = Clock.System,
) {
    private val cipher = AtRestKeyCipher(encryptionKeySeed, secureRandom)
    private val keyPair: KeyPair by lazy(::loadOrCreateKeyPair)
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

    private fun loadOrCreateKeyPair(): KeyPair {
        val stored = repository.getOrCreate(::newStoredKey)
        val privateKeyBytes =
            try {
                cipher.decrypt(stored.privateKeyEncrypted)
            } catch (e: GeneralSecurityException) {
                throw IllegalStateException(
                    "The stored OAuth signing key can't be decrypted. ${AtprotoSigningKeyKek.envVar} must keep the " +
                        "value that encrypted it.",
                    e,
                )
            }
        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
        return KeyPair(
            keyFactory.generatePublic(X509EncodedKeySpec(Base64.decode(stored.publicKeySpki))),
            keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes)),
        )
    }

    private fun newStoredKey(): StoredOAuthSigningKey {
        val generated =
            KeyPairGenerator.getInstance(KEY_ALGORITHM).run {
                initialize(ECGenParameterSpec(P256_CURVE_NAME), secureRandom)
                generateKeyPair()
            }
        val publicKey = generated.public as ECPublicKey
        return StoredOAuthSigningKey(
            keyId = jwkThumbprint(x = coordinate(publicKey.w.affineX), y = coordinate(publicKey.w.affineY)),
            privateKeyEncrypted = cipher.encrypt(generated.private.encoded),
            publicKeySpki = Base64.encode(publicKey.encoded),
            createdAt = clock.now(),
        )
    }

    private fun coordinate(value: BigInteger): String = base64Url(value.toFixedWidth(P256_COORDINATE_BYTES))

    private fun buildCurrentJwk(): JsonWebKey {
        val publicKey = keyPair.public as ECPublicKey
        val x = coordinate(publicKey.w.affineX)
        val y = coordinate(publicKey.w.affineY)
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
