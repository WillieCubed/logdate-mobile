package studio.hypertext.atproto.syntax

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Validates the AT Protocol `Nsid` (Namespaced Identifier) format.
 *
 * This test suite enforces reverse-domain name structure, normalization rules
 * (such as lowercase-only authorities), and character restrictions on authority
 * and method names, ensuring interoperability across lexicographic contexts.
 */
class NsidTest {
    @Test
    fun normalizesAuthorityToLowercase() {
        val nsid = Nsid.require("Com.Example.fooBar")

        assertEquals("com.example.fooBar", box(nsid).value)
        assertEquals("com.example.fooBar", nsid.value)
        assertEquals("com.example", nsid.authority)
        assertEquals("fooBar", nsid.name)
        assertEquals("com.example.fooBar", box(nsid).toString())
    }

    @Test
    fun serializesAsJsonString() {
        val json = Json.encodeToString(Nsid.require("com.atproto.sync.getRecord"))

        assertEquals("\"com.atproto.sync.getRecord\"", json)
    }

    @Test
    fun rejectsHyphenatedNames() {
        assertFailsWith<InvalidNsidException> {
            Nsid.require("com.example.foo-bar")
        }
    }

    @Test
    fun parseReturnsFailureForTooFewSegments() {
        assertTrue(Nsid.parse("example.foo").isFailure)
    }

    @Test
    fun reportsValidityAndRejectsAdditionalInvalidShapes() {
        assertTrue(Nsid.isValid("com.example.fooBar"))

        val overlongAuthority = "${"a".repeat(64)}.${"b".repeat(64)}.${"c".repeat(64)}.${"d".repeat(61)}.foo"
        assertFailsWith<InvalidNsidException> {
            Nsid.require("com.exámple.foo")
        }
        assertFailsWith<InvalidNsidException> {
            Nsid.require("9com.example.foo")
        }
        assertFailsWith<InvalidNsidException> {
            Nsid.require("com.${"a".repeat(64)}.foo")
        }
        assertFailsWith<InvalidNsidException> {
            Nsid.require(overlongAuthority)
        }
        assertFailsWith<InvalidNsidException> {
            Nsid.require("-com.example.foo")
        }
        assertFailsWith<InvalidNsidException> {
            Nsid.require("co_m.example.foo")
        }
        assertFailsWith<InvalidNsidException> {
            Nsid.require("com.example.9foo")
        }
    }
}
