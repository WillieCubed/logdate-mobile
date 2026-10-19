package app.logdate.client.sync.space.mappers

import app.logdate.client.repository.location.HistoryRecord
import app.logdate.client.repository.location.HistoryRecordStore
import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.shared.model.location.HistoryPayload
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The account and server whose location history this device is syncing. */
data class HistoryScope(
    val owner: String,
    val origin: String,
)

/** Which account and server the location history on this device belongs to right now; null while no account is signed in. */
fun interface HistoryScopeProvider {
    fun current(): HistoryScope?
}

/**
 * Reads and writes the location history records the personal-space kinds sync, for the current
 * account and server.
 */
internal class HistoryStoreAccess(
    val store: HistoryRecordStore,
    private val scopes: HistoryScopeProvider,
    private val deviceId: () -> String?,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The scope the records belong to, or a failure while no account is signed in. */
    fun scope(): HistoryScope = scopes.current() ?: throw IllegalStateException("No account is signed in to own location history")

    /** The id of this device. */
    fun device(): String = deviceId() ?: throw IllegalStateException("This device has no id yet")

    suspend fun record(id: String): HistoryRecord? = scope().let { store.record(it.owner, it.origin, id) }

    suspend fun ofType(recordType: String): List<HistoryRecord> = scope().let { store.recordsOfType(it.owner, it.origin, recordType) }

    suspend fun put(vararg records: HistoryRecord) {
        val scope = scope()
        store.putBatch(scope.owner, scope.origin, records.toList())
    }

    suspend fun acknowledge(
        record: HistoryRecord,
        seq: Long,
    ) {
        val scope = scope()
        store.acknowledge(scope.owner, scope.origin, record.id, record.deviceVersion, maxOf(seq, record.serverVersion))
    }

    /** What [record] holds, or a refusal naming the record and not its content. */
    fun payloadOf(record: HistoryRecord): HistoryPayload? {
        val text = record.payload ?: return null
        return try {
            json.decodeFromString(HistoryPayload.serializer(), text)
        } catch (error: SerializationException) {
            throw SpaceRecordMappingException("The ${record.recordType} record ${record.id} on this device can't be read", error)
        } catch (error: IllegalArgumentException) {
            throw SpaceRecordMappingException("The ${record.recordType} record ${record.id} on this device can't be read", error)
        }
    }

    fun encode(payload: HistoryPayload): String = json.encodeToString(HistoryPayload.serializer(), payload)
}
