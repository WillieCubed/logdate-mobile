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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class MediaCaptionSyncTest {
    private val repository =
        OfflineFirstJournalNotesRepository(
            textNoteDao = FakeTextNoteDao(),
            imageNoteDao = FakeImageNoteDao(),
            audioNoteDao = FakeAudioNoteDao(),
            videoNoteDao = FakeVideoNoteDao(),
            journalContentDao = FakeJournalContentDao(),
            journalRepository = FakeJournalRepository(),
            mediaCaptionDao = FakeMediaCaptionDao(),
            syncMetadataService = FakeSyncMetadataService(),
        )
    private val at = Instant.fromEpochMilliseconds(1)

    @Test fun `removing a photo caption on the phone clears its synced copy`() =
        runTest {
            val note = JournalNote.Image(creationTimestamp = at, lastUpdated = at, mediaRef = "photo.jpg", caption = "old caption")
            repository.createFromSync(note)
            repository.createFromSync(note.copy(caption = ""))
            assertEquals("", (repository.getNoteById(note.uid) as JournalNote.Image).caption)
        }

    @Test fun `removing a video caption on the phone clears its synced copy`() =
        runTest {
            val note = JournalNote.Video(creationTimestamp = at, lastUpdated = at, mediaRef = "video.mp4", caption = "old caption")
            repository.createFromSync(note)
            repository.createFromSync(note.copy(caption = ""))
            assertEquals("", (repository.getNoteById(note.uid) as JournalNote.Video).caption)
        }
}
