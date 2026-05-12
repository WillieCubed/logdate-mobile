package app.logdate.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
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

    /**
     * Reads the saved main-window position, or returns [WindowPosition.PlatformDefault] when
     * nothing has been persisted yet. Stored as floating-point dp so the JVM-side preferences
     * store doesn't need to know about Compose's [WindowPosition] sealed type.
     */
    fun readMainWindowPosition(): WindowPosition {
        if (!store.getBoolean(KEY_MAIN_POSITION_SET, false)) {
            return WindowPosition.PlatformDefault
        }
        val x = store.getFloat(KEY_MAIN_X, 0f)
        val y = store.getFloat(KEY_MAIN_Y, 0f)
        return WindowPosition.Absolute(x.dp, y.dp)
    }

    fun writeMainWindowPosition(position: WindowPosition) {
        // PlatformDefault / Aligned positions can't be persisted as absolute coordinates;
        // they recompute every launch. Skip those rather than writing a misleading 0,0.
        if (position !is WindowPosition.Absolute) return
        store.putFloat(KEY_MAIN_X, position.x.value)
        store.putFloat(KEY_MAIN_Y, position.y.value)
        store.putBoolean(KEY_MAIN_POSITION_SET, true)
        store.flush()
    }

    companion object {
        private const val NODE_PATH = "app/logdate/desktop/window"
        private const val KEY_MAIN_WIDTH = "main.width.dp"
        private const val KEY_MAIN_HEIGHT = "main.height.dp"
        private const val KEY_MAIN_X = "main.x.dp"
        private const val KEY_MAIN_Y = "main.y.dp"
        private const val KEY_MAIN_POSITION_SET = "main.position.set"
    }
}
