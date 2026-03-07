package studio.hypertext.atproto.syntax

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
