package app.logdate.client.sync.cloud

import kotlinx.datetime.Instant
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
    suspend fun uploadMedia(accessToken: String, media: MediaFile): Result<MediaUploadResult>
    
    /**
     * Downloads a media file from the cloud.
     */
    suspend fun downloadMedia(accessToken: String, mediaId: String): Result<MediaFile>
}

/**
 * Represents a media file for sync operations.
 */
data class MediaFile(
    val contentId: Uuid,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val data: ByteArray
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
    val uploadedAt: Instant
)

/**
 * Default implementation of CloudMediaDataSource using the CloudApiClient.
 */
class DefaultCloudMediaDataSource(
    private val cloudApiClient: CloudApiClient
) : CloudMediaDataSource {
    
    override suspend fun uploadMedia(accessToken: String, media: MediaFile): Result<MediaUploadResult> {
        val request = MediaUploadRequest(
            contentId = media.contentId.toString(),
            fileName = media.fileName,
            mimeType = media.mimeType,
            sizeBytes = media.sizeBytes,
            data = media.data
        )
        
        return cloudApiClient.uploadMedia(accessToken, request).map { response ->
            MediaUploadResult(
                mediaId = response.mediaId,
                downloadUrl = response.downloadUrl,
                uploadedAt = Instant.fromEpochMilliseconds(response.uploadedAt)
            )
        }
    }
    
    override suspend fun downloadMedia(accessToken: String, mediaId: String): Result<MediaFile> {
        return cloudApiClient.downloadMedia(accessToken, mediaId).map { response ->
            MediaFile(
                contentId = Uuid.parse(response.contentId),
                fileName = response.fileName,
                mimeType = response.mimeType,
                sizeBytes = response.sizeBytes,
                data = response.data
            )
        }
    }
}