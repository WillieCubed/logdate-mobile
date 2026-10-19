package app.logdate.client.device.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the recovery key's P-256 behaviour on every platform. The same key material has to give
 * the same public key on Android, desktop and iOS, or a phrase saved on one device would not
 * match the key registered from another.
 *
 * The key is the P-256 test key from RFC 6979 appendix A.2.5.
 */
class PlcRecoveryKeySupportTest {
    @Test
    fun `platform support derives the pinned did key`() {
        assertEquals(EXPECTED_DID_KEY, platformPlcRecoveryKeySupport().didKey(PRIVATE_KEY))
    }

    @Test
    fun `platform support signs with verifiable low s signatures`() {
        assertSignsVerifiably(platformPlcRecoveryKeySupport())
    }

    @Test
    fun `provider support derives the pinned did key`() {
        assertEquals(EXPECTED_DID_KEY, CryptographyPlcRecoveryKeySupport().didKey(PRIVATE_KEY))
    }

    @Test
    fun `provider support signs with verifiable low s signatures`() {
        assertSignsVerifiably(CryptographyPlcRecoveryKeySupport())
    }

    @Test
    fun `platform support accepts only scalars between one and the curve order`() {
        val support = platformPlcRecoveryKeySupport()

        assertTrue(support.isValidPrivateKey(PRIVATE_KEY))
        assertTrue(
            support.isValidPrivateKey(CryptographyPlcRecoveryKeySupport.subtract(CryptographyPlcRecoveryKeySupport.CURVE_ORDER, ONE)),
        )
        assertFalse(support.isValidPrivateKey(ByteArray(32)))
        assertFalse(support.isValidPrivateKey(CryptographyPlcRecoveryKeySupport.CURVE_ORDER))
    }

    @Test
    fun `byte arithmetic compares and subtracts big endian integers`() {
        val order = CryptographyPlcRecoveryKeySupport.CURVE_ORDER
        val half = CryptographyPlcRecoveryKeySupport.HALF_CURVE_ORDER

        assertTrue(CryptographyPlcRecoveryKeySupport.compareUnsigned(half, order) < 0)
        assertEquals(0, CryptographyPlcRecoveryKeySupport.compareUnsigned(order, order.copyOf()))
        // n - (n/2) is n/2 + 1 because n is odd.
        assertContentEquals(
            CryptographyPlcRecoveryKeySupport.subtract(half, ByteArray(32)),
            CryptographyPlcRecoveryKeySupport.subtract(CryptographyPlcRecoveryKeySupport.subtract(order, half), ONE),
        )
    }

    private fun assertSignsVerifiably(support: PlcRecoveryKeySupport) {
        val publicKey =
            CryptographyProvider.Default
                .get(ECDSA)
                .publicKeyDecoder(EC.Curve.P256)
                .decodeFromByteArrayBlocking(EC.PublicKey.Format.RAW, UNCOMPRESSED_PUBLIC_KEY)
        val verifier = publicKey.signatureVerifier(SHA256, ECDSA.SignatureFormat.RAW)

        // ECDSA may be randomised, and about half of all raw signatures have a high s, so several
        // signatures exercise the normalisation path.
        repeat(SIGNATURE_SAMPLES) { index ->
            val payload = "plc recovery payload $index".encodeToByteArray()
            val signature = decodeBase64Url(support.signPayload(PRIVATE_KEY, payload))

            assertEquals(64, signature.size)
            val s = signature.copyOfRange(32, 64)
            assertTrue(CryptographyPlcRecoveryKeySupport.compareUnsigned(s, CryptographyPlcRecoveryKeySupport.HALF_CURVE_ORDER) <= 0)
            assertTrue(verifier.tryVerifySignatureBlocking(payload, signature), "signature $index does not verify")
        }
    }

    private fun decodeBase64Url(value: String): ByteArray {
        val padding = (4 - value.length % 4) % 4
        return Base64.UrlSafe.decode(value + "=".repeat(padding))
    }

    private companion object {
        private const val SIGNATURE_SAMPLES = 16
        private const val EXPECTED_DID_KEY = "did:key:zDnaepBuvsQ8cpsWrVKw8fbpGpvPeNSjVPTWoq6cRqaYzBKVP"
        private val PRIVATE_KEY = hex("c9afa9d845ba75166b5c215767b1d6934e50c3db36e89b127b8a622b120f6721")
        private val UNCOMPRESSED_PUBLIC_KEY =
            hex(
                "0460fed4ba255a9d31c961eb74c6356d68c049b8923b61fa6ce669622e60f29fb6" +
                    "7903fe1008b8bc99a41ae9e95628bc64f2f1b20c2d7e9f5177a3c294d4462299",
            )
        private val ONE = ByteArray(32).also { it[31] = 1 }

        private fun hex(value: String): ByteArray = ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
