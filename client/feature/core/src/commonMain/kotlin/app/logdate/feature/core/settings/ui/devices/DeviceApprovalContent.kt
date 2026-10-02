@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.connect_device_account
import logdate.client.feature.core.generated.resources.connect_device_code
import logdate.client.feature.core.generated.resources.connect_device_confirm
import logdate.client.feature.core.generated.resources.connect_device_confirmation_help
import logdate.client.feature.core.generated.resources.connect_device_confirmation_title
import logdate.client.feature.core.generated.resources.connect_device_connected
import logdate.client.feature.core.generated.resources.connect_device_connecting
import logdate.client.feature.core.generated.resources.connect_device_description
import logdate.client.feature.core.generated.resources.connect_device_error_account_mismatch
import logdate.client.feature.core.generated.resources.connect_device_error_already_used
import logdate.client.feature.core.generated.resources.connect_device_error_camera_denied
import logdate.client.feature.core.generated.resources.connect_device_error_connection_failed
import logdate.client.feature.core.generated.resources.connect_device_error_expired
import logdate.client.feature.core.generated.resources.connect_device_error_keys_missing
import logdate.client.feature.core.generated.resources.connect_device_error_not_a_code
import logdate.client.feature.core.generated.resources.connect_device_error_scan_failed
import logdate.client.feature.core.generated.resources.connect_device_error_server
import logdate.client.feature.core.generated.resources.connect_device_error_signed_out
import logdate.client.feature.core.generated.resources.connect_device_error_wrong_account_or_expired
import logdate.client.feature.core.generated.resources.connect_device_looking_up
import logdate.client.feature.core.generated.resources.connect_device_reject
import logdate.client.feature.core.generated.resources.connect_device_rejected
import logdate.client.feature.core.generated.resources.connect_device_rejecting
import logdate.client.feature.core.generated.resources.connect_device_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The "Connect a device" card, its status line, and the confirmation dialog, shared by every
 * platform that can scan a connection code. [onConnectClick] starts that platform's scanner.
 */
@Composable
internal fun DeviceApprovalContent(
    state: DeviceApprovalUiState,
    onConnectClick: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit,
) {
    val busy = state is DeviceApprovalUiState.LookingUp || state is DeviceApprovalUiState.Working
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ConnectDeviceCard(onClick = onConnectClick, enabled = !busy)
        statusMessage(state)?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (state is DeviceApprovalUiState.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier =
                    Modifier
                        .padding(horizontal = Spacing.xs)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("connect-device-status"),
            )
        }
    }
    if (state is DeviceApprovalUiState.Confirm) {
        ConfirmDeviceDialog(state = state, onApprove = onApprove, onReject = onReject, onDismiss = onDismiss)
    }
}

@Composable
private fun statusMessage(state: DeviceApprovalUiState): String? =
    when (state) {
        DeviceApprovalUiState.Idle, is DeviceApprovalUiState.Confirm -> null
        DeviceApprovalUiState.LookingUp -> stringResource(Res.string.connect_device_looking_up)
        is DeviceApprovalUiState.Working ->
            stringResource(
                if (state.connecting) Res.string.connect_device_connecting else Res.string.connect_device_rejecting,
                state.deviceName,
            )
        is DeviceApprovalUiState.Done ->
            stringResource(
                if (state.connected) Res.string.connect_device_connected else Res.string.connect_device_rejected,
                state.deviceName,
            )
        is DeviceApprovalUiState.Failed -> stringResource(state.reason.message)
    }

private val DeviceApprovalFailure.message: StringResource
    get() =
        when (this) {
            DeviceApprovalFailure.NotAConnectionCode -> Res.string.connect_device_error_not_a_code
            DeviceApprovalFailure.ScanFailed -> Res.string.connect_device_error_scan_failed
            DeviceApprovalFailure.CameraDenied -> Res.string.connect_device_error_camera_denied
            DeviceApprovalFailure.SignedOut -> Res.string.connect_device_error_signed_out
            DeviceApprovalFailure.AccountMismatch -> Res.string.connect_device_error_account_mismatch
            DeviceApprovalFailure.WrongAccountOrExpired -> Res.string.connect_device_error_wrong_account_or_expired
            DeviceApprovalFailure.Expired -> Res.string.connect_device_error_expired
            DeviceApprovalFailure.AlreadyUsed -> Res.string.connect_device_error_already_used
            DeviceApprovalFailure.KeysMissing -> Res.string.connect_device_error_keys_missing
            DeviceApprovalFailure.ConnectionFailed -> Res.string.connect_device_error_connection_failed
            DeviceApprovalFailure.ServerError -> Res.string.connect_device_error_server
        }

@Composable
private fun ConfirmDeviceDialog(
    state: DeviceApprovalUiState.Confirm,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.connect_device_confirmation_title, state.deviceName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(Res.string.connect_device_account, state.accountName))
                Text(
                    stringResource(Res.string.connect_device_code, state.code),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(stringResource(Res.string.connect_device_confirmation_help))
                state.failure?.let { failure ->
                    Text(
                        text = stringResource(failure.message),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onApprove) { Text(stringResource(Res.string.connect_device_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text(stringResource(Res.string.connect_device_reject)) }
        },
    )
}

@Composable
internal fun ConnectDeviceCard(
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().testTag("connect-device-action"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(Res.string.connect_device_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(Res.string.connect_device_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}
