package app.logdate.server.sync

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import io.github.aakira.napier.Napier
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Google Cloud Storage implementation for media file storage.
 * Handles upload, download, and signed URL generation for media files.
 */
class GcsMediaStorage(
    private val bucketName: String,
    projectId: String? = null
) {
    private val storage: Storage = StorageOptions.newBuilder()
        .apply { projectId?.let { setProjectId(it) } }
        .build()
        .service

    /**
     * Upload media file to GCS.
     * @param userId The owner's user ID
     * @param mediaId Unique identifier for this media file
     * @param fileName Original filename
     * @param mimeType Content type (e.g., "image/jpeg")
     * @param data File contents as ByteArray
     * @return The storage path for database storage (users/{userId}/media/{mediaId}/{filename})
     */
    fun uploadMedia(
        userId: UUID,
        mediaId: UUID,
        fileName: String,
        mimeType: String,
        data: ByteArray
    ): String {
        val storagePath = buildStoragePath(userId, mediaId, fileName)
        val blobId = BlobId.of(bucketName, storagePath)
        val blobInfo = BlobInfo.newBuilder(blobId)
            .setContentType(mimeType)
            .build()

        try {
            storage.create(blobInfo, data)
            Napier.d("Uploaded media to GCS: $storagePath (${data.size} bytes)")
            return storagePath
        } catch (e: Exception) {
            Napier.e("Failed to upload media to GCS: $storagePath", e)
            throw MediaStorageException("Failed to upload media", e)
        }
    }

    /**
     * Generate a signed URL for media download.
     * @param storagePath The GCS storage path
     * @param expirationHours How long the URL should be valid (default: 1 hour)
     * @return Signed URL for downloading the media file
     */
    fun getSignedDownloadUrl(storagePath: String, expirationHours: Long = 1): String {
        val blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, storagePath)).build()
        return try {
            val url: URL = storage.signUrl(
                blobInfo,
                expirationHours,
                TimeUnit.HOURS,
                Storage.SignUrlOption.withV4Signature()
            )
            url.toString()
        } catch (e: Exception) {
            Napier.e("Failed to generate signed URL for: $storagePath", e)
            throw MediaStorageException("Failed to generate download URL", e)
        }
    }

    /**
     * Download media file bytes from GCS.
     * @param storagePath The GCS storage path
     * @return File contents as ByteArray, or null if not found
     */
    fun downloadMedia(storagePath: String): ByteArray? {
        val blobId = BlobId.of(bucketName, storagePath)
        return try {
            storage.readAllBytes(blobId)
        } catch (e: Exception) {
            Napier.e("Failed to download media from GCS: $storagePath", e)
            null
        }
    }

    /**
     * Delete media from GCS.
     * @param storagePath The GCS storage path
     * @return true if deleted successfully, false otherwise
     */
    fun deleteMedia(storagePath: String): Boolean {
        val blobId = BlobId.of(bucketName, storagePath)
        return try {
            val deleted = storage.delete(blobId)
            if (deleted) {
                Napier.d("Deleted media from GCS: $storagePath")
            } else {
                Napier.w("Media not found in GCS: $storagePath")
            }
            deleted
        } catch (e: Exception) {
            Napier.e("Failed to delete media from GCS: $storagePath", e)
            false
        }
    }

    /**
     * Check if media exists in GCS.
     * @param storagePath The GCS storage path
     * @return true if the file exists
     */
    fun exists(storagePath: String): Boolean {
        val blobId = BlobId.of(bucketName, storagePath)
        return try {
            storage.get(blobId) != null
        } catch (e: Exception) {
            false
        }
    }

    private fun buildStoragePath(userId: UUID, mediaId: UUID, fileName: String): String {
        // Sanitize filename to prevent path traversal
        val sanitizedFileName = fileName
            .replace("..", "")
            .replace("/", "_")
            .replace("\\", "_")
        return "users/$userId/media/$mediaId/$sanitizedFileName"
    }

    companion object {
        /**
         * Create GcsMediaStorage from environment variables.
         * @return GcsMediaStorage instance, or null if not configured
         */
        fun fromEnvironment(): GcsMediaStorage? {
            val bucketName = System.getenv("GCS_BUCKET_NAME") ?: return null
            val projectId = System.getenv("GCS_PROJECT_ID")
            return GcsMediaStorage(bucketName, projectId)
        }
    }
}

/**
 * Exception thrown when media storage operations fail.
 */
class MediaStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
