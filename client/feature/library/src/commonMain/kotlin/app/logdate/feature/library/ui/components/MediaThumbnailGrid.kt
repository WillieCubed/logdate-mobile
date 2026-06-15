package app.logdate.feature.library.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.feature.library.ui.LibraryGridGroup
import app.logdate.ui.common.MultiSelectState
import app.logdate.ui.common.rememberMultiSelectState
import app.logdate.ui.foldable.FoldableSplitLayout
import app.logdate.ui.foldable.calculateFoldableSplitLayout
import app.logdate.ui.foldable.rememberFoldableLayoutInfo
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.uuid.Uuid

/**
 * A responsive grid of media thumbnails grouped by month.
 *
 * Supports multi-selection via long-press or Ctrl+click when
 * [multiSelectState] has active selections.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun MediaThumbnailGrid(
    groups: List<LibraryGridGroup>,
    columnCount: Int,
    onItemClick: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
    multiSelectState: MultiSelectState = rememberMultiSelectState(),
) {
    val foldableLayoutInfo = rememberFoldableLayoutInfo()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        when (
            val splitLayout =
                calculateFoldableSplitLayout(
                    containerWidth = maxWidth,
                    containerHeight = maxHeight,
                    layoutInfo = foldableLayoutInfo,
                    minPaneWidth = MINIMUM_BOOK_PANE_WIDTH,
                )
        ) {
            FoldableSplitLayout.None,
            is FoldableSplitLayout.Horizontal,
            -> {
                MediaThumbnailGridPane(
                    groups = groups,
                    columnCount = columnCount,
                    onItemClick = onItemClick,
                    multiSelectState = multiSelectState,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is FoldableSplitLayout.Vertical -> {
                val (leftGroups, rightGroups) = splitMediaGroupsForBookPosture(groups)
                Row(modifier = Modifier.fillMaxSize()) {
                    MediaThumbnailGridPane(
                        groups = leftGroups,
                        columnCount =
                            mediaGridColumnCountForBookPane(
                                paneWidth = splitLayout.leftPane.width,
                                requestedColumnCount = columnCount,
                            ),
                        onItemClick = onItemClick,
                        multiSelectState = multiSelectState,
                        modifier =
                            Modifier
                                .width(splitLayout.leftPane.width)
                                .fillMaxHeight(),
                    )
                    androidx.compose.foundation.layout.Spacer(
                        modifier =
                            Modifier
                                .width(splitLayout.hingeBounds.width)
                                .fillMaxHeight(),
                    )
                    MediaThumbnailGridPane(
                        groups = rightGroups,
                        columnCount =
                            mediaGridColumnCountForBookPane(
                                paneWidth = splitLayout.rightPane.width,
                                requestedColumnCount = columnCount,
                            ),
                        onItemClick = onItemClick,
                        multiSelectState = multiSelectState,
                        modifier =
                            Modifier
                                .width(splitLayout.rightPane.width)
                                .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun MediaThumbnailGridPane(
    groups: List<LibraryGridGroup>,
    columnCount: Int,
    onItemClick: (Uuid) -> Unit,
    multiSelectState: MultiSelectState,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount),
        modifier = modifier,
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
                val id = item.uid.toString()
                MediaThumbnailItem(
                    item = item,
                    onItemClick = { uid ->
                        if (multiSelectState.hasSelection) {
                            multiSelectState.toggle(id)
                        } else {
                            onItemClick(uid)
                        }
                    },
                    isSelected = multiSelectState.isSelected(id),
                )
            }
        }
    }
}

internal fun splitMediaGroupsForBookPosture(
    groups: List<LibraryGridGroup>,
): Pair<
    List<LibraryGridGroup>,
    List<LibraryGridGroup>,
> {
    val totalItemCount = groups.sumOf { it.items.size }
    if (totalItemCount <= 1) {
        return groups to emptyList()
    }

    val targetLeftItemCount = ceil(totalItemCount / 2f).toInt()
    val leftGroups = mutableListOf<LibraryGridGroup>()
    val rightGroups = mutableListOf<LibraryGridGroup>()
    var leftItemCount = 0

    groups.forEach { group ->
        when {
            leftItemCount >= targetLeftItemCount -> {
                rightGroups += group
            }

            leftItemCount + group.items.size <= targetLeftItemCount -> {
                leftGroups += group
                leftItemCount += group.items.size
            }

            else -> {
                val leftTakeCount = targetLeftItemCount - leftItemCount
                if (leftTakeCount > 0) {
                    leftGroups += group.copy(items = group.items.take(leftTakeCount))
                }
                val rightItems = group.items.drop(leftTakeCount)
                if (rightItems.isNotEmpty()) {
                    rightGroups += group.copy(items = rightItems)
                }
                leftItemCount = targetLeftItemCount
            }
        }
    }

    return leftGroups to rightGroups
}

internal fun mediaGridColumnCountForBookPane(
    paneWidth: Dp,
    requestedColumnCount: Int,
): Int {
    val paneColumnCount =
        floor(paneWidth.value / MINIMUM_BOOK_PANE_THUMBNAIL_WIDTH.value)
            .toInt()
            .coerceAtLeast(MINIMUM_BOOK_PANE_COLUMN_COUNT)
    return paneColumnCount.coerceAtMost(
        requestedColumnCount.coerceAtLeast(MINIMUM_BOOK_PANE_COLUMN_COUNT),
    )
}

private val MINIMUM_BOOK_PANE_WIDTH = 320.dp
private val MINIMUM_BOOK_PANE_THUMBNAIL_WIDTH = 160.dp
private const val MINIMUM_BOOK_PANE_COLUMN_COUNT = 2
