package app.logdate.feature.timeline.ui.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import coil3.compose.AsyncImage

/**
 * The UI state for [SpecialMessageSection]
 */
data class SpecialMessageUiState(
    /**
     * The title of the special message.
     *
     * Example: "You have birthday messages waiting for you."
     */
    val title: String,
    /**
     * The text of the action button that opens the special message.
     *
     * Example: "Take a peek"
     */
    val actionText: String,
    /**
     * Preview media URIs to display for the special message.
     */
    val mediaUris: List<String> = emptyList(),
)

/**
 * A special message section that can be displayed in the timeline.
 *
 * This can be used to display notices for special events in a user's life such as birthdays or
 * anniversaries.
 */
@Composable
fun SpecialMessageSection(
    uiState: SpecialMessageUiState,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        SpecialMessagePreviewCarousel(
            mediaUris = uiState.mediaUris,
            onDismiss = onDismiss
        )
        Text(uiState.title, style = MaterialTheme.typography.titleLarge)
        FilledTonalButton(onClick = onOpen) {
            Icon(
                Icons.Default.Celebration,
                contentDescription = null,
                modifier = Modifier.Companion
                    .size(24.dp)
                    .padding(end = Spacing.sm)
            )
            Text(uiState.actionText)
        }
    }
}

/**
 * An image preview to accompany a special message.
 */
@Composable
internal fun SpecialMessagePreviewCarousel(
    mediaUris: List<String>,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(4 / 5f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        // TODO: Maybe turn into interactive-ish carousel, support animations/video
        AsyncImage(
            mediaUris.firstOrNull(),
            contentDescription = null,
        )
        // TODO: Probably move button up to container
        OutlinedIconButton(
            onClick = onDismiss,
            modifier = Modifier.offset(x = Spacing.sm, y = Spacing.sm),
        ) {
            Icon(
                Icons.Default.Close,
                "Close",
            )
        }
    }
}

@Preview
@Composable
private fun SpecialMessageSectionPreview() {
    val messageUiState = SpecialMessageUiState(
        title = "A few of your friends had some special messages for you.",
        actionText = "Take a peek",
    )
    Surface {
        SpecialMessageSection(
            uiState = messageUiState,
            onOpen = {},
            onDismiss = {},
        )
    }
}
