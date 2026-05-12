package app.logdate.client.permissions

import androidx.compose.runtime.Composable

@Composable
actual fun rememberLocationPermissionState(): LocationPermissionState {
    // Desktop doesn't require runtime location permissions.
    // Location features are available when the user configures a default location.
    return LocationPermissionState(
        hasPermission = true,
        shouldShowRationale = false,
        permissionRequested = false,
        requestPermission = { /* No-op on desktop */ },
    )
}
