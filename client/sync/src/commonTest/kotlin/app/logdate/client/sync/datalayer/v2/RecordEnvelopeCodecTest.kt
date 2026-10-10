package app.logdate.client.sync.datalayer.v2

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests [RecordEnvelopeCodec], the wrapper every record crosses the Data Layer in.
 */
class RecordEnvelopeCodecTest {
    private val envelope =
        RecordEnvelope(
            kind = RecordKind.NOTE,
            id = ID,
            contentVersion = 1_710_000_000_123,
            origin = RecordOrigin.PHONE,
            reasons = setOf(WindowReason.RECENT),
            body = buildJsonObject { put("text", "hi") },
        )

    @Test
    fun `an envelope has a stable wire form`() {
        assertEquals(GOLDEN, RecordEnvelopeCodec.encode(envelope))
    }

    @Test
    fun `an envelope reads back`() {
        assertEquals(Decoded.Supported(envelope), RecordEnvelopeCodec.decode(RecordEnvelopeCodec.encode(envelope)))
    }

    @Test
    fun `keys a newer phone adds are ignored`() {
        val newer = GOLDEN.replace("\"media\":[]", "\"media\":[],\"later\":true")

        assertEquals(Decoded.Supported(envelope), RecordEnvelopeCodec.decode(newer))
    }

    @Test
    fun `a newer envelope schema is unsupported`() {
        val decoded = RecordEnvelopeCodec.decode(GOLDEN.replace("\"schema\":1", "\"schema\":2"))

        assertEquals(2, assertIs<Decoded.Unsupported>(decoded).schema)
    }

    @Test
    fun `an encrypted envelope is unsupported by this version`() {
        val encrypted = GOLDEN.replace("\"media\":[]", "\"media\":[],\"enc\":\"aes-gcm\"")

        assertIs<Decoded.Unsupported>(RecordEnvelopeCodec.decode(encrypted))
    }

    @Test
    fun `an unknown kind in this schema is malformed`() {
        assertIs<Decoded.Malformed>(RecordEnvelopeCodec.decode(GOLDEN.replace("\"NOTE\"", "\"POSTCARD\"")))
    }

    @Test
    fun `an envelope with both an inline body and a reference is refused`() {
        val both = envelope.copy(bodyRef = BodyRef(sha256 = "ab".repeat(32), sizeBytes = 10))
        val wire = GOLDEN.replace("\"media\":[]", "\"bodyRef\":{\"sha256\":\"${"ab".repeat(32)}\",\"sizeBytes\":10},\"media\":[]")

        assertFailsWith<IllegalArgumentException> { RecordEnvelopeCodec.encode(both) }
        assertIs<Decoded.Malformed>(RecordEnvelopeCodec.decode(wire))
    }

    @Test
    fun `a body over 90 KB goes over a channel`() {
        val large = JsonPrimitive("x".repeat(WearProtocol.MAX_INLINE_BODY_BYTES))

        assertTrue(RecordEnvelopeCodec.needsChannel(large))
        assertFalse(RecordEnvelopeCodec.needsChannel(JsonPrimitive("small")))
        assertFailsWith<IllegalArgumentException> { RecordEnvelopeCodec.encode(envelope.copy(body = large)) }
    }

    private companion object {
        const val ID = "550e8400-e29b-41d4-a716-446655440000"
        const val GOLDEN =
            """{"schema":1,"kind":"NOTE","id":"550e8400-e29b-41d4-a716-446655440000","contentVersion":1710000000123,""" +
                """"syncVersion":0,"origin":"PHONE","reasons":["RECENT"],"priority":"NORMAL","body":{"text":"hi"},"media":[]}"""
    }
}
