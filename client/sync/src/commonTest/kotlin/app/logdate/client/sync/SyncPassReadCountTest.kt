package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.sync.cloud.ContentChange
import app.logdate.client.sync.cloud.ContentChangesResponse
import app.logdate.client.sync.cloud.ContentDeletion
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.FakeJournalRepository
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** A sync pass reads only the records it uploads or applies, however large the local journal is. */
class SyncPassReadCountTest {
    @Test
    fun `downloading one remote change never reads the whole local journal`() =
        runTest {
            val notes = CountingNotesRepository().withLocalNotes(LARGE_JOURNAL)
            val local = notes.addTestNote("Deleted elsewhere")
            val remote = remoteTextChange()
            val deletion = ContentDeletion(local.uid.toString(), Clock.System.now().toEpochMilliseconds())
            val api =
                fakeCloudApiClient {
                    getContentChangesResponse =
                        Result.success(ContentChangesResponse(listOf(remote), listOf(deletion), Clock.System.now().toEpochMilliseconds()))
                }

            val result =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(api),
                    journalNotesRepository = notes,
                ).downloadRemoteChanges()

            assertTrue(result.success, "${result.errors}")
            assertEquals(0, notes.fullReads, "A download must not load every local note")
            assertTrue(notes.lookups <= MAX_LOOKUPS_PER_RECORD * 2, "Expected per-record lookups, got ${notes.lookups}")
        }

    @Test
    fun `uploading one edited note never reads the whole local journal`() =
        runTest {
            val notes = CountingNotesRepository().withLocalNotes(LARGE_JOURNAL)
            val edited = notes.addTestNote("Edited")
            val metadata = fakeSyncMetadataService().apply { addPending(edited.uid, EntityType.NOTE) }

            val result =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(fakeCloudApiClient()),
                    journalNotesRepository = notes,
                    syncMetadataService = metadata,
                ).uploadPendingChanges()

            assertTrue(result.success, "${result.errors}")
            assertEquals(0, notes.fullReads, "An upload must not load every local note")
            assertTrue(notes.lookups <= MAX_LOOKUPS_PER_RECORD, "Expected per-record lookups, got ${notes.lookups}")
        }

    @Test
    fun `uploading one edited journal never reads every local journal`() =
        runTest {
            val journals = CountingJournalRepository()
            repeat(LARGE_JOURNAL) { journals.create(journal("Journal $it")) }
            val edited = journal("Edited").also { journals.create(it) }
            val metadata = fakeSyncMetadataService().apply { addPending(edited.id, EntityType.JOURNAL) }
            journals.fullReads = 0

            val result =
                testDefaultSyncManager(
                    cloudJournalDataSource = DefaultCloudJournalDataSource(fakeCloudApiClient()),
                    journalRepository = journals,
                    syncMetadataService = metadata,
                ).uploadPendingChanges()

            assertTrue(result.success, "${result.errors}")
            assertEquals(0, journals.fullReads, "An upload must not load every local journal")
        }

    private class CountingNotesRepository(
        private val delegate: FakeJournalNotesRepository = FakeJournalNotesRepository(),
    ) : SyncableJournalNotesRepository by delegate {
        var fullReads = 0
        var lookups = 0

        override val allNotesObserved: Flow<List<JournalNote>>
            get() = delegate.allNotesObserved.onStart { fullReads++ }

        override suspend fun getNoteById(noteId: Uuid): JournalNote? {
            lookups++
            return delegate.getNoteById(noteId)
        }

        fun addTestNote(content: String) = delegate.addTestNote(content)

        fun withLocalNotes(count: Int) = apply { repeat(count) { delegate.addTestNote("Local note $it") } }
    }

    private class CountingJournalRepository(
        private val delegate: FakeJournalRepository = FakeJournalRepository(),
    ) : JournalRepository by delegate {
        var fullReads = 0

        override val allJournalsObserved: Flow<List<Journal>>
            get() = delegate.allJournalsObserved.onStart { fullReads++ }
    }

    private fun journal(title: String) =
        Journal(
            id = Uuid.random(),
            title = title,
            created = Clock.System.now(),
            lastUpdated = Clock.System.now(),
        )

    private fun remoteTextChange(): ContentChange {
        val now = Clock.System.now().toEpochMilliseconds()
        return ContentChange(
            id = Uuid.random().toString(),
            type = "TEXT",
            content = "Remote note",
            mediaUri = null,
            createdAt = now,
            lastUpdated = now,
            serverVersion = 1,
        )
    }

    private companion object {
        const val LARGE_JOURNAL = 1_000
        const val MAX_LOOKUPS_PER_RECORD = 4
    }
}
