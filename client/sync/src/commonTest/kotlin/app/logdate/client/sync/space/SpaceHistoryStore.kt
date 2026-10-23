package app.logdate.client.sync.space

import app.logdate.client.repository.location.HistoryRecord
import app.logdate.client.repository.location.HistoryRecordStore
import app.logdate.shared.model.location.HistoryPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json

/**
 * Location history records kept in memory with the rules the stored ones have: a deletion stays
 * (a record never comes back after it), and keeps the time its record had.
 */
internal class SpaceHistoryStore(
    private val expectedOwner: String? = null,
) : HistoryRecordStore {
    private fun requireOwner(owner: String) {
        check(expectedOwner == null || owner == expectedOwner) { "Wrong raw history scope" }
    }

    val items = mutableListOf<HistoryRecord>()
    private val json = Json { ignoreUnknownKeys = true }

    override fun observe(
        ownerId: String,
        origin: String,
    ): Flow<List<HistoryRecord>> = MutableStateFlow(items.toList())

    override suspend fun records(
        ownerId: String,
        origin: String,
    ): List<HistoryRecord> {
        requireOwner(ownerId)
        return items.toList()
    }

    override suspend fun record(
        ownerId: String,
        origin: String,
        id: String,
    ): HistoryRecord? {
        requireOwner(ownerId)
        return items.firstOrNull { it.id == id }
    }

    override suspend fun put(
        ownerId: String,
        origin: String,
        record: HistoryRecord,
    ) {
        requireOwner(ownerId)
        val existing = items.firstOrNull { it.id == record.id }
        if (existing?.deleted == true && !record.deleted) return
        val observedAt = payloadTime(record) ?: record.observedAtMillis ?: if (record.deleted) existing?.observedAtMillis else null
        val stored = record.copy(observedAtMillis = observedAt)
        if (existing == null) items += stored else items[items.indexOf(existing)] = stored
    }

    override suspend fun putBatch(
        ownerId: String,
        origin: String,
        records: List<HistoryRecord>,
    ) = records.forEach { put(ownerId, origin, it) }

    override suspend fun pending(
        ownerId: String,
        origin: String,
        limit: Int,
    ): List<HistoryRecord> {
        requireOwner(ownerId)
        return items.filter { it.dirty }.sortedBy { it.id }.take(limit)
    }

    override suspend fun cursor(
        ownerId: String,
        origin: String,
    ): Long = 0

    override suspend fun applyPage(
        ownerId: String,
        origin: String,
        records: List<HistoryRecord>,
        cursor: Long,
    ) = error("Location history from the personal space doesn't use pages")

    override suspend fun acknowledge(
        ownerId: String,
        origin: String,
        id: String,
        deviceVersion: Long,
        serverVersion: Long,
    ) {
        requireOwner(ownerId)
        val existing = items.firstOrNull { it.id == id } ?: return
        items[items.indexOf(existing)] =
            existing.copy(
                dirty = if (existing.deviceVersion == deviceVersion && serverVersion >= existing.serverVersion) false else existing.dirty,
                serverVersion = maxOf(existing.serverVersion, serverVersion),
            )
    }

    private fun payloadTime(record: HistoryRecord): Long? =
        record.payload?.let {
            when (val value = runCatching { json.decodeFromString(HistoryPayload.serializer(), it) }.getOrNull()) {
                is HistoryPayload.Observation -> value.value.timestamp.toEpochMilliseconds()
                is HistoryPayload.Activity -> value.value.timestamp.toEpochMilliseconds()
                else -> null
            }
        }
}
