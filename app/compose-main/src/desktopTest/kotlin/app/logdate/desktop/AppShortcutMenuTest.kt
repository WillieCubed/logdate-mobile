package app.logdate.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import app.logdate.ui.keyboard.KeyChord
import kotlin.test.Test
import kotlin.test.assertEquals

class AppShortcutMenuTest {
    @Test
    fun `the primary modifier is Command on macOS`() {
        assertEquals(KeyShortcut(Key.N, meta = true), KeyChord(Key.N, primary = true).toKeyShortcut(isMac = true))
    }

    @Test
    fun `the primary modifier is Ctrl on Windows and Linux`() {
        assertEquals(KeyShortcut(Key.N, ctrl = true), KeyChord(Key.N, primary = true).toKeyShortcut(isMac = false))
    }

    @Test
    fun `shift carries over`() {
        assertEquals(
            KeyShortcut(Key.R, meta = true, shift = true),
            KeyChord(Key.R, primary = true, shift = true).toKeyShortcut(isMac = true),
        )
    }
}
