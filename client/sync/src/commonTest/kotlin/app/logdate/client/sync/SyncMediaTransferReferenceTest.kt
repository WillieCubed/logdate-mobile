package app.logdate.client.sync

import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaFileSource
import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.MediaFile
import app.logdate.client.sync.cloud.MediaUploadResult
import app.logdate.client.sync.metadata.MediaSyncRef
import app.logdate.client.sync.test.InMemoryMediaSyncRefStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** A note whose stored media reference changed spelling still counts as the media already uploaded. */
class SyncMediaTransferReferenceTest {
    private val references = StoredMediaReferences { reference -> reference.replace("file:///install/media/", "logdate-media://library/") }
    private val refs = InMemoryMediaSyncRefStore()
    private val uploads = RecordingCloudMediaDataSource()
    private val created = Instant.parse("2026-01-02T03:04:05Z")

    @Test
    fun `a migrated note reuses the upload made under its older reference`() =
        runTest {
            val note = JournalNote.Image(creationTimestamp = created, lastUpdated = created, mediaRef = "logdate-media://library/beach.jpg")
            refs.upsert(cachedUpload(note.uid, localUri = "file:///install/media/beach.jpg"))

            val result = transfer().uploadIfNeeded("token", note).getOrThrow()

            assertEquals("https://cloud.example/media/m1", (result as JournalNote.Image).mediaRef)
            assertTrue(uploads.uploaded.isEmpty(), "Uploaded again: ${uploads.uploaded}")
        }

    @Test
    fun `a different file is uploaded`() =
        runTest {
            val note = JournalNote.Image(creationTimestamp = created, lastUpdated = created, mediaRef = "logdate-media://library/other.jpg")
            refs.upsert(cachedUpload(note.uid, localUri = "file:///install/media/beach.jpg"))

            transfer().uploadIfNeeded("token", note)

            assertEquals(1, uploads.uploaded.size)
        }

    private fun transfer(media: InMemoryMediaManager = InMemoryMediaManager()) = SyncMediaTransfer(media, refs, uploads, references)

    private fun cachedUpload(
        noteId: Uuid,
        localUri: String,
    ) = MediaSyncRef(
        noteId = noteId.toString(),
        localUri = localUri,
        remoteUrl = "https://cloud.example/media/m1",
        mediaId = "m1",
        updatedAt = 1,
    )
}

private class RecordingCloudMediaDataSource : CloudMediaDataSource {
    val uploaded = mutableListOf<Uuid>()

    override suspend fun uploadMedia(
        accessToken: String,
        contentId: Uuid,
        media: MediaFileSource,
    ): Result<MediaUploadResult> {
        uploaded += contentId
        return Result.success(MediaUploadResult("m2", "https://cloud.example/media/m2", Instant.parse("2026-01-02T03:04:05Z")))
    }

    override suspend fun downloadMedia(
        accessToken: String,
        mediaId: String,
    ): Result<MediaFile> = Result.failure(UnsupportedOperationException())
}
