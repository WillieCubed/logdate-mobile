@file:Suppress("ktlint:standard:filename")

package app.logdate.client.permissions

import androidx.compose.runtime.Composable

@Composable
expect fun rememberNotificationPermissionState(): NotificationPermissionState

/**
 * Represents the state of notification permission.
 */
data class NotificationPermissionState(
    val hasPermission: Boolean,
    val shouldShowRationale: Boolean,
    val permissionRequested: Boolean,
    val requestPermission: () -> Unit,
)
