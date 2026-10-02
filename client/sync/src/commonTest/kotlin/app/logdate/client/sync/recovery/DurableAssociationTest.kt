package app.logdate.client.sync.recovery

import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.test.FakeJournalContentRepository
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.sync.AssociationChange
import app.logdate.shared.model.sync.AssociationChangesResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class DurableAssociationTest {
    @Test
    fun `failed relationship stays queued while unrelated relationship settles and retries after restart`() =
        runTest {
            val db = DownloadInboxTest.Database()
            var now = 1L
            val scope = { DownloadScope("account", "server") }
            val journal = Uuid.random()
            val bad = Uuid.random()
            val good = Uuid.random()
            val applied = mutableSetOf<Uuid>()
            var fail = true
            val repository =
                object : JournalContentRepository by FakeJournalContentRepository() {
                    override suspend fun addContentToJournal(
                        contentId: Uuid,
                        journalId: Uuid,
                    ) {
                        if (contentId == bad && fail) error("storage unavailable")
                        applied += contentId
                    }
                }
            val api =
                fakeCloudApiClient {
                    getAssociationChangesResponse =
                        Result.success(
                            AssociationChangesResponse(
                                listOf(bad, good).mapIndexed { index, id ->
                                    AssociationChange(
                                        journal.toString(),
                                        id.toString(),
                                        1,
                                        index + 1L,
                                    )
                                },
                                emptyList(),
                                2,
                            ),
                        )
                }

            suspend fun execute(inbox: DownloadInbox) =
                testDefaultSyncManager(
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(DurableCloudApiClient(api, inbox)),
                    journalContentRepository = repository,
                    downloadInbox = inbox,
                    transactionManager = db,
                    syncScope = backgroundScope,
                ).downloadRemoteChanges()
            val inbox = DownloadInbox(db, db, scope, { now })
            assertFalse(execute(inbox).success)
            assertEquals(setOf(good), applied)
            assertEquals(1, inbox.count())
            now += 10000
            fail = false
            api.getAssociationChangesResponse = Result.success(AssociationChangesResponse(emptyList(), emptyList(), 2))
            val restarted = DownloadInbox(db, db, scope, { now })
            assertTrue(execute(restarted).success)
            assertEquals(setOf(good, bad), applied)
            assertEquals(0, restarted.count())
        }
}
