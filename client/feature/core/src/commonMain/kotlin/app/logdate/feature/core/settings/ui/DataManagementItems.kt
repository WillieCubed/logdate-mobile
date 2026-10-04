@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateTimeShort
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.audit_and_repair_local_links_and_sync_metadata
import logdate.client.feature.core.generated.resources.check
import logdate.client.feature.core.generated.resources.checking
import logdate.client.feature.core.generated.resources.export
import logdate.client.feature.core.generated.resources.`import`
import logdate.client.feature.core.generated.resources.import_backup
import logdate.client.feature.core.generated.resources.integrity_check
import logdate.client.feature.core.generated.resources.last_check_issue_count
import logdate.client.feature.core.generated.resources.repair
import logdate.client.feature.core.generated.resources.repairing
import logdate.client.feature.core.generated.resources.restore_entries_from_a_logdate_export_archive
import logdate.client.feature.core.generated.resources.settings_export_entries_description
import logdate.client.feature.core.generated.resources.settings_export_entries_label
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExportDataItem(onShowExportOptions: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(Res.string.settings_export_entries_label)) },
        supportingContent = {
            Text(stringResource(Res.string.settings_export_entries_description))
        },
        trailingContent = {
            Button(onClick = onShowExportOptions) {
                Text(stringResource(Res.string.export))
            }
        },
    )
}

@Composable
internal fun ImportBackupItem(onShowRestoreSheet: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(Res.string.import_backup)) },
        supportingContent = {
            Text(stringResource(Res.string.restore_entries_from_a_logdate_export_archive))
        },
        trailingContent = {
            Button(onClick = onShowRestoreSheet) {
                Text(stringResource(Res.string.`import`))
            }
        },
    )
}

@Composable
internal fun IntegrityCheckItem(
    integrityState: IntegrityState,
    onRunIntegrityCheck: () -> Unit,
    onRepairIntegrity: () -> Unit,
) {
    val report = integrityState.lastReport
    val issueCount =
        report?.let {
            it.orphanedJournalLinks +
                it.orphanedContentLinks +
                it.pendingMissingJournals +
                it.pendingMissingNotes +
                it.pendingAssociationMissingLinks +
                it.pendingAssociationMalformed
        } ?: 0
    ListItem(
        headlineContent = { Text(stringResource(Res.string.integrity_check)) },
        supportingContent = {
            Column {
                Text(stringResource(Res.string.audit_and_repair_local_links_and_sync_metadata))
                IntegrityReportText(report, issueCount)
                IntegrityErrorText(integrityState.errorMessage)
            }
        },
        trailingContent = {
            Column {
                Button(
                    onClick = onRunIntegrityCheck,
                    enabled = !integrityState.isChecking,
                ) {
                    Text(
                        if (integrityState.isChecking) {
                            stringResource(Res.string.checking)
                        } else {
                            stringResource(Res.string.check)
                        },
                    )
                }
                TextButton(
                    onClick = onRepairIntegrity,
                    enabled = issueCount > 0 && !integrityState.isRepairing,
                ) {
                    Text(
                        if (integrityState.isRepairing) {
                            stringResource(Res.string.repairing)
                        } else {
                            stringResource(Res.string.repair)
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun IntegrityReportText(
    report: app.logdate.client.data.maintenance.IntegrityReport?,
    issueCount: Int,
) {
    report?.let {
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text =
                stringResource(
                    Res.string.last_check_issue_count,
                    it.checkedAt.toReadableDateTimeShort(),
                    issueCount,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IntegrityErrorText(errorMessage: String?) {
    errorMessage?.let { message ->
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
