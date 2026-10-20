package app.logdate.client.data.journals

import app.logdate.client.data.fakes.FakeDraftRepository
import app.logdate.client.data.fakes.FakeJournalDao
import app.logdate.client.data.fakes.FakeRemoteJournalDataSource
import app.logdate.client.data.fakes.FakeSyncMetadataService
import app.logdate.client.repository.journals.DraftRepository
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class UnclaimedJournalDraftTest {
    @Test
    fun `journal sync can distinguish an unclaimed local draft from deleted data`() =
        runTest {
            val retained = Uuid.random()
            val drafts =
                object : DraftRepository by FakeDraftRepository() {
                    override suspend fun hasUnclaimedDraft(id: Uuid): Boolean = id == retained
                }
            val repository =
                OfflineFirstJournalRepository(
                    journalDao = FakeJournalDao(),
                    remoteDataSource = FakeRemoteJournalDataSource(),
                    draftRepository = drafts,
                    syncMetadataService = FakeSyncMetadataService(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                    externalScope = backgroundScope,
                )

            assertTrue(repository.getAllDraftsForSync().isEmpty())
            assertTrue(repository.hasUnclaimedDraft(retained))
            assertFalse(repository.hasUnclaimedDraft(Uuid.random()))
        }
}
