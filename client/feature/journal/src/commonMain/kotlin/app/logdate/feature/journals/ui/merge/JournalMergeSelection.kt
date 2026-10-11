@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.journals.ui.merge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.logdate.client.repository.journals.JournalMergePreview
import app.logdate.ui.theme.Spacing
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.feature.journal.generated.resources.journal_merge_change_destination
import logdate.client.feature.journal.generated.resources.journal_merge_change_source
import logdate.client.feature.journal.generated.resources.journal_merge_from
import logdate.client.feature.journal.generated.resources.journal_merge_into
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun JournalMergeSelection(
    preview: JournalMergePreview,
    onChangeDestination: () -> Unit,
    enabled: Boolean,
    onChangeSource: () -> Unit = {},
    sourceEditable: Boolean = true,
    changed: Boolean = false,
) {
    val updatedOutcome = remember { BringIntoViewRequester() }
    LaunchedEffect(changed, preview) {
        if (changed) {
            withFrameNanos { }
            updatedOutcome.bringIntoView()
        }
    }
    Column(Modifier.testTag("journal_merge_selection"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        MergeJournalCard(
            stringResource(Res.string.journal_merge_from),
            preview.source.title,
            stringResource(Res.string.journal_merge_change_source),
            enabled && sourceEditable,
            onChangeSource,
        ) { JournalMergeCardMetadata(preview, source = true) }
        Column(
            Modifier.fillMaxWidth().testTag("journal_merge_outcome").bringIntoViewRequester(updatedOutcome),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            MergeJournalCard(
                stringResource(Res.string.journal_merge_into),
                preview.destination.title,
                stringResource(Res.string.journal_merge_change_destination),
                enabled,
                onChangeDestination,
            ) { JournalMergeCardMetadata(preview, source = false) }
            if (changed) JournalMergeFeedback(JournalMergeError.Changed)
        }
    }
}

@Composable
private fun MergeJournalCard(
    role: String,
    title: String,
    actionLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
    metadata: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = actionLabel },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(role, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.titleMedium)
                metadata()
            }
            if (enabled) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
