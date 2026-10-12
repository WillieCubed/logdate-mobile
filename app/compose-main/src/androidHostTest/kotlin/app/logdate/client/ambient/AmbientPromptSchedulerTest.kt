package app.logdate.client.ambient

import android.app.AlarmManager
import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.recommendation.MemoriesSettings
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.WatchPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class AmbientPromptSchedulerTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val enabled = MutableStateFlow(false)
    private val reminders = MutableStateFlow(false)
    private val flags =
        object : FeatureFlagStore {
            override fun observe(flag: FeatureFlag): Flow<Boolean> {
                check(flag == FeatureFlag.WATCH_EXPERIENCE_V2)
                return enabled
            }

            override suspend fun setEnabled(
                flag: FeatureFlag,
                enabled: Boolean,
            ) {
                this@AmbientPromptSchedulerTest.enabled.value = enabled
            }
        }
    private val shared =
        object : WatchPreferencesRepository {
            @Suppress("UNCHECKED_CAST")
            override fun <T> observe(key: SharedPreferenceKey<T>): Flow<T> {
                check(key == SharedPreferenceKey.RemindersEnabled)
                return reminders as Flow<T>
            }

            @Suppress("UNCHECKED_CAST")
            override suspend fun <T> get(key: SharedPreferenceKey<T>): T {
                check(key == SharedPreferenceKey.RemindersEnabled)
                return reminders.value as T
            }

            override suspend fun <T> set(
                key: SharedPreferenceKey<T>,
                value: T,
            ) {
                reminders.value = value as Boolean
            }
        }
    private val settings =
        object : MemoriesSettingsRepository {
            override suspend fun getSettings() = MemoriesSettings()

            override fun observeSettings() = flowOf(MemoriesSettings())

            override suspend fun updateSettings(settings: MemoriesSettings) = Unit
        }
    private lateinit var scheduler: AmbientPromptScheduler
    private lateinit var work: WorkManager
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms

    @Before fun prepare() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration
                .Builder()
                .setWorkerFactory(
                    object : WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ): ListenableWorker =
                            object : ListenableWorker(appContext, workerParameters) {
                                private var pending: CallbackToFutureAdapter.Completer<Result>? = null

                                override fun startWork() =
                                    CallbackToFutureAdapter.getFuture<Result> {
                                        pending = it
                                        "Held scheduling test work"
                                    }

                                override fun onStopped() {
                                    pending?.setCancelled()
                                }
                            }
                    },
                ).build(),
        )
        work = WorkManager.getInstance(context)
        scheduler = AmbientPromptScheduler(context, settings, flags, shared)
    }

    @After fun closeWork() {
        work.cancelAllWork().result.get(10, TimeUnit.SECONDS)
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    private fun scheduled() =
        work
            .getWorkInfosForUniqueWork("logdate:ambient:periodic")
            .get(10, TimeUnit.SECONDS)
            .filter { it.state != WorkInfo.State.CANCELLED }

    @Test fun flagOffPreservesExistingFixedAlarms() =
        runBlocking {
            scheduler.refreshSchedules()
            assertEquals(2, alarms.size)
            assertEquals(TimeUnit.HOURS.toMillis(6), scheduled().single().periodicityInfo?.repeatIntervalMillis)
        }

    @Test fun enablingMomentRemindersReplacesFixedAlarmsWithFrequentEvaluation() =
        runBlocking {
            scheduler.refreshSchedules()
            enabled.value = true
            reminders.value = true
            scheduler.refreshSchedules()
            assertEquals(0, alarms.size)
            assertEquals(TimeUnit.MINUTES.toMillis(15), scheduled().single().periodicityInfo?.repeatIntervalMillis)
        }

    @Test fun consentOffCancelsPreviouslyScheduledEvaluationAndFixedAlarms() =
        runBlocking {
            scheduler.refreshSchedules()
            enabled.value = true
            scheduler.refreshSchedules()
            assertEquals(0, alarms.size)
            assertTrue(scheduled().isEmpty())
        }

    @Test fun newSharedConsentDoesNotDependOnLegacyAmbientSwitches() =
        runBlocking {
            enabled.value = true
            reminders.value = true
            scheduler.refreshSchedules(MemoriesSettings(contextualRecommendationsEnabled = false, ambientPromptsEnabled = false))
            assertEquals(0, alarms.size)
            assertEquals(TimeUnit.MINUTES.toMillis(15), scheduled().single().periodicityInfo?.repeatIntervalMillis)
        }

    @Test fun rollingFlagBackRestoresLegacySchedule() =
        runBlocking {
            enabled.value = true
            reminders.value = true
            scheduler.refreshSchedules()
            enabled.value = false
            scheduler.refreshSchedules()
            assertEquals(2, alarms.size)
            assertEquals(TimeUnit.HOURS.toMillis(6), scheduled().single().periodicityInfo?.repeatIntervalMillis)
        }

    @Test fun observerReactsToRolloutAndSharedConsentWithoutLegacySettingsChanging() =
        runTest {
            AmbientPromptSchedulingObserver(settings, scheduler, backgroundScope, flags, shared).start()
            runCurrent()
            assertEquals(2, alarms.size)
            enabled.value = true
            runCurrent()
            assertEquals(0, alarms.size)
            assertTrue(scheduled().isEmpty())
            reminders.value = true
            runCurrent()
            assertEquals(TimeUnit.MINUTES.toMillis(15), scheduled().single().periodicityInfo?.repeatIntervalMillis)
            reminders.value = false
            runCurrent()
            assertTrue(scheduled().isEmpty())
        }

    @Test fun signalUpdatesQueueBehindActiveEvaluationWithoutCancellingIt() {
        scheduler.enqueueMomentEvaluation()
        val first = work.getWorkInfosForUniqueWork("logdate:moment-reminder:signal").get(10, TimeUnit.SECONDS).single()
        scheduler.enqueueMomentEvaluation()
        val queued = work.getWorkInfosForUniqueWork("logdate:moment-reminder:signal").get(10, TimeUnit.SECONDS)
        assertEquals(2, queued.size)
        assertEquals(WorkInfo.State.RUNNING, queued.single { it.id == first.id }.state)
        assertEquals(WorkInfo.State.BLOCKED, queued.single { it.id != first.id }.state)
    }

    @Test fun optingOutCancelsPendingSignalEvaluationsAsWellAsPeriodicWork() =
        runBlocking {
            enabled.value = true
            scheduler.enqueueMomentEvaluation()
            scheduler.enqueueMomentEvaluation()
            scheduler.refreshSchedules()
            val cancelled = work.getWorkInfosForUniqueWork("logdate:moment-reminder:signal").get(10, TimeUnit.SECONDS)
            assertEquals(2, cancelled.size)
            assertTrue(cancelled.all { it.state == WorkInfo.State.CANCELLED })
        }
}
