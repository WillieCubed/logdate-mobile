package app.logdate.ui.common

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.net.Uri
import android.view.View
import android.webkit.MimeTypeMap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import app.logdate.client.media.storage.androidMediaFileResolver
import io.github.aakira.napier.Napier
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
actual fun Modifier.noteDragSource(text: String): Modifier =
    this.dragAndDropSource { _ ->
        DragAndDropTransferData(
            clipData = ClipData.newPlainText("note", text),
        )
    }

@OptIn(ExperimentalFoundationApi::class)
actual fun Modifier.batchDragSource(items: List<String>): Modifier =
    this.dragAndDropSource { _ ->
        val clipData = ClipData.newPlainText("batch", items.firstOrNull() ?: "")
        items.drop(1).forEach { item ->
            clipData.addItem(ClipData.Item(item))
        }
        DragAndDropTransferData(clipData = clipData)
    }

@OptIn(ExperimentalFoundationApi::class)
actual fun Modifier.noteDropTarget(onDrop: (String) -> Unit): Modifier {
    val target =
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val text =
                    event
                        .toAndroidDragEvent()
                        .clipData
                        ?.getItemAt(0)
                        ?.text
                        ?.toString()
                        ?: return false
                onDrop(text)
                return true
            }
        }
    return this.dragAndDropTarget(
        shouldStartDragAndDrop = { event ->
            event
                .toAndroidDragEvent()
                .clipDescription
                ?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
        },
        target = target,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
actual fun Modifier.mediaDragSource(uri: String): Modifier {
    val context = LocalContext.current
    return this.dragAndDropSource { _ ->
        val shareable = context.shareableMediaUri(uri)
        if (shareable == null) {
            DragAndDropTransferData(clipData = ClipData.newPlainText("media", uri))
        } else {
            val mimeType =
                context.contentResolver.getType(shareable)
                    ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(uri))
                    ?: "application/octet-stream"
            DragAndDropTransferData(
                clipData = ClipData(ClipDescription("LogDate media", arrayOf(mimeType)), ClipData.Item(shareable)),
                flags = View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ,
            )
        }
    }
}

/**
 * A content URI another app may read for [uri]. Content URIs pass through. App-private files, named
 * by path or by a `logdate-media://` reference, are exposed through the app's FileProvider. Anything
 * else returns null.
 */
private fun Context.shareableMediaUri(uri: String): Uri? {
    val parsed = Uri.parse(uri)
    if (parsed.scheme == "content") return parsed
    val path = androidMediaFileResolver(this).filePath(uri) ?: return null
    return runCatching {
        FileProvider.getUriForFile(this, "$packageName.provider", File(path))
    }.onFailure { error -> Napier.w("Media at $uri is outside the shareable folders", error) }
        .getOrNull()
}
