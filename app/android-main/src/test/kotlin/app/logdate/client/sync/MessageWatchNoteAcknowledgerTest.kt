package app.logdate.client.sync

import app.logdate.client.sync.datalayer.NoteAckPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests [MessageWatchNoteAcknowledger], which tells the watch a note is stored on the phone, and which
 * version of it.
 */
class MessageWatchNoteAcknowledgerTest {
    private val noteId = Uuid.parse("550e8400-e29b-41d4-a716-446655440000")
    private val stored = Instant.fromEpochMilliseconds(1_710_000_000_123)

    @Test
    fun `acknowledging a note messages the watch on the note ack path`() =
        runTest {
            val sentPaths = mutableListOf<String>()
            val acknowledger = MessageWatchNoteAcknowledger { path, _ -> sentPaths.add(path) }

            acknowledger.acknowledge(noteId, stored)

            assertEquals(listOf("/logdate/notes/550e8400-e29b-41d4-a716-446655440000/ack"), sentPaths)
        }

    @Test
    fun `the acknowledgement names the version of the note the phone stored`() =
        runTest {
            val payloads = mutableListOf<ByteArray>()
            val acknowledger = MessageWatchNoteAcknowledger { _, data -> payloads.add(data) }

            acknowledger.acknowledge(noteId, stored)

            assertEquals(NoteAckPayload(lastUpdated = 1_710_000_000_123), NoteAckPayload.decode(payloads.single()))
        }

    @Test
    fun `an undeliverable acknowledgement does not fail the caller`() =
        runTest {
            val acknowledger = MessageWatchNoteAcknowledger { _, _ -> false }

            acknowledger.acknowledge(noteId, stored)
        }
}
