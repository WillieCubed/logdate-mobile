package app.logdate.server.sync

import app.logdate.shared.model.sync.LocationHistoryChangesResponse
import app.logdate.shared.model.sync.LocationHistoryRecord
import app.logdate.shared.model.sync.LocationHistoryUpload
import java.util.UUID

interface LocationHistoryRepository {
    fun upload(
        userId: UUID,
        records: List<LocationHistoryUpload>,
    ): List<LocationHistoryRecord>

    fun changes(
        userId: UUID,
        since: Long,
        limit: Int,
    ): LocationHistoryChangesResponse
}

class LocationHistoryConflictException : IllegalStateException("Location history version conflict")

class InMemoryLocationHistoryRepository : LocationHistoryRepository {
    private val users = mutableMapOf<UUID, MutableMap<String, LocationHistoryRecord>>()
    private val versions = mutableMapOf<UUID, Long>()

    @Synchronized
    override fun upload(
        userId: UUID,
        records: List<LocationHistoryUpload>,
    ): List<LocationHistoryRecord> {
        validateLocationHistoryBatch(records)
        val stored = users.getOrPut(userId) { mutableMapOf() }
        records.forEach { validateLocationHistoryVersion(stored[it.id], it) }
        return records.map { upload ->
            val existing = stored[upload.id]
            if (existing != null && existing.matches(upload)) {
                existing
            } else {
                val version = (versions[userId] ?: 0) + 1
                versions[userId] = version
                upload.toRecord(version).also { stored[upload.id] = it }
            }
        }
    }

    @Synchronized
    override fun changes(
        userId: UUID,
        since: Long,
        limit: Int,
    ): LocationHistoryChangesResponse {
        require(since >= 0 && limit in 1..100)
        val records =
            users[userId]
                .orEmpty()
                .values
                .filter { it.serverVersion > since }
                .sortedBy { it.serverVersion }
                .take(limit + 1)
        return locationHistoryPage(records, since, limit)
    }
}

internal fun validateLocationHistoryBatch(records: List<LocationHistoryUpload>) {
    require(records.size in 1..100 && records.map { it.id }.distinct().size == records.size)
    records.forEach {
        require(it.id.isNotBlank() && it.id.length <= 128 && it.deviceId.isNotBlank() && it.deviceId.length <= 128)
        require(it.recordType in setOf("observation", "activity", "place", "correction", "memory-link", "manual"))
        require(it.payloadSchemaVersion > 0 && it.deviceVersion > 0 && it.expectedServerVersion >= 0)
        val payload = it.payload
        require(
            if (it.deleted) {
                payload == null
            } else {
                payload != null && payload.startsWith("LDSE2:") && payload.length in 7..65536
            },
        )
    }
}

internal fun validateLocationHistoryVersion(
    existing: LocationHistoryRecord?,
    upload: LocationHistoryUpload,
) {
    if (existing != null && existing.matches(upload)) return
    if (existing?.deleted == true ||
        upload.expectedServerVersion != (existing?.serverVersion ?: 0L) ||
        (existing != null && existing.deviceId == upload.deviceId && upload.deviceVersion <= existing.deviceVersion)
    ) {
        throw LocationHistoryConflictException()
    }
}

/** Device versions identify immutable edits; re-encryption may use a fresh nonce on retry. */
internal fun LocationHistoryRecord.matches(upload: LocationHistoryUpload): Boolean =
    id == upload.id &&
        recordType == upload.recordType &&
        payloadSchemaVersion == upload.payloadSchemaVersion &&
        deviceId == upload.deviceId &&
        deviceVersion == upload.deviceVersion &&
        deleted == upload.deleted

internal fun LocationHistoryUpload.toRecord(version: Long) =
    LocationHistoryRecord(
        id,
        recordType,
        payload,
        payloadSchemaVersion,
        deviceId,
        deviceVersion,
        version,
        deleted,
    )

internal fun locationHistoryPage(
    records: List<LocationHistoryRecord>,
    since: Long,
    limit: Int,
): LocationHistoryChangesResponse {
    val page = records.take(limit)
    return LocationHistoryChangesResponse(page, page.lastOrNull()?.serverVersion ?: since, records.size > limit)
}
