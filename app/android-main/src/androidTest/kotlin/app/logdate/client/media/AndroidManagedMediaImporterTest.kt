package app.logdate.client.media

import android.Manifest
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.ByteArrayInputStream
import java.io.OutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/**
 * Exercises the real Android ContentResolver -> flushed staging file -> MediaStore boundary.
 *
 * The resulting image/video URI remains readable after the source backing data disappears,
 * and streams are closed before that happens. FileProvider coverage here does not claim to
 * simulate Android revoking a real external picker grant. Managed copies are user-visible
 * MediaStore rows, not app-private durability against deletion outside LogDate.
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
            discardManagedMedia = AndroidManagedMediaDiscarder(context, Dispatchers.Unconfined)::discard,
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
            context.contentResolver.delete(Uri.parse(uri), null, null)
        }
        sourceFiles.forEach(File::delete)
        File(context.cacheDir, STAGING_DIRECTORY_NAME).listFiles()?.forEach(File::delete)
    }

    @Test
    fun recentMediaStoreImageIsCopiedBeforeOriginalRowDisappears() =
        runTest {
            val imageBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 13, 10, 26, 10)
            val sourceFile = createSourceFile("recent", "png", imageBytes)
            val sourceUri =
                mediaManager
                    .saveMediaFromFile(
                        sourceFilePath = sourceFile.absolutePath,
                        fileName = sourceFile.name,
                        mimeType = "image/png",
                    ).also(publishedUris::add)

            val managedUri = importer.import(sourceUri).also(publishedUris::add)

            assertNotEquals(sourceUri, managedUri)
            assertTrue(managedUri.startsWith("content://media"))
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
    fun providerVideoIsCopiedAndClosedBeforeSourceAccessDisappears() =
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
    fun cancellationDuringMediaStoreCopyDeletesDestinationAndStagingFile() =
        runTest {
            val selectedUri = Uri.parse("content://picker.provider/images/cancelled")
            val insertedUri = Uri.parse("content://media/external/images/media/911")
            val fakeResolver = mockk<android.content.ContentResolver>()
            val fakeContext = mockk<Context>()
            val fakeRoot = File(context.cacheDir, "cancelled-import-${Uuid.random()}")
            val fakeCacheDirectory = File(fakeRoot, "cache")
            val fakeFilesDirectory = File(fakeRoot, "files")
            val nameCursor =
                MatrixCursor(arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)).apply {
                    addRow(arrayOf("cancelled.png"))
                }
            lateinit var importJob: Job
            every { fakeContext.applicationContext } returns fakeContext
            every { fakeContext.contentResolver } returns fakeResolver
            every { fakeContext.cacheDir } returns fakeCacheDirectory
            every { fakeContext.filesDir } returns fakeFilesDirectory
            every { fakeResolver.query(selectedUri, any(), null, null, null) } returns nameCursor
            every { fakeResolver.getType(selectedUri) } returns "image/png"
            every { fakeResolver.openInputStream(selectedUri) } returns ByteArrayInputStream(ByteArray(32 * 1024) { 7 })
            every { fakeResolver.insert(any(), any()) } returns insertedUri
            every { fakeResolver.openOutputStream(insertedUri, "w") } returns
                object : OutputStream() {
                    override fun write(byte: Int) = Unit

                    override fun write(
                        bytes: ByteArray,
                        offset: Int,
                        length: Int,
                    ) {
                        importJob.cancel(CancellationException("Editor left during destination copy"))
                    }
                }
            every { fakeResolver.update(any(), any(), any(), any()) } returns 1
            every { fakeResolver.delete(insertedUri, null, null) } returns 1
            val manager = AndroidMediaManager(fakeResolver, fakeContext, Dispatchers.Unconfined)
            val importer =
                ManagedMediaImporter(
                    mediaManager = manager,
                    source = AndroidManagedMediaImportSource(fakeContext, Dispatchers.Unconfined),
                    discardManagedMedia = AndroidManagedMediaDiscarder(fakeContext, Dispatchers.Unconfined)::discard,
                )

            try {
                importJob =
                    launch(start = CoroutineStart.LAZY) {
                        importer.import(selectedUri.toString())
                    }
                importJob.start()
                importJob.join()

                assertTrue(importJob.isCancelled)
                verify(exactly = 1) { fakeResolver.delete(insertedUri, null, null) }
                verify(exactly = 0) { fakeResolver.update(any(), any(), any(), any()) }
                assertTrue(
                    File(fakeCacheDirectory, STAGING_DIRECTORY_NAME).listFiles().isNullOrEmpty(),
                    "Cancellation must remove the private staging copy",
                )
            } finally {
                fakeRoot.deleteRecursively()
            }
        }

    @Test
    fun unknownImageSubtypeIsRejectedBeforeUntrustedExtensionCanBePublished() =
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
}

private const val STAGING_DIRECTORY_NAME = "managed_media_imports"
