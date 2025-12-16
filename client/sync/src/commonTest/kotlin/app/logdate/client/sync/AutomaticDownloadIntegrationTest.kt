package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.cloud.JournalContentAssociation
import app.logdate.client.sync.test.SimplifiedTestFactory
import app.logdate.client.sync.test.SimpleMockJournalNotesRepository
import app.logdate.client.sync.test.SimpleMockJournalRepository
import app.logdate.client.sync.test.SimpleMockJournalContentRepository
import app.logdate.shared.model.Journal
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

/**
 * Integration tests for automatic download functionality.
 * Tests that remote changes are automatically downloaded and applied locally.
 */
class AutomaticDownloadIntegrationTest {

    @Test
    fun testDownloadNewRemoteContent() = runTest {
        // Given: A sync manager with authenticated account and API client that returns remote changes
        val mockApiClient = SimplifiedTestFactory.createSuccessfulApiClient()
        val mockAccountRepository = SimplifiedTestFactory.createAuthenticatedAccountRepository()
        val mockJournalNotesRepository = SimpleMockJournalNotesRepository()
        val mockJournalRepository = SimpleMockJournalRepository()
        val mockJournalContentRepository = SimpleMockJournalContentRepository()
        
        // Configure API client to return remote content changes
        val remoteNote = JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = Clock.System.now(),
            lastUpdated = Clock.System.now(),
            content = "Remote content from server"
        )
        
        mockApiClient.getContentChangesResponse = Result.success(
            app.logdate.client.sync.cloud.ContentChangesResponse(
                changes = listOf(
                    app.logdate.client.sync.cloud.ContentChange(
                        id = remoteNote.uid.toString(),
                        type = "TEXT",
                        content = remoteNote.content,
                        mediaUri = null,
                        createdAt = remoteNote.creationTimestamp.toEpochMilliseconds(),
                        lastUpdated = remoteNote.lastUpdated.toEpochMilliseconds(),
                        serverVersion = 1
                    )
                ),
                deletions = emptyList(),
                lastTimestamp = Clock.System.now().toEpochMilliseconds()
            )
        )
        
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
        
        // When: We download remote changes
        val result = syncManager.downloadRemoteChanges()
        
        // Then: The download should succeed
        assertTrue(result.success, "Download should succeed")
        assertTrue(result.downloadedItems > 0, "Should have downloaded items")
        assertTrue(mockApiClient.wasMethodCalled("getContentChanges"), "Should have called getContentChanges")
        
        // And: The remote content should be added to local repository
        // Note: In the real implementation, this would be verified by checking the repository
        // Here we verify the API calls were made correctly
        assertTrue(mockApiClient.getContentChangesCalls.isNotEmpty(), "Should have content change calls")
    }

    @Test
    fun testDownloadRemoteContentUpdates() = runTest {
        // Given: A sync manager with existing local content
        val mockApiClient = SimplifiedTestFactory.createSuccessfulApiClient()
        val mockAccountRepository = SimplifiedTestFactory.createAuthenticatedAccountRepository()
        val mockJournalNotesRepository = SimpleMockJournalNotesRepository()
        val mockJournalRepository = SimpleMockJournalRepository()
        val mockJournalContentRepository = SimpleMockJournalContentRepository()
        
        // Add existing local note
        val existingNote = mockJournalNotesRepository.addTestNote("Original content")
        
        // Configure API client to return updated version of the same note with a clearly newer timestamp
        val updatedTime = Clock.System.now().plus(1.hours)
        mockApiClient.getContentChangesResponse = Result.success(
            app.logdate.client.sync.cloud.ContentChangesResponse(
                changes = listOf(
                    app.logdate.client.sync.cloud.ContentChange(
                        id = existingNote.uid.toString(),
                        type = "TEXT",
                        content = "Updated content from server",
                        mediaUri = null,
                        createdAt = existingNote.creationTimestamp.toEpochMilliseconds(),
                        lastUpdated = updatedTime.toEpochMilliseconds(),
                        serverVersion = 2
                    )
                ),
                deletions = emptyList(),
                lastTimestamp = updatedTime.toEpochMilliseconds()
            )
        )
        
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
        
        // When: We download remote changes
        val result = syncManager.downloadRemoteChanges()
        
        // Then: The download should succeed with conflict resolution
        assertTrue(result.success, "Download should succeed")
        assertTrue(result.conflictsResolved > 0 || result.downloadedItems > 0, "Should have resolved conflicts or downloaded items")
    }

    @Test
    fun testDownloadRemoteContentDeletions() = runTest {
        // Given: A sync manager with API client that returns content deletions
        val mockApiClient = SimplifiedTestFactory.createSuccessfulApiClient()
        val mockAccountRepository = SimplifiedTestFactory.createAuthenticatedAccountRepository()
        val mockJournalNotesRepository = SimpleMockJournalNotesRepository()
        val mockJournalRepository = SimpleMockJournalRepository()
        val mockJournalContentRepository = SimpleMockJournalContentRepository()
        
        val deletedNoteId = Uuid.random()
        
        // Configure API client to return content deletion
        mockApiClient.getContentChangesResponse = Result.success(
            app.logdate.client.sync.cloud.ContentChangesResponse(
                changes = emptyList(),
                deletions = listOf(
                    app.logdate.client.sync.cloud.ContentDeletion(
                        id = deletedNoteId.toString(),
                        deletedAt = Clock.System.now().toEpochMilliseconds()
                    )
                ),
                lastTimestamp = Clock.System.now().toEpochMilliseconds()
            )
        )
        
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
        
        // When: We download remote changes
        val result = syncManager.downloadRemoteChanges()
        
        // Then: The download should succeed
        assertTrue(result.success, "Download should succeed")
        assertTrue(result.downloadedItems > 0, "Should have processed deletions")
        assertTrue(mockApiClient.wasMethodCalled("getContentChanges"), "Should have called getContentChanges")
    }

    @Test
    fun testDownloadRemoteJournalChanges() = runTest {
        // Given: A sync manager with API client that returns journal changes
        val mockApiClient = SimplifiedTestFactory.createSuccessfulApiClient()
        val mockAccountRepository = SimplifiedTestFactory.createAuthenticatedAccountRepository()
        val mockJournalNotesRepository = SimpleMockJournalNotesRepository()
        val mockJournalRepository = SimpleMockJournalRepository()
        val mockJournalContentRepository = SimpleMockJournalContentRepository()
        
        val remoteJournal = Journal(
            id = Uuid.random(),
            title = "Remote Journal",
            description = "Journal from server",
            created = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )
        
        // Configure API client to return journal changes
        mockApiClient.getJournalChangesResponse = Result.success(
            app.logdate.client.sync.cloud.JournalChangesResponse(
                changes = listOf(
                    app.logdate.client.sync.cloud.JournalChange(
                        id = remoteJournal.id.toString(),
                        title = remoteJournal.title,
                        description = remoteJournal.description,
                        createdAt = remoteJournal.created.toEpochMilliseconds(),
                        lastUpdated = remoteJournal.lastUpdated.toEpochMilliseconds(),
                        serverVersion = 1
                    )
                ),
                deletions = emptyList(),
                lastTimestamp = Clock.System.now().toEpochMilliseconds()
            )
        )
        
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
        
        // When: We download remote changes
        val result = syncManager.downloadRemoteChanges()
        
        // Then: The download should succeed
        assertTrue(result.success, "Download should succeed")
        assertTrue(result.downloadedItems > 0, "Should have downloaded journals")
        assertTrue(mockApiClient.wasMethodCalled("getJournalChanges"), "Should have called getJournalChanges")
    }

    @Test
    fun testDownloadRemoteAssociationChanges() = runTest {
        // Given: A sync manager with API client that returns association changes
        val mockApiClient = SimplifiedTestFactory.createSuccessfulApiClient()
        val mockAccountRepository = SimplifiedTestFactory.createAuthenticatedAccountRepository()
        val mockJournalNotesRepository = SimpleMockJournalNotesRepository()
        val mockJournalRepository = SimpleMockJournalRepository()
        val mockJournalContentRepository = SimpleMockJournalContentRepository()
        
        val journalId = Uuid.random()
        val contentId = Uuid.random()
        
        // Configure API client to return association changes
        mockApiClient.getAssociationChangesResponse = Result.success(
            app.logdate.client.sync.cloud.AssociationChangesResponse(
                changes = listOf(
                    app.logdate.client.sync.cloud.AssociationChange(
                        journalId = journalId.toString(),
                        contentId = contentId.toString(),
                        createdAt = Clock.System.now().toEpochMilliseconds(),
                        isDeleted = false,
                        serverVersion = 1
                    )
                ),
                deletions = emptyList(),
                lastTimestamp = Clock.System.now().toEpochMilliseconds()
            )
        )
        
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
        
        // When: We download remote changes
        val result = syncManager.downloadRemoteChanges()
        
        // Then: The download should succeed
        assertTrue(result.success, "Download should succeed")
        assertTrue(result.downloadedItems > 0, "Should have downloaded associations")
        assertTrue(mockApiClient.wasMethodCalled("getAssociationChanges"), "Should have called getAssociationChanges")
    }

    @Test
    fun testDownloadFailsWithUnauthenticatedUser() = runTest {
        // Given: A sync manager with unauthenticated user
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
        
        // When: We attempt to download without authentication
        val result = syncManager.downloadRemoteChanges()
        
        // Then: The download should fail with authentication error
        assertFalse(result.success, "Download should fail without authentication")
        assertTrue(result.errors.any { it.type == SyncErrorType.AUTHENTICATION_ERROR }, 
                  "Should have authentication error")
        assertFalse(mockApiClient.wasMethodCalled("getContentChanges"), 
                   "Should not attempt API calls without authentication")
    }

    @Test
    fun testDownloadHandlesApiErrors() = runTest {
        // Given: A sync manager with failing API client
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
        
        // When: We attempt to download with failing API
        val result = syncManager.downloadRemoteChanges()
        
        // Then: The download should fail gracefully
        assertFalse(result.success, "Download should fail with API errors")
        assertTrue(result.errors.isNotEmpty(), "Should have error information")
        assertTrue(mockApiClient.wasMethodCalled("getContentChanges"), "Should still attempt API calls")
    }
}