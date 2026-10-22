package app.logdate.client.sync.recovery

import android.content.Context
import app.logdate.client.media.AndroidPhotoLibraryMediaManager
import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaFileSource
import app.logdate.client.media.MediaManager
import app.logdate.client.media.storage.AndroidCanonicalMediaStore
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.SyncMediaTransfer
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.DownloadedMediaFile
import app.logdate.client.sync.cloud.FileMediaDataSource
import app.logdate.client.sync.cloud.MediaFile
import app.logdate.client.sync.cloud.MediaUploadResult
import app.logdate.client.sync.test.InMemoryMediaSyncRefStore
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import java.io.File
import java.io.FilterInputStream
import java.net.URI
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class FileMediaHydrationScopeTest {
    @Test
    fun `account change after deduplication preserves an existing attachment original`() =
        runTest {
            val root = Files.createTempDirectory("logdate-file-hydration-").toFile()
            try {
                val bytes = "shared journal original".encodeToByteArray()
                var current = true
                var changeAfterSave = false
                val store = AndroidCanonicalMediaStore(root) { if (changeAfterSave) current = false }
                val shared = File(URI(store.store(bytes.inputStream(), "image/png", bytes.size.toLong())))
                val download = File(root, "download.png").apply { writeBytes(bytes) }
                val refs = InMemoryMediaSyncRefStore()
                val media = canonicalMedia(store)
                val note = remoteNote()
                val transfer = SyncMediaTransfer(media, refs, fileSource(note.uid, download))
                changeAfterSave = true

                assertFailsWith<CancellationException> { transfer.hydrate("synthetic", note) { current } }

                assertTrue(shared.isFile, "Canceling recovery must not delete another entry's deduplicated original")
                assertContentEquals(bytes, shared.readBytes())
                assertNull(refs.get(note.uid), "Canceled recovery cannot publish a media mapping")
                assertFalse(download.exists(), "The private download is disposable after cancellation")
            } finally {
                root.deleteRecursively()
            }
        }

    @Test
    fun `account change while staging prevents a new original from being published`() =
        runTest {
            val root = Files.createTempDirectory("logdate-file-hydration-").toFile()
            try {
                var current = true
                var commits = 0
                val store = AndroidCanonicalMediaStore(root) { commits++ }
                val download = File(root, "download.png").apply { writeText("new journal original") }
                val note = remoteNote()
                val refs = InMemoryMediaSyncRefStore()
                val media = canonicalMedia(store) { current = false }
                val transfer = SyncMediaTransfer(media, refs, fileSource(note.uid, download))

                assertFailsWith<CancellationException> { transfer.hydrate("synthetic", note) { current } }

                assertTrue(File(root, "media/objects").walkTopDown().none { it.isFile })
                assertTrue(File(root, "media/staging").walkTopDown().none { it.isFile })
                assertEquals(0, commits, "A revoked scope cannot publish a new durable object before cleanup")
                assertNull(refs.get(note.uid))
                assertFalse(download.exists())
            } finally {
                root.deleteRecursively()
            }
        }

    private fun canonicalMedia(
        store: AndroidCanonicalMediaStore,
        onReadComplete: () -> Unit = {},
    ): MediaManager =
        object : InMemoryMediaManager() {
            override suspend fun saveMediaFromFile(
                sourceFilePath: String,
                fileName: String,
                mimeType: String,
            ): String = saveMediaFromFile(sourceFilePath, fileName, mimeType) { true }

            override suspend fun saveMediaFromFile(
                sourceFilePath: String,
                fileName: String,
                mimeType: String,
                canCommit: () -> Boolean,
            ): String {
                val source = File(sourceFilePath)
                val input =
                    object : FilterInputStream(source.inputStream()) {
                        override fun read(
                            buffer: ByteArray,
                            offset: Int,
                            length: Int,
                        ): Int = super.read(buffer, offset, length).also { if (it < 0) onReadComplete() }
                    }
                return store.store(input, mimeType, source.length(), canCommit)
            }

            override suspend fun deleteOwnedMedia(uri: String): Boolean = store.deleteOwned(uri)
        }.let { AndroidPhotoLibraryMediaManager(it, mockk<Context>(relaxed = true)) }

    private fun remoteNote() =
        JournalNote.Image(
            Uuid.random(),
            Instant.fromEpochMilliseconds(1),
            Instant.fromEpochMilliseconds(1),
            "https://fixture.example/media/original",
        )

    private fun fileSource(
        noteId: Uuid,
        file: File,
    ) = object : CloudMediaDataSource, FileMediaDataSource {
        override suspend fun uploadMedia(
            accessToken: String,
            contentId: Uuid,
            media: MediaFileSource,
        ): Result<MediaUploadResult> = error("Recovery does not upload")

        override suspend fun downloadMedia(
            accessToken: String,
            mediaId: String,
        ): Result<MediaFile> = error("The regression must exercise file-based recovery")

        override suspend fun downloadMediaToFile(
            accessToken: String,
            reference: String,
            mediaId: String,
        ): Result<DownloadedMediaFile> =
            Result.success(DownloadedMediaFile(noteId, file.name, "image/png", Path(file.absolutePath), file.length()))
    }
}
