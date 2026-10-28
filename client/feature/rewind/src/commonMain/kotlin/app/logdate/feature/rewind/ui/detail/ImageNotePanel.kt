@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.logdate.ui.workspace.LocalWorkspacePlaybackInsets
import coil3.compose.AsyncImage
import logdate.client.feature.rewind.generated.resources.Res
import logdate.client.feature.rewind.generated.resources.cd_rewind_journal_photo
import logdate.client.feature.rewind.generated.resources.cd_rewind_video_frame
import logdate.client.feature.rewind.generated.resources.rewind_video_frame_label
import org.jetbrains.compose.resources.stringResource

/**
 * Panel for displaying an image note within a rewind story.
 *
 * This panel showcases a captured image from the user's journal with
 * optional caption and date context. Designed for visual memories and moments.
 *
 * ## Visual Design:
 * - **Image Focus**: Bounded media preserves its proportions inside the full scene
 * - **Caption**: Leading text beneath the media shares its width
 * - **Date Context**: Clear indication of when the image was captured
 *
 * @param imageUri URI of the image to display
 * @param caption Optional text caption for the image
 * @param dateFormatted Formatted date string (e.g., "Tuesday, Nov 19")
 * @param modifier Modifier for customizing the panel container
 */
@Composable
fun ImageNotePanel(
    imageUri: String,
    caption: String?,
    dateFormatted: String,
    isVideoFrame: Boolean = false,
    modifier: Modifier = Modifier,
    memoryId: String = "image-memory",
) {
    var loadState by remember(imageUri) { mutableStateOf<RewindMemoryLoadState>(RewindMemoryLoadState.Loading) }
    val insets = LocalWorkspacePlaybackInsets.current
    val safeTop = with(LocalDensity.current) { insets.getTop(this).toDp() }
    val headerClearance = (LocalRewindChromeHeight.current - safeTop).coerceAtLeast(0.dp) + 12.dp
    RewindMemoryFrame(memoryId, loadState, modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(insets)
                .padding(horizontal = RewindMemoryContentPadding)
                .padding(top = headerClearance, bottom = RewindMemoryContentPadding),
            contentAlignment = Alignment.Center,
        ) {
            val captionLimit = maxHeight * 0.32f
            Column(
                Modifier
                    .widthIn(max = RewindMediaMaxWidth)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .testTag("rewind-media-content"),
                horizontalAlignment = Alignment.Start,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(dateFormatted, style = MaterialTheme.typography.labelLarge, color = Color.White)
                    if (isVideoFrame) {
                        Text(
                            stringResource(Res.string.rewind_video_frame_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                AsyncImage(
                    model = imageUri,
                    contentDescription =
                        if (isVideoFrame) {
                            stringResource(Res.string.cd_rewind_video_frame, dateFormatted)
                        } else {
                            caption?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.cd_rewind_journal_photo, dateFormatted)
                        },
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                    onLoading = { loadState = RewindMemoryLoadState.Loading },
                    onError = { loadState = RewindMemoryLoadState.Unavailable },
                    onSuccess = {
                        loadState = RewindMemoryLoadState.Ready
                    },
                )
                if (!caption.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = captionLimit)
                            .verticalScroll(rememberScrollState())
                            .padding(RewindMemoryContentPadding),
                    ) {
                        RewindRichMemoryText(content = caption)
                    }
                }
            }
        }
    }
}
