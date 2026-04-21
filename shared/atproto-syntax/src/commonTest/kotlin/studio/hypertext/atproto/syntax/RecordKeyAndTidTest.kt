package studio.hypertext.atproto.syntax

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Validates the core syntactical rules for AT Protocol `RecordKey` and `Tid` values.
 *
 * This test suite provides high-level verification for both identifier types,
 * focusing on basic acceptance of valid formats and rejection of invalid
 * structures like slashes in record keys.
 */
class RecordKeyAndTidTest {
    @Test
    fun acceptsValidRecordKeys() {
        val recordKey = RecordKey.require("self")

        assertEquals("self", recordKey.value)
    }

    @Test
    fun rejectsRecordKeysWithSlashes() {
        assertFailsWith<InvalidRecordKeyException> {
            RecordKey.require("a/b")
        }
    }

    @Test
    fun acceptsValidTid() {
        val tid = Tid.require("3jqfcqzm3fo2j")

        assertEquals("3jqfcqzm3fo2j", tid.value)
    }

    @Test
    fun rejectsInvalidTid() {
        assertFailsWith<InvalidTidException> {
            Tid.require("not-a-tid")
        }
    }
}
