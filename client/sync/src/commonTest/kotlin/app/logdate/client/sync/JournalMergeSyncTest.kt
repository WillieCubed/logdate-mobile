package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalMergeOperation
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.CloudRequestLocation
import app.logdate.client.sync.cloud.CloudRequestLocationProvider
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.client.sync.test.FakeJournalRepository
import app.logdate.client.sync.test.fakeAccountRepository
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.shared.model.sync.JournalMergeRequest
import app.logdate.shared.model.sync.JournalMergeResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class JournalMergeSyncTest {
    private val origin = app.logdate.shared.config.DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL

    private fun operation(
        source: Uuid = Uuid.random(),
        destination: Uuid = Uuid.random(),
    ) = JournalMergeOperation(
        Uuid.random(),
        source,
        destination,
        setOf(Uuid.random()),
        JournalMergeScope("test-account-id", origin),
        "From",
        "Keep",
    )

    private val accounts =
        object : CloudAccountRepository by fakeAccountRepository(), CloudRequestLocationProvider {
            override fun captureLocation() = CloudRequestLocation(origin, "$origin/api/v1")

            override fun isCurrentOrigin(origin: String) = origin == this@JournalMergeSyncTest.origin
        }

    @Test
    fun `offline chain uploads destination merge first while preserving stable requests`() =
        runTest {
            val a = Uuid.random()
            val b = Uuid.random()
            val c = Uuid.random()
            val ops = mutableListOf(operation(a, b), operation(b, c))
            val original = ops.toList()
            val metadata = fakeSyncMetadataService()
            ops.forEach { metadata.enqueuePending(it.operationId.toString(), EntityType.JOURNAL_MERGE, PendingOperation.CREATE) }
            val requests = mutableListOf<Pair<String, JournalMergeRequest>>()
            val api =
                object : FakeCloudApiClient() {
                    override suspend fun mergeJournals(
                        accessToken: String,
                        sourceId: String,
                        request: JournalMergeRequest,
                    ): Result<JournalMergeResponse> {
                        requests += sourceId to request
                        return Result.success(JournalMergeResponse(request.operationId, sourceId, c.toString()))
                    }
                }
            val repo =
                object : JournalRepository by FakeJournalRepository() {
                    override suspend fun pendingJournalMerges() = ops.toList()

                    override suspend fun markJournalMergeSynced(operation: JournalMergeOperation) {
                        ops.remove(operation)
                    }

                    override suspend fun resolveJournalId(journalId: Uuid) = if (journalId == a || journalId == b) c else journalId
                }
            val manager =
                testDefaultSyncManager(
                    journalRepository = repo,
                    cloudApiClient = api,
                    cloudAccountRepository = accounts,
                    sessionStorage = fakeSessionStorage(),
                    syncMetadataService = metadata,
                    syncScope = backgroundScope,
                )
            assertTrue(manager.uploadPendingChanges().success)
            assertEquals(listOf(b.toString(), a.toString()), requests.map { it.first })
            assertEquals(b.toString(), requests.last().second.destinationId)
            assertEquals(original.map { it.operationId.toString() }.toSet(), requests.map { it.second.operationId }.toSet())
            assertTrue(ops.isEmpty())
            assertTrue(metadata.getPendingUploads(EntityType.JOURNAL_MERGE).isEmpty())
        }

    @Test
    fun `deleted destination retains operation and exposes recovery`() =
        runTest {
            val op = operation()
            val metadata = fakeSyncMetadataService()
            metadata.enqueuePending(op.operationId.toString(), EntityType.JOURNAL_MERGE, PendingOperation.CREATE)
            var needsDestination = false
            val repo =
                object : JournalRepository by FakeJournalRepository() {
                    override suspend fun pendingJournalMerges() = listOf(op)

                    override suspend fun markJournalMergeNeedsDestination(operation: JournalMergeOperation) {
                        needsDestination = true
                    }
                }
            val api =
                object : FakeCloudApiClient() {
                    override suspend fun mergeJournals(
                        accessToken: String,
                        sourceId: String,
                        request: JournalMergeRequest,
                    ) = Result.failure<JournalMergeResponse>(CloudApiException("MERGE_DESTINATION_MISSING", "Unavailable", 404))
                }
            val manager =
                testDefaultSyncManager(
                    journalRepository = repo,
                    cloudApiClient = api,
                    cloudAccountRepository = accounts,
                    syncMetadataService = metadata,
                    syncScope = backgroundScope,
                )
            assertFalse(manager.uploadPendingChanges().success)
            assertTrue(needsDestination)
            assertEquals(1, metadata.getPendingUploads(EntityType.JOURNAL_MERGE).size)
        }

    @Test
    fun `dependent merge waits while destination needs recovery and resumes after retarget`() =
        runTest {
            val a = Uuid.random()
            val b = Uuid.random()
            val c = Uuid.random()
            val d = Uuid.random()
            val ops = mutableListOf(operation(a, b), operation(b, c))
            val metadata = fakeSyncMetadataService()
            ops.forEach { metadata.enqueuePending(it.operationId.toString(), EntityType.JOURNAL_MERGE, PendingOperation.CREATE) }
            val requests = mutableListOf<String>()
            val repo =
                object : JournalRepository by FakeJournalRepository() {
                    override suspend fun pendingJournalMerges() = ops.toList()

                    override suspend fun markJournalMergeNeedsDestination(operation: JournalMergeOperation) {
                        ops[ops.indexOf(operation)] = operation.copy(needsDestination = true)
                    }

                    override suspend fun markJournalMergeSynced(operation: JournalMergeOperation) {
                        ops.remove(operation)
                    }
                }
            val api =
                object : FakeCloudApiClient() {
                    override suspend fun mergeJournals(
                        accessToken: String,
                        sourceId: String,
                        request: JournalMergeRequest,
                    ): Result<JournalMergeResponse> {
                        requests += sourceId
                        return if (request.destinationId == c.toString()) {
                            Result.failure(CloudApiException("MERGE_DESTINATION_MISSING", "Unavailable", 404))
                        } else {
                            Result.success(JournalMergeResponse(request.operationId, sourceId, d.toString()))
                        }
                    }
                }
            val manager =
                testDefaultSyncManager(
                    journalRepository = repo,
                    cloudApiClient = api,
                    cloudAccountRepository = accounts,
                    syncMetadataService = metadata,
                    syncScope = backgroundScope,
                )
            assertFalse(manager.uploadPendingChanges().success)
            manager.uploadPendingChanges()
            assertEquals(listOf(b.toString()), requests)
            assertFalse(ops.first { it.sourceId == a }.needsDestination)
            val blocked = ops.first { it.sourceId == b }
            val replacement = blocked.copy(operationId = Uuid.random(), destinationId = d, needsDestination = false)
            ops[ops.indexOf(blocked)] = replacement
            metadata.enqueuePending(replacement.operationId.toString(), EntityType.JOURNAL_MERGE, PendingOperation.CREATE)
            assertTrue(manager.uploadPendingChanges().success)
            assertEquals(listOf(b.toString(), b.toString(), a.toString()), requests)
            assertTrue(ops.isEmpty())
        }

    @Test
    fun `late memberships wait for pending merge including missing destination and network failure`() =
        runTest {
            for (failure in listOf(CloudApiException("MERGE_DESTINATION_MISSING", "Unavailable", 404), Exception("Offline"))) {
                var op = operation()
                val metadata = fakeSyncMetadataService()
                val associationId = AssociationPendingKey(op.destinationId, Uuid.random()).toPendingId()
                metadata.enqueuePending(op.operationId.toString(), EntityType.JOURNAL_MERGE, PendingOperation.CREATE)
                metadata.enqueuePending(associationId, EntityType.ASSOCIATION, PendingOperation.CREATE)
                val operations = mutableListOf(op)
                val repo =
                    object : JournalRepository by FakeJournalRepository() {
                        override suspend fun pendingJournalMerges() = operations.toList()

                        override suspend fun resolveJournalId(journalId: Uuid) =
                            if (journalId ==
                                op.sourceId
                            ) {
                                op.destinationId
                            } else {
                                journalId
                            }

                        override suspend fun markJournalMergeNeedsDestination(operation: JournalMergeOperation) {
                            op = operation.copy(needsDestination = true)
                            operations[0] = op
                        }
                    }
                val api =
                    object : FakeCloudApiClient() {
                        override suspend fun mergeJournals(
                            accessToken: String,
                            sourceId: String,
                            request: JournalMergeRequest,
                        ) = Result.failure<JournalMergeResponse>(failure)
                    }
                val manager =
                    testDefaultSyncManager(
                        journalRepository = repo,
                        cloudApiClient = api,
                        cloudAssociationDataSource = DefaultCloudAssociationDataSource(api),
                        cloudAccountRepository = accounts,
                        syncMetadataService = metadata,
                        syncScope = backgroundScope,
                    )
                assertFalse(manager.uploadPendingChanges().success)
                assertFalse("uploadAssociations" in api.methodCalls)
                assertEquals(listOf(associationId), metadata.getPendingUploads(EntityType.ASSOCIATION).map { it.entityId })
                manager.uploadPendingChanges()
                assertFalse("uploadAssociations" in api.methodCalls)
                operations.clear()
                manager.uploadPendingChanges()
                assertTrue("uploadAssociations" in api.methodCalls)
                assertTrue(metadata.getPendingUploads(EntityType.ASSOCIATION).isEmpty())
            }
        }

    @Test
    fun `a failed destination upload prevents merge request`() =
        runTest {
            val op = operation()
            val metadata = fakeSyncMetadataService()
            metadata.enqueuePending(op.destinationId.toString(), EntityType.JOURNAL, PendingOperation.CREATE)
            metadata.enqueuePending(op.operationId.toString(), EntityType.JOURNAL_MERGE, PendingOperation.CREATE)
            var mergeCalls = 0
            val repo =
                object : JournalRepository by FakeJournalRepository() {
                    override suspend fun pendingJournalMerges() = listOf(op)
                }
            val api =
                object : FakeCloudApiClient() {
                    override suspend fun mergeJournals(
                        accessToken: String,
                        sourceId: String,
                        request: JournalMergeRequest,
                    ): Result<JournalMergeResponse> {
                        mergeCalls++
                        error("Prerequisite missing")
                    }
                }
            val manager =
                testDefaultSyncManager(
                    journalRepository = repo,
                    cloudApiClient = api,
                    cloudAccountRepository = accounts,
                    syncMetadataService = metadata,
                    syncScope = backgroundScope,
                )
            assertFalse(manager.uploadPendingChanges().success)
            assertEquals(0, mergeCalls)
        }
}
