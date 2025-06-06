package app.logdate.ui.common

import androidx.compose.foundation.lazy.LazyListState

/**
 * Scrolls to the top of the list.
 */
suspend fun LazyListState.scrollToTop() {
    scrollToItem(0)
}