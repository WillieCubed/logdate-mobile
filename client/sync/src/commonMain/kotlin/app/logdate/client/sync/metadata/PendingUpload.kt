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
