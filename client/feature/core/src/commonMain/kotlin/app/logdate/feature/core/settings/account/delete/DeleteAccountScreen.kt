@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.account.delete

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.account_delete_title
import logdate.client.feature.core.generated.resources.delete_account_action
import logdate.client.feature.core.generated.resources.delete_account_confirm_message
import logdate.client.feature.core.generated.resources.delete_account_confirm_message_erase
import logdate.client.feature.core.generated.resources.delete_account_confirm_title
import logdate.client.feature.core.generated.resources.delete_account_done_body
import logdate.client.feature.core.generated.resources.delete_account_done_title
import logdate.client.feature.core.generated.resources.delete_account_erase_device
import logdate.client.feature.core.generated.resources.delete_account_erase_device_description
import logdate.client.feature.core.generated.resources.delete_account_failed_offline
import logdate.client.feature.core.generated.resources.delete_account_failed_server
import logdate.client.feature.core.generated.resources.delete_account_failed_signed_out
import logdate.client.feature.core.generated.resources.delete_account_failed_unavailable
import logdate.client.feature.core.generated.resources.delete_account_what_account
import logdate.client.feature.core.generated.resources.delete_account_what_passkeys
import logdate.client.feature.core.generated.resources.delete_account_what_permanent
import logdate.client.feature.core.generated.resources.delete_account_what_synced
import logdate.client.ui.generated.resources.common_cancel
import logdate.client.ui.generated.resources.common_done
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import logdate.client.ui.generated.resources.Res as UiRes

/**
 * Deletes the account after spelling out what goes and what stays.
 *
 * @param onDeviceErased called once the account is deleted and this device erased, so the app can
 *   return to its first-run state
 */
@Composable
fun DeleteAccountScreen(
    onBack: () -> Unit,
    onDeviceErased: () -> Unit,
    viewModel: DeleteAccountViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The view model outlives this screen, so every visit starts from the choices, not a result.
    LaunchedEffect(Unit) { viewModel.reset() }
    LaunchedEffect(state.phase) {
        val phase = state.phase
        if (phase is DeleteAccountUiState.Phase.Deleted && phase.erasedThisDevice) onDeviceErased()
    }

    DeleteAccountContent(
        state = state,
        onBack = onBack,
        onEraseThisDeviceChange = viewModel::setEraseThisDevice,
        onDelete = viewModel::delete,
    )
}

@Composable
fun DeleteAccountContent(
    state: DeleteAccountUiState,
    onBack: () -> Unit,
    onEraseThisDeviceChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }

    SettingsScaffold(title = stringResource(Res.string.account_delete_title), onBack = onBack) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                val phase = state.phase
                if (phase is DeleteAccountUiState.Phase.Deleted) {
                    Text(stringResource(Res.string.delete_account_done_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(Res.string.delete_account_done_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onBack) { Text(stringResource(UiRes.string.common_done)) }
                    return@Column
                }

                MaterialContainer {
                    Consequence(Icons.Outlined.PersonOff, stringResource(Res.string.delete_account_what_account, state.serverName))
                    Consequence(Icons.Outlined.CloudOff, stringResource(Res.string.delete_account_what_synced))
                    Consequence(Icons.Outlined.Key, stringResource(Res.string.delete_account_what_passkeys))
                    Consequence(Icons.Outlined.WarningAmber, stringResource(Res.string.delete_account_what_permanent))
                }
                MaterialContainer {
                    ListItem(
                        leadingContent = { Checkbox(checked = state.eraseThisDevice, onCheckedChange = null) },
                        headlineContent = { Text(stringResource(Res.string.delete_account_erase_device)) },
                        supportingContent = { Text(stringResource(Res.string.delete_account_erase_device_description)) },
                        modifier =
                            Modifier.toggleable(
                                value = state.eraseThisDevice,
                                enabled = phase != DeleteAccountUiState.Phase.Deleting,
                                role = Role.Checkbox,
                                onValueChange = onEraseThisDeviceChange,
                            ),
                    )
                }
                if (phase is DeleteAccountUiState.Phase.Failed) {
                    Text(
                        text = failureMessage(phase.reason, state.serverName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = { confirming = true },
                    enabled = phase != DeleteAccountUiState.Phase.Deleting,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (phase == DeleteAccountUiState.Phase.Deleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError,
                        )
                    } else {
                        Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                        Text(stringResource(Res.string.delete_account_action), modifier = Modifier.padding(start = Spacing.sm))
                    }
                }
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(Res.string.delete_account_confirm_title)) },
            text = {
                val message =
                    if (state.eraseThisDevice) {
                        Res.string.delete_account_confirm_message_erase
                    } else {
                        Res.string.delete_account_confirm_message
                    }
                Text(stringResource(message, state.serverName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onDelete()
                    },
                ) { Text(stringResource(Res.string.delete_account_action), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(UiRes.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun Consequence(
    icon: ImageVector,
    text: String,
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(text, style = MaterialTheme.typography.bodyMedium) },
    )
}

@Composable
private fun failureMessage(
    reason: DeleteAccountFailure,
    serverName: String,
): String =
    when (reason) {
        DeleteAccountFailure.OFFLINE -> stringResource(Res.string.delete_account_failed_offline, serverName)
        DeleteAccountFailure.UNAVAILABLE -> stringResource(Res.string.delete_account_failed_unavailable, serverName)
        DeleteAccountFailure.NOT_SIGNED_IN -> stringResource(Res.string.delete_account_failed_signed_out)
        DeleteAccountFailure.SERVER -> stringResource(Res.string.delete_account_failed_server, serverName)
    }

@Preview
@Composable
private fun DeleteAccountPreview() {
    DeleteAccountContent(
        state = DeleteAccountUiState(serverName = "LogDate Cloud"),
        onBack = {},
        onEraseThisDeviceChange = {},
        onDelete = {},
    )
}
