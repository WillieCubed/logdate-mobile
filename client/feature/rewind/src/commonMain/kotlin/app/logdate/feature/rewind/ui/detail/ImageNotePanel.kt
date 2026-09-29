@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.logdate.ui.content.ImageScrimOverlay
import coil3.compose.AsyncImage
import logdate.client.feature.rewind.generated.resources.Res
import logdate.client.feature.rewind.generated.resources.cd_rewind_journal_photo
import logdate.client.feature.rewind.generated.resources.cd_rewind_video_frame
import logdate.client.feature.rewind.generated.resources.rewind_media_unavailable
import logdate.client.feature.rewind.generated.resources.rewind_video_frame_label
import org.jetbrains.compose.resources.stringResource

/**
 * Panel for displaying an image note within a rewind story.
 *
 * This panel showcases a captured image from the user's journal with
 * optional caption and date context. Designed for visual memories and moments.
 *
 * ## Visual Design:
 * - **Image Focus**: Maximizes visual impact of the image
 * - **Caption**: Optional text overlay for context
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
) {
    var imageFailed by remember(imageUri) { mutableStateOf(false) }
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Image as full background
        AsyncImage(
            model = imageUri,
            contentDescription =
                if (isVideoFrame) {
                    stringResource(Res.string.cd_rewind_video_frame, dateFormatted)
                } else {
                    caption?.takeIf { it.isNotBlank() }
                        ?: stringResource(Res.string.cd_rewind_journal_photo, dateFormatted)
                },
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onError = { imageFailed = true },
            onSuccess = { imageFailed = false },
        )

        // Top and bottom gradient overlays for more polished look
        ImageScrimOverlay(alphaStops = listOf(0.3f, 0f, 0f, 0.7f))

        if (imageFailed) {
            Text(
                text = stringResource(Res.string.rewind_media_unavailable),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                modifier = Modifier.padding(24.dp),
            )
        }

        // Date indicator at top
        if (isVideoFrame) {
            Text(
                text = stringResource(Res.string.rewind_video_frame_label),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            Text(
                text = dateFormatted,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        // Caption at bottom with enhanced styling
        if (!caption.isNullOrBlank()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 24.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(16.dp),
                ) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f,
                    )
                }
            }
        }
    }
}
