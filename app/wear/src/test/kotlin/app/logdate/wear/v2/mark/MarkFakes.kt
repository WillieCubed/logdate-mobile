package app.logdate.wear.v2.mark

import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.database.dao.sync.PeerDeliveryDao
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.sync.SyncTransactionManager
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

    override suspend fun liveIds(): List<Uuid> =
        stored.value.values
            .filter { it.deletedAt == null }
            .map { it.id }

    override fun observeDeleted() = stored.map { rows -> rows.values.filter { it.deletedAt != null } }

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

/** [PeerDeliveryDao] in memory, shared with the delivery tests so query contracts stay consistent. */
class FakePeerDeliveryDao :
    PeerDeliveryDao by app.logdate.wear.sync.v2.delivery
        .MemoryPeerDeliveryDao()

object MarkTestTransactions : SyncTransactionManager {
    override suspend fun <T> withTransaction(block: suspend () -> T): T = block()
}
