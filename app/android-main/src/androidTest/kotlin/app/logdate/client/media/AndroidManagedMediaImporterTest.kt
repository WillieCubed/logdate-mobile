package app.logdate.client.media

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.ByteArrayInputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/**
 * Exercises the real Android ContentResolver -> flushed staging file -> app-private canonical
 * media store boundary.
 *
 * The resulting image/video URI remains readable after the source backing data disappears,
 * and streams are closed before that happens. FileProvider coverage here does not claim to
 * simulate Android revoking a real external picker grant. Managed copies live in LogDate's
 * private, content-addressed store -- not the shared MediaStore -- so deleting one never
 * touches the user's own photo library.
 */
@RunWith(AndroidJUnit4::class)
class AndroidManagedMediaImporterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mediaManager =
        AndroidMediaManager(
            contentResolver = context.contentResolver,
            context = context,
            ioDispatcher = Dispatchers.Unconfined,
        )
    private val importer =
        ManagedMediaImporter(
            mediaManager = mediaManager,
            source = AndroidManagedMediaImportSource(context, Dispatchers.Unconfined),
            discardManagedMedia = AndroidManagedMediaDiscarder(context, mediaManager, Dispatchers.Unconfined)::discard,
        )
    private val publishedUris = mutableSetOf<String>()
    private val sourceFiles = mutableSetOf<File>()
    private val requiredPermissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(*requiredPermissions)

    @After
    fun tearDown() {
        publishedUris.forEach { uri ->
            if (Uri.parse(uri).scheme == "file") {
                runBlocking { mediaManager.deleteOwnedMedia(uri) }
            } else {
                context.contentResolver.delete(Uri.parse(uri), null, null)
            }
        }
        sourceFiles.forEach(File::delete)
        File(context.cacheDir, STAGING_DIRECTORY_NAME).listFiles()?.forEach(File::delete)
    }

    @Test
    fun `recent media store image is copied before original row disappears`() =
        runTest {
            val imageBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 13, 10, 26, 10)
            val sourceUri = insertRealMediaStoreImage("recent-${Uuid.random()}.png", imageBytes).also(publishedUris::add)

            val managedUri = importer.import(sourceUri).also(publishedUris::add)

            assertNotEquals(sourceUri, managedUri)
            assertTrue(managedUri.startsWith("file:"))
            assertTrue(context.contentResolver.delete(Uri.parse(sourceUri), null, null) > 0)
            publishedUris.remove(sourceUri)
            assertSourceIsUnavailable(sourceUri)
            assertManagedPayload(
                managedUri = managedUri,
                expectedBytes = imageBytes,
                expectedMimeType = "image/png",
                expectedExtension = ".png",
            )
            assertStagingIsEmpty()
        }

    @Test
    fun `provider video is copied and closed before source access disappears`() =
        runTest {
            val videoBytes = byteArrayOf(0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70, 0x6D, 0x70, 0x34, 0x32)
            val sourceFile = createSourceFile("picker-video", "mp4", videoBytes)
            val sourceUri =
                FileProvider
                    .getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        sourceFile,
                    ).toString()

            val managedUri = importer.import(sourceUri).also(publishedUris::add)

            assertNotEquals(sourceUri, managedUri)
            assertTrue(sourceFile.delete())
            sourceFiles.remove(sourceFile)
            assertSourceIsUnavailable(sourceUri)
            assertManagedPayload(
                managedUri = managedUri,
                expectedBytes = videoBytes,
                expectedMimeType = "video/mp4",
                expectedExtension = ".mp4",
            )
            assertStagingIsEmpty()
        }

    @Test
    fun `cancellation after destination is created discards the managed copy and staging file`() =
        runTest {
            val sourceBytes = byteArrayOf(1, 2, 3, 4)
            val sourceUri =
                insertRealMediaStoreImage("cancel-source-${Uuid.random()}.png", sourceBytes)
                    .also(publishedUris::add)
            lateinit var importJob: Job
            var capturedManagedUri: String? = null
            val cancelAfterSaveMediaManager =
                object : MediaManager by mediaManager {
                    override suspend fun saveMediaFromFile(
                        sourceFilePath: String,
                        fileName: String,
                        mimeType: String,
                    ): String {
                        val managedUri = mediaManager.saveMediaFromFile(sourceFilePath, fileName, mimeType)
                        capturedManagedUri = managedUri
                        importJob.cancel(CancellationException("Editor left after destination creation"))
                        return managedUri
                    }
                }
            val cancellingImporter =
                ManagedMediaImporter(
                    mediaManager = cancelAfterSaveMediaManager,
                    source = AndroidManagedMediaImportSource(context, Dispatchers.Unconfined),
                    discardManagedMedia = AndroidManagedMediaDiscarder(context, mediaManager, Dispatchers.Unconfined)::discard,
                )

            try {
                importJob =
                    launch(start = CoroutineStart.LAZY) {
                        cancellingImporter.import(sourceUri)
                    }
                importJob.start()
                importJob.join()

                assertTrue(importJob.isCancelled)
                val managedUri = checkNotNull(capturedManagedUri) { "Destination copy never ran" }
                assertFalse(
                    mediaManager.deleteOwnedMedia(managedUri),
                    "Cancellation must have already discarded the managed copy",
                )
                assertStagingIsEmpty()
            } finally {
                context.contentResolver.delete(Uri.parse(sourceUri), null, null)
                publishedUris.remove(sourceUri)
            }
        }

    @Test
    fun `unknown image subtype is rejected before untrusted extension can be published`() =
        runTest {
            val selectedUri = Uri.parse("content://untrusted.provider/items/1")
            val fakeResolver = mockk<android.content.ContentResolver>()
            val fakeContext = mockk<Context>()
            val fakeCacheDirectory = File(context.cacheDir, "unknown-mime-${Uuid.random()}")
            val nameCursor =
                MatrixCursor(arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)).apply {
                    addRow(arrayOf("invoice.exe"))
                }
            every { fakeContext.applicationContext } returns fakeContext
            every { fakeContext.contentResolver } returns fakeResolver
            every { fakeContext.cacheDir } returns fakeCacheDirectory
            every { fakeResolver.query(selectedUri, any(), null, null, null) } returns nameCursor
            every { fakeResolver.getType(selectedUri) } returns "image/x-unrecognised"
            every { fakeResolver.openInputStream(selectedUri) } returns ByteArrayInputStream(byteArrayOf(1, 2, 3))
            val importSource = AndroidManagedMediaImportSource(fakeContext, Dispatchers.Unconfined)

            try {
                assertFailsWith<IllegalArgumentException> {
                    importSource.stage(selectedUri.toString())
                }
                verify(exactly = 0) { fakeResolver.openInputStream(selectedUri) }
                assertTrue(fakeCacheDirectory.listFiles().isNullOrEmpty())
            } finally {
                fakeCacheDirectory.deleteRecursively()
            }
        }

    private suspend fun assertManagedPayload(
        managedUri: String,
        expectedBytes: ByteArray,
        expectedMimeType: String,
        expectedExtension: String,
    ) {
        val payload = mediaManager.readMedia(managedUri)
        assertContentEquals(expectedBytes, payload.data)
        assertEquals(expectedMimeType, payload.mimeType)
        assertTrue(payload.fileName.endsWith(expectedExtension, ignoreCase = true))
    }

    private fun assertSourceIsUnavailable(sourceUri: String) {
        val sourceRemainsReadable =
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { input ->
                    input.read()
                } ?: error("Source URI returned no stream")
            }.isSuccess
        assertFalse(sourceRemainsReadable)
    }

    private fun assertStagingIsEmpty() {
        assertTrue(
            File(context.cacheDir, STAGING_DIRECTORY_NAME).listFiles().isNullOrEmpty(),
            "Managed import staging should be empty after publication",
        )
    }

    private fun createSourceFile(
        prefix: String,
        extension: String,
        bytes: ByteArray,
    ): File {
        val directory = File(context.cacheDir, "androidTestShare").apply { mkdirs() }
        return File(directory, "$prefix-${Uuid.random()}.$extension")
            .apply {
                writeBytes(bytes)
                check(setLastModified(System.currentTimeMillis()))
            }.also(sourceFiles::add)
    }

    /** Inserts a real row into the shared MediaStore, simulating a photo already on the device. */
    private fun insertRealMediaStoreImage(
        displayName: String,
        bytes: ByteArray,
    ): String {
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            }
        val uri =
            checkNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) {
                "Unable to insert a real MediaStore row for the test"
            }
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: error("Unable to open the inserted MediaStore row for writing")
        return uri.toString()
    }
}

private const val STAGING_DIRECTORY_NAME = "managed_media_imports"
