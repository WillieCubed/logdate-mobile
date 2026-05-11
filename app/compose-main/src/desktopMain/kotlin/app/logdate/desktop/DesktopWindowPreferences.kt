package app.logdate.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.util.prefs.Preferences

/**
 * Desktop-local window state persisted across launches.
 *
 * Backed by `java.util.prefs.Preferences` so the JVM owns the storage path per
 * platform (macOS uses `~/Library/Preferences`, Linux uses `~/.java/.userPrefs`,
 * Windows uses the registry). Keeps desktop-only window geometry out of the
 * cross-platform DataStore.
 */
internal class DesktopWindowPreferences(
    private val store: Preferences = Preferences.userRoot().node(NODE_PATH),
) {
    fun readMainWindowSize(default: DpSize): DpSize {
        val width = store.getFloat(KEY_MAIN_WIDTH, default.width.value)
        val height = store.getFloat(KEY_MAIN_HEIGHT, default.height.value)
        return DpSize(width.dp, height.dp)
    }

    fun writeMainWindowSize(size: DpSize) {
        store.putFloat(KEY_MAIN_WIDTH, size.width.value)
        store.putFloat(KEY_MAIN_HEIGHT, size.height.value)
        store.flush()
    }

    companion object {
        private const val NODE_PATH = "app/logdate/desktop/window"
        private const val KEY_MAIN_WIDTH = "main.width.dp"
        private const val KEY_MAIN_HEIGHT = "main.height.dp"
    }
}
