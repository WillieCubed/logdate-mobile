package app.logdate.client.database.entities.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import kotlin.uuid.Uuid

/**
 * Tracks entities that have been modified locally and need to be uploaded.
 * Acts as an outbox for the sync system.
 */
@Entity(
    tableName = "pending_uploads",
    primaryKeys = ["ownerId", "serverOrigin", "entityType", "entityId"],
)
data class PendingUploadEntity(
    @ColumnInfo(defaultValue = "''")
    val ownerId: String,
    val serverOrigin: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val createdAt: Long,
    val retryCount: Int = 0,
    val expectedServerVersion: Long? = null,
    @ColumnInfo(defaultValue = "''")
    val operationId: String = Uuid.random().toString(),
)

/**
 * Operations that can be pending for sync.
 */
object PendingOperation {
    const val CREATE = "CREATE"
    const val UPDATE = "UPDATE"
    const val DELETE = "DELETE"
}
