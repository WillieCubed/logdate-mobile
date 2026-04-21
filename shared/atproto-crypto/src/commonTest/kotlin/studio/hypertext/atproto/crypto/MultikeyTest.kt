package studio.hypertext.atproto.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests the [Multikey] utility for encoding and decoding AT Protocol multikey strings.
 *
 * These tests verify the correct application of multicodec prefixes and base58btc multibase
 * encoding for various elliptic curve public keys, including P-256 and K-256 (secp256k1).
 * It also ensures robust error handling for invalid key lengths and malformed input strings.
 */
class MultikeyTest {
    @Test
    fun `encodeP256PublicKey produces base58btc multibase value`() {
        val compressedKey =
            byteArrayOf(
                0x02,
                0x01,
                0x02,
                0x03,
                0x04,
                0x05,
                0x06,
                0x07,
                0x08,
                0x09,
                0x0a,
                0x0b,
                0x0c,
                0x0d,
                0x0e,
                0x0f,
                0x10,
                0x11,
                0x12,
                0x13,
                0x14,
                0x15,
                0x16,
                0x17,
                0x18,
                0x19,
                0x1a,
                0x1b,
                0x1c,
                0x1d,
                0x1e,
                0x1f,
                0x20,
            )

        val encoded = Multikey.encodeP256PublicKey(compressedKey)

        assertTrue(encoded.startsWith("z"))
        assertContentEquals(Multikey.p256PublicKeyPrefix + compressedKey, Multikey.decode(encoded))
    }

    @Test
    fun `decode rejects non base58btc multibase strings`() {
        assertFailsWith<IllegalArgumentException> {
            Multikey.decode("fabc")
        }
    }

    @Test
    fun `encodeK256PublicKey produces base58btc multibase value`() {
        val compressedKey =
            byteArrayOf(
                0x03,
                0x21,
                0x22,
                0x23,
                0x24,
                0x25,
                0x26,
                0x27,
                0x28,
                0x29,
                0x2a,
                0x2b,
                0x2c,
                0x2d,
                0x2e,
                0x2f,
                0x30,
                0x31,
                0x32,
                0x33,
                0x34,
                0x35,
                0x36,
                0x37,
                0x38,
                0x39,
                0x3a,
                0x3b,
                0x3c,
                0x3d,
                0x3e,
                0x3f,
                0x40,
            )

        val encoded = Multikey.encodeK256PublicKey(compressedKey)

        assertTrue(encoded.startsWith("z"))
        assertContentEquals(Multikey.k256PublicKeyPrefix + compressedKey, Multikey.decode(encoded))
    }

    @Test
    fun `encodeP256PublicKey rejects invalid key length`() {
        assertFailsWith<IllegalArgumentException> {
            Multikey.encodeP256PublicKey(byteArrayOf(0x02, 0x03))
        }
    }

    @Test
    fun `encodeK256PublicKey rejects invalid key length`() {
        assertFailsWith<IllegalArgumentException> {
            Multikey.encodeK256PublicKey(byteArrayOf(0x02, 0x03))
        }
    }

    @Test
    fun `encodeP256PublicKey rejects invalid compression prefix`() {
        val invalidCompressedKey = ByteArray(33) { 0x01 }

        assertFailsWith<IllegalArgumentException> {
            Multikey.encodeP256PublicKey(invalidCompressedKey)
        }
    }

    @Test
    fun `curve returns recognized EC key curves`() {
        val p256 =
            Multikey.encodeP256PublicKey(
                byteArrayOf(
                    0x02,
                    0x01,
                    0x02,
                    0x03,
                    0x04,
                    0x05,
                    0x06,
                    0x07,
                    0x08,
                    0x09,
                    0x0a,
                    0x0b,
                    0x0c,
                    0x0d,
                    0x0e,
                    0x0f,
                    0x10,
                    0x11,
                    0x12,
                    0x13,
                    0x14,
                    0x15,
                    0x16,
                    0x17,
                    0x18,
                    0x19,
                    0x1a,
                    0x1b,
                    0x1c,
                    0x1d,
                    0x1e,
                    0x1f,
                    0x20,
                ),
            )
        val k256 =
            Multikey.encodeK256PublicKey(
                byteArrayOf(
                    0x03,
                    0x21,
                    0x22,
                    0x23,
                    0x24,
                    0x25,
                    0x26,
                    0x27,
                    0x28,
                    0x29,
                    0x2a,
                    0x2b,
                    0x2c,
                    0x2d,
                    0x2e,
                    0x2f,
                    0x30,
                    0x31,
                    0x32,
                    0x33,
                    0x34,
                    0x35,
                    0x36,
                    0x37,
                    0x38,
                    0x39,
                    0x3a,
                    0x3b,
                    0x3c,
                    0x3d,
                    0x3e,
                    0x3f,
                    0x40,
                ),
            )
        val ed25519 = Multikey.encode(Multikey.ed25519PublicKeyPrefix, byteArrayOf(1, 2, 3))

        assertTrue(Multikey.curve(p256) == EcCurve.P256)
        assertTrue(Multikey.curve(k256) == EcCurve.K256)
        assertTrue(Multikey.curve(ed25519) == null)
    }

    @Test
    fun `encode combines multicodec prefix and key bytes`() {
        val encoded = Multikey.encode(multicodecPrefix = Multikey.ed25519PublicKeyPrefix, keyBytes = byteArrayOf(1, 2, 3))

        assertTrue(encoded.startsWith("z"))
        assertContentEquals(Multikey.ed25519PublicKeyPrefix + byteArrayOf(1, 2, 3), Multikey.decode(encoded))
    }
}
