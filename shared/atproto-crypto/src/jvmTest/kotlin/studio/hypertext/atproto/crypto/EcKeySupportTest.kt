package studio.hypertext.atproto.crypto

import java.security.interfaces.ECPublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validates the elliptic curve cryptography (ECC) utility functions used for AT
 * Protocol identities.
 *
 * This test suite covers key pair generation, public key encoding into
 * multibase formats, and signature generation and verification for supported
 * curves (K-256 and P-256).
 */
class EcKeySupportTest {
    @Test
    fun `generate key pair and multikey round trip for supported curves`() {
        val payload = "hello, atproto".encodeToByteArray()

        EcCurve.entries.forEach { curve ->
            val keyPair = EcKeySupport.generateKeyPair(curve)
            val publicKey = keyPair.public as ECPublicKey
            val multikey = EcKeySupport.encodePublicKeyMultibase(publicKey, curve)
            val decoded = EcKeySupport.decodePublicKey(multikey)
            val signature = EcKeySupport.signSha256(keyPair.private, curve, payload)

            assertEquals(curve, decoded.curve)
            assertEquals(multikey, EcKeySupport.encodePublicKeyMultibase(decoded.publicKey, curve))
            assertTrue(EcKeySupport.verifySha256(decoded.publicKey, curve, payload, signature))
            assertTrue(!EcKeySupport.verifySha256(decoded.publicKey, curve, "tampered".encodeToByteArray(), signature))
        }
    }

    @Test
    fun `decode public key from jwk coordinates verifies signatures`() {
        val curve = EcCurve.K256
        val keyPair = EcKeySupport.generateKeyPair(curve)
        val publicKey = keyPair.public as ECPublicKey
        val payload = "jwk".encodeToByteArray()
        val signature = EcKeySupport.signSha256(keyPair.private, curve, payload)
        val decoded =
            EcKeySupport.decodePublicKeyFromJwk(
                curve = curve,
                x = publicKey.w.affineX.toFixedWidth(curve.coordinateBytes),
                y = publicKey.w.affineY.toFixedWidth(curve.coordinateBytes),
            )

        assertTrue(EcKeySupport.verifySha256(decoded, curve, payload, signature))
    }
}

private fun java.math.BigInteger.toFixedWidth(width: Int): ByteArray {
    val encoded = toByteArray()
    return when {
        encoded.size == width -> encoded
        encoded.size < width -> ByteArray(width - encoded.size) + encoded
        else -> encoded.copyOfRange(encoded.size - width, encoded.size)
    }
}
