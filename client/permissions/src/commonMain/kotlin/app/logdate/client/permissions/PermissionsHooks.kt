@file:Suppress("ktlint:standard:filename")

package app.logdate.client.permissions

import androidx.compose.runtime.Composable

@Composable
expect fun rememberNotificationPermissionState(): NotificationPermissionState

@Composable
expect fun rememberHealthConnectPermissionState(): HealthConnectPermissionState

/**
 * Represents the state of notification permission.
 */
data class NotificationPermissionState(
    val hasPermission: Boolean,
    val shouldShowRationale: Boolean,
    val permissionRequested: Boolean,
    val requestPermission: () -> Unit,
)

/**
 * Represents the state of the Health Connect sleep permission request flow.
 */
data class HealthConnectPermissionState(
    val completedRequestCount: Int,
    val requestPermission: () -> Unit,
)
