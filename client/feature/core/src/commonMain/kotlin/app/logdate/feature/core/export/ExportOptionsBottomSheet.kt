@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.export

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.logdate.client.domain.export.ExportCounts
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.cancel
import logdate.client.feature.core.generated.resources.export
import logdate.client.feature.core.generated.resources.export_category_drafts
import logdate.client.feature.core.generated.resources.export_category_item_count
import logdate.client.feature.core.generated.resources.export_category_journals
import logdate.client.feature.core.generated.resources.export_category_media
import logdate.client.feature.core.generated.resources.export_category_notes
import logdate.client.feature.core.generated.resources.export_date_range_all_time
import logdate.client.feature.core.generated.resources.export_date_range_all_time_hint
import logdate.client.feature.core.generated.resources.export_date_range_filtered_hint
import logdate.client.feature.core.generated.resources.export_date_range_label
import logdate.client.feature.core.generated.resources.export_date_range_last_30_days
import logdate.client.feature.core.generated.resources.export_date_range_last_90_days
import logdate.client.feature.core.generated.resources.export_date_range_last_year
import logdate.client.feature.core.generated.resources.export_options_description
import logdate.client.feature.core.generated.resources.export_options_title
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom sheet that contains the entire export session lifecycle:
 * configuration, progress, success, and failure states.
 *
 * The sheet stays open from the moment the user taps Export until they
 * dismiss the result or cancel the operation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExportBottomSheet(
    exportState: ExportState,
    onOptionsChanged: (ExportOptions) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onBrowse: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .navigationBarsPadding(),
        ) {
            AnimatedContent(
                targetState = exportState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "export-sheet-state",
            ) { state ->
                when (state) {
                    is ExportState.Configuring -> {
                        ExportConfigContent(
                            options = state.options,
                            counts = state.counts,
                            onOptionsChanged = onOptionsChanged,
                            onConfirm = onConfirm,
                            onDismiss = onDismiss,
                        )
                    }
                    is ExportState.Selecting -> {
                        // SAF picker is open on top — show config with disabled button
                        ExportConfigContent(
                            options = ExportOptions(),
                            counts = null,
                            onOptionsChanged = {},
                            onConfirm = {},
                            onDismiss = onDismiss,
                            buttonsEnabled = false,
                        )
                    }
                    is ExportState.Exporting -> {
                        ExportProgressCard(
                            progressPercent = state.progressPercent,
                            message = state.message,
                            onCancel = onCancel,
                        )
                    }
                    is ExportState.Completed -> {
                        ExportSuccessCard(
                            fileName = state.fileName,
                            stats = state.stats,
                            onBrowse = { onBrowse(state.path) },
                            onDismiss = onDismiss,
                        )
                    }
                    is ExportState.Failed -> {
                        ExportFailureCard(
                            reason = state.reason,
                            onRetry = onRetry,
                            onDismiss = onDismiss,
                        )
                    }
                    is ExportState.Idle -> {
                        // Sheet is being dismissed, show nothing
                        Spacer(modifier = Modifier.height(Spacing.lg))
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun ExportConfigContent(
    options: ExportOptions,
    counts: ExportCounts?,
    onOptionsChanged: (ExportOptions) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    buttonsEnabled: Boolean = true,
) {
    Column {
        Text(
            text = stringResource(Res.string.export_options_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = stringResource(Res.string.export_options_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.lg))

        // Category toggles
        ExportCategoryToggle(
            label = stringResource(Res.string.export_category_journals),
            count = counts?.journalCount,
            checked = options.includeJournals,
            onCheckedChange = { onOptionsChanged(options.copy(includeJournals = it)) },
            enabled = buttonsEnabled,
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = Spacing.md),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        )
        ExportCategoryToggle(
            label = stringResource(Res.string.export_category_notes),
            count = counts?.noteCount,
            checked = options.includeNotes,
            onCheckedChange = { onOptionsChanged(options.copy(includeNotes = it)) },
            enabled = buttonsEnabled,
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = Spacing.md),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        )
        ExportCategoryToggle(
            label = stringResource(Res.string.export_category_drafts),
            count = counts?.draftCount,
            checked = options.includeDrafts,
            onCheckedChange = { onOptionsChanged(options.copy(includeDrafts = it)) },
            enabled = buttonsEnabled,
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = Spacing.md),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        )
        ExportCategoryToggle(
            label = stringResource(Res.string.export_category_media),
            count = counts?.mediaCount,
            checked = options.includeMedia,
            onCheckedChange = { onOptionsChanged(options.copy(includeMedia = it)) },
            enabled = buttonsEnabled,
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        // Date range selector
        DateRangeSelector(
            dateRange = options.dateRange,
            onDateRangeChanged = { onOptionsChanged(options.copy(dateRange = it)) },
            enabled = buttonsEnabled,
        )

        Spacer(modifier = Modifier.height(Spacing.xl))

        // Actions
        val hasAnySelected =
            options.includeJournals ||
                options.includeNotes ||
                options.includeDrafts ||
                options.includeMedia
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onDismiss,
                enabled = buttonsEnabled,
            ) {
                Text(stringResource(Res.string.cancel))
            }
            Button(
                onClick = onConfirm,
                enabled = buttonsEnabled && hasAnySelected,
                modifier = Modifier.padding(start = Spacing.sm),
            ) {
                Text(stringResource(Res.string.export))
            }
        }
    }
}

@Composable
private fun ExportCategoryToggle(
    label: String,
    count: Int?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent =
            count?.let {
                {
                    Text(
                        text = stringResource(Res.string.export_category_item_count, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
    )
}

@Composable
private fun DateRangeSelector(
    dateRange: ExportDateRange,
    onDateRangeChanged: (ExportDateRange) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = stringResource(Res.string.export_date_range_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))

        val options =
            listOf(
                stringResource(Res.string.export_date_range_all_time) to ExportDateRange.AllTime,
                stringResource(Res.string.export_date_range_last_30_days) to ExportDateRange.Last30Days,
                stringResource(Res.string.export_date_range_last_90_days) to ExportDateRange.Last90Days,
                stringResource(Res.string.export_date_range_last_year) to ExportDateRange.LastYear,
            )

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            options.forEach { (label, range) ->
                val isSelected = dateRange == range
                if (isSelected) {
                    Button(
                        onClick = {},
                        enabled = enabled,
                    ) {
                        Text(label)
                    }
                } else {
                    TextButton(
                        onClick = { onDateRangeChanged(range) },
                        enabled = enabled,
                    ) {
                        Text(label)
                    }
                }
            }
        }

        if (dateRange is ExportDateRange.AllTime) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(Res.string.export_date_range_all_time_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (dateRange !is ExportDateRange.Custom) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(Res.string.export_date_range_filtered_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
