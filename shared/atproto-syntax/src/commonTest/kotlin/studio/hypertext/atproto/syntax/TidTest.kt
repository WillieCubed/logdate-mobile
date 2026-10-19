package studio.hypertext.atproto.syntax

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Validates the base32-sortable `Tid` (Timestamp Identifier) class.
 *
 * This test suite ensures that TIDs follow the AT Protocol format (13 base32
 * characters), correctly handles conversion to and from 64-bit integer values,
 * and maintains lexicographical ordering and JSON serialization.
 */
class TidTest {
    @Test
    fun `accepts valid tid`() {
        val tid = Tid.require("3jzfcijpj2z2a")

        assertEquals("3jzfcijpj2z2a", box(tid).value)
        assertEquals("3jzfcijpj2z2a", tid.value)
        assertEquals("3jzfcijpj2z2a", box(tid).toString())
    }

    @Test
    fun `serializes as json string`() {
        val json = Json.encodeToString(Tid.require("2222222222222"))

        assertEquals("\"2222222222222\"", json)
    }

    @Test
    fun `rejects legacy dash syntax`() {
        assertFailsWith<InvalidTidException> {
            Tid.require("3jzf-cij-pj2z-2a")
        }
    }

    @Test
    fun `parse returns failure for uppercase`() {
        assertTrue(Tid.parse("3JZFCIJPJ2Z2A").isFailure)
    }

    @Test
    fun `reports validity for valid tid`() {
        assertTrue(Tid.isValid("3jzfcijpj2z2a"))
    }

    @Test
    fun `encodes and decodes long values`() {
        val tid = Tid.fromLong(42L)

        assertEquals("222222222223e", tid.value)
        assertEquals(42L, tid.toLong())
    }

    @Test
    fun `builds a tid from a timestamp and clock identifier`() {
        val instant = Instant.parse("2026-10-18T12:00:00.123456Z")

        val tid = Tid.fromInstant(instant, clockId = 7)

        assertEquals(instant.epochSeconds * 1_000_000 + 123_456, tid.timestampMicros)
        assertEquals(7, tid.clockId)
        assertEquals(tid, Tid.require(tid.value))
    }

    @Test
    fun `later timestamps sort after earlier ones as strings and numbers`() {
        val earlier = Tid.fromInstant(Instant.parse("2023-01-01T00:00:00Z"), clockId = 1023)
        val later = Tid.fromInstant(Instant.parse("2023-01-01T00:00:00.000001Z"), clockId = 0)

        assertTrue(earlier.value < later.value)
        assertTrue(earlier.toLong() < later.toLong())
    }

    @Test
    fun `rejects clock identifiers outside ten bits and times before the epoch`() {
        assertFailsWith<IllegalArgumentException> { Tid.fromInstant(Instant.parse("2026-01-01T00:00:00Z"), clockId = 1024) }
        assertFailsWith<IllegalArgumentException> { Tid.fromInstant(Instant.parse("2026-01-01T00:00:00Z"), clockId = -1) }
        assertFailsWith<IllegalArgumentException> { Tid.fromInstant(Instant.parse("1969-12-31T23:59:59Z")) }
    }
}
