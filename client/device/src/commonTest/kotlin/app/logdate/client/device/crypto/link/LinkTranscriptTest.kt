package app.logdate.client.device.crypto.link

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Tests the byte layout every link key is bound to.
 */
class LinkTranscriptTest {
    @Test
    fun `the transcript starts with its label and prefixes each field with its length`() {
        val encoded = transcript().encode()

        assertEquals(151, encoded.size)
        assertEquals("LDLINK1", encoded.copyOfRange(0, 7).decodeToString())
        assertContentEquals(byteArrayOf(0, 0, 0, 10), encoded.copyOfRange(7, 11))
        assertContentEquals(byteArrayOf(0, 0, 0, 1), encoded.copyOfRange(147, 151))
    }

    @Test
    fun `moving a byte between node names changes the transcript`() {
        val first = transcript(phoneNode = "ab", watchNode = "c").encode()
        val second = transcript(phoneNode = "a", watchNode = "bc").encode()

        assertFalse(first.contentEquals(second))
    }

    private fun transcript(
        phoneNode: String = "phone-node",
        watchNode: String = "watch-node",
    ) = LinkTranscript(
        phoneNode = phoneNode,
        watchNode = watchNode,
        phonePublicKey = ByteArray(32) { 1 },
        watchPublicKey = ByteArray(32) { 2 },
        phoneNonce = ByteArray(16) { 1 },
        watchNonce = ByteArray(16) { 2 },
        schema = 1,
    )
}
