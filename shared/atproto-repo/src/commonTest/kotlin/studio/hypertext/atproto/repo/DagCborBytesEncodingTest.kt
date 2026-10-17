package studio.hypertext.atproto.repo

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * The AT Protocol data model writes bytes in JSON as `{"$bytes": "..."}` using the standard base64
 * alphabet, with padding optional on read. Other implementations depend on that exact form.
 */
class DagCborBytesEncodingTest {
    // 0xfb 0xff 0xbf encodes to "+/+/" in standard base64 and "-_-_" in the URL-safe alphabet.
    private val bytesNeedingSymbols = byteArrayOf(0xfb.toByte(), 0xff.toByte(), 0xbf.toByte())

    @Test
    fun `bytes are written with the standard base64 alphabet and no padding`() {
        assertEquals("+/+/", DagCborCodec.bytes(bytesNeedingSymbols)["\$bytes"]?.jsonPrimitive?.content)
        assertEquals("AQ", DagCborCodec.bytes(byteArrayOf(1))["\$bytes"]?.jsonPrimitive?.content)
    }

    @Test
    fun `bytes are read with or without padding`() {
        assertContentEquals(byteArrayOf(1), DagCborCodec.bytesOrNull(bytesJson("AQ")))
        assertContentEquals(byteArrayOf(1), DagCborCodec.bytesOrNull(bytesJson("AQ==")))
        assertContentEquals(bytesNeedingSymbols, DagCborCodec.bytesOrNull(bytesJson("+/+/")))
    }

    @Test
    fun `bytes survive a round trip through DAG-CBOR`() {
        val element = JsonObject(mapOf("data" to DagCborCodec.bytes(bytesNeedingSymbols)))

        val decoded = DagCborCodec.decode(DagCborCodec.encode(element)) as JsonObject

        assertEquals(element, decoded)
        assertContentEquals(bytesNeedingSymbols, DagCborCodec.bytesOrNull(decoded["data"]))
    }

    private fun bytesJson(encoded: String) = JsonObject(mapOf("\$bytes" to JsonPrimitive(encoded)))
}
