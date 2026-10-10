package app.logdate.client.shortcuts

import kotlin.test.Test
import kotlin.test.assertEquals

class ShortcutSlotsTest {
    @Test
    fun `manifest and live shortcuts share the activity's limit with dynamic ones`() {
        // Android counts the three static capture shortcuts against the same limit.
        assertEquals(11, dynamicShortcutSlots(maxPerActivity = 15, manifestShortcuts = 3, liveShortcuts = 1))
    }

    @Test
    fun `there are never fewer than zero slots`() {
        assertEquals(0, dynamicShortcutSlots(maxPerActivity = 4, manifestShortcuts = 3, liveShortcuts = 2))
    }
}
