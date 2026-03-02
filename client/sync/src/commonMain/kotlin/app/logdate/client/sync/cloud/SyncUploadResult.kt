package app.logdate.client.sync.cloud

import kotlin.time.Instant

/**
 * Represents a successful upload/update result from the server.
 */
data class SyncUploadResult(
    val serverVersion: Long,
    val syncedAt: Instant,
)
