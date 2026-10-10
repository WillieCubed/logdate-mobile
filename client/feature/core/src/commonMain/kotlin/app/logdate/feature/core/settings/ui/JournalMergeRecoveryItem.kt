@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.client.repository.journals.JournalMergeOperation
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.journal_merge_recovery_choose
import logdate.client.feature.core.generated.resources.journal_merge_recovery_message
import logdate.client.feature.core.generated.resources.journal_merge_recovery_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun JournalMergeRecoveryItem(
    operation: JournalMergeOperation,
    onChooseDestination: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(Res.string.journal_merge_recovery_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.journal_merge_recovery_message, operation.sourceTitle, operation.destinationTitle))
            TextButton(onClick = onChooseDestination) { Text(stringResource(Res.string.journal_merge_recovery_choose)) }
        }
    }
}
