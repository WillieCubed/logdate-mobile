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
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.reminders.DeviceReminderBudget
import app.logdate.client.domain.reminders.PhoneReminderDelivery
import app.logdate.client.domain.reminders.PhoneReminderSnapshot
import app.logdate.client.domain.reminders.PhoneReminderSnapshotSource
import app.logdate.client.domain.reminders.ReminderClock
import app.logdate.client.domain.reminders.ReminderPostedHistory
import app.logdate.client.domain.reminders.ReminderSignals
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.WatchPreferencesRepository
import app.logdate.client.reminders.MomentReminderWorker
import app.logdate.client.reminders.PhoneMomentReminderNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Clock

@RunWith(AndroidJUnit4::class)
class MomentReminderWorkerRuntimeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val koin get() = KoinJavaComponent.getKoin()

    private fun worker(delivery: PhoneReminderDelivery): MomentReminderWorker =
        TestListenableWorkerBuilder<MomentReminderWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker {
                        check(workerClassName == MomentReminderWorker::class.java.name)
                        return MomentReminderWorker(appContext, workerParameters, koin.get(), delivery)
                    }
                },
            ).build()

    private suspend fun withFlag(
        enabled: Boolean,
        block: suspend () -> Unit,
    ) {
        val flags = koin.get<FeatureFlagStore>()
        val previous = flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)
        try {
            flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, enabled)
            block()
        } finally {
            flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, previous)
        }
    }

    @Test fun queuedSignalWorkDoesNotBecomeLegacyDeliveryWhenRolloutIsDisabled(): Unit =
        runBlocking {
            withFlag(false) {
                val source = PhoneReminderSnapshotSource { error("A held signal job must not read private inputs") }
                assertEquals(ListenableWorker.Result.success(), worker(koin.get { parametersOf(source) }).doWork())
            }
        }

    @Test fun installedFactoryResolvesSignalWorkerAndItsLiveOptOutSource(): Unit =
        runBlocking {
            val shared = koin.get<WatchPreferencesRepository>()
            val previous = shared.get(SharedPreferenceKey.RemindersEnabled)
            try {
                shared.set(SharedPreferenceKey.RemindersEnabled, false)
                withFlag(true) {
                    assertFalse(
                        koin
                            .get<PhoneReminderSnapshotSource>()
                            .read()
                            .signals.enabled,
                    )
                    val installed =
                        TestListenableWorkerBuilder<MomentReminderWorker>(context)
                            .setWorkerFactory(KoinWorkerFactory())
                            .build()
                    assertEquals(ListenableWorker.Result.success(), installed.doWork())
                }
            } finally {
                shared.set(SharedPreferenceKey.RemindersEnabled, previous)
            }
        }

    @Test fun signalWorkerPropagatesCancellation(): Unit =
        runBlocking {
            withFlag(true) {
                val source = PhoneReminderSnapshotSource { throw CancellationException("Interrupted signal evaluation") }
                assertFailsWith<CancellationException> { worker(koin.get { parametersOf(source) }).doWork() }
            }
        }

    @Test fun unavailableSignalsStayQuietWithoutRetryingImmediately(): Unit =
        runBlocking {
            withFlag(true) {
                val source = PhoneReminderSnapshotSource { throw IOException("Signal source unavailable") }
                assertEquals(ListenableWorker.Result.success(), worker(koin.get { parametersOf(source) }).doWork())
            }
        }

    @Test fun concurrentSignalWorkersPostOnceThroughTheInstalledDeviceBudget(): Unit =
        runBlocking {
            InstrumentationRegistry
                .getInstrumentation()
                .uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            val storage = koin.get<KeyValueStorage>()
            val keys = listOf(DeviceReminderBudget.STORAGE_KEY, ReminderPostedHistory.STORAGE_KEY)
            val previous = keys.associateWith { storage.getString(it) }
            val manager = context.getSystemService(NotificationManager::class.java)
            val notifier = koin.get<PhoneMomentReminderNotifier>()
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
                        "concurrent-signal-fixture",
                    )
                }
            val posted = AtomicInteger()
            try {
                keys.forEach { storage.remove(it) }
                withFlag(true) {
                    coroutineScope {
                        (1..4)
                            .map {
                                async(Dispatchers.Default) {
                                    val delivery =
                                        PhoneReminderDelivery(
                                            koin.get(),
                                            koin.get(),
                                            koin.get(),
                                            koin.get(),
                                            koin.get(),
                                            source,
                                            isCurrent = { error("Standalone fixture has no peer") },
                                            postPhone = { trigger, marks, date ->
                                                posted.incrementAndGet()
                                                notifier.post(trigger, marks, date)
                                            },
                                        )
                                    worker(delivery).doWork()
                                }
                            }.awaitAll()
                            .forEach { assertEquals(ListenableWorker.Result.success(), it) }
                    }
                    assertEquals(1, posted.get())
                    withTimeout(5_000) {
                        while (manager.activeNotifications.none { it.tag == "logdate.reminder" && it.id == 4005 }) delay(25)
                    }
                    assertFalse(DeviceReminderBudget(storage).available(day))
                }
            } finally {
                manager.cancel("logdate.reminder", 4005)
                previous.forEach { (key, value) -> if (value == null) storage.remove(key) else storage.putString(key, value) }
            }
        }
}
