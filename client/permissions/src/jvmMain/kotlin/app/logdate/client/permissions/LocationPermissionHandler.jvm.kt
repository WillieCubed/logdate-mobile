package app.logdate.client.permissions

import androidx.compose.runtime.*

@Composable
actual fun rememberLocationPermissionState(): LocationPermissionState {
    // Desktop doesn't require runtime location permissions
    // The DesktopLocationProvider uses IP geolocation which doesn't need permissions
    return LocationPermissionState(
        hasPermission = true, // No permissions needed for IP geolocation
        shouldShowRationale = false,
        permissionRequested = false,
        requestPermission = { /* No-op on desktop */ }
    )
}