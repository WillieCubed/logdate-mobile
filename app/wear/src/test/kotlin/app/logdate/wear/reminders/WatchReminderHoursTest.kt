package app.logdate.wear.reminders

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.domain.reminders.WakingHours
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PhoneState
import app.logdate.client.sync.peer.state.ReminderHours
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.wear.sync.v2.MemoryKeyValueStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchReminderHoursTest {
    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun `startup waits for retained hours before scheduling an offline check`() =
        runTest {
            hours().adopt("phone", document())
            val resume = CompletableDeferred<Unit>()
            val delayed =
                object : KeyValueStorage by storage {
                    override suspend fun getString(key: String): String? {
                        resume.await()
                        return storage.getString(key)
                    }
                }
            val hours = WatchReminderHours(delayed) { true }
            val work = FakeEveningWork()
            val now = berlin(10, 21)
            val scheduler = EveningReminderScheduler(work, FixedClock(now), { REMINDER_ZONE }, { hours.current })
            val reminders =
                WatchReminders(
                    FakeReminderPreferences(),
                    FakeActivityRegistration(),
                    FakeReminderPermissions(),
                    scheduler,
                    initializeHours = hours::initialize,
                )
            val restore = async { reminders.restore() }
            runCurrent()
            assertEquals(emptyList(), work.delays)
            resume.complete(Unit)
            restore.await()
            assertEquals(listOf(3 * 60 * 60 * 1_000L), work.delays)
        }

    @Test fun `unavailable storage does not crash adoption or restoration`() =
        runTest {
            val failing =
                object : KeyValueStorage by storage {
                    override suspend fun getString(key: String): String? = error("storage unavailable")
                }
            val hours = WatchReminderHours(failing) { true }
            assertFalse(hours.adopt("phone", document()))
            hours.initialize()
            assertEquals(WakingHours.Fallback, hours.current)
        }

    @Test fun `dropped write does not change live schedule`() =
        runTest {
            val dropping =
                object : KeyValueStorage by storage {
                    override suspend fun putString(
                        key: String,
                        value: String,
                    ) = Unit
                }
            val hours = WatchReminderHours(dropping) { true }
            assertFalse(hours.adopt("phone", document()))
            assertEquals(WakingHours.Fallback, hours.current)
        }

    private val storage = MemoryKeyValueStorage()
    private var linked = true

    private fun hours() = WatchReminderHours(storage) { linked && it == "phone" }

    private fun document(
        at: Long = 1_000,
        hours: ReminderHours? = ReminderHours(32_400, 7_200),
    ) = StateDocument(role = PeerRole.PHONE, writtenAt = at, phone = PhoneState(reminderHours = hours))

    @Test fun `verified phone hours survive watch process recreation`() =
        runTest {
            val hours = hours()
            assertTrue(hours.adopt("phone", document()))
            assertEquals(WakingHours(LocalTime(9, 0), LocalTime(2, 0)), hours.current)
            val reopened = hours()
            reopened.initialize()
            assertEquals(hours.current, reopened.current)
        }

    @Test fun `foreign or revoked phone cannot provide hours`() =
        runTest {
            val hours = hours()
            assertFalse(hours.adopt("foreign", document()))
            linked = false
            assertFalse(hours.adopt("phone", document()))
            assertEquals(WakingHours.Fallback, hours.current)
        }

    @Test fun `revoked saved source is not restored`() =
        runTest {
            hours().adopt("phone", document())
            linked = false
            val reopened = hours()
            reopened.initialize()
            assertEquals(WakingHours.Fallback, reopened.current)
        }

    @Test fun `older replay does not replace latest hours`() =
        runTest {
            val hours = hours()
            hours.adopt("phone", document())
            assertFalse(hours.adopt("phone", document(900, ReminderHours(25_200, 75_600))))
            assertEquals(WakingHours(LocalTime(9, 0), LocalTime(2, 0)), hours.current)
        }

    @Test fun `missing and invalid newer hours restore fallback`() =
        runTest {
            for (wire in listOf(null, ReminderHours(-1, 4), ReminderHours(4, 4), ReminderHours(90_000, 4))) {
                val hours = hours()
                hours.adopt("phone", document())
                assertTrue(hours.adopt("phone", document(2_000, wire)))
                assertEquals(WakingHours.Fallback, hours.current)
                storage.clear()
            }
        }
}
