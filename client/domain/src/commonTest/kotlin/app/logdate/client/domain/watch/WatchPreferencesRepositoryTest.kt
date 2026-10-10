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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests [DefaultWatchPreferencesRepository]: typed reads with defaults, validated writes, and values
 * from another device or app version that this one cannot use reading as the default.
 */
class WatchPreferencesRepositoryTest {
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(NOW)
        }
    private val store = StampedPreferenceStore(FakeKeyValueStorage(), PeerRole.PHONE, StateMerger(clock), clock)
    private val repository = DefaultWatchPreferencesRepository(store)

    @Test
    fun `an unset preference reads as its default`() =
        runTest {
            assertEquals(14, repository.get(SharedPreferenceKey.WindowDays))
            assertFalse(repository.get(SharedPreferenceKey.CapturePlaces))
            assertFalse(repository.get(SharedPreferenceKey.RemindersEnabled))
            assertTrue(repository.get(SharedPreferenceKey.MoodAfterRecording))
            assertTrue(repository.get(SharedPreferenceKey.WindowOnThisDay))
            assertTrue(repository.get(SharedPreferenceKey.WindowRewind))
        }

    @Test
    fun `a preference reads back after it is set`() =
        runTest {
            repository.set(SharedPreferenceKey.WindowDays, 30)

            assertEquals(30, repository.get(SharedPreferenceKey.WindowDays))
            assertEquals(30, repository.observe(SharedPreferenceKey.WindowDays).first())
        }

    @Test
    fun `a library length outside the choices is refused`() =
        runTest {
            assertFailsWith<IllegalArgumentException> { repository.set(SharedPreferenceKey.WindowDays, 10) }
            assertEquals(14, repository.get(SharedPreferenceKey.WindowDays))
        }

    @Test
    fun `a value this version cannot read is the default`() =
        runTest {
            store.adopt(
                mapOf(
                    "window.days" to Stamped(JsonPrimitive("forever"), NOW),
                    "capture.places" to Stamped(JsonPrimitive(3), NOW),
                ),
            )

            assertEquals(14, repository.get(SharedPreferenceKey.WindowDays))
            assertFalse(repository.get(SharedPreferenceKey.CapturePlaces))
        }

    @Test
    fun `a library length outside the choices from another device is the default`() =
        runTest {
            store.adopt(mapOf("window.days" to Stamped(JsonPrimitive(21), NOW)))

            assertEquals(14, repository.get(SharedPreferenceKey.WindowDays))
        }

    @Test
    fun `a choice made on the watch reaches observers`() =
        runTest {
            store.adopt(mapOf("capture.places" to Stamped(JsonPrimitive(true), NOW)))

            assertTrue(repository.observe(SharedPreferenceKey.CapturePlaces).first())
        }

    @Test
    fun `every key has its own name`() {
        val names = SharedPreferenceKey.ALL.map { it.name }

        assertEquals(names.size, names.toSet().size)
    }

    private companion object {
        const val NOW = 1_760_000_000_000
    }
}
