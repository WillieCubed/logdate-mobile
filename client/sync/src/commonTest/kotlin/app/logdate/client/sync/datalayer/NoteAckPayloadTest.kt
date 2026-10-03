package app.logdate.client.sync.datalayer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * Tests [NoteAckPayload], the version the phone names when it acknowledges a watch note.
 */
class NoteAckPayloadTest {
    @Test
    fun `a payload reads back`() {
        val ack = NoteAckPayload(lastUpdated = 1_710_000_000_123)

        assertEquals(ack, NoteAckPayload.decode(ack.encode()))
    }

    @Test
    fun `the payload is a small JSON object`() {
        val json = NoteAckPayload(lastUpdated = 1_710_000_000_123).encode().decodeToString()

        assertEquals("""{"lastUpdated":1710000000123}""", json)
    }

    @Test
    fun `an empty payload from an older phone has no version`() {
        assertNull(NoteAckPayload.decode(ByteArray(0)))
    }

    @Test
    fun `fields a newer phone adds are ignored`() {
        val payload = """{"lastUpdated":5,"backedUp":true}""".encodeToByteArray()

        assertEquals(NoteAckPayload(lastUpdated = 5), NoteAckPayload.decode(payload))
    }

    @Test
    fun `a payload that is not an acknowledgement is rejected`() {
        assertFailsWith<IllegalArgumentException> { NoteAckPayload.decode("not json".encodeToByteArray()) }
    }

    @Test
    fun `the version is the note's last update to the millisecond`() {
        val ack = NoteAckPayload.of(Instant.fromEpochMilliseconds(1_710_000_000_123))

        assertEquals(NoteAckPayload(lastUpdated = 1_710_000_000_123), ack)
    }
}
