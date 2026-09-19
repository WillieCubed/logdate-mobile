package app.logdate.client.sync.cloud

import app.logdate.client.media.MediaFileSource
import app.logdate.client.sync.test.FakeCloudApiClient
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Media uploads must read the file only while the request body is written. A recording can be
 * tens of megabytes; loading it into memory before the upload starts is what let one sync pass
 * exhaust the Android heap.
 */
class CloudMediaUploadStreamingTest {
    @Test
    fun `upload reaches the API client without the file having been read`() =
        runTest {
            val file = CountingMediaFile(ByteArray(2 * 1024 * 1024) { (it % 251).toByte() })
            val apiClient = CapturingCloudApiClient()

            DefaultCloudMediaDataSource(apiClient).uploadMedia("token", Uuid.random(), file).getOrThrow()

            val upload = apiClient.captured ?: error("No upload reached the API client")
            assertTrue(
                file.bytesRead <= HEADER_PEEK_BYTES,
                "Read ${file.bytesRead} bytes before the request body was written",
            )
            assertEquals(file.data.size.toLong(), upload.sizeBytes)
            assertContentEquals(file.data, upload.openBody().buffered().use { it.readByteArray() })
        }

    @Test
    fun `media over the upload limit is refused from its size alone`() =
        runTest {
            val oversized = CountingMediaFile(ByteArray(0), declaredSize = 40L * 1024 * 1024)
            val apiClient = CapturingCloudApiClient()

            val result = DefaultCloudMediaDataSource(apiClient).uploadMedia("token", Uuid.random(), oversized)

            assertIs<MediaTooLargeException>(result.exceptionOrNull())
            assertNull(apiClient.captured, "An upload that can never succeed must not be sent")
            assertTrue(oversized.bytesRead <= HEADER_PEEK_BYTES)
        }

    private class CapturingCloudApiClient : FakeCloudApiClient() {
        var captured: MediaUpload? = null

        override suspend fun uploadMedia(
            accessToken: String,
            media: MediaUpload,
        ): Result<MediaUploadResponse> {
            captured = media
            return Result.success(
                MediaUploadResponse(
                    contentId = media.contentId,
                    mediaId = "media-1",
                    downloadUrl = "https://example.com/media-1",
                    uploadedAt = 0,
                ),
            )
        }
    }

    /** A file whose reads are counted, standing in for a recording on disk. */
    private class CountingMediaFile(
        val data: ByteArray,
        private val declaredSize: Long = data.size.toLong(),
    ) {
        var bytesRead = 0L
            private set

        fun asSource(): MediaFileSource = MediaFileSource("recording.m4a", "audio/mp4", declaredSize) { open() }

        private fun open(): RawSource {
            val buffer = Buffer().apply { write(data) }
            return object : RawSource {
                override fun readAtMostTo(
                    sink: Buffer,
                    byteCount: Long,
                ): Long {
                    val read = buffer.readAtMostTo(sink, byteCount)
                    if (read > 0) bytesRead += read
                    return read
                }

                override fun close() = Unit
            }
        }
    }

    private suspend fun DefaultCloudMediaDataSource.uploadMedia(
        accessToken: String,
        contentId: Uuid,
        file: CountingMediaFile,
    ) = uploadMedia(accessToken, contentId, file.asSource())

    private companion object {
        /** Enough to recognise a payload that is already encrypted. */
        const val HEADER_PEEK_BYTES = 5L
    }
}
