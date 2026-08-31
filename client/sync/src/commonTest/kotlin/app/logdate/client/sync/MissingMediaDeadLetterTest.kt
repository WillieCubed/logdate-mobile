package app.logdate.client.sync

import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaPayload
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.test.InMemorySyncDeadLetterStore
import app.logdate.client.sync.test.fakeJournalNotesRepository
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * A recording whose file has been deleted can never upload, so retrying it is not a recovery
 * strategy — it is a queue that never drains. One such note kept every later change from syncing
 * and showed up only as a "waiting" count that never went down.
 */
class MissingMediaDeadLetterTest {
    private class MissingFileMediaManager : InMemoryMediaManager() {
        override suspend fun readMedia(uri: String): MediaPayload =
            throw IllegalStateException("$uri: open failed: ENOENT (No such file or directory)")

        override suspend fun exists(mediaId: String): Boolean = false
    }

    @Test
    fun `a note whose media file is gone dead-letters on the first attempt`() =
        runTest {
            val notesRepository = fakeJournalNotesRepository()
            val note =
                JournalNote.Audio(
                    mediaRef = "/files/audio_notes/recording.m4a",
                    uid = Uuid.random(),
                    creationTimestamp = Clock.System.now(),
                    lastUpdated = Clock.System.now(),
                )
            notesRepository.create(note)

            val metadata = fakeSyncMetadataService()
            val deadLetters = InMemorySyncDeadLetterStore()
            val syncManager =
                testDefaultSyncManager(
                    mediaManager = MissingFileMediaManager(),
                    journalNotesRepository = notesRepository,
                    deadLetterStore = deadLetters,
                    syncMetadataService = metadata,
                )

            metadata.addPending(note.uid, EntityType.NOTE)
            syncManager.fullSync()

            val records = deadLetters.list()
            assertEquals(1, records.size, "the unreadable note should be set aside, not retried")
            assertEquals(note.uid.toString(), records.single().entityId)
            assertTrue(
                records.single().lastError.contains("ENOENT"),
                "the record should say why it was set aside: ${records.single().lastError}",
            )
        }
}
