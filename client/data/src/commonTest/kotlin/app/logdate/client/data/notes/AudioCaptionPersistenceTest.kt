package app.logdate.client.data.notes

import app.logdate.client.data.fakes.FakeAudioNoteDao
import app.logdate.client.data.fakes.FakeImageNoteDao
import app.logdate.client.data.fakes.FakeJournalContentDao
import app.logdate.client.data.fakes.FakeJournalRepository
import app.logdate.client.data.fakes.FakeMediaCaptionDao
import app.logdate.client.data.fakes.FakeSyncMetadataService
import app.logdate.client.data.fakes.FakeTextNoteDao
import app.logdate.client.data.fakes.FakeVideoNoteDao
import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class AudioCaptionPersistenceTest {
    private val captions = FakeMediaCaptionDao()
    private val repository =
        OfflineFirstJournalNotesRepository(
            textNoteDao = FakeTextNoteDao(),
            imageNoteDao = FakeImageNoteDao(),
            audioNoteDao = FakeAudioNoteDao(),
            videoNoteDao = FakeVideoNoteDao(),
            journalContentDao = FakeJournalContentDao(),
            journalRepository = FakeJournalRepository(),
            mediaCaptionDao = captions,
            syncMetadataService = FakeSyncMetadataService(),
        )
    private val recordedAt = Instant.parse("2026-01-02T03:04:05Z")
    private val note =
        JournalNote.Audio(
            mediaRef = "/audio/station.m4a",
            creationTimestamp = recordedAt,
            lastUpdated = recordedAt,
            caption = "At the station",
        )

    @Test
    fun `published audio captions survive repository reads`() =
        runTest {
            repository.create(note)

            assertEquals("At the station", (repository.getNoteById(note.uid) as JournalNote.Audio).caption)
            assertEquals("At the station", (repository.allNotesObserved.first().single() as JournalNote.Audio).caption)
            assertEquals(
                "At the station",
                repository
                    .observeRecentAudioNotes(1)
                    .first()
                    .single()
                    .caption,
            )
            assertEquals(
                "At the station",
                (repository.getNotesBefore(Instant.parse("2026-01-03T00:00:00Z"), 1).single() as JournalNote.Audio).caption,
            )
        }

    @Test
    fun `synced audio captions can be replaced and cleared`() =
        runTest {
            repository.createFromSync(note)
            assertEquals("At the station", (repository.getNoteById(note.uid) as JournalNote.Audio).caption)

            repository.createFromSync(note.copy(caption = "Back at home"))
            assertEquals("Back at home", (repository.getNoteById(note.uid) as JournalNote.Audio).caption)

            repository.createFromSync(note.copy(caption = ""))
            assertEquals("", (repository.getNoteById(note.uid) as JournalNote.Audio).caption)
        }

    @Test
    fun `deleting audio removes its caption sidecar`() =
        runTest {
            repository.create(note)
            assertEquals("At the station", captions.getCaption(note.uid)?.caption)

            repository.remove(note)

            assertNull(captions.getCaption(note.uid))
            assertNull(repository.getNoteById(note.uid))
        }
}
