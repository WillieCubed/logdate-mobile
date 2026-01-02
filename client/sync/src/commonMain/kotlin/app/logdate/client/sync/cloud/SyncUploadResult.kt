package app.logdate.client.sync.cloud

import kotlinx.datetime.Instant

/**
 * Represents a successful upload/update result from the server.
 */
data class SyncUploadResult(
    val serverVersion: Long,
    val syncedAt: Instant
)
