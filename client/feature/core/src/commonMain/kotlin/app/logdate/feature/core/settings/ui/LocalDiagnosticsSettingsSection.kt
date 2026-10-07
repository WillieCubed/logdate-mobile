@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.ToggleSettingsItem
import app.logdate.ui.common.disabledAlpha
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.diagnostics_clear
import logdate.client.feature.core.generated.resources.diagnostics_clear_description
import logdate.client.feature.core.generated.resources.diagnostics_clear_failed
import logdate.client.feature.core.generated.resources.diagnostics_clear_message
import logdate.client.feature.core.generated.resources.diagnostics_clear_title
import logdate.client.feature.core.generated.resources.diagnostics_cleared
import logdate.client.feature.core.generated.resources.diagnostics_empty
import logdate.client.feature.core.generated.resources.diagnostics_export
import logdate.client.feature.core.generated.resources.diagnostics_export_description
import logdate.client.feature.core.generated.resources.diagnostics_export_failed
import logdate.client.feature.core.generated.resources.diagnostics_exported
import logdate.client.feature.core.generated.resources.diagnostics_local_description
import logdate.client.feature.core.generated.resources.diagnostics_local_title
import logdate.client.feature.core.generated.resources.diagnostics_preview
import logdate.client.feature.core.generated.resources.diagnostics_verbose_description
import logdate.client.feature.core.generated.resources.diagnostics_verbose_remaining
import logdate.client.feature.core.generated.resources.diagnostics_verbose_title
import logdate.client.ui.generated.resources.common_cancel
import logdate.client.ui.generated.resources.common_close
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

@Composable
internal fun LocalDiagnosticsSettingsSection(
    state: LocalDiagnosticsState,
    onPreview: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
    onSetVerboseEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPreview by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val hasHistory = state.eventCount > 0
    Column(modifier = modifier) {
        SettingsSection(title = stringResource(Res.string.diagnostics_local_title)) {
            val verboseMinutes = ((state.verboseRemainingMillis + 59_999L) / 60_000L).toInt()
            ToggleSettingsItem(
                title = stringResource(Res.string.diagnostics_verbose_title),
                description =
                    if (verboseMinutes > 0) {
                        stringResource(Res.string.diagnostics_verbose_remaining, verboseMinutes)
                    } else {
                        stringResource(Res.string.diagnostics_verbose_description)
                    },
                checked = verboseMinutes > 0,
                onCheckedChange = onSetVerboseEnabled,
            )
            DiagnosticsActionItem(
                icon = Icons.Outlined.Description,
                title = stringResource(Res.string.diagnostics_preview),
                description = stringResource(Res.string.diagnostics_local_description),
                onClick = {
                    onPreview()
                    showPreview = true
                },
            )
            DiagnosticsActionItem(
                icon = Icons.Outlined.FileDownload,
                title = stringResource(Res.string.diagnostics_export),
                description = stringResource(Res.string.diagnostics_export_description),
                onClick = onExport,
            )
            DiagnosticsActionItem(
                icon = Icons.Outlined.Delete,
                title = stringResource(Res.string.diagnostics_clear),
                description = stringResource(Res.string.diagnostics_clear_description),
                enabled = hasHistory,
                onClick = { confirmClear = true },
            )
        }
        state.feedback?.let { feedback ->
            val resource =
                when (feedback) {
                    LocalDiagnosticsFeedback.EXPORTED -> Res.string.diagnostics_exported
                    LocalDiagnosticsFeedback.EXPORT_FAILED -> Res.string.diagnostics_export_failed
                    LocalDiagnosticsFeedback.CLEARED -> Res.string.diagnostics_cleared
                    LocalDiagnosticsFeedback.CLEAR_FAILED -> Res.string.diagnostics_clear_failed
                }
            Text(
                stringResource(resource),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
    if (showPreview) {
        DiagnosticsPreviewDialog(
            preview = state.preview,
            onDismiss = { showPreview = false },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(Res.string.diagnostics_clear_title)) },
            text = { Text(stringResource(Res.string.diagnostics_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClear()
                }) { Text(stringResource(Res.string.diagnostics_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(UiRes.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun DiagnosticsActionItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description, style = MaterialTheme.typography.bodySmall) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        modifier =
            Modifier
                .disabledAlpha(enabled)
                .clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun DiagnosticsPreviewDialog(
    preview: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.diagnostics_preview)) },
        text = {
            if (preview == null) {
                Text(stringResource(Res.string.diagnostics_empty))
            } else {
                SelectionContainer(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(preview, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(UiRes.string.common_close)) }
        },
    )
}
