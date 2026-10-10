package app.logdate.wear.v2.permissions

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedPreferencesPermissionPromptStoreTest {
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val preferences = mockk<SharedPreferences> { every { edit() } returns editor }
    private val store = SharedPreferencesPermissionPromptStore(preferences)

    @Test
    fun `nothing saved means not asked`() {
        every { preferences.getBoolean("asked_notifications", false) } returns false

        assertFalse(store.hasAsked(WatchPermission.NOTIFICATIONS))
    }

    @Test
    fun `a saved ask is read back`() {
        every { preferences.getBoolean("asked_notifications", false) } returns true

        assertTrue(store.hasAsked(WatchPermission.NOTIFICATIONS))
    }

    @Test
    fun `marking a permission asked saves it`() {
        store.markAsked(WatchPermission.MICROPHONE)

        verify { editor.putBoolean("asked_microphone", true) }
        verify { editor.apply() }
    }
}
