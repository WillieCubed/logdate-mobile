package app.logdate.client.domain.reminders

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.datalayer.v2.WearJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

/** The latest known reminder on this device, including its verified peer's posts. */
class ReminderPostedHistory(
    private val storage: KeyValueStorage,
    private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()

    fun observe(): Flow<Instant?> =
        storage
            .observeString(STORAGE_KEY)
            .map { decode(it).lastAt?.let(Instant::fromEpochMilliseconds) }
            .distinctUntilChanged()

    suspend fun latest(): Instant? = decode(storage.getString(STORAGE_KEY)).lastAt?.let(Instant::fromEpochMilliseconds)

    suspend fun recordPostedAt(at: Instant) = merge("local", at.toEpochMilliseconds())

    suspend fun adoptPeer(
        nodeId: String,
        at: Long,
    ) {
        if (nodeId.isBlank()) return
        merge("peer:$nodeId", at)
    }

    private suspend fun merge(
        source: String,
        at: Long,
    ) = mutex.withLock {
        if (at <= 0) return@withLock
        val current = decode(storage.getString(STORAGE_KEY))
        if (at <= (current.sources[source] ?: 0)) return@withLock
        val observed = minOf(at, clock.now().toEpochMilliseconds())
        val updated = current.copy(lastAt = maxOf(current.lastAt ?: observed, observed), sources = current.sources + (source to at))
        storage.putString(STORAGE_KEY, WearJson.encode(Snapshot.serializer(), updated))
    }

    private fun decode(text: String?): Snapshot {
        if (text == null) return Snapshot()
        val snapshot =
            try {
                WearJson.json.decodeFromString(Snapshot.serializer(), text)
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException("Stored reminder history could not be read", e)
            }
        check(snapshot.schema == 1 && (snapshot.lastAt == null || snapshot.lastAt > 0) && snapshot.sources.values.all { it > 0 }) {
            "Stored reminder history has an unsupported schema or invalid time"
        }
        return snapshot
    }

    @Serializable
    private data class Snapshot(
        val schema: Int = 1,
        val lastAt: Long? = null,
        val sources: Map<String, Long> = emptyMap(),
    )

    companion object {
        const val STORAGE_KEY = "moment_reminder_posted_history_v1"
    }
}
