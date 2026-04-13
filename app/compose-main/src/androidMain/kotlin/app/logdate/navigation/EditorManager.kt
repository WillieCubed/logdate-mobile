package app.logdate.navigation

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import app.logdate.client.EditorActivity
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Manager class for creating and handling multiple editor windows.
 *
 * This class provides a central point for launching editor windows and
 * managing multi-window capabilities.
 */
class EditorManager(
    private val context: Context,
) {
    /**
     * Checks if multiple editor windows are supported on this device.
     *
     * Multi-window support requires Android N (API 24) or higher. This method should be called
     * before attempting to open multiple editor windows to ensure the device supports this feature.
     *
     * @return true if the device supports multiple windows (Android N+), false otherwise
     */
    fun supportsMultiWindow(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    /**
     * Opens a new editor window for creating a new entry.
     *
     * This launches a new instance of EditorActivity as a separate window task. The new window
     * is independent and can be managed separately on devices that support multi-window mode.
     * Initial content can be optionally provided (e.g., from a Share intent or template).
     *
     * @param initialText Optional initial text content to populate the new entry with
     * @param attachments Optional list of attachment URIs (image, audio, or video) to add to the new entry
     * @param journalIds Optional journals to pre-select for the new entry. Used when a sharing
     *   shortcut targets a specific journal so the user doesn't have to pick one inside the editor.
     */
    fun openNewEditorWindow(
        initialText: String? = null,
        attachments: List<String>? = null,
        journalIds: List<Uuid> = emptyList(),
    ) {
        try {
            val intent =
                EditorActivity.createIntent(
                    context = context,
                    initialText = initialText,
                    attachments = attachments,
                    journalIds = journalIds,
                )

            // Launch the activity as a new document
            context.startActivity(intent)
            Napier.d("Launched new editor window")
        } catch (e: Exception) {
            Napier.e("Failed to open new editor window", e)
        }
    }

    /**
     * Opens an existing entry in a new editor window.
     *
     * This is used for multi-window editing on large screens or devices that support split-screen mode.
     * When the user selects "Open in New Window" from a timeline entry's context menu, this method
     * is called to launch a separate EditorActivity instance with that specific entry loaded for editing.
     *
     * @param entryId The unique identifier of the entry to load and edit in the new window
     * @param journalId Optional journal ID providing context for the entry (used if entry has no journal association)
     */
    fun openEntryInNewWindow(
        entryId: Uuid,
        journalId: Uuid? = null,
    ) {
        try {
            val intent =
                EditorActivity.createIntent(
                    context = context,
                    entryId = entryId,
                    journalId = journalId,
                )

            context.startActivity(intent)
            Napier.d("Opened entry $entryId in new window")
        } catch (e: Exception) {
            Napier.e("Failed to open entry in new window", e)
        }
    }

    /**
     * Gets window layout information for a given activity.
     *
     * Returns a Flow of WindowLayoutInfo that emits whenever the window layout changes.
     * This is useful for adapting the editor UI based on window state (e.g., when the device
     * is folded/unfolded, rotated, or enters/exits split-screen mode). The Flow will continue
     * to emit updates for the lifetime of the activity.
     *
     * @param activity The activity to monitor for window layout changes
     * @return A Flow that emits WindowLayoutInfo whenever the layout changes
     */
    fun getWindowLayoutInfo(activity: Activity): Flow<WindowLayoutInfo> =
        WindowInfoTracker
            .getOrCreate(activity)
            .windowLayoutInfo(activity)
}
