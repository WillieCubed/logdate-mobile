package studio.hypertext.atproto.syntax

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HandleTest {
    @Test
    fun normalizesHandlesToLowercase() {
        val handle = Handle.require("XX.LCS.MIT.EDU")

        assertEquals("xx.lcs.mit.edu", box(handle).value)
        assertEquals("xx.lcs.mit.edu", handle.normalized)
        assertEquals("xx.lcs.mit.edu", handle.toString())
    }

    @Test
    fun allowsReservedTldsAtSyntaxLevel() {
        val handle = Handle.require("xn--ls8h.test")

        assertEquals("xn--ls8h.test", handle.value)
    }

    @Test
    fun serializesAsJsonString() {
        val json = Json.encodeToString(Handle.require("jay.bsky.social"))

        assertEquals("\"jay.bsky.social\"", json)
    }

    @Test
    fun rejectsInvalidSyntax() {
        assertFailsWith<InvalidHandleException> {
            Handle.require("org")
        }
    }

    @Test
    fun parseReturnsFailureForInvalidHandle() {
        assertTrue(Handle.parse("john..test").isFailure)
    }

    @Test
    fun reportsValidityAndRejectsOverlongHandles() {
        assertTrue(Handle.isValid("example.com"))

        val overlong = "${"a".repeat(64)}.${"b".repeat(64)}.${"c".repeat(64)}.${"d".repeat(64)}"
        assertFailsWith<InvalidHandleException> {
            Handle.require(overlong)
        }
    }
}
