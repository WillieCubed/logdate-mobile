package app.logdate.client.sync.recovery

import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.cloud.CloudDraftDataSource
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudDraftDataSource
import app.logdate.client.sync.cloud.DraftSyncResult
import app.logdate.client.sync.cloud.SyncedDraft
import app.logdate.client.sync.diagnostics.DiagnosticSource
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.test.FakeJournalContentRepository
import app.logdate.client.sync.test.FakeJournalRepository
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import app.logdate.shared.model.sync.AssociationChange
import app.logdate.shared.model.sync.AssociationChangesResponse
import app.logdate.shared.model.sync.DeviceId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ManualDownloadDiagnosticInstrumentationTest {
    @Test
    fun `draft apply reports durable operation only after page transaction and acknowledgment`() =
        runTest {
            val db = DownloadInboxTest.Database()
            var transactionDepth = 0
            val transactions =
                object : SyncTransactionManager {
                    override suspend fun <T> withTransaction(block: suspend () -> T): T {
                        transactionDepth++
                        try {
                            return db.withTransaction(block)
                        } finally {
                            transactionDepth--
                        }
                    }
                }
            val selected = DownloadScope("owner", "origin")
            val source = DiagnosticSource(UploadScope("owner", "origin"), "epoch")
            val events = mutableListOf<Pair<SyncDiagnosticEvent, Int>>()
            val inbox =
                DownloadInbox(db, transactions, { selected }, { 1L }, { source }, { event, captured ->
                    assertTrue(captured === source)
                    events += event to transactionDepth
                })
            val id = Uuid.random()
            inbox.stage("DRAFT", 4, listOf(WireDownload(id.toString(), 4, false, "encrypted wire")))
            val operation = db.rows.single().operationId
            val timestamp = Instant.fromEpochMilliseconds(1)
            val draft = EditorDraft(id = id, createdAt = timestamp, lastModifiedAt = timestamp)
            val api = fakeCloudApiClient()
            val remote =
                object : CloudDraftDataSource by DefaultCloudDraftDataSource(api) {
                    override suspend fun getDraftChanges(
                        accessToken: String,
                        since: Instant,
                        limit: Int?,
                    ) = Result.success(
                        DraftSyncResult(
                            changes = listOf(SyncedDraft(id, "", DeviceId("device"), timestamp, timestamp, 4, richDraft = draft)),
                            deletions = emptyList(),
                            lastSyncTimestamp = Instant.fromEpochMilliseconds(4),
                        ),
                    )
                }
            val local = FakeJournalRepository()
            val result =
                testDefaultSyncManager(
                    cloudDraftDataSource = remote,
                    journalRepository = local,
                    downloadInbox = inbox,
                    transactionManager = transactions,
                    syncScope = backgroundScope,
                ).syncDrafts()
            assertTrue(result.success)
            assertNotNull(local.getDraft(id))
            val applied = events.single { it.first.phase == DiagnosticPhase.APPLY && it.first.outcome == DiagnosticOutcome.SUCCEEDED }
            assertEquals(operation, applied.first.operationId)
            assertNotNull(applied.first.attemptId)
            assertEquals(0, applied.second, "draft was reported applied before the page transaction committed")
        }

    @Test
    fun `association apply reports durable operation after transaction and sink errors are isolated`() =
        runTest {
            val db = DownloadInboxTest.Database()
            var transactionDepth = 0
            val transactions =
                object : SyncTransactionManager {
                    override suspend fun <T> withTransaction(block: suspend () -> T): T {
                        transactionDepth++
                        try {
                            return db.withTransaction(block)
                        } finally {
                            transactionDepth--
                        }
                    }
                }
            val selected = DownloadScope("owner", "origin")
            val source = DiagnosticSource(UploadScope("owner", "origin"), "epoch")
            val laterSource = DiagnosticSource(UploadScope("owner", "origin"), "later-epoch")
            var currentSource = source
            val events = mutableListOf<Triple<SyncDiagnosticEvent, Int, DiagnosticSource?>>()
            val inbox =
                DownloadInbox(db, transactions, { selected }, { 1L }, { currentSource }, { event, captured ->
                    events += Triple(event, transactionDepth, captured)
                })
            val journal = Uuid.random()
            val content = Uuid.random()
            val key = AssociationPendingKey(journal, content).toPendingId()
            inbox.stage("ASSOCIATION", 4, listOf(WireDownload(key, 4, false, "encrypted wire")))
            val operation = db.rows.single().operationId
            val backing = FakeJournalContentRepository()
            val applied = mutableListOf<Uuid>()
            val repository =
                object : JournalContentRepository by backing {
                    override suspend fun addContentToJournal(
                        contentId: Uuid,
                        journalId: Uuid,
                    ) {
                        currentSource = laterSource
                        applied += contentId
                    }
                }
            val api =
                fakeCloudApiClient {
                    getAssociationChangesResponse =
                        Result.success(
                            AssociationChangesResponse(
                                listOf(AssociationChange(journal.toString(), content.toString(), 1, 4)),
                                emptyList(),
                                4,
                            ),
                        )
                }
            val result =
                testDefaultSyncManager(
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(api),
                    journalContentRepository = repository,
                    downloadInbox = inbox,
                    transactionManager = transactions,
                    syncScope = backgroundScope,
                ).syncAssociations()
            assertTrue(result.success)
            assertEquals(listOf(content), applied)
            val success = events.single { it.first.phase == DiagnosticPhase.APPLY && it.first.outcome == DiagnosticOutcome.SUCCEEDED }
            assertEquals(operation, success.first.operationId)
            assertNotNull(success.first.attemptId)
            assertEquals(0, success.second)
            assertTrue(success.third === source, "apply was reattributed after consent changed during the transaction")

            val throwingInbox = DownloadInbox(db, transactions, { selected }, { 1L }, { source }, { _, _ -> error("sink unavailable") })
            throwingInbox.stage("ASSOCIATION", 5, listOf(WireDownload(key, 5, false, "new encrypted wire")))
            assertEquals(5L, throwingInbox.cursor("ASSOCIATION"))
        }
}
