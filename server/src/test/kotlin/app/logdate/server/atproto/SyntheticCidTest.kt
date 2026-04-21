package app.logdate.server.atproto

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [SyntheticCid], validating the deterministic generation of Content
 * Identifiers for repository records.
 *
 * It ensures that the underlying encoding (varint, base32) is correct and that
 * the resulting CIDs are stable for identical inputs.
 */
class SyntheticCidTest {
    @Test
    fun `synthetic cid helpers handle varint and base32 edge cases`() {
        val syntheticCidClass = Class.forName("app.logdate.server.atproto.SyntheticCid")
        val instance = syntheticCidClass.getField("INSTANCE").get(null)
        val encodeVarint = syntheticCidClass.getDeclaredMethod("encodeVarint", Int::class.javaPrimitiveType)
        val encodeBase32 = syntheticCidClass.getDeclaredMethod("encodeBase32", ByteArray::class.java)
        encodeVarint.isAccessible = true
        encodeBase32.isAccessible = true

        val multiByteVarint = encodeVarint.invoke(instance, 128) as ByteArray
        val emptyBase32 = encodeBase32.invoke(instance, byteArrayOf()) as String

        assertContentEquals(byteArrayOf(0x80.toByte(), 0x01), multiByteVarint)
        assertEquals("", emptyBase32)
    }

    @Test
    fun `synthetic cid output is stable and invalid swap exposes metadata`() {
        val syntheticCidClass = Class.forName("app.logdate.server.atproto.SyntheticCid")
        val instance = syntheticCidClass.getField("INSTANCE").get(null)
        val fromRecord = syntheticCidClass.getDeclaredMethod("fromRecord", String::class.java, JsonObject::class.java)
        fromRecord.isAccessible = true

        val value =
            buildJsonObject {
                put("\$type", "studio.hypertext.logdate.content")
                put("content", "hello")
            }

        val firstCid =
            fromRecord.invoke(
                instance,
                "at://did:web:alice.logdate.app/studio.hypertext.logdate.content/entry-1",
                value,
            ) as String
        val secondCid =
            fromRecord.invoke(
                instance,
                "at://did:web:alice.logdate.app/studio.hypertext.logdate.content/entry-1",
                value,
            ) as String
        val exception = InvalidSwapException(expectedCid = "bafy-expected", providedCid = "bafy-provided")

        assertEquals(firstCid, secondCid)
        assertTrue(firstCid.startsWith("b"))
        assertEquals("bafy-expected", exception.expectedCid)
        assertEquals("bafy-provided", exception.providedCid)
    }
}
