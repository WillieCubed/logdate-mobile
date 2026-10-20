@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.account.recoverykey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.logdate.ui.common.AlertDialog
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_action
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_intro
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_step_check
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_step_passkey
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_step_rank
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_step_replace
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_step_unchanged
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_title
import logdate.client.feature.core.generated.resources.recovery_key_replace_key_confirm_action
import logdate.client.feature.core.generated.resources.recovery_key_replace_key_confirm_body
import logdate.client.feature.core.generated.resources.recovery_key_replace_key_confirm_title
import logdate.client.feature.core.generated.resources.recovery_key_turn_on_confirm_action
import logdate.client.feature.core.generated.resources.recovery_key_turn_on_confirm_body
import logdate.client.feature.core.generated.resources.recovery_key_turn_on_confirm_title
import logdate.client.ui.generated.resources.common_cancel
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

/** Says what turning on the recovery key changes before anything is sent. */
@Composable
fun TurnOnRecoveryKeyDialog(
    state: RecoveryKeyUiState.Ready,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = stringResource(Res.string.recovery_key_turn_on_confirm_title),
        text = { Text(stringResource(Res.string.recovery_key_turn_on_confirm_body, state.serverName, state.handle)) },
        confirmLabel = stringResource(Res.string.recovery_key_turn_on_confirm_action),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** Lists what replacing the server's key will do before anything is sent. */
@Composable
fun ReplaceServerKeyDialog(
    state: RecoveryKeyUiState.Ready,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = stringResource(Res.string.recovery_key_recover_confirm_title),
        text = { ReplaceSteps(state) },
        confirmLabel = stringResource(Res.string.recovery_key_recover_confirm_action),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** Says what replacing the registered recovery key changes before anything is sent. */
@Composable
fun ReplaceRecoveryKeyDialog(
    state: RecoveryKeyUiState.Ready,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = stringResource(Res.string.recovery_key_replace_key_confirm_title),
        text = { Text(stringResource(Res.string.recovery_key_replace_key_confirm_body, state.serverName, state.handle)) },
        confirmLabel = stringResource(Res.string.recovery_key_replace_key_confirm_action),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: @Composable () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text,
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiRes.string.common_cancel)) } },
    )
}

@Composable
private fun ReplaceSteps(state: RecoveryKeyUiState.Ready) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(Res.string.recovery_key_recover_confirm_intro))
        listOf(
            stringResource(Res.string.recovery_key_recover_confirm_step_check),
            stringResource(Res.string.recovery_key_recover_confirm_step_passkey),
            stringResource(Res.string.recovery_key_recover_confirm_step_replace, state.serverName, state.handle),
            stringResource(Res.string.recovery_key_recover_confirm_step_rank),
            stringResource(Res.string.recovery_key_recover_confirm_step_unchanged),
        ).forEach { step ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("•")
                Text(step)
            }
        }
    }
}
