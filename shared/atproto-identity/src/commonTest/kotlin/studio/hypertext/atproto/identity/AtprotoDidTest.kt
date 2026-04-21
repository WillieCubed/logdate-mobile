package studio.hypertext.atproto.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Validates the parsing, validation, and document URL generation for AT Protocol-supported
 * DID methods (`did:plc` and `did:web`).
 *
 * This test suite enforces method-specific restrictions, such as prohibiting paths
 * in `did:web` and requiring specific identifier lengths for `did:plc`, and
 * ensures correct mapping to remote document endpoints.
 */
class AtprotoDidTest {
    @Test
    fun acceptsDidPlc() {
        val did = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")

        assertEquals("did:plc:ewvi7nxzyoun6zhxrhs64oiz", box(did).value.value)
        assertEquals("plc", did.method)
        assertEquals("did:plc:ewvi7nxzyoun6zhxrhs64oiz", box(did).toString())
    }

    @Test
    fun acceptsHostnameLevelDidWeb() {
        val did = AtprotoDid.require("did:web:example.com%3A8443")

        assertEquals("web", did.method)
    }

    @Test
    fun rejectsPathBasedDidWeb() {
        assertFailsWith<InvalidAtprotoDidException> {
            AtprotoDid.require("did:web:example.com:users:alice")
        }
    }

    @Test
    fun rejectsUnsupportedDidMethod() {
        assertFailsWith<InvalidAtprotoDidException> {
            AtprotoDid.require("did:key:z6Mk")
        }
    }

    @Test
    fun parseAndHelperUrlsCoverSupportedAndUnsupportedCases() {
        val plcDid = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
        val webDid = AtprotoDid.require("did:web:example.com%3A8443")

        assertEquals("https://plc.directory/did:plc:ewvi7nxzyoun6zhxrhs64oiz", plcDid.didPlcDocumentUrl())
        assertEquals("https://example.com:8443/.well-known/did.json", webDid.didWebDocumentUrl())
        assertEquals(webDid, AtprotoDid.parse("did:web:example.com%3A8443").getOrThrow())
        assertTrue(AtprotoDid.parse("did:web:example.com%3Aabc").isFailure)

        assertFailsWith<InvalidAtprotoDidException> {
            AtprotoDid.require("did:plc:short")
        }
        assertFailsWith<InvalidAtprotoDidException> {
            AtprotoDid.require("did:web:exa_mple.com")
        }
        assertFailsWith<InvalidAtprotoDidException> {
            AtprotoDid.require("did:web:example.com%3Aabc")
        }
        assertFailsWith<InvalidAtprotoDidException> {
            plcDid.didWebDocumentUrl()
        }
        assertFailsWith<InvalidAtprotoDidException> {
            webDid.didPlcDocumentUrl()
        }
    }
}
