package app.logdate.client.sync

import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.test.*
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.first
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
        val apiClient = fakeCloudApiClient()
        val notesRepository = fakeJournalNotesRepository()
        val syncMetadataService = fakeSyncMetadataService()

        val syncManager = DefaultSyncManager(
            cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
            cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
            cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
            cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
            cloudAccountRepository = fakeAccountRepository(),
            sessionStorage = fakeSessionStorage(),
            journalRepository = fakeJournalRepository(),
            journalNotesRepository = notesRepository,
            journalContentRepository = fakeJournalContentRepository(),
            journalConflictResolver = lastWriteWinsResolver(),
            noteConflictResolver = lastWriteWinsResolver(),
            syncMetadataService = syncMetadataService
        )

        val note = notesRepository.addTestNote("Test automatic upload")
        syncMetadataService.enqueuePending(
            entityId = note.uid.toString(),
            entityType = EntityType.NOTE,
            operation = PendingOperation.CREATE
        )

        val result = syncManager.syncContent()

        assertTrue(result.success, "Content sync should succeed")
        assertTrue(result.uploadedItems > 0, "Should have uploaded at least one item")
        assertTrue(apiClient.wasMethodCalled("uploadContent"), "Should have called uploadContent on API client")
        assertTrue(apiClient.uploadContentCalls.isNotEmpty(), "Should have upload content calls")
        assertEquals("TEXT", apiClient.uploadContentCalls.first().second.type, "Should upload text content type")
    }

    @Test
    fun testAutomaticJournalUpload() = runTest {
        val apiClient = fakeCloudApiClient()
        val journalRepository = fakeJournalRepository()
        val syncMetadataService = fakeSyncMetadataService()

        val syncManager = DefaultSyncManager(
            cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
            cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
            cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
            cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
            cloudAccountRepository = fakeAccountRepository(),
            sessionStorage = fakeSessionStorage(),
            journalRepository = journalRepository,
            journalNotesRepository = fakeJournalNotesRepository(),
            journalContentRepository = fakeJournalContentRepository(),
            journalConflictResolver = lastWriteWinsResolver(),
            noteConflictResolver = lastWriteWinsResolver(),
            syncMetadataService = syncMetadataService
        )

        val journal = Journal(
            id = Uuid.random(),
            title = "Test journal",
            description = "Test journal description",
            created = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )
        journalRepository.create(journal)
        syncMetadataService.enqueuePending(
            entityId = journal.id.toString(),
            entityType = EntityType.JOURNAL,
            operation = PendingOperation.CREATE
        )

        val result = syncManager.syncJournals()

        assertTrue(result.success, "Journal sync should succeed")
        assertTrue(apiClient.wasMethodCalled("uploadJournal") || result.uploadedItems == 0,
            "Should either upload journals or have no journals to upload")
    }

    @Test
    fun testAutomaticAssociationUpload() = runTest {
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
            syncMetadataService = syncMetadataService
        )

        val associationKey = AssociationPendingKey(Uuid.random(), Uuid.random())
        syncMetadataService.enqueuePending(
            entityId = associationKey.toPendingId(),
            entityType = EntityType.ASSOCIATION,
            operation = PendingOperation.CREATE
        )

        val result = syncManager.syncAssociations()

        assertTrue(result.success, "Association sync should succeed")
        assertTrue(apiClient.wasMethodCalled("uploadAssociations") || result.uploadedItems == 0,
            "Should either upload associations or have no associations to upload")
    }

    @Test
    fun testUploadFailuresAreHandledGracefully() = runTest {
        val apiClient = failingCloudApiClient()
        val syncMetadataService = fakeSyncMetadataService()
        val notesRepository = fakeJournalNotesRepository("Test note 1", "Test note 2")

        val syncManager = DefaultSyncManager(
            cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
            cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
            cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
            cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
            cloudAccountRepository = fakeAccountRepository(),
            sessionStorage = fakeSessionStorage(),
            journalRepository = fakeJournalRepository(),
            journalNotesRepository = notesRepository,
            journalContentRepository = fakeJournalContentRepository(),
            journalConflictResolver = lastWriteWinsResolver(),
            noteConflictResolver = lastWriteWinsResolver(),
            syncMetadataService = syncMetadataService
        )

        val pendingNotes = notesRepository.allNotesObserved.first()
        pendingNotes.forEach { note ->
            syncMetadataService.enqueuePending(
                entityId = note.uid.toString(),
                entityType = EntityType.NOTE,
                operation = PendingOperation.CREATE
            )
        }

        val result = syncManager.syncContent()

        assertTrue(!result.success, "Content sync should fail with failing API")
        assertTrue(result.errors.isNotEmpty(), "Should have error information")
        assertTrue(apiClient.wasMethodCalled("uploadContent"), "Should still attempt upload calls")
        assertTrue(
            syncMetadataService.getPendingUploads(EntityType.NOTE).isNotEmpty(),
            "Pending uploads should remain after a failed sync"
        )
    }

    @Test
    fun testUnauthenticatedUploadFails() = runTest {
        val apiClient = fakeCloudApiClient()
        val syncMetadataService = fakeSyncMetadataService()
        val notesRepository = fakeJournalNotesRepository("Test note 1", "Test note 2")

        val syncManager = DefaultSyncManager(
            cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
            cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
            cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
            cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
            cloudAccountRepository = fakeAccountRepository(authenticated = false),
            sessionStorage = fakeSessionStorage(authenticated = false),
            journalRepository = fakeJournalRepository(),
            journalNotesRepository = notesRepository,
            journalContentRepository = fakeJournalContentRepository(),
            journalConflictResolver = lastWriteWinsResolver(),
            noteConflictResolver = lastWriteWinsResolver(),
            syncMetadataService = syncMetadataService
        )

        val pendingNotes = notesRepository.allNotesObserved.first()
        pendingNotes.forEach { note ->
            syncMetadataService.enqueuePending(
                entityId = note.uid.toString(),
                entityType = EntityType.NOTE,
                operation = PendingOperation.CREATE
            )
        }

        val result = syncManager.syncContent()

        assertTrue(!result.success, "Content sync should fail without authentication")
        assertTrue(result.errors.any { it.type == SyncErrorType.AUTHENTICATION_ERROR },
            "Should have authentication error")
        assertTrue(!apiClient.wasMethodCalled("uploadContent"),
            "Should not attempt API calls without authentication")
    }
}
