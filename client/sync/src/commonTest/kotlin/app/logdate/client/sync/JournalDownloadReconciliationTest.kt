package app.logdate.client.sync

import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.sync.cloud.AssociationChangesResponse
import app.logdate.client.sync.cloud.AssociationDeletion
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudDraftDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.cloud.JournalChange
import app.logdate.client.sync.cloud.JournalChangesResponse
import app.logdate.client.sync.cloud.JournalDeletion
import app.logdate.client.sync.conflict.KeyValueSyncConflictStore
import app.logdate.client.sync.conflict.SyncConflictRecord
import app.logdate.client.sync.conflict.SyncConflictStore
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.test.FakeJournalContentRepository
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.FakeJournalRepository
import app.logdate.client.sync.test.InMemoryMediaSyncRefStore
import app.logdate.client.sync.test.InMemorySyncConflictStore
import app.logdate.client.sync.test.InMemorySyncDeadLetterStore
import app.logdate.client.sync.test.InMemorySyncRetryScheduleStore
import app.logdate.client.sync.test.TrackingJournalContentRepository
import app.logdate.client.sync.test.fakeAccountRepository
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeDataUsagePolicy
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.lastWriteWinsResolver
import app.logdate.client.sync.test.testSyncTransactionManager
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class JournalDownloadReconciliationTest {
    @Test
    fun `download settles matching pending association deletion without conflict`() =
        runTest {
            val mockApiClient = fakeCloudApiClient()
            val syncMetadataService = fakeSyncMetadataService()
            val conflictStore = InMemorySyncConflictStore()
            val mockJournalRepository = FakeJournalRepository()
            val mockNotesRepository = FakeJournalNotesRepository()
            val trackingContentRepository = TrackingJournalContentRepository()

            val journalId = Uuid.random()
            val contentId = Uuid.random()

            trackingContentRepository.addContentToJournal(contentId, journalId)
            syncMetadataService.enqueuePending(
                entityId = AssociationPendingKey(journalId, contentId).toPendingId(),
                entityType = EntityType.ASSOCIATION,
                operation = PendingOperation.DELETE,
            )

            mockApiClient.getAssociationChangesResponse =
                Result.success(
                    AssociationChangesResponse(
                        changes = emptyList(),
                        deletions =
                            listOf(
                                AssociationDeletion(
                                    journalId = journalId.toString(),
                                    contentId = contentId.toString(),
                                    deletedAt = Clock.System.now().toEpochMilliseconds(),
                                ),
                            ),
                        lastTimestamp = Clock.System.now().toEpochMilliseconds(),
                    ),
                )

            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(mockApiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(mockApiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(mockApiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(mockApiClient),
                    cloudDraftDataSource = DefaultCloudDraftDataSource(mockApiClient),
                    cloudAccountRepository = fakeAccountRepository(),
                    sessionStorage = fakeSessionStorage(),
                    mediaManager = InMemoryMediaManager(),
                    mediaSyncRefStore = InMemoryMediaSyncRefStore(),
                    journalRepository = mockJournalRepository,
                    journalNotesRepository = mockNotesRepository,
                    journalContentRepository = trackingContentRepository,
                    journalConflictResolver = lastWriteWinsResolver(),
                    noteConflictResolver = lastWriteWinsResolver(),
                    conflictStore = conflictStore,
                    deadLetterStore = InMemorySyncDeadLetterStore(),
                    retryScheduleStore = InMemorySyncRetryScheduleStore(),
                    syncMetadataService = syncMetadataService,
                    transactionManager = testSyncTransactionManager(),
                    dataUsagePolicy = fakeDataUsagePolicy(),
                )

            val result = syncManager.downloadRemoteChanges()

            assertTrue(result.success)
            assertFalse(trackingContentRepository.hasAssociation(contentId, journalId), "Matching deletion removes the relationship")
            assertTrue(syncMetadataService.getPendingUploads(EntityType.ASSOCIATION).isEmpty(), "Matching delete must settle")
            assertEquals(0, result.conflictsResolved, "A matching delete does not conflict")
            assertTrue(conflictStore.list().isEmpty(), "Matching deletion needs no recovery record")
        }

    @Test
    fun `download remote journal changes`() =
        runTest {
            // Given: A sync manager with API client that returns journal changes
            val mockApiClient = fakeCloudApiClient()
            val mockAccountRepository = fakeAccountRepository()
            val mockJournalNotesRepository = FakeJournalNotesRepository()
            val mockJournalRepository = FakeJournalRepository()
            val mockJournalContentRepository = FakeJournalContentRepository()

            val remoteJournal =
                Journal(
                    id = Uuid.random(),
                    title = "Remote Journal",
                    description = "Journal from server",
                    created = Clock.System.now(),
                    lastUpdated = Clock.System.now(),
                )

            // Configure API client to return journal changes
            mockApiClient.getJournalChangesResponse =
                Result.success(
                    JournalChangesResponse(
                        changes =
                            listOf(
                                JournalChange(
                                    id = remoteJournal.id.toString(),
                                    title = remoteJournal.title,
                                    description = remoteJournal.description,
                                    createdAt = remoteJournal.created.toEpochMilliseconds(),
                                    lastUpdated = remoteJournal.lastUpdated.toEpochMilliseconds(),
                                    serverVersion = 1,
                                ),
                            ),
                        deletions = emptyList(),
                        lastTimestamp = Clock.System.now().toEpochMilliseconds(),
                    ),
                )

            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(mockApiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(mockApiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(mockApiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(mockApiClient),
                    cloudDraftDataSource = DefaultCloudDraftDataSource(mockApiClient),
                    cloudAccountRepository = mockAccountRepository,
                    sessionStorage = fakeSessionStorage(),
                    mediaManager = InMemoryMediaManager(),
                    mediaSyncRefStore = InMemoryMediaSyncRefStore(),
                    journalRepository = mockJournalRepository,
                    journalNotesRepository = mockJournalNotesRepository,
                    journalContentRepository = mockJournalContentRepository,
                    journalConflictResolver = lastWriteWinsResolver(),
                    noteConflictResolver = lastWriteWinsResolver(),
                    conflictStore = InMemorySyncConflictStore(),
                    deadLetterStore = InMemorySyncDeadLetterStore(),
                    retryScheduleStore = InMemorySyncRetryScheduleStore(),
                    syncMetadataService = fakeSyncMetadataService(),
                    transactionManager = testSyncTransactionManager(),
                    dataUsagePolicy = fakeDataUsagePolicy(),
                )

            // When: We download remote changes
            val result = syncManager.downloadRemoteChanges()

            // Then: The download should succeed
            assertTrue(result.success, "Download should succeed")
            assertTrue(result.downloadedItems > 0, "Should have downloaded journals")
            assertTrue(mockApiClient.wasMethodCalled("getJournalChanges"), "Should have called getJournalChanges")
        }

    @Test
    fun `download retains pending journal edit before applying deletion`() =
        runTest {
            val mockApiClient = fakeCloudApiClient()
            val mockNotesRepository = FakeJournalNotesRepository()
            val mockJournalRepository = FakeJournalRepository()
            val mockJournalContentRepository = FakeJournalContentRepository()
            val syncMetadataService = fakeSyncMetadataService()
            val recoveryStorage = InMemoryKeyValueStorage()
            val conflictStore =
                preservingStore(recoveryStorage) { record ->
                    val snapshot = assertNotNull(record.deletedEdit?.journal)
                    assertEquals(
                        snapshot,
                        mockJournalRepository.getJournalById(snapshot.id),
                        "Snapshot must be saved before original deletion",
                    )
                    assertTrue(
                        syncMetadataService
                            .getPendingUploads(EntityType.JOURNAL)
                            .any { it.entityId == record.entityId && it.operation != PendingOperation.DELETE },
                        "The edit must still be pending when its recovery snapshot is saved",
                    )
                }

            val localJournal =
                Journal(
                    id = Uuid.random(),
                    title = "Pending Journal",
                    description = "Local pending journal",
                    created = Clock.System.now(),
                    lastUpdated = Clock.System.now(),
                )

            mockJournalRepository.create(localJournal)
            syncMetadataService.addPending(localJournal.id, EntityType.JOURNAL)

            mockApiClient.getJournalChangesResponse =
                Result.success(
                    JournalChangesResponse(
                        changes = emptyList(),
                        deletions =
                            listOf(
                                JournalDeletion(
                                    id = localJournal.id.toString(),
                                    deletedAt = Clock.System.now().toEpochMilliseconds(),
                                ),
                            ),
                        lastTimestamp = Clock.System.now().toEpochMilliseconds(),
                    ),
                )

            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(mockApiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(mockApiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(mockApiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(mockApiClient),
                    cloudDraftDataSource = DefaultCloudDraftDataSource(mockApiClient),
                    cloudAccountRepository = fakeAccountRepository(),
                    sessionStorage = fakeSessionStorage(),
                    mediaManager = InMemoryMediaManager(),
                    mediaSyncRefStore = InMemoryMediaSyncRefStore(),
                    journalRepository = mockJournalRepository,
                    journalNotesRepository = mockNotesRepository,
                    journalContentRepository = mockJournalContentRepository,
                    journalConflictResolver = lastWriteWinsResolver(),
                    noteConflictResolver = lastWriteWinsResolver(),
                    conflictStore = conflictStore,
                    deadLetterStore = InMemorySyncDeadLetterStore(),
                    retryScheduleStore = InMemorySyncRetryScheduleStore(),
                    syncMetadataService = syncMetadataService,
                    transactionManager = testSyncTransactionManager(),
                    dataUsagePolicy = fakeDataUsagePolicy(),
                )

            val result = syncManager.downloadRemoteChanges()
            val remainingJournals = mockJournalRepository.allJournalsObserved.first()

            assertTrue(result.success, "Preservation and deletion should both succeed")
            assertFalse(remainingJournals.any { it.id == localJournal.id }, "Original deleted ID must be removed")
            val retained = KeyValueSyncConflictStore(recoveryStorage).list().single()
            assertEquals("DELETION_CONFLICT", retained.reason)
            assertEquals(localJournal, retained.deletedEdit?.journal, "A fresh store must recover the complete local edit")
            assertTrue(syncMetadataService.getPendingUploads(EntityType.JOURNAL).isEmpty(), "The rejected edit must stop uploading")
            assertEquals(1, result.conflictsResolved, "The preserved edit must be visible as one recovery conflict")
        }

    @Test
    fun `download retains later journal edit before applying deletion`() =
        runTest {
            val mockApiClient = fakeCloudApiClient()
            val mockNotesRepository = FakeJournalNotesRepository()
            val mockJournalRepository = FakeJournalRepository()
            val mockJournalContentRepository = FakeJournalContentRepository()
            val syncMetadataService = fakeSyncMetadataService()
            val recoveryStorage = InMemoryKeyValueStorage()
            val conflictStore =
                preservingStore(recoveryStorage) { record ->
                    val snapshot = assertNotNull(record.deletedEdit?.journal)
                    assertEquals(
                        snapshot,
                        mockJournalRepository.getJournalById(snapshot.id),
                        "Snapshot must be saved before original deletion",
                    )
                    assertTrue(
                        syncMetadataService
                            .getPendingUploads(EntityType.JOURNAL)
                            .any { it.entityId == record.entityId && it.operation != PendingOperation.DELETE },
                        "The edit must still be pending when its recovery snapshot is saved",
                    )
                }

            val syncManager =
                DefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(mockApiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(mockApiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(mockApiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(mockApiClient),
                    cloudDraftDataSource = DefaultCloudDraftDataSource(mockApiClient),
                    cloudAccountRepository = fakeAccountRepository(),
                    sessionStorage = fakeSessionStorage(),
                    mediaManager = InMemoryMediaManager(),
                    mediaSyncRefStore = InMemoryMediaSyncRefStore(),
                    journalRepository = mockJournalRepository,
                    journalNotesRepository = mockNotesRepository,
                    journalContentRepository = mockJournalContentRepository,
                    journalConflictResolver = lastWriteWinsResolver(),
                    noteConflictResolver = lastWriteWinsResolver(),
                    conflictStore = conflictStore,
                    deadLetterStore = InMemorySyncDeadLetterStore(),
                    retryScheduleStore = InMemorySyncRetryScheduleStore(),
                    syncMetadataService = syncMetadataService,
                    transactionManager = testSyncTransactionManager(),
                    dataUsagePolicy = fakeDataUsagePolicy(),
                )

            mockApiClient.getJournalChangesResponse =
                Result.success(
                    JournalChangesResponse(
                        changes = emptyList(),
                        deletions = emptyList(),
                        lastTimestamp = Clock.System.now().toEpochMilliseconds(),
                    ),
                )
            val firstResult = syncManager.downloadRemoteChanges()
            assertTrue(firstResult.success, "Initial download should succeed")

            val lastSyncTime = syncManager.getSyncStatus().lastSyncTime
            assertNotNull(lastSyncTime, "Sync should record last sync time")

            val localJournal =
                Journal(
                    id = Uuid.random(),
                    title = "Local Journal",
                    description = "Local update after sync",
                    created = lastSyncTime,
                    lastUpdated = lastSyncTime.plus(1.seconds),
                )
            mockJournalRepository.create(localJournal)
            syncMetadataService.addPending(localJournal.id, EntityType.JOURNAL)

            mockApiClient.getJournalChangesResponse =
                Result.success(
                    JournalChangesResponse(
                        changes = emptyList(),
                        deletions =
                            listOf(
                                JournalDeletion(
                                    id = localJournal.id.toString(),
                                    deletedAt = Clock.System.now().toEpochMilliseconds(),
                                ),
                            ),
                        lastTimestamp = Clock.System.now().toEpochMilliseconds(),
                    ),
                )

            val result = syncManager.downloadRemoteChanges()
            val remainingJournals = mockJournalRepository.allJournalsObserved.first()

            assertTrue(result.success, "Preservation and deletion should both succeed")
            assertFalse(remainingJournals.any { it.id == localJournal.id }, "Original deleted ID must be removed")
            val retained = KeyValueSyncConflictStore(recoveryStorage).list().single()
            assertEquals("DELETION_CONFLICT", retained.reason)
            assertEquals(localJournal, retained.deletedEdit?.journal, "A fresh store must recover the complete local edit")
            assertTrue(syncMetadataService.getPendingUploads(EntityType.JOURNAL).isEmpty(), "The rejected edit must stop uploading")
            assertEquals(1, result.conflictsResolved, "The preserved edit must be visible as one recovery conflict")
        }

    private fun preservingStore(
        storage: InMemoryKeyValueStorage,
        beforeSave: suspend (SyncConflictRecord) -> Unit,
    ): SyncConflictStore {
        val persisted = KeyValueSyncConflictStore(storage)
        return object : SyncConflictStore by persisted {
            override suspend fun add(record: SyncConflictRecord) {
                beforeSave(record)
                persisted.add(record)
            }
        }
    }
}
