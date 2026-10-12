package app.logdate.wear.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.database.dao.sync.PeerDeliveryDao
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.database.entities.sync.PeerDeliveryEntity
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.wear.di.WatchCloud
import app.logdate.wear.v2.mark.MarkDelivery
import app.logdate.wear.v2.mark.MarkedMomentStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Saves a mark through the real Koin graph and the watch's encrypted database on a Wear OS emulator. */
@RunWith(AndroidJUnit4::class)
class MarkedMomentStoreE2ETest {
    private val koin get() = KoinJavaComponent.getKoin()

    @Test
    fun a_saved_mark_is_in_the_database_the_outbox_and_the_ledger() =
        runBlocking {
            // The database stores milliseconds, so the mark is made at a whole millisecond to compare equal.
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            val mark =
                MarkedMomentEntity(
                    id = Uuid.random(),
                    capturedAt = now,
                    timeZoneId = "UTC",
                    state = MarkedMomentState.OPEN,
                    originDeviceId = "e2e",
                    lastUpdated = now,
                )

            koin.get<MarkedMomentStore>().create(mark)

            assertEquals(mark, koin.get<MarkedMomentDao>().getById(mark.id))
            assertTrue(koin.get<SyncMetadataService>().hasPending(EntityType.MOMENT, mark.id.toString()))
            val delivery =
                koin.get<PeerDeliveryDao>().get(MarkDelivery.PEER_SCOPE, MarkDelivery.DIRECTION, EntityType.MOMENT.name, mark.id.toString())
            assertEquals(MarkDelivery.STATE_WAITING, delivery?.state)
        }

    @Test
    fun interrupted_capture_rolls_back_mark_outbox_and_delivery_together() =
        runBlocking {
            val mark = newMark()
            assertFailsWith<CancellationException> { interruptedStore().create(mark) }
            assertNull(koin.get<MarkedMomentDao>().getById(mark.id))
            assertFalse(koin.get<SyncMetadataService>().hasPending(EntityType.MOMENT, mark.id.toString()))
            assertNull(delivery(mark))
        }

    @Test
    fun interrupted_sensor_update_preserves_resolution_pending_operation_and_delivery() =
        runBlocking {
            val mark = newMark()
            koin.get<MarkedMomentStore>().create(mark)
            val metadata = koin.get<SyncMetadataService>()
            val before = metadata.getPendingUploads(EntityType.MOMENT).single { it.entityId == mark.id.toString() }
            val receipt = delivery(mark)
            val resolved =
                mark.copy(
                    state = MarkedMomentState.FILLED,
                    caption = "Phone resolution",
                    syncVersion = 8,
                    lastUpdated =
                        mark.lastUpdated + 10.seconds,
                )
            koin.get<MarkedMomentDao>().upsert(resolved)
            assertFailsWith<CancellationException> { interruptedStore().update(mark.copy(heartRateBpm = 72)) }
            assertEquals(resolved, koin.get<MarkedMomentDao>().getById(mark.id))
            assertEquals(before, metadata.getPendingUploads(EntityType.MOMENT).single { it.entityId == mark.id.toString() })
            assertEquals(receipt, delivery(mark))
        }

    private fun interruptedStore(): MarkedMomentStore {
        val dao = koin.get<PeerDeliveryDao>()
        val interrupted =
            object : PeerDeliveryDao by dao {
                override suspend fun upsert(entry: PeerDeliveryEntity) {
                    dao.upsert(entry)
                    throw CancellationException("capture interrupted after delivery write")
                }
            }
        return MarkedMomentStore(koin.get(), interrupted, koin.get(), transactions = koin.get<SyncTransactionManager>(WatchCloud))
    }

    private suspend fun delivery(mark: MarkedMomentEntity) =
        koin.get<PeerDeliveryDao>().get(
            MarkDelivery.PEER_SCOPE,
            MarkDelivery.DIRECTION,
            EntityType.MOMENT.name,
            mark.id.toString(),
        )

    private fun newMark(): MarkedMomentEntity {
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        return MarkedMomentEntity(
            id = Uuid.random(),
            capturedAt = now,
            timeZoneId = "UTC",
            state = MarkedMomentState.OPEN,
            originDeviceId = "e2e",
            lastUpdated = now,
        )
    }
}
