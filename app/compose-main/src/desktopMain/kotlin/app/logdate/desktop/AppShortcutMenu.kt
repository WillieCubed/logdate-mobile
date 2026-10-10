@file:Suppress("ktlint:standard:function-naming")

package app.logdate.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.MenuScope
import app.logdate.ui.keyboard.AppKeyboardShortcut
import app.logdate.ui.keyboard.KeyChord
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skiko.hostOs

/** This chord as a menu accelerator: the primary modifier is Command on macOS and Ctrl elsewhere. */
internal fun KeyChord.toKeyShortcut(isMac: Boolean = hostOs.isMacOS): KeyShortcut =
    KeyShortcut(key, ctrl = primary && !isMac, meta = primary && isMac, shift = shift)

/**
 * One menu item per shortcut in [shortcuts], labeled and bound to its keys from the shared
 * [AppKeyboardShortcut] catalog, so the menu bar, the key handlers, and Android's shortcut helper
 * always name the same keys.
 */
@Composable
internal fun MenuScope.ShortcutItems(
    shortcuts: List<AppKeyboardShortcut>,
    onClick: (AppKeyboardShortcut) -> Unit,
) {
    shortcuts.forEach { shortcut ->
        Item(
            text = stringResource(shortcut.label),
            shortcut = shortcut.chord.toKeyShortcut(),
            onClick = { onClick(shortcut) },
        )
    }
}
