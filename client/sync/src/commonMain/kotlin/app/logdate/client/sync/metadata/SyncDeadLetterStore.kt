package app.logdate.client.sync.metadata

import app.logdate.client.datastore.KeyValueStorage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SyncDeadLetterRecord(
    val id: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val retryCount: Int,
    val lastError: String,
    val failedAt: Long,
)

interface SyncDeadLetterStore {
    fun observe(): Flow<List<SyncDeadLetterRecord>>

    suspend fun list(): List<SyncDeadLetterRecord>

    suspend fun add(record: SyncDeadLetterRecord)

    suspend fun remove(id: String)

    suspend fun clear()
}

class KeyValueSyncDeadLetterStore(
    private val storage: KeyValueStorage,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SyncDeadLetterStore {
    private val mutex = Mutex()
    private val recordsFlow = MutableStateFlow<List<SyncDeadLetterRecord>>(emptyList())
    private var loaded = false

    override fun observe(): Flow<List<SyncDeadLetterRecord>> = recordsFlow.asStateFlow()

    override suspend fun list(): List<SyncDeadLetterRecord> = ensureLoaded()

    override suspend fun add(record: SyncDeadLetterRecord) {
        mutate { current -> current.filterNot { it.id == record.id } + record }
    }

    override suspend fun remove(id: String) {
        mutate { current -> current.filterNot { it.id == id } }
    }

    override suspend fun clear() {
        mutex.withLock {
            storage.remove(DEAD_LETTER_KEY)
            recordsFlow.value = emptyList()
            loaded = true
        }
    }

    private suspend fun mutate(update: (List<SyncDeadLetterRecord>) -> List<SyncDeadLetterRecord>) {
        mutex.withLock {
            val current = if (loaded) recordsFlow.value else readFromStorage().also { loaded = true }
            val next = update(current)
            storage.putString(DEAD_LETTER_KEY, json.encodeToString(next))
            recordsFlow.value = next
        }
    }

    private suspend fun ensureLoaded(): List<SyncDeadLetterRecord> =
        mutex.withLock {
            if (!loaded) {
                recordsFlow.value = readFromStorage()
                loaded = true
            }
            recordsFlow.value
        }

    private suspend fun readFromStorage(): List<SyncDeadLetterRecord> {
        val raw = storage.getString(DEAD_LETTER_KEY) ?: return emptyList()
        return runCatching { json.decodeFromString<List<SyncDeadLetterRecord>>(raw) }
            .onFailure { Napier.w("Failed to decode sync dead-letter store", it) }
            .getOrElse { emptyList() }
    }

    private companion object {
        const val DEAD_LETTER_KEY = "sync_dead_letter_queue"
    }
}
