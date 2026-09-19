package app.logdate.client.sync.metadata

import app.logdate.client.datastore.KeyValueStorage

interface SyncRetryScheduleStore {
    suspend fun nextAttemptAt(
        entityType: EntityType,
        entityId: String,
    ): Long?

    suspend fun setNextAttemptAt(
        entityType: EntityType,
        entityId: String,
        timestamp: Long,
    )

    /** Forgets the entity's retry schedule and any attempt still recorded as started. */
    suspend fun clear(
        entityType: EntityType,
        entityId: String,
    )

    /**
     * Records that an upload attempt at this entity is starting, and returns how many earlier
     * attempts started and never finished. Stored durably, so an attempt that ends with the
     * process dying is still counted when the app next runs.
     */
    suspend fun beginAttempt(
        entityType: EntityType,
        entityId: String,
    ): Int

    /** Records that the attempt started by [beginAttempt] finished, whatever its outcome. */
    suspend fun endAttempt(
        entityType: EntityType,
        entityId: String,
    )
}

class KeyValueSyncRetryScheduleStore(
    private val storage: KeyValueStorage,
) : SyncRetryScheduleStore {
    override suspend fun nextAttemptAt(
        entityType: EntityType,
        entityId: String,
    ): Long? {
        val key = key(entityType, entityId)
        val value = storage.getLong(key, -1L)
        return value.takeIf { it >= 0L }
    }

    override suspend fun setNextAttemptAt(
        entityType: EntityType,
        entityId: String,
        timestamp: Long,
    ) {
        storage.putLong(key(entityType, entityId), timestamp)
    }

    override suspend fun clear(
        entityType: EntityType,
        entityId: String,
    ) {
        storage.remove(key(entityType, entityId))
        storage.remove(inFlightKey(entityType, entityId))
    }

    override suspend fun beginAttempt(
        entityType: EntityType,
        entityId: String,
    ): Int {
        val key = inFlightKey(entityType, entityId)
        val unfinished = storage.getLong(key, 0L).toInt()
        storage.putLong(key, unfinished + 1L)
        return unfinished
    }

    override suspend fun endAttempt(
        entityType: EntityType,
        entityId: String,
    ) {
        storage.remove(inFlightKey(entityType, entityId))
    }

    private fun key(
        entityType: EntityType,
        entityId: String,
    ): String = "sync_retry_${entityType.name}_$entityId"

    private fun inFlightKey(
        entityType: EntityType,
        entityId: String,
    ): String = "sync_inflight_${entityType.name}_$entityId"
}
