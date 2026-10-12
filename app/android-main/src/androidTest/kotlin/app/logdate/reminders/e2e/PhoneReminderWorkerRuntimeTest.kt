package app.logdate.reminders.e2e

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.logdate.client.ambient.AmbientPromptWorker
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.reminders.DeviceReminderBudget
import app.logdate.client.domain.reminders.PhoneReminderDelivery
import app.logdate.client.domain.reminders.PhoneReminderOutcome
import app.logdate.client.domain.reminders.PhoneReminderSnapshot
import app.logdate.client.domain.reminders.PhoneReminderSnapshotSource
import app.logdate.client.domain.reminders.ReminderClock
import app.logdate.client.domain.reminders.ReminderPostedHistory
import app.logdate.client.domain.reminders.ReminderSignals
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.WatchPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Clock

@RunWith(AndroidJUnit4::class)
class PhoneReminderWorkerRuntimeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val koin get() = KoinJavaComponent.getKoin()

    private fun worker(source: PhoneReminderSnapshotSource): AmbientPromptWorker {
        val delivery = koin.get<PhoneReminderDelivery> { parametersOf(source) }
        return TestListenableWorkerBuilder<AmbientPromptWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker {
                        check(workerClassName == AmbientPromptWorker::class.java.name)
                        return AmbientPromptWorker(appContext, workerParameters, koin.get(), delivery, koin.get(), koin.get(), koin.get())
                    }
                },
            ).build()
    }

    private suspend fun withRollout(block: suspend () -> Unit) {
        val flags = koin.get<FeatureFlagStore>()
        val previous = flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)
        try {
            flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, true)
            block()
        } finally {
            flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, previous)
        }
    }

    @Test fun installedWorkerFactoryUsesLiveSourceAndRespectsOptOut(): Unit =
        runBlocking {
            val shared = koin.get<WatchPreferencesRepository>()
            val storage = koin.get<KeyValueStorage>()
            val previous = shared.get(SharedPreferenceKey.RemindersEnabled)
            val budget = storage.getString(DeviceReminderBudget.STORAGE_KEY)
            val history = storage.getString(ReminderPostedHistory.STORAGE_KEY)
            try {
                shared.set(SharedPreferenceKey.RemindersEnabled, false)
                withRollout {
                    assertFalse(
                        koin
                            .get<PhoneReminderSnapshotSource>()
                            .read()
                            .signals.enabled,
                    )
                    assertEquals(PhoneReminderOutcome.SKIPPED, koin.get<PhoneReminderDelivery>().run())
                    val installed =
                        TestListenableWorkerBuilder<AmbientPromptWorker>(context)
                            .setWorkerFactory(KoinWorkerFactory())
                            .build()
                    assertEquals(ListenableWorker.Result.success(), installed.doWork())
                    assertEquals(budget, storage.getString(DeviceReminderBudget.STORAGE_KEY))
                    assertEquals(history, storage.getString(ReminderPostedHistory.STORAGE_KEY))
                }
            } finally {
                shared.set(SharedPreferenceKey.RemindersEnabled, previous)
            }
        }

    @Test fun installedWorkerPostsMomentReminderAndSuppressesAnotherRun(): Unit =
        runBlocking {
            InstrumentationRegistry
                .getInstrumentation()
                .uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            val storage = koin.get<KeyValueStorage>()
            val keys = listOf(DeviceReminderBudget.STORAGE_KEY, ReminderPostedHistory.STORAGE_KEY)
            val previous = keys.associateWith { storage.getString(it) }
            val manager = context.getSystemService(NotificationManager::class.java)
            val day =
                Clock.System
                    .now()
                    .toLocalDateTime(TimeZone.UTC)
                    .date
            val noon = LocalDateTime(day, LocalTime(12, 0)).toInstant(TimeZone.UTC)
            val source =
                PhoneReminderSnapshotSource {
                    PhoneReminderSnapshot(
                        ReminderClock(noon, TimeZone.UTC),
                        ReminderSignals(enabled = true, writtenToday = false, workoutEndedAt = noon),
                        "worker-fixture",
                    )
                }
            try {
                keys.forEach { storage.remove(it) }
                withRollout {
                    assertEquals(ListenableWorker.Result.success(), worker(source).doWork())
                    withTimeout(5_000) {
                        while (manager.activeNotifications.none { it.tag == "logdate.reminder" && it.id == 4005 }) delay(25)
                    }
                    val posted = manager.activeNotifications.single { it.tag == "logdate.reminder" && it.id == 4005 }.postTime
                    assertFalse(DeviceReminderBudget(storage).available(day))
                    assertEquals(ListenableWorker.Result.success(), worker(source).doWork())
                    assertEquals(posted, manager.activeNotifications.single { it.tag == "logdate.reminder" && it.id == 4005 }.postTime)
                }
            } finally {
                manager.cancel("logdate.reminder", 4005)
                previous.forEach { (key, value) -> if (value == null) storage.remove(key) else storage.putString(key, value) }
            }
        }

    @Test fun unavailableSignalsStayQuietUntilTheNextEvaluation(): Unit =
        runBlocking {
            withRollout {
                assertEquals(ListenableWorker.Result.success(), worker { throw IOException("Signal unavailable") }.doWork())
            }
        }

    @Test fun cancellationDoesNotContinueIntoLegacyDelivery(): Unit =
        runBlocking {
            withRollout {
                assertFailsWith<CancellationException> {
                    worker { throw CancellationException("Cancelled signal collection") }.doWork()
                }
            }
        }
}
