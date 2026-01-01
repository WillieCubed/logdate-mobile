package app.logdate.client.database.entities.sync

import androidx.room.Entity

/**
 * Tracks entities that have been modified locally and need to be uploaded.
 * Acts as an outbox for the sync system.
 */
@Entity(
    tableName = "pending_uploads",
    primaryKeys = ["entityType", "entityId"]
)
data class PendingUploadEntity(
    val entityType: String,
    val entityId: String,
    val operation: String,
    val createdAt: Long,
    val retryCount: Int = 0
)

/**
 * Operations that can be pending for sync.
 */
object PendingOperation {
    const val CREATE = "CREATE"
    const val UPDATE = "UPDATE"
    const val DELETE = "DELETE"
}
