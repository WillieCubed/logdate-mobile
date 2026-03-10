package app.logdate.server.logdate

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory blob storage implementation for tests and non-database server runs.
 */
class InMemoryLogDateBlobStorage : LogDateBlobStorage {
    private val blobs = ConcurrentHashMap<String, StoredBlob>()

    override fun putBlob(request: LogDateBlobWriteRequest): String {
        val storagePath = storagePathFor(request)
        blobs[storagePath] =
            StoredBlob(
                bytes = request.bytes,
                contentType = request.contentType,
            )
        return storagePath
    }

    override fun getBlob(storagePath: String): ByteArray? = blobs[storagePath]?.bytes

    override fun deleteBlob(storagePath: String): Boolean = blobs.remove(storagePath) != null

    override fun getSignedDownloadUrl(
        storagePath: String,
        expirationHours: Long,
    ): String = "https://logdate.test/download/$storagePath?expiresInHours=$expirationHours"

    private fun storagePathFor(request: LogDateBlobWriteRequest): String =
        when (request.namespace) {
            LogDateBlobNamespace.MEDIA -> {
                val fileName = requireNotNull(request.fileName) { "Media blobs require a file name" }
                "users/${request.ownerId}/media/${request.blobId}/$fileName"
            }

            LogDateBlobNamespace.BACKUP -> "users/${request.ownerId}/backups/${request.blobId}.enc"
            LogDateBlobNamespace.ATPROTO -> "users/${request.ownerId}/atproto/blobs/${request.blobId}"
        }

    private data class StoredBlob(
        val bytes: ByteArray,
        val contentType: String,
    )
}
