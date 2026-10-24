package app.logdate.feature.editor.ui.drop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.platform.LocalContext
import app.logdate.client.media.ManagedMediaPublicationCustody
import app.logdate.client.media.MediaManager
import app.logdate.feature.editor.ui.common.findActivity
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
actual fun Modifier.entryDropTarget(
    onText: (String) -> Unit,
    onMediaImported: (EntryMediaImport) -> Unit,
    onActiveChanged: (Boolean) -> Unit,
): Modifier {
    val context = LocalContext.current
    val mediaManager: MediaManager = koinInject()
    val publicationCustody: ManagedMediaPublicationCustody = koinInject()
    val scope = rememberCoroutineScope()
    // The target outlives recompositions, so it reads the latest callbacks rather than the first ones.
    val currentOnText by rememberUpdatedState(onText)
    val currentOnMediaImported by rememberUpdatedState(onMediaImported)
    val currentOnActiveChanged by rememberUpdatedState(onActiveChanged)
    val target =
        remember(context, mediaManager, publicationCustody) {
            object : DragAndDropTarget {
                override fun onEntered(event: DragAndDropEvent) = currentOnActiveChanged(true)

                override fun onExited(event: DragAndDropEvent) = currentOnActiveChanged(false)

                override fun onEnded(event: DragAndDropEvent) = currentOnActiveChanged(false)

                override fun onDrop(event: DragAndDropEvent): Boolean {
                    currentOnActiveChanged(false)
                    val dragEvent = event.toAndroidDragEvent()
                    val clip = dragEvent.clipData ?: return false
                    // Reading another app's file, even its type, needs this permission, and it ends
                    // when released.
                    val hasFiles = (0 until clip.itemCount).any { clip.getItemAt(it).uri != null }
                    val permissions = if (hasFiles) context.findActivity()?.requestDragAndDropPermissions(dragEvent) else null
                    val drop = routeDroppedItems(clip.droppedItems(context))
                    drop.texts.forEach { currentOnText(it) }
                    if (drop.mediaUris.isEmpty()) {
                        permissions?.release()
                        return drop.texts.isNotEmpty()
                    }
                    scope.launch {
                        try {
                            currentOnMediaImported(importIntoEntry(context, mediaManager, publicationCustody, drop.mediaUris))
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
