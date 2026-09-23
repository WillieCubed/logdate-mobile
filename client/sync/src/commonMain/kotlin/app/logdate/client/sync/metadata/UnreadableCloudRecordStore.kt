package app.logdate.client.sync.metadata

import app.logdate.client.datastore.KeyValueStorage
import kotlin.uuid.Uuid

/**
 * Records on the server this device cannot read, and has no local copy to repair from -- see
 * [app.logdate.client.sync.SyncDownloadEngine.repairUnreadable]. Nothing here can fix them: the
 * device that wrote them, encrypted under a key this device does not have, would need to come
 * back to re-encrypt them, or the user recovers the right identity here and downloads them fresh.
 * Tracked so Backup status can say a plain count instead of silently doing nothing about entries
 * that will never sync to this device.
 */
interface UnreadableCloudRecordStore {
    suspend fun record(
        entityType: EntityType,
        ids: List<Uuid>,
    )

    suspend fun count(): Int

    /** Forgets every recorded id. Call after recovering a different identity, since a fresh
     * download with the right key may now read records this device previously could not. */
    suspend fun clear()
}

class InMemoryUnreadableCloudRecordStore : UnreadableCloudRecordStore {
    private val ids = mutableSetOf<String>()

    override suspend fun record(
        entityType: EntityType,
        ids: List<Uuid>,
    ) {
        this.ids += ids.map { key(entityType, it) }
    }

    override suspend fun count(): Int = ids.size

    override suspend fun clear() {
        ids.clear()
    }
}

class KeyValueUnreadableCloudRecordStore(
    private val storage: KeyValueStorage,
) : UnreadableCloudRecordStore {
    override suspend fun record(
        entityType: EntityType,
        ids: List<Uuid>,
    ) {
        val updated = current() + ids.map { key(entityType, it) }
        storage.putString(KEY, updated.joinToString(","))
    }

    override suspend fun count(): Int = current().size

    override suspend fun clear() {
        storage.remove(KEY)
    }

    private suspend fun current(): Set<String> =
        storage
            .getString(KEY)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet() ?: emptySet()

    private companion object {
        const val KEY = "sync_unreadable_cloud_only_ids"
    }
}

private fun key(
    entityType: EntityType,
    id: Uuid,
) = "${entityType.name}:$id"
