package app.logdate.wear.reminders

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class WatchReminderPreferencesTest {
    @Test
    fun `future phone replay after restart retains its first observed time`() {
        every { editor.commit() } returns true
        val store = SharedPreferencesWatchReminderPreferences(preferences)
        store.mergePhoneReminderAt("phone", 2_000, Instant.fromEpochMilliseconds(1_000))
        assertEquals(Instant.fromEpochMilliseconds(1_000), store.lastReminderAt())
        val restarted = SharedPreferencesWatchReminderPreferences(preferences)
        restarted.mergePhoneReminderAt("phone", 2_000, Instant.fromEpochMilliseconds(1_500))
        assertEquals(Instant.fromEpochMilliseconds(1_000), restarted.lastReminderAt())
        restarted.mergePhoneReminderAt("phone", 3_000, Instant.fromEpochMilliseconds(2_500))
        assertEquals(Instant.fromEpochMilliseconds(2_500), restarted.lastReminderAt())
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun `posting a reminder updates the observed timestamp`() =
        runTest {
            val store = SharedPreferencesWatchReminderPreferences(preferences)
            val observed = mutableListOf<Instant?>()
            backgroundScope.launch { store.observeLastReminderAt().take(2).toList(observed) }
            runCurrent()
            val at = Instant.fromEpochMilliseconds(1_000)
            store.setLastReminderAt(at)
            runCurrent()
            assertEquals(listOf(null, at), observed)
        }

    @Test
    fun `a backwards clock cannot move the last reminder earlier`() {
        val store = SharedPreferencesWatchReminderPreferences(preferences)
        store.setLastReminderAt(Instant.fromEpochMilliseconds(2_000))
        store.setLastReminderAt(Instant.fromEpochMilliseconds(1_000))
        assertEquals(Instant.fromEpochMilliseconds(2_000), store.lastReminderAt())
    }

    private val saved = mutableMapOf<String, Any>()
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val preferences =
        mockk<SharedPreferences> {
            every { edit() } returns editor
            every { getBoolean(any(), any()) } answers { saved[firstArg<String>()] as? Boolean ?: secondArg() }
            every { getLong(any(), any()) } answers { saved[firstArg<String>()] as? Long ?: secondArg() }
        }

    init {
        every { editor.putBoolean(any(), any()) } answers {
            saved[firstArg()] = secondArg<Boolean>()
            editor
        }
        every { editor.putLong(any(), any()) } answers {
            saved[firstArg()] = secondArg<Long>()
            editor
        }
    }

    @Test
    fun `reminders start off with none sent`() {
        val store = SharedPreferencesWatchReminderPreferences(preferences)

        assertEquals(false, store.enabled.value)
        assertNull(store.lastReminderAt())
    }

    @Test
    fun `turning reminders on saves it and reports it at once`() {
        val store = SharedPreferencesWatchReminderPreferences(preferences)

        store.setEnabled(true)

        verify { editor.putBoolean("enabled", true) }
        assertEquals(true, store.enabled.value)
        assertEquals(true, SharedPreferencesWatchReminderPreferences(preferences).enabled.value)
    }

    @Test
    fun `the last reminder time is saved and read back`() {
        val store = SharedPreferencesWatchReminderPreferences(preferences)

        store.setLastReminderAt(Instant.fromEpochMilliseconds(1_792_000_000_000L))

        verify { editor.putLong("last_reminder_at", 1_792_000_000_000L) }
        assertEquals(
            Instant.fromEpochMilliseconds(1_792_000_000_000L),
            SharedPreferencesWatchReminderPreferences(preferences).lastReminderAt(),
        )
    }
}
