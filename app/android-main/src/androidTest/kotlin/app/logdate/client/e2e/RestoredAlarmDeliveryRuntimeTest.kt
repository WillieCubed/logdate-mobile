package app.logdate.client.e2e

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import androidx.work.await
import app.logdate.client.ambient.AmbientPromptAlarmReceiver
import app.logdate.client.ambient.AmbientPromptScheduler
import app.logdate.client.ambient.AmbientPromptWorker
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.WatchPreferencesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Fires the PendingIntent registered by the production scheduler through Android's AlarmManager. */
@RunWith(AndroidJUnit4::class)
class RestoredAlarmDeliveryRuntimeTest {
    @Test
    fun restoredMorningAlarmInvokesReceiverAndQueuesOneEvaluation() =
        runBlocking {
            assumeTrue(InstrumentationRegistry.getArguments().getString("restoreAlarmFireAcceptance") == "true")
            require(Build.HARDWARE in setOf("ranchu", "goldfish"))
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            require(context.packageName.endsWith(".debug"))
            check(!context.getDatabasePath("logdate").exists()) { "Use a fresh managed fixture before foreground startup" }
            val koin = GlobalContext.get()
            val settings = koin.get<MemoriesSettingsRepository>()
            val flags = koin.get<FeatureFlagStore>()
            val shared = koin.get<WatchPreferencesRepository>()
            val scheduler = koin.get<AmbientPromptScheduler>()
            val work = WorkManager.getInstance(context)
            val name = "logdate:ambient:immediate:morning_schedule"
            val before = work.getWorkInfosForUniqueWork(name).get()
            check(before.none { !it.state.isFinished }) { "The fixture cannot replace another morning evaluation" }
            val original = settings.getSettings()
            val rollout = flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)
            val consent = shared.get(SharedPreferenceKey.RemindersEnabled)
            try {
                flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, false)
                settings.updateSettings(
                    original.copy(
                        contextualRecommendationsEnabled = true,
                        captureNudgesEnabled = true,
                        ambientPromptsEnabled = true,
                        morningPromptEnabled = true,
                    ),
                )
                scheduler.refreshSchedules()
                val alarm =
                    assertNotNull(
                        PendingIntent.getBroadcast(
                            context,
                            4101,
                            Intent(context, AmbientPromptAlarmReceiver::class.java).setAction(AmbientPromptScheduler.ACTION_MORNING_PROMPT),
                            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                        ),
                        "The restored scheduler must register the real morning receiver",
                    )
                // Foreground observers remain unstarted: this fixture isolates cold receiver
                // delivery, while RestoredReminderColdStartTest checks foreground reconstruction.
                context.getSystemService(AlarmManager::class.java).set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 1_000,
                    alarm,
                )
                val previous = before.map { info -> info.id }.toSet()
                val delivered =
                    withTimeout(45_000) {
                        while (true) {
                            val requests = work.getWorkInfosForUniqueWork(name).get().filter { info -> info.id !in previous }
                            if (requests.isNotEmpty()) return@withTimeout requests
                            delay(100)
                        }
                        error("Unreachable")
                    }
                assertEquals(1, delivered.size, "A restored alarm must enqueue exactly one evaluation")
                assertTrue(AmbientPromptWorker::class.java.name in delivered.single().tags)
                // The receiver bounds asynchronous scheduling to eight seconds. Observe beyond
                // that entire window instead of accepting only the first nonempty snapshot.
                val settled = SystemClock.elapsedRealtime() + 8_500
                while (SystemClock.elapsedRealtime() < settled) {
                    assertEquals(
                        listOf(delivered.single().id),
                        work
                            .getWorkInfosForUniqueWork(name)
                            .get()
                            .filter { info -> info.id !in previous }
                            .map { info -> info.id },
                        "One alarm must not append another evaluation later in the receiver's scheduling window",
                    )
                    delay(100)
                }
            } finally {
                restoreFixtureState(
                    { work.cancelUniqueWork(name).await() },
                    {
                        settings.updateSettings(original)
                        assertEquals(original, settings.getSettings())
                    },
                    {
                        shared.set(SharedPreferenceKey.RemindersEnabled, consent)
                        assertEquals(consent, shared.get(SharedPreferenceKey.RemindersEnabled))
                    },
                    {
                        flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, rollout)
                        assertEquals(rollout, flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2))
                    },
                    { scheduler.refreshSchedules() },
                )
            }
        }
}
