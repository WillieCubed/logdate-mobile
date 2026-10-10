package app.logdate.navigation

import app.logdate.feature.editor.navigation.EditorLaunchMode
import app.logdate.feature.editor.navigation.EntryEditorRoute
import app.logdate.ui.keyboard.AppKeyboardShortcut
import app.logdate.ui.keyboard.AppShortcutScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AppShortcutRouteTest {
    @Test
    fun `every app-wide shortcut opens a screen`() {
        AppKeyboardShortcut.inScope(AppShortcutScope.App).forEach { shortcut ->
            assertNotNull(shortcut.appRoute(), "$shortcut opens nothing")
        }
    }

    @Test
    fun `shortcuts that only work in the editor or on Home open no screen of their own`() {
        (AppKeyboardShortcut.inScope(AppShortcutScope.Editor) + AppKeyboardShortcut.inScope(AppShortcutScope.Home)).forEach { shortcut ->
            assertNull(shortcut.appRoute(), "$shortcut should be handled where it is scoped")
        }
    }

    @Test
    fun `record voice opens the editor already recording`() {
        assertEquals(EntryEditorRoute(launchMode = EditorLaunchMode.Voice), AppKeyboardShortcut.RecordVoice.appRoute())
    }
}
