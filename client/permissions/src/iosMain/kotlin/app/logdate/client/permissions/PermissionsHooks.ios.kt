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

@Composable
actual fun rememberHealthConnectPermissionState(): HealthConnectPermissionState =
    HealthConnectPermissionState(
        completedRequestCount = 0,
        requestPermission = {},
    )
