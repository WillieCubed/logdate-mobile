package app.logdate.wear.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.database.dao.sync.PeerDeliveryDao
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.wear.v2.mark.MarkDelivery
import app.logdate.wear.v2.mark.MarkedMomentStore
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
}
