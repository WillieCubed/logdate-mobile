package app.logdate.client.sync.peer.state

import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.Decoded
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Tests [StateDocumentCodec], the per-device document at `/logdate/state`.
 */
class StateDocumentCodecTest {
    @Test
    fun `a watch's link problem reads back and is left out when there is none`() {
        val failed =
            watchDocument.copy(watch = watchDocument.watch?.copy(link = PeerLinkState.NEEDS_RELINK, linkProblem = "DEVICE_LIMIT_REACHED"))

        assertEquals(Decoded.Supported(failed), StateDocumentCodec.decode(StateDocumentCodec.encode(failed)))
        assertEquals(false, StateDocumentCodec.encode(watchDocument).contains("linkProblem"))
    }

    private val phoneDocument =
        StateDocument(
            role = PeerRole.PHONE,
            writtenAt = 1_000,
            phone =
                PhoneState(
                    flags = mapOf("watch_experience_v2_enabled" to true),
                    cloud = CloudPresence.SIGNED_IN,
                    zone = "Europe/Paris",
                    features = setOf("receipts.v1"),
                ),
        )

    private val watchDocument =
        StateDocument(
            role = PeerRole.WATCH,
            writtenAt = 1_000,
            watch =
                WatchState(
                    outboxCount = 3,
                    deadLetterCount = 1,
                    freeBytes = 1_024,
                    link = PeerLinkState.LINKED,
                    presence = Presence(PresenceState.ON_WRIST, observedAt = 900),
                    features = setOf("receipts.v1"),
                ),
            shared = mapOf("reminders.enabled" to Stamped(JsonPrimitive(true), at = 950)),
            lastReminderAt = 800,
        )

    @Test
    fun `a phone document has a stable wire form`() {
        assertEquals(PHONE_GOLDEN, StateDocumentCodec.encode(phoneDocument))
    }

    @Test
    fun `a watch document reads back`() {
        assertEquals(Decoded.Supported(watchDocument), StateDocumentCodec.decode(StateDocumentCodec.encode(watchDocument)))
    }

    @Test
    fun `keys a newer device adds are ignored at every level`() {
        val newer =
            PHONE_GOLDEN
                .replace("\"schema\":1,", "\"schema\":1,\"later\":{\"a\":1},")
                .replace("\"openMarks\":0,", "\"openMarks\":0,\"laterPhoneField\":true,")

        assertEquals(Decoded.Supported(phoneDocument), StateDocumentCodec.decode(newer))
    }

    @Test
    fun `a newer schema is unsupported`() {
        val decoded = StateDocumentCodec.decode(PHONE_GOLDEN.replace("\"schema\":1", "\"schema\":2"))

        assertEquals(2, assertIs<Decoded.Unsupported>(decoded).schema)
    }

    @Test
    fun `an unknown presence state in this schema is malformed`() {
        val wire = StateDocumentCodec.encode(watchDocument).replace("ON_WRIST", "ASLEEP")

        assertIs<Decoded.Malformed>(StateDocumentCodec.decode(wire))
    }

    @Test
    fun `a document over 8 KB is refused`() {
        val oversized = phoneDocument.copy(phone = PhoneState(flags = (1..1_000).associate { "flag_$it" to true }))

        assertFailsWith<IllegalArgumentException> { StateDocumentCodec.encode(oversized) }
    }

    @Test
    fun `the document travels under one data item key`() {
        val data = StateDocumentCodec.toDataMap(phoneDocument)

        assertEquals(setOf("doc"), data.keys)
        assertEquals(Decoded.Supported(phoneDocument), StateDocumentCodec.fromDataMap(data))
    }

    @Test
    fun `a data item without a document is malformed`() {
        assertIs<Decoded.Malformed>(StateDocumentCodec.fromDataMap(emptyMap()))
    }

    private companion object {
        const val PHONE_GOLDEN =
            """{"schema":1,"role":"PHONE","writtenAt":1000,"phone":{"flags":{"watch_experience_v2_enabled":true},""" +
                """"cloud":"SIGNED_IN","dayStartHour":0,"zone":"Europe/Paris","openMarks":0,"features":["receipts.v1"]},"shared":{}}"""
    }
}
