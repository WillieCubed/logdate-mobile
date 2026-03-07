package app.logdate.atproto.syntax

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AtUriTest {
    @Test
    fun normalizesHandleAuthorityAndCollection() {
        val uri = AtUri.require("at://BNEWBOLD.BSKY.TEAM/App.Bsky.feed.post/3jwdwj2ctlk26")

        assertEquals("at://bnewbold.bsky.team/app.bsky.feed.post/3jwdwj2ctlk26", box(uri).value)
        assertEquals("at://bnewbold.bsky.team/app.bsky.feed.post/3jwdwj2ctlk26", uri.value)
        assertEquals("bnewbold.bsky.team", uri.authority)
        assertEquals("app.bsky.feed.post", uri.collection?.value)
        assertEquals("3jwdwj2ctlk26", uri.recordKey?.value)
        assertEquals("at://bnewbold.bsky.team/app.bsky.feed.post/3jwdwj2ctlk26", box(uri).toString())
    }

    @Test
    fun supportsRepositoryOnlyUris() {
        val uri = AtUri.require("at://did:plc:44ybard66vv44zksje25o7dz")

        assertEquals("did:plc:44ybard66vv44zksje25o7dz", uri.authority)
        assertNull(uri.collection)
        assertNull(uri.recordKey)
    }

    @Test
    fun serializesAsJsonString() {
        val json = Json.encodeToString(AtUri.require("at://foo.com/com.example.foo/123"))

        assertEquals("\"at://foo.com/com.example.foo/123\"", json)
    }

    @Test
    fun rejectsTrailingSlash() {
        assertFailsWith<InvalidAtUriException> {
            AtUri.require("at://foo.com/")
        }
    }

    @Test
    fun parseReturnsFailureForInvalidCollectionShape() {
        assertTrue(AtUri.parse("at://foo.com/example/123").isFailure)
    }

    @Test
    fun reportsValidityAndRejectsAdditionalInvalidShapes() {
        assertTrue(AtUri.isValid("at://foo.com/com.example.foo/123"))

        assertFailsWith<InvalidAtUriException> {
            AtUri.require("at://foo.com/com.example.foo/123?bad=true")
        }
        assertFailsWith<InvalidAtUriException> {
            AtUri.require("at://foo.com/com.example.foo/123#fragment")
        }
        assertFailsWith<InvalidAtUriException> {
            AtUri.require("at://foo.com/com.example.foo/123/extra")
        }
    }
}
