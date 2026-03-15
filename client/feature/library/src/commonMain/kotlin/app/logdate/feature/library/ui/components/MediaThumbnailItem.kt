package app.logdate.feature.library.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.logdate.feature.library.ui.LibraryMediaItem
import coil3.compose.AsyncImage
import kotlin.uuid.Uuid

/**
 * A single thumbnail in the media grid, displaying an image preview with a video badge overlay
 * when the item is a video.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun MediaThumbnailItem(
    item: LibraryMediaItem,
    onItemClick: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clickable { onItemClick(item.uid) },
    ) {
        AsyncImage(
            model = item.thumbnailUri ?: item.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.isVideo) {
            Icon(
                imageVector = Icons.Filled.PlayCircleFilled,
                contentDescription = "Video",
                tint = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(20.dp),
            )
        }
    }
}
