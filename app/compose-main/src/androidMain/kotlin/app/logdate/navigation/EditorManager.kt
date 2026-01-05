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
class EditorManager(private val context: Context) {
    
    /**
     * Checks if multiple editor windows are supported on this device (Android N+).
     */
    fun supportsMultiWindow(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    }
    
    /**
     * Opens a new editor window with optional initial content for a new entry.
     */
    fun openNewEditorWindow(
        initialText: String? = null,
        attachments: List<String>? = null
    ) {
        try {
            val intent = EditorActivity.createIntent(
                context = context,
                initialText = initialText,
                attachments = attachments
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
     */
    fun openEntryInNewWindow(
        entryId: Uuid,
        journalId: Uuid? = null
    ) {
        try {
            val intent = EditorActivity.createIntent(
                context = context,
                entryId = entryId,
                journalId = journalId
            )

            context.startActivity(intent)
            Napier.d("Opened entry $entryId in new window")

        } catch (e: Exception) {
            Napier.e("Failed to open entry in new window", e)
        }
    }

    /**
     * Gets window layout information for a given activity, useful for adapting the UI
     * based on window state or screen layout changes (e.g., foldable devices).
     */
    fun getWindowLayoutInfo(activity: Activity): Flow<WindowLayoutInfo> {
        return WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
    }
}