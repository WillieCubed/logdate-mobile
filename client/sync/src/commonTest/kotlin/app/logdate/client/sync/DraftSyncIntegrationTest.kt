package app.logdate.client.sync

import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaPayload
import app.logdate.client.sync.cloud.CloudDraftDataSource
import app.logdate.client.sync.cloud.DefaultCloudDraftDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.cloud.DraftChange
import app.logdate.client.sync.cloud.DraftChangesResponse
import app.logdate.client.sync.cloud.DraftSyncResult
import app.logdate.client.sync.cloud.InMemorySecureStorage
import app.logdate.client.sync.cloud.SyncedDraft
import app.logdate.client.sync.cloud.TestCryptoManager
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadInboxTest
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.recovery.WireDownload
import app.logdate.client.sync.test.FakeJournalRepository
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.sync.DeviceId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class DraftSyncIntegrationTest {
    @Test
    fun `same server version keeps already hydrated draft media local`() =
        runTest {
            val database = DownloadInboxTest.Database()
            val inbox = DownloadInbox(database, database, { DownloadScope("owner", "origin") }, { 1L })
            val timestamp = Instant.fromEpochMilliseconds(1)
            val draftId = Uuid.random()
            val blockId = Uuid.random()
            val remoteUrl = "https://example.invalid/media/image"
            val remote =
                EditorDraft(
                    id = draftId,
                    blocks = listOf(SerializableImageBlock(blockId, timestamp, uri = remoteUrl)),
                    createdAt = timestamp,
                    lastModifiedAt = timestamp,
                )
            val local = remote.copy(blocks = listOf(SerializableImageBlock(blockId, timestamp, uri = "file:///local/image")))
            val drafts = FakeJournalRepository()
            drafts.saveDraftFromSync(local)
            val wire =
                DraftChange(
                    id = draftId.toString(),
                    content = "ciphertext",
                    blockTypes = listOf("IMAGE"),
                    journalIds = emptyList(),
                    createdAt = 1,
                    lastUpdated = 1,
                    deviceId = DeviceId("device"),
                    serverVersion = 4,
                    encryptedBlocksVersion = 1,
                    encryptedBlocks = "LDSE2:ciphertext",
                )
            inbox.stage("DRAFT", 4, listOf(WireDownload(draftId.toString(), 4, false, Json.encodeToString(wire))))
            inbox.applied("DRAFT", draftId.toString(), 4)
            val source =
                object : CloudDraftDataSource by DefaultCloudDraftDataSource(fakeCloudApiClient()) {
                    override suspend fun getDraftChanges(
                        accessToken: String,
                        since: Instant,
                        limit: Int?,
                    ): Result<DraftSyncResult> =
                        Result.success(
                            DraftSyncResult(
                                changes =
                                    listOf(
                                        SyncedDraft(
                                            draftId,
                                            "",
                                            DeviceId("device"),
                                            timestamp,
                                            timestamp,
                                            4,
                                            richDraft = remote,
                                        ),
                                    ),
                                deletions = emptyList(),
                                lastSyncTimestamp = timestamp,
                            ),
                        )
                }
            val manager =
                testDefaultSyncManager(
                    cloudDraftDataSource = source,
                    journalRepository = drafts,
                    downloadInbox = inbox,
                    transactionManager = database,
                )

            manager.syncDrafts()

            assertEquals(local, drafts.getDraft(draftId))
        }

    @Test
    fun `draft media is uploaded before its rich record references the remote file`() =
        runTest {
            val api = fakeCloudApiClient()
            val media = InMemoryMediaManager()
            val localUri = media.saveMedia(MediaPayload("image.jpg", "image/jpeg", 1, byteArrayOf(7)))
            val timestamp = Clock.System.now()
            val draft =
                EditorDraft(
                    blocks = listOf(SerializableImageBlock(Uuid.random(), timestamp, uri = localUri)),
                    createdAt = timestamp,
                    lastModifiedAt = timestamp,
                )
            val drafts = FakeJournalRepository()
            drafts.saveDraft(draft)
            val metadata = fakeSyncMetadataService()
            metadata.enqueuePending(draft.id.toString(), EntityType.DRAFT, PendingOperation.CREATE)
            val payloadCipher = cipher()
            val manager =
                testDefaultSyncManager(
                    cloudDraftDataSource = DefaultCloudDraftDataSource(api, payloadCipher, supportsRichDrafts = { true }),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(api),
                    mediaManager = media,
                    journalRepository = drafts,
                    syncMetadataService = metadata,
                    supportsRichDrafts = { true },
                )

            val result = manager.syncDrafts()

            assertTrue(result.success)
            assertTrue(api.methodCalls.indexOf("uploadMedia") < api.methodCalls.indexOf("uploadDraft"))
            val wire = api.uploadDraftCalls.single().second
            val plaintext = payloadCipher.decryptString("sync:draft:${draft.id}:blocks", requireNotNull(wire.encryptedBlocks))
            val uploadedDraft = Json.decodeFromString<EditorDraft>(plaintext)
            assertEquals("https://example.com/media", (uploadedDraft.blocks.single() as SerializableImageBlock).uri)
        }

    @Test
    fun `draft page fetched for previous account cannot be saved after account switch`() =
        runTest {
            val database = DownloadInboxTest.Database()
            var selected = DownloadScope("owner-a", "origin")
            val inbox = DownloadInbox(database, database, { selected }, { 1L })
            val draftId = Uuid.random()
            val repository = FakeJournalRepository()
            val datasource =
                object : CloudDraftDataSource by DefaultCloudDraftDataSource(fakeCloudApiClient()) {
                    override suspend fun getDraftChanges(
                        accessToken: String,
                        since: Instant,
                        limit: Int?,
                    ): Result<DraftSyncResult> {
                        selected = DownloadScope("owner-b", "origin")
                        return Result.success(
                            DraftSyncResult(
                                changes =
                                    listOf(
                                        SyncedDraft(
                                            draftId,
                                            "old account text",
                                            DeviceId("device-a"),
                                            Instant.fromEpochMilliseconds(1),
                                            Instant.fromEpochMilliseconds(2),
                                            3,
                                        ),
                                    ),
                                deletions = emptyList(),
                                lastSyncTimestamp = Instant.fromEpochMilliseconds(3),
                            ),
                        )
                    }
                }
            val manager =
                testDefaultSyncManager(
                    cloudDraftDataSource = datasource,
                    journalRepository = repository,
                    downloadInbox = inbox,
                    transactionManager = database,
                )

            manager.syncDrafts()

            assertEquals(null, repository.getDraft(draftId))
        }

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
                    cloudDraftDataSource = DefaultCloudDraftDataSource(apiClient, cipher(), supportsRichDrafts = { true }),
                    journalRepository = journalRepository,
                    syncMetadataService = syncMetadataService,
                    supportsRichDrafts = { true },
                )

            val result = syncManager.syncDrafts()

            assertTrue(result.success)
            assertEquals(1, result.uploadedItems)
            val uploaded = apiClient.uploadDraftCalls.single().second
            assertEquals(draft.id.toString(), uploaded.id)
            assertTrue(uploaded.content.startsWith("LDSE2:"))
            assertTrue(uploaded.encryptedBlocks.orEmpty().startsWith("LDSE2:"))
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
                    cloudDraftDataSource = DefaultCloudDraftDataSource(apiClient, cipher(), supportsRichDrafts = { true }),
                    journalRepository = journalRepository,
                    syncMetadataService = syncMetadataService,
                    supportsRichDrafts = { true },
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
                    cloudDraftDataSource = DefaultCloudDraftDataSource(apiClient, cipher(), supportsRichDrafts = { true }),
                    journalRepository = journalRepository,
                    syncMetadataService = syncMetadataService,
                    supportsRichDrafts = { true },
                )

            val result = syncManager.syncDrafts()

            assertTrue(result.success)
            assertEquals(1, result.conflictsResolved)
            assertEquals("local unsynced draft", journalRepository.getDraft(draftId)?.textContent())
            val uploadedDraft = apiClient.uploadDraftCalls.single().second
            assertTrue(uploadedDraft.content.startsWith("LDSE2:"))
            assertTrue(uploadedDraft.encryptedBlocks.orEmpty().startsWith("LDSE2:"))
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

    private suspend fun cipher(): SyncPayloadCipher {
        val crypto = TestCryptoManager()
        val keys = IdentityKeyManager(InMemorySecureStorage(), crypto)
        keys.setupNewIdentity()
        return SyncPayloadCipher(ContentEncryptionService(keys, KeyDerivation(crypto), crypto), keys, crypto)
    }

    private fun EditorDraft.textContent(): String =
        blocks
            .filterIsInstance<SerializableTextBlock>()
            .joinToString("\n") { it.content }
}
