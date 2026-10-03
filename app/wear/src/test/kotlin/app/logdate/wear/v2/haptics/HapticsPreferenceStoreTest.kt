package app.logdate.wear.v2.haptics

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import kotlin.test.assertEquals

class HapticsPreferenceStoreTest {
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val preferences = mockk<SharedPreferences> { every { edit() } returns editor }

    @Test
    fun `nothing saved means All`() {
        every { preferences.getString("haptics", null) } returns null

        assertEquals(HapticsPreference.ALL, store().preference.value)
    }

    @Test
    fun `a saved Essential is read back`() {
        every { preferences.getString("haptics", null) } returns "essential"

        assertEquals(HapticsPreference.ESSENTIAL, store().preference.value)
    }

    @Test
    fun `a value a later version saved means All`() {
        every { preferences.getString("haptics", null) } returns "loud"

        assertEquals(HapticsPreference.ALL, store().preference.value)
    }

    @Test
    fun `setting Essential saves it and reports it at once`() {
        every { preferences.getString("haptics", null) } returns null
        val store = store()

        store.set(HapticsPreference.ESSENTIAL)

        verify { editor.putString("haptics", "essential") }
        verify { editor.apply() }
        assertEquals(HapticsPreference.ESSENTIAL, store.preference.value)
    }

    private fun store() = SharedPreferencesHapticsPreferenceStore(preferences)
}
