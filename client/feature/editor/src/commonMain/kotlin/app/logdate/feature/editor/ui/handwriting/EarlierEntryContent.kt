@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.handwriting

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.client.repository.journals.JournalNote
import app.logdate.feature.editor.ui.audio.AudioPlaybackComponent
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.image.ImageBlockPreview
import app.logdate.feature.editor.ui.video.VideoPlayerContent

@Composable
internal fun EarlierEntryContent(note: JournalNote) {
    when (note) {
        is JournalNote.Text -> Text(note.content, style = MaterialTheme.typography.bodyLarge)
        is JournalNote.Handwriting -> {
            if (note.unreadableContent != null) {
                Text("This handwriting needs a newer version of LogDate.")
            } else {
                HandwritingCanvas(
                    note.document.toPage(),
                    Modifier.fillMaxWidth().heightIn(min = 208.dp, max = 320.dp),
                    description = "Handwritten entry",
                    fitPreviewToWidth = false,
                    adaptInkToPaper = true,
                    paperColor = MaterialTheme.colorScheme.surfaceContainerLow,
                )
            }
        }
        is JournalNote.Image -> {
            ImageBlockPreview(
                ImageBlockUiState(uri = note.mediaRef, caption = note.caption),
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 400.dp),
                wrapToImage = true,
            )
            if (note.caption.isNotBlank()) Text(note.caption)
        }
        is JournalNote.Audio -> {
            AudioPlaybackComponent(note.mediaRef, Modifier.fillMaxWidth())
            if (note.caption.isNotBlank()) Text(note.caption)
        }
        is JournalNote.Video -> {
            VideoPlayerContent(note.mediaRef, Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 400.dp))
            if (note.caption.isNotBlank()) Text(note.caption)
        }
    }
}
