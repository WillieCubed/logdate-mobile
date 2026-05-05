package app.logdate.client.editor

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
    fun supportsMultiWindow(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

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
            context.startActivity(intent)
            Napier.d("Launched new editor window")
        } catch (e: Exception) {
            Napier.e("Failed to open new editor window", e)
        }
    }

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

    fun getWindowLayoutInfo(activity: Activity): Flow<WindowLayoutInfo> =
        WindowInfoTracker
            .getOrCreate(activity)
            .windowLayoutInfo(activity)
}
