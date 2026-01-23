package app.logdate.client.sync.metadata

import app.logdate.client.datastore.KeyValueStorage

interface SyncRetryScheduleStore {
    suspend fun nextAttemptAt(entityType: EntityType, entityId: String): Long?
    suspend fun setNextAttemptAt(entityType: EntityType, entityId: String, timestamp: Long)
    suspend fun clear(entityType: EntityType, entityId: String)
}

class KeyValueSyncRetryScheduleStore(
    private val storage: KeyValueStorage
) : SyncRetryScheduleStore {

    override suspend fun nextAttemptAt(entityType: EntityType, entityId: String): Long? {
        val key = key(entityType, entityId)
        val value = storage.getLong(key, -1L)
        return value.takeIf { it >= 0L }
    }

    override suspend fun setNextAttemptAt(entityType: EntityType, entityId: String, timestamp: Long) {
        storage.putLong(key(entityType, entityId), timestamp)
    }

    override suspend fun clear(entityType: EntityType, entityId: String) {
        storage.remove(key(entityType, entityId))
    }

    private fun key(entityType: EntityType, entityId: String): String {
        return "sync_retry_${entityType.name}_$entityId"
    }
}
