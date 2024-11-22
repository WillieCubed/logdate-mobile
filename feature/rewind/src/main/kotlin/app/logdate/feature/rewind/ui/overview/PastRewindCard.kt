package app.logdate.feature.rewind.ui.overview

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.feature.rewind.ui.RewindOpenCallback

@Composable
fun PastRewindCard(
    history: RewindHistoryUiState,
    onOpenRewind: RewindOpenCallback,
    modifier: Modifier = Modifier.Companion,
) {
    Surface(
        onClick = { onOpenRewind(history.uid) },
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Text(history.title)
    }
}