package app.logdate.client.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ManagedMediaImporterTest {
    @Test
    fun `imported image remains readable after source access is revoked`() =
        runTest {
            val sourceUri = "content://picker/images/42"
            val imageBytes = byteArrayOf(1, 3, 3, 7)
            val source =
                RevocableImportSource(
                    sourceUri = sourceUri,
                    fileName = "launch-day.jpg",
                    mimeType = "image/jpeg",
                    bytes = imageBytes,
                )
            val mediaManager = CopyingMediaManager(source::readStagedBytes)
            val importer = ManagedMediaImporter(mediaManager, source)

            val managedUri = importer.import(sourceUri)
            source.revokeAccess()

            assertNotEquals(sourceUri, managedUri)
            assertContentEquals(imageBytes, mediaManager.readManagedBytes(managedUri))
            assertEquals("image/jpeg", mediaManager.savedMimeTypes.single())
            assertEquals("launch-day.jpg", mediaManager.savedFileNames.single())
            assertTrue(source.discardedPaths.single().startsWith("staged://"))
            assertFalse(source.hasStagedFiles())
            assertTrue(mediaManager.hasManagedMedia(managedUri))
        }

    @Test
    fun `imported video remains readable after source access is revoked`() =
        runTest {
            val sourceUri = "content://picker/videos/84"
            val videoBytes = byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112)
            val source =
                RevocableImportSource(
                    sourceUri = sourceUri,
                    fileName = "launch-day.mp4",
                    mimeType = "video/mp4",
                    bytes = videoBytes,
                )
            val mediaManager = CopyingMediaManager(source::readStagedBytes)
            val importer = ManagedMediaImporter(mediaManager, source)

            val managedUri = importer.import(sourceUri)
            source.revokeAccess()

            assertNotEquals(sourceUri, managedUri)
            assertContentEquals(videoBytes, mediaManager.readManagedBytes(managedUri))
            assertEquals("video/mp4", mediaManager.savedMimeTypes.single())
            assertEquals("launch-day.mp4", mediaManager.savedFileNames.single())
            assertTrue(source.discardedPaths.single().startsWith("staged://"))
            assertFalse(source.hasStagedFiles())
            assertTrue(mediaManager.hasManagedMedia(managedUri))
        }

    @Test
    fun `unsafe source names cannot escape the managed import boundary`() {
        assertEquals(
            "family trip.jpg",
            safeImportedFileName(
                sourceName = "../../family trip.exe\u0000",
                preferredExtension = "jpg",
            ),
        )
        assertEquals(
            "imported_media.mp4",
            safeImportedFileName(
                sourceName = "..",
                preferredExtension = "mp4",
            ),
        )
        assertEquals(
            "already-safe.PNG",
            safeImportedFileName(
                sourceName = "already-safe.PNG",
                preferredExtension = "png",
            ),
        )
        assertEquals(
            "imported_media",
            safeImportedFileName(
                sourceName = "invoice.exe",
                preferredExtension = null,
            ),
        )
    }

    @Test
    fun `cancelled persistence removes only the staged copy and rethrows cancellation`() =
        runTest {
            val sourceUri = "content://picker/videos/cancelled"
            val source =
                RevocableImportSource(
                    sourceUri = sourceUri,
                    fileName = "cancelled.mp4",
                    mimeType = "video/mp4",
                    bytes = byteArrayOf(1, 2, 3),
                )
            val mediaManager =
                CopyingMediaManager(source::readStagedBytes).apply {
                    saveFailure = CancellationException("Editor left composition")
                }
            val importer = ManagedMediaImporter(mediaManager, source)

            assertFailsWith<CancellationException> {
                importer.import(sourceUri)
            }

            assertFalse(source.hasStagedFiles())
            assertEquals(1, source.discardedPaths.size)
            assertTrue(mediaManager.managedUris().isEmpty())
        }

    @Test
    fun `cancellation after destination creation discards managed media before returning`() =
        runTest {
            val sourceUri = "content://picker/images/cancel-after-save"
            val source =
                RevocableImportSource(
                    sourceUri = sourceUri,
                    fileName = "cancel-after-save.png",
                    mimeType = "image/png",
                    bytes = byteArrayOf(1, 2, 3, 4),
                )
            val mediaManager =
                CopyingMediaManager(source::readStagedBytes).apply {
                    cancelAfterSave = true
                }
            val discardedManagedUris = mutableListOf<String>()
            val importer =
                ManagedMediaImporter(
                    mediaManager = mediaManager,
                    source = source,
                    discardManagedMedia = discardedManagedUris::add,
                )
            var publishedUri: String? = null

            val importJob = launch { publishedUri = importer.import(sourceUri) }
            importJob.join()

            assertTrue(importJob.isCancelled)
            assertEquals(null, publishedUri)
            assertEquals(listOf("managed://media/1"), discardedManagedUris)
            assertFalse(source.hasStagedFiles())
        }
}

private class RevocableImportSource(
    private val sourceUri: String,
    private val fileName: String,
    private val mimeType: String,
    private val bytes: ByteArray,
) : ManagedMediaImportSource {
    private var canReadSource = true
    private var stageCount = 0
    private val stagedBytes = mutableMapOf<String, ByteArray>()
    val discardedPaths = mutableListOf<String>()

    override suspend fun stage(sourceUri: String): StagedMediaFile {
        check(canReadSource) { "Source grant was revoked" }
        check(sourceUri == this.sourceUri)
        val stagedPath = "staged://${++stageCount}"
        stagedBytes[stagedPath] = bytes.copyOf()
        return StagedMediaFile(stagedPath, fileName, mimeType)
    }

    override suspend fun discard(stagedMedia: StagedMediaFile) {
        discardedPaths += stagedMedia.sourceFilePath
        stagedBytes.remove(stagedMedia.sourceFilePath)
    }

    fun readStagedBytes(path: String): ByteArray = checkNotNull(stagedBytes[path]).copyOf()

    fun revokeAccess() {
        canReadSource = false
    }

    fun hasStagedFiles(): Boolean = stagedBytes.isNotEmpty()
}

private class CopyingMediaManager(
    private val readStagedBytes: (String) -> ByteArray,
) : MediaManager {
    private var saveCount = 0
    private val managedBytes = mutableMapOf<String, ByteArray>()
    val savedFileNames = mutableListOf<String>()
    val savedMimeTypes = mutableListOf<String>()
    var saveFailure: Exception? = null
    var cancelAfterSave: Boolean = false

    override suspend fun getMedia(uri: String): MediaObject = error("Not needed")

    override suspend fun deleteOwnedMedia(uri: String): Boolean = false

    override suspend fun exists(mediaId: String): Boolean = managedBytes.containsKey(mediaId)

    override suspend fun getRecentMedia(limit: Int): Flow<List<MediaObject>> = flowOf(emptyList())

    override suspend fun queryMediaByDate(
        start: Instant,
        end: Instant,
    ): Flow<List<MediaObject>> = flowOf(emptyList())

    override suspend fun addToDefaultCollection(uri: String) = Unit

    override suspend fun readMedia(uri: String): MediaPayload {
        val bytes = checkNotNull(managedBytes[uri])
        return MediaPayload("managed", "application/octet-stream", bytes.size.toLong(), bytes.copyOf())
    }

    override suspend fun saveMedia(payload: MediaPayload): String = error("Not needed")

    override suspend fun saveMediaFromFile(
        sourceFilePath: String,
        fileName: String,
        mimeType: String,
    ): String {
        saveFailure?.let { throw it }
        val managedUri = "managed://media/${++saveCount}"
        managedBytes[managedUri] = readStagedBytes(sourceFilePath)
        savedFileNames += fileName
        savedMimeTypes += mimeType
        if (cancelAfterSave) {
            currentCoroutineContext().cancel(CancellationException("Caller left after destination creation"))
        }
        return managedUri
    }

    fun readManagedBytes(uri: String): ByteArray = checkNotNull(managedBytes[uri]).copyOf()

    fun hasManagedMedia(uri: String): Boolean = managedBytes.containsKey(uri)

    fun managedUris(): Set<String> = managedBytes.keys
}
