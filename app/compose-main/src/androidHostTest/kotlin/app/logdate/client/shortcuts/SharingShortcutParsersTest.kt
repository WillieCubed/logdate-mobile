package app.logdate.client.shortcuts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * Tests the parsing logic for Android Launcher Shortcuts.
 *
 * This class ensures that data extracted from shortcut intents—specifically journal
 * identifiers—is correctly converted into strongly-typed domain objects (UUIDs).
 * It verifies both successful parsing of valid IDs and graceful handling of
 * malformed input.
 */
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
