package app.logdate.client.sync.metadata

import app.logdate.client.datastore.KeyValueStorage

/**
 * Records, per [EntityType], whether the first-sync local-table sweep (queuing everything already
 * on this device the first time it ever talks to a server) has already completed successfully.
 *
 * That sweep used to be gated only on [SyncMetadataService.getLastSyncTime] returning null -- "has
 * the download cursor ever advanced for this type." If downloading keeps failing (a poison page, a
 * network problem, an account issue), the cursor never advances, so that check kept returning true
 * forever and the sweep -- a full local table scan plus a re-enqueue of every journal or note --
 * ran again on every single full sync attempt. This store decouples "have we ever swept" from the
 * download cursor, so the sweep runs at most once ever per entity type regardless of whether
 * downloads ever succeed.
 */
interface FirstSyncEnqueueStore {
    suspend fun hasEnqueued(entityType: EntityType): Boolean

    /** Marks [entityType]'s sweep complete. Call only after it actually finished without error. */
    suspend fun markEnqueued(entityType: EntityType)
}

class InMemoryFirstSyncEnqueueStore : FirstSyncEnqueueStore {
    private val enqueued = mutableSetOf<EntityType>()

    override suspend fun hasEnqueued(entityType: EntityType): Boolean = entityType in enqueued

    override suspend fun markEnqueued(entityType: EntityType) {
        enqueued += entityType
    }
}

class KeyValueFirstSyncEnqueueStore(
    private val storage: KeyValueStorage,
) : FirstSyncEnqueueStore {
    override suspend fun hasEnqueued(entityType: EntityType): Boolean = storage.getBoolean(key(entityType), false)

    override suspend fun markEnqueued(entityType: EntityType) {
        storage.putBoolean(key(entityType), true)
    }

    private fun key(entityType: EntityType) = "$KEY_PREFIX${entityType.name}"

    private companion object {
        const val KEY_PREFIX = "sync_first_enqueue_done_"
    }
}
