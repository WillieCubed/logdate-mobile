package app.logdate.client.permissions

import androidx.compose.runtime.Composable

@Composable
expect fun rememberNotificationPermissionState(): NotificationPermissionState

data class NotificationPermissionState(
    val permissionId: String
)