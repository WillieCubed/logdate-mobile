package studio.hypertext.atproto.oauth.client

import org.bouncycastle.crypto.ec.CustomNamedCurves
import studio.hypertext.atproto.crypto.EcCurve
import studio.hypertext.atproto.crypto.EcKeySupport
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPrivateKeySpec
import java.util.Base64

/**
 * [OAuthCrypto] for the JVM, built on `java.security` and the `atproto-crypto` signing helpers.
 *
 * Signatures are deterministic (RFC 6979) and use the low-S form.
 */
public object JvmOAuthCrypto : OAuthCrypto {
    private val secureRandom = SecureRandom()
    private val urlEncoder = Base64.getUrlEncoder().withoutPadding()
    private val urlDecoder = Base64.getUrlDecoder()

    override fun randomBytes(byteCount: Int): ByteArray = ByteArray(byteCount).also(secureRandom::nextBytes)

    override fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)

    override fun generateEs256Key(): JsonWebKey {
        val keyPair = EcKeySupport.generateKeyPair(EcCurve.P256, secureRandom)
        val publicKey = keyPair.public as ECPublicKey
        val privateKey = keyPair.private as ECPrivateKey
        return JsonWebKey(
            kty = "EC",
            crv = P256_JWK_CURVE,
            x = urlEncoder.encodeToString(publicKey.w.affineX.toFixedWidth()),
            y = urlEncoder.encodeToString(publicKey.w.affineY.toFixedWidth()),
            d = urlEncoder.encodeToString(privateKey.s.toFixedWidth()),
            alg = ES256,
        )
    }

    override fun signEs256(
        privateKey: JsonWebKey,
        signingInput: ByteArray,
    ): ByteArray = EcKeySupport.signSha256(toPrivateKey(privateKey), EcCurve.P256, signingInput)

    /**
     * Generates a new client authentication key with the key identifier [kid].
     *
     * Store the result (for example as JSON in a secret manager) and pass it to
     * [OAuthClientConfig.keys].
     */
    public fun generateClientKey(kid: String): OAuthClientKey = OAuthClientKey(generateEs256Key().copy(kid = kid))

    /**
     * Converts a PKCS#8-encoded P-256 private key into a private [JsonWebKey] with [kid].
     */
    public fun privateJwkFromPkcs8(
        pkcs8: ByteArray,
        kid: String,
    ): JsonWebKey {
        val privateKey = EcKeySupport.decodePrivateKey(pkcs8) as ECPrivateKey
        require(privateKey.params.order == p256Parameters().order) { "The PKCS#8 key is not a P-256 key" }
        val curve = CustomNamedCurves.getByName("secp256r1")
        val point = curve.g.multiply(privateKey.s).normalize()
        return JsonWebKey(
            kty = "EC",
            crv = P256_JWK_CURVE,
            x = urlEncoder.encodeToString(point.affineXCoord.toBigInteger().toFixedWidth()),
            y = urlEncoder.encodeToString(point.affineYCoord.toBigInteger().toFixedWidth()),
            d = urlEncoder.encodeToString(privateKey.s.toFixedWidth()),
            kid = kid,
            alg = ES256,
        )
    }

    private fun toPrivateKey(jwk: JsonWebKey): ECPrivateKey {
        require(jwk.kty == "EC" && jwk.crv == P256_JWK_CURVE) { "ES256 needs a P-256 EC key, got kty=${jwk.kty} crv=${jwk.crv}" }
        val d = requireNotNull(jwk.d) { "Signing needs a private key" }
        val scalar = BigInteger(1, urlDecoder.decode(d))
        return KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(scalar, p256Parameters())) as ECPrivateKey
    }

    private fun p256Parameters(): ECParameterSpec =
        AlgorithmParameters
            .getInstance("EC")
            .apply { init(ECGenParameterSpec("secp256r1")) }
            .getParameterSpec(ECParameterSpec::class.java)

    private fun BigInteger.toFixedWidth(): ByteArray {
        val bytes = toByteArray()
        return when {
            bytes.size == COORDINATE_BYTES -> bytes
            bytes.size > COORDINATE_BYTES -> bytes.copyOfRange(bytes.size - COORDINATE_BYTES, bytes.size)
            else -> ByteArray(COORDINATE_BYTES - bytes.size) + bytes
        }
    }

    private const val P256_JWK_CURVE: String = "P-256"
    private const val COORDINATE_BYTES: Int = 32
}
