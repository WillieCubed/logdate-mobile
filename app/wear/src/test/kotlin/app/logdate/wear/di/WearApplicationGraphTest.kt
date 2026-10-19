package app.logdate.wear.di

import android.app.Activity
import android.app.Application
import android.content.Context
import app.logdate.client.sync.cloud.account.ServerAccountStore
import app.logdate.client.sync.datalayer.v2.WearProtocolGate
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.wear.reminders.WatchReminderHours
import app.logdate.wear.reminders.WatchReminders
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import app.logdate.wear.sync.v2.removal.WatchRemovalCaches
import app.logdate.wear.v2.continuity.WatchPlaybackHandoff
import app.logdate.wear.v2.continuity.WatchRecordingContinuity
import app.logdate.wear.v2.haptics.HapticsPreferenceStore
import app.logdate.wear.v2.phone.PhoneHandoff
import app.logdate.wear.v2.playback.MomentPlayer
import app.logdate.wear.v2.playback.PlaybackPreferencesStore
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Resolves actual singleton factories before any Activity exists; mocks stop at platform/domain boundaries. */
class WearApplicationGraphTest {
    private lateinit var graph: KoinApplication

    @Before
    fun setup() {
        val application = mockk<Application>()
        val messages = mockk<MessageClient>()
        mockkStatic(Wearable::class)
        every { Wearable.getMessageClient(application as Context) } returns messages
        graph =
            koinApplication {
                modules(
                    wearV2Module,
                    wearRemovalModule,
                    module {
                        single<Context> { application }
                        single<WatchDeliveryPeer> { mockk() }
                        single<MomentPlayer> { mockk() }
                        single<PhoneHandoff> { mockk() }
                        single<WearProtocolGate> { mockk() }
                        single<WatchReminderHours> { mockk() }
                        single<PeerStateStore> { mockk() }
                        single<WatchReminders> { mockk() }
                        single<PlaybackPreferencesStore> { mockk() }
                        single<HapticsPreferenceStore> { mockk() }
                        single<ServerAccountStore> { mockk() }
                    },
                )
            }
        assertNull(graph.koin.getOrNull<Activity>(), "Application startup must not require an Activity binding")
    }

    @After
    fun teardown() {
        graph.close()
        unmockkStatic(Wearable::class)
    }

    @Test
    fun recordingContinuityResolvesFromTheApplicationContext() {
        assertNotNull(graph.koin.get<WatchRecordingContinuity>())
    }

    @Test
    fun playbackHandoffResolvesFromTheApplicationContext() {
        assertNotNull(graph.koin.get<WatchPlaybackHandoff>())
    }

    @Test
    fun removalCachesResolveTheActualContinuityFactoryBeforeUiExists() {
        assertNotNull(graph.koin.get<WatchRemovalCaches>())
    }
}
