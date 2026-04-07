@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.restore

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.client.domain.export.ExportStats
import app.logdate.client.domain.restore.ArchivePreview
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateShort
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.export_category_drafts
import logdate.client.feature.core.generated.resources.export_category_item_count
import logdate.client.feature.core.generated.resources.export_category_media
import logdate.client.feature.core.generated.resources.import_backup
import logdate.client.feature.core.generated.resources.restore_choose_file
import logdate.client.feature.core.generated.resources.restore_confirm_description
import logdate.client.feature.core.generated.resources.restore_confirm_import
import logdate.client.feature.core.generated.resources.restore_error_file_not_accessible
import logdate.client.feature.core.generated.resources.restore_error_file_picker_failed
import logdate.client.feature.core.generated.resources.restore_error_file_picker_unavailable
import logdate.client.feature.core.generated.resources.restore_error_generic
import logdate.client.feature.core.generated.resources.restore_error_invalid_archive
import logdate.client.feature.core.generated.resources.restore_error_invalid_summary
import logdate.client.feature.core.generated.resources.restore_error_ios_requires_folder
import logdate.client.feature.core.generated.resources.restore_error_missing_source
import logdate.client.feature.core.generated.resources.restore_error_no_summary
import logdate.client.feature.core.generated.resources.restore_error_unsupported_version
import logdate.client.feature.core.generated.resources.restore_import_options_label
import logdate.client.feature.core.generated.resources.restore_merge_info
import logdate.client.feature.core.generated.resources.restore_preview_description
import logdate.client.ui.generated.resources.common_cancel
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RestoreBottomSheet(
    restoreState: RestoreState,
    onSelectFile: () -> Unit,
    onUpdateOptions: (ImportOptions) -> Unit,
    onConfirmImport: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
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
                targetState = restoreState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentKey = { it::class },
                label = "restore-sheet-state",
            ) { state ->
                when (state) {
                    is RestoreState.Confirming -> {
                        RestoreConfirmContent(
                            onSelectFile = onSelectFile,
                            onDismiss = onDismiss,
                        )
                    }
                    is RestoreState.Selecting -> {
                        RestoreConfirmContent(
                            onSelectFile = onSelectFile,
                            onDismiss = onDismiss,
                            buttonsEnabled = false,
                        )
                    }
                    is RestoreState.Previewing -> {
                        RestorePreviewContent(
                            preview = state.preview,
                            fileName = state.fileName,
                            options = state.options,
                            onOptionsChanged = onUpdateOptions,
                            onConfirmImport = onConfirmImport,
                            onDismiss = onDismiss,
                        )
                    }
                    is RestoreState.Restoring -> {
                        RestoreProgressCard(
                            progressPercent = state.progressPercent,
                            message = resolveStageText(state.stage),
                            onCancel = onCancel,
                        )
                    }
                    is RestoreState.Completed -> {
                        RestoreSuccessCard(
                            summary = state.summary,
                            onDismiss = onDismiss,
                        )
                    }
                    is RestoreState.Failed -> {
                        RestoreFailureCard(
                            reason = resolveErrorText(state.error),
                            onRetry = onRetry,
                            onDismiss = onDismiss,
                        )
                    }
                    is RestoreState.Idle -> {
                        Spacer(modifier = Modifier.height(Spacing.lg))
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun RestoreConfirmContent(
    onSelectFile: () -> Unit,
    onDismiss: () -> Unit,
    buttonsEnabled: Boolean = true,
) {
    Column {
        Text(
            text = stringResource(Res.string.import_backup),
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        Text(
            text = stringResource(Res.string.restore_confirm_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.restore_merge_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onDismiss,
                enabled = buttonsEnabled,
            ) {
                Text(stringResource(UiRes.string.common_cancel))
            }
            Button(
                onClick = onSelectFile,
                enabled = buttonsEnabled,
                modifier = Modifier.padding(start = Spacing.sm),
            ) {
                Text(stringResource(Res.string.restore_choose_file))
            }
        }
    }
}

@Composable
private fun RestorePreviewContent(
    preview: ArchivePreview,
    fileName: String,
    options: ImportOptions,
    onOptionsChanged: (ImportOptions) -> Unit,
    onConfirmImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column {
        Text(
            text = stringResource(Res.string.import_backup),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = stringResource(Res.string.restore_preview_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.labelLarge,
                )
                val details =
                    buildList {
                        add("Exported ${preview.exportDate.toReadableDateShort()}")
                        if (preview.appVersion.isNotBlank()) {
                            add("v${preview.appVersion}")
                        }
                    }.joinToString(" \u00B7 ")
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.lg))

        PreviewStatsGrid(stats = preview.stats)

        Spacer(modifier = Modifier.height(Spacing.lg))

        Text(
            text = stringResource(Res.string.restore_import_options_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))

        if (preview.hasDrafts) {
            ImportCategoryToggle(
                label = stringResource(Res.string.export_category_drafts),
                count = preview.stats.draftCount,
                checked = options.includeDrafts,
                onCheckedChange = { onOptionsChanged(options.copy(includeDrafts = it)) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Spacing.md),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            )
        }
        if (preview.hasMedia) {
            ImportCategoryToggle(
                label = stringResource(Res.string.export_category_media),
                count = preview.stats.mediaCount,
                checked = options.includeMedia,
                onCheckedChange = { onOptionsChanged(options.copy(includeMedia = it)) },
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.restore_merge_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(UiRes.string.common_cancel))
            }
            Button(
                onClick = onConfirmImport,
                modifier = Modifier.padding(start = Spacing.sm),
            ) {
                Text(stringResource(Res.string.restore_confirm_import))
            }
        }
    }
}

@Composable
private fun PreviewStatsGrid(
    stats: ExportStats,
    modifier: Modifier = Modifier,
) {
    app.logdate.feature.core.common.DataStatsGrid(
        journalCount = stats.journalCount,
        noteCount = stats.noteCount,
        draftCount = stats.draftCount,
        mediaCount = stats.mediaCount,
        modifier = modifier,
    )
}

@Composable
private fun ImportCategoryToggle(
    label: String,
    count: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(
                text = stringResource(Res.string.export_category_item_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

@Composable
private fun resolveStageText(stage: RestoreStage): String = stage.labelResource?.let { stringResource(it) }.orEmpty()

@Composable
private fun resolveErrorText(error: RestoreError): String =
    when (error) {
        RestoreError.INVALID_ARCHIVE -> stringResource(Res.string.restore_error_invalid_archive)
        RestoreError.MISSING_SOURCE -> stringResource(Res.string.restore_error_missing_source)
        RestoreError.FILE_NOT_ACCESSIBLE -> stringResource(Res.string.restore_error_file_not_accessible)
        RestoreError.FILE_PICKER_UNAVAILABLE -> stringResource(Res.string.restore_error_file_picker_unavailable)
        RestoreError.FILE_PICKER_FAILED -> stringResource(Res.string.restore_error_file_picker_failed)
        RestoreError.INVALID_SUMMARY -> stringResource(Res.string.restore_error_invalid_summary)
        RestoreError.NO_SUMMARY_RETURNED -> stringResource(Res.string.restore_error_no_summary)
        RestoreError.IOS_REQUIRES_FOLDER -> stringResource(Res.string.restore_error_ios_requires_folder)
        RestoreError.UNSUPPORTED_VERSION -> stringResource(Res.string.restore_error_unsupported_version)
        RestoreError.RESTORE_FAILED -> stringResource(Res.string.restore_error_generic)
    }
