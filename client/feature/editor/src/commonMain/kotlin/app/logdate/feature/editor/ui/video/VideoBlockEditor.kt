package app.logdate.feature.editor.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.editor.VideoBlockUiState
import io.github.aakira.napier.Napier

/**
 * Editor component for video blocks.
 * Displays either the video with playback controls and caption editing,
 * or a video picker UI if no video is selected.
 *
 * @param block The video block state
 * @param onBlockUpdated Callback when the block is updated
 * @param onDeleteRequested Callback when the block should be deleted
 * @param modifier Modifier for layout customization
 */
@Composable
fun VideoBlockEditor(
    block: VideoBlockUiState,
    onBlockUpdated: (VideoBlockUiState) -> Unit,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasExistingVideo = block.uri != null

    Napier.d("VideoBlockEditor - hasExistingVideo: $hasExistingVideo, URI: ${block.uri}")

    if (hasExistingVideo) {
        VideoDisplayContent(
            block = block,
            onBlockUpdated = onBlockUpdated,
            onDeleteRequested = onDeleteRequested,
            modifier = modifier
        )
    } else {
        VideoPickerContent(
            onVideoSelected = { uri, durationMs ->
                Napier.d("VideoBlockEditor - Video selected: $uri, duration: $durationMs")
                onBlockUpdated(block.copy(uri = uri, durationMs = durationMs))
            },
            modifier = modifier
        )
    }
}

/**
 * Displays a video with playback controls and caption editing.
 */
@Composable
private fun VideoDisplayContent(
    block: VideoBlockUiState,
    onBlockUpdated: (VideoBlockUiState) -> Unit,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            VideoPlayerContent(
                uri = block.uri ?: "",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            IconButton(
                onClick = onDeleteRequested,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete video",
                    tint = MaterialTheme.colorScheme.inverseOnSurface
                )
            }

            if (block.durationMs > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = formatDuration(block.durationMs),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }
        }

        OutlinedTextField(
            value = block.caption,
            onValueChange = { newCaption ->
                onBlockUpdated(block.copy(caption = newCaption))
            },
            placeholder = { Text("Add a caption...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            maxLines = 3
        )
    }
}

/**
 * Formats a duration in milliseconds to a MM:SS string.
 */
private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / 1000) / 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * Platform-specific video player content.
 *
 * @param uri The URI of the video to play
 * @param modifier Modifier for layout customization
 */
@Composable
expect fun VideoPlayerContent(
    uri: String,
    modifier: Modifier = Modifier
)

/**
 * Platform-specific video picker content.
 *
 * @param onVideoSelected Callback when a video is selected, providing URI and duration
 * @param modifier Modifier for layout customization
 */
@Composable
expect fun VideoPickerContent(
    onVideoSelected: (uri: String, durationMs: Long) -> Unit,
    modifier: Modifier = Modifier
)
