package app.logdate.wear.v2.mark

import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.database.dao.sync.PeerDeliveryDao
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.sync.PeerDeliveryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** [MarkedMomentDao] in memory. Set [failure] to make the next writes throw. */
class FakeMarkedMomentDao : MarkedMomentDao {
    private val stored = MutableStateFlow<Map<Uuid, MarkedMomentEntity>>(emptyMap())
    var failure: Exception? = null

    val rows: Map<Uuid, MarkedMomentEntity> get() = stored.value

    override suspend fun upsert(moment: MarkedMomentEntity) {
        failure?.let { throw it }
        stored.value = stored.value + (moment.id to moment)
    }

    override suspend fun getById(id: Uuid): MarkedMomentEntity? = stored.value[id]

    override fun observeByState(state: String): Flow<List<MarkedMomentEntity>> =
        stored.map { rows -> rows.values.filter { it.state == state && it.deletedAt == null }.sortedByDescending { it.capturedAt } }

    override suspend fun fillLocationIfMissing(
        id: Uuid,
        latitude: Double,
        longitude: Double,
        accuracy: Float?,
        lastUpdated: Instant,
    ): Int {
        val row = stored.value[id] ?: return 0
        if (row.latitude != null || row.deletedAt != null) return 0
        stored.value = stored.value +
            (id to row.copy(latitude = latitude, longitude = longitude, locationAccuracy = accuracy, lastUpdated = lastUpdated))
        return 1
    }
}

/** [PeerDeliveryDao] in memory. */
class FakePeerDeliveryDao : PeerDeliveryDao {
    private val rows = mutableMapOf<List<String>, PeerDeliveryEntity>()

    override suspend fun upsert(entry: PeerDeliveryEntity) {
        rows[listOf(entry.peerScope, entry.direction, entry.entityType, entry.entityId)] = entry
    }

    override suspend fun get(
        peerScope: String,
        direction: String,
        entityType: String,
        entityId: String,
    ): PeerDeliveryEntity? = rows[listOf(peerScope, direction, entityType, entityId)]

    override suspend fun due(
        peerScope: String,
        direction: String,
        now: Long,
        limit: Int,
    ): List<PeerDeliveryEntity> =
        rows.values
            .filter { it.peerScope == peerScope && it.direction == direction && it.nextAttemptAt?.let { next -> next <= now } == true }
            .sortedBy { it.nextAttemptAt }
            .take(limit)

    override suspend fun delete(
        peerScope: String,
        direction: String,
        entityType: String,
        entityId: String,
    ) {
        rows.remove(listOf(peerScope, direction, entityType, entityId))
    }
}
