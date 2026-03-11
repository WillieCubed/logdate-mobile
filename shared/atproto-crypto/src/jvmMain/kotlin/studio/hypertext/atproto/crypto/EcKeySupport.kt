package studio.hypertext.atproto.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.HMacDSAKCalculator
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec

/**
 * JVM elliptic-curve helpers used by hosted AT Protocol identity and repo signing flows.
 */
public object EcKeySupport {
    init {
        if (Security.getProvider(BOUNCY_CASTLE_PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    /**
     * Generates a new EC [KeyPair] for [curve].
     */
    public fun generateKeyPair(
        curve: EcCurve,
        secureRandom: SecureRandom = SecureRandom(),
    ): KeyPair =
        KeyPairGenerator
            .getInstance(KEY_ALGORITHM, BOUNCY_CASTLE_PROVIDER_NAME)
            .apply { initialize(ECGenParameterSpec(curve.parameterSpecName), secureRandom) }
            .generateKeyPair()

    /**
     * Decodes a PKCS#8 EC private key.
     */
    public fun decodePrivateKey(pkcs8: ByteArray): PrivateKey =
        KeyFactory
            .getInstance(KEY_ALGORITHM, BOUNCY_CASTLE_PROVIDER_NAME)
            .generatePrivate(PKCS8EncodedKeySpec(pkcs8))

    /**
     * Encodes [publicKey] to an AT Protocol multikey value for [curve].
     */
    public fun encodePublicKeyMultibase(
        publicKey: ECPublicKey,
        curve: EcCurve,
    ): String {
        val compressedKey = compressPublicKey(publicKey)
        return when (curve) {
            EcCurve.P256 -> Multikey.encodeP256PublicKey(compressedKey)
            EcCurve.K256 -> Multikey.encodeK256PublicKey(compressedKey)
        }
    }

    /**
     * Decodes an EC public key from [multibase].
     */
    public fun decodePublicKey(multibase: String): DecodedEcPublicKey {
        val decoded = Multikey.decode(multibase)
        val curve =
            when {
                decoded.startsWith(Multikey.p256PublicKeyPrefix) -> EcCurve.P256
                decoded.startsWith(Multikey.k256PublicKeyPrefix) -> EcCurve.K256
                else -> error("Unsupported EC multikey prefix")
            }
        val compressedPoint = decoded.copyOfRange(multikeyPrefix(curve).size, decoded.size)
        val parameters = parameters(curve)
        val point = decompressPoint(compressedPoint, parameters)
        val publicKey =
            KeyFactory
                .getInstance(KEY_ALGORITHM, BOUNCY_CASTLE_PROVIDER_NAME)
                .generatePublic(ECPublicKeySpec(point, parameters)) as ECPublicKey
        return DecodedEcPublicKey(curve = curve, publicKey = publicKey)
    }

    /**
     * Decodes an EC public key from JWK coordinates for [curve].
     */
    public fun decodePublicKeyFromJwk(
        curve: EcCurve,
        x: ByteArray,
        y: ByteArray,
    ): ECPublicKey {
        val parameters = parameters(curve)
        val point = ECPoint(BigInteger(1, x), BigInteger(1, y))
        return KeyFactory
            .getInstance(KEY_ALGORITHM, BOUNCY_CASTLE_PROVIDER_NAME)
            .generatePublic(ECPublicKeySpec(point, parameters)) as ECPublicKey
    }

    /**
     * Signs [payload] with SHA-256 and deterministic ECDSA for [curve].
     */
    public fun signSha256(
        privateKey: PrivateKey,
        curve: EcCurve,
        payload: ByteArray,
    ): ByteArray {
        val digest = sha256(payload)
        val domain = domainParameters(curve)
        val ecPrivateKey = privateKey as ECPrivateKey
        val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
        signer.init(true, ECPrivateKeyParameters(ecPrivateKey.s, domain))
        val (r, s) = signer.generateSignature(digest)
        val lowS = normalizeLowS(s = s, curve = curve)
        return r.toFixedWidth(curve.coordinateBytes) + lowS.toFixedWidth(curve.coordinateBytes)
    }

    /**
     * Verifies a SHA-256 ECDSA signature for [payload].
     */
    public fun verifySha256(
        publicKey: PublicKey,
        curve: EcCurve,
        payload: ByteArray,
        signature: ByteArray,
    ): Boolean {
        require(signature.size == curve.coordinateBytes * 2) { "Unexpected ECDSA signature size" }
        val digest = sha256(payload)
        val domain = domainParameters(curve)
        val ecPublicKey = publicKey as ECPublicKey
        val signer = ECDSASigner()
        signer.init(
            false,
            ECPublicKeyParameters(
                domain.curve.createPoint(ecPublicKey.w.affineX, ecPublicKey.w.affineY),
                domain,
            ),
        )
        val coordinateWidth = curve.coordinateBytes
        val r = BigInteger(1, signature.copyOfRange(0, coordinateWidth))
        val s = BigInteger(1, signature.copyOfRange(coordinateWidth, signature.size))
        return signer.verifySignature(digest, r, s)
    }

    private fun parameters(curve: EcCurve): ECParameterSpec =
        AlgorithmParameters
            .getInstance(KEY_ALGORITHM, BOUNCY_CASTLE_PROVIDER_NAME)
            .apply { init(ECGenParameterSpec(curve.parameterSpecName)) }
            .getParameterSpec(ECParameterSpec::class.java)

    private fun domainParameters(curve: EcCurve): ECDomainParameters {
        val parameters =
            requireNotNull(CustomNamedCurves.getByName(curve.parameterSpecName)) {
                "Unsupported curve ${curve.parameterSpecName}"
            }
        return ECDomainParameters(parameters.curve, parameters.g, parameters.n, parameters.h)
    }

    private fun compressPublicKey(publicKey: ECPublicKey): ByteArray {
        val x = publicKey.w.affineX.toFixedWidth(EC_COMPRESSED_POINT_BYTES - 1)
        val prefix = if (publicKey.w.affineY.testBit(0)) COMPRESSED_ODD_PREFIX else COMPRESSED_EVEN_PREFIX
        return byteArrayOf(prefix.toByte()) + x
    }

    private fun decompressPoint(
        compressedPoint: ByteArray,
        parameters: ECParameterSpec,
    ): ECPoint {
        val x = BigInteger(1, compressedPoint.copyOfRange(1, compressedPoint.size))
        val prime = (parameters.curve.field as ECFieldFp).p
        val a = parameters.curve.a
        val b = parameters.curve.b
        val alpha =
            x
                .modPow(BigInteger.valueOf(3), prime)
                .add(a.multiply(x))
                .add(b)
                .mod(prime)
        val beta = alpha.modPow(prime.add(BigInteger.ONE).shiftRight(2), prime)
        val yIsOdd = compressedPoint[0].toInt() == COMPRESSED_ODD_PREFIX
        val y = if (beta.testBit(0) == yIsOdd) beta else prime.subtract(beta)
        return ECPoint(x, y)
    }

    private fun normalizeLowS(
        s: BigInteger,
        curve: EcCurve,
    ): BigInteger {
        val order = domainParameters(curve).n
        val halfOrder = order.shiftRight(1)
        return if (s > halfOrder) order.subtract(s) else s
    }

    private fun multikeyPrefix(curve: EcCurve): ByteArray =
        when (curve) {
            EcCurve.P256 -> Multikey.p256PublicKeyPrefix
            EcCurve.K256 -> Multikey.k256PublicKeyPrefix
        }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance(SHA_256_ALGORITHM).digest(bytes)

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && copyOfRange(0, prefix.size).contentEquals(prefix)

    private fun BigInteger.toFixedWidth(width: Int): ByteArray {
        val encoded = toByteArray()
        return when {
            encoded.size == width -> encoded
            encoded.size < width -> ByteArray(width - encoded.size) + encoded
            else -> encoded.copyOfRange(encoded.size - width, encoded.size)
        }
    }

    private const val KEY_ALGORITHM: String = "EC"
    private const val BOUNCY_CASTLE_PROVIDER_NAME: String = "BC"
    private const val SHA_256_ALGORITHM: String = "SHA-256"
    private const val EC_COMPRESSED_POINT_BYTES: Int = 33
    private const val COMPRESSED_EVEN_PREFIX: Int = 0x02
    private const val COMPRESSED_ODD_PREFIX: Int = 0x03
}

/**
 * Decoded EC public key plus the AT Protocol curve it uses.
 */
public data class DecodedEcPublicKey(
    val curve: EcCurve,
    val publicKey: ECPublicKey,
)
