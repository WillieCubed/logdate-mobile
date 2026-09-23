package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.InMemorySyncDeadLetterStore
import app.logdate.client.sync.test.InMemorySyncRetryScheduleStore
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

/**
 * An entry that failed before waits out a backoff that can reach hours. "Back up now" used to leave
 * that wait in place, so the upload it asked for skipped every waiting entry and reported success
 * with nothing sent.
 */
class ManualBackupReleasesBackoffTest {
    @Test
    fun `back up now attempts entries that were waiting out a retry backoff`() =
        runTest {
            val api = fakeCloudApiClient()
            val notes = FakeJournalNotesRepository()
            val waiting = Uuid.random()
            notes.create(
                JournalNote.Text(uid = waiting, creationTimestamp = Clock.System.now(), lastUpdated = Clock.System.now(), content = "x"),
            )
            val metadata = fakeSyncMetadataService()
            metadata.clearPending()
            metadata.enqueuePending(waiting.toString(), EntityType.NOTE, PendingOperation.CREATE)
            val schedule = InMemorySyncRetryScheduleStore()
            schedule.setNextAttemptAt(EntityType.NOTE, waiting.toString(), (Clock.System.now() + 4.hours).toEpochMilliseconds())
            val manager =
                testDefaultSyncManager(
                    cloudContentDataSource =
                        app.logdate.client.sync.cloud
                            .DefaultCloudContentDataSource(api),
                    journalNotesRepository = notes,
                    syncMetadataService = metadata,
                    retryScheduleStore = schedule,
                )

            manager.releaseUploadBackoff()
            manager.uploadPendingChanges()

            assertEquals(listOf(waiting.toString()), api.uploadContentCalls.map { it.second.id })
        }

    @Test
    fun `entries set aside in sync issues stay parked`() =
        runTest {
            val metadata = fakeSyncMetadataService()
            metadata.clearPending()
            val parked = Uuid.random().toString()
            metadata.enqueuePending(parked, EntityType.NOTE, PendingOperation.CREATE)
            val schedule = InMemorySyncRetryScheduleStore()
            val parkedUntil = (Clock.System.now() + 24.hours).toEpochMilliseconds()
            schedule.setNextAttemptAt(EntityType.NOTE, parked, parkedUntil)
            val deadLetters = InMemorySyncDeadLetterStore()
            deadLetters.add(
                SyncDeadLetterRecord(
                    id = "NOTE:$parked",
                    entityType = "NOTE",
                    entityId = parked,
                    operation = "CREATE",
                    retryCount = 9,
                    lastError = "file no longer exists",
                    failedAt = 0L,
                ),
            )
            val manager =
                testDefaultSyncManager(
                    syncMetadataService = metadata,
                    retryScheduleStore = schedule,
                    deadLetterStore = deadLetters,
                )

            manager.releaseUploadBackoff()

            assertEquals(parkedUntil, schedule.nextAttemptAt(EntityType.NOTE, parked))
        }
}
