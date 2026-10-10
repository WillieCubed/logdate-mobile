package app.logdate.feature.editor.ui.drop

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.logdate.client.media.MediaManager
import kotlinx.coroutines.launch
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.paste_as_attachment
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.stringResource as resourceString

private object MediaPasteKey

@Composable
actual fun Modifier.entryPasteTarget(onMediaImported: (List<String>) -> Unit): Modifier {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val mediaManager: MediaManager = koinInject()
    val scope = rememberCoroutineScope()
    val pasteLabel = stringResource(android.R.string.paste)
    val pasteAsAttachmentLabel = resourceString(Res.string.paste_as_attachment)
    val pasteMedia: () -> Unit = {
        scope.launch {
            val imported = pastedMedia(context, clipboard, mediaManager)
            if (imported.isNotEmpty()) onMediaImported(imported)
        }
    }
    return onPreviewKeyEvent { event ->
        val paste = event.type == KeyEventType.KeyDown && event.key == Key.V && (event.isCtrlPressed || event.isMetaPressed)
        if (paste) pasteMedia()
        // The text field still handles the same keys, so any text on the clipboard is pasted too.
        false
    }.appendTextContextMenuComponents {
        // The description is enough to choose the item and, unlike the clip itself, reading it
        // does not tell the person LogDate read their clipboard.
        val description = context.getSystemService(ClipboardManager::class.java)?.primaryClipDescription
        val mimeTypes = description?.let { (0 until it.mimeTypeCount).map(it::getMimeType) }.orEmpty()
        val label =
            when (mediaPasteItem(mimeTypes)) {
                MediaPasteItem.Paste -> pasteLabel
                MediaPasteItem.PasteAsAttachment -> pasteAsAttachmentLabel
                null -> return@appendTextContextMenuComponents
            }
        item(key = MediaPasteKey, label = label) {
            close()
            pasteMedia()
        }
    }
}

/** Copies the photos, videos, and audio on the clipboard into LogDate's store and returns the copies. */
private suspend fun pastedMedia(
    context: Context,
    clipboard: Clipboard,
    mediaManager: MediaManager,
): List<String> {
    val clip = clipboard.getClipEntry()?.clipData ?: return emptyList()
    val media = routeDroppedItems(clip.droppedItems(context)).mediaUris
    if (media.isEmpty()) return emptyList()
    return importIntoEntry(context, mediaManager, media)
}
