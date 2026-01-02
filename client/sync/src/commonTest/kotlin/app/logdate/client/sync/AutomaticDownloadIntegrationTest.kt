package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.cloud.AssociationChange
import app.logdate.client.sync.cloud.AssociationChangesResponse
import app.logdate.client.sync.cloud.AssociationDeletion
import app.logdate.client.sync.cloud.ContentChange
import app.logdate.client.sync.cloud.ContentChangesResponse
import app.logdate.client.sync.cloud.ContentDeletion
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.cloud.JournalChange
import app.logdate.client.sync.cloud.JournalChangesResponse
import app.logdate.client.sync.cloud.JournalDeletion
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.test.*
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

/**
 * Integration tests for automatic download functionality.
 * Tests that remote changes are automatically downloaded and applied locally.
 */
class AutomaticDownloadIntegrationTest {

    @Test
    fun testDownloadNewRemoteContent() = runTest {
        // Given: A sync manager with authenticated account and API client that returns remote changes
        val mockApiClient = fakeCloudApiClient()
        val mockAccountRepository = fakeAccountRepository()
        val mockJournalNotesRepository = FakeJournalNotesRepository()
        val mockJournalRepository = FakeJournalRepository()
        val mockJournalContentRepository = FakeJournalContentRepository()
        
        // Configure API client to return remote content changes
        val remoteNote = JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = Clock.System.now(),
            lastUpdated = Clock.System.now(),
            content = "Remote content from server"
        )
        
        mockApiClient.getContentChangesResponse = Result.success(
            ContentChangesResponse(
                changes = listOf(
                    ContentChange(
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
            sessionStorage = fakeSessionStorage(),
            journalRepository = mockJournalRepository,
            journalNotesRepository = mockJournalNotesRepository,
            journalContentRepository = mockJournalContentRepository,
            journalConflictResolver = lastWriteWinsResolver(),
            noteConflictResolver = lastWriteWinsResolver(),
            syncMetadataService = fakeSyncMetadataService()
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
        val mockApiClient = fakeCloudApiClient()
        val mockAccountRepository = fakeAccountRepository()
        val mockJournalNotesRepository = FakeJournalNotesRepository()
        val mockJournalRepository = FakeJournalRepository()
        val mockJournalContentRepository = FakeJournalContentRepository()
        
        // Add existing local note
        val existingNote = mockJournalNotesRepository.addTestNote("Original content")
        
        // Configure API client to return updated version of the same note with a clearly newer timestamp
        val updatedTime = Clock.System.now().plus(1.hours)
        mockApiClient.getContentChangesResponse = Result.success(
            ContentChangesResponse(
                changes = listOf(
                    ContentChange(
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
            sessionStorage = fakeSessionStorage(),
            journalRepository = mockJournalRepository,
            journalNotesRepository = mockJournalNotesRepository,
            journalContentRepository = mockJournalContentRepository,
            journalConflictResolver = lastWriteWinsResolver(),
            noteConflictResolver = lastWriteWinsResolver(),
            syncMetadataService = fakeSyncMetadataService()
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
        val mockApiClient = fakeCloudApiClient()
        val mockAccountRepository = fakeAccountRepository()
        val mockJournalNotesRepository = FakeJournalNotesRepository()
        val mockJournalRepository = FakeJournalRepository()
        val mockJournalContentRepository = FakeJournalContentRepository()
        
        val deletedNoteId = Uuid.random()
        
        // Configure API client to return content deletion
        mockApiClient.getContentChangesResponse = Result.success(
            ContentChangesResponse(
                changes = emptyList(),
                deletions = listOf(
                    ContentDeletion(
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
            sessionStorage = fakeSessionStorage(),
            journalRepository = mockJournalRepository,
            journalNotesRepository = mockJournalNotesRepository,
            journalContentRepository = mockJournalContentRepository,
            journalConflictResolver = lastWriteWinsResolver(),
            noteConflictResolver = lastWriteWinsResolver(),
            syncMetadataService = fakeSyncMetadataService()
        )

        // When: We download remote changes
        val result = syncManager.downloadRemoteChanges()
        
        // Then: The download should succeed
        assertTrue(result.success, "Download should succeed")
        assertTrue(result.downloadedItems > 0, "Should have processed deletions")
        assertTrue(mockApiClient.wasMethodCalled("getContentChanges"), "Should have called getContentChanges")
    }

    @Test
    fun testDownloadSkipsPendingContentDeletion() = runTest {
        val mockApiClient = fakeCloudApiClient()
        val mockNotesRepository = FakeJournalNotesRepository()
        val mockJournalRepository = FakeJournalRepository()
        val mockJournalContentRepository = FakeJournalContentRepository()
        val syncMetadataService = fakeSyncMetadataService()

        val localNote = mockNotesRepository.addTestNote("Local pending note")
        syncMetadataService.addPending(localNote.uid, EntityType.NOTE)

        mockApiClient.getContentChangesResponse = Result.success(
            ContentChangesResponse(
                changes = emptyList(),
                deletions = listOf(
                    ContentDeletion(
                        id = localNote.uid.toString(),
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
            cloudAccountRepository = fakeAccountRepository(),
            sessionStorage = fakeSessionStorage(),
            journalRepository = mockJournalRepository,
            journalNotesRepository = mockNotesRepository,
            journalContentRepository = mockJournalContentRepository,
            journalConflictResolver = lastWriteWinsResolver(),
            noteConflictResolver = lastWriteWinsResolver(),
            syncMetadataService = syncMetadataService
        )

        val result = syncManager.downloadRemoteChanges()
        val remainingNotes = mockNotesRepository.allNotesObserved.first()

        assertTrue(remainingNotes.any { it.uid == localNote.uid }, "Pending note should not be deleted")
        assertTrue(result.conflictsResolved > 0, "Should register a conflict when skipping deletion")
    }

    @Test
    fun testDownloadSkipsDeletionWhenLocalNoteUpdatedAfterLastSync() = runTest {
        val mockApiClient = fakeCloudApiClient()
        val mockNotesRepository = FakeJournalNotesRepository()
        val mockJournalRepository = FakeJournalRepository()
        val mockJournalContentRepository = FakeJournalContentRepository()

        val syncManager = DefaultSyncManager(
            cloudContentDataSource = DefaultCloudContentDataSource(mockApiClient),
            cloudJournalDataSource = DefaultCloudJournalDataSource(mockApiClient),
            cloudAssociationDataSource = DefaultCloudAssociationDataSource(mockApiClient),
            cloudMediaDataSource = DefaultCloudMediaDataSource(mockApiClient),
            cloudAccountRepository = fakeAccountRepository(),
            sessionStorage = fakeSessionStorage(),
            journalRepository = mockJournalRepository,
            journalNotesRepository = mockNotesRepository,
            journalContentRepository = mockJournalContentRepository,
            journalConflictResolver = lastWriteWinsResolver(),
            noteConflictResolver = lastWriteWinsResolver(),
            syncMetadataService = fakeSyncMetadataService()
        )

        mockApiClient.getContentChangesResponse = Result.success(
            ContentChangesResponse(
                changes = emptyList(),
                deletions = emptyList(),
                lastTimestamp = Clock.System.now().toEpochMilliseconds()
            )
        )
        val firstResult = syncManager.downloadRemoteChanges()
        assertTrue(firstResult.success, "Initial download should succeed")

        val lastSyncTime = syncManager.getSyncStatus().lastSyncTime
        assertNotNull(lastSyncTime, "Sync should record last sync time")

        val localNote = JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = lastSyncTime,
            lastUpdated = lastSyncTime.plus(1.seconds),
            content = "Local update after sync"
        )
        mockNotesRepository.create(localNote)

        mockApiClient.getContentChangesResponse = Result.success(
            ContentChangesResponse(
                changes = emptyList(),
                deletions = listOf(
                    ContentDeletion(
                        id = localNote.uid.toString(),
                        deletedAt = Clock.System.now().toEpochMilliseconds()
                    )
                ),
                lastTimestamp = Clock.System.now().toEpochMilliseconds()
            )
        )

        val result = syncManager.downloadRemoteChanges()
        val remainingNotes = mockNotesRepository.allNotesObserved.first()

        assertTrue(remainingNotes.any { it.uid == localNote.uid }, "Updated note should not be deleted")
        assertTrue(result.conflictsResolved > 0, "Should register a conflict when skipping deletion")
    }

    @Test
    fun testDownloadDoesNotAdvanceCursorWhenApplyFails() = runTest {
        val mockApiClient = fakeCloudApiClient()
        val syncMetadataService = fakeSyncMetadataService()

        val failingNotesRepository = FailingJournalNotesRepository()
        val mockJournalRepository = FakeJournalRepository()
        val mockJournalContentRepository = FakeJournalContentRepository()

        val remoteNote = JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = Clock.System.now(),
            lastUpdated = Clock.System.now(),
            content = "Remote note"
        )

        val remoteTimestamp = Clock.System.now().toEpochMilliseconds()
        mockApiClient.getContentChangesResponse = Result.success(
            ContentChangesResponse(
                changes = listOf(
                    ContentChange(
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
                lastTimestamp = remoteTimestamp
            )
        )

        val syncManager = DefaultSyncManager(
            cloudContentDataSource = DefaultCloudContentDataSource(mockApiClient),
            cloudJournalDataSource = DefaultCloudJournalDataSource(mockApiClient),
            cloudAssociationDataSource = DefaultCloudAssociationDataSource(mockApiClient),
            cloudMediaDataSource = DefaultCloudMediaDataSource(mockApiClient),
            cloudAccountRepository = fakeAccountRepository(),
            sessionStorage = fakeSessionStorage(),
            journalRepository = mockJournalRepository,
            journalNotesRepository = failingNotesRepository,
            journalContentRepository = mockJournalContentRepository,
            journalConflictResolver = lastWriteWinsResolver(),
            noteConflictResolver = lastWriteWinsResolver(),
            syncMetadataService = syncMetadataService
        )

        val result = syncManager.downloadRemoteChanges()

        assertFalse(result.success, "Download should fail when applying a change throws")
        assertEquals(
            null,
            syncMetadataService.getLastSyncTime(EntityType.NOTE),
            "Cursor should not advance when applying changes fails"
        )
    }

    @Test
    fun testDownloadSkipsPendingAssociationDeletion() = runTest {
        val mockApiClient = fakeCloudApiClient()
        val syncMetadataService = fakeSyncMetadataService()
        val mockJournalRepository = FakeJournalRepository()
        val mockNotesRepository = FakeJournalNotesRepository()
        val trackingContentRepository = TrackingJournalContentRepository()

        val journalId = Uuid.random()
        val contentId = Uuid.random()

        trackingContentRepository.addContentToJournal(contentId, journalId)
        syncMetadataService.enqueuePending(
            entityId = AssociationPendingKey(journalId, contentId).toPendingId(),
            entityType = EntityType.ASSOCIATION,
            operation = PendingOperation.DELETE
        )

        mockApiClient.getAssociationChangesResponse = Result.success(
            AssociationChangesResponse(
                changes = emptyList(),
                deletions = listOf(
                    AssociationDeletion(
                        journalId = journalId.toString(),
                        contentId = contentId.toString(),
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
            cloudAccountRepository = fakeAccountRepository(),
            sessionStorage = fakeSessionStorage(),
            journalRepository = mockJournalRepository,
            journalNotesRepository = mockNotesRepository,
            journalContentRepository = trackingContentRepository,
            journalConflictResolver = lastWriteWinsResolver(),
            noteConflictResolver = lastWriteWinsResolver(),
            syncMetadataService = syncMetadataService
        )

        val result = syncManager.downloadRemoteChanges()

        assertTrue(
            trackingContentRepository.hasAssociation(contentId, journalId),
            "Pending association should not be deleted"
        )
        assertTrue(result.conflictsResolved > 0, "Should register a conflict when skipping association deletion")
    }

    @Test
    fun testDownloadRemoteJournalChanges() = runTest {
        // Given: A sync manager with API client that returns journal changes
        val mockApiClient = fakeCloudApiClient()
        val mockAccountRepository = fakeAccountRepository()
        val mockJournalNotesRepository = FakeJournalNotesRepository()
        val mockJournalRepository = FakeJournalRepository()
        val mockJournalContentRepository = FakeJournalContentRepository()
        
        val remoteJournal = Journal(
            id = Uuid.random(),
            title = "Remote Journal",
            description = "Journal from server",
            created = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )
        
        // Configure API client to return journal changes
        mockApiClient.getJournalChangesResponse = Result.success(
            JournalChangesResponse(
                changes = listOf(
                    JournalChange(
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
            sessionStorage = fakeSessionStorage(),
            journalRepository = mockJournalRepository,
            journalNotesRepository = mockJournalNotesRepository,
            journalContentRepository = mockJournalContentRepository,
            journalConflictResolver = lastWriteWinsResolver(),
            noteConflictResolver = lastWriteWinsResolver(),
            syncMetadataService = fakeSyncMetadataService()
        )

        // When: We download remote changes
        val result = syncManager.downloadRemoteChanges()
        
        // Then: The download should succeed
        assertTrue(result.success, "Download should succeed")
        assertTrue(result.downloadedItems > 0, "Should have downloaded journals")
        assertTrue(mockApiClient.wasMethodCalled("getJournalChanges"), "Should have called getJournalChanges")
    }

    @Test
    fun testDownloadSkipsPendingJournalDeletion() = runTest {
        val mockApiClient = fakeCloudApiClient()
        val mockNotesRepository = FakeJournalNotesRepository()
        val mockJournalRepository = FakeJournalRepository()
        val mockJournalContentRepository = FakeJournalContentRepository()
        val syncMetadataService = fakeSyncMetadataService()

        val localJournal = Journal(
            id = Uuid.random(),
            title = "Pending Journal",
            description = "Local pending journal",
            created = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )

        mockJournalRepository.create(localJournal)
        syncMetadataService.addPending(localJournal.id, EntityType.JOURNAL)

        mockApiClient.getJournalChangesResponse = Result.success(
            JournalChangesResponse(
                changes = emptyList(),
                deletions = listOf(
                    JournalDeletion(
                        id = localJournal.id.toString(),
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
            cloudAccountRepository = fakeAccountRepository(),
            sessionStorage = fakeSessionStorage(),
            journalRepository = mockJournalRepository,
            journalNotesRepository = mockNotesRepository,
            journalContentRepository = mockJournalContentRepository,
            journalConflictResolver = lastWriteWinsResolver(),
            noteConflictResolver = lastWriteWinsResolver(),
            syncMetadataService = syncMetadataService
        )

        val result = syncManager.downloadRemoteChanges()
        val remainingJournals = mockJournalRepository.allJournalsObserved.first()

        assertTrue(remainingJournals.any { it.id == localJournal.id }, "Pending journal should not be deleted")
        assertTrue(result.conflictsResolved > 0, "Should register a conflict when skipping deletion")
    }

    @Test
    fun testDownloadSkipsDeletionWhenLocalJournalUpdatedAfterLastSync() = runTest {
        val mockApiClient = fakeCloudApiClient()
        val mockNotesRepository = FakeJournalNotesRepository()
        val mockJournalRepository = FakeJournalRepository()
        val mockJournalContentRepository = FakeJournalContentRepository()

        val syncManager = DefaultSyncManager(
            cloudContentDataSource = DefaultCloudContentDataSource(mockApiClient),
            cloudJournalDataSource = DefaultCloudJournalDataSource(mockApiClient),
            cloudAssociationDataSource = DefaultCloudAssociationDataSource(mockApiClient),
            cloudMediaDataSource = DefaultCloudMediaDataSource(mockApiClient),
            cloudAccountRepository = fakeAccountRepository(),
            sessionStorage = fakeSessionStorage(),
            journalRepository = mockJournalRepository,
            journalNotesRepository = mockNotesRepository,
            journalContentRepository = mockJournalContentRepository,
            journalConflictResolver = lastWriteWinsResolver(),
            noteConflictResolver = lastWriteWinsResolver(),
            syncMetadataService = fakeSyncMetadataService()
        )

        mockApiClient.getJournalChangesResponse = Result.success(
            JournalChangesResponse(
                changes = emptyList(),
                deletions = emptyList(),
                lastTimestamp = Clock.System.now().toEpochMilliseconds()
            )
        )
        val firstResult = syncManager.downloadRemoteChanges()
        assertTrue(firstResult.success, "Initial download should succeed")

        val lastSyncTime = syncManager.getSyncStatus().lastSyncTime
        assertNotNull(lastSyncTime, "Sync should record last sync time")

        val localJournal = Journal(
            id = Uuid.random(),
            title = "Local Journal",
            description = "Local update after sync",
            created = lastSyncTime,
            lastUpdated = lastSyncTime.plus(1.seconds)
        )
        mockJournalRepository.create(localJournal)

        mockApiClient.getJournalChangesResponse = Result.success(
            JournalChangesResponse(
                changes = emptyList(),
                deletions = listOf(
                    JournalDeletion(
                        id = localJournal.id.toString(),
                        deletedAt = Clock.System.now().toEpochMilliseconds()
                    )
                ),
                lastTimestamp = Clock.System.now().toEpochMilliseconds()
            )
        )

        val result = syncManager.downloadRemoteChanges()
        val remainingJournals = mockJournalRepository.allJournalsObserved.first()

        assertTrue(remainingJournals.any { it.id == localJournal.id }, "Updated journal should not be deleted")
        assertTrue(result.conflictsResolved > 0, "Should register a conflict when skipping deletion")
    }

    @Test
    fun testDownloadRemoteAssociationChanges() = runTest {
        // Given: A sync manager with API client that returns association changes
        val mockApiClient = fakeCloudApiClient()
        val mockAccountRepository = fakeAccountRepository()
        val mockJournalNotesRepository = FakeJournalNotesRepository()
        val mockJournalRepository = FakeJournalRepository()
        val mockJournalContentRepository = FakeJournalContentRepository()
        
        val journalId = Uuid.random()
        val contentId = Uuid.random()
        
        // Configure API client to return association changes
        mockApiClient.getAssociationChangesResponse = Result.success(
            AssociationChangesResponse(
                changes = listOf(
                    AssociationChange(
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
            sessionStorage = fakeSessionStorage(),
            journalRepository = mockJournalRepository,
            journalNotesRepository = mockJournalNotesRepository,
            journalContentRepository = mockJournalContentRepository,
            journalConflictResolver = lastWriteWinsResolver(),
            noteConflictResolver = lastWriteWinsResolver(),
            syncMetadataService = fakeSyncMetadataService()
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
        val mockApiClient = fakeCloudApiClient()
        val mockAccountRepository = fakeAccountRepository(authenticated = false)
        val mockJournalNotesRepository = FakeJournalNotesRepository()
        val mockJournalRepository = FakeJournalRepository()
        val mockJournalContentRepository = FakeJournalContentRepository()
        
        val syncManager = DefaultSyncManager(
            cloudContentDataSource = DefaultCloudContentDataSource(mockApiClient),
            cloudJournalDataSource = DefaultCloudJournalDataSource(mockApiClient),
            cloudAssociationDataSource = DefaultCloudAssociationDataSource(mockApiClient),
            cloudMediaDataSource = DefaultCloudMediaDataSource(mockApiClient),
            cloudAccountRepository = mockAccountRepository,
            sessionStorage = fakeSessionStorage(authenticated = false),
            journalRepository = mockJournalRepository,
            journalNotesRepository = mockJournalNotesRepository,
            journalContentRepository = mockJournalContentRepository,
            journalConflictResolver = lastWriteWinsResolver(),
            noteConflictResolver = lastWriteWinsResolver(),
            syncMetadataService = fakeSyncMetadataService()
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
        val mockApiClient = failingCloudApiClient()
        val mockAccountRepository = fakeAccountRepository()
        val mockJournalNotesRepository = FakeJournalNotesRepository()
        val mockJournalRepository = FakeJournalRepository()
        val mockJournalContentRepository = FakeJournalContentRepository()
        
        val syncManager = DefaultSyncManager(
            cloudContentDataSource = DefaultCloudContentDataSource(mockApiClient),
            cloudJournalDataSource = DefaultCloudJournalDataSource(mockApiClient),
            cloudAssociationDataSource = DefaultCloudAssociationDataSource(mockApiClient),
            cloudMediaDataSource = DefaultCloudMediaDataSource(mockApiClient),
            cloudAccountRepository = mockAccountRepository,
            sessionStorage = fakeSessionStorage(),
            journalRepository = mockJournalRepository,
            journalNotesRepository = mockJournalNotesRepository,
            journalContentRepository = mockJournalContentRepository,
            journalConflictResolver = lastWriteWinsResolver(),
            noteConflictResolver = lastWriteWinsResolver(),
            syncMetadataService = fakeSyncMetadataService()
        )

        // When: We attempt to download with failing API
        val result = syncManager.downloadRemoteChanges()
        
        // Then: The download should fail gracefully
        assertFalse(result.success, "Download should fail with API errors")
        assertTrue(result.errors.isNotEmpty(), "Should have error information")
        assertTrue(mockApiClient.wasMethodCalled("getContentChanges"), "Should still attempt API calls")
    }
}
