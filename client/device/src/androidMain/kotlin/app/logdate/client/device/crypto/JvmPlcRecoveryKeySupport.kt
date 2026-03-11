package app.logdate.client.device.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.HMacDSAKCalculator
import org.bouncycastle.jce.provider.BouncyCastleProvider
import studio.hypertext.atproto.crypto.Multikey
import java.math.BigInteger
import java.security.MessageDigest
import java.security.Security
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
internal class JvmPlcRecoveryKeySupport : PlcRecoveryKeySupport {
    init {
        if (Security.getProvider(BOUNCY_CASTLE_PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    override fun isValidPrivateKey(privateKeyMaterial: ByteArray): Boolean {
        val scalar = BigInteger(1, privateKeyMaterial)
        return scalar > BigInteger.ZERO && scalar < domain.n
    }

    override fun didKey(privateKeyMaterial: ByteArray): String {
        val publicKey = publicKey(privateKeyMaterial)
        return "did:key:${Multikey.encodeP256PublicKey(publicKey)}"
    }

    override fun signPayload(
        privateKeyMaterial: ByteArray,
        payload: ByteArray,
    ): String {
        val scalar = BigInteger(1, privateKeyMaterial)
        require(scalar > BigInteger.ZERO && scalar < domain.n) { "Invalid PLC recovery key material" }

        val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
        signer.init(true, ECPrivateKeyParameters(scalar, domain))
        val (r, s) = signer.generateSignature(sha256(payload))
        val lowS = normalizeLowS(s)
        val signature = r.toFixedWidth(COORDINATE_BYTES) + lowS.toFixedWidth(COORDINATE_BYTES)
        return Base64.UrlSafe.encode(signature).trimEnd('=')
    }

    private fun publicKey(privateKeyMaterial: ByteArray): ByteArray {
        val scalar = BigInteger(1, privateKeyMaterial)
        require(scalar > BigInteger.ZERO && scalar < domain.n) { "Invalid PLC recovery key material" }
        return domain.g
            .multiply(scalar)
            .normalize()
            .getEncoded(true)
    }

    private fun normalizeLowS(value: BigInteger): BigInteger {
        val halfOrder = domain.n.shiftRight(1)
        return if (value > halfOrder) domain.n.subtract(value) else value
    }

    private fun sha256(payload: ByteArray): ByteArray = MessageDigest.getInstance(SHA_256_ALGORITHM).digest(payload)

    private fun BigInteger.toFixedWidth(width: Int): ByteArray {
        val encoded = toByteArray()
        return when {
            encoded.size == width -> encoded
            encoded.size < width -> ByteArray(width - encoded.size) + encoded
            else -> encoded.copyOfRange(encoded.size - width, encoded.size)
        }
    }

    private companion object {
        private val curve = requireNotNull(CustomNamedCurves.getByName("secp256r1"))
        private val domain = ECDomainParameters(curve.curve, curve.g, curve.n, curve.h)
        private const val BOUNCY_CASTLE_PROVIDER_NAME: String = "BC"
        private const val COORDINATE_BYTES: Int = 32
        private const val SHA_256_ALGORITHM: String = "SHA-256"
    }
}
