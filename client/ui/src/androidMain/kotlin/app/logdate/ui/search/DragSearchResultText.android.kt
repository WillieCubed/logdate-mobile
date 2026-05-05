@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package app.logdate.ui.search

import android.content.ClipData
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferData

actual fun Modifier.dragSearchResultText(text: String): Modifier =
    dragAndDropSource { _ ->
        DragAndDropTransferData(
            clipData = ClipData.newPlainText("Search result", text),
            flags = android.view.View.DRAG_FLAG_GLOBAL,
        )
    }
