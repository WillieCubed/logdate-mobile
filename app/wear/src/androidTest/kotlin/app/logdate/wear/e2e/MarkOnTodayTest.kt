package app.logdate.wear.e2e

import android.Manifest
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.database.dao.sync.PeerDeliveryDao
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.database.entities.sync.PeerDeliveryEntity
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.wear.R
import app.logdate.wear.presentation.MainActivity
import app.logdate.wear.v2.mark.MarkDelivery
import app.logdate.wear.v2.today.TodayTags
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Taps Mark on Today in the real activity: the moment is saved and queued, and "Moment marked" is
 * shown. The heart rate permission is granted first so the system prompt does not cover the screen;
 * whether the emulator can read a heart rate does not matter, because a mark without one still succeeds.
 */
@RunWith(AndroidJUnit4::class)
class MarkOnTodayTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val koin get() = KoinJavaComponent.getKoin()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun openTheNewWatchApp() {
        InstrumentationRegistry
            .getInstrumentation()
            .uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.BODY_SENSORS)
        setNewWatchApp(enabled = true)
    }

    @After
    fun tearDown() {
        scenario?.onActivity { it.finish() }
        setNewWatchApp(enabled = false)
    }

    @Test
    fun tapping_Mark_saves_the_moment_confirms_it_and_queues_it_for_backup() {
        val before = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        scenario = ActivityScenario.launch(MainActivity::class.java)
        assertTrue(appears(LAUNCH_TIMEOUT_MS) { composeRule.onAllNodesWithTag(TodayTags.MARK) }, "the Mark button never appeared")

        composeRule.onNodeWithTag(TodayTags.MARK).performClick()

        runBlocking {
            val mark = withTimeout(TIMEOUT_MS) { openMarksSince(before).first { it.isNotEmpty() }.single() }
            val delivery = withTimeout(TIMEOUT_MS) { waitForDelivery(mark.id.toString()) }
            assertEquals(MarkDelivery.STATE_WAITING, delivery.state)
            assertTrue(koin.get<SyncMetadataService>().hasPending(EntityType.MOMENT, mark.id.toString()))
        }
        assertTrue(
            appears(TIMEOUT_MS) { composeRule.onAllNodesWithContentDescription(context.getString(R.string.wear_mark_saved)) },
            "\"Moment marked\" never appeared",
        )
    }

    private suspend fun waitForDelivery(markId: String): PeerDeliveryEntity {
        val dao = koin.get<PeerDeliveryDao>()
        while (true) {
            dao.get(MarkDelivery.PEER_SCOPE, MarkDelivery.DIRECTION, EntityType.MOMENT.name, markId)?.let { return it }
            delay(POLL_MS)
        }
    }

    private fun openMarksSince(after: Instant) =
        koin.get<MarkedMomentDao>().observeByState(MarkedMomentState.OPEN).map { rows -> rows.filter { it.capturedAt >= after } }

    private fun setNewWatchApp(enabled: Boolean) =
        runBlocking {
            koin.get<FeatureFlagStore>().setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, enabled)
        }

    /** Whether [nodes] appear within [timeoutMs]; before the activity has composed there is no hierarchy to search yet. */
    private fun appears(
        timeoutMs: Long,
        nodes: () -> SemanticsNodeInteractionCollection,
    ): Boolean =
        try {
            composeRule.waitUntil(timeoutMs) { runCatching { nodes().fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
            true
        } catch (e: ComposeTimeoutException) {
            false
        }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val LAUNCH_TIMEOUT_MS = 30_000L
        const val POLL_MS = 50L
    }
}
