package app.logdate.client.permissions

import androidx.compose.runtime.Composable

/**
 * Represents the state of location permission.
 */
data class LocationPermissionState(
    val hasPermission: Boolean,
    val shouldShowRationale: Boolean,
    val permissionRequested: Boolean,
    val requestPermission: () -> Unit
)

/**
 * Remembers the location permission state for the current platform.
 */
@Composable
expect fun rememberLocationPermissionState(): LocationPermissionState