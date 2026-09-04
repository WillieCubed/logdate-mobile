package app.logdate.client.sync

import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaPayload
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.test.InMemorySyncDeadLetterStore
import app.logdate.client.sync.test.InMemorySyncRetryScheduleStore
import app.logdate.client.sync.test.fakeCloudApiClient
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
 * A recording whose file has been deleted can never upload, so retrying it forever is not a
 * recovery strategy — it is a queue that never drains. But a single failed existence check is not
 * proof the file is gone for good either: [app.logdate.client.media.MediaManager.exists] answers
 * `false` for a provider that merely refused to answer, not only for bytes truly missing. Requiring
 * two independent, consecutive checks before giving up trades a little patience for far fewer
 * wrongly-permanent failures.
 */
class MissingMediaDeadLetterTest {
    private class MissingFileMediaManager : InMemoryMediaManager() {
        override suspend fun readMedia(uri: String): MediaPayload =
            throw IllegalStateException("$uri: open failed: ENOENT (No such file or directory)")

        override suspend fun exists(mediaId: String): Boolean = false
    }

    /**
     * Reads the local file fine the first time (so its bytes are proven to still be on disk),
     * then reports ENOENT with `exists() == false` on every read after that -- simulating a file
     * that genuinely disappears between two sync attempts, rather than one that was never there.
     */
    private class FlakyThenMissingFileMediaManager : InMemoryMediaManager() {
        private var readAttempts = 0

        override suspend fun readMedia(uri: String): MediaPayload {
            readAttempts += 1
            return if (readAttempts == 1) {
                super.readMedia(uri)
            } else {
                throw IllegalStateException("$uri: open failed: ENOENT (No such file or directory)")
            }
        }

        override suspend fun exists(mediaId: String): Boolean = false
    }

    @Test
    fun `a note whose media file is gone is retried rather than dead-lettered on the first attempt`() =
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

            assertTrue(
                deadLetters.list().isEmpty(),
                "a single failed existence check should not be treated as permanent",
            )
            assertEquals(
                1,
                metadata.getPendingUploads(EntityType.NOTE).single().retryCount,
                "the attempt should still count, so a second miss can be recognized",
            )
        }

    @Test
    fun `a note whose media file is still gone on a second attempt dead-letters`() =
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
            val retryScheduleStore = InMemorySyncRetryScheduleStore()
            val syncManager =
                testDefaultSyncManager(
                    mediaManager = MissingFileMediaManager(),
                    journalNotesRepository = notesRepository,
                    deadLetterStore = deadLetters,
                    syncMetadataService = metadata,
                    retryScheduleStore = retryScheduleStore,
                )

            metadata.addPending(note.uid, EntityType.NOTE)
            syncManager.fullSync()
            // The first miss scheduled a backoff delay; clear it so the second attempt below is
            // exercising "still gone next time", not "gone before its backoff even elapsed".
            retryScheduleStore.clear(EntityType.NOTE, note.uid.toString())
            syncManager.fullSync()

            val records = deadLetters.list()
            assertEquals(1, records.size, "a second consecutive miss should be set aside, not retried forever")
            assertEquals(note.uid.toString(), records.single().entityId)
            assertTrue(
                records.single().lastError.contains("ENOENT"),
                "the record should say why it was set aside: ${records.single().lastError}",
            )
        }

    @Test
    fun `an unrelated prior failure does not cause a first missing-media miss to dead-letter`() =
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
            val retryScheduleStore = InMemorySyncRetryScheduleStore()
            val mediaManager = FlakyThenMissingFileMediaManager()
            // The file read succeeds both times (see the manager above), but the *server* upload
            // of that media fails on the first attempt -- an unrelated, non-missing-media reason
            // to fail this entity, exactly like a transient network or server error would.
            val cloudApiClient =
                fakeCloudApiClient {
                    uploadMediaResponse = Result.failure(IllegalStateException("Transient network error"))
                }
            val syncManager =
                testDefaultSyncManager(
                    mediaManager = mediaManager,
                    journalNotesRepository = notesRepository,
                    deadLetterStore = deadLetters,
                    syncMetadataService = metadata,
                    retryScheduleStore = retryScheduleStore,
                    cloudMediaDataSource = DefaultCloudMediaDataSource(cloudApiClient),
                )

            metadata.addPending(note.uid, EntityType.NOTE)
            syncManager.fullSync()

            assertTrue(
                deadLetters.list().isEmpty(),
                "the first failure was an unrelated server error, not a missing-media miss",
            )
            assertEquals(
                1,
                metadata.getPendingUploads(EntityType.NOTE).single().retryCount,
                "the unrelated failure should still count toward the generic retry budget",
            )

            // The first miss scheduled a backoff delay; clear it so the second attempt below
            // actually runs instead of being skipped by shouldAttempt().
            retryScheduleStore.clear(EntityType.NOTE, note.uid.toString())
            syncManager.fullSync()

            assertTrue(
                deadLetters.list().isEmpty(),
                "a single missing-media miss should not dead-letter just because retryCount " +
                    "happened to already be >= 1 from an earlier, unrelated failure",
            )
        }
}
