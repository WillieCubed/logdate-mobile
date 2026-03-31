package app.logdate.client.media

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class AndroidMediaManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mediaManager =
        AndroidMediaManager(
            context.contentResolver,
            context,
            Dispatchers.Unconfined,
        )
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
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(*requiredPermissions)

    @After
    fun tearDown() {
        context.filesDir.resolve("user_media").listFiles()?.forEach { file ->
            file.delete()
        }
    }

    @Test
    fun getRecentMedia_backfillsLegacyEntryMediaIntoMediaStore() = runTest {
        val legacyFile = createLegacyImageFile()
        var publishedUri: String? = null

        try {
            val recentMedia = mediaManager.getRecentMedia().first()
            val backfilled = recentMedia.firstOrNull { it.name == legacyFile.name }

            assertNotNull(backfilled, "Expected the legacy file to appear in recent media")
            assertTrue(backfilled.uri.startsWith("content://media"))
            assertTrue(legacyFile.exists())

            publishedUri = backfilled.uri
        } finally {
            publishedUri?.let { uri ->
                context.contentResolver.delete(android.net.Uri.parse(uri), null, null)
            }
            legacyFile.delete()
        }
    }

    @Test
    fun getMedia_readsFileUrisAndUsesDateAddedFallbackWhenNeeded() = runTest {
        val imageFile = createImageFile(context.cacheDir, "file-image")
        val videoFile = File(context.cacheDir, "file-video-${Uuid.random()}.mp4")
        videoFile.writeBytes(byteArrayOf(0, 1, 2, 3))
        videoFile.setLastModified(1_234_567_890_000L)

        try {
            val image = mediaManager.getMedia(Uri.fromFile(imageFile).toString())
            assertTrue(image is MediaObject.Image)
            assertEquals(imageFile.name, image.name)
            assertEquals(imageFile.length().toInt(), image.size)

            assertFailsWith<IllegalStateException> {
                mediaManager.getMedia(Uri.fromFile(videoFile).toString())
            }

            val environment = createMockEnvironment()
            val fallbackUri = Uri.parse("content://example.media/items/fallback.png")

            try {
                every { environment.contentResolver.getType(fallbackUri) } returns null
                every {
                    environment.contentResolver.query(
                        fallbackUri,
                        any(),
                        null,
                        null,
                        null,
                    )
                } answers {
                    when (secondArg<Array<String>>().toList()) {
                        listOf(MediaStore.MediaColumns.DISPLAY_NAME) ->
                            emptyCursor(MediaStore.MediaColumns.DISPLAY_NAME)
                        listOf(
                            MediaStore.MediaColumns.DATE_TAKEN,
                            MediaStore.MediaColumns.DATE_ADDED,
                        ) ->
                            singleRowCursor(
                                MediaStore.MediaColumns.DATE_TAKEN to 0L,
                                MediaStore.MediaColumns.DATE_ADDED to 42L,
                            )
                        else -> emptyCursor(*secondArg<Array<String>>())
                    }
                }
                every {
                    environment.contentResolver.openInputStream(fallbackUri)
                } returns ByteArrayInputStream(byteArrayOf(9, 8, 7))

                val payload = environment.mediaManager.readMedia(fallbackUri.toString())
                assertEquals("fallback.png", payload.fileName)
                assertEquals("image/png", payload.mimeType)
                assertEquals(3, payload.data.size)

                val fallbackNameUri = Uri.parse("content://example.media/items/blob")
                every { environment.contentResolver.getType(fallbackNameUri) } returns null
                every {
                    environment.contentResolver.query(
                        fallbackNameUri,
                        any(),
                        null,
                        null,
                        null,
                    )
                } answers {
                    when (secondArg<Array<String>>().toList()) {
                        listOf(MediaStore.MediaColumns.DISPLAY_NAME) -> emptyCursor(MediaStore.MediaColumns.DISPLAY_NAME)
                        else -> emptyCursor(*secondArg<Array<String>>())
                    }
                }
                every {
                    environment.contentResolver.openInputStream(fallbackNameUri)
                } returns ByteArrayInputStream(byteArrayOf())

                assertFailsWith<IllegalArgumentException> {
                    environment.mediaManager.readMedia(fallbackNameUri.toString())
                }
            } finally {
                environment.filesDir.deleteRecursively()
                clearAllMocks()
            }
        } finally {
            imageFile.delete()
            videoFile.delete()
        }
    }

    @Test
    fun saveMediaFromFile_publishesImageIntoMediaStore() = runTest {
        val sourceFile = createImageFile(context.cacheDir, "source")
        var savedUri: String? = null

        try {
            savedUri =
                mediaManager.saveMediaFromFile(
                    sourceFilePath = sourceFile.absolutePath,
                    fileName = sourceFile.name,
                    mimeType = "image/png",
                )

            assertTrue(savedUri.startsWith("content://media"))

            val media = mediaManager.getMedia(savedUri)
            assertEquals(sourceFile.name, media.name)
            assertTrue(media.size > 0)
        } finally {
            savedUri?.let { uri ->
                context.contentResolver.delete(android.net.Uri.parse(uri), null, null)
            }
            sourceFile.delete()
        }
    }

    @Test
    fun saveMediaFromFile_rejectsUnsupportedMimeType() = runTest {
        val sourceFile = createImageFile(context.cacheDir, "fallback-file")

        try {
            assertFailsWith<IllegalArgumentException> {
                mediaManager.saveMediaFromFile(
                    sourceFilePath = sourceFile.absolutePath,
                    fileName = "../fallback.txt",
                    mimeType = "text/plain",
                )
            }
            assertTrue(context.filesDir.resolve("user_media").listFiles().isNullOrEmpty())
        } finally {
            sourceFile.delete()
        }
    }

    @Test
    fun saveMedia_publishesImageIntoMediaStore() = runTest {
        val sourceFile = createImageFile(context.cacheDir, "payload")
        val payloadBytes = sourceFile.readBytes()
        var savedUri: String? = null

        try {
            savedUri =
                mediaManager.saveMedia(
                    MediaPayload(
                        fileName = "../payload.png",
                        mimeType = "image/png",
                        sizeBytes = payloadBytes.size.toLong(),
                        data = payloadBytes,
                    ),
                )

            assertTrue(savedUri.startsWith("content://media"))

            val media = mediaManager.getMedia(savedUri)
            assertTrue(media is MediaObject.Image)
            assertEquals("__payload.png", media.name)
        } finally {
            savedUri?.let { uri ->
                context.contentResolver.delete(Uri.parse(uri), null, null)
            }
            sourceFile.delete()
        }
    }

    @Test
    fun saveMedia_throwsWhenPublishingFails() = runTest {
        val environment = createMockEnvironment()
        val payloadBytes = byteArrayOf(1, 2, 3, 4)
        val payload =
            MediaPayload(
                fileName = "publish.png",
                mimeType = "image/png",
                sizeBytes = payloadBytes.size.toLong(),
                data = payloadBytes,
            )

        try {
            every { environment.contentResolver.insert(any(), any()) } returns null

            assertFailsWith<IllegalStateException> {
                environment.mediaManager.saveMedia(payload)
            }
            assertTrue(environment.filesDir.resolve("user_media").listFiles().isNullOrEmpty())
        } finally {
            environment.filesDir.deleteRecursively()
            clearAllMocks()
        }
    }

    @Test
    fun saveMedia_rejectsUnsupportedMimeType() = runTest {
        val sourceFile = createImageFile(context.cacheDir, "fallback")
        val payloadBytes = sourceFile.readBytes()

        try {
            assertFailsWith<IllegalArgumentException> {
                mediaManager.saveMedia(
                    MediaPayload(
                        fileName = "../fallback.txt",
                        mimeType = "",
                        sizeBytes = payloadBytes.size.toLong(),
                        data = payloadBytes,
                    ),
                )
            }
            assertTrue(context.filesDir.resolve("user_media").listFiles().isNullOrEmpty())
        } finally {
            sourceFile.delete()
        }
    }

    @Test
    fun saveMediaFromFile_throwsWhenPublishingFails() = runTest {
        val environment = createMockEnvironment()
        val sourceFile = createImageFile(environment.filesDir, "publish-fallback")

        try {
            every { environment.contentResolver.insert(any(), any()) } returns null

            assertFailsWith<IllegalStateException> {
                environment.mediaManager.saveMediaFromFile(
                    sourceFilePath = sourceFile.absolutePath,
                    fileName = sourceFile.name,
                    mimeType = "image/png",
                )
            }
            assertTrue(environment.filesDir.resolve("user_media").listFiles().isNullOrEmpty())
        } finally {
            environment.filesDir.deleteRecursively()
            clearAllMocks()
        }
    }

    @Test
    fun saveMediaFromFile_publishesVideoIntoMediaStore() = runTest {
        val sourceFile = File(context.cacheDir, "video-${Uuid.random()}.mp4")
        sourceFile.writeBytes(byteArrayOf(0, 1, 2, 3, 4))
        sourceFile.setLastModified(1_234_567_890_000L)
        var savedUri: String? = null

        try {
            savedUri =
                mediaManager.saveMediaFromFile(
                    sourceFilePath = sourceFile.absolutePath,
                    fileName = "../clip.mp4",
                    mimeType = "video/mp4",
                )

            assertTrue(savedUri.startsWith("content://media"))

            assertTrue(mediaManager.exists(savedUri))
        } finally {
            savedUri?.let { uri ->
                context.contentResolver.delete(Uri.parse(uri), null, null)
            }
            sourceFile.delete()
        }
    }

    @Test
    fun addToDefaultCollection_publishesContentUriIntoMediaStore() = runTest {
        val environment = createMockEnvironment()
        val sourceUri = Uri.parse("content://example.media/items/1")
        val payloadBytes = byteArrayOf(10, 11, 12)
        val valuesSlot = slot<ContentValues>()

        try {
            every { environment.contentResolver.getType(sourceUri) } returns "image/png"
            every {
                environment.contentResolver.query(
                    sourceUri,
                    any(),
                    null,
                    null,
                    null,
                )
            } answers {
                when (secondArg<Array<String>>().toList()) {
                    listOf(MediaStore.MediaColumns.DISPLAY_NAME) ->
                        singleRowCursor(MediaStore.MediaColumns.DISPLAY_NAME to "../camera.png")
                    listOf(
                        MediaStore.MediaColumns.DATE_TAKEN,
                        MediaStore.MediaColumns.DATE_ADDED,
                    ) ->
                        singleRowCursor(
                            MediaStore.MediaColumns.DATE_TAKEN to 1_000L,
                            MediaStore.MediaColumns.DATE_ADDED to 1L,
                        )
                    else -> emptyCursor(*secondArg<Array<String>>())
                }
            }
            every { environment.contentResolver.openInputStream(sourceUri) } returns ByteArrayInputStream(payloadBytes)
            every { environment.contentResolver.insert(any(), capture(valuesSlot)) } returns Uri.parse("content://media/external/images/media/101")
            every { environment.contentResolver.openOutputStream(any(), any()) } returns java.io.ByteArrayOutputStream()
            every { environment.contentResolver.update(any(), any(), any(), any()) } returns 1

            environment.mediaManager.addToDefaultCollection(sourceUri.toString())

            verify(exactly = 1) {
                environment.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, any())
                environment.contentResolver.openOutputStream(Uri.parse("content://media/external/images/media/101"), "w")
                environment.contentResolver.update(Uri.parse("content://media/external/images/media/101"), any(), null, null)
            }
            assertEquals("__camera.png", valuesSlot.captured.getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
            assertEquals("image/png", valuesSlot.captured.getAsString(MediaStore.MediaColumns.MIME_TYPE))
            assertEquals(1_000L, valuesSlot.captured.getAsLong(MediaStore.MediaColumns.DATE_TAKEN))
            assertEquals(1, valuesSlot.captured.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
        } finally {
            environment.filesDir.deleteRecursively()
            clearAllMocks()
        }
    }

    @Test
    fun addToDefaultCollection_throwsWhenTimestampMetadataIsMissing() = runTest {
        val environment = createMockEnvironment()
        val sourceUri = Uri.parse("content://example.media/items/no-timestamp.png")

        try {
            every { environment.contentResolver.getType(sourceUri) } returns "image/png"
            every {
                environment.contentResolver.query(
                    sourceUri,
                    any(),
                    null,
                    null,
                    null,
                )
            } answers {
                when (secondArg<Array<String>>().toList()) {
                    listOf(MediaStore.MediaColumns.DISPLAY_NAME) ->
                        singleRowCursor(MediaStore.MediaColumns.DISPLAY_NAME to "../no-timestamp.png")
                    listOf(
                        MediaStore.MediaColumns.DATE_TAKEN,
                        MediaStore.MediaColumns.DATE_ADDED,
                    ) ->
                        emptyCursor(
                            MediaStore.MediaColumns.DATE_TAKEN,
                            MediaStore.MediaColumns.DATE_ADDED,
                        )
                    else -> emptyCursor(*secondArg<Array<String>>())
                }
            }

            assertFailsWith<IllegalStateException> {
                environment.mediaManager.addToDefaultCollection(sourceUri.toString())
            }
            assertTrue(environment.filesDir.resolve("user_media").listFiles().isNullOrEmpty())
        } finally {
            environment.filesDir.deleteRecursively()
            clearAllMocks()
        }
    }

    @Test
    fun addToDefaultCollection_failsWhenPublishAndFallbackCopyFail() = runTest {
        val environment = createMockEnvironment()
        val sourceUri = Uri.parse("content://example.media/items/fail.png")

        try {
            every { environment.contentResolver.getType(sourceUri) } returns "image/png"
            every {
                environment.contentResolver.query(
                    sourceUri,
                    any(),
                    null,
                    null,
                    null,
                )
            } answers {
                when (secondArg<Array<String>>().toList()) {
                    listOf(MediaStore.MediaColumns.DISPLAY_NAME) ->
                        singleRowCursor(MediaStore.MediaColumns.DISPLAY_NAME to "fail.png")
                    listOf(
                        MediaStore.MediaColumns.DATE_TAKEN,
                        MediaStore.MediaColumns.DATE_ADDED,
                    ) ->
                        singleRowCursor(
                            MediaStore.MediaColumns.DATE_TAKEN to 2_000L,
                            MediaStore.MediaColumns.DATE_ADDED to 2L,
                        )
                    else -> emptyCursor(*secondArg<Array<String>>())
                }
            }
            every {
                environment.contentResolver.openInputStream(sourceUri)
            } returns object : java.io.InputStream() {
                override fun read(): Int = throw IllegalStateException("boom")
            }
            every { environment.contentResolver.insert(any(), any()) } returns Uri.parse("content://media/external/images/media/202")
            every { environment.contentResolver.openOutputStream(any(), any()) } returns java.io.ByteArrayOutputStream()
            every { environment.contentResolver.update(any(), any(), any(), any()) } returns 1
            every { environment.contentResolver.delete(any(), any(), any()) } returns 1

            assertFailsWith<IllegalStateException> {
                environment.mediaManager.addToDefaultCollection(sourceUri.toString())
            }
            assertTrue(environment.filesDir.resolve("user_media").listFiles().isNullOrEmpty())
        } finally {
            environment.filesDir.deleteRecursively()
            clearAllMocks()
        }
    }

    @Test
    fun addToDefaultCollection_publishesNewFileUriAndSkipsDuplicate() = runTest {
        val freshFile = createImageFile(context.cacheDir, "fresh")
        val duplicateFile = createImageFile(context.cacheDir, "duplicate")
        duplicateFile.setLastModified(2_222_222_222_000L)
        var freshPublishedUri: String? = null
        var duplicatePublishedUri: String? = null

        try {
            mediaManager.addToDefaultCollection(Uri.fromFile(freshFile).toString())
            val afterFresh = mediaManager.getRecentMedia().first().count { it.name == freshFile.name }
            assertEquals(1, afterFresh)

            duplicatePublishedUri =
                mediaManager.saveMediaFromFile(
                    sourceFilePath = duplicateFile.absolutePath,
                    fileName = duplicateFile.name,
                    mimeType = "image/png",
                )
            val beforeDuplicate = mediaManager.getRecentMedia().first().count { it.name == duplicateFile.name }
            mediaManager.addToDefaultCollection(Uri.fromFile(duplicateFile).toString())
            val afterDuplicate = mediaManager.getRecentMedia().first().count { it.name == duplicateFile.name }

            assertEquals(beforeDuplicate, afterDuplicate)
            assertEquals(1, afterDuplicate)

            freshPublishedUri = mediaManager.getRecentMedia().first().firstOrNull { it.name == freshFile.name }?.uri
        } finally {
            freshPublishedUri?.let { uri ->
                context.contentResolver.delete(Uri.parse(uri), null, null)
            }
            duplicatePublishedUri?.let { uri ->
                context.contentResolver.delete(Uri.parse(uri), null, null)
            }
            freshFile.delete()
            duplicateFile.delete()
        }
    }

    @Test
    fun addToDefaultCollection_rejectsUnsupportedContentUri() = runTest {
        val environment = createMockEnvironment()
        val sourceUri = Uri.parse("content://example.media/items/1")

        try {
            every { environment.contentResolver.getType(sourceUri) } returns "text/plain"
            every {
                environment.contentResolver.query(
                    sourceUri,
                    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )
            } returns singleRowCursor(
                MediaStore.MediaColumns.DISPLAY_NAME to "../note.txt",
            )

            assertFailsWith<IllegalArgumentException> {
                environment.mediaManager.addToDefaultCollection(sourceUri.toString())
            }
            assertTrue(environment.filesDir.resolve("user_media").listFiles().isNullOrEmpty())
        } finally {
            environment.filesDir.deleteRecursively()
            clearAllMocks()
        }
    }

    @Test
    fun getMedia_rejectsUnsupportedFileType() = runTest {
        val file = File(context.cacheDir, "unsupported-${Uuid.random()}.txt")
        file.writeText("not media")

        try {
            assertFailsWith<IllegalArgumentException> {
                mediaManager.getMedia(Uri.fromFile(file).toString())
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun getMedia_readsImageAndVideoMetadataFromCursor() = runTest {
        val environment = createMockEnvironment()
        val imageUri = Uri.parse("content://example.media/images/1")
        val videoUri = Uri.parse("content://example.media/videos/2")

        try {
            every { environment.contentResolver.getType(imageUri) } returns "image/png"
            every {
                environment.contentResolver.query(
                    imageUri,
                    any(),
                    null,
                    null,
                    null,
                )
            } returns singleRowCursor(
                MediaStore.MediaColumns.DISPLAY_NAME to "image.png",
                MediaStore.MediaColumns.SIZE to 321,
                MediaStore.MediaColumns.DATE_TAKEN to 10_000L,
                MediaStore.MediaColumns.DATE_ADDED to 9L,
            )

            every { environment.contentResolver.getType(videoUri) } returns "video/mp4"
            every {
                environment.contentResolver.query(
                    videoUri,
                    any(),
                    null,
                    null,
                    null,
                )
            } returns singleRowCursor(
                MediaStore.MediaColumns.DISPLAY_NAME to "video.mp4",
                MediaStore.MediaColumns.SIZE to 654,
                MediaStore.Video.Media.DURATION to 4_500L,
                MediaStore.MediaColumns.DATE_TAKEN to 0L,
                MediaStore.MediaColumns.DATE_ADDED to 11L,
            )

            val image = environment.mediaManager.getMedia(imageUri.toString())
            assertTrue(image is MediaObject.Image)
            assertEquals("image.png", image.name)
            assertEquals(321, image.size)
            assertEquals(10_000L, image.timestamp.toEpochMilliseconds())

            val video = environment.mediaManager.getMedia(videoUri.toString())
            assertTrue(video is MediaObject.Video)
            assertEquals("video.mp4", video.name)
            assertEquals(654, video.size)
            assertEquals(4_500L, video.duration.inWholeMilliseconds)
            assertEquals(11_000L, video.timestamp.toEpochMilliseconds())
        } finally {
            environment.filesDir.deleteRecursively()
            clearAllMocks()
        }
    }

    @Test
    fun getMedia_returnsFallbackObjectsWhenCursorHasNoRows() = runTest {
        val environment = createMockEnvironment()
        val imageUri = Uri.parse("content://example.media/images/empty")
        val videoUri = Uri.parse("content://example.media/videos/empty")

        try {
            every { environment.contentResolver.getType(imageUri) } returns "image/png"
            every {
                environment.contentResolver.query(
                    imageUri,
                    any(),
                    null,
                    null,
                    null,
                )
            } returns emptyCursor(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DATE_ADDED,
            )

            every { environment.contentResolver.getType(videoUri) } returns "video/mp4"
            every {
                environment.contentResolver.query(
                    videoUri,
                    any(),
                    null,
                    null,
                    null,
                )
            } returns emptyCursor(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.Video.Media.DURATION,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DATE_ADDED,
            )

            assertFailsWith<IllegalStateException> {
                environment.mediaManager.getMedia(imageUri.toString())
            }

            assertFailsWith<IllegalStateException> {
                environment.mediaManager.getMedia(videoUri.toString())
            }
        } finally {
            environment.filesDir.deleteRecursively()
            clearAllMocks()
        }
    }

    @Test
    fun queryMediaByDate_returnsSortedImageAndVideoResults() = runTest {
        val environment = createMockEnvironment()
        val start = Instant.fromEpochMilliseconds(0)
        val end = Instant.fromEpochMilliseconds(20_000L)
        val imageCollection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val videoCollection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        try {
            every {
                environment.contentResolver.query(
                    imageCollection,
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns singleRowCursor(
                MediaStore.Images.Media._ID to 2L,
                MediaStore.MediaColumns.DISPLAY_NAME to "image.png",
                MediaStore.MediaColumns.SIZE to 321,
                MediaStore.MediaColumns.DATE_TAKEN to 15_000L,
                MediaStore.MediaColumns.DATE_ADDED to 14L,
            )

            every {
                environment.contentResolver.query(
                    videoCollection,
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns singleRowCursor(
                MediaStore.Video.Media._ID to 1L,
                MediaStore.MediaColumns.DISPLAY_NAME to "video.mp4",
                MediaStore.MediaColumns.SIZE to 654,
                MediaStore.Video.Media.DURATION to 4_500L,
                MediaStore.MediaColumns.DATE_TAKEN to 5_000L,
                MediaStore.MediaColumns.DATE_ADDED to 4L,
            )

            val result = environment.mediaManager.queryMediaByDate(start, end).first()
            assertEquals(2, result.size)
            assertTrue(result[0] is MediaObject.Video)
            assertTrue(result[1] is MediaObject.Image)
            assertEquals(5_000L, result[0].timestamp.toEpochMilliseconds())
            assertEquals(15_000L, result[1].timestamp.toEpochMilliseconds())
        } finally {
            environment.filesDir.deleteRecursively()
            clearAllMocks()
        }
    }

    @Test
    fun queryMediaByDate_throwsWhenQueriesFailAndNoMediaFound() = runTest {
        val environment = createMockEnvironment()
        val start = Instant.fromEpochMilliseconds(0)
        val end = Instant.fromEpochMilliseconds(20_000L)
        val imageCollection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val videoCollection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        try {
            every {
                environment.contentResolver.query(
                    imageCollection,
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } throws SecurityException("no image access")

            every {
                environment.contentResolver.query(
                    videoCollection,
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } throws SecurityException("no video access")

            assertFailsWith<SecurityException> {
                environment.mediaManager.queryMediaByDate(start, end).first()
            }
        } finally {
            environment.filesDir.deleteRecursively()
            clearAllMocks()
        }
    }

    @Test
    fun getRecentMedia_throwsWhenQueriesFail() = runTest {
        val environment = createMockEnvironment()
        val imageCollection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val videoCollection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        try {
            every {
                environment.contentResolver.query(
                    imageCollection,
                    any(),
                    null,
                    null,
                    any(),
                )
            } throws SecurityException("no permission")

            every {
                environment.contentResolver.query(
                    videoCollection,
                    any(),
                    null,
                    null,
                    any(),
                )
            } throws IllegalStateException("broken provider")

            assertFailsWith<SecurityException> {
                environment.mediaManager.getRecentMedia().first()
            }
        } finally {
            environment.filesDir.deleteRecursively()
            clearAllMocks()
        }
    }

    @Test
    fun queryMediaByDate_throwsWhenRowMetadataIsMissing() = runTest {
        val environment = createMockEnvironment()
        val start = Instant.fromEpochMilliseconds(0)
        val end = Instant.fromEpochMilliseconds(20_000L)
        val imageCollection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val videoCollection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        try {
            every {
                environment.contentResolver.query(
                    imageCollection,
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns singleRowCursor(
                MediaStore.Images.Media._ID to 2L,
                MediaStore.MediaColumns.DISPLAY_NAME to "image.png",
                MediaStore.MediaColumns.SIZE to 321,
                MediaStore.MediaColumns.DATE_TAKEN to 0L,
                MediaStore.MediaColumns.DATE_ADDED to 0L,
            )

            every {
                environment.contentResolver.query(
                    videoCollection,
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns emptyCursor(
                MediaStore.Video.Media._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.Video.Media.DURATION,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DATE_ADDED,
            )

            assertFailsWith<IllegalStateException> {
                environment.mediaManager.queryMediaByDate(start, end).first()
            }
        } finally {
            environment.filesDir.deleteRecursively()
            clearAllMocks()
        }
    }

    @Test
    fun getRecentMedia_throwsWhenRowMetadataIsMissing() = runTest {
        val environment = createMockEnvironment()
        val imageCollection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val videoCollection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        try {
            every {
                environment.contentResolver.query(
                    imageCollection,
                    any(),
                    null,
                    null,
                    any(),
                )
            } returns singleRowCursor(
                MediaStore.Images.Media._ID to 2L,
                MediaStore.MediaColumns.DISPLAY_NAME to "image.png",
                MediaStore.MediaColumns.SIZE to 321,
                MediaStore.MediaColumns.DATE_TAKEN to 0L,
                MediaStore.MediaColumns.DATE_ADDED to 0L,
            )

            every {
                environment.contentResolver.query(
                    videoCollection,
                    any(),
                    null,
                    null,
                    any(),
                )
            } returns emptyCursor(
                MediaStore.Video.Media._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.Video.Media.DURATION,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DATE_ADDED,
            )

            assertFailsWith<IllegalStateException> {
                environment.mediaManager.getRecentMedia().first()
            }
        } finally {
            environment.filesDir.deleteRecursively()
            clearAllMocks()
        }
    }

    @Test
    fun getMedia_throwsWhenTimestampMetadataIsMissing() = runTest {
        val environment = createMockEnvironment()
        val imageUri = Uri.parse("content://example.media/images/missing-timestamp")
        val videoUri = Uri.parse("content://example.media/videos/missing-timestamp")

        try {
            every { environment.contentResolver.getType(imageUri) } returns "image/png"
            every {
                environment.contentResolver.query(
                    imageUri,
                    any(),
                    null,
                    null,
                    null,
                )
            } returns singleRowCursor(
                MediaStore.MediaColumns.DISPLAY_NAME to "image.png",
                MediaStore.MediaColumns.SIZE to 321,
                MediaStore.MediaColumns.DATE_TAKEN to 0L,
                MediaStore.MediaColumns.DATE_ADDED to 0L,
            )

            every { environment.contentResolver.getType(videoUri) } returns "video/mp4"
            every {
                environment.contentResolver.query(
                    videoUri,
                    any(),
                    null,
                    null,
                    null,
                )
            } returns singleRowCursor(
                MediaStore.MediaColumns.DISPLAY_NAME to "video.mp4",
                MediaStore.MediaColumns.SIZE to 654,
                MediaStore.Video.Media.DURATION to 4_500L,
                MediaStore.MediaColumns.DATE_TAKEN to 0L,
                MediaStore.MediaColumns.DATE_ADDED to 0L,
            )

            assertFailsWith<IllegalStateException> {
                environment.mediaManager.getMedia(imageUri.toString())
            }
            assertFailsWith<IllegalStateException> {
                environment.mediaManager.getMedia(videoUri.toString())
            }
        } finally {
            environment.filesDir.deleteRecursively()
            clearAllMocks()
        }
    }

    @Test
    fun exists_recognizesPersistedMediaUris() = runTest {
        val environment = createMockEnvironment()
        val sourceFile = createImageFile(environment.filesDir, "exists")
        val contentUri = Uri.parse("content://example.media/items/1")
        val rawId = "123"

        try {
            every {
                environment.contentResolver.query(
                    any(),
                    any(),
                    null,
                    null,
                    null,
                )
            } answers {
                val uri = firstArg<Uri>().toString()
                val lastPathSegment = firstArg<Uri>().lastPathSegment
                if (uri.contains("missing") || lastPathSegment == "not-a-real-id") {
                    emptyCursor(MediaStore.MediaColumns._ID)
                } else {
                    singleRowCursor(MediaStore.MediaColumns._ID to 1L)
                }
            }

            assertTrue(environment.mediaManager.exists(contentUri.toString()))
            assertTrue(environment.mediaManager.exists(rawId))
            assertTrue(environment.mediaManager.exists(Uri.fromFile(sourceFile).toString()))
            assertFalse(environment.mediaManager.exists("content://example.media/missing"))
            assertFalse(environment.mediaManager.exists("not-a-real-id"))
            assertFalse(environment.mediaManager.exists("file://"))
        } finally {
            environment.filesDir.deleteRecursively()
            clearAllMocks()
        }
    }

    @Test
    fun getMedia_withMalformedFileUri_throwsIllegalArgumentException() = runTest {
        assertFailsWith<IllegalArgumentException> {
            mediaManager.getMedia("file://")
        }
    }

    private fun createLegacyImageFile(): File {
        val directory = context.filesDir.resolve("user_media").apply {
            mkdirs()
        }
        return createImageFile(directory, "legacy")
    }

    private fun createImageFile(
        directory: File,
        prefix: String,
    ): File {
        val file = File(directory, "$prefix-${Uuid.random()}.png")
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.MAGENTA)
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    private data class MockEnvironment(
        val contentResolver: ContentResolver,
        val mediaManager: AndroidMediaManager,
        val filesDir: File,
    )

    private fun createMockEnvironment(): MockEnvironment {
        val filesDir = File(context.cacheDir, "media-${Uuid.random()}").apply { mkdirs() }
        val contentResolver = mockk<ContentResolver>()
        val mockContext = mockk<Context>()

        every { mockContext.filesDir } returns filesDir

        return MockEnvironment(
            contentResolver = contentResolver,
            mediaManager = AndroidMediaManager(contentResolver, mockContext, Dispatchers.Unconfined),
            filesDir = filesDir,
        )
    }

    private fun singleRowCursor(vararg values: Pair<String, Any?>): Cursor =
        MatrixCursor(values.map { it.first }.toTypedArray()).apply {
            addRow(values.map { it.second }.toTypedArray())
        }

    private fun emptyCursor(vararg columns: String): Cursor = MatrixCursor(columns)
}
