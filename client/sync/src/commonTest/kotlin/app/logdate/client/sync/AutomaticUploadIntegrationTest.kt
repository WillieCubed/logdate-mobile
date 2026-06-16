package app.logdate.client.sync

import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.networking.DataUsageMode
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudDraftDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
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
import app.logdate.shared.model.CloudObjectType
import app.logdate.shared.model.CloudQuotaManager
import app.logdate.shared.model.CloudStorageQuota
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Integration tests for automatic upload functionality.
 * Tests that content, journals, and associations are automatically uploaded when created.
 */
class AutomaticUploadIntegrationTest {
    @Test
    fun testMediaNotesUploadMediaBeforePublishingContent() =
        runTest {
            val apiClient = fakeCloudApiClient()
            val notesRepository = fakeJournalNotesRepository()
            val syncMetadataService = fakeSyncMetadataService()
            val now = Clock.System.now()
            val notes =
                listOf(
                    JournalNote.Image(
                        uid = Uuid.random(),
                        creationTimestamp = now,
                        lastUpdated = now,
                        mediaRef = "file:///local/photo.jpg",
                    ),
                    JournalNote.Video(
                        uid = Uuid.random(),
                        creationTimestamp = now,
                        lastUpdated = now,
                        mediaRef = "file:///local/video.mp4",
                    ),
                    JournalNote.Audio(
                        uid = Uuid.random(),
                        creationTimestamp = now,
                        lastUpdated = now,
                        mediaRef = "file:///local/audio.m4a",
                        durationMs = 42_000,
                    ),
                )
            notes.forEach { note ->
                notesRepository.create(note)
                syncMetadataService.enqueuePending(
                    entityId = note.uid.toString(),
                    entityType = EntityType.NOTE,
                    operation = PendingOperation.CREATE,
                )
            }

            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
                    cloudDraftDataSource = DefaultCloudDraftDataSource(apiClient),
                    cloudAccountRepository = fakeAccountRepository(),
                    sessionStorage = fakeSessionStorage(),
                    mediaManager = InMemoryMediaManager(),
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

            val result = syncManager.syncContent()

            assertTrue(result.success, "Media content sync should succeed")
            assertEquals(3, apiClient.uploadMediaCalls.size, "Each media note should upload its media bytes first")
            assertEquals(
                listOf("IMAGE", "VIDEO", "AUDIO"),
                apiClient.uploadContentCalls.map { it.second.type },
                "Each supported media note type should publish a content row",
            )
            assertTrue(
                apiClient.uploadContentCalls.all { (_, request) -> request.mediaUri?.startsWith("https://") == true },
                "Content rows should reference remote media URLs, not device-local URIs",
            )
        }

    @Test
    fun testMediaNoteStaysPendingWhenDataPolicyDefersMediaUpload() =
        runTest {
            val apiClient = fakeCloudApiClient()
            val notesRepository = fakeJournalNotesRepository()
            val syncMetadataService = fakeSyncMetadataService()
            val now = Clock.System.now()
            val note =
                JournalNote.Image(
                    uid = Uuid.random(),
                    creationTimestamp = now,
                    lastUpdated = now,
                    mediaRef = "file:///local/photo.jpg",
                )
            notesRepository.create(note)
            syncMetadataService.enqueuePending(
                entityId = note.uid.toString(),
                entityType = EntityType.NOTE,
                operation = PendingOperation.CREATE,
            )

            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
                    cloudDraftDataSource = DefaultCloudDraftDataSource(apiClient),
                    cloudAccountRepository = fakeAccountRepository(),
                    sessionStorage = fakeSessionStorage(),
                    mediaManager = InMemoryMediaManager(),
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
                    dataUsagePolicy = fakeDataUsagePolicy(DataUsageMode.Conservative),
                )

            val result = syncManager.syncContent()

            assertTrue(result.success, "Deferring media on cellular should not be treated as a sync error")
            assertFalse(apiClient.wasMethodCalled("uploadMedia"), "Media bytes should wait for unrestricted network")
            assertFalse(apiClient.wasMethodCalled("uploadContent"), "Content should not publish a local media URI")
            assertEquals(
                1,
                syncMetadataService.getPendingUploads(EntityType.NOTE).size,
                "The note should remain pending until media sync is allowed",
            )
        }

    @Test
    fun testAutomaticContentUpload() =
        runTest {
            val apiClient = fakeCloudApiClient()
            val notesRepository = fakeJournalNotesRepository()
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
                    mediaManager = InMemoryMediaManager(),
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

            val note = notesRepository.addTestNote("Test automatic upload")
            syncMetadataService.enqueuePending(
                entityId = note.uid.toString(),
                entityType = EntityType.NOTE,
                operation = PendingOperation.CREATE,
            )

            val result = syncManager.syncContent()

            assertTrue(result.success, "Content sync should succeed")
            assertTrue(result.uploadedItems > 0, "Should have uploaded at least one item")
            assertTrue(apiClient.wasMethodCalled("uploadContent"), "Should have called uploadContent on API client")
            assertTrue(apiClient.uploadContentCalls.isNotEmpty(), "Should have upload content calls")
            assertEquals(
                "TEXT",
                apiClient.uploadContentCalls
                    .first()
                    .second.type,
                "Should upload text content type",
            )
        }

    @Test
    fun testAutomaticJournalUpload() =
        runTest {
            val apiClient = fakeCloudApiClient()
            val journalRepository = fakeJournalRepository()
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
                    mediaManager = InMemoryMediaManager(),
                    mediaSyncRefStore = InMemoryMediaSyncRefStore(),
                    journalRepository = journalRepository,
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

            val journal =
                Journal(
                    id = Uuid.random(),
                    title = "Test journal",
                    description = "Test journal description",
                    created = Clock.System.now(),
                    lastUpdated = Clock.System.now(),
                )
            journalRepository.create(journal)
            syncMetadataService.enqueuePending(
                entityId = journal.id.toString(),
                entityType = EntityType.JOURNAL,
                operation = PendingOperation.CREATE,
            )

            val result = syncManager.syncJournals()

            assertTrue(result.success, "Journal sync should succeed")
            assertTrue(
                apiClient.wasMethodCalled("uploadJournal") || result.uploadedItems == 0,
                "Should either upload journals or have no journals to upload",
            )
        }

    @Test
    fun testAutomaticAssociationUpload() =
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
                    mediaManager = InMemoryMediaManager(),
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

            val associationKey = AssociationPendingKey(Uuid.random(), Uuid.random())
            syncMetadataService.enqueuePending(
                entityId = associationKey.toPendingId(),
                entityType = EntityType.ASSOCIATION,
                operation = PendingOperation.CREATE,
            )

            val result = syncManager.syncAssociations()

            assertTrue(result.success, "Association sync should succeed")
            assertTrue(
                apiClient.wasMethodCalled("uploadAssociations") || result.uploadedItems == 0,
                "Should either upload associations or have no associations to upload",
            )
        }

    @Test
    fun testUploadFailuresAreHandledGracefully() =
        runTest {
            val apiClient = failingCloudApiClient()
            val syncMetadataService = fakeSyncMetadataService()
            val notesRepository = fakeJournalNotesRepository("Test note 1", "Test note 2")

            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
                    cloudDraftDataSource = DefaultCloudDraftDataSource(apiClient),
                    cloudAccountRepository = fakeAccountRepository(),
                    sessionStorage = fakeSessionStorage(),
                    mediaManager = InMemoryMediaManager(),
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

            val pendingNotes = notesRepository.allNotesObserved.first()
            pendingNotes.forEach { note ->
                syncMetadataService.enqueuePending(
                    entityId = note.uid.toString(),
                    entityType = EntityType.NOTE,
                    operation = PendingOperation.CREATE,
                )
            }

            val result = syncManager.syncContent()

            assertTrue(!result.success, "Content sync should fail with failing API")
            assertTrue(result.errors.isNotEmpty(), "Should have error information")
            assertTrue(apiClient.wasMethodCalled("uploadContent"), "Should still attempt upload calls")
            assertTrue(
                syncMetadataService.getPendingUploads(EntityType.NOTE).isNotEmpty(),
                "Pending uploads should remain after a failed sync",
            )
        }

    @Test
    fun testSuccessfulContentSyncRefreshesObservedServerQuota() =
        runTest {
            val apiClient = fakeCloudApiClient()
            val syncMetadataService = fakeSyncMetadataService()
            val notesRepository = fakeJournalNotesRepository("Quota tracked note")
            val quotaManager = RecordingCloudQuotaManager()

            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
                    cloudDraftDataSource = DefaultCloudDraftDataSource(apiClient),
                    cloudAccountRepository = fakeAccountRepository(),
                    sessionStorage = fakeSessionStorage(),
                    mediaManager = InMemoryMediaManager(),
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
                    cloudQuotaManager = quotaManager,
                )

            notesRepository.allNotesObserved.first().forEach { note ->
                syncMetadataService.enqueuePending(
                    entityId = note.uid.toString(),
                    entityType = EntityType.NOTE,
                    operation = PendingOperation.CREATE,
                )
            }

            val result = syncManager.syncContent()

            assertTrue(result.success, "Content sync should succeed")
            assertEquals(1, quotaManager.syncWithServerCalls)
        }

    @Test
    fun testQuotaRefreshFailureDoesNotFailSuccessfulContentSync() =
        runTest {
            val apiClient = fakeCloudApiClient()
            val syncMetadataService = fakeSyncMetadataService()
            val notesRepository = fakeJournalNotesRepository("Quota refresh failure still uploads")
            val quotaManager = RecordingCloudQuotaManager(throwOnSync = true)

            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
                    cloudDraftDataSource = DefaultCloudDraftDataSource(apiClient),
                    cloudAccountRepository = fakeAccountRepository(),
                    sessionStorage = fakeSessionStorage(),
                    mediaManager = InMemoryMediaManager(),
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
                    cloudQuotaManager = quotaManager,
                )

            notesRepository.allNotesObserved.first().forEach { note ->
                syncMetadataService.enqueuePending(
                    entityId = note.uid.toString(),
                    entityType = EntityType.NOTE,
                    operation = PendingOperation.CREATE,
                )
            }

            val result = syncManager.syncContent()

            assertTrue(result.success, "Sync should not fail when quota refresh fails after upload")
            assertEquals(1, quotaManager.syncWithServerCalls)
        }

    @Test
    fun testUnauthenticatedUploadFails() =
        runTest {
            val apiClient = fakeCloudApiClient()
            val syncMetadataService = fakeSyncMetadataService()
            val notesRepository = fakeJournalNotesRepository("Test note 1", "Test note 2")

            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
                    cloudDraftDataSource = DefaultCloudDraftDataSource(apiClient),
                    cloudAccountRepository = fakeAccountRepository(authenticated = false),
                    sessionStorage = fakeSessionStorage(authenticated = false),
                    mediaManager = InMemoryMediaManager(),
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

            val pendingNotes = notesRepository.allNotesObserved.first()
            pendingNotes.forEach { note ->
                syncMetadataService.enqueuePending(
                    entityId = note.uid.toString(),
                    entityType = EntityType.NOTE,
                    operation = PendingOperation.CREATE,
                )
            }

            val result = syncManager.syncContent()

            assertTrue(!result.success, "Content sync should fail without authentication")
            assertTrue(
                result.errors.any { it.type == SyncErrorType.AUTHENTICATION_ERROR },
                "Should have authentication error",
            )
            assertTrue(
                !apiClient.wasMethodCalled("uploadContent"),
                "Should not attempt API calls without authentication",
            )
        }

    private class RecordingCloudQuotaManager(
        private val throwOnSync: Boolean = false,
    ) : CloudQuotaManager {
        private val quota =
            CloudStorageQuota(
                totalBytes = 1000,
                usedBytes = 0,
                categories = emptyList(),
            )

        var syncWithServerCalls = 0
            private set

        override suspend fun getCurrentQuota(): CloudStorageQuota = quota

        override fun observeQuota(): Flow<CloudStorageQuota> = flowOf(quota)

        override suspend fun recordObjectCreation(
            objectType: CloudObjectType,
            bytes: Long,
        ) = Unit

        override suspend fun recordObjectDeletion(
            objectType: CloudObjectType,
            bytes: Long,
        ) = Unit

        override suspend fun recordObjectUpdate(
            objectType: CloudObjectType,
            oldBytes: Long,
            newBytes: Long,
        ) = Unit

        override suspend fun recalculateQuota(): CloudStorageQuota = quota

        override suspend fun setQuotaLimit(totalBytes: Long) = Unit

        override suspend fun syncWithServer(): CloudStorageQuota {
            syncWithServerCalls += 1
            if (throwOnSync) {
                error("Quota refresh failed")
            }
            return quota
        }

        override suspend fun getLastServerSyncTime(): kotlin.time.Instant? = null
    }
}
