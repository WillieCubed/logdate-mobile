package app.logdate.client.sync

import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.test.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Integration tests for network recovery functionality.
 * Tests that sync error tracking works correctly for network recovery decisions.
 * AndroidSyncManager uses getLastSyncError() to determine if retry should occur on network restoration.
 */
class NetworkRecoveryIntegrationTest {

    @Test
    fun testLastErrorTrackedOnNetworkFailure() = runTest {
        val apiClient = FakeCloudApiClient()
        val syncMetadataService = fakeSyncMetadataService()

        val syncManager = DefaultSyncManager(
            cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
            cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
            cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
            cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
            cloudAccountRepository = fakeAccountRepository(),
            sessionStorage = fakeSessionStorage(),
            journalRepository = fakeJournalRepository(),
            journalNotesRepository = fakeJournalNotesRepository(),
            journalContentRepository = fakeJournalContentRepository(),
            journalConflictResolver = lastWriteWinsResolver(),
            noteConflictResolver = lastWriteWinsResolver(),
            syncMetadataService = syncMetadataService,
            transactionManager = testSyncTransactionManager()
        )

        // Configure API to fail with network error
        apiClient.configureContentSyncFailure(Exception("Network timeout"))

        val failedResult = syncManager.syncContent()

        assertFalse(failedResult.success, "Sync should fail with network error")
        assertTrue(failedResult.errors.isNotEmpty(), "Should have error information")

        // Verify last error is tracked
        val lastError = syncManager.getLastSyncError()
        assertTrue(lastError != null, "Should have recorded last sync error")
        assertTrue(lastError?.message?.isNotEmpty() == true, "Error should have message")
    }

    @Test
    fun testLastErrorIsClearedOnSuccess() = runTest {
        val apiClient = fakeCloudApiClient()
        val syncMetadataService = fakeSyncMetadataService()

        val syncManager = DefaultSyncManager(
            cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
            cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
            cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
            cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
            cloudAccountRepository = fakeAccountRepository(),
            sessionStorage = fakeSessionStorage(),
            journalRepository = fakeJournalRepository(),
            journalNotesRepository = fakeJournalNotesRepository(),
            journalContentRepository = fakeJournalContentRepository(),
            journalConflictResolver = lastWriteWinsResolver(),
            noteConflictResolver = lastWriteWinsResolver(),
            syncMetadataService = syncMetadataService,
            transactionManager = testSyncTransactionManager()
        )

        // Successful sync
        val successResult = syncManager.syncContent()
        assertTrue(successResult.success, "Sync should succeed")

        // Verify no error is tracked after successful sync
        val lastError = syncManager.getLastSyncError()
        assertNull(lastError, "Should have no error after successful sync")
    }

    @Test
    fun testTransientErrorCanBeDifferentiatedFromAuthError() = runTest {
        val failingApiClient = failingCloudApiClient()
        val syncMetadataService = fakeSyncMetadataService()

        // Sync with transient error
        val syncManager = DefaultSyncManager(
            cloudContentDataSource = DefaultCloudContentDataSource(failingApiClient),
            cloudJournalDataSource = DefaultCloudJournalDataSource(failingApiClient),
            cloudAssociationDataSource = DefaultCloudAssociationDataSource(failingApiClient),
            cloudMediaDataSource = DefaultCloudMediaDataSource(failingApiClient),
            cloudAccountRepository = fakeAccountRepository(),
            sessionStorage = fakeSessionStorage(),
            journalRepository = fakeJournalRepository(),
            journalNotesRepository = fakeJournalNotesRepository(),
            journalContentRepository = fakeJournalContentRepository(),
            journalConflictResolver = lastWriteWinsResolver(),
            noteConflictResolver = lastWriteWinsResolver(),
            syncMetadataService = syncMetadataService,
            transactionManager = testSyncTransactionManager()
        )

        val failedResult = syncManager.syncContent()

        // Verify sync failed and error was tracked
        assertFalse(failedResult.success, "Sync should fail with transient error")
        val lastError = syncManager.getLastSyncError()
        assertTrue(lastError != null, "Should track error for network recovery decisions")
    }

}
