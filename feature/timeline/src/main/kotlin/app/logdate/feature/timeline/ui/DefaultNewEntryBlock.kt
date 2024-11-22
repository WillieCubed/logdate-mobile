package app.logdate.feature.timeline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing

@Composable
internal fun DefaultNewEntryBlock(
    onNewEntry: () -> Unit,
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onNewEntry,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(
                    start = Spacing.xl,
                    end = Spacing.xl,
                    bottom = Spacing.sm,
                    top = Spacing.sm
                )
                .fillMaxWidth(),
        ) {

            Text(
                message,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(Spacing.lg),
            )
            Row(
                modifier = Modifier.padding(Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.padding(end = Spacing.sm))
                Text("Add new entry")
            }
        }
    }
}

@Preview
@Composable
private fun DefaultNewEntryBlockPreview() {
    DefaultNewEntryBlock(
        onNewEntry = {},
        message = "No notes from today yet!",
    )
}