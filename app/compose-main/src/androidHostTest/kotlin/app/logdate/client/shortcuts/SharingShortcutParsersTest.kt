package app.logdate.client.shortcuts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class SharingShortcutParsersTest {
    @Test
    fun `parseLauncherShortcutTargetJournalId returns parsed uuid when raw value is present`() {
        val journalId = Uuid.random()

        assertEquals(journalId, parseLauncherShortcutTargetJournalId(journalId.toString()))
    }

    @Test
    fun `parseLauncherShortcutTargetJournalId returns null when raw value is invalid`() {
        assertNull(parseLauncherShortcutTargetJournalId("not-a-uuid"))
    }
}
