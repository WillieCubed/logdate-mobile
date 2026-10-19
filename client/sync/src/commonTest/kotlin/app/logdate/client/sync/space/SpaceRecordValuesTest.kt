package app.logdate.client.sync.space

import app.logdate.lexicon.app.logdate.journal.Journal
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class SpaceRecordValuesTest {
    @Test
    fun `halves round away from zero in both directions`() {
        assertEquals(3, roundHalfAwayFromZero("x", 2.5))
        assertEquals(-3, roundHalfAwayFromZero("x", -2.5))
        assertEquals(2, roundHalfAwayFromZero("x", 2.4999))
        assertEquals(-2, roundHalfAwayFromZero("x", -2.4999))
        assertEquals(1, roundHalfAwayFromZero("x", 0.5))
        assertEquals(-1, roundHalfAwayFromZero("x", -0.5))
        assertEquals(0, roundHalfAwayFromZero("x", -0.4))
    }

    @Test
    fun `NaN and infinity are refused instead of written as a number`() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val failure = assertFailsWith<SpaceRecordMappingException> { roundHalfAwayFromZero("app.logdate.place.sample/1 speed", value) }
            assertFalse(failure.retryable)
            assertTrue(failure.message!!.startsWith("app.logdate.place.sample/1 speed"))
        }
        assertFailsWith<SpaceRecordMappingException> { Float.NaN.toThousandths("c") }
        assertFailsWith<SpaceRecordMappingException> { Float.POSITIVE_INFINITY.toCentimeters("a") }
        assertFailsWith<SpaceRecordMappingException> { Double.NaN.toBearingHundredths("b") }
        assertFailsWith<SpaceRecordMappingException> { Double.NEGATIVE_INFINITY.toDecimalString("d") }
    }

    @Test
    fun `fractions become whole units with known answers`() {
        assertEquals(1000, 1.0f.toThousandths("c"))
        assertEquals(0, (-0.2f).toThousandths("c"))
        assertEquals(1000, 1.7f.toThousandths("c"))
        assertEquals(63, 0.0625f.toThousandths("c"))
        assertEquals(1250, 12.5f.toCentimeters("a"))
        assertEquals(0, (-3f).toCentimeters("a"))
        assertEquals(13, 0.125f.toCentimeters("a"))
        assertEquals(0.25f, 250L.fromThousandths())
        assertEquals(12.5f, 1250L.centimetersToMeters())
    }

    @Test
    fun `bearings are taken modulo a full turn into whole hundredths of a degree`() {
        assertEquals(0, 0.0.toBearingHundredths("b"))
        assertEquals(27000, (-90.0).toBearingHundredths("b"))
        assertEquals(0, 360.0.toBearingHundredths("b"))
        assertEquals(4500, 405.0.toBearingHundredths("b"))
        assertEquals(35999, 359.994.toBearingHundredths("b"))
        assertEquals(0, 359.996.toBearingHundredths("b"))
        assertEquals(35999, (-0.006).toBearingHundredths("b"))
        assertEquals(0, (-0.004).toBearingHundredths("b"))
        assertEquals(270.0, 27000L.hundredthsToBearing())
    }

    @Test
    fun `decimals are written plainly and read back exactly`() {
        assertEquals("0.00001", 1.0e-5.toDecimalString("d"))
        assertEquals("-123400000000000000000000.0", (-1.234e23).toDecimalString("d"))
        assertEquals("37.7749", 37.7749.toDecimalString("d"))
        for (value in listOf(1.0e-5, -1.234e23, 37.7749, 0.1 + 0.2, Double.MIN_VALUE, -0.0)) {
            assertEquals(value, parseDecimal("d", value.toDecimalString("d")))
        }
        assertFailsWith<SpaceRecordMappingException> { parseDecimal("d", "NaN") }
        assertFailsWith<SpaceRecordMappingException> { parseDecimal("d", "1e400") }
        assertFailsWith<SpaceRecordMappingException> { parseDecimal("d", "0x10") }
    }

    @Test
    fun `record keys match regardless of case and are written lowercase`() {
        val id = Uuid.parse("6f1c2c9e-4b1a-4c55-9a39-2f0d6c3b8e71")

        assertEquals(id, parseUuidKey("app.logdate.journal.entry", "6F1C2C9E-4B1A-4C55-9A39-2F0D6C3B8E71"))
        assertTrue(sameRecordKey("6F1C2C9E-4B1A-4C55-9A39-2F0D6C3B8E71", id.toString()))
        assertEquals(id.toString(), canonicalRecordKey("6F1C2C9E-4B1A-4C55-9A39-2F0D6C3B8E71"))
        assertEquals("6f1c2c9e-4b1a-4c55-9a39-2f0d6c3b8e71", recordRef("app.logdate.journal.entry", id).rkey)
        assertFailsWith<SpaceRecordMappingException> { parseUuidKey("app.logdate.journal.entry", "self") }
    }

    @Test
    fun `datetimes keep their precision and refuse what the format can't hold`() {
        val instant = Instant.parse("2026-10-18T10:00:00.123456Z")

        assertEquals(instant, parseDatetime("t", instant.toDatetime("t")))
        assertFailsWith<SpaceRecordMappingException> { parseDatetime("t", "yesterday") }
        assertFailsWith<SpaceRecordMappingException> { parseDatetime("t", "2026-10-18") }
    }

    @Test
    fun `records decode only as their own type and name the record in failures`() {
        val record =
            buildJsonObject {
                put("\$type", Journal.TYPE)
                put("title", "Lake trips")
                put("createdAt", "2026-10-01T09:00:00Z")
            }

        assertEquals("Lake trips", decodeRecord(Journal.serializer(), Journal.TYPE, "j1", record).title)
        assertEquals(
            JsonPrimitive("Lake trips"),
            encodeRecord(Journal.serializer(), decodeRecord(Journal.serializer(), Journal.TYPE, "j1", record))["title"],
        )
        val wrongType =
            assertFailsWith<SpaceRecordMappingException> { decodeRecord(Journal.serializer(), "app.logdate.journal.entry", "j1", record) }
        assertTrue(wrongType.message!!.startsWith("app.logdate.journal.entry/j1"))
        val missing = buildJsonObject { put("\$type", Journal.TYPE) }
        assertFailsWith<SpaceRecordMappingException> { decodeRecord(Journal.serializer(), Journal.TYPE, "j1", missing) }
    }
}
