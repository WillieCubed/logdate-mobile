package app.logdate.reminders.e2e

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.domain.reminders.DeviceReminderBudget
import app.logdate.client.domain.reminders.PhoneReminderDelivery
import app.logdate.client.domain.reminders.PhoneReminderOutcome
import app.logdate.client.domain.reminders.PhoneReminderSnapshot
import app.logdate.client.domain.reminders.PhoneReminderSnapshotSource
import app.logdate.client.domain.reminders.ReminderClock
import app.logdate.client.domain.reminders.ReminderPostedHistory
import app.logdate.client.domain.reminders.ReminderSignals
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
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

@RunWith(AndroidJUnit4::class)
class PhoneReminderDeliveryRuntimeTest {
    @Test
    fun installedStandaloneDeliveryPostsAndDurablySuppressesAnotherRun() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            InstrumentationRegistry
                .getInstrumentation()
                .uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            val koin = KoinJavaComponent.getKoin()
            val storage = koin.get<KeyValueStorage>()
            val keys = listOf(DeviceReminderBudget.STORAGE_KEY, ReminderPostedHistory.STORAGE_KEY)
            val before = keys.associateWith { storage.getString(it) }
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
                        "native-fixture",
                    )
                }
            try {
                keys.forEach { storage.remove(it) }
                val first = koin.get<PhoneReminderDelivery> { parametersOf(source) }
                assertEquals(PhoneReminderOutcome.PHONE_POSTED, first.run())
                assertFalse(DeviceReminderBudget(storage).available(day))
                assertNotNull(ReminderPostedHistory(storage).latest())
                withTimeout(5_000) {
                    while (manager.activeNotifications.none { it.tag == "logdate.reminder" && it.id == 4005 }) delay(25)
                }
                val notification = manager.activeNotifications.single { it.tag == "logdate.reminder" && it.id == 4005 }
                val second = koin.get<PhoneReminderDelivery> { parametersOf(source) }.run()
                assertTrue(second == PhoneReminderOutcome.SKIPPED || second == PhoneReminderOutcome.HELD)
                assertEquals(
                    notification.postTime,
                    manager.activeNotifications.single { it.tag == "logdate.reminder" && it.id == 4005 }.postTime,
                )
            } finally {
                manager.cancel("logdate.reminder", 4005)
                before.forEach { (key, value) -> if (value == null) storage.remove(key) else storage.putString(key, value) }
            }
        }
}
