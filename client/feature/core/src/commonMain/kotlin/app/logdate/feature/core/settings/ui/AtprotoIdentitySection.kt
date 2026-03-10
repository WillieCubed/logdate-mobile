@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.atproto_did
import logdate.client.feature.core.generated.resources.atproto_handle
import logdate.client.feature.core.generated.resources.atproto_identity
import logdate.client.feature.core.generated.resources.atproto_identity_export_bundle
import logdate.client.feature.core.generated.resources.atproto_identity_loading
import logdate.client.feature.core.generated.resources.atproto_import_signing_key
import logdate.client.feature.core.generated.resources.atproto_no_plc_operations
import logdate.client.feature.core.generated.resources.atproto_not_registered
import logdate.client.feature.core.generated.resources.atproto_operation_history
import logdate.client.feature.core.generated.resources.atproto_passphrase
import logdate.client.feature.core.generated.resources.atproto_plc_operation_count
import logdate.client.feature.core.generated.resources.atproto_plc_recovery_key
import logdate.client.feature.core.generated.resources.atproto_refresh_identity
import logdate.client.feature.core.generated.resources.atproto_register_plc_recovery_key
import logdate.client.feature.core.generated.resources.atproto_rotate_signing_key
import logdate.client.feature.core.generated.resources.atproto_signing_key
import logdate.client.feature.core.generated.resources.atproto_signing_key_json
import logdate.client.feature.core.generated.resources.cancel
import logdate.client.feature.core.generated.resources.confirm
import logdate.client.feature.core.generated.resources.dismiss
import logdate.client.feature.core.generated.resources.export
import logdate.client.feature.core.generated.resources.save
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtprotoIdentitySection(
    identityState: AccountIdentityState,
    onRefresh: () -> Unit,
    onExportSigningKey: (String) -> Unit,
    onRotateSigningKey: (String) -> Unit,
    onImportSigningKey: (String, String) -> Unit,
    onRegisterPlcRecoveryKey: (String) -> Unit,
    onClearIdentityActionState: () -> Unit,
    onClearExportedKeyJson: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showExportDialog by remember { mutableStateOf(false) }
    var showRotateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showRecoveryKeyDialog by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(Res.string.atproto_identity),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = onRefresh) {
                Text(stringResource(Res.string.atproto_refresh_identity))
            }
        }

        MaterialContainer {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.padding(Spacing.lg),
            ) {
                when {
                    identityState.isLoading && identityState.status == null -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(stringResource(Res.string.atproto_identity_loading))
                        }
                    }

                    identityState.status != null -> {
                        val status = identityState.status
                        IdentityValue(stringResource(Res.string.atproto_handle), status.handle)
                        IdentityValue(stringResource(Res.string.atproto_did), status.did)
                        IdentityValue(stringResource(Res.string.atproto_signing_key), status.signingKeyDidKey)
                        IdentityValue(
                            stringResource(Res.string.atproto_plc_recovery_key),
                            status.plcRecoveryDidKey ?: stringResource(Res.string.atproto_not_registered),
                        )
                        IdentityValue(
                            stringResource(Res.string.atproto_plc_operation_count),
                            status.plcOperationCount.toString(),
                        )
                    }
                }

                when (val actionState = identityState.actionState) {
                    is IdentityActionState.Working -> {
                        StatusCard(actionState.label, isError = false)
                    }
                    is IdentityActionState.Success -> {
                        StatusCard(actionState.message, isError = false, onDismiss = onClearIdentityActionState)
                    }
                    is IdentityActionState.Error -> {
                        StatusCard(actionState.message, isError = true, onDismiss = onClearIdentityActionState)
                    }
                    IdentityActionState.Idle -> Unit
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Button(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.export))
                    }

                    OutlinedButton(
                        onClick = { showRotateDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.atproto_rotate_signing_key))
                    }

                    OutlinedButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.atproto_import_signing_key))
                    }

                    OutlinedButton(
                        onClick = { showRecoveryKeyDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.atproto_register_plc_recovery_key))
                    }
                }

                identityState.exportedKeyJson?.let { exportedKeyJson ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = stringResource(Res.string.atproto_identity_export_bundle),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        OutlinedTextField(
                            value = exportedKeyJson,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(Res.string.atproto_signing_key_json)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(onClick = onClearExportedKeyJson) {
                            Text(stringResource(Res.string.dismiss))
                        }
                    }
                }

                Text(
                    text = stringResource(Res.string.atproto_operation_history),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (identityState.operations.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.atproto_no_plc_operations),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        identityState.operations.forEach { operation ->
                            Card(
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                            ) {
                                ListItem(
                                    headlineContent = { Text(operation.operationType) },
                                    supportingContent = {
                                        Column {
                                            Text(operation.createdAt)
                                            operation.cid?.let { Text(it) }
                                        }
                                    },
                                    leadingContent = {
                                        Icon(Icons.Default.Cloud, contentDescription = null)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        PassphraseDialog(
            title = stringResource(Res.string.export),
            onDismiss = { showExportDialog = false },
            onConfirm = { passphrase ->
                onExportSigningKey(passphrase)
                showExportDialog = false
            },
        )
    }

    if (showRotateDialog) {
        PassphraseDialog(
            title = stringResource(Res.string.atproto_rotate_signing_key),
            onDismiss = { showRotateDialog = false },
            onConfirm = { passphrase ->
                onRotateSigningKey(passphrase)
                showRotateDialog = false
            },
        )
    }

    if (showImportDialog) {
        ImportSigningKeyDialog(
            onDismiss = { showImportDialog = false },
            onConfirm = { passphrase, exportedKeyJson ->
                onImportSigningKey(passphrase, exportedKeyJson)
                showImportDialog = false
            },
        )
    }

    if (showRecoveryKeyDialog) {
        RecoveryDidKeyDialog(
            onDismiss = { showRecoveryKeyDialog = false },
            onConfirm = { recoveryDidKey ->
                onRegisterPlcRecoveryKey(recoveryDidKey)
                showRecoveryKeyDialog = false
            },
        )
    }
}

@Composable
private fun IdentityValue(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun StatusCard(
    message: String,
    isError: Boolean,
    onDismiss: (() -> Unit)? = null,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
            ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(Spacing.md),
        ) {
            Text(
                text = message,
                color =
                    if (isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
            )
            onDismiss?.let {
                TextButton(onClick = it) {
                    Text(stringResource(Res.string.dismiss))
                }
            }
        }
    }
}

@Composable
private fun PassphraseDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text(stringResource(Res.string.atproto_passphrase)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(passphrase) },
                enabled = passphrase.isNotBlank(),
            ) {
                Text(stringResource(Res.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun ImportSigningKeyDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var exportedKeyJson by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.atproto_import_signing_key)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(Res.string.atproto_passphrase)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next,
                        ),
                )
                OutlinedTextField(
                    value = exportedKeyJson,
                    onValueChange = { exportedKeyJson = it },
                    label = { Text(stringResource(Res.string.atproto_signing_key_json)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(passphrase, exportedKeyJson) },
                enabled = passphrase.isNotBlank() && exportedKeyJson.isNotBlank(),
            ) {
                Text(stringResource(Res.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun RecoveryDidKeyDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var recoveryDidKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.atproto_register_plc_recovery_key)) },
        text = {
            OutlinedTextField(
                value = recoveryDidKey,
                onValueChange = { recoveryDidKey = it },
                label = { Text(stringResource(Res.string.atproto_plc_recovery_key)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(recoveryDidKey) },
                enabled = recoveryDidKey.isNotBlank(),
            ) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
