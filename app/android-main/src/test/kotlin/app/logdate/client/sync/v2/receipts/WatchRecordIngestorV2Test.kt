package app.logdate.client.sync.v2.receipts

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.WatchNoteAcknowledger
import app.logdate.client.sync.datalayer.AudioFileDigest
import app.logdate.client.sync.datalayer.NoteDataMapper
import app.logdate.client.sync.datalayer.v2.DeliveryPriority
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.MediaRef
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordEnvelope
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.NoteEnvelopeBody
import app.logdate.client.sync.peer.link.LinkWire
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests [WatchRecordIngestorV2]: a watch record becomes a phone note, and anything unreadable is refused by name. */
class WatchRecordIngestorV2Test {
    private val stored = mutableListOf<Map<String, String>>()
    private val audio = mutableListOf<Uuid>()
    private val acknowledged = mutableListOf<Pair<Uuid, Long>>()
    private val rejected = mutableListOf<Pair<RecordKind, String>>()
    private val notes = mockk<JournalNotesRepository>()
    private val ingestor =
        WatchRecordIngestorV2(
            ingestMetadata = { stored += it },
            ingestAudio = { id, _ -> audio += id },
            notes = notes,
            acknowledger = WatchNoteAcknowledger { id, at -> acknowledged += id to at.toEpochMilliseconds() },
            rejection = RecordRejection { kind, id -> rejected += kind to id },
        )
    private val text =
        JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = Instant.fromEpochMilliseconds(1),
            lastUpdated = Instant.fromEpochMilliseconds(5),
            content = "said on the watch",
        )

    @Test
    fun `the unbound ingestion API cannot consume a scoped envelope`() =
        runTest {
            coEvery { notes.getNoteById(text.uid) } returns text
            val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32)), "phone", "watch", LinkMode.PAIR)
            val envelope = NoteEnvelopeBody.envelope(text, RecordOrigin.WATCH, DeliveryPriority.URGENT).copy(context = peer)
            ingestor.onRecord(WearPaths.watchRecord(RecordKind.NOTE, text.uid), EnvelopeDataMap.toDataMap(envelope, sentAt = 1))
            assertTrue(stored.isEmpty())
            assertTrue(acknowledged.isEmpty())
            assertTrue(rejected.isEmpty())
        }

    @Test
    fun `the unbound ingestion API cannot adopt scoped audio`() =
        runTest {
            val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32)), "phone", "watch", LinkMode.PAIR)
            ingestor.onMedia(WearPaths.recordMedia(RecordKind.NOTE, text.uid, peer), ByteArrayInputStream(byteArrayOf(1)))
            assertTrue(audio.isEmpty())
            assertTrue(acknowledged.isEmpty())
            assertTrue(rejected.isEmpty())
        }

    @Test
    fun `a text note is stored and acknowledged once the phone has it`() =
        runTest {
            coEvery { notes.getNoteById(text.uid) } returns text

            ingestor.onRecord(WearPaths.watchRecord(RecordKind.NOTE, text.uid), dataMap(text))

            assertEquals(text, NoteDataMapper().fromDataMap(stored.single()))
            assertEquals(listOf(text.uid to 5L), acknowledged)
        }

    @Test
    fun `an audio note includes its file's size and digest`() =
        runTest {
            val recording =
                JournalNote.Audio(
                    mediaRef = "/data/user/0/app/files/audio_notes/a.m4a",
                    uid = Uuid.random(),
                    creationTimestamp = Instant.fromEpochMilliseconds(1),
                    lastUpdated = Instant.fromEpochMilliseconds(5),
                )
            val media = MediaRef(sha256 = "ab".repeat(32), sizeBytes = 42, mimeType = "audio/mp4")
            val envelope = NoteEnvelopeBody.envelope(recording, RecordOrigin.WATCH, DeliveryPriority.URGENT, media = listOf(media))

            ingestor.onRecord(WearPaths.watchRecord(RecordKind.NOTE, recording.uid), EnvelopeDataMap.toDataMap(envelope, sentAt = 1))

            assertEquals(AudioFileDigest(sizeBytes = 42, sha256Hex = "ab".repeat(32)), NoteDataMapper.audioDigest(stored.single()))
            assertTrue(acknowledged.isEmpty())
        }

    @Test
    fun `a record from a newer version is rejected so the watch stops sending it`() =
        runTest {
            val newer = mapOf(EnvelopeDataMap.KEY_ENVELOPE to """{"schema":2,"kind":"NOTE","id":"${text.uid}"}""")

            ingestor.onRecord(WearPaths.watchRecord(RecordKind.NOTE, text.uid), newer)

            assertEquals(listOf(RecordKind.NOTE to text.uid.toString()), rejected)
            assertTrue(stored.isEmpty())
        }

    @Test
    fun `a marked moment is rejected until this phone takes them`() =
        runTest {
            val id = Uuid.random()
            val moment =
                RecordEnvelope(
                    kind = RecordKind.MOMENT,
                    id = id.toString(),
                    contentVersion = 1,
                    origin = RecordOrigin.WATCH,
                    body = JsonObject(emptyMap()),
                )

            ingestor.onRecord(WearPaths.watchRecord(RecordKind.MOMENT, id), EnvelopeDataMap.toDataMap(moment, sentAt = 1))

            assertEquals(listOf(RecordKind.MOMENT to id.toString()), rejected)
        }

    @Test
    fun `a body that is not a note is rejected`() =
        runTest {
            val broken =
                RecordEnvelope(
                    kind = RecordKind.NOTE,
                    id = text.uid.toString(),
                    contentVersion = 5,
                    origin = RecordOrigin.WATCH,
                    body = JsonPrimitive("x"),
                )

            ingestor.onRecord(WearPaths.watchRecord(RecordKind.NOTE, text.uid), EnvelopeDataMap.toDataMap(broken, sentAt = 1))

            assertEquals(listOf(RecordKind.NOTE to text.uid.toString()), rejected)
            assertTrue(stored.isEmpty())
        }

    @Test
    fun `a recording goes to its note's audio`() =
        runTest {
            ingestor.onMedia(WearPaths.recordMedia(RecordKind.NOTE, text.uid), ByteArrayInputStream(ByteArray(3)))

            assertEquals(listOf(text.uid), audio)
        }

    @Test
    fun `a path kind that differs from its envelope is rejected`() =
        runTest {
            coEvery { notes.getNoteById(text.uid) } returns text
            ingestor.onRecord(WearPaths.watchRecord(RecordKind.MOMENT, text.uid), dataMap(text))
            assertEquals(listOf(RecordKind.MOMENT to text.uid.toString()), rejected)
            assertTrue(stored.isEmpty())
        }

    @Test
    fun `a body id that differs from its envelope is rejected`() =
        runTest {
            coEvery { notes.getNoteById(any()) } returns text
            val wrong = NoteEnvelopeBody.envelope(text, RecordOrigin.WATCH, DeliveryPriority.URGENT).copy(id = Uuid.random().toString())
            ingestor.onRecord(WearPaths.watchRecord(RecordKind.NOTE, Uuid.parse(wrong.id)), EnvelopeDataMap.toDataMap(wrong, 1))
            assertTrue(stored.isEmpty())
            assertEquals(listOf(RecordKind.NOTE to wrong.id), rejected)
        }

    @Test
    fun `a body version that differs from its envelope is rejected`() =
        runTest {
            coEvery { notes.getNoteById(text.uid) } returns text
            val wrong = NoteEnvelopeBody.envelope(text, RecordOrigin.WATCH, DeliveryPriority.URGENT).copy(contentVersion = 6)
            ingestor.onRecord(WearPaths.watchRecord(RecordKind.NOTE, text.uid), EnvelopeDataMap.toDataMap(wrong, 1))
            assertTrue(stored.isEmpty())
            assertEquals(listOf(RecordKind.NOTE to text.uid.toString()), rejected)
        }

    @Test
    fun `a phone-origin envelope is not accepted as a watch capture`() =
        runTest {
            coEvery { notes.getNoteById(text.uid) } returns text
            val wrong = NoteEnvelopeBody.envelope(text, RecordOrigin.PHONE, DeliveryPriority.URGENT)
            ingestor.onRecord(WearPaths.watchRecord(RecordKind.NOTE, text.uid), EnvelopeDataMap.toDataMap(wrong, 1))
            assertTrue(stored.isEmpty())
            assertEquals(listOf(RecordKind.NOTE to text.uid.toString()), rejected)
        }

    @Test
    fun `v2 audio without an integrity reference is rejected`() =
        runTest {
            val note =
                JournalNote.Audio(
                    mediaRef = "a.m4a",
                    uid = text.uid,
                    creationTimestamp = text.creationTimestamp,
                    lastUpdated = text.lastUpdated,
                )
            ingestor.onRecord(WearPaths.watchRecord(RecordKind.NOTE, note.uid), dataMap(note))
            assertTrue(stored.isEmpty())
            assertEquals(listOf(RecordKind.NOTE to text.uid.toString()), rejected)
        }

    @Test
    fun `redelivery cannot recreate a capture deleted on the phone`() =
        runTest {
            coEvery { notes.getNoteById(text.uid) } returns null
            val guarded =
                WatchRecordIngestorV2(
                    ingestMetadata = { stored += it },
                    ingestAudio = { id, _ -> audio += id },
                    notes = notes,
                    acknowledger = WatchNoteAcknowledger { id, at -> acknowledged += id to at.toEpochMilliseconds() },
                    rejection = RecordRejection { kind, id -> rejected += kind to id },
                    receivedVersion = { 5L },
                )
            guarded.onRecord(WearPaths.watchRecord(RecordKind.NOTE, text.uid), dataMap(text))
            assertTrue(stored.isEmpty())
            assertEquals(listOf(text.uid to 5L), acknowledged)
        }

    private fun dataMap(note: JournalNote) =
        EnvelopeDataMap.toDataMap(NoteEnvelopeBody.envelope(note, RecordOrigin.WATCH, DeliveryPriority.URGENT), sentAt = 1)
}
