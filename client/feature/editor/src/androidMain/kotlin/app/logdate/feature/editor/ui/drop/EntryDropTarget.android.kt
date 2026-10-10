package app.logdate.feature.editor.ui.drop

import android.app.Activity
import android.content.ClipDescription
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
import app.logdate.client.media.AndroidManagedMediaDiscarder
import app.logdate.client.media.AndroidManagedMediaImportSource
import app.logdate.client.media.ManagedMediaImporter
import app.logdate.client.media.MediaManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
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
                    val description = dragEvent.clipDescription
                    val items =
                        (0 until clip.itemCount).map { index ->
                            val item = clip.getItemAt(index)
                            val mimeType =
                                item.uri?.let { context.contentResolver.getType(it) }
                                    ?: description?.getMimeType(index.coerceAtMost(description.mimeTypeCount - 1))
                                    ?: ClipDescription.MIMETYPE_TEXT_PLAIN
                            DroppedItem(mimeType = mimeType, uri = item.uri?.toString(), text = item.text?.toString())
                        }
                    val drop = routeDroppedItems(items)
                    drop.texts.forEach(onText)
                    if (drop.mediaUris.isEmpty()) return drop.texts.isNotEmpty()

                    // Reading another app's file needs this permission, and it ends when released.
                    val permissions = context.findActivity()?.requestDragAndDropPermissions(dragEvent)
                    scope.launch {
                        try {
                            val importer =
                                ManagedMediaImporter(
                                    mediaManager = mediaManager,
                                    source = AndroidManagedMediaImportSource(context),
                                    discardManagedMedia = AndroidManagedMediaDiscarder(context, mediaManager)::discard,
                                )
                            val imported =
                                drop.mediaUris.mapNotNull { uri ->
                                    try {
                                        importer.import(uri)
                                    } catch (cancellation: CancellationException) {
                                        throw cancellation
                                    } catch (error: Exception) {
                                        Napier.w("Could not add the dropped file $uri to the entry", error)
                                        null
                                    }
                                }
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
