package studio.hypertext.atproto.syntax

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Validates the core syntactical structure and parsing of decentralized identifiers (DIDs).
 *
 * This test suite covers method/identifier splitting, supporting arbitrary DID
 * methods, and enforcing strict requirements for lowercase method names and
 * valid identifier characters.
 */
class DidTest {
    @Test
    fun parsesMethodAndIdentifier() {
        val did = Did.require("did:web:logdate.app")

        assertEquals("did:web:logdate.app", box(did).value)
        assertEquals("web", did.method)
        assertEquals("logdate.app", did.identifier)
        assertEquals("did:web:logdate.app", box(did).toString())
    }

    @Test
    fun supportsUnknownMethods() {
        val did = Did.require("did:example:custom-id")

        assertEquals("example", did.method)
        assertEquals("custom-id", did.identifier)
    }

    @Test
    fun serializesAsJsonString() {
        val json = Json.encodeToString(Did.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz"))

        assertEquals("\"did:plc:ewvi7nxzyoun6zhxrhs64oiz\"", json)
    }

    @Test
    fun rejectsMalformedDid() {
        assertFailsWith<InvalidDidException> {
            Did.require("invalid")
        }
    }

    @Test
    fun parseReturnsFailureForInvalidDid() {
        assertTrue(Did.parse("did::missing").isFailure)
        assertTrue(Did.parse("did:plc").isFailure)
    }

    @Test
    fun reportsValidityForSuccessfulAndFailedParses() {
        assertTrue(Did.isValid("did:plc:ewvi7nxzyoun6zhxrhs64oiz"))
        assertTrue(Did.parse("did:web:example.com").isSuccess)
        assertTrue(Did.parse("did:UPPER:example").isFailure)
    }

    @Test
    fun rejectsInvalidMethodAndIdentifierSegments() {
        assertFailsWith<InvalidDidException> {
            Did.require("did:Upper:example")
        }
        assertFailsWith<InvalidDidException> {
            Did.require("did:web:example.com::alice")
        }
    }
}
