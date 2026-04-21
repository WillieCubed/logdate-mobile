package studio.hypertext.atproto.syntax

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Validates the strict character and length constraints for AT Protocol `RecordKey`
 * identifiers.
 *
 * This test suite covers parsing allowed character sets (alphanumeric, hyphens,
 * underscores, etc.), rejecting prohibited structures like dot-segments, and
 * ensuring correct JSON serialization.
 */
class RecordKeyTest {
    @Test
    fun acceptsSpecExamples() {
        val recordKey = RecordKey.require("self")

        assertEquals("self", box(recordKey).value)
        assertEquals("self", box(recordKey).toString())
        assertEquals("pre:fix", RecordKey.require("pre:fix").value)
    }

    @Test
    fun serializesAsJsonString() {
        val json = Json.encodeToString(RecordKey.require("~1.2-3_"))

        assertEquals("\"~1.2-3_\"", json)
    }

    @Test
    fun rejectsDotSegments() {
        assertFailsWith<InvalidRecordKeyException> {
            RecordKey.require("..")
        }
    }

    @Test
    fun parseReturnsFailureForInvalidCharacter() {
        assertTrue(RecordKey.parse("alpha/beta").isFailure)
    }

    @Test
    fun reportsValidityForAllowedCharacters() {
        assertTrue(RecordKey.isValid("~1.2-3_"))
    }
}
