package app.logdate.ui.keyboard

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.keyboard_shortcut_close_editor
import logdate.client.ui.generated.resources.keyboard_shortcut_home_tab_1
import logdate.client.ui.generated.resources.keyboard_shortcut_home_tab_2
import logdate.client.ui.generated.resources.keyboard_shortcut_home_tab_3
import logdate.client.ui.generated.resources.keyboard_shortcut_home_tab_4
import logdate.client.ui.generated.resources.keyboard_shortcut_home_tab_5
import logdate.client.ui.generated.resources.keyboard_shortcut_new_entry
import logdate.client.ui.generated.resources.keyboard_shortcut_record_voice
import logdate.client.ui.generated.resources.keyboard_shortcut_save_entry
import logdate.client.ui.generated.resources.keyboard_shortcut_search
import logdate.client.ui.generated.resources.keyboard_shortcut_settings
import org.jetbrains.compose.resources.StringResource

/** Where a keyboard shortcut works. */
enum class AppShortcutScope {
    /** Anywhere in the app. */
    App,

    /** Only while an entry is open in the editor. */
    Editor,

    /** Only on Home, where it switches between destinations. */
    Home,
}

/**
 * A key plus modifiers.
 *
 * @property primary Whether the platform's primary modifier is held: Ctrl on Android, ChromeOS, and
 *   Windows; Command on macOS. Either key satisfies it, so one catalog works everywhere.
 */
data class KeyChord(
    val key: Key,
    val primary: Boolean = false,
    val shift: Boolean = false,
)

/**
 * Every keyboard shortcut in LogDate, defined once.
 *
 * The Compose key handlers, Android's keyboard shortcut helper (Meta+/), and the desktop menu bar all
 * read this list, so a shortcut cannot work in one place and be missing from the others.
 */
enum class AppKeyboardShortcut(
    val chord: KeyChord,
    val scope: AppShortcutScope,
    val label: StringResource,
) {
    NewEntry(KeyChord(Key.N, primary = true), AppShortcutScope.App, Res.string.keyboard_shortcut_new_entry),
    RecordVoice(KeyChord(Key.R, primary = true, shift = true), AppShortcutScope.App, Res.string.keyboard_shortcut_record_voice),
    Search(KeyChord(Key.F, primary = true), AppShortcutScope.App, Res.string.keyboard_shortcut_search),
    Settings(KeyChord(Key.Comma, primary = true), AppShortcutScope.App, Res.string.keyboard_shortcut_settings),
    SaveEntry(KeyChord(Key.S, primary = true), AppShortcutScope.Editor, Res.string.keyboard_shortcut_save_entry),
    CloseEditor(KeyChord(Key.Escape), AppShortcutScope.Editor, Res.string.keyboard_shortcut_close_editor),
    HomeTab1(KeyChord(Key.One, primary = true), AppShortcutScope.Home, Res.string.keyboard_shortcut_home_tab_1),
    HomeTab2(KeyChord(Key.Two, primary = true), AppShortcutScope.Home, Res.string.keyboard_shortcut_home_tab_2),
    HomeTab3(KeyChord(Key.Three, primary = true), AppShortcutScope.Home, Res.string.keyboard_shortcut_home_tab_3),
    HomeTab4(KeyChord(Key.Four, primary = true), AppShortcutScope.Home, Res.string.keyboard_shortcut_home_tab_4),
    HomeTab5(KeyChord(Key.Five, primary = true), AppShortcutScope.Home, Res.string.keyboard_shortcut_home_tab_5),
    ;

    /** For a Home tab shortcut, the zero-based position of the tab it opens; otherwise null. */
    val homeTabIndex: Int?
        get() =
            when (this) {
                HomeTab1 -> 0
                HomeTab2 -> 1
                HomeTab3 -> 2
                HomeTab4 -> 3
                HomeTab5 -> 4
                else -> null
            }

    companion object {
        fun inScope(scope: AppShortcutScope): List<AppKeyboardShortcut> = entries.filter { it.scope == scope }

        /** The shortcut in [scope] that [chord] triggers, if any. */
        fun match(
            chord: KeyChord,
            scope: AppShortcutScope,
        ): AppKeyboardShortcut? = inScope(scope).firstOrNull { it.chord == chord }

        /** The shortcut in [scope] that a key-down [event] triggers, if any. */
        fun match(
            event: KeyEvent,
            scope: AppShortcutScope,
        ): AppKeyboardShortcut? {
            if (event.type != KeyEventType.KeyDown) return null
            val chord = KeyChord(event.key, primary = event.isCtrlPressed || event.isMetaPressed, shift = event.isShiftPressed)
            return match(chord, scope)
        }
    }
}
