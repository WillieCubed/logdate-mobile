package app.logdate.feature.rewind.ui.overview

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.feature.rewind.ui.RewindOpenCallback

/**
 * A list of previous [Rewind]s.
 */
@Composable
fun RewindHistoryList(
    rewinds: List<RewindHistoryUiState>,
    onOpenRewind: RewindOpenCallback,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
    ) {
        items(rewinds) { history ->
            PastRewindCard(history, onOpenRewind)
        }
    }
}