package app.logdate.feature.editor.ui.drop

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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import app.logdate.client.media.MediaManager
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
actual fun Modifier.entryPasteTarget(onMediaImported: (List<String>) -> Unit): Modifier {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val mediaManager: MediaManager = koinInject()
    val scope = rememberCoroutineScope()
    return onPreviewKeyEvent { event ->
        val paste = event.type == KeyEventType.KeyDown && event.key == Key.V && (event.isCtrlPressed || event.isMetaPressed)
        if (paste) {
            scope.launch {
                val clip = clipboard.getClipEntry()?.clipData ?: return@launch
                val media = routeDroppedItems(clip.droppedItems(context)).mediaUris
                if (media.isEmpty()) return@launch
                val imported = importIntoEntry(context, mediaManager, media)
                if (imported.isNotEmpty()) onMediaImported(imported)
            }
        }
        // The text field still handles the same keys, so any text on the clipboard is pasted too.
        false
    }
}
