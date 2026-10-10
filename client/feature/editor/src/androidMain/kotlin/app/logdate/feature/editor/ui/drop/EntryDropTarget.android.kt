package app.logdate.feature.editor.ui.drop

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.platform.LocalContext
import app.logdate.client.media.MediaManager
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
actual fun Modifier.entryDropTarget(
    onText: (String) -> Unit,
    onMediaImported: (List<String>) -> Unit,
    onActiveChanged: (Boolean) -> Unit,
): Modifier {
    val context = LocalContext.current
    val mediaManager: MediaManager = koinInject()
    val scope = rememberCoroutineScope()
    val target =
        remember(context, mediaManager) {
            object : DragAndDropTarget {
                override fun onEntered(event: DragAndDropEvent) = onActiveChanged(true)

                override fun onExited(event: DragAndDropEvent) = onActiveChanged(false)

                override fun onEnded(event: DragAndDropEvent) = onActiveChanged(false)

                override fun onDrop(event: DragAndDropEvent): Boolean {
                    onActiveChanged(false)
                    val dragEvent = event.toAndroidDragEvent()
                    val clip = dragEvent.clipData ?: return false
                    // Reading another app's file, even its type, needs this permission, and it ends
                    // when released.
                    val hasFiles = (0 until clip.itemCount).any { clip.getItemAt(it).uri != null }
                    val permissions = if (hasFiles) context.findActivity()?.requestDragAndDropPermissions(dragEvent) else null
                    val drop = routeDroppedItems(clip.droppedItems(context))
                    drop.texts.forEach(onText)
                    if (drop.mediaUris.isEmpty()) {
                        permissions?.release()
                        return drop.texts.isNotEmpty()
                    }
                    scope.launch {
                        try {
                            val imported = importIntoEntry(context, mediaManager, drop.mediaUris)
                            if (imported.isNotEmpty()) onMediaImported(imported)
                        } finally {
                            permissions?.release()
                        }
                    }
                    return true
                }
            }
        }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { event ->
            val description = event.toAndroidDragEvent().clipDescription ?: return@dragAndDropTarget false
            (0 until description.mimeTypeCount).any { acceptsDroppedMimeType(description.getMimeType(it)) }
        },
        target = target,
    )
}

private fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
