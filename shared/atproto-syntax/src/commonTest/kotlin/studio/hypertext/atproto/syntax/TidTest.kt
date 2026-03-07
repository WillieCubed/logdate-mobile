package studio.hypertext.atproto.syntax

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TidTest {
    @Test
    fun acceptsValidTid() {
        val tid = Tid.require("3jzfcijpj2z2a")

        assertEquals("3jzfcijpj2z2a", box(tid).value)
        assertEquals("3jzfcijpj2z2a", tid.value)
        assertEquals("3jzfcijpj2z2a", box(tid).toString())
    }

    @Test
    fun serializesAsJsonString() {
        val json = Json.encodeToString(Tid.require("2222222222222"))

        assertEquals("\"2222222222222\"", json)
    }

    @Test
    fun rejectsLegacyDashSyntax() {
        assertFailsWith<InvalidTidException> {
            Tid.require("3jzf-cij-pj2z-2a")
        }
    }

    @Test
    fun parseReturnsFailureForUppercase() {
        assertTrue(Tid.parse("3JZFCIJPJ2Z2A").isFailure)
    }

    @Test
    fun reportsValidityForValidTid() {
        assertTrue(Tid.isValid("3jzfcijpj2z2a"))
    }
}
