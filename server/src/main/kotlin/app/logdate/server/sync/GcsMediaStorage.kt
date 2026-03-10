package app.logdate.server.sync

import app.logdate.server.logdate.LogDateBlobNamespace
import app.logdate.server.logdate.LogDateBlobStorage
import app.logdate.server.logdate.LogDateBlobWriteRequest
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import io.github.aakira.napier.Napier
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Google Cloud Storage implementation for media file storage.
 * Handles upload, download, and signed URL generation for media files.
 */
class GcsMediaStorage(
    private val bucketName: String,
    projectId: String? = null,
    private val kmsKeyName: String? = null,
    private val storage: Storage =
        StorageOptions
            .newBuilder()
            .apply { projectId?.let { setProjectId(it) } }
            .build()
            .service,
) : LogDateBlobStorage {
    /**
     * Upload a LogDate blob into the configured GCS bucket.
     */
    override fun putBlob(request: LogDateBlobWriteRequest): String {
        val storagePath = buildStoragePath(request)
        val label = request.namespace.errorLabel()
        val blobId = BlobId.of(bucketName, storagePath)
        val blobInfo =
            BlobInfo
                .newBuilder(blobId)
                .setContentType(request.contentType)
                .build()

        try {
            val options =
                if (kmsKeyName != null) {
                    arrayOf(Storage.BlobTargetOption.kmsKeyName(kmsKeyName))
                } else {
                    emptyArray()
                }
            storage.create(blobInfo, request.bytes, *options)
            Napier.d("Uploaded $label to GCS: $storagePath (${request.bytes.size} bytes)")
            return storagePath
        } catch (e: Exception) {
            Napier.e("Failed to upload $label to GCS: $storagePath", e)
            throw MediaStorageException("Failed to upload $label", e)
        }
    }

    /**
     * Generate a signed URL for media download.
     * @param storagePath The GCS storage path
     * @param expirationHours How long the URL should be valid (default: 1 hour)
     * @return Signed URL for downloading the media file
     */
    override fun getSignedDownloadUrl(
        storagePath: String,
        expirationHours: Long,
    ): String {
        val blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, storagePath)).build()
        return try {
            val url: URL =
                storage.signUrl(
                    blobInfo,
                    expirationHours,
                    TimeUnit.HOURS,
                    Storage.SignUrlOption.withV4Signature(),
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
    override fun getBlob(storagePath: String): ByteArray? {
        val blobId = BlobId.of(bucketName, storagePath)
        return try {
            storage.readAllBytes(blobId)
        } catch (e: Exception) {
            Napier.e("Failed to download blob from GCS: $storagePath", e)
            null
        }
    }

    /**
     * Delete media from GCS.
     * @param storagePath The GCS storage path
     * @return true if deleted successfully, false otherwise
     */
    override fun deleteBlob(storagePath: String): Boolean {
        val blobId = BlobId.of(bucketName, storagePath)
        return try {
            val deleted = storage.delete(blobId)
            if (deleted) {
                Napier.d("Deleted blob from GCS: $storagePath")
            } else {
                Napier.w("Blob not found in GCS: $storagePath")
            }
            deleted
        } catch (e: Exception) {
            Napier.e("Failed to delete blob from GCS: $storagePath", e)
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

    private fun buildStoragePath(request: LogDateBlobWriteRequest): String =
        when (request.namespace) {
            LogDateBlobNamespace.MEDIA -> {
                val fileName = requireNotNull(request.fileName) { "Media blobs require a file name" }
                val sanitizedFileName =
                    fileName
                        .replace("..", "")
                        .replace("/", "_")
                        .replace("\\", "_")
                "users/${request.ownerId}/media/${request.blobId}/$sanitizedFileName"
            }

            LogDateBlobNamespace.BACKUP -> "users/${request.ownerId}/backups/${request.blobId}.enc"

            LogDateBlobNamespace.ATPROTO -> "users/${request.ownerId}/atproto/blobs/${request.blobId}"
        }

    companion object {
        /**
         * Create GcsMediaStorage from environment variables.
         * @return GcsMediaStorage instance, or null if not configured
         */
        fun fromEnvironment(env: (String) -> String? = System::getenv): GcsMediaStorage? {
            val bucketName = env("GCS_BUCKET_NAME") ?: return null
            val projectId = env("GCS_PROJECT_ID")
            val kmsKeyName = env("GCS_MEDIA_KMS_KEY")
            return GcsMediaStorage(bucketName, projectId, kmsKeyName)
        }
    }
}

private fun LogDateBlobNamespace.errorLabel(): String =
    when (this) {
        LogDateBlobNamespace.MEDIA -> "media"
        LogDateBlobNamespace.BACKUP -> "backup"
        LogDateBlobNamespace.ATPROTO -> "blob"
    }

/**
 * Exception thrown when media storage operations fail.
 */
class MediaStorageException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
