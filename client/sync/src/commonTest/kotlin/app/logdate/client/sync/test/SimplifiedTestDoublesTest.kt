package app.logdate.client.sync.test

import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Tests for the simplified test doubles to ensure they work correctly for sync testing.
 */
class SimplifiedTestDoublesTest {

    @Test
    fun testSimpleMockCloudApiClientTracksCalls() = runTest {
        // Given: A simple mock API client
        val mockApiClient = SimpleMockCloudApiClient()
        
        // When: Making API calls
        mockApiClient.uploadContent("token", app.logdate.client.sync.cloud.ContentUploadRequest(
            id = "test",
            type = "TEXT",
            content = "Test content",
            mediaUri = null,
            createdAt = Clock.System.now().toEpochMilliseconds(),
            lastUpdated = Clock.System.now().toEpochMilliseconds()
        ))
        mockApiClient.getContentChanges("token", Clock.System.now().toEpochMilliseconds())
        
        // Then: Calls are tracked
        assertTrue(mockApiClient.wasMethodCalled("uploadContent"), "Should track uploadContent call")
        assertTrue(mockApiClient.wasMethodCalled("getContentChanges"), "Should track getContentChanges call")
        assertEquals(2, mockApiClient.methodCalls.size, "Should track total method calls")
    }

    @Test
    fun testSimpleMockCloudApiClientConfigurableFailures() = runTest {
        // Given: A mock API client configured for content sync failure
        val mockApiClient = SimpleMockCloudApiClient()
        val testError = Exception("Test network error")
        mockApiClient.configureContentSyncFailure(testError)
        
        // When: Making a content upload call
        val result = mockApiClient.uploadContent("token", app.logdate.client.sync.cloud.ContentUploadRequest(
            id = "test",
            type = "TEXT",
            content = "Test content",
            mediaUri = null,
            createdAt = Clock.System.now().toEpochMilliseconds(),
            lastUpdated = Clock.System.now().toEpochMilliseconds()
        ))
        
        // Then: Should return failure as configured
        assertTrue(result.isFailure, "Should return failure as configured")
        assertEquals(testError, result.exceptionOrNull(), "Should return configured exception")
    }

    @Test
    fun testSimpleMockCloudAccountRepositoryAuthenticationStates() = runTest {
        // Given: A simple mock cloud account repository
        val mockRepository = SimpleMockCloudAccountRepository()
        
        // When: Setting unauthenticated state
        mockRepository.setAuthenticated(false)
        
        // Then: Should reflect unauthenticated state
        assertEquals(null, mockRepository.getCurrentAccount(), "Should have no current account when unauthenticated")
        assertEquals(null, mockRepository.accessToken, "Should have no access token when unauthenticated")
        
        // When: Setting authenticated state
        mockRepository.setAuthenticated(true)
        
        // Then: Should reflect authenticated state
        assertTrue(mockRepository.getCurrentAccount() != null, "Should have current account when authenticated")
        assertEquals("test-access-token", mockRepository.accessToken, "Should have access token when authenticated")
    }

    @Test
    fun testTrackingSyncManagerTracksAllCalls() = runTest {
        // Given: A tracking sync manager
        val mockSyncManager = TrackingSyncManager()
        
        // When: Making various sync calls
        mockSyncManager.syncContent()
        mockSyncManager.syncJournals()
        mockSyncManager.syncContent() // Second call
        mockSyncManager.uploadPendingChanges()
        val status = mockSyncManager.getSyncStatus()
        
        // Then: All calls are properly tracked
        assertEquals(2, mockSyncManager.syncContentCalls, "Should track multiple syncContent calls")
        assertEquals(1, mockSyncManager.syncJournalsCalls, "Should track syncJournals calls")
        assertEquals(1, mockSyncManager.uploadPendingChangesCalls, "Should track uploadPendingChanges calls")
        assertEquals(1, mockSyncManager.getSyncStatusCalls, "Should track getSyncStatus calls")
        assertTrue(status.isEnabled, "Should return configured sync status")
    }

    @Test
    fun testTrackingSyncManagerConfigurableResults() = runTest {
        // Given: A tracking sync manager configured for failure
        val mockSyncManager = TrackingSyncManager()
        mockSyncManager.configureSyncFailure("Test sync failure")
        
        // When: Performing sync operations
        val result = mockSyncManager.syncContent()
        
        // Then: Should return configured failure
        assertFalse(result.success, "Should return failure as configured")
        assertEquals(1, result.errors.size, "Should have one error")
        assertEquals("Test sync failure", result.errors.first().message, "Should have configured error message")
        
        // When: Reconfiguring for success
        mockSyncManager.configureSyncSuccess(uploadedItems = 3, downloadedItems = 2)
        val successResult = mockSyncManager.syncContent()
        
        // Then: Should return configured success
        assertTrue(successResult.success, "Should return success as configured")
        assertEquals(3, successResult.uploadedItems, "Should have configured uploaded items")
        assertEquals(2, successResult.downloadedItems, "Should have configured downloaded items")
    }

    @Test
    fun testSimpleMockRepositoryBasicOperations() = runTest {
        // Given: A simple mock notes repository
        val repository = SimpleMockJournalNotesRepository()
        
        // When: Adding test notes
        val note1 = repository.addTestNote("Test note 1")
        val note2 = repository.addTestNote("Test note 2")
        
        // Then: Repository should contain the notes
        // We can't easily test the flow contents without more setup,
        // but we can verify the methods work without errors
        
        // When: Removing a note
        repository.remove(note1)
        
        // Then: Should not throw an exception
        
        // When: Clearing the repository
        repository.clear()
        
        // Then: Should not throw an exception
    }

    @Test
    fun testEndToEndSyncWithSimplifiedMocks() = runTest {
        // Given: A complete sync setup with simplified mocks
        val mockApiClient = SimplifiedTestFactory.createSuccessfulApiClient()
        val mockAccountRepository = SimplifiedTestFactory.createAuthenticatedAccountRepository()
        val mockJournalNotesRepository = SimplifiedTestFactory.createRepositoryWithTestData()
        val mockJournalRepository = SimpleMockJournalRepository()
        val mockJournalContentRepository = SimpleMockJournalContentRepository()
        
        val syncManager = DefaultSyncManager(
            cloudContentDataSource = DefaultCloudContentDataSource(mockApiClient),
            cloudJournalDataSource = DefaultCloudJournalDataSource(mockApiClient),
            cloudAssociationDataSource = DefaultCloudAssociationDataSource(mockApiClient),
            cloudMediaDataSource = DefaultCloudMediaDataSource(mockApiClient),
            cloudAccountRepository = mockAccountRepository,
            journalRepository = mockJournalRepository,
            journalNotesRepository = mockJournalNotesRepository,
            journalContentRepository = mockJournalContentRepository
        )
        
        // When: Performing a content sync
        val result = syncManager.syncContent()
        
        // Then: Sync should succeed and API calls should be made
        assertTrue(result.success, "Sync should succeed with authenticated user and successful API")
        assertTrue(mockApiClient.wasMethodCalled("uploadContent") || mockApiClient.wasMethodCalled("getContentChanges"), 
                  "Should make API calls during sync")
    }

    @Test 
    fun testSyncFailureWithUnauthenticatedUser() = runTest {
        // Given: An unauthenticated user setup
        val mockApiClient = SimplifiedTestFactory.createSuccessfulApiClient()
        val mockAccountRepository = SimplifiedTestFactory.createUnauthenticatedAccountRepository()
        val mockJournalNotesRepository = SimpleMockJournalNotesRepository()
        val mockJournalRepository = SimpleMockJournalRepository()
        val mockJournalContentRepository = SimpleMockJournalContentRepository()
        
        val syncManager = DefaultSyncManager(
            cloudContentDataSource = DefaultCloudContentDataSource(mockApiClient),
            cloudJournalDataSource = DefaultCloudJournalDataSource(mockApiClient),
            cloudAssociationDataSource = DefaultCloudAssociationDataSource(mockApiClient),
            cloudMediaDataSource = DefaultCloudMediaDataSource(mockApiClient),
            cloudAccountRepository = mockAccountRepository,
            journalRepository = mockJournalRepository,
            journalNotesRepository = mockJournalNotesRepository,
            journalContentRepository = mockJournalContentRepository
        )
        
        // When: Attempting to sync without authentication
        val result = syncManager.syncContent()
        
        // Then: Sync should fail
        assertFalse(result.success, "Sync should fail without authentication")
        assertTrue(result.errors.isNotEmpty(), "Should have error information")
    }

    @Test
    fun testSyncFailureWithNetworkErrors() = runTest {
        // Given: Network errors from API
        val mockApiClient = SimplifiedTestFactory.createFailingApiClient()
        val mockAccountRepository = SimplifiedTestFactory.createAuthenticatedAccountRepository()
        val mockJournalNotesRepository = SimpleMockJournalNotesRepository()
        val mockJournalRepository = SimpleMockJournalRepository()
        val mockJournalContentRepository = SimpleMockJournalContentRepository()
        
        val syncManager = DefaultSyncManager(
            cloudContentDataSource = DefaultCloudContentDataSource(mockApiClient),
            cloudJournalDataSource = DefaultCloudJournalDataSource(mockApiClient),
            cloudAssociationDataSource = DefaultCloudAssociationDataSource(mockApiClient),
            cloudMediaDataSource = DefaultCloudMediaDataSource(mockApiClient),
            cloudAccountRepository = mockAccountRepository,
            journalRepository = mockJournalRepository,
            journalNotesRepository = mockJournalNotesRepository,
            journalContentRepository = mockJournalContentRepository
        )
        
        // When: Attempting to sync with network failures
        val result = syncManager.syncContent()
        
        // Then: Sync should fail gracefully
        assertFalse(result.success, "Sync should fail with network errors")
        assertTrue(result.errors.isNotEmpty(), "Should have error details")
    }
}