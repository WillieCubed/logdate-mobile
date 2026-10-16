package app.logdate.client.sync

import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaFileSource
import app.logdate.client.media.MediaPayload
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.MediaFile
import app.logdate.client.sync.cloud.MediaUploadResult
import app.logdate.client.sync.metadata.KeyValueMediaSyncRefStore
import app.logdate.client.sync.metadata.MediaSyncRef
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableVideoBlock
import app.logdate.shared.model.diagnostics.DiagnosticReason
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class NativeMissingMediaRecoveryTest {
    private val timestamp = Instant.fromEpochMilliseconds(1)
    private val missing = "logdate-media://library/old-original.mp4"
    private val remote = "https://cloud.example/media/original"
    private val refs = KeyValueMediaSyncRefStore(InMemoryKeyValueStorage(), { "origin" }, { "owner" })
    private val media = InMemoryMediaManager()
    private val downloads = mutableListOf<String>()
    private var downloadedOwner = Uuid.random()
    private val downloadedOwners = mutableMapOf<String, Uuid>()
    private val cloud =
        object : CloudMediaDataSource {
            override suspend fun uploadMedia(
                accessToken: String,
                contentId: Uuid,
                media: MediaFileSource,
            ): Result<MediaUploadResult> = error("unexpected upload")

            override suspend fun downloadMedia(
                accessToken: String,
                mediaId: String,
            ): Result<MediaFile> {
                downloads += mediaId
                return Result.success(
                    MediaFile(downloadedOwners[mediaId] ?: downloadedOwner, "$mediaId.mp4", "video/mp4", 1, byteArrayOf(1)),
                )
            }
        }

    @Test
    fun `remote reference reuses its transferred original without a network request`() =
        runTest {
            val local = media.saveMedia(MediaPayload("existing.mp4", "video/mp4", 1, byteArrayOf(1)))
            val note = JournalNote.Video(creationTimestamp = timestamp, lastUpdated = timestamp, mediaRef = remote)
            refs.upsert(mapping(note.uid).copy(localUri = local))
            val result = assertIs<MediaHydrationResult.Available>(SyncMediaTransfer(media, refs, cloud).hydrate("token", note))
            assertEquals(local, (result.note as JournalNote.Video).mediaRef)
            assertTrue(downloads.isEmpty())
        }

    @Test
    fun `an old local alias reuses the current cached original offline`() =
        runTest {
            val local = media.saveMedia(MediaPayload("existing.mp4", "video/mp4", 1, byteArrayOf(1)))
            val note = JournalNote.Video(creationTimestamp = timestamp, lastUpdated = timestamp, mediaRef = missing)
            refs.upsert(mapping(note.uid).copy(localUri = local, localAliases = listOf(missing)))
            val result = assertIs<MediaHydrationResult.Available>(SyncMediaTransfer(media, refs, cloud).hydrate("token", note))
            assertEquals(local, (result.note as JournalNote.Video).mediaRef)
            assertTrue(downloads.isEmpty())
        }

    @Test
    fun `a download for another entry cannot be installed as the missing original`() =
        runTest {
            val note = JournalNote.Video(creationTimestamp = timestamp, lastUpdated = timestamp, mediaRef = remote)
            downloadedOwner = Uuid.random()
            val result = assertIs<MediaHydrationResult.Pending>(SyncMediaTransfer(media, refs, cloud).hydrate("token", note))
            assertEquals(DiagnosticReason.CORRUPT_PAYLOAD, result.reason)
        }

    @Test
    fun `missing local originals use their scoped remote mapping`() =
        runTest {
            val note =
                JournalNote.Video(
                    creationTimestamp = timestamp,
                    lastUpdated = timestamp,
                    mediaRef = missing,
                    caption = "Retained caption",
                )
            downloadedOwner = note.uid
            refs.upsert(mapping(note.uid))
            val restored = assertIs<MediaHydrationResult.Available>(SyncMediaTransfer(media, refs, cloud).hydrate("token", note))
            assertTrue(media.exists((restored.note as JournalNote.Video).mediaRef))
            assertEquals("Retained caption", restored.note.caption)
            assertEquals(listOf("original"), downloads)
        }

    @Test
    fun `missing local originals without a matching mapping remain unfinished`() =
        runTest {
            val note = JournalNote.Video(creationTimestamp = timestamp, lastUpdated = timestamp, mediaRef = missing)
            refs.upsert(mapping(note.uid).copy(localUri = "logdate-media://library/different.mp4"))
            val result = assertIs<MediaHydrationResult.Pending>(SyncMediaTransfer(media, refs, cloud).hydrate("token", note))
            assertEquals(DiagnosticReason.MISSING_MEDIA, result.reason)
            assertTrue(downloads.isEmpty())
        }

    @Test
    fun `a transferred original remains available offline`() =
        runTest {
            val local = media.saveMedia(MediaPayload("existing.mp4", "video/mp4", 1, byteArrayOf(1)))
            val note = JournalNote.Video(creationTimestamp = timestamp, lastUpdated = timestamp, mediaRef = local)
            assertIs<MediaHydrationResult.Available>(SyncMediaTransfer(media, refs, cloud).hydrate("token", note))
            assertTrue(downloads.isEmpty())
        }

    @Test
    fun `a retained preview never makes its missing video original available`() =
        runTest {
            val thumbnail = media.saveMedia(MediaPayload("preview.jpg", "image/jpeg", 1, byteArrayOf(2)))
            val block =
                SerializableVideoBlock(Uuid.random(), timestamp, uri = missing, thumbnailUri = thumbnail, caption = "Caption")
            val draft = EditorDraft(Uuid.random(), listOf(block), createdAt = timestamp, lastModifiedAt = timestamp)
            val result =
                assertIs<DraftMediaHydrationResult.Pending>(
                    SyncMediaTransfer(media, refs, cloud).hydrateDraftMedia("token", draft),
                )
            assertEquals(block, result.draft.blocks.single())
            assertEquals(DiagnosticReason.MISSING_MEDIA, result.reason)
            assertTrue(media.exists(thumbnail))
        }

    @Test
    fun `a mapped draft original is downloaded while its preview is preserved`() =
        runTest {
            val thumbnail = media.saveMedia(MediaPayload("preview.jpg", "image/jpeg", 1, byteArrayOf(2)))
            val block = SerializableVideoBlock(Uuid.random(), timestamp, uri = missing, thumbnailUri = thumbnail, caption = "Caption")
            val draft = EditorDraft(Uuid.random(), listOf(block), createdAt = timestamp, lastModifiedAt = timestamp)
            downloadedOwner = block.id
            refs.upsertDraftAsset(draft.id, block.id, "video", mapping(block.id))
            val result =
                assertIs<DraftMediaHydrationResult.Available>(
                    SyncMediaTransfer(media, refs, cloud).hydrateDraftMedia("token", draft),
                )
            val recovered = assertIs<SerializableVideoBlock>(result.draft.blocks.single())
            assertTrue(media.exists(requireNotNull(recovered.uri)))
            assertEquals(thumbnail, recovered.thumbnailUri)
            assertEquals("Caption", recovered.caption)
            assertEquals(timestamp, result.draft.lastModifiedAt)
        }

    @Test
    fun `one missing original does not block its remote preview or later attachments`() =
        runTest {
            val video =
                SerializableVideoBlock(
                    Uuid.random(),
                    timestamp,
                    uri = missing,
                    thumbnailUri = "https://cloud.example/media/thumbnail",
                    caption = "Keep video",
                )
            val image =
                app.logdate.shared.model.SerializableImageBlock(
                    Uuid.random(),
                    timestamp,
                    uri = "https://cloud.example/media/later",
                    caption = "Keep image",
                )
            downloadedOwners["thumbnail"] = video.id
            downloadedOwners["later"] = image.id
            val draft = EditorDraft(Uuid.random(), listOf(video, image), createdAt = timestamp, lastModifiedAt = timestamp)
            val result =
                assertIs<DraftMediaHydrationResult.Pending>(
                    SyncMediaTransfer(media, refs, cloud).hydrateDraftMedia("token", draft),
                )
            val repairedVideo = assertIs<SerializableVideoBlock>(result.draft.blocks[0])
            val repairedImage = assertIs<app.logdate.shared.model.SerializableImageBlock>(result.draft.blocks[1])
            assertEquals(missing, repairedVideo.uri)
            assertTrue(media.exists(requireNotNull(repairedVideo.thumbnailUri)))
            assertTrue(media.exists(requireNotNull(repairedImage.uri)))
            assertEquals(listOf("thumbnail", "later"), downloads)
            assertEquals(listOf(video.id, image.id), result.draft.blocks.map { it.id })
            assertEquals(timestamp, result.draft.lastModifiedAt)
        }

    private fun mapping(id: Uuid) = MediaSyncRef(id.toString(), missing, remote, "original", 1)
}
