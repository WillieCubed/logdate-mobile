package app.logdate.feature.journals.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing

@Composable
fun JournalListPlaceholder(modifier: Modifier = Modifier, journals: Int = 4) {
    LazyVerticalGrid(
        modifier = modifier,
        columns = GridCells.Adaptive(minSize = 172.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        contentPadding = PaddingValues(Spacing.lg),
    ) {
        items(journals) {
            JournalPlaceholderItem()
        }
    }
}

@Preview
@Composable
fun JournalListPlaceholderPreview() {
    LogDateTheme {
        JournalListPlaceholder()
    }
}