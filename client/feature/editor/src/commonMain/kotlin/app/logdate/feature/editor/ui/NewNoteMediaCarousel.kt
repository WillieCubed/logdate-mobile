package app.logdate.feature.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * A Material 3 carousel that allows the user to view selected media items.
 *
 * @param mediaItems A list of URI strings that represent the media items to display
 * @param onItemClicked A callback that is invoked when a media item is clicked
 * @param onRemoveItem A callback that is invoked when a media item is deleted
 * @param modifier The modifier to apply to the carousel
 */
@Composable
internal fun NewNoteMediaCarousel(
    mediaItems: List<String>,
    onItemClicked: (uri: String) -> Unit,
    onRemoveItem: (uri: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(mediaItems) { uri ->
            MediaCarouselItem(
                uri = uri,
                onClick = { onItemClicked(uri) },
                onDelete = { onRemoveItem(uri) },
                modifier = Modifier.fillParentMaxHeight(),
            )
        }
    }
}

@Composable
internal fun MediaCarouselItem(
    uri: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clip(RoundedCornerShape(4.dp))) {
        val imageRequest = ImageRequest.Builder(LocalPlatformContext.current)
            .data(uri)
            .apply {
                crossfade(true)
//                    placeholder(R.drawable.ic_image_placeholder)
//                    error(R.drawable.ic_image_error)
            }.build()
        AsyncImage(
            model = imageRequest,
            // TODO: Add image descriptions like "photo of a cat taken on July 4th, 2022"
            contentDescription = null,
            contentScale = ContentScale.FillHeight,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .size(120.dp)
                .clickable(onClick = onClick),
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(32.dp)
                .align(Alignment.TopEnd)
                .offset(x = (-4).dp, y = 4.dp)
                .background(
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp)
                )
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}