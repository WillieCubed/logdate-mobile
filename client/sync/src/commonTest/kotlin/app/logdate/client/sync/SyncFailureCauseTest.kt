package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.cloud.ContentChange
import app.logdate.client.sync.cloud.ContentChangesResponse
import app.logdate.client.sync.cloud.ContentDeletion
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.test.FailingJournalNotesRepository
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** A failed sync keeps the exception that caused it, so the failure can be diagnosed and acted on. */
class SyncFailureCauseTest {
    @Test
    fun `a failed remote fetch keeps the request failure as its cause`() =
        runTest {
            val failure = IllegalStateException("fetch failed")
            val api = fakeCloudApiClient { getContentChangesResponse = Result.failure(failure) }

            val result = testDefaultSyncManager(cloudContentDataSource = DefaultCloudContentDataSource(api)).downloadRemoteChanges()

            assertFalse(result.success)
            assertTrue(result.causes().any { it === failure }, "Expected the fetch failure as a cause, got ${result.errors}")
        }

    @Test
    fun `a remote change that fails to apply keeps the storage failure as its cause`() =
        runTest {
            val api = fakeCloudApiClient { getContentChangesResponse = Result.success(changesPage(changes = listOf(remoteTextChange()))) }

            val result =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(api),
                    journalNotesRepository = FailingJournalNotesRepository(),
                ).downloadRemoteChanges()

            assertFalse(result.success)
            assertTrue(
                result.causes().any { it is IllegalStateException && it.message == "Simulated note create failure" },
                "Expected the apply failure as a cause, got ${result.errors}",
            )
        }

    @Test
    fun `a remote deletion that fails to apply keeps the storage failure as its cause`() =
        runTest {
            val failure = IllegalStateException("delete failed")
            val notes = FakeJournalNotesRepository()
            val local = notes.addTestNote("Local note")
            val deletion = ContentDeletion(local.uid.toString(), Clock.System.now().toEpochMilliseconds())
            val api = fakeCloudApiClient { getContentChangesResponse = Result.success(changesPage(deletions = listOf(deletion))) }

            val result =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(api),
                    journalNotesRepository = DeleteFailingNotesRepository(notes, failure),
                ).downloadRemoteChanges()

            assertFalse(result.success)
            assertTrue(result.causes().any { it === failure }, "Expected the deletion failure as a cause, got ${result.errors}")
        }

    @Test
    fun `a failed upload keeps the request failure as its cause`() =
        runTest {
            val failure = IllegalStateException("upload failed")
            val api = fakeCloudApiClient { updateContentResponse = Result.failure(failure) }
            val notes = FakeJournalNotesRepository()
            val note = notes.addTestNote("Edited note")
            val metadata = fakeSyncMetadataService().apply { addPending(note.uid, EntityType.NOTE) }

            val result =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(api),
                    journalNotesRepository = notes,
                    syncMetadataService = metadata,
                ).uploadPendingChanges()

            assertFalse(result.success)
            assertTrue(result.causes().any { it === failure }, "Expected the upload failure as a cause, got ${result.errors}")
        }

    private class DeleteFailingNotesRepository(
        delegate: FakeJournalNotesRepository,
        private val failure: Exception,
    ) : JournalNotesRepository by delegate {
        override suspend fun removeById(noteId: Uuid) = throw failure
    }

    private fun SyncResult.causes(): List<Throwable> = errors.flatMap { error -> generateSequence(error.cause) { it.cause }.toList() }

    private fun changesPage(
        changes: List<ContentChange> = emptyList(),
        deletions: List<ContentDeletion> = emptyList(),
    ) = ContentChangesResponse(changes, deletions, Clock.System.now().toEpochMilliseconds())

    private fun remoteTextChange(): ContentChange {
        val note =
            JournalNote.Text(
                uid = Uuid.random(),
                creationTimestamp = Clock.System.now(),
                lastUpdated = Clock.System.now(),
                content = "Remote note",
            )
        return ContentChange(
            id = note.uid.toString(),
            type = "TEXT",
            content = note.content,
            mediaUri = null,
            createdAt = note.creationTimestamp.toEpochMilliseconds(),
            lastUpdated = note.lastUpdated.toEpochMilliseconds(),
            serverVersion = 1,
        )
    }
}
