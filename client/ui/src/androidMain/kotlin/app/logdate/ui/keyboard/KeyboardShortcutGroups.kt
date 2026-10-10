package app.logdate.ui.keyboard

import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import androidx.compose.ui.input.key.nativeKeyCode
import kotlinx.coroutines.runBlocking
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.keyboard_shortcuts_app_group
import logdate.client.ui.generated.resources.keyboard_shortcuts_editor_group
import logdate.client.ui.generated.resources.keyboard_shortcuts_home_group
import org.jetbrains.compose.resources.getString

/**
 * LogDate's shortcuts as the groups Android lists in its keyboard shortcut helper (Meta+/).
 *
 * Call from `Activity.onProvideKeyboardShortcuts`. Labels come from the same catalog as the Compose
 * key handlers, so the helper always lists what actually works.
 */
fun appKeyboardShortcutGroups(scopes: List<AppShortcutScope>): List<KeyboardShortcutGroup> =
    runBlocking {
        scopes.map { scope ->
            KeyboardShortcutGroup(
                getString(
                    when (scope) {
                        AppShortcutScope.App -> Res.string.keyboard_shortcuts_app_group
                        AppShortcutScope.Editor -> Res.string.keyboard_shortcuts_editor_group
                        AppShortcutScope.Home -> Res.string.keyboard_shortcuts_home_group
                    },
                ),
                AppKeyboardShortcut.inScope(scope).map { shortcut ->
                    KeyboardShortcutInfo(getString(shortcut.label), shortcut.chord.key.nativeKeyCode, shortcut.chord.androidModifiers())
                },
            )
        }
    }

private fun KeyChord.androidModifiers(): Int = (if (primary) KeyEvent.META_CTRL_ON else 0) or (if (shift) KeyEvent.META_SHIFT_ON else 0)
