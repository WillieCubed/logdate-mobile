package app.logdate.client.data.moments

import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.MarkedMomentRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Reads marked moments from Room. Marks are written by sync and the watch ingest, not here. */
class OfflineFirstMarkedMomentRepository(
    private val dao: MarkedMomentDao,
) : MarkedMomentRepository {
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
