package app.logdate.client.permissions

import androidx.compose.runtime.Composable

@Composable
actual fun rememberNotificationPermissionState(): NotificationPermissionState =
    NotificationPermissionState(
        hasPermission = true,
        shouldShowRationale = false,
        permissionRequested = false,
        requestPermission = {},
    )
