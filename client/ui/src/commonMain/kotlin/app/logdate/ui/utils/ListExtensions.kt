package app.logdate.ui.utils

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState

/**
 * Scrolls the list to the last visible item with an animation.
 */
suspend fun LazyListState.scrollToLastVisibleItem() {
    // Scroll to the last visible item in the list
    if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
        val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        animateScrollToItem(lastVisibleItemIndex)
    }
}

/**
 * Scrolls the list to its last item with an animation.
 *
 * Compared to [scrollToLastVisibleItem], this will always scroll to the very end of the list,
 * regardless of what is currently visible.
 *
 * - For a column list (e.g. [LazyColumn]), this will scroll to the bottom.
 * - For a horizontal list (e.g. [LazyRow]), it will scroll to the rightmost item.
 */
suspend fun LazyListState.scrollToEnd() {
    // Scroll to the end of the list
    if (layoutInfo.totalItemsCount > 0) {
        val lastItemIndex = layoutInfo.totalItemsCount - 1
        animateScrollToItem(lastItemIndex)
    }
}