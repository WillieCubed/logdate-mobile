package app.logdate.client.data.moments

import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.SyncableMarkedMomentRepository
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.conflict.MarkedMomentConflictResolver
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Reads marked moments and transactionally merges verified remote copies into Room. */
class OfflineFirstMarkedMomentRepository(
    private val dao: MarkedMomentDao,
    private val transactions: SyncTransactionManager,
) : SyncableMarkedMomentRepository {
    override suspend fun applyFromSync(moment: MarkedMoment): MarkedMoment? =
        transactions.withTransaction {
            val existing = dao.getById(moment.id)
            if (existing?.deletedAt != null) return@withTransaction null
            val local = existing?.toModel()
            if (existing != null && local == null) return@withTransaction null
            val merged =
                try {
                    if (local == null) moment else MarkedMomentConflictResolver().merge(local, moment)
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
    ): Boolean = dao.fillLocationIfMissing(id, location.latitude, location.longitude, location.accuracyMeters, updatedAt) > 0
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
    )
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
