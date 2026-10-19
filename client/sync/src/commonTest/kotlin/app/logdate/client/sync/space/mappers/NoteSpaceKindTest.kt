package app.logdate.client.sync.space.mappers

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.repository.transcription.TranscriptDocumentStatus
import app.logdate.client.repository.transcription.TranscriptSegment
import app.logdate.client.repository.transcription.TranscriptSoundTag
import app.logdate.client.sync.conflict.LastWriteWinsResolver
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.space.ENTRY_COLLECTION
import app.logdate.client.sync.space.FakeDataHome
import app.logdate.client.sync.space.SpaceDevice
import app.logdate.client.sync.space.TRANSCRIPT_COLLECTION
import app.logdate.client.sync.space.open
import app.logdate.client.sync.space.transcriptKey
import app.logdate.client.sync.space.uploadedAudioRef
import app.logdate.client.sync.space.withSyncVersion
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** The note and transcript mappers registered as the notes kind, syncing through a personal space. */
class NoteSpaceKindTest {
    private val created = Instant.parse("2026-10-01T09:00:00Z")

    private fun device(home: FakeDataHome) = SpaceDevice(home, noteKindOf = { noteSpaceKind(it, LastWriteWinsResolver()) })

    private val location = NoteLocation(coordinates = NoteCoordinates(latitude = -33.86882, longitude = 151.209295, accuracy = 4.75f))

    private fun transcribedRecording(id: Uuid = Uuid.random()) =
        JournalNote.Audio(
            uid = id,
            mediaRef = uploadedAudioRef(id),
            durationMs = 4_000,
            caption = "Morning thoughts",
            creationTimestamp = created,
            lastUpdated = created,
            location = location,
            timeZoneId = "Australia/Sydney",
            transcript =
                TranscriptDocument(
                    revision = 3,
                    status = TranscriptDocumentStatus.FINAL,
                    language = "en-AU",
                    segments = listOf(TranscriptSegment("s1", "Good morning", 0, 900, confidence = 0.9f, isFinal = true)),
                    soundTags = listOf(TranscriptSoundTag("birdsong", startMs = 1_000, durationMs = 2_000)),
                    createdAt = created,
                    updatedAt = created + 1.hours,
                ),
        )

    @Test
    fun `a transcribed recording reaches another device with everything the note holds`() =
        runTest {
            val home = FakeDataHome()
            val phone = device(home)
            val tablet = device(home)
            val note = transcribedRecording()
            phone.saveNote(note)

            val uploaded = phone.manager.uploadPendingChanges()
            val downloaded = tablet.manager.downloadRemoteChanges()

            assertTrue(uploaded.success, uploaded.errors.toString())
            assertTrue(downloaded.success, downloaded.errors.toString())
            assertEquals(note.withSyncVersion(2), tablet.note(note.uid))
            assertNotNull(home.open(ENTRY_COLLECTION, note.uid.toString()))
            assertNotNull(home.open(TRANSCRIPT_COLLECTION, transcriptKey(note.uid)))
        }

    @Test
    fun `a text note and a recording without a transcript each reach another device as one record`() =
        runTest {
            val home = FakeDataHome()
            val phone = device(home)
            val tablet = device(home)
            val text =
                JournalNote.Text(creationTimestamp = created, lastUpdated = created, content = "By the river", location = location)
            val bare = transcribedRecording().copy(transcript = null)
            phone.saveNote(text)
            phone.saveNote(bare)

            phone.manager.uploadPendingChanges()
            tablet.manager.downloadRemoteChanges()

            assertEquals(text.copy(syncVersion = tablet.note(text.uid)!!.syncVersion), tablet.note(text.uid))
            assertEquals(bare.copy(syncVersion = tablet.note(bare.uid)!!.syncVersion), tablet.note(bare.uid))
            assertNull(home.open(TRANSCRIPT_COLLECTION, transcriptKey(bare.uid)))
        }

    @Test
    fun `an edited transcript reaches another device that already has the recording`() =
        runTest {
            val home = FakeDataHome()
            val phone = device(home)
            val tablet = device(home)
            val note = transcribedRecording()
            phone.saveNote(note)
            phone.manager.uploadPendingChanges()
            tablet.manager.downloadRemoteChanges()
            val revised = note.transcript!!.copy(revision = 4, updatedAt = created + 2.hours)

            phone.saveNote(note.copy(transcript = revised, lastUpdated = created + 2.hours), PendingOperation.UPDATE)
            phone.manager.uploadPendingChanges()
            tablet.manager.downloadRemoteChanges()

            assertEquals(revised, (tablet.note(note.uid) as JournalNote.Audio).transcript)
        }

    @Test
    fun `deleting a recording deletes its entry and its transcript from the space`() =
        runTest {
            val home = FakeDataHome()
            val phone = device(home)
            val tablet = device(home)
            val note = transcribedRecording()
            phone.saveNote(note)
            phone.manager.uploadPendingChanges()
            tablet.manager.downloadRemoteChanges()

            phone.deleteNote(note.uid)
            val result = phone.manager.uploadPendingChanges()
            tablet.manager.downloadRemoteChanges()

            assertTrue(result.success, result.errors.toString())
            assertTrue(home.liveRkeys().isEmpty())
            assertNull(tablet.note(note.uid))
        }
}
