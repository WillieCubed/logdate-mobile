package app.logdate.reminders.e2e

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
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
    fun installedLiveSnapshotReadsSavedJournalActivityWithExplicitConsent(): Unit =
        runBlocking {
            val koin = KoinJavaComponent.getKoin()
            val flags = koin.get<FeatureFlagStore>()
            val shared = koin.get<WatchPreferencesRepository>()
            val notes = koin.get<JournalNotesRepository>()
            val previousFlag = flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)
            val previousReminders = shared.get(SharedPreferenceKey.RemindersEnabled)
            val previousPlaces = shared.get(SharedPreferenceKey.CapturePlaces)
            val now = Clock.System.now()
            val fixture = JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "Reminder runtime fixture")
            try {
                shared.set(SharedPreferenceKey.CapturePlaces, false)
                shared.set(SharedPreferenceKey.RemindersEnabled, true)
                flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, true)
                notes.create(fixture)
                val snapshot = koin.get<PhoneReminderSnapshotSource>().read()
                assertTrue(snapshot.signals.enabled)
                assertTrue(snapshot.signals.writtenToday)
                assertTrue(snapshot.audience.isNotBlank())
                assertEquals(null, snapshot.signals.newPlaceArrivedAt)
                assertEquals(null, snapshot.signals.workoutEndedAt)
                assertNotNull(notes.getNoteById(fixture.uid))
            } finally {
                notes.removeById(fixture.uid)
                flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, previousFlag)
                shared.set(SharedPreferenceKey.RemindersEnabled, previousReminders)
                shared.set(SharedPreferenceKey.CapturePlaces, previousPlaces)
            }
        }

    @Test
    fun installedLiveSnapshotReadsTheRolloutHoldWithoutCollectingPrivateSignals() =
        runBlocking {
            val koin = KoinJavaComponent.getKoin()
            val flags = koin.get<FeatureFlagStore>()
            val previous = flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)
            try {
                flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, false)
                val snapshot = koin.get<PhoneReminderSnapshotSource>().read()
                assertFalse(snapshot.signals.enabled)
                assertEquals(0, snapshot.signals.openMarks)
                assertEquals(null, snapshot.signals.newPlaceArrivedAt)
                assertEquals(null, snapshot.signals.workoutEndedAt)
                assertEquals(PhoneReminderOutcome.SKIPPED, koin.get<PhoneReminderDelivery>().run())
            } finally {
                flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, previous)
            }
        }

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
