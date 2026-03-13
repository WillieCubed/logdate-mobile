@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.feature.core.settings.ui.dialogs.ClearDataConfirmationDialog
import app.logdate.feature.core.settings.ui.dialogs.ResetAppConfirmationDialog
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.launch
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.action_reset_app
import logdate.client.feature.core.generated.resources.before_you_reset
import logdate.client.feature.core.generated.resources.before_you_reset_description
import logdate.client.feature.core.generated.resources.clear_all_data
import logdate.client.feature.core.generated.resources.clear_all_your_data_while_keeping_your_account
import logdate.client.feature.core.generated.resources.clear_data
import logdate.client.feature.core.generated.resources.clear_data_failed
import logdate.client.feature.core.generated.resources.clear_data_success
import logdate.client.feature.core.generated.resources.danger_zone
import logdate.client.feature.core.generated.resources.reset_actions
import logdate.client.feature.core.generated.resources.settings_reset_app_description
import logdate.client.feature.core.generated.resources.settings_reset_app_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Danger zone settings screen with destructive actions.
 *
 * This screen automatically adapts to different screen sizes:
 * - Large screens: Acts as a detail pane with minimal header (when in two-pane layout)
 * - Small screens: Standard screen with back navigation
 *
 * @param onBack Callback for when the user presses the back button
 * @param onAppReset Callback to reset the app
 */
@Composable
fun DangerZoneSettingsScreen(
    onBack: () -> Unit,
    onAppReset: () -> Unit,
    viewModel: DangerZoneSettingsViewModel = koinViewModel(),
) {
    DangerZoneSettingsContent(
        onBack = onBack,
        onAppReset = { viewModel.resetApp { onAppReset() } },
        onClearData = { onSuccess, onError -> viewModel.clearLocalData(onSuccess, onError) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DangerZoneSettingsContent(
    onBack: () -> Unit,
    onAppReset: () -> Unit,
    onClearData: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clearDataSuccessMessage = stringResource(Res.string.clear_data_success)
    val clearDataFailedMessage = stringResource(Res.string.clear_data_failed)
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(Res.string.danger_zone),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        topBarColors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                titleContentColor = MaterialTheme.colorScheme.error,
                navigationIconContentColor = MaterialTheme.colorScheme.error,
            ),
    ) {
        item {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(Res.string.reset_actions),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                MaterialContainer {
                    SurfaceItem {
                        ResetWarningItem()
                    }
                    SurfaceItem {
                        ResetAppItem(onShowResetDialog = { showResetDialog = true })
                    }
                    SurfaceItem {
                        ClearDataItem(onShowClearDataDialog = { showClearDataDialog = true })
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        ResetAppConfirmationDialog(
            onDismissRequest = { showResetDialog = false },
            onConfirmation = {
                onAppReset()
                showResetDialog = false
            },
        )
    }

    if (showClearDataDialog) {
        ClearDataConfirmationDialog(
            onDismissRequest = { showClearDataDialog = false },
            onConfirmation = {
                showClearDataDialog = false
                onClearData(
                    { scope.launch { snackbarHostState.showSnackbar(clearDataSuccessMessage) } },
                    { _ -> scope.launch { snackbarHostState.showSnackbar(clearDataFailedMessage) } },
                )
            },
        )
    }
}

@Composable
private fun ResetWarningItem() {
    ListItem(
        headlineContent = {
            Text(stringResource(Res.string.before_you_reset))
        },
        supportingContent = {
            Text(stringResource(Res.string.before_you_reset_description))
        },
        leadingContent = {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
    )
}

@Composable
private fun ResetAppItem(onShowResetDialog: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(Res.string.settings_reset_app_title),
                color = MaterialTheme.colorScheme.error,
            )
        },
        supportingContent = {
            Text(
                text = stringResource(Res.string.settings_reset_app_description),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        },
        leadingContent = {
            Icon(
                Icons.Default.RestartAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        trailingContent = {
            DangerOutlinedButton(
                onClick = onShowResetDialog,
                text = stringResource(Res.string.action_reset_app),
            )
        },
    )
}

@Composable
private fun ClearDataItem(onShowClearDataDialog: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(Res.string.clear_all_data),
                color = MaterialTheme.colorScheme.error,
            )
        },
        supportingContent = {
            Text(
                text = stringResource(Res.string.clear_all_your_data_while_keeping_your_account),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        },
        leadingContent = {
            Icon(
                Icons.Default.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        trailingContent = {
            DangerOutlinedButton(
                onClick = onShowClearDataDialog,
                text = stringResource(Res.string.clear_data),
            )
        },
    )
}

@Composable
private fun DangerOutlinedButton(
    onClick: () -> Unit,
    text: String,
) {
    OutlinedButton(
        onClick = onClick,
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.error,
            ),
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
    ) {
        Text(text)
    }
}

@Preview
@Composable
private fun DangerZoneSettingsScreenPreview() {
    DangerZoneSettingsContent(
        onBack = {},
        onAppReset = {},
        onClearData = { _, _ -> },
    )
}
