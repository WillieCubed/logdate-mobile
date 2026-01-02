package app.logdate.client.sync.metadata

/**
 * Represents a pending sync operation for an entity.
 */
data class PendingUpload(
    val entityId: String,
    val operation: PendingOperation,
    val retryCount: Int = 0
)

/**
 * Sync operations queued in the outbox.
 */
enum class PendingOperation {
    CREATE,
    UPDATE,
    DELETE;

    companion object {
        fun fromStorage(value: String): PendingOperation {
            return values().firstOrNull { it.name == value } ?: UPDATE
        }
    }
}
