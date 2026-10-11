package app.logdate.wear.reminders

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class WatchReminderPreferencesTest {
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
