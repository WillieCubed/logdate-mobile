package app.logdate.wear.reminders

import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.StampedPreferenceStore
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.Stamped
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.StateMerger
import app.logdate.wear.sync.v2.MemoryKeyValueStorage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class SharedWatchReminderSettingsTest {
    @Test
    fun `replaying future phone time does not invent a later reminder`() =
        runTest {
            val document = document(false, 900).copy(lastReminderAt = 2_000)
            settings.adopt("phone", document)
            clock.now = Instant.fromEpochMilliseconds(1_500)
            settings.adopt("phone", document)
            assertEquals(Instant.fromEpochMilliseconds(1_000), local.lastReminderAt())
        }

    private val clock = FixedClock(Instant.fromEpochMilliseconds(1_000))
    private val shared = StampedPreferenceStore(MemoryKeyValueStorage(), PeerRole.WATCH, StateMerger(clock), clock)
    private val local = FakeReminderPreferences(enabled = false)
    private var linked = true
    private val settings = SharedWatchReminderSettings(shared, local, { linked && it == "phone" }, clock)

    @Test
    fun `phone posted time is adopted even when the switch stays off`() =
        runTest {
            assertFalse(settings.adopt("phone", document(false, 900).copy(lastReminderAt = 900)))
            assertEquals(Instant.fromEpochMilliseconds(900), local.lastReminderAt())
            settings.adopt("phone", document(false, 950).copy(lastReminderAt = 800))
            assertEquals(Instant.fromEpochMilliseconds(900), local.lastReminderAt())
        }

    @Test
    fun `future peer time is clamped to now instead of blocking later days`() =
        runTest {
            settings.adopt("phone", document(false, 900).copy(lastReminderAt = 2_000))
            assertEquals(Instant.fromEpochMilliseconds(1_000), local.lastReminderAt())
        }

    @Test
    fun `fresh install remains off without creating a shared opt in`() =
        runTest {
            settings.initialize()
            assertFalse(local.enabled.value)
            assertFalse(SharedPreferenceKey.RemindersEnabled.name in shared.snapshot())
        }

    @Test
    fun `existing reminder choice migrates into shared preferences`() =
        runTest {
            local.setEnabled(true)
            settings.initialize()
            assertEquals(Stamped(JsonPrimitive(true), 1_000), shared.snapshot()["reminders.enabled"])
            assertTrue(local.enabled.value)
        }

    @Test
    fun `saved shared opt out wins over old local opt in`() =
        runTest {
            local.setEnabled(true)
            shared.write("reminders.enabled", JsonPrimitive(false))
            settings.initialize()
            assertFalse(local.enabled.value)
        }

    @Test
    fun `watch choice is written for the phone before publication`() =
        runTest {
            settings.persistChoice(true)
            assertEquals(Stamped(JsonPrimitive(true), 1_000), shared.snapshot()["reminders.enabled"])
        }

    @Test
    fun `current phone can turn watch reminders on and off`() =
        runTest {
            assertTrue(settings.adopt("phone", document(true, 900)))
            assertTrue(local.enabled.value)
            assertTrue(settings.adopt("phone", document(false, 950)))
            assertFalse(local.enabled.value)
        }

    @Test
    fun `foreign or unlinked phone cannot change reminders`() =
        runTest {
            assertFalse(settings.adopt("foreign", document(true, 900)))
            linked = false
            assertFalse(settings.adopt("phone", document(true, 900)))
            assertFalse(local.enabled.value)
            assertTrue(shared.snapshot().isEmpty())
        }

    @Test
    fun `new watch choice survives an old phone document`() =
        runTest {
            settings.persistChoice(true)
            local.setEnabled(true)
            assertFalse(settings.adopt("phone", document(false, 900)))
            assertTrue(local.enabled.value)
            assertEquals(JsonPrimitive(true), shared.snapshot()["reminders.enabled"]?.value)
        }

    private fun document(
        enabled: Boolean,
        at: Long,
    ) = StateDocument(
        role = PeerRole.PHONE,
        writtenAt = at,
        shared = mapOf("reminders.enabled" to Stamped(JsonPrimitive(enabled), at)),
    )
}
