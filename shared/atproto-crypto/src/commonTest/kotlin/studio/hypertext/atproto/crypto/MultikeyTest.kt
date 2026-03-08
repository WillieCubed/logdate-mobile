package studio.hypertext.atproto.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
    fun `encodeP256PublicKey rejects invalid key length`() {
        assertFailsWith<IllegalArgumentException> {
            Multikey.encodeP256PublicKey(byteArrayOf(0x02, 0x03))
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
    fun `encode combines multicodec prefix and key bytes`() {
        val encoded = Multikey.encode(multicodecPrefix = Multikey.ed25519PublicKeyPrefix, keyBytes = byteArrayOf(1, 2, 3))

        assertTrue(encoded.startsWith("z"))
        assertContentEquals(Multikey.ed25519PublicKeyPrefix + byteArrayOf(1, 2, 3), Multikey.decode(encoded))
    }
}
