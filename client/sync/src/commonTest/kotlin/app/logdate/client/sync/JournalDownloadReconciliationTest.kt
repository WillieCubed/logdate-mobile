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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class JournalDownloadReconciliationTest {
    @Test
    fun `download skips pending association deletion`() =
        runTest {
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
                    conflictStore = InMemorySyncConflictStore(),
                    deadLetterStore = InMemorySyncDeadLetterStore(),
                    retryScheduleStore = InMemorySyncRetryScheduleStore(),
                    syncMetadataService = syncMetadataService,
                    transactionManager = testSyncTransactionManager(),
                    dataUsagePolicy = fakeDataUsagePolicy(),
                )

            val result = syncManager.downloadRemoteChanges()

            assertTrue(
                trackingContentRepository.hasAssociation(contentId, journalId),
                "Pending association should not be deleted",
            )
            assertTrue(result.conflictsResolved > 0, "Should register a conflict when skipping association deletion")
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
    fun `download skips pending journal deletion`() =
        runTest {
            val mockApiClient = fakeCloudApiClient()
            val mockNotesRepository = FakeJournalNotesRepository()
            val mockJournalRepository = FakeJournalRepository()
            val mockJournalContentRepository = FakeJournalContentRepository()
            val syncMetadataService = fakeSyncMetadataService()

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
                    conflictStore = InMemorySyncConflictStore(),
                    deadLetterStore = InMemorySyncDeadLetterStore(),
                    retryScheduleStore = InMemorySyncRetryScheduleStore(),
                    syncMetadataService = syncMetadataService,
                    transactionManager = testSyncTransactionManager(),
                    dataUsagePolicy = fakeDataUsagePolicy(),
                )

            val result = syncManager.downloadRemoteChanges()
            val remainingJournals = mockJournalRepository.allJournalsObserved.first()

            assertTrue(remainingJournals.any { it.id == localJournal.id }, "Pending journal should not be deleted")
            assertTrue(result.conflictsResolved > 0, "Should register a conflict when skipping deletion")
        }

    @Test
    fun `download skips deletion when local journal updated after last sync`() =
        runTest {
            val mockApiClient = fakeCloudApiClient()
            val mockNotesRepository = FakeJournalNotesRepository()
            val mockJournalRepository = FakeJournalRepository()
            val mockJournalContentRepository = FakeJournalContentRepository()
            val syncMetadataService = fakeSyncMetadataService()

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
                    conflictStore = InMemorySyncConflictStore(),
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

            assertTrue(remainingJournals.any { it.id == localJournal.id }, "Updated journal should not be deleted")
            assertTrue(result.conflictsResolved > 0, "Should register a conflict when skipping deletion")
        }
}
