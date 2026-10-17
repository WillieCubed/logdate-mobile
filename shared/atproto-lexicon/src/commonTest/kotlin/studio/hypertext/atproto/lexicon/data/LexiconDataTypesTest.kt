package studio.hypertext.atproto.lexicon.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import studio.hypertext.atproto.repo.Cid
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Blobs, content links and bytes as the AT Protocol data model writes them in JSON and DAG-CBOR.
 */
class LexiconDataTypesTest {
    private val cid = Cid.rawSha256("photo".encodeToByteArray())

    @Test
    fun `a content link is written as a link object`() {
        val encoded = Json.encodeToString(CidLink.serializer(), CidLink(cid))

        assertEquals("""{"${'$'}link":"$cid"}""", encoded)
        assertEquals(CidLink(cid), Json.decodeFromString(CidLink.serializer(), encoded))
    }

    @Test
    fun `bytes are written with standard base64 and compared by content`() {
        val bytes = Bytes(byteArrayOf(0xfb.toByte(), 0xff.toByte(), 0xbf.toByte()))

        val encoded = Json.encodeToString(Bytes.serializer(), bytes)

        assertEquals("""{"${'$'}bytes":"+/+/"}""", encoded)
        assertEquals(bytes, Json.decodeFromString(Bytes.serializer(), encoded))
        assertNotEquals(bytes, Bytes(byteArrayOf(1)))
        assertContentEquals(byteArrayOf(0xfb.toByte(), 0xff.toByte(), 0xbf.toByte()), bytes.toByteArray())
    }

    @Test
    fun `a blob is written in the current form with its type`() {
        val blob = Blob(ref = CidLink(cid), mimeType = "image/jpeg", size = 1234)

        val encoded = Json.parseToJsonElement(Json.encodeToString(Blob.serializer(), blob)).jsonObject

        assertEquals(
            Json.parseToJsonElement(
                """{"${'$'}type":"blob","ref":{"${'$'}link":"$cid"},"mimeType":"image/jpeg","size":1234}""",
            ),
            encoded,
        )
        assertEquals(blob, Json.decodeFromJsonElement(Blob.serializer(), encoded))
    }

    @Test
    fun `a blob without the blob type or in the legacy form is rejected with a clear error`() {
        val wrongType = """{"${'$'}type":"image","ref":{"${'$'}link":"$cid"},"mimeType":"image/jpeg","size":1}"""
        val legacy = """{"cid":"$cid","mimeType":"image/jpeg"}"""

        val wrongTypeError = assertFailsWith<SerializationException> { Json.decodeFromString(Blob.serializer(), wrongType) }
        val legacyError = assertFailsWith<SerializationException> { Json.decodeFromString(Blob.serializer(), legacy) }

        assertEquals("A blob's \$type must be \"blob\", not \"image\"", wrongTypeError.message)
        assertEquals("Legacy blobs (cid and mimeType without a size) aren't supported", legacyError.message)
    }

    @Test
    fun `a blob with a negative size is rejected`() {
        val negative = """{"${'$'}type":"blob","ref":{"${'$'}link":"$cid"},"mimeType":"image/jpeg","size":-1}"""

        val error = assertFailsWith<SerializationException> { Json.decodeFromString(Blob.serializer(), negative) }

        assertEquals("A blob's size must not be negative", error.message)
    }

    @Test
    fun `values survive a round trip through DAG-CBOR`() {
        val blob = Blob(ref = CidLink(cid), mimeType = "audio/mp4", size = 99)
        val bytes = Bytes(byteArrayOf(0, 1, 2, 3, 0xff.toByte()))

        assertEquals(blob, LexiconData.decodeFromDagCbor(Blob.serializer(), LexiconData.encodeToDagCbor(Blob.serializer(), blob)))
        assertEquals(bytes, LexiconData.decodeFromDagCbor(Bytes.serializer(), LexiconData.encodeToDagCbor(Bytes.serializer(), bytes)))
        assertEquals(
            CidLink(cid),
            LexiconData.decodeFromDagCbor(CidLink.serializer(), LexiconData.encodeToDagCbor(CidLink.serializer(), CidLink(cid))),
        )
    }

    @Test
    fun `DAG-CBOR encoding writes a blob ref as a native link`() {
        val blob = Blob(ref = CidLink(cid), mimeType = "image/png", size = 1)

        val element = LexiconData.json.encodeToJsonElement(Blob.serializer(), blob) as JsonObject
        val cbor = LexiconData.encodeToDagCbor(Blob.serializer(), blob)

        // CBOR tag 42 marks a link; its presence shows the ref wasn't written as a plain string map.
        assertTrue(cbor.indexOfTag42() >= 0, "Expected a CBOR tag 42 link in $element")
    }

    private fun ByteArray.indexOfTag42(): Int = indices.firstOrNull { this[it] == 0xd8.toByte() && getOrNull(it + 1) == 42.toByte() } ?: -1
}
