package app.logdate.navigation

import androidx.navigation3.runtime.NavKey
import app.logdate.client.ui.navigation.SearchRoute
import app.logdate.feature.core.settings.navigation.SettingsRoute
import app.logdate.feature.editor.navigation.EditorLaunchMode
import app.logdate.feature.editor.navigation.EntryEditorRoute
import app.logdate.ui.keyboard.AppKeyboardShortcut
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * The screen an app-wide shortcut opens, or null for a shortcut that works only where it is scoped
 * (the editor, or Home's tabs).
 */
fun AppKeyboardShortcut.appRoute(): NavKey? =
    when (this) {
        AppKeyboardShortcut.NewEntry -> EntryEditorRoute()
        AppKeyboardShortcut.RecordVoice -> EntryEditorRoute(launchMode = EditorLaunchMode.Voice)
        AppKeyboardShortcut.Search -> SearchRoute()
        AppKeyboardShortcut.Settings -> SettingsRoute()
        else -> null
    }

/**
 * App-wide shortcuts chosen somewhere other than the keyboard, such as the desktop menu bar.
 *
 * [LogDateNavDisplay] opens each request the same way it opens the shortcut's keys, including
 * ignoring it while LogDate is locked or not yet set up, so a menu item and its keys always agree.
 */
class AppShortcutRequests {
    private val pending = MutableSharedFlow<AppKeyboardShortcut>(extraBufferCapacity = 8)

    /** The shortcuts requested, in order, for [LogDateNavDisplay] to open. */
    val requests: Flow<AppKeyboardShortcut> = pending

    /** Asks for [shortcut]'s screen to open. */
    fun request(shortcut: AppKeyboardShortcut) {
        pending.tryEmit(shortcut)
    }
}
