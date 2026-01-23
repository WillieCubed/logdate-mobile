package app.logdate.client.sync.conflict

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import app.logdate.client.datastore.KeyValueStorage
import io.github.aakira.napier.Napier

@Serializable
data class SyncConflictRecord(
    val id: String,
    val entityType: String,
    val entityId: String,
    val localVersion: Long?,
    val remoteVersion: Long?,
    val localUpdatedAt: Long?,
    val remoteUpdatedAt: Long?,
    val reason: String,
    val detectedAt: Long
)

interface SyncConflictStore {
    suspend fun list(): List<SyncConflictRecord>
    suspend fun add(record: SyncConflictRecord)
    suspend fun remove(id: String)
    suspend fun clear()
}

class KeyValueSyncConflictStore(
    private val storage: KeyValueStorage,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : SyncConflictStore {

    override suspend fun list(): List<SyncConflictRecord> = readAll()

    override suspend fun add(record: SyncConflictRecord) {
        val updated = readAll().filterNot { it.id == record.id } + record
        storage.putString(CONFLICT_KEY, json.encodeToString(updated))
    }

    override suspend fun remove(id: String) {
        val updated = readAll().filterNot { it.id == id }
        storage.putString(CONFLICT_KEY, json.encodeToString(updated))
    }

    override suspend fun clear() {
        storage.remove(CONFLICT_KEY)
    }

    private suspend fun readAll(): List<SyncConflictRecord> {
        val raw = storage.getString(CONFLICT_KEY) ?: return emptyList()
        return runCatching { json.decodeFromString<List<SyncConflictRecord>>(raw) }
            .onFailure { Napier.w("Failed to decode sync conflicts store", it) }
            .getOrElse { emptyList() }
    }

    private companion object {
        const val CONFLICT_KEY = "sync_conflict_queue"
    }
}
