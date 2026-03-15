package app.logdate.feature.library.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.feature.library.ui.LibraryGridGroup
import kotlin.uuid.Uuid

/**
 * A responsive grid of media thumbnails grouped by month.
 *
 * @param groups Media items grouped by month with date headers
 * @param columnCount Number of columns to display (varies by screen width)
 * @param onItemClick Callback when a media item is tapped
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun MediaThumbnailGrid(
    groups: List<LibraryGridGroup>,
    columnCount: Int,
    onItemClick: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount),
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        groups.forEach { group ->
            item(
                key = "header-${group.label}",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                DateGroupHeader(label = group.label)
            }
            items(
                items = group.items,
                key = { it.uid },
            ) { item ->
                MediaThumbnailItem(
                    item = item,
                    onItemClick = onItemClick,
                )
            }
        }
    }
}
