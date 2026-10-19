package app.logdate.space

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SealedPaddingTest {
    @Test
    fun `sizes round up to powers of two then to 64 KiB steps`() {
        assertEquals(512, SealedPadding.paddedSize(0))
        assertEquals(512, SealedPadding.paddedSize(508))
        assertEquals(1024, SealedPadding.paddedSize(509))
        assertEquals(65_536, SealedPadding.paddedSize(65_532))
        assertEquals(131_072, SealedPadding.paddedSize(65_533))
        assertEquals(196_608, SealedPadding.paddedSize(131_069))
    }

    @Test
    fun `padding matches the shared conformance sizes`() {
        val padding = ConformanceFixtures.padding
        padding.list("sizes").forEach { entry ->
            val length = entry.int("payloadLength")
            val padded = SealedPadding.pad(ByteArray(length) { 1 })
            assertEquals(entry.int("paddedLength"), SealedPadding.paddedSize(length), "payload of $length bytes")
            assertEquals(entry.int("paddedLength"), padded.size, "padded payload of $length bytes")
            assertEquals(
                length,
                (0 until 4).fold(0) { sum, index -> (sum shl 8) or (padded[index].toInt() and 0xff) },
                "length header of $length bytes",
            )
            assertContentEquals(ByteArray(length) { 1 }, SealedPadding.unpad(padded))
        }
        assertEquals(padding.int("largestSealablePayload"), padding.list("sizes").maxOf { it.int("payloadLength") })
        assertEquals(padding.int("largestSealablePayload") + 1, padding.int("smallestRefusedPayload"))
        assertFailsWith<SealedRecordException> { SealedPadding.pad(ByteArray(padding.int("smallestRefusedPayload"))) }
    }

    @Test
    fun `padding round-trips and hides the length`() {
        val short = SealedPadding.pad(ByteArray(10) { 1 })
        val longer = SealedPadding.pad(ByteArray(400) { 2 })
        assertEquals(short.size, longer.size)
        assertContentEquals(ByteArray(10) { 1 }, SealedPadding.unpad(short))
    }

    @Test
    fun `only padded sizes plus the tag are accepted as ciphertext sizes`() {
        assertTrue(SealedPadding.isPaddedSize(512))
        assertTrue(SealedPadding.isPaddedSize(196_608))
        assertTrue(!SealedPadding.isPaddedSize(600))
    }

    @Test
    fun `a payload too large for the schema is refused`() {
        assertFailsWith<SealedRecordException> { SealedPadding.pad(ByteArray(4_000_000)) }
    }

    @Test
    fun `the largest bucket the schema allows still seals`() {
        val largest = 61 * 65_536
        assertEquals(largest, SealedPadding.pad(ByteArray(largest - 4)).size)
        assertTrue(!SealedPadding.isPaddedSize(62 * 65_536))
        assertFailsWith<SealedRecordException> { SealedPadding.pad(ByteArray(largest - 3)) }
    }

    @Test
    fun `padding that pad couldn't have produced is refused`() {
        val padded = SealedPadding.pad(ByteArray(10) { 1 })
        assertFailsWith<SealedRecordException> { SealedPadding.unpad(padded.copyOf(600)) }
        assertFailsWith<SealedRecordException> { SealedPadding.unpad(ByteArray(3)) }
        assertFailsWith<SealedRecordException> {
            SealedPadding.unpad(padded.copyOf().also { it[1] = 1 })
        }
        assertFailsWith<SealedRecordException> {
            SealedPadding.unpad(padded.copyOf().also { it[0] = 0x80.toByte() })
        }
        assertFailsWith<SealedRecordException> {
            SealedPadding.unpad(padded.copyOf().also { it[padded.size - 1] = 1 })
        }
    }
}
