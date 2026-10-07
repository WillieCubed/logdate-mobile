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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
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
import logdate.client.feature.core.generated.resources.connect_device_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The scanner action and one connection dialog, including progress and completion. */
@Composable
fun DeviceApprovalContent(
    state: DeviceApprovalUiState,
    onConnectClick: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConnectDeviceCard(onClick = onConnectClick, enabled = state is DeviceApprovalUiState.Idle)
    if (state !is DeviceApprovalUiState.Idle) {
        DeviceConnectionDialog(state, onConnectClick, onApprove, onReject, onDismiss)
    }
}

internal val DeviceApprovalFailure.message: StringResource
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
