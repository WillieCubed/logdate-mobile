package app.logdate.server.logdate

import java.util.UUID

/**
 * LogDate-owned binary object storage boundary for media and encrypted backups.
 *
 * The current production implementation is GCS-backed, but route code depends on this interface
 * so storage-provider details do not leak through the rest of the server.
 */
interface LogDateBlobStorage {
    fun uploadMedia(
        userId: UUID,
        mediaId: UUID,
        fileName: String,
        mimeType: String,
        data: ByteArray,
    ): String

    fun uploadBackup(
        userId: UUID,
        backupId: UUID,
        data: ByteArray,
    ): String

    fun getSignedDownloadUrl(
        storagePath: String,
        expirationHours: Long = 1,
    ): String

    fun downloadMedia(storagePath: String): ByteArray?

    fun deleteMedia(storagePath: String): Boolean
}
