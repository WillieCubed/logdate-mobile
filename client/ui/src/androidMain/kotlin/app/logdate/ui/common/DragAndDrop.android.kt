package app.logdate.ui.common

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent

@OptIn(ExperimentalFoundationApi::class)
actual fun Modifier.noteDragSource(text: String): Modifier =
    this.dragAndDropSource { _ ->
        DragAndDropTransferData(
            clipData = ClipData.newPlainText("note", text),
        )
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
