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
    fun `parses method and identifier`() {
        val did = Did.require("did:web:logdate.app")

        assertEquals("did:web:logdate.app", box(did).value)
        assertEquals("web", did.method)
        assertEquals("logdate.app", did.identifier)
        assertEquals("did:web:logdate.app", box(did).toString())
    }

    @Test
    fun `supports unknown methods`() {
        val did = Did.require("did:example:custom-id")

        assertEquals("example", did.method)
        assertEquals("custom-id", did.identifier)
    }

    @Test
    fun `serializes as json string`() {
        val json = Json.encodeToString(Did.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz"))

        assertEquals("\"did:plc:ewvi7nxzyoun6zhxrhs64oiz\"", json)
    }

    @Test
    fun `rejects malformed did`() {
        assertFailsWith<InvalidDidException> {
            Did.require("invalid")
        }
    }

    @Test
    fun `parse returns failure for invalid did`() {
        assertTrue(Did.parse("did::missing").isFailure)
        assertTrue(Did.parse("did:plc").isFailure)
    }

    @Test
    fun `reports validity for successful and failed parses`() {
        assertTrue(Did.isValid("did:plc:ewvi7nxzyoun6zhxrhs64oiz"))
        assertTrue(Did.parse("did:web:example.com").isSuccess)
        assertTrue(Did.parse("did:UPPER:example").isFailure)
    }

    @Test
    fun `rejects invalid method and identifier segments`() {
        assertFailsWith<InvalidDidException> {
            Did.require("did:Upper:example")
        }
        assertFailsWith<InvalidDidException> {
            Did.require("did:web:example.com::alice")
        }
    }
}
