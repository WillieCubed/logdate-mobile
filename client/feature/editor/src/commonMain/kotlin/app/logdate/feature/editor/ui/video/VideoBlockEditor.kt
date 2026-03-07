package app.logdate.feature.editor.ui.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.common.DeleteMediaButton
import app.logdate.feature.editor.ui.common.MediaCaptionField
import app.logdate.feature.editor.ui.editor.VideoBlockUiState
import app.logdate.feature.editor.ui.formatMediaDuration
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.delete_video
import org.jetbrains.compose.resources.stringResource

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
@Suppress("ktlint:standard:function-naming")
@Composable
fun VideoBlockEditor(
    block: VideoBlockUiState,
    onBlockUpdated: (VideoBlockUiState) -> Unit,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasExistingVideo = block.uri != null

    if (hasExistingVideo) {
        VideoDisplayContent(
            block = block,
            onBlockUpdated = onBlockUpdated,
            onDeleteRequested = onDeleteRequested,
            modifier = modifier,
        )
    } else {
        VideoPickerContent(
            onVideoSelected = { uri, durationMs ->
                onBlockUpdated(block.copy(uri = uri, durationMs = durationMs))
            },
            modifier = modifier,
        )
    }
}

/**
 * Displays a video with playback controls and caption editing.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun VideoDisplayContent(
    block: VideoBlockUiState,
    onBlockUpdated: (VideoBlockUiState) -> Unit,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(8.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            VideoPlayerContent(
                uri = block.uri ?: "",
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
            )

            DeleteMediaButton(
                onClick = onDeleteRequested,
                contentDescription = stringResource(Res.string.delete_video),
                modifier = Modifier.align(Alignment.TopEnd),
            )

            if (block.durationMs > 0) {
                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f),
                ) {
                    Text(
                        text = formatDuration(block.durationMs),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
        }

        MediaCaptionField(
            caption = block.caption,
            onCaptionChanged = { onBlockUpdated(block.copy(caption = it)) },
        )
    }
}

/**
 * Formats a duration in milliseconds to a MM:SS string.
 */
private fun formatDuration(durationMs: Long): String = formatMediaDuration(durationMs, false)

/**
 * Platform-specific video player content.
 *
 * @param uri The URI of the video to play
 * @param modifier Modifier for layout customization
 */
@Suppress("ktlint:standard:function-naming")
@Composable
expect fun VideoPlayerContent(
    uri: String,
    modifier: Modifier = Modifier,
)

/**
 * Platform-specific video picker content.
 *
 * @param onVideoSelected Callback when a video is selected, providing URI and duration
 * @param modifier Modifier for layout customization
 */
@Suppress("ktlint:standard:function-naming")
@Composable
expect fun VideoPickerContent(
    onVideoSelected: (uri: String, durationMs: Long) -> Unit,
    modifier: Modifier = Modifier,
)
