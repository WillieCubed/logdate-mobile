package app.logdate.client.device.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import studio.hypertext.atproto.crypto.Multikey
import kotlin.io.encoding.Base64

/**
 * [PlcRecoveryKeySupport] on the platform's own cryptography provider: CryptoKit on iOS.
 *
 * The provider imports the derived 32-byte scalar as a P-256 private key, reports its public key
 * and signs with ECDSA over SHA-256. PLC accepts only low-`s` signatures, and CryptoKit does not
 * normalise `s`, so a high `s` is replaced with `n - s` here.
 */
internal class CryptographyPlcRecoveryKeySupport(
    private val provider: CryptographyProvider = CryptographyProvider.Default,
) : PlcRecoveryKeySupport {
    private val ecdsa: ECDSA get() = provider.get(ECDSA)

    override fun isValidPrivateKey(privateKeyMaterial: ByteArray): Boolean =
        privateKeyMaterial.size == SCALAR_BYTES &&
            privateKeyMaterial.any { it != ZERO } &&
            compareUnsigned(privateKeyMaterial, CURVE_ORDER) < 0

    override fun didKey(privateKeyMaterial: ByteArray): String {
        val uncompressed =
            privateKey(privateKeyMaterial)
                .getPublicKeyBlocking()
                .encodeToByteArrayBlocking(EC.PublicKey.Format.RAW)
        return "did:key:${Multikey.encodeP256PublicKey(compress(uncompressed))}"
    }

    override fun signPayload(
        privateKeyMaterial: ByteArray,
        payload: ByteArray,
    ): String {
        val signature =
            privateKey(privateKeyMaterial)
                .signatureGenerator(SHA256, ECDSA.SignatureFormat.RAW)
                .generateSignatureBlocking(payload)
        check(signature.size == SCALAR_BYTES * 2) { "Unexpected P-256 signature length ${signature.size}" }
        val r = signature.copyOfRange(0, SCALAR_BYTES)
        val s = signature.copyOfRange(SCALAR_BYTES, SCALAR_BYTES * 2)
        val lowS = if (compareUnsigned(s, HALF_CURVE_ORDER) > 0) subtract(CURVE_ORDER, s) else s
        return Base64.UrlSafe.encode(r + lowS).trimEnd('=')
    }

    private fun privateKey(privateKeyMaterial: ByteArray): ECDSA.PrivateKey {
        require(isValidPrivateKey(privateKeyMaterial)) { "Invalid PLC recovery key material" }
        return ecdsa
            .privateKeyDecoder(EC.Curve.P256)
            .decodeFromByteArrayBlocking(EC.PrivateKey.Format.RAW, privateKeyMaterial)
    }

    private fun compress(uncompressed: ByteArray): ByteArray {
        check(uncompressed.size == 1 + SCALAR_BYTES * 2 && uncompressed[0] == UNCOMPRESSED_PREFIX) {
            "Unexpected P-256 public key encoding"
        }
        val x = uncompressed.copyOfRange(1, 1 + SCALAR_BYTES)
        val yIsOdd = uncompressed.last().toInt() and 1 == 1
        return byteArrayOf(if (yIsOdd) ODD_Y_PREFIX else EVEN_Y_PREFIX) + x
    }

    internal companion object {
        private const val SCALAR_BYTES: Int = 32
        private const val ZERO: Byte = 0
        private const val UNCOMPRESSED_PREFIX: Byte = 0x04
        private const val EVEN_Y_PREFIX: Byte = 0x02
        private const val ODD_Y_PREFIX: Byte = 0x03

        /** The order `n` of the P-256 base point. */
        internal val CURVE_ORDER: ByteArray = hex("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551")

        /** `n / 2`, the largest `s` PLC accepts. */
        internal val HALF_CURVE_ORDER: ByteArray = hex("7fffffff800000007fffffffffffffffde737d56d38bcf4279dce5617e3192a8")

        /** Compares two equal-length big-endian unsigned integers. */
        internal fun compareUnsigned(
            left: ByteArray,
            right: ByteArray,
        ): Int {
            require(left.size == right.size) { "Operands must have the same length" }
            for (index in left.indices) {
                val difference = (left[index].toInt() and 0xff) - (right[index].toInt() and 0xff)
                if (difference != 0) return difference
            }
            return 0
        }

        /** `minuend - subtrahend` for equal-length big-endian unsigned integers with `minuend >= subtrahend`. */
        internal fun subtract(
            minuend: ByteArray,
            subtrahend: ByteArray,
        ): ByteArray {
            require(compareUnsigned(minuend, subtrahend) >= 0) { "Subtraction would be negative" }
            val result = ByteArray(minuend.size)
            var borrow = 0
            for (index in minuend.indices.reversed()) {
                val difference = (minuend[index].toInt() and 0xff) - (subtrahend[index].toInt() and 0xff) - borrow
                borrow = if (difference < 0) 1 else 0
                result[index] = (difference + (borrow shl 8)).toByte()
            }
            return result
        }

        private fun hex(value: String): ByteArray = ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
