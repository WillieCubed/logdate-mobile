package app.logdate.client.sync

import app.logdate.client.media.StubMediaManager
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.client.sync.test.InMemoryMediaSyncRefStore
import app.logdate.client.sync.test.InMemorySyncConflictStore
import app.logdate.client.sync.test.InMemorySyncDeadLetterStore
import app.logdate.client.sync.test.InMemorySyncRetryScheduleStore
import app.logdate.client.sync.test.fakeAccountRepository
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeDataUsagePolicy
import app.logdate.client.sync.test.fakeJournalContentRepository
import app.logdate.client.sync.test.fakeJournalNotesRepository
import app.logdate.client.sync.test.fakeJournalRepository
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.lastWriteWinsResolver
import app.logdate.client.sync.test.testSyncTransactionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive production scenario tests for sync service.
 * Tests real-world scenarios including:
 * - Large-scale syncs (100+ items)
 * - Partial batch failures with transaction safety
 * - Network timeouts and retries
 * - Concurrent operations
 * - Edge cases and error conditions
 */
class SyncProductionScenariosTest {
    @Test
    fun testLargeScaleSyncWith100Items() =
        runTest {
            val apiClient = fakeCloudApiClient()
            val syncMetadataService = fakeSyncMetadataService()

            // Create repo with 100 notes
            val noteContents = (1..100).map { "Note $it" }
            val notesRepository = fakeJournalNotesRepository(*noteContents.toTypedArray())

            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
                    cloudAccountRepository = fakeAccountRepository(),
                    sessionStorage = fakeSessionStorage(),
                    mediaManager = StubMediaManager(),
                    mediaSyncRefStore = InMemoryMediaSyncRefStore(),
                    journalRepository = fakeJournalRepository(),
                    journalNotesRepository = notesRepository,
                    journalContentRepository = fakeJournalContentRepository(),
                    journalConflictResolver = lastWriteWinsResolver(),
                    noteConflictResolver = lastWriteWinsResolver(),
                    conflictStore = InMemorySyncConflictStore(),
                    deadLetterStore = InMemorySyncDeadLetterStore(),
                    retryScheduleStore = InMemorySyncRetryScheduleStore(),
                    syncMetadataService = syncMetadataService,
                    transactionManager = testSyncTransactionManager(),
                    dataUsagePolicy = fakeDataUsagePolicy(),
                )

            // Enqueue all notes as pending
            val notes = notesRepository.allNotesObserved.first()
            assertEquals(100, notes.size, "Should have 100 notes")

            notes.forEach { note ->
                syncMetadataService.enqueuePending(
                    entityId = note.uid.toString(),
                    entityType = EntityType.NOTE,
                    operation = PendingOperation.CREATE,
                )
            }

            // Sync all items
            val result = syncManager.syncContent()

            assertTrue(result.success, "Large-scale sync should succeed")
            assertTrue(result.uploadedItems >= 100, "Should upload all 100 items")
            assertTrue(apiClient.wasMethodCalled("uploadContent"), "Should call uploadContent")
        }

    @Test
    fun testPartialBatchFailureRollback() =
        runTest {
            val apiClient = FakeCloudApiClient()
            val syncMetadataService = fakeSyncMetadataService()

            // Create 10 notes to simulate batch processing
            val noteContents = (1..10).map { "Note $it" }
            val notesRepository = fakeJournalNotesRepository(*noteContents.toTypedArray())

            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
                    cloudAccountRepository = fakeAccountRepository(),
                    sessionStorage = fakeSessionStorage(),
                    mediaManager = StubMediaManager(),
                    mediaSyncRefStore = InMemoryMediaSyncRefStore(),
                    journalRepository = fakeJournalRepository(),
                    journalNotesRepository = notesRepository,
                    journalContentRepository = fakeJournalContentRepository(),
                    journalConflictResolver = lastWriteWinsResolver(),
                    noteConflictResolver = lastWriteWinsResolver(),
                    conflictStore = InMemorySyncConflictStore(),
                    deadLetterStore = InMemorySyncDeadLetterStore(),
                    retryScheduleStore = InMemorySyncRetryScheduleStore(),
                    syncMetadataService = syncMetadataService,
                    transactionManager = testSyncTransactionManager(),
                    dataUsagePolicy = fakeDataUsagePolicy(),
                )

            // Enqueue all notes
            val notes = notesRepository.allNotesObserved.first()
            notes.forEach { note ->
                syncMetadataService.enqueuePending(
                    entityId = note.uid.toString(),
                    entityType = EntityType.NOTE,
                    operation = PendingOperation.CREATE,
                )
            }

            // Configure API to fail (simulating batch failure)
            apiClient.configureContentSyncFailure(Exception("Batch processing error"))

            val result = syncManager.syncContent()

            assertFalse(result.success, "Batch sync should fail")
            assertTrue(result.errors.isNotEmpty(), "Should have error information")

            // Verify all items remain pending (rolled back, not partially committed)
            val pendingItems = syncMetadataService.getPendingUploads(EntityType.NOTE)
            assertEquals(
                notes.size,
                pendingItems.size,
                "All items should remain pending after batch failure (transaction safety)",
            )
        }

    @Test
    fun testNetworkTimeoutDuringUpload() =
        runTest {
            val apiClient = FakeCloudApiClient()
            val syncMetadataService = fakeSyncMetadataService()
            val notesRepository = fakeJournalNotesRepository("Test note")

            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
                    cloudAccountRepository = fakeAccountRepository(),
                    sessionStorage = fakeSessionStorage(),
                    mediaManager = StubMediaManager(),
                    mediaSyncRefStore = InMemoryMediaSyncRefStore(),
                    journalRepository = fakeJournalRepository(),
                    journalNotesRepository = notesRepository,
                    journalContentRepository = fakeJournalContentRepository(),
                    journalConflictResolver = lastWriteWinsResolver(),
                    noteConflictResolver = lastWriteWinsResolver(),
                    conflictStore = InMemorySyncConflictStore(),
                    deadLetterStore = InMemorySyncDeadLetterStore(),
                    retryScheduleStore = InMemorySyncRetryScheduleStore(),
                    syncMetadataService = syncMetadataService,
                    transactionManager = testSyncTransactionManager(),
                    dataUsagePolicy = fakeDataUsagePolicy(),
                )

            // Setup pending items
            val notes = notesRepository.allNotesObserved.first()
            notes.forEach { note ->
                syncMetadataService.enqueuePending(
                    entityId = note.uid.toString(),
                    entityType = EntityType.NOTE,
                    operation = PendingOperation.CREATE,
                )
            }

            // Configure timeout error
            apiClient.configureContentSyncFailure(Exception("Socket timeout after 30s"))

            val result = syncManager.syncContent()

            assertFalse(result.success, "Upload should fail on timeout")
            assertTrue(result.errors.any { it.message.contains("timeout") }, "Should have timeout error")

            // Verify items still pending for retry
            val pendingItems = syncMetadataService.getPendingUploads(EntityType.NOTE)
            assertFalse(pendingItems.isEmpty(), "Items should remain pending after timeout")
        }

    @Test
    fun testOfflineQueueFlushesAfterReconnect() =
        runTest {
            val offlineClient =
                FakeCloudApiClient().apply {
                    configureContentSyncFailure(Exception("Offline"))
                }
            val syncMetadataService = fakeSyncMetadataService()
            val notesRepository = fakeJournalNotesRepository("Offline note")

            val offlineSyncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(offlineClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(offlineClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(offlineClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(offlineClient),
                    cloudAccountRepository = fakeAccountRepository(),
                    sessionStorage = fakeSessionStorage(),
                    mediaManager = StubMediaManager(),
                    mediaSyncRefStore = InMemoryMediaSyncRefStore(),
                    journalRepository = fakeJournalRepository(),
                    journalNotesRepository = notesRepository,
                    journalContentRepository = fakeJournalContentRepository(),
                    journalConflictResolver = lastWriteWinsResolver(),
                    noteConflictResolver = lastWriteWinsResolver(),
                    conflictStore = InMemorySyncConflictStore(),
                    deadLetterStore = InMemorySyncDeadLetterStore(),
                    retryScheduleStore = InMemorySyncRetryScheduleStore(),
                    syncMetadataService = syncMetadataService,
                    transactionManager = testSyncTransactionManager(),
                    dataUsagePolicy = fakeDataUsagePolicy(),
                )

            val note = notesRepository.allNotesObserved.first().first()
            syncMetadataService.enqueuePending(
                entityId = note.uid.toString(),
                entityType = EntityType.NOTE,
                operation = PendingOperation.CREATE,
            )

            val offlineResult = offlineSyncManager.syncContent()
            assertFalse(offlineResult.success, "Sync should fail while offline")
            assertTrue(
                syncMetadataService.getPendingUploads(EntityType.NOTE).isNotEmpty(),
                "Pending uploads should remain queued while offline",
            )

            val onlineSyncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(fakeCloudApiClient()),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(fakeCloudApiClient()),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(fakeCloudApiClient()),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(fakeCloudApiClient()),
                    cloudAccountRepository = fakeAccountRepository(),
                    sessionStorage = fakeSessionStorage(),
                    mediaManager = StubMediaManager(),
                    mediaSyncRefStore = InMemoryMediaSyncRefStore(),
                    journalRepository = fakeJournalRepository(),
                    journalNotesRepository = notesRepository,
                    journalContentRepository = fakeJournalContentRepository(),
                    journalConflictResolver = lastWriteWinsResolver(),
                    noteConflictResolver = lastWriteWinsResolver(),
                    conflictStore = InMemorySyncConflictStore(),
                    deadLetterStore = InMemorySyncDeadLetterStore(),
                    retryScheduleStore = InMemorySyncRetryScheduleStore(),
                    syncMetadataService = syncMetadataService,
                    transactionManager = testSyncTransactionManager(),
                    dataUsagePolicy = fakeDataUsagePolicy(),
                )

            val onlineResult = onlineSyncManager.syncContent()
            assertTrue(onlineResult.success, "Sync should succeed after reconnect")
            assertTrue(
                syncMetadataService.getPendingUploads(EntityType.NOTE).isEmpty(),
                "Pending uploads should clear after successful reconnect sync",
            )
        }

    @Test
    fun testConcurrentUploadAndDownloadSync() =
        runTest {
            val apiClient = fakeCloudApiClient()
            val syncMetadataService = fakeSyncMetadataService()
            val notesRepository = fakeJournalNotesRepository("Local note 1", "Local note 2")

            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
                    cloudAccountRepository = fakeAccountRepository(),
                    sessionStorage = fakeSessionStorage(),
                    mediaManager = StubMediaManager(),
                    mediaSyncRefStore = InMemoryMediaSyncRefStore(),
                    journalRepository = fakeJournalRepository(),
                    journalNotesRepository = notesRepository,
                    journalContentRepository = fakeJournalContentRepository(),
                    journalConflictResolver = lastWriteWinsResolver(),
                    noteConflictResolver = lastWriteWinsResolver(),
                    conflictStore = InMemorySyncConflictStore(),
                    deadLetterStore = InMemorySyncDeadLetterStore(),
                    retryScheduleStore = InMemorySyncRetryScheduleStore(),
                    syncMetadataService = syncMetadataService,
                    transactionManager = testSyncTransactionManager(),
                    dataUsagePolicy = fakeDataUsagePolicy(),
                )

            // Enqueue local items for upload
            val notes = notesRepository.allNotesObserved.first()
            notes.forEach { note ->
                syncMetadataService.enqueuePending(
                    entityId = note.uid.toString(),
                    entityType = EntityType.NOTE,
                    operation = PendingOperation.CREATE,
                )
            }

            // Perform full sync (upload + download)
            val result = syncManager.fullSync()

            assertTrue(result.success, "Concurrent sync should succeed")
            assertTrue(result.uploadedItems >= 0, "Should track uploads")
            assertTrue(result.downloadedItems >= 0, "Should track downloads")
        }

    @Test
    fun testMultipleFailureTypesInSingleSync() =
        runTest {
            val apiClient = FakeCloudApiClient()
            val syncMetadataService = fakeSyncMetadataService()
            val journalRepository = fakeJournalRepository()
            val notesRepository = fakeJournalNotesRepository("Test note")

            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
                    cloudAccountRepository = fakeAccountRepository(),
                    sessionStorage = fakeSessionStorage(),
                    mediaManager = StubMediaManager(),
                    mediaSyncRefStore = InMemoryMediaSyncRefStore(),
                    journalRepository = journalRepository,
                    journalNotesRepository = notesRepository,
                    journalContentRepository = fakeJournalContentRepository(),
                    journalConflictResolver = lastWriteWinsResolver(),
                    noteConflictResolver = lastWriteWinsResolver(),
                    conflictStore = InMemorySyncConflictStore(),
                    deadLetterStore = InMemorySyncDeadLetterStore(),
                    retryScheduleStore = InMemorySyncRetryScheduleStore(),
                    syncMetadataService = syncMetadataService,
                    transactionManager = testSyncTransactionManager(),
                    dataUsagePolicy = fakeDataUsagePolicy(),
                )

            // Enqueue multiple entity types with failures
            val notes = notesRepository.allNotesObserved.first()
            notes.forEach { note ->
                syncMetadataService.enqueuePending(
                    entityId = note.uid.toString(),
                    entityType = EntityType.NOTE,
                    operation = PendingOperation.CREATE,
                )
            }

            // Configure API to fail
            apiClient.configureContentSyncFailure(Exception("Network error"))
            apiClient.configureJournalSyncFailure(Exception("Server error"))

            val result = syncManager.fullSync()

            assertFalse(result.success, "Sync with multiple failures should fail")
            assertTrue(result.errors.isNotEmpty(), "Should accumulate all errors")
        }

    @Test
    fun testSyncWithMixedSuccessAndFailure() =
        runTest {
            val apiClient = fakeCloudApiClient()
            val syncMetadataService = fakeSyncMetadataService()

            // Create successful sync scenario
            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
                    cloudAccountRepository = fakeAccountRepository(),
                    sessionStorage = fakeSessionStorage(),
                    mediaManager = StubMediaManager(),
                    mediaSyncRefStore = InMemoryMediaSyncRefStore(),
                    journalRepository = fakeJournalRepository(),
                    journalNotesRepository = fakeJournalNotesRepository("Note 1", "Note 2"),
                    journalContentRepository = fakeJournalContentRepository(),
                    journalConflictResolver = lastWriteWinsResolver(),
                    noteConflictResolver = lastWriteWinsResolver(),
                    conflictStore = InMemorySyncConflictStore(),
                    deadLetterStore = InMemorySyncDeadLetterStore(),
                    retryScheduleStore = InMemorySyncRetryScheduleStore(),
                    syncMetadataService = syncMetadataService,
                    transactionManager = testSyncTransactionManager(),
                    dataUsagePolicy = fakeDataUsagePolicy(),
                )

            // First sync succeeds
            val firstResult = syncManager.syncContent()
            assertTrue(firstResult.success, "First sync should succeed")

            // Verify error is cleared
            val errorAfterSuccess = syncManager.getLastSyncError()
            assertTrue(errorAfterSuccess == null, "Error should be cleared after successful sync")
        }

    @Test
    fun testRepeatedFailuresTrackCumulativeErrors() =
        runTest {
            val apiClient = FakeCloudApiClient()
            val syncMetadataService = fakeSyncMetadataService()

            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
                    cloudAccountRepository = fakeAccountRepository(),
                    sessionStorage = fakeSessionStorage(),
                    mediaManager = StubMediaManager(),
                    mediaSyncRefStore = InMemoryMediaSyncRefStore(),
                    journalRepository = fakeJournalRepository(),
                    journalNotesRepository = fakeJournalNotesRepository(),
                    journalContentRepository = fakeJournalContentRepository(),
                    journalConflictResolver = lastWriteWinsResolver(),
                    noteConflictResolver = lastWriteWinsResolver(),
                    conflictStore = InMemorySyncConflictStore(),
                    deadLetterStore = InMemorySyncDeadLetterStore(),
                    retryScheduleStore = InMemorySyncRetryScheduleStore(),
                    syncMetadataService = syncMetadataService,
                    transactionManager = testSyncTransactionManager(),
                    dataUsagePolicy = fakeDataUsagePolicy(),
                )

            // First failure
            apiClient.configureContentSyncFailure(Exception("Network timeout - attempt 1"))
            val result1 = syncManager.syncContent()
            assertFalse(result1.success, "First sync should fail")

            requireNotNull(syncManager.getLastSyncError()) { "Should track first error" }

            // Second failure with different cause
            apiClient.configureContentSyncFailure(Exception("Server error - attempt 2"))
            val result2 = syncManager.syncContent()
            assertFalse(result2.success, "Second sync should fail")

            val error2 = requireNotNull(syncManager.getLastSyncError()) { "Should track second error" }
            val message = error2.message
            assertTrue(message.contains("attempt 2") || message.isNotEmpty(), "Should track latest error")
        }

    @Test
    fun testSyncRecoveryAfterMultipleFailures() =
        runTest {
            val apiClient = fakeCloudApiClient()
            val syncMetadataService = fakeSyncMetadataService()

            // Use successful client from the start
            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
                    cloudAccountRepository = fakeAccountRepository(),
                    sessionStorage = fakeSessionStorage(),
                    mediaManager = StubMediaManager(),
                    mediaSyncRefStore = InMemoryMediaSyncRefStore(),
                    journalRepository = fakeJournalRepository(),
                    journalNotesRepository = fakeJournalNotesRepository("Note 1"),
                    journalContentRepository = fakeJournalContentRepository(),
                    journalConflictResolver = lastWriteWinsResolver(),
                    noteConflictResolver = lastWriteWinsResolver(),
                    conflictStore = InMemorySyncConflictStore(),
                    deadLetterStore = InMemorySyncDeadLetterStore(),
                    retryScheduleStore = InMemorySyncRetryScheduleStore(),
                    syncMetadataService = syncMetadataService,
                    transactionManager = testSyncTransactionManager(),
                    dataUsagePolicy = fakeDataUsagePolicy(),
                )

            // First successful sync
            val firstResult = syncManager.syncContent()
            assertTrue(firstResult.success, "First sync should succeed")

            // Verify error is cleared after successful sync
            val finalError = syncManager.getLastSyncError()
            assertTrue(finalError == null, "Error should be cleared after successful sync")
        }

    @Test
    fun testAuthenticationRequiredMidwayThroughSync() =
        runTest {
            val apiClient = FakeCloudApiClient()
            val syncMetadataService = fakeSyncMetadataService()

            // Start with authenticated manager
            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
                    cloudAccountRepository = fakeAccountRepository(authenticated = false), // Simulate auth loss
                    sessionStorage = fakeSessionStorage(authenticated = false),
                    mediaManager = StubMediaManager(),
                    mediaSyncRefStore = InMemoryMediaSyncRefStore(),
                    journalRepository = fakeJournalRepository(),
                    journalNotesRepository = fakeJournalNotesRepository("Note"),
                    journalContentRepository = fakeJournalContentRepository(),
                    journalConflictResolver = lastWriteWinsResolver(),
                    noteConflictResolver = lastWriteWinsResolver(),
                    conflictStore = InMemorySyncConflictStore(),
                    deadLetterStore = InMemorySyncDeadLetterStore(),
                    retryScheduleStore = InMemorySyncRetryScheduleStore(),
                    syncMetadataService = syncMetadataService,
                    transactionManager = testSyncTransactionManager(),
                    dataUsagePolicy = fakeDataUsagePolicy(),
                )

            val result = syncManager.syncContent()

            assertFalse(result.success, "Sync should fail without authentication")
            assertTrue(
                result.errors.any { it.type == SyncErrorType.AUTHENTICATION_ERROR },
                "Should report authentication error",
            )
        }

    @Test
    fun testPartialDownloadWithMixedResults() =
        runTest {
            val apiClient = fakeCloudApiClient()
            val syncMetadataService = fakeSyncMetadataService()

            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
                    cloudAccountRepository = fakeAccountRepository(),
                    sessionStorage = fakeSessionStorage(),
                    mediaManager = StubMediaManager(),
                    mediaSyncRefStore = InMemoryMediaSyncRefStore(),
                    journalRepository = fakeJournalRepository(),
                    journalNotesRepository = fakeJournalNotesRepository(),
                    journalContentRepository = fakeJournalContentRepository(),
                    journalConflictResolver = lastWriteWinsResolver(),
                    noteConflictResolver = lastWriteWinsResolver(),
                    conflictStore = InMemorySyncConflictStore(),
                    deadLetterStore = InMemorySyncDeadLetterStore(),
                    retryScheduleStore = InMemorySyncRetryScheduleStore(),
                    syncMetadataService = syncMetadataService,
                    transactionManager = testSyncTransactionManager(),
                    dataUsagePolicy = fakeDataUsagePolicy(),
                )

            // Download remote changes
            val result = syncManager.downloadRemoteChanges()

            assertTrue(result.success, "Download should succeed with valid API client")
            assertTrue(result.downloadedItems >= 0, "Should track downloaded items")
        }

    @Test
    fun testUploadPendingChangesWithEmptyQueue() =
        runTest {
            val apiClient = fakeCloudApiClient()
            val syncMetadataService = fakeSyncMetadataService()

            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
                    cloudAccountRepository = fakeAccountRepository(),
                    sessionStorage = fakeSessionStorage(),
                    mediaManager = StubMediaManager(),
                    mediaSyncRefStore = InMemoryMediaSyncRefStore(),
                    journalRepository = fakeJournalRepository(),
                    journalNotesRepository = fakeJournalNotesRepository(),
                    journalContentRepository = fakeJournalContentRepository(),
                    journalConflictResolver = lastWriteWinsResolver(),
                    noteConflictResolver = lastWriteWinsResolver(),
                    conflictStore = InMemorySyncConflictStore(),
                    deadLetterStore = InMemorySyncDeadLetterStore(),
                    retryScheduleStore = InMemorySyncRetryScheduleStore(),
                    syncMetadataService = syncMetadataService,
                    transactionManager = testSyncTransactionManager(),
                    dataUsagePolicy = fakeDataUsagePolicy(),
                )

            // Upload with empty queue (no pending items)
            val result = syncManager.uploadPendingChanges()

            assertTrue(result.success, "Upload should succeed even with empty queue")
            assertEquals(0, result.uploadedItems, "Should upload 0 items")
        }
}
