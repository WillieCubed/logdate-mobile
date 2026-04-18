package app.logdate.client.sync.cloud

import app.logdate.client.sync.crypto.MediaPayloadCrypto
import app.logdate.client.sync.crypto.NoOpMediaPayloadCrypto
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Data source for syncing media files with LogDate Cloud.
 *
 * Handles uploading and downloading media files (images, videos, audio)
 * associated with journal content.
 */
interface CloudMediaDataSource {
    /**
     * Uploads a media file to the cloud.
     */
    suspend fun uploadMedia(
        accessToken: String,
        media: MediaFile,
    ): Result<MediaUploadResult>

    /**
     * Downloads a media file from the cloud.
     */
    suspend fun downloadMedia(
        accessToken: String,
        mediaId: String,
    ): Result<MediaFile>
}

/**
 * Represents a media file for sync operations.
 */
data class MediaFile(
    val contentId: Uuid,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MediaFile

        if (contentId != other.contentId) return false
        if (fileName != other.fileName) return false
        if (mimeType != other.mimeType) return false
        if (sizeBytes != other.sizeBytes) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = contentId.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * Result of a media upload operation.
 */
data class MediaUploadResult(
    val mediaId: String,
    val downloadUrl: String,
    val uploadedAt: Instant,
)

/**
 * Default implementation of CloudMediaDataSource using the CloudApiClient.
 *
 * **Encryption:** [mediaPayloadCrypto] defaults to [NoOpMediaPayloadCrypto] only so tests can
 * construct this class without wiring a key source. Production builds receive
 * [StoredMediaPayloadCrypto] via DI (`CloudModule.kt`) and bytes on the wire are always
 * AES-GCM–encrypted client-side before they reach the server.
 */
class DefaultCloudMediaDataSource(
    private val cloudApiClient: CloudApiClient,
    private val mediaPayloadCrypto: MediaPayloadCrypto = NoOpMediaPayloadCrypto,
) : CloudMediaDataSource {
    override suspend fun uploadMedia(
        accessToken: String,
        media: MediaFile,
    ): Result<MediaUploadResult> {
        val encrypted =
            try {
                mediaPayloadCrypto.encrypt(media.data)
            } catch (error: Exception) {
                return Result.failure(error)
            }
        val request =
            MediaUploadRequest(
                contentId = media.contentId.toString(),
                fileName = media.fileName,
                mimeType = media.mimeType,
                sizeBytes = media.sizeBytes,
                data = encrypted,
            )

        return cloudApiClient.uploadMedia(accessToken, request).map { response ->
            MediaUploadResult(
                mediaId = response.mediaId,
                downloadUrl = response.downloadUrl,
                uploadedAt = Instant.fromEpochMilliseconds(response.uploadedAt),
            )
        }
    }

    override suspend fun downloadMedia(
        accessToken: String,
        mediaId: String,
    ): Result<MediaFile> {
        val responseResult = cloudApiClient.downloadMedia(accessToken, mediaId)
        return responseResult.fold(
            onSuccess = { response ->
                try {
                    val decrypted = mediaPayloadCrypto.decrypt(response.data)
                    Result.success(
                        MediaFile(
                            contentId = Uuid.parse(response.contentId),
                            fileName = response.fileName,
                            mimeType = response.mimeType,
                            sizeBytes = response.sizeBytes,
                            data = decrypted,
                        ),
                    )
                } catch (error: Exception) {
                    Result.failure(error)
                }
            },
            onFailure = { error -> Result.failure(error) },
        )
    }
}
