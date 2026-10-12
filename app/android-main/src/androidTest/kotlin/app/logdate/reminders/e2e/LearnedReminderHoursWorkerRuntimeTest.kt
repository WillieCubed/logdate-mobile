package app.logdate.reminders.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import app.logdate.client.ambient.AmbientPromptWorker
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.reminders.LearnedReminderHours
import app.logdate.client.domain.reminders.WakingHours
import app.logdate.client.domain.reminders.WindDownTimeResolver
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.WatchPreferencesRepository
import app.logdate.client.health.SleepRepository
import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/** The actual periodic worker refreshes published hours even when delivery's preflight skips. */
@RunWith(AndroidJUnit4::class)
class LearnedReminderHoursWorkerRuntimeTest {
    @Test(timeout = 30_000)
    fun installedPeriodicWorkerRefreshesHealthAccessAndLearnedHours() =
        runBlocking {
            coroutineScope {
                val koin = KoinJavaComponent.getKoin()
                val original = koin.get<LearnedReminderHours>()
                val flags = koin.get<FeatureFlagStore>()
                val shared = koin.get<WatchPreferencesRepository>()
                val oldFlag = flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)
                val oldChoice = shared.get(SharedPreferenceKey.RemindersEnabled)
                val sleep = TestSleep()
                val hours =
                    LearnedReminderHours(
                        MutableStateFlow(true),
                        WindDownTimeResolver(sleep),
                        clock =
                            object : Clock {
                                override fun now() = Instant.parse("2026-10-12T20:00:00Z")
                            },
                        zone = { TimeZone.UTC },
                    )
                val observed = Channel<WakingHours?>(Channel.UNLIMITED)
                val collector = launch(Dispatchers.Default) { hours.observe().collect { observed.send(it) } }
                try {
                    assertEquals(WakingHours.Fallback, withTimeout(10_000) { observed.receive() })
                    loadKoinModules(module { single<LearnedReminderHours> { hours } })
                    // Keep real content reads and notifications off; this exercises the periodic refresh before preflight.
                    shared.set(SharedPreferenceKey.RemindersEnabled, false)
                    flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, true)
                    sleep.granted = true
                    val context = ApplicationProvider.getApplicationContext<Context>()
                    val worker = TestListenableWorkerBuilder<AmbientPromptWorker>(context).setWorkerFactory(KoinWorkerFactory()).build()
                    assertEquals(ListenableWorker.Result.success(), worker.doWork())
                    assertEquals(WakingHours(LocalTime(8, 0), LocalTime(23, 0)), withTimeout(10_000) { observed.receive() })
                    sleep.granted = false
                    val revoked = TestListenableWorkerBuilder<AmbientPromptWorker>(context).setWorkerFactory(KoinWorkerFactory()).build()
                    assertEquals(ListenableWorker.Result.success(), revoked.doWork())
                    assertEquals(WakingHours.Fallback, withTimeout(10_000) { observed.receive() })
                } finally {
                    collector.cancel()
                    loadKoinModules(module { single<LearnedReminderHours> { original } })
                    shared.set(SharedPreferenceKey.RemindersEnabled, oldChoice)
                    flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, oldFlag)
                }
            }
        }

    private class TestSleep : SleepRepository {
        @Volatile var granted = false

        override suspend fun hasSleepPermissions() = granted

        override suspend fun requestSleepPermissions(): Boolean = error("No background permission request")

        override suspend fun getSleepSessions(
            start: Instant,
            end: Instant,
        ) = listOf(SleepSession("sleep", Instant.parse("2026-10-11T23:00:00Z"), Instant.parse("2026-10-12T08:00:00Z")))

        override suspend fun getAverageWakeUpTime(
            timeZone: TimeZone,
            days: Int,
        ): TimeOfDay? = error("Use sessions")

        override suspend fun getAverageSleepTime(
            timeZone: TimeZone,
            days: Int,
        ): TimeOfDay? = error("Use sessions")
    }
}
