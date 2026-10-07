@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.client.sync.diagnostics.DiagnosticConsent
import app.logdate.client.sync.diagnostics.DiagnosticReportingController
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.ToggleSettingsItem
import kotlinx.coroutines.launch
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.diagnostic_reporting_consent_body
import logdate.client.feature.core.generated.resources.diagnostic_reporting_consent_title
import logdate.client.feature.core.generated.resources.diagnostic_reporting_delete
import logdate.client.feature.core.generated.resources.diagnostic_reporting_delete_body
import logdate.client.feature.core.generated.resources.diagnostic_reporting_delete_title
import logdate.client.feature.core.generated.resources.diagnostic_reporting_deleted
import logdate.client.feature.core.generated.resources.diagnostic_reporting_description
import logdate.client.feature.core.generated.resources.diagnostic_reporting_destination
import logdate.client.feature.core.generated.resources.diagnostic_reporting_failed
import logdate.client.feature.core.generated.resources.diagnostic_reporting_logs_notice
import logdate.client.feature.core.generated.resources.diagnostic_reporting_pending
import logdate.client.feature.core.generated.resources.diagnostic_reporting_title
import logdate.client.feature.core.generated.resources.diagnostic_reporting_unavailable
import logdate.client.ui.generated.resources.common_cancel
import logdate.client.ui.generated.resources.common_confirm
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

private data class ReportingConfirmation(
    val consent: DiagnosticConsent,
    val delete: Boolean,
)

@Composable
internal fun AutomaticDiagnosticReportingSection(controller: DiagnosticReportingController) {
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmation by remember { mutableStateOf<ReportingConfirmation?>(null) }
    var failed by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }
    LaunchedEffect(controller) { controller.refresh() }
    SettingsSection(title = stringResource(Res.string.diagnostic_reporting_title)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.destination?.let { Text(stringResource(Res.string.diagnostic_reporting_destination, it)) }
            if (state.available || state.enabled) {
                ToggleSettingsItem(
                    title = stringResource(Res.string.diagnostic_reporting_title),
                    description = stringResource(Res.string.diagnostic_reporting_description),
                    checked = state.enabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            failed = false
                            if (enabled) {
                                val consent = controller.prepareConsent()
                                if (consent == null) failed = true else confirmation = ReportingConfirmation(consent, false)
                            } else {
                                failed = !controller.disable()
                            }
                        }
                    },
                )
                if (state.pendingReports > 0) Text(stringResource(Res.string.diagnostic_reporting_pending, state.pendingReports))
                TextButton(enabled = state.available, onClick = {
                    scope.launch {
                        val consent = controller.prepareConsent()
                        if (consent == null) failed = true else confirmation = ReportingConfirmation(consent, true)
                    }
                }) { Text(stringResource(Res.string.diagnostic_reporting_delete)) }
            } else {
                Text(stringResource(Res.string.diagnostic_reporting_unavailable))
            }
            Text(stringResource(Res.string.diagnostic_reporting_logs_notice))
            if (failed || state.storageFailed) Text(stringResource(Res.string.diagnostic_reporting_failed))
            if (deleted) Text(stringResource(Res.string.diagnostic_reporting_deleted))
        }
    }
    confirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = {
                Text(
                    stringResource(
                        if (pending.delete) Res.string.diagnostic_reporting_delete_title else Res.string.diagnostic_reporting_consent_title,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (pending.delete) Res.string.diagnostic_reporting_delete_body else Res.string.diagnostic_reporting_consent_body,
                        pending.consent.destination,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    scope.launch {
                        if (pending.delete) {
                            deleted = controller.deleteUploadedReports(pending.consent)
                            failed = !deleted
                        } else {
                            failed = !controller.enable(pending.consent)
                        }
                    }
                }) { Text(stringResource(UiRes.string.common_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(UiRes.string.common_cancel)) } },
        )
    }
}
