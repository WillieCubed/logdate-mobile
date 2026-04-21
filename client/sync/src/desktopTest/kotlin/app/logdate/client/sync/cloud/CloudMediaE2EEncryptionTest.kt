package app.logdate.client.sync.cloud

import app.logdate.client.sync.crypto.AesGcmMediaPayloadCrypto
import app.logdate.client.sync.crypto.CLIENT_MEDIA_PREFIX_BYTES
import app.logdate.client.sync.test.FakeCloudApiClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * End-to-end encryption tests for cloud media storage.
 *
 * Verifies that media files are correctly encrypted before being uploaded to
 * the cloud and successfully decrypted after being downloaded, ensuring data
 * privacy. It also checks that tampering with encrypted data leads to
 * decryption failure.
 */
class CloudMediaE2EEncryptionTest {
    @Test
    fun `media upload encrypts and download decrypts`() =
        runTest {
            val key = ByteArray(32) { index -> (index + 1).toByte() }
            val crypto = AesGcmMediaPayloadCrypto(key)
            val apiClient = RecordingCloudApiClient()
            val dataSource = DefaultCloudMediaDataSource(apiClient, crypto)
            val plaintext = byteArrayOf(1, 2, 3, 4, 5)
            val media =
                MediaFile(
                    contentId = Uuid.random(),
                    fileName = "secret.bin",
                    mimeType = "application/octet-stream",
                    sizeBytes = plaintext.size.toLong(),
                    data = plaintext,
                )

            val upload = dataSource.uploadMedia("token", media).getOrElse { throw it }
            val uploaded = apiClient.lastUpload ?: fail("Upload request not captured")
            assertFalse(uploaded.data.contentEquals(plaintext))
            val prefix = uploaded.data.copyOfRange(0, CLIENT_MEDIA_PREFIX_BYTES.size)
            assertTrue(prefix.contentEquals(CLIENT_MEDIA_PREFIX_BYTES))

            val download = dataSource.downloadMedia("token", upload.mediaId).getOrElse { throw it }
            assertTrue(download.data.contentEquals(plaintext))
        }

    @Test
    fun `media download fails when ciphertext is tampered`() =
        runTest {
            val key = ByteArray(32) { index -> (index + 2).toByte() }
            val crypto = AesGcmMediaPayloadCrypto(key)
            val apiClient = RecordingCloudApiClient()
            val dataSource = DefaultCloudMediaDataSource(apiClient, crypto)
            val plaintext = byteArrayOf(9, 8, 7, 6)
            val media =
                MediaFile(
                    contentId = Uuid.random(),
                    fileName = "secret.bin",
                    mimeType = "application/octet-stream",
                    sizeBytes = plaintext.size.toLong(),
                    data = plaintext,
                )

            val upload = dataSource.uploadMedia("token", media).getOrElse { throw it }
            apiClient.tamperStoredData()

            val result = dataSource.downloadMedia("token", upload.mediaId)
            assertTrue(result.isFailure)
        }
}

/**
 * A [CloudApiClient] that records media uploads and provides them for download verification.
 */
private class RecordingCloudApiClient : FakeCloudApiClient() {
    var lastUpload: MediaUploadRequest? = null
        private set
    private var storedData: ByteArray = ByteArray(0)
    private var storedMeta: MediaUploadRequest? = null

    override suspend fun uploadMedia(
        accessToken: String,
        media: MediaUploadRequest,
    ): Result<MediaUploadResponse> {
        lastUpload = media
        storedMeta = media
        storedData = media.data
        return Result.success(
            MediaUploadResponse(
                contentId = media.contentId,
                mediaId = "media-${Clock.System.now().toEpochMilliseconds()}",
                downloadUrl = "https://example.com/media",
                uploadedAt = Clock.System.now().toEpochMilliseconds(),
            ),
        )
    }

    override suspend fun downloadMedia(
        accessToken: String,
        mediaId: String,
    ): Result<MediaDownloadResponse> {
        val meta = storedMeta ?: return Result.failure(IllegalStateException("No media uploaded"))
        return Result.success(
            MediaDownloadResponse(
                contentId = meta.contentId,
                fileName = meta.fileName,
                mimeType = meta.mimeType,
                sizeBytes = storedData.size.toLong(),
                data = storedData,
                downloadUrl = "https://example.com/media/$mediaId",
            ),
        )
    }

    /**
     * Tampers with the stored encrypted data to simulate data corruption or unauthorized modification.
     */
    fun tamperStoredData() {
        if (storedData.isNotEmpty()) {
            storedData[storedData.lastIndex] = (storedData.last().toInt() xor 0xFF).toByte()
        }
    }
}
