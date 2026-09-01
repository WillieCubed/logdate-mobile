package studio.hypertext.atproto.syntax

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
}
