@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.events.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import app.logdate.ui.platform.PlatformIcons
import app.logdate.ui.theme.Spacing
import coil3.compose.AsyncImage

/**
 * Cover image card for the event detail editor.
 *
 * Tapping the card opens the platform photo picker. Long-pressing it (only when an image is
 * already set) clears the cover. The card always renders at a 16:9 aspect ratio with rounded
 * corners; the empty state is a tappable placeholder showing an image icon and helper text.
 *
 * @param coverImageUri the current cover image URI, or `null` when no cover is set.
 * @param onPickImage invoked when the user taps the card to choose a new image.
 * @param onClearImage invoked on long-press when an image is set; ignored when empty.
 * @param modifier standard Compose modifier slot.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun EventCoverImageCard(
    coverImageUri: String?,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Spacing.lg)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .combinedClickable(
                    onClick = onPickImage,
                    onLongClick = if (coverImageUri != null) onClearImage else null,
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (coverImageUri != null) {
            AsyncImage(
                model = coverImageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            EmptyCoverState()
        }
    }
}

@Composable
private fun EmptyCoverState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
        modifier = Modifier.padding(Spacing.lg),
    ) {
        Icon(
            painter = PlatformIcons.library(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Add a cover image",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
