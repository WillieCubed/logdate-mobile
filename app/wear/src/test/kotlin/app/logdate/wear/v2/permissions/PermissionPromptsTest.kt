package app.logdate.wear.v2.permissions

import android.Manifest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionPromptsTest {
    private val granted = mutableSetOf<WatchPermission>()
    private val store = InMemoryPromptStore()
    private val prompts = PermissionPrompts(checker = { it in granted }, store = store)

    @Test
    fun `an optional permission that is missing is offered once`() {
        assertTrue(prompts.takeOptionalPrompt(WatchPermission.NOTIFICATIONS))
        assertFalse(prompts.takeOptionalPrompt(WatchPermission.NOTIFICATIONS))
        assertTrue(store.hasAsked(WatchPermission.NOTIFICATIONS))
    }

    @Test
    fun `a granted permission is never offered`() {
        granted += WatchPermission.NOTIFICATIONS

        assertFalse(prompts.takeOptionalPrompt(WatchPermission.NOTIFICATIONS))
        assertFalse(store.hasAsked(WatchPermission.NOTIFICATIONS))
    }

    @Test
    fun `each permission is offered on its own`() {
        assertTrue(prompts.takeOptionalPrompt(WatchPermission.NOTIFICATIONS))
        assertTrue(prompts.takeOptionalPrompt(WatchPermission.MICROPHONE))
    }

    @Test
    fun `granted follows the checker`() {
        assertFalse(prompts.isGranted(WatchPermission.MICROPHONE))
        granted += WatchPermission.MICROPHONE
        assertTrue(prompts.isGranted(WatchPermission.MICROPHONE))
    }

    @Test
    fun `a refusal the system will ask about again is a denial`() {
        assertEquals(PermissionResult.GRANTED, permissionResult(granted = true, systemWillAskAgain = false))
        assertEquals(PermissionResult.DENIED, permissionResult(granted = false, systemWillAskAgain = true))
        assertEquals(PermissionResult.BLOCKED, permissionResult(granted = false, systemWillAskAgain = false))
    }

    @Test
    fun `each permission has its system name`() {
        assertEquals(Manifest.permission.RECORD_AUDIO, WatchPermission.MICROPHONE.manifestName())
        assertEquals(Manifest.permission.POST_NOTIFICATIONS, WatchPermission.NOTIFICATIONS.manifestName())
    }

    private class InMemoryPromptStore : PermissionPromptStore {
        private val asked = mutableSetOf<WatchPermission>()

        override fun hasAsked(permission: WatchPermission): Boolean = permission in asked

        override fun markAsked(permission: WatchPermission) {
            asked += permission
        }
    }
}
