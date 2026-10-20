package app.logdate.client.e2e

import android.app.Application
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.logdate.client.ambient.AmbientPromptScheduler
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.recommendation.AmbientPromptTriggerContext
import app.logdate.client.domain.recommendation.MemoriesSettings
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.WatchPreferencesRepository
import app.logdate.client.ensureLogDateApplicationStarted
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Actual AlarmManager registrations and WorkManager persistence on an isolated managed device. */
@RunWith(AndroidJUnit4::class)
class RestoredReminderSchedulingRuntimeTest {
    @Test
    fun currentPreferencesRecreateOnlyValidSchedules() =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            assumeTrue(InstrumentationRegistry.getArguments().getString("restoreAlarmAcceptance") == "true")
            require(Build.HARDWARE in setOf("ranchu", "goldfish"))
            val context = instrumentation.targetContext
            require(context.packageName.endsWith(".debug"))
            ensureLogDateApplicationStarted(context.applicationContext as Application)
            val koin = GlobalContext.get()
            val settings = koin.get<MemoriesSettingsRepository>()
            val flags = koin.get<FeatureFlagStore>()
            val shared = koin.get<WatchPreferencesRepository>()
            val scheduler = koin.get<AmbientPromptScheduler>()
            val work = WorkManager.getInstance(context)
            val originalSettings = settings.getSettings()
            val originalRollout = flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)
            val originalConsent = shared.get(SharedPreferenceKey.RemindersEnabled)

            fun active(name: String): List<WorkInfo> =
                work.getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS).filterNot { it.state.isFinished }

            fun fixedAlarmCounts(): List<Int> {
                val dump =
                    ParcelFileDescriptor
                        .AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("dumpsys alarm"))
                        .bufferedReader()
                        .use { it.readText() }
                assertTrue(!dump.contains("Permission Denial"), "AlarmManager inspection requires shell access")
                return listOf(AmbientPromptScheduler.ACTION_MORNING_PROMPT, AmbientPromptScheduler.ACTION_EVENING_PROMPT).map { action ->
                    dump.lineSequence().count { it.trim() == "tag=*walarm*:$action" }
                }
            }

            fun assertNoEvaluation() {
                assertTrue(active("logdate:ambient:periodic").isEmpty())
                assertTrue(active("logdate:moment-reminder:signal").isEmpty())
                AmbientPromptTriggerContext.entries.forEach {
                    assertTrue(active("logdate:ambient:immediate:${it.name.lowercase()}").isEmpty())
                }
            }

            try {
                flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, false)
                settings.updateSettings(MemoriesSettings())
                repeat(3) { scheduler.refreshSchedules() }
                assertEquals(listOf(1, 1), fixedAlarmCounts(), "Repeated reconciliation must register each fixed alarm once")
                assertEquals(
                    TimeUnit.HOURS.toMillis(6),
                    active("logdate:ambient:periodic").single().periodicityInfo?.repeatIntervalMillis,
                )
                AmbientPromptScheduler(context, settings, flags, shared).refreshSchedules()
                assertEquals(listOf(1, 1), fixedAlarmCounts(), "A reconstructed scheduler must retain one registration per action")

                settings.updateSettings(MemoriesSettings(ambientPromptsEnabled = false))
                scheduler.refreshSchedules()
                AmbientPromptTriggerContext.entries.forEach { scheduler.enqueueImmediateEvaluation(it) }
                assertEquals(listOf(0, 0), fixedAlarmCounts())
                assertNoEvaluation()

                flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, true)
                shared.set(SharedPreferenceKey.RemindersEnabled, true)
                repeat(3) { scheduler.refreshSchedules() }
                assertEquals(listOf(0, 0), fixedAlarmCounts())
                assertEquals(
                    TimeUnit.MINUTES.toMillis(15),
                    active("logdate:ambient:periodic").single().periodicityInfo?.repeatIntervalMillis,
                )

                shared.set(SharedPreferenceKey.RemindersEnabled, false)
                scheduler.refreshSchedules()
                AmbientPromptTriggerContext.entries.forEach { scheduler.enqueueImmediateEvaluation(it) }
                scheduler.enqueueMomentEvaluation()
                assertEquals(listOf(0, 0), fixedAlarmCounts())
                assertNoEvaluation()
            } finally {
                settings.updateSettings(originalSettings)
                shared.set(SharedPreferenceKey.RemindersEnabled, originalConsent)
                flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, originalRollout)
                scheduler.refreshSchedules()
                assertEquals(originalSettings, settings.getSettings())
                assertEquals(originalConsent, shared.get(SharedPreferenceKey.RemindersEnabled))
                assertEquals(originalRollout, flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2))
            }
        }
}
