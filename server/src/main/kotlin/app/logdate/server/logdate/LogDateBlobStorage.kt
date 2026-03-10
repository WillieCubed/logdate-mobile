package app.logdate.server.logdate

import java.util.UUID

/**
 * LogDate-owned blob namespace for binary objects.
 *
 * `ATPROTO` is reserved for the future blob semantics work that still needs to be layered onto
 * the current first-party sync media and backup objects.
 */
enum class LogDateBlobNamespace {
    MEDIA,
    BACKUP,
    ATPROTO,
}

/**
 * Generic blob write request.
 */
data class LogDateBlobWriteRequest(
    val ownerId: UUID,
    val namespace: LogDateBlobNamespace,
    val blobId: String,
    val fileName: String? = null,
    val contentType: String,
    val bytes: ByteArray,
)

/**
 * LogDate-owned binary object storage boundary for media, encrypted backups, and future ATProto
 * blob semantics.
 *
 * The current production implementation is still GCS-backed, but the interface is now generic so
 * route code is no longer coupled to media-specific upload methods.
 */
interface LogDateBlobStorage {
    fun putBlob(request: LogDateBlobWriteRequest): String

    fun getBlob(storagePath: String): ByteArray?

    fun deleteBlob(storagePath: String): Boolean

    fun getSignedDownloadUrl(
        storagePath: String,
        expirationHours: Long = 1,
    ): String

    fun uploadMedia(
        userId: UUID,
        mediaId: UUID,
        fileName: String,
        mimeType: String,
        data: ByteArray,
    ): String =
        putBlob(
            LogDateBlobWriteRequest(
                ownerId = userId,
                namespace = LogDateBlobNamespace.MEDIA,
                blobId = mediaId.toString(),
                fileName = fileName,
                contentType = mimeType,
                bytes = data,
            ),
        )

    fun uploadBackup(
        userId: UUID,
        backupId: UUID,
        data: ByteArray,
    ): String =
        putBlob(
            LogDateBlobWriteRequest(
                ownerId = userId,
                namespace = LogDateBlobNamespace.BACKUP,
                blobId = backupId.toString(),
                fileName = null,
                contentType = "application/octet-stream",
                bytes = data,
            ),
        )

    fun downloadMedia(storagePath: String): ByteArray? = getBlob(storagePath)

    fun deleteMedia(storagePath: String): Boolean = deleteBlob(storagePath)
}
