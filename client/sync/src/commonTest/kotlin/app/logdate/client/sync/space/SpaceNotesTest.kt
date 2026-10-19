package app.logdate.client.sync.space

import app.logdate.client.media.MediaPayload
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.repository.transcription.TranscriptDocumentStatus
import app.logdate.client.sync.metadata.MediaSyncRef
import app.logdate.client.sync.metadata.PendingOperation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Notes are a kind with two record types: the entry and, for a transcribed recording, its transcript. */
class SpaceNotesTest {
    private val created = Instant.parse("2026-10-01T09:00:00Z")

    private fun recording(
        id: Uuid = Uuid.random(),
        transcript: TranscriptDocument? = transcript(1),
    ) = JournalNote.Audio(
        uid = id,
        mediaRef = uploadedAudioRef(id),
        durationMs = 4_000,
        creationTimestamp = created,
        lastUpdated = created,
        transcript = transcript,
    )

    private fun transcript(revision: Int) =
        TranscriptDocument(revision = revision, status = TranscriptDocumentStatus.FINAL, language = "en-US")

    @Test
    fun `a transcribed recording is written as its entry and its transcript`() =
        runTest {
            val device = SpaceDevice(FakeDataHome())
            val note = recording()
            device.saveNote(note)

            val result = device.manager.uploadPendingChanges()

            assertTrue(result.success, result.errors.toString())
            val entry = assertNotNull(device.home.open(ENTRY_COLLECTION, note.uid.toString())).record.jsonObject
            val block = (entry["blocks"] as JsonArray).single().jsonObject
            assertEquals(transcriptKey(note.uid), (block["transcript"] as JsonObject)["rkey"]?.jsonPrimitive?.content)
            val stored = assertNotNull(device.home.open(TRANSCRIPT_COLLECTION, transcriptKey(note.uid)))
            assertEquals(
                1L,
                stored.record.jsonObject["revision"]
                    ?.jsonPrimitive
                    ?.content
                    ?.toLong(),
            )
            assertNotNull(device.swapStore.get(device.scope, ENTRY_COLLECTION, note.uid.toString()))
            assertNotNull(device.swapStore.get(device.scope, TRANSCRIPT_COLLECTION, transcriptKey(note.uid)))
            assertEquals(2, device.home.writes)
            assertTrue(device.pendingNotes().isEmpty())
        }

    @Test
    fun `another device applies the entry and transcript as one note`() =
        runTest {
            val home = FakeDataHome()
            val phone = SpaceDevice(home)
            val tablet = SpaceDevice(home)
            val note = recording()
            phone.saveNote(note)
            phone.manager.uploadPendingChanges()

            val result = tablet.manager.downloadRemoteChanges()

            assertTrue(result.success, result.errors.toString())
            val applied = assertIs<JournalNote.Audio>(tablet.note(note.uid))
            assertEquals(note.mediaRef, applied.mediaRef)
            assertEquals(transcript(1), applied.transcript)
            assertEquals(2L, applied.syncVersion, "The newest record's position is the note's version")
            assertEquals(0, tablet.waitingRecords())
        }

    @Test
    fun `a new transcript alone updates the note this device already has`() =
        runTest {
            val home = FakeDataHome()
            val phone = SpaceDevice(home)
            val tablet = SpaceDevice(home)
            val note = recording(transcript = null)
            phone.saveNote(note)
            phone.manager.uploadPendingChanges()
            tablet.manager.downloadRemoteChanges()
            assertNull((tablet.note(note.uid) as JournalNote.Audio).transcript)

            phone.saveNote(note.copy(transcript = transcript(2), lastUpdated = created + 1.hours), PendingOperation.UPDATE)
            phone.manager.uploadPendingChanges()
            tablet.manager.downloadRemoteChanges()

            assertEquals(transcript(2), (tablet.note(note.uid) as JournalNote.Audio).transcript)
            assertEquals(0, tablet.waitingRecords())
        }

    @Test
    fun `a transcript removed here is deleted from the space and from other devices`() =
        runTest {
            val home = FakeDataHome()
            val phone = SpaceDevice(home)
            val tablet = SpaceDevice(home)
            val note = recording()
            phone.saveNote(note)
            phone.manager.uploadPendingChanges()
            tablet.manager.downloadRemoteChanges()

            phone.saveNote(
                phone
                    .note(note.uid)
                    .let {
                        it as JournalNote.Audio
                    }.copy(transcript = null, lastUpdated = created + 1.hours),
                PendingOperation.UPDATE,
            )
            val result = phone.manager.uploadPendingChanges()
            tablet.manager.downloadRemoteChanges()

            assertTrue(result.success, result.errors.toString())
            assertNull(home.open(TRANSCRIPT_COLLECTION, transcriptKey(note.uid)))
            assertNull(phone.swapStore.get(phone.scope, TRANSCRIPT_COLLECTION, transcriptKey(note.uid)))
            assertNull((tablet.note(note.uid) as JournalNote.Audio).transcript)
            assertNotNull(tablet.note(note.uid))
        }

    @Test
    fun `a transcript that arrives before its entry waits for it`() =
        runTest {
            val home = FakeDataHome()
            val tablet = SpaceDevice(home)
            val note = recording()
            home.putFromAnotherDevice(
                TRANSCRIPT_COLLECTION,
                transcriptKey(note.uid),
                TestTranscriptMapper.toRecord(TestTranscript(note.uid, transcript(3))),
            )

            tablet.manager.downloadRemoteChanges()

            assertNull(tablet.note(note.uid))
            assertEquals(1, tablet.waitingRecords(), "The transcript waits for its entry")

            home.putFromAnotherDevice(ENTRY_COLLECTION, note.uid.toString(), TestEntryMapper.toRecord(note.copy(transcript = null)))
            tablet.manager.downloadRemoteChanges()

            assertEquals(transcript(3), (tablet.note(note.uid) as JournalNote.Audio).transcript)
            assertEquals(0, tablet.waitingRecords())
        }

    @Test
    fun `deleting a note deletes its entry and its transcript`() =
        runTest {
            val home = FakeDataHome()
            val phone = SpaceDevice(home)
            val tablet = SpaceDevice(home)
            val note = recording()
            phone.saveNote(note)
            phone.manager.uploadPendingChanges()
            tablet.manager.downloadRemoteChanges()

            phone.deleteNote(note.uid)
            val result = phone.manager.uploadPendingChanges()
            tablet.manager.downloadRemoteChanges()

            assertTrue(result.success, result.errors.toString())
            assertTrue(home.liveRkeys().isEmpty())
            assertNull(tablet.note(note.uid))
            assertTrue(tablet.swapStore.states.isEmpty())
            assertEquals(0, tablet.waitingRecords())
        }

    @Test
    fun `a recording's file goes to the space encrypted and comes back to another device's media store`() =
        runTest {
            val home = FakeDataHome()
            val phone = SpaceDevice(home)
            val tablet = SpaceDevice(home)
            val sound = ByteArray(300_000) { (it % 199).toByte() }
            val local = phone.media.saveMedia(MediaPayload("memo.m4a", "audio/mp4", sound.size.toLong(), sound))
            val note = recording().copy(mediaRef = local)
            phone.saveNote(note)

            val uploaded = phone.manager.uploadPendingChanges()
            val downloaded = tablet.manager.downloadRemoteChanges()

            assertTrue(uploaded.success, uploaded.errors.toString())
            assertTrue(downloaded.success, downloaded.errors.toString())
            val stored = home.blobs.values.single()
            assertEquals("LDCE2", stored.copyOf(5).decodeToString())
            val applied = assertIs<JournalNote.Audio>(tablet.note(note.uid))
            assertFalse(applied.mediaRef.startsWith("https://"), "The file was recovered into the media store")
            assertContentEquals(sound, tablet.media.readMedia(applied.mediaRef).data)
            assertEquals(0, tablet.inbox.count())
        }

    @Test
    fun `a file uploaded to the old endpoint is uploaded again into the space`() =
        runTest {
            val home = FakeDataHome()
            val phone = SpaceDevice(home)
            val sound = ByteArray(1_000) { 4 }
            val local = phone.media.saveMedia(MediaPayload("memo.m4a", "audio/mp4", sound.size.toLong(), sound))
            val note = recording().copy(mediaRef = local)
            phone.mediaRefs.upsert(MediaSyncRef(note.uid.toString(), local, "https://cloud.test/api/v1/media/m1", "m1", 0))
            phone.saveNote(note)

            val result = phone.manager.uploadPendingChanges()

            assertTrue(result.success, result.errors.toString())
            assertEquals(1, home.blobs.size)
            assertNotNull(SpaceMediaReference.parse(phone.mediaRefs.get(note.uid)!!.remoteUrl))
        }

    @Test
    fun `a note that can't be written yet waits while the rest upload`() =
        runTest {
            val device = SpaceDevice(FakeDataHome())
            // Its file lives on the server it synced with before, not in the personal space.
            val elsewhere = recording().copy(mediaRef = "https://cloud.test/api/v1/media/m1")
            val text = JournalNote.Text(creationTimestamp = created, lastUpdated = created, content = "Swam")
            device.saveNote(elsewhere)
            device.saveNote(text)

            val result = device.manager.uploadPendingChanges()

            assertFalse(result.success)
            assertTrue(result.errors.single().retryable, result.errors.toString())
            assertNotNull(device.home.open(ENTRY_COLLECTION, text.uid.toString()))
            assertNull(device.home.open(ENTRY_COLLECTION, elsewhere.uid.toString()))
            assertEquals(listOf(elsewhere.uid.toString()), device.pendingNotes().map { it.entityId })
            assertTrue(device.deadLetters.list().isEmpty())
        }

    @Test
    fun `a note no record can hold is set aside while the rest upload`() =
        runTest {
            val device = SpaceDevice(FakeDataHome())
            val photo = JournalNote.Image(mediaRef = uploadedAudioRef(Uuid.random()), creationTimestamp = created, lastUpdated = created)
            val text = JournalNote.Text(creationTimestamp = created, lastUpdated = created, content = "Swam")
            device.saveNote(photo)
            device.saveNote(text)

            val result = device.manager.uploadPendingChanges()

            assertFalse(result.errors.single().retryable)
            assertNotNull(device.home.open(ENTRY_COLLECTION, text.uid.toString()))
            assertEquals(
                photo.uid.toString(),
                device.deadLetters
                    .list()
                    .single()
                    .entityId,
            )
        }
}
