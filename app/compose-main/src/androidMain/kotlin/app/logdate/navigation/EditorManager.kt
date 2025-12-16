package app.logdate.navigation

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import app.logdate.client.EditorActivity
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow

/**
 * Manager class for creating and handling multiple editor windows.
 * 
 * This class provides a central point for launching editor windows and
 * managing multi-window capabilities.
 */
class EditorManager(private val context: Context) {
    
    /**
     * Checks if multiple editor windows are supported on this device.
     * 
     * @return true if multiple windows are supported, false otherwise
     */
    fun supportsMultiWindow(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    }
    
    /**
     * Opens a new editor window with optional initial content.
     * 
     * @param initialText Optional initial text content for the editor
     * @param attachments Optional list of attachment URIs
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
     * Gets window layout information for a given activity.
     * This is useful for adapting the UI based on the window state.
     * 
     * @param activity The activity to get window info for
     * @return Flow of WindowLayoutInfo that updates when window layout changes
     */
    fun getWindowLayoutInfo(activity: Activity): Flow<WindowLayoutInfo> {
        return WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
    }
}