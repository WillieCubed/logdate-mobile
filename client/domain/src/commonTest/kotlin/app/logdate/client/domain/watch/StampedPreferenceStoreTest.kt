package app.logdate.client.domain.watch

import app.logdate.client.domain.recommendation.FakeKeyValueStorage
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.Stamped
import app.logdate.client.sync.peer.state.StateMerger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests [StampedPreferenceStore]: a local write is stamped and never goes back in time, and the other
 * device's entries are taken by the rule both devices apply to their state documents.
 */
class StampedPreferenceStoreTest {
    private val clock = MutableClock(NOW)
    private val storage = FakeKeyValueStorage()

    private fun store(role: PeerRole = PeerRole.PHONE) =
        StampedPreferenceStore(
            storage = storage,
            localRole = role,
            merger = StateMerger(clock),
            clock = clock,
        )

    @Test
    fun `a write is stamped with the current time`() =
        runTest {
            val store = store()

            val written = store.write(KEY, JsonPrimitive(true))

            assertEquals(Stamped(JsonPrimitive(true), NOW), written)
            assertEquals(mapOf(KEY to written), store.snapshot())
        }

    @Test
    fun `a write after the clock moved back is still the latest`() =
        runTest {
            val store = store()
            store.write(KEY, JsonPrimitive(true))
            clock.millis = NOW - 60_000

            val second = store.write(KEY, JsonPrimitive(false))

            assertEquals(NOW + 1, second.at)
        }

    @Test
    fun `a later entry from the watch is taken`() =
        runTest {
            val store = store()
            clock.millis = NOW - 10
            store.write(KEY, JsonPrimitive(false))
            clock.millis = NOW
            val fromWatch = Stamped(JsonPrimitive(true), NOW - 5)

            assertTrue(store.adopt(mapOf(KEY to fromWatch)))
            assertEquals(fromWatch, store.snapshot()[KEY])
        }

    @Test
    fun `an earlier entry from the watch changes nothing`() =
        runTest {
            val store = store()
            val local = store.write(KEY, JsonPrimitive(true))

            assertFalse(store.adopt(mapOf(KEY to Stamped(JsonPrimitive(false), NOW - 5))))
            assertEquals(local, store.snapshot()[KEY])
        }

    @Test
    fun `a tie goes to the phone on either device`() =
        runTest {
            val onPhone = store(PeerRole.PHONE)
            val phoneEntry = onPhone.write(KEY, JsonPrimitive(true))
            assertFalse(onPhone.adopt(mapOf(KEY to Stamped(JsonPrimitive(false), NOW))))
            assertEquals(phoneEntry, onPhone.snapshot()[KEY])

            storage.clear()
            val onWatch = store(PeerRole.WATCH)
            onWatch.write(KEY, JsonPrimitive(false))
            assertTrue(onWatch.adopt(mapOf(KEY to Stamped(JsonPrimitive(true), NOW))))
            assertEquals(JsonPrimitive(true), onWatch.snapshot()[KEY]?.value)
        }

    @Test
    fun `entries only the other device wrote are added`() =
        runTest {
            val store = store()
            store.write(KEY, JsonPrimitive(true))
            val fromWatch = Stamped(JsonPrimitive(30), NOW - 100)

            assertTrue(store.adopt(mapOf("window.days" to fromWatch)))
            assertEquals(setOf(KEY, "window.days"), store.snapshot().keys)
        }

    @Test
    fun `nothing from the other device changes nothing`() =
        runTest {
            assertFalse(store().adopt(emptyMap()))
        }

    @Test
    fun `stored preferences that cannot be read count as none`() =
        runTest {
            storage.putString(StampedPreferenceStore.STORAGE_KEY, "not json")

            assertEquals(emptyMap(), store().snapshot())
        }

    @Test
    fun `observers see each write`() =
        runTest {
            val store = store()

            store.write(KEY, JsonPrimitive(true))

            assertEquals(mapOf(KEY to Stamped(JsonPrimitive(true), NOW)), store.observe().first())
        }

    private class MutableClock(
        var millis: Long,
    ) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
    }

    private companion object {
        const val NOW = 1_760_000_000_000
        const val KEY = "reminders.enabled"
    }
}
