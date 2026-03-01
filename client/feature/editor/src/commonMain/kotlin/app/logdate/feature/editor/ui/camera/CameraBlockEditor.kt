package app.logdate.feature.editor.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.common.DeleteMediaButton
import app.logdate.feature.editor.ui.common.MediaCaptionField
import app.logdate.feature.editor.ui.editor.CameraBlockUiState
import app.logdate.feature.editor.ui.formatMediaDuration
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.aakira.napier.Napier

/**
 * Editor component for camera-captured media blocks.
 * Displays either the captured media with caption editing, or the camera capture UI.
 *
 * @param block The camera block state
 * @param onBlockUpdated Callback when the block is updated
 * @param onDeleteRequested Callback when the block should be deleted
 * @param modifier Modifier for layout customization
 */
@Composable
fun CameraBlockEditor(
    block: CameraBlockUiState,
    onBlockUpdated: (CameraBlockUiState) -> Unit,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasExistingMedia = block.uri != null

    Napier.d("CameraBlockEditor - hasExistingMedia: $hasExistingMedia, URI: ${block.uri}, mediaType: ${block.mediaType}")

    if (hasExistingMedia) {
        CapturedMediaContent(
            block = block,
            onBlockUpdated = onBlockUpdated,
            onDeleteRequested = onDeleteRequested,
            modifier = modifier
        )
    } else {
        CameraCaptureContent(
            onMediaCaptured = { uri, mediaType, durationMs ->
                Napier.d("CameraBlockEditor - Media captured: $uri, type: $mediaType")
                onBlockUpdated(
                    block.copy(
                        uri = uri,
                        mediaType = mediaType,
                        durationMs = durationMs
                    )
                )
            },
            onClose = onDeleteRequested,
            modifier = modifier
        )
    }
}

/**
 * Displays captured media (photo or video) with caption editing and delete option.
 */
@Composable
private fun CapturedMediaContent(
    block: CameraBlockUiState,
    onBlockUpdated: (CameraBlockUiState) -> Unit,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            when (block.mediaType) {
                CapturedMediaType.PHOTO -> {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalPlatformContext.current)
                            .data(block.uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = block.caption.ifBlank { "Captured photo" },
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }

                CapturedMediaType.VIDEO -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play video",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatDuration(block.durationMs),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            DeleteMediaButton(
                onClick = onDeleteRequested,
                contentDescription = "Delete media",
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }

        MediaCaptionField(
            caption = block.caption,
            onCaptionChanged = { onBlockUpdated(block.copy(caption = it)) }
        )
    }
}

/**
 * Formats a duration in milliseconds to a MM:SS string.
 */
private fun formatDuration(durationMs: Long): String {
    return formatMediaDuration(durationMs, true)
}

/**
 * Platform-specific camera capture content.
 * Shows an inline preview with camera controls.
 *
 * @param onMediaCaptured Callback when media is captured, providing URI, type, and duration
 * @param onClose Callback when the user closes the camera without capturing
 * @param modifier Modifier for layout customization
 */
@Composable
expect fun CameraCaptureContent(
    onMediaCaptured: (uri: String, mediaType: CapturedMediaType, durationMs: Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
)
