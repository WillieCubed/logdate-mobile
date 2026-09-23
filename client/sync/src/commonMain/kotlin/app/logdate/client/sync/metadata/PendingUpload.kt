package app.logdate.client.sync.metadata

/**
 * Represents a pending sync operation for an entity.
 */
data class PendingUpload(
    val entityId: String,
    val operation: PendingOperation,
    val retryCount: Int = 0,
)

/**
 * One change waiting in the outbox, as the backup status screen lists it.
 *
 * [entityType] is null for a row whose type this build doesn't know, such as one written by a
 * newer version. It is still waiting and still counted, so it is kept rather than dropped.
 */
data class QueuedUpload(
    val entityType: EntityType?,
    val entityId: String,
    val operation: PendingOperation,
    val retryCount: Int,
)

/**
 * Sync operations queued in the outbox.
 */
enum class PendingOperation {
    CREATE,
    UPDATE,
    DELETE,
    ;

    companion object {
        fun fromStorage(value: String): PendingOperation = values().firstOrNull { it.name == value } ?: UPDATE

        /**
         * Collapse the queued op for an entity given an [existing] outbox state and an [incoming]
         * write. Returns the operation that should be persisted, or `null` to indicate the
         * pending entry should be removed entirely (e.g. CREATE followed by DELETE never needs
         * to round-trip through the server).
         */
        fun coalesce(
            existing: PendingOperation?,
            incoming: PendingOperation,
        ): PendingOperation? =
            when (existing) {
                null -> incoming
                CREATE ->
                    when (incoming) {
                        CREATE, UPDATE -> CREATE
                        DELETE -> null
                    }
                UPDATE ->
                    when (incoming) {
                        CREATE -> CREATE
                        UPDATE -> UPDATE
                        DELETE -> DELETE
                    }
                DELETE ->
                    when (incoming) {
                        CREATE, UPDATE -> CREATE
                        DELETE -> DELETE
                    }
            }
    }
}
