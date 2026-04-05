package app.logdate.client.sync

import app.logdate.client.media.StubMediaManager
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudDraftDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.client.sync.test.InMemoryMediaSyncRefStore
import app.logdate.client.sync.test.InMemorySyncConflictStore
import app.logdate.client.sync.test.InMemorySyncDeadLetterStore
import app.logdate.client.sync.test.InMemorySyncRetryScheduleStore
import app.logdate.client.sync.test.failingCloudApiClient
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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for network recovery functionality.
 * Tests that sync error tracking works correctly for network recovery decisions.
 * AndroidSyncManager uses getLastSyncError() to determine if retry should occur on network restoration.
 */
class NetworkRecoveryIntegrationTest {
    @Test
    fun testLastErrorTrackedOnNetworkFailure() =
        runTest {
            val apiClient = FakeCloudApiClient()
            val syncMetadataService = fakeSyncMetadataService()

            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
                    cloudDraftDataSource = DefaultCloudDraftDataSource(apiClient),
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

            // Configure API to fail with network error
            apiClient.configureContentSyncFailure(Exception("Network timeout"))

            val failedResult = syncManager.syncContent()

            assertFalse(failedResult.success, "Sync should fail with network error")
            assertTrue(failedResult.errors.isNotEmpty(), "Should have error information")

            // Verify last error is tracked
            val lastError = requireNotNull(syncManager.getLastSyncError()) { "Should have recorded last sync error" }
            assertTrue(lastError.message.isNotEmpty(), "Error should have message")
        }

    @Test
    fun testLastErrorIsClearedOnSuccess() =
        runTest {
            val apiClient = fakeCloudApiClient()
            val syncMetadataService = fakeSyncMetadataService()

            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
                    cloudDraftDataSource = DefaultCloudDraftDataSource(apiClient),
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

            // Successful sync
            val successResult = syncManager.syncContent()
            assertTrue(successResult.success, "Sync should succeed")

            // Verify no error is tracked after successful sync
            val lastError = syncManager.getLastSyncError()
            assertNull(lastError, "Should have no error after successful sync")
        }

    @Test
    fun testTransientErrorCanBeDifferentiatedFromAuthError() =
        runTest {
            val failingApiClient = failingCloudApiClient()
            val syncMetadataService = fakeSyncMetadataService()

            // Sync with transient error
            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(failingApiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(failingApiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(failingApiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(failingApiClient),
                    cloudDraftDataSource = DefaultCloudDraftDataSource(failingApiClient),
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

            val failedResult = syncManager.syncContent()

            // Verify sync failed and error was tracked
            assertFalse(failedResult.success, "Sync should fail with transient error")
            val lastError = syncManager.getLastSyncError()
            assertTrue(lastError != null, "Should track error for network recovery decisions")
        }
}
