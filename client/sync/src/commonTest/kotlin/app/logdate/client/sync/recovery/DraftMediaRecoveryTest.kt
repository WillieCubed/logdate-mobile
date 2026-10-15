package app.logdate.client.sync.recovery

import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.sync.InMemoryKeyValueStorage
import app.logdate.client.sync.SyncMediaTransfer
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.MediaFile
import app.logdate.client.sync.cloud.MediaUploadResult
import app.logdate.client.sync.metadata.KeyValueMediaSyncRefStore
import app.logdate.client.sync.test.FakeJournalRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.sync.DeviceId
import app.logdate.shared.model.sync.DraftChange
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class DraftMediaRecoveryTest {
    @Test
    fun `completed draft attachments remain linked when a later attachment fails`() =
        runTest {
            val database = DownloadInboxTest.Database()
            val inbox = DownloadInbox(database, database, { DownloadScope("owner", "origin") }, { 1L })
            val draftId = Uuid.random()
            val firstId = Uuid.random()
            val secondId = Uuid.random()
            val firstUrl = "https://example.invalid/media/first"
            val secondUrl = "https://example.invalid/media/second"
            val timestamp = Instant.fromEpochMilliseconds(1)
            val draft =
                EditorDraft(
                    id = draftId,
                    blocks =
                        listOf(
                            SerializableImageBlock(firstId, timestamp, uri = firstUrl),
                            SerializableImageBlock(secondId, timestamp, uri = secondUrl),
                        ),
                    createdAt = timestamp,
                    lastModifiedAt = timestamp,
                )
            val drafts = FakeJournalRepository()
            drafts.saveDraftFromSync(draft)
            val wire =
                DraftChange(
                    id = draftId.toString(),
                    content = "encrypted text",
                    blockTypes = listOf("IMAGE", "IMAGE"),
                    journalIds = emptyList(),
                    createdAt = 1,
                    lastUpdated = 1,
                    deviceId = DeviceId("device"),
                    serverVersion = 4,
                    encryptedBlocksVersion = 1,
                    encryptedBlocks = "ciphertext",
                )
            inbox.stage("DRAFT", 4, listOf(WireDownload(draftId.toString(), 4, false, Json.encodeToString(wire))))
            inbox.applied("DRAFT", draftId.toString(), 4)

            var firstDownloads = 0
            val media =
                object : CloudMediaDataSource {
                    override suspend fun uploadMedia(
                        accessToken: String,
                        contentId: Uuid,
                        media: app.logdate.client.media.MediaFileSource,
                    ): Result<MediaUploadResult> = error("No upload expected")

                    override suspend fun downloadMedia(
                        accessToken: String,
                        mediaId: String,
                    ): Result<MediaFile> =
                        if (mediaId == "first") {
                            firstDownloads++
                            Result.success(MediaFile(firstId, "first.jpg", "image/jpeg", 1, byteArrayOf(1)))
                        } else {
                            Result.failure(IllegalStateException("temporarily unavailable"))
                        }
                }
            val manager = InMemoryMediaManager()
            val refs = KeyValueMediaSyncRefStore(InMemoryKeyValueStorage(), currentOrigin = { "origin" }, currentOwnerId = { "owner" })
            val recovery = SyncDraftMediaRecovery(inbox, drafts, SyncMediaTransfer(manager, refs, media), refs, database)

            recovery.recover("token")

            val saved = assertNotNull(drafts.getDraft(draftId))
            val first = saved.blocks[0] as SerializableImageBlock
            assertTrue(first.uri.orEmpty().startsWith("file://"))
            assertEquals(secondUrl, (saved.blocks[1] as SerializableImageBlock).uri)
            assertNotNull(refs.getDraftAsset(draftId, firstId, "image"))
            inbox.release()
            recovery.recover("token")
            assertEquals(1, firstDownloads)
        }

    @Test
    fun `an editor save during attachment mapping is not overwritten by hydration`() =
        runTest {
            val database = DownloadInboxTest.Database()
            val inbox = DownloadInbox(database, database, { DownloadScope("owner", "origin") }, { 1L })
            val timestamp = Instant.fromEpochMilliseconds(1)
            val blockId = Uuid.random()
            val original =
                EditorDraft(
                    Uuid.random(),
                    listOf(SerializableImageBlock(blockId, timestamp, uri = "https://example.invalid/media/original")),
                    createdAt = timestamp,
                    lastModifiedAt = timestamp,
                )
            val edited =
                original.copy(
                    blocks = listOf((original.blocks.single() as SerializableImageBlock).copy(caption = "Written during download")),
                    lastModifiedAt = Instant.fromEpochMilliseconds(2),
                )
            val drafts = FakeJournalRepository()
            drafts.saveDraftFromSync(original)
            val wire =
                DraftChange(
                    id = original.id.toString(),
                    content = "",
                    blockTypes = listOf("IMAGE"),
                    journalIds = emptyList(),
                    createdAt = 1,
                    lastUpdated = 1,
                    deviceId = DeviceId("device"),
                    serverVersion = 4,
                    encryptedBlocksVersion = 1,
                    encryptedBlocks = "ciphertext",
                )
            inbox.stage("DRAFT", 4, listOf(WireDownload(original.id.toString(), 4, false, Json.encodeToString(wire))))
            inbox.applied("DRAFT", original.id.toString(), 4)
            val backing = KeyValueMediaSyncRefStore(InMemoryKeyValueStorage(), { "origin" }, { "owner" })
            val refs =
                object : app.logdate.client.sync.metadata.MediaSyncRefStore by backing {
                    override suspend fun upsertDraftAsset(
                        draftId: Uuid,
                        blockId: Uuid,
                        kind: String,
                        ref: app.logdate.client.sync.metadata.MediaSyncRef,
                    ) {
                        backing.upsertDraftAsset(draftId, blockId, kind, ref)
                        drafts.saveDraft(edited)
                    }
                }
            val cloud =
                object : CloudMediaDataSource {
                    override suspend fun uploadMedia(
                        accessToken: String,
                        contentId: Uuid,
                        media: app.logdate.client.media.MediaFileSource,
                    ): Result<MediaUploadResult> = error("unexpected upload")

                    override suspend fun downloadMedia(
                        accessToken: String,
                        mediaId: String,
                    ): Result<MediaFile> = Result.success(MediaFile(blockId, "original.jpg", "image/jpeg", 1, byteArrayOf(1)))
                }
            SyncDraftMediaRecovery(inbox, drafts, SyncMediaTransfer(InMemoryMediaManager(), refs, cloud), refs, database).recover("token")
            assertEquals(edited, drafts.getDraft(original.id))
        }
}
