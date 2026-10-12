package app.logdate.client.data.moments

import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.SyncableMarkedMomentRepository
import app.logdate.client.repository.moments.atMillisecondPrecision
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.conflict.MarkedMomentConflictResolver
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Saves marks and their local outbox mutations together, and merges verified remote projections. */
class OfflineFirstMarkedMomentRepository(
    private val dao: MarkedMomentDao,
    private val transactions: SyncTransactionManager,
    private val metadata: SyncMetadataService,
) : SyncableMarkedMomentRepository {
    override suspend fun dismiss(
        id: Uuid,
        updatedAt: Instant,
    ): Boolean = mutate(id, updatedAt) { if (it.state == MarkState.OPEN) it.copy(state = MarkState.DISMISSED) else it }

    override suspend fun fill(
        id: Uuid,
        caption: String,
        noteIds: Set<Uuid>,
        updatedAt: Instant,
    ): Boolean =
        mutate(id, updatedAt) {
            val joined = it.resolvedNoteIds + noteIds
            if (caption.isBlank() &&
                joined.isEmpty()
            ) {
                it
            } else {
                it.copy(state = MarkState.FILLED, caption = caption, resolvedNoteIds = joined)
            }
        }

    override suspend fun delete(
        id: Uuid,
        deletedAt: Instant,
    ): Boolean =
        transactions.withTransaction {
            val existing = dao.getById(id) ?: return@withTransaction false
            if (existing.deletedAt != null || existing.toModel() == null) return@withTransaction false
            val updated = maxOf(deletedAt, existing.lastUpdated + 1.milliseconds)
            dao.upsert(existing.copy(deletedAt = updated, lastUpdated = updated))
            metadata.enqueuePending(id.toString(), EntityType.MOMENT, PendingOperation.DELETE)
            true
        }

    override suspend fun applyDeletionFromSync(
        id: Uuid,
        deletedAt: Instant,
        serverVersion: Long,
    ) {
        require(serverVersion > 0) { "Remote deletion requires a server version" }
        transactions.withTransaction {
            val existing = dao.getById(id)
            if (existing != null && existing.syncVersion > serverVersion) return@withTransaction
            // A deletion can arrive before its capture. These required capture fields are never
            // exposed: deletedAt gates all reads and prevents later live delivery from reviving it.
            val tombstone =
                existing ?: MarkedMomentEntity(
                    id = id,
                    capturedAt = deletedAt,
                    timeZoneId = "",
                    state = MarkedMomentState.OPEN,
                    originDeviceId = "",
                    lastUpdated = deletedAt,
                )
            val row =
                tombstone.copy(
                    deletedAt = maxOf(tombstone.deletedAt ?: deletedAt, deletedAt),
                    lastUpdated = maxOf(tombstone.lastUpdated, deletedAt),
                    syncVersion = serverVersion,
                )
            if (row != existing) dao.upsert(row)
        }
    }

    private suspend fun mutate(
        id: Uuid,
        updatedAt: Instant,
        change: (MarkedMoment) -> MarkedMoment,
    ): Boolean =
        transactions.withTransaction {
            val row = dao.getById(id)?.takeIf { it.deletedAt == null } ?: return@withTransaction false
            val existing = row.toModel() ?: return@withTransaction false
            val changed = change(existing)
            if (changed == existing) return@withTransaction false
            val updated = changed.copy(lastUpdated = maxOf(updatedAt, existing.lastUpdated + 1.milliseconds))
            dao.upsert(updated.toEntity())
            metadata.enqueuePending(id.toString(), EntityType.MOMENT, PendingOperation.UPDATE)
            true
        }

    override suspend fun applyFromSync(moment: MarkedMoment): MarkedMoment? =
        transactions.withTransaction {
            val projection = moment.atMillisecondPrecision()
            val existing = dao.getById(moment.id)
            if (existing?.deletedAt != null) return@withTransaction null
            val local = existing?.toModel()
            if (existing != null && local == null) return@withTransaction null
            val merged =
                try {
                    if (local == null) projection else MarkedMomentConflictResolver().merge(local, projection)
                } catch (e: IllegalArgumentException) {
                    Napier.w("A remote marked moment disagrees with its stored capture identity", e)
                    return@withTransaction null
                }
            val row = merged.toEntity()
            if (row != existing) dao.upsert(row)
            merged
        }

    override fun observeOpenMarks(): Flow<List<MarkedMoment>> =
        dao.observeByState(MarkedMomentState.OPEN).map { rows -> rows.mapNotNull { it.toModel() } }

    override suspend fun getMark(id: Uuid): MarkedMoment? = dao.getById(id)?.takeIf { it.deletedAt == null }?.toModel()

    override suspend fun fillLocationIfMissing(
        id: Uuid,
        location: MarkLocation,
        updatedAt: Instant,
    ): Boolean = mutate(id, updatedAt) { if (it.location == null) it.copy(location = location) else it }
}

/** The mark this row stores, or null for a state a newer app version wrote, which this one cannot show. */
internal fun MarkedMomentEntity.toModel(): MarkedMoment? {
    val markState = MarkState.entries.firstOrNull { it.name == state }
    if (markState == null) {
        Napier.w("Mark $id is in a state this version does not know; it is left out until the app updates")
        return null
    }
    return MarkedMoment(
        id = id,
        capturedAt = capturedAt,
        timeZoneId = timeZoneId,
        location = locationOrNull(),
        heartRateBpm = heartRateBpm,
        state = markState,
        caption = caption,
        resolvedNoteIds = resolvedNoteIds.split(',').mapNotNull { Uuid.parseOrNull(it.trim()) }.toSet(),
        originDeviceId = originDeviceId,
        lastUpdated = lastUpdated,
        syncVersion = syncVersion,
    ).atMillisecondPrecision()
}

private fun MarkedMomentEntity.locationOrNull(): MarkLocation? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    return MarkLocation(latitude = lat, longitude = lon, accuracyMeters = locationAccuracy)
}

private fun MarkedMoment.toEntity(): MarkedMomentEntity =
    MarkedMomentEntity(
        id = id,
        capturedAt = capturedAt,
        timeZoneId = timeZoneId,
        latitude = location?.latitude,
        longitude = location?.longitude,
        locationAccuracy = location?.accuracyMeters,
        heartRateBpm = heartRateBpm,
        state = state.name,
        caption = caption,
        resolvedNoteIds = resolvedNoteIds.map { it.toString() }.sorted().joinToString(","),
        originDeviceId = originDeviceId,
        lastUpdated = lastUpdated,
        syncVersion = syncVersion,
    )
