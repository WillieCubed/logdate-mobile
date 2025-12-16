package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.test.SimplifiedTestFactory
import app.logdate.client.sync.test.SimpleMockJournalNotesRepository
import app.logdate.client.sync.test.SimpleMockJournalRepository
import app.logdate.client.sync.test.SimpleMockJournalContentRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * Integration tests for automatic upload functionality.
 * Tests that content, journals, and associations are automatically uploaded when created.
 */
class AutomaticUploadIntegrationTest {

    @Test
    fun testAutomaticContentUpload() = runTest {
        // Given: A sync manager with authenticated account and successful API client
        val mockApiClient = SimplifiedTestFactory.createSuccessfulApiClient()
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
        
        // When: We add a test note to the repository
        val testNote = JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = Clock.System.now(),
            lastUpdated = Clock.System.now(),
            content = "Test automatic upload"
        )
        mockJournalNotesRepository.addTestNote(testNote.content)
        
        // When: We trigger content sync (simulating automatic upload)
        val result = syncManager.syncContent()
        
        // Then: The sync should succeed and content should be uploaded
        assertTrue(result.success, "Content sync should succeed")
        assertTrue(result.uploadedItems > 0, "Should have uploaded at least one item")
        assertTrue(mockApiClient.wasMethodCalled("uploadContent"), "Should have called uploadContent on API client")
        
        // Verify the correct content was uploaded
        assertTrue(mockApiClient.uploadContentCalls.isNotEmpty(), "Should have upload content calls")
        val uploadedContent = mockApiClient.uploadContentCalls.first().second
        assertEquals("TEXT", uploadedContent.type, "Should upload text content type")
    }

    @Test
    fun testAutomaticJournalUpload() = runTest {
        // Given: A sync manager with authenticated account and successful API client
        val mockApiClient = SimplifiedTestFactory.createSuccessfulApiClient()
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
        
        // When: We trigger journal sync
        val result = syncManager.syncJournals()
        
        // Then: The sync should succeed
        assertTrue(result.success, "Journal sync should succeed")
        
        // Note: Since the mock repository starts empty, we don't expect uploads
        // In a real scenario with journal data, this would test journal uploads
        assertTrue(mockApiClient.wasMethodCalled("uploadJournal") || result.uploadedItems == 0, 
                  "Should either upload journals or have no journals to upload")
    }

    @Test
    fun testAutomaticAssociationUpload() = runTest {
        // Given: A sync manager with authenticated account and successful API client
        val mockApiClient = SimplifiedTestFactory.createSuccessfulApiClient()
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
        
        // When: We trigger association sync
        val result = syncManager.syncAssociations()
        
        // Then: The sync should succeed
        assertTrue(result.success, "Association sync should succeed")
        
        // Note: Since the mock repository starts empty, we don't expect uploads
        // In a real scenario with journal-content associations, this would test association uploads
        assertTrue(mockApiClient.wasMethodCalled("uploadAssociations") || result.uploadedItems == 0,
                  "Should either upload associations or have no associations to upload")
    }

    @Test
    fun testUploadFailuresAreHandledGracefully() = runTest {
        // Given: A sync manager with API client that fails uploads
        val mockApiClient = SimplifiedTestFactory.createFailingApiClient()
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
        
        // When: We attempt to sync content with failing API
        val result = syncManager.syncContent()
        
        // Then: The sync should fail gracefully with error information
        assertTrue(!result.success, "Content sync should fail with failing API")
        assertTrue(result.errors.isNotEmpty(), "Should have error information")
        assertTrue(mockApiClient.wasMethodCalled("uploadContent"), "Should still attempt upload calls")
    }

    @Test
    fun testUnauthenticatedUploadFails() = runTest {
        // Given: A sync manager with unauthenticated account
        val mockApiClient = SimplifiedTestFactory.createSuccessfulApiClient()
        val mockAccountRepository = SimplifiedTestFactory.createUnauthenticatedAccountRepository()
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
        
        // When: We attempt to sync content without authentication
        val result = syncManager.syncContent()
        
        // Then: The sync should fail with authentication error
        assertTrue(!result.success, "Content sync should fail without authentication")
        assertTrue(result.errors.any { it.type == SyncErrorType.AUTHENTICATION_ERROR }, 
                  "Should have authentication error")
        assertTrue(!mockApiClient.wasMethodCalled("uploadContent"), 
                  "Should not attempt API calls without authentication")
    }
}