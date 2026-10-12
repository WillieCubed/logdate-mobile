package app.logdate.wear.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.PrefixedKeyValueStorage
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PhoneState
import app.logdate.client.sync.peer.state.ReminderHours
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.StateDocumentCodec
import app.logdate.wear.reminders.WatchReminderHours
import app.logdate.wear.reminders.WatchReminderRules
import app.logdate.wear.reminders.WatchReminders
import app.logdate.wear.reminders.nextEveningCheck
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class LearnedReminderHoursRuntimeTest {
    private val koin get() = KoinJavaComponent.getKoin()
    private val storage = PrefixedKeyValueStorage(koin.get<KeyValueStorage>(), "learned_hours_runtime:")

    @Before @After
    fun cleanUp() = runBlocking { storage.remove(WatchReminderHours.STORAGE_KEY) }

    @Test(timeout = 30_000)
    fun decodedPhoneHoursPersistAndDriveOfflineWatchWindow() =
        runBlocking {
            koin.get<WatchReminders>()
            val phone =
                StateDocument(
                    role = PeerRole.PHONE,
                    writtenAt = 1_000,
                    phone = PhoneState(reminderHours = ReminderHours(32_400, 7_200)),
                )
            val decoded = assertIs<Decoded.Supported<StateDocument>>(StateDocumentCodec.decode(StateDocumentCodec.encode(phone)))
            assertTrue(WatchReminderHours(storage) { it == "phone" }.adopt("phone", decoded.value))
            val reopened = WatchReminderHours(storage) { it == "phone" }
            reopened.initialize()
            val now = Instant.parse("2026-10-12T21:00:00Z")
            assertEquals(Instant.parse("2026-10-13T00:00:00Z"), nextEveningCheck(now, TimeZone.UTC, reopened.current))
            assertFalse(WatchReminderRules.eveningReminderDue(now, null, 0, false, TimeZone.UTC, reopened.current))
            assertTrue(
                WatchReminderRules.eveningReminderDue(
                    Instant.parse("2026-10-13T01:00:00Z"),
                    null,
                    0,
                    false,
                    TimeZone.UTC,
                    reopened.current,
                ),
            )
        }
}
