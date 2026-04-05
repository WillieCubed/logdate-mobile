package app.logdate.feature.postcards.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.focusableWithRing
import coil3.compose.AsyncImage

/**
 * Content staging shelf with collapsible photo/sticker strips.
 *
 * Tapping the drag handle collapses the content, leaving only the mode tabs visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorShelf(
    mode: ShelfMode,
    photos: List<ShelfPhoto>,
    browsePhotos: List<ShelfPhoto>,
    stickers: List<StickerShelfItem>,
    onModeChange: (ShelfMode) -> Unit,
    onPhotoDrag: (ShelfPhoto, Float, Float) -> Unit,
    onStickerTap: (StickerShelfItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isCollapsed by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { isCollapsed = !isCollapsed },
            contentAlignment = Alignment.Center,
        ) {
            BottomSheetDefaults.DragHandle()
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = mode is ShelfMode.Photos,
                onClick = { onModeChange(ShelfMode.Photos) },
                label = { Text("Photos") },
            )
            FilterChip(
                selected = mode is ShelfMode.Stickers,
                onClick = { onModeChange(ShelfMode.Stickers) },
                label = { Text("Stickers") },
            )
            FilterChip(
                selected = mode is ShelfMode.Browse,
                onClick = { onModeChange(ShelfMode.Browse) },
                label = { Text("Browse") },
            )
        }

        AnimatedVisibility(visible = !isCollapsed) {
            when (mode) {
                is ShelfMode.Photos -> ShelfPhotoStrip(photos, onPhotoDrag)
                is ShelfMode.Stickers -> StickerShelfStrip(stickers, onStickerTap)
                is ShelfMode.Browse -> ShelfPhotoStrip(browsePhotos, onPhotoDrag)
            }
        }
    }
}

@Composable
private fun ShelfPhotoStrip(
    photos: List<ShelfPhoto>,
    onPhotoDrag: (ShelfPhoto, Float, Float) -> Unit,
) {
    if (photos.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Your photos will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(photos, key = { it.mediaUri }) { photo ->
            AsyncImage(
                model = photo.mediaUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .focusableWithRing()
                        .clickable { onPhotoDrag(photo, 0f, 0f) },
            )
        }
    }
}

@Composable
private fun StickerShelfStrip(
    stickers: List<StickerShelfItem>,
    onStickerTap: (StickerShelfItem) -> Unit,
) {
    if (stickers.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Extract stickers from photos in your library",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(stickers, key = { it.id.toString() }) { sticker ->
            AsyncImage(
                model = sticker.imageUri,
                contentDescription = sticker.label,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .focusableWithRing()
                        .clickable { onStickerTap(sticker) },
            )
        }
    }
}
