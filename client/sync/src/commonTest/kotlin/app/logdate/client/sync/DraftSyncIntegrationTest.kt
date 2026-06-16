package app.logdate.client.sync

import app.logdate.client.sync.cloud.DefaultCloudDraftDataSource
import app.logdate.client.sync.cloud.DraftChange
import app.logdate.client.sync.cloud.DraftChangesResponse
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.test.FakeJournalRepository
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.sync.DeviceId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class DraftSyncIntegrationTest {
    @Test
    fun `syncDrafts uploads pending local draft and clears outbox`() =
        runTest {
            val apiClient = fakeCloudApiClient()
            val journalRepository = FakeJournalRepository()
            val syncMetadataService = fakeSyncMetadataService()
            val draft = testDraft(content = "continue this thought")
            journalRepository.saveDraft(draft)
            syncMetadataService.enqueuePending(draft.id.toString(), EntityType.DRAFT, PendingOperation.CREATE)
            val syncManager =
                testDefaultSyncManager(
                    cloudDraftDataSource = DefaultCloudDraftDataSource(apiClient),
                    journalRepository = journalRepository,
                    syncMetadataService = syncMetadataService,
                )

            val result = syncManager.syncDrafts()

            assertTrue(result.success)
            assertEquals(1, result.uploadedItems)
            val uploaded = apiClient.uploadDraftCalls.single().second
            assertEquals(draft.id.toString(), uploaded.id)
            assertEquals("continue this thought", uploaded.content)
            assertEquals(emptyList(), syncMetadataService.getPendingUploads(EntityType.DRAFT))
        }

    @Test
    fun `syncDrafts applies remote draft changes to local repository`() =
        runTest {
            val apiClient = fakeCloudApiClient()
            val journalRepository = FakeJournalRepository()
            val syncMetadataService = fakeSyncMetadataService()
            val journalId = Uuid.random()
            val draftId = Uuid.random()
            val now = Clock.System.now()
            apiClient.getDraftChangesResponse =
                Result.success(
                    DraftChangesResponse(
                        drafts =
                            listOf(
                                DraftChange(
                                    id = draftId.toString(),
                                    content = "remote draft",
                                    blockTypes = listOf("TEXT"),
                                    journalIds = listOf(journalId.toString()),
                                    createdAt = now.toEpochMilliseconds(),
                                    lastUpdated = now.toEpochMilliseconds(),
                                    deviceId = DeviceId("device-b"),
                                    serverVersion = 7,
                                ),
                            ),
                    ),
                )
            val syncManager =
                testDefaultSyncManager(
                    cloudDraftDataSource = DefaultCloudDraftDataSource(apiClient),
                    journalRepository = journalRepository,
                    syncMetadataService = syncMetadataService,
                )

            val result = syncManager.syncDrafts()

            assertTrue(result.success)
            assertEquals(1, result.downloadedItems)
            val saved = journalRepository.getDraft(draftId)
            assertEquals("remote draft", saved?.textContent())
            assertEquals(listOf(journalId), saved?.selectedJournalIds)
            assertEquals(emptyList(), syncMetadataService.getPendingUploads(EntityType.DRAFT))
        }

    @Test
    fun `syncDrafts preserves pending local draft when remote update exists`() =
        runTest {
            val apiClient = fakeCloudApiClient()
            val journalRepository = FakeJournalRepository()
            val syncMetadataService = fakeSyncMetadataService()
            val draftId = Uuid.random()
            val localDraft = testDraft(id = draftId, content = "local unsynced draft")
            journalRepository.saveDraft(localDraft)
            syncMetadataService.enqueuePending(draftId.toString(), EntityType.DRAFT, PendingOperation.UPDATE)
            val remoteUpdatedAt = localDraft.lastModifiedAt.plus(kotlin.time.Duration.parse("1s"))
            apiClient.getDraftChangesResponse =
                Result.success(
                    DraftChangesResponse(
                        drafts =
                            listOf(
                                DraftChange(
                                    id = draftId.toString(),
                                    content = "remote draft that must not overwrite local",
                                    blockTypes = listOf("TEXT"),
                                    journalIds = emptyList(),
                                    createdAt = localDraft.createdAt.toEpochMilliseconds(),
                                    lastUpdated = remoteUpdatedAt.toEpochMilliseconds(),
                                    deviceId = DeviceId("device-b"),
                                    serverVersion = 8,
                                ),
                            ),
                    ),
                )
            val syncManager =
                testDefaultSyncManager(
                    cloudDraftDataSource = DefaultCloudDraftDataSource(apiClient),
                    journalRepository = journalRepository,
                    syncMetadataService = syncMetadataService,
                )

            val result = syncManager.syncDrafts()

            assertTrue(result.success)
            assertEquals(1, result.conflictsResolved)
            assertEquals("local unsynced draft", journalRepository.getDraft(draftId)?.textContent())
            val uploadedDraft = apiClient.uploadDraftCalls.single().second
            assertEquals("local unsynced draft", uploadedDraft.content)
            assertEquals(emptyList(), syncMetadataService.getPendingUploads(EntityType.DRAFT))
        }

    @Test
    fun `syncDrafts applies remote draft deletions when no local draft is pending`() =
        runTest {
            val apiClient = fakeCloudApiClient()
            val journalRepository = FakeJournalRepository()
            val syncMetadataService = fakeSyncMetadataService()
            val draft = testDraft(content = "delete me remotely")
            journalRepository.saveDraft(draft)
            val now = Clock.System.now()
            apiClient.getDraftChangesResponse =
                Result.success(
                    DraftChangesResponse(
                        drafts =
                            listOf(
                                DraftChange(
                                    id = draft.id.toString(),
                                    content = "",
                                    blockTypes = emptyList(),
                                    journalIds = emptyList(),
                                    createdAt = draft.createdAt.toEpochMilliseconds(),
                                    lastUpdated = now.toEpochMilliseconds(),
                                    deviceId = DeviceId("device-b"),
                                    serverVersion = 9,
                                    isDeleted = true,
                                ),
                            ),
                    ),
                )
            val syncManager =
                testDefaultSyncManager(
                    cloudDraftDataSource = DefaultCloudDraftDataSource(apiClient),
                    journalRepository = journalRepository,
                    syncMetadataService = syncMetadataService,
                )

            val result = syncManager.syncDrafts()

            assertTrue(result.success)
            assertEquals(1, result.downloadedItems)
            assertEquals(null, journalRepository.getDraft(draft.id))
            assertEquals(emptyList(), syncMetadataService.getPendingUploads(EntityType.DRAFT))
        }

    private fun testDraft(
        id: Uuid = Uuid.random(),
        content: String,
    ): EditorDraft {
        val now = Clock.System.now()
        return EditorDraft(
            id = id,
            blocks =
                listOf(
                    SerializableTextBlock(
                        id = Uuid.random(),
                        timestamp = now,
                        content = content,
                    ),
                ),
            createdAt = now,
            lastModifiedAt = now,
        )
    }

    private fun EditorDraft.textContent(): String =
        blocks
            .filterIsInstance<SerializableTextBlock>()
            .joinToString("\n") { it.content }
}
