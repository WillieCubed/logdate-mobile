package app.logdate.client.sync.metadata

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.github.aakira.napier.Napier
import app.logdate.client.datastore.KeyValueStorage

@Serializable
data class SyncDeadLetterRecord(
    val id: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val retryCount: Int,
    val lastError: String,
    val failedAt: Long
)

interface SyncDeadLetterStore {
    suspend fun list(): List<SyncDeadLetterRecord>
    suspend fun add(record: SyncDeadLetterRecord)
    suspend fun remove(id: String)
    suspend fun clear()
}

class KeyValueSyncDeadLetterStore(
    private val storage: KeyValueStorage,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : SyncDeadLetterStore {

    override suspend fun list(): List<SyncDeadLetterRecord> = readAll()

    override suspend fun add(record: SyncDeadLetterRecord) {
        val updated = readAll().filterNot { it.id == record.id } + record
        storage.putString(DEAD_LETTER_KEY, json.encodeToString(updated))
    }

    override suspend fun remove(id: String) {
        val updated = readAll().filterNot { it.id == id }
        storage.putString(DEAD_LETTER_KEY, json.encodeToString(updated))
    }

    override suspend fun clear() {
        storage.remove(DEAD_LETTER_KEY)
    }

    private suspend fun readAll(): List<SyncDeadLetterRecord> {
        val raw = storage.getString(DEAD_LETTER_KEY) ?: return emptyList()
        return runCatching { json.decodeFromString<List<SyncDeadLetterRecord>>(raw) }
            .onFailure { Napier.w("Failed to decode sync dead-letter store", it) }
            .getOrElse { emptyList() }
    }

    private companion object {
        const val DEAD_LETTER_KEY = "sync_dead_letter_queue"
    }
}
