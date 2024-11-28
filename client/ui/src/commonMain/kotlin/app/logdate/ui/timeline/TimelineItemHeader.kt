package app.logdate.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.logdate.ui.theme.Spacing

@Composable
fun TimelineItemHeader(
    title: String,
    metadata: @Composable () -> Unit,
    actions: @Composable () -> Unit = {},
    detailLevel: ItemDetailLevel = ItemDetailLevel.MAX,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(title)
            if (detailLevel == ItemDetailLevel.MAX) {
                metadata()
            }
        }
        actions()
    }
}