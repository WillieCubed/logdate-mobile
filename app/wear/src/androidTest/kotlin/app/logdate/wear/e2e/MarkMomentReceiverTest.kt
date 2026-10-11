package app.logdate.wear.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.database.dao.sync.PeerDeliveryDao
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.database.entities.sync.PeerDeliveryEntity
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.wear.v2.mark.MarkDelivery
import app.logdate.wear.v2.mark.MarkMomentReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Fires Mark's pending intent, as a complication or tile does, and reads the mark from the watch's database. */
@RunWith(AndroidJUnit4::class)
class MarkMomentReceiverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val koin get() = KoinJavaComponent.getKoin()

    @After
    fun tearDown() = runBlocking { setNewWatchApp(enabled = false) }

    @Test
    fun a_broadcast_marks_the_moment_and_queues_it_for_backup() =
        runBlocking {
            setNewWatchApp(enabled = true)
            val before = Instant.fromEpochMilliseconds(System.currentTimeMillis())

            MarkMomentReceiver.pendingIntent(context).send()

            val mark = withTimeout(TIMEOUT_MS) { openMarksSince(before).first { it.isNotEmpty() }.single() }
            assertNull(mark.latitude)
            assertNull(mark.heartRateBpm)
            val delivery = withTimeout(TIMEOUT_MS) { waitForDelivery(mark.id.toString()) }
            assertEquals(MarkDelivery.STATE_WAITING, delivery.state)
            assertTrue(koin.get<SyncMetadataService>().hasPending(EntityType.MOMENT, mark.id.toString()))
        }

    @Test
    fun nothing_is_marked_while_the_new_watch_app_is_off() =
        runBlocking {
            setNewWatchApp(enabled = false)
            val before = Instant.fromEpochMilliseconds(System.currentTimeMillis())

            MarkMomentReceiver.pendingIntent(context).send()
            delay(3_000)

            assertTrue(openMarksSince(before).first().isEmpty())
        }

    /** The store writes the mark, then the outbox, then the ledger, so the ledger row is the last to appear. */
    private suspend fun waitForDelivery(markId: String): PeerDeliveryEntity {
        val dao = koin.get<PeerDeliveryDao>()
        while (true) {
            dao.get(MarkDelivery.PEER_SCOPE, MarkDelivery.DIRECTION, EntityType.MOMENT.name, markId)?.let { return it }
            delay(POLL_MS)
        }
    }

    private fun openMarksSince(after: Instant): Flow<List<MarkedMomentEntity>> =
        koin.get<MarkedMomentDao>().observeByState(MarkedMomentState.OPEN).map { rows -> rows.filter { it.capturedAt >= after } }

    private suspend fun setNewWatchApp(enabled: Boolean) {
        koin.get<FeatureFlagStore>().setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, enabled)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 50L
    }
}
