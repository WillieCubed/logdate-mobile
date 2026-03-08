package studio.hypertext.atproto.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Base58BtcTest {
    @Test
    fun `encode handles empty bytes`() {
        assertEquals("", Base58Btc.encode(ByteArray(0)))
    }

    @Test
    fun `encode single zero byte as one leading marker`() {
        assertEquals("1", Base58Btc.encode(byteArrayOf(0)))
    }

    @Test
    fun `decode handles empty string`() {
        assertContentEquals(ByteArray(0), Base58Btc.decode(""))
    }

    @Test
    fun `decode single leading marker into zero byte`() {
        assertContentEquals(byteArrayOf(0), Base58Btc.decode("1"))
    }

    @Test
    fun `encode preserves leading zeros`() {
        assertEquals("112", Base58Btc.encode(byteArrayOf(0, 0, 1)))
    }

    @Test
    fun `decode preserves leading zeros`() {
        assertContentEquals(byteArrayOf(0, 0, 1), Base58Btc.decode("112"))
    }

    @Test
    fun `decode rejects invalid characters`() {
        assertFailsWith<IllegalArgumentException> {
            Base58Btc.decode("0OIl")
        }
    }

    @Test
    fun `round trips arbitrary payloads`() {
        val payload =
            byteArrayOf(
                0,
                0,
                1,
                2,
                3,
                4,
                5,
                6,
                7,
                8,
                9,
                10,
                11,
                12,
                13,
                14,
                15,
            )

        val encoded = Base58Btc.encode(payload)

        assertContentEquals(payload, Base58Btc.decode(encoded))
    }
}
