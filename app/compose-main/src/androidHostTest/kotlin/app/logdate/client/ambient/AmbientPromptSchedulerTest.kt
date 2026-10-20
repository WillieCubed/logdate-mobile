package app.logdate.client.ambient

import android.app.AlarmManager
import android.app.Notification
import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.Configuration
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.recommendation.AmbientPromptTriggerContext
import app.logdate.client.domain.recommendation.MemoriesSettings
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.WatchPreferencesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
    private val savedSettings = MutableStateFlow(MemoriesSettings())
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
            override suspend fun getSettings() = savedSettings.value

            override fun observeSettings() = savedSettings

            override suspend fun updateSettings(settings: MemoriesSettings) {
                savedSettings.value = settings
            }
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

                                override fun getForegroundInfoAsync() =
                                    CallbackToFutureAdapter.getFuture<ForegroundInfo> {
                                        it.set(ForegroundInfo(1947999572, Notification()))
                                        "Foreground information for held expedited test work"
                                    }

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

    private fun immediate(trigger: AmbientPromptTriggerContext = AmbientPromptTriggerContext.PERIODIC) =
        work
            .getWorkInfosForUniqueWork("logdate:ambient:immediate:${trigger.name.lowercase()}")
            .get(10, TimeUnit.SECONDS)
            .filterNot { it.state.isFinished }

    @Test fun startupOrBootCannotRecreateEvaluationAfterConsentWasDisabled() =
        runBlocking {
            enabled.value = true
            reminders.value = false
            scheduler.refreshSchedules()
            scheduler.enqueueImmediateEvaluation(AmbientPromptTriggerContext.PERIODIC)
            assertTrue(immediate().isEmpty())
        }

    @Test fun legacyOptOutRejectsLateStartupAndAlarmCallbacks() =
        runBlocking {
            savedSettings.value = MemoriesSettings(ambientPromptsEnabled = false)
            scheduler.refreshSchedules()
            AmbientPromptTriggerContext.entries.forEach { scheduler.enqueueImmediateEvaluation(it) }
            AmbientPromptTriggerContext.entries.forEach { assertTrue(immediate(it).isEmpty()) }
        }

    @Test fun repeatedStartupRetainsTheAcceptedEvaluation() =
        runBlocking {
            scheduler.enqueueImmediateEvaluation(AmbientPromptTriggerContext.PERIODIC)
            val original = immediate().single()
            scheduler.enqueueImmediateEvaluation(AmbientPromptTriggerContext.PERIODIC)
            assertEquals(original.id, immediate().single().id)
        }

    @Test fun disabledMorningAlarmCannotRecreateAnEvaluation() =
        runBlocking {
            savedSettings.value = MemoriesSettings(morningPromptEnabled = false)
            scheduler.enqueueImmediateEvaluation(AmbientPromptTriggerContext.MORNING_SCHEDULE)
            assertTrue(immediate(AmbientPromptTriggerContext.MORNING_SCHEDULE).isEmpty())
        }

    @Test fun staleMomentSignalCannotRecreateEvaluationAfterOptOut() =
        runBlocking {
            enabled.value = true
            reminders.value = false
            scheduler.refreshSchedules()
            scheduler.enqueueMomentEvaluation()
            assertTrue(work.getWorkInfosForUniqueWork("logdate:moment-reminder:signal").get().none { !it.state.isFinished })
        }

    @Test fun optOutWaitsForAnOlderEnqueueThenCancelsItsAcceptedWork() =
        runTest {
            val reading = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var firstRead = true
            val delayedSettings =
                object : MemoriesSettingsRepository by settings {
                    override suspend fun getSettings(): MemoriesSettings {
                        val snapshot = savedSettings.value
                        if (firstRead) {
                            firstRead = false
                            reading.complete(Unit)
                            release.await()
                        }
                        return snapshot
                    }
                }
            val delayedScheduler = AmbientPromptScheduler(context, delayedSettings, flags, shared)
            val enqueue = backgroundScope.launch { delayedScheduler.enqueueImmediateEvaluation(AmbientPromptTriggerContext.PERIODIC) }
            runCurrent()
            assertTrue(reading.isCompleted)
            savedSettings.value = MemoriesSettings(ambientPromptsEnabled = false)
            val optOut = backgroundScope.launch { delayedScheduler.refreshSchedules() }
            runCurrent()
            assertTrue(!optOut.isCompleted, "Opt-out must serialize with the older accepted enqueue")
            release.complete(Unit)
            runCurrent()
            assertTrue(enqueue.isCompleted)
            assertTrue(optOut.isCompleted)
            assertTrue(immediate().isEmpty())
            assertTrue(scheduled().isEmpty())
        }

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
            savedSettings.value = MemoriesSettings(contextualRecommendationsEnabled = false, ambientPromptsEnabled = false)
            scheduler.refreshSchedules()
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

    @Test fun signalUpdatesQueueBehindActiveEvaluationWithoutCancellingIt() =
        runBlocking {
            enabled.value = true
            reminders.value = true
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
            reminders.value = true
            scheduler.enqueueMomentEvaluation()
            scheduler.enqueueMomentEvaluation()
            reminders.value = false
            scheduler.refreshSchedules()
            val cancelled = work.getWorkInfosForUniqueWork("logdate:moment-reminder:signal").get(10, TimeUnit.SECONDS)
            assertEquals(2, cancelled.size)
            assertTrue(cancelled.all { it.state == WorkInfo.State.CANCELLED })
        }
}
