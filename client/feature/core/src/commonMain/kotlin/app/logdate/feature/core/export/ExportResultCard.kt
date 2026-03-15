@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.client.domain.export.ExportStats
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.action_browse
import logdate.client.feature.core.generated.resources.action_done
import logdate.client.feature.core.generated.resources.action_retry
import logdate.client.feature.core.generated.resources.dismiss
import logdate.client.feature.core.generated.resources.export_category_drafts
import logdate.client.feature.core.generated.resources.export_category_journals
import logdate.client.feature.core.generated.resources.export_category_media
import logdate.client.feature.core.generated.resources.export_category_notes
import logdate.client.feature.core.generated.resources.export_complete_description
import logdate.client.feature.core.generated.resources.export_complete_title
import logdate.client.feature.core.generated.resources.export_failed_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExportSuccessCard(
    fileName: String,
    stats: ExportStats?,
    onBrowse: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        Text(
            text = stringResource(Res.string.export_complete_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        Text(
            text = stringResource(Res.string.export_complete_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        stats?.let {
            Spacer(modifier = Modifier.height(Spacing.lg))

            ExportStatsGrid(stats = it)
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

        Button(
            onClick = onBrowse,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.action_browse))
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.action_done))
        }
    }
}

@Composable
private fun ExportStatsGrid(
    stats: ExportStats,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            StatCell(
                label = stringResource(Res.string.export_category_journals),
                count = stats.journalCount,
                modifier = Modifier.weight(1f),
            )
            StatCell(
                label = stringResource(Res.string.export_category_notes),
                count = stats.noteCount,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            StatCell(
                label = stringResource(Res.string.export_category_drafts),
                count = stats.draftCount,
                modifier = Modifier.weight(1f),
            )
            StatCell(
                label = stringResource(Res.string.export_category_media),
                count = stats.mediaCount,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.md, horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ExportFailureCard(
    reason: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(Res.string.export_failed_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.dismiss))
                }
                Button(
                    onClick = onRetry,
                    modifier = Modifier.padding(start = Spacing.sm),
                ) {
                    Text(stringResource(Res.string.action_retry))
                }
            }
        }
    }
}
