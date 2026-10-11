package app.logdate.ui.keyboard

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppKeyboardShortcutsTest {
    @Test
    fun `no two shortcuts in a scope share a chord`() {
        AppShortcutScope.entries.forEach { scope ->
            val chords = AppKeyboardShortcut.inScope(scope).map { it.chord }
            assertEquals(chords.size, chords.toSet().size, "duplicate chord in $scope")
        }
    }

    @Test
    fun `primary-modifier chords match with either control or command`() {
        val chord = KeyChord(Key.N, primary = true)
        assertEquals(AppKeyboardShortcut.NewEntry, AppKeyboardShortcut.match(chord, AppShortcutScope.App))
        assertEquals(AppKeyboardShortcut.NewEntry, AppKeyboardShortcut.match(KeyChord(Key.N, primary = true), AppShortcutScope.App))
    }

    @Test
    fun `shift distinguishes recording from other chords`() {
        assertEquals(
            AppKeyboardShortcut.RecordVoice,
            AppKeyboardShortcut.match(KeyChord(Key.R, primary = true, shift = true), AppShortcutScope.App),
        )
        assertNull(AppKeyboardShortcut.match(KeyChord(Key.R, primary = true), AppShortcutScope.App))
    }

    @Test
    fun `a letter without the primary modifier is never a shortcut`() {
        assertNull(AppKeyboardShortcut.match(KeyChord(Key.N), AppShortcutScope.App))
        assertNull(AppKeyboardShortcut.match(KeyChord(Key.F), AppShortcutScope.App))
    }

    @Test
    fun `editor shortcuts only match in the editor`() {
        assertEquals(AppKeyboardShortcut.SaveEntry, AppKeyboardShortcut.match(KeyChord(Key.S, primary = true), AppShortcutScope.Editor))
        assertNull(AppKeyboardShortcut.match(KeyChord(Key.S, primary = true), AppShortcutScope.App))
        assertEquals(AppKeyboardShortcut.CloseEditor, AppKeyboardShortcut.match(KeyChord(Key.Escape), AppShortcutScope.Editor))
    }

    @Test
    fun `primary plus a digit opens that Home tab only on Home`() {
        val tabs =
            listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five).map { key ->
                AppKeyboardShortcut.match(KeyChord(key, primary = true), AppShortcutScope.Home)?.homeTabIndex
            }

        assertEquals(listOf(0, 1, 2, 3, 4), tabs)
        assertEquals(null, AppKeyboardShortcut.match(KeyChord(Key.One, primary = true), AppShortcutScope.App))
        assertEquals(null, AppKeyboardShortcut.NewEntry.homeTabIndex)
    }
}
