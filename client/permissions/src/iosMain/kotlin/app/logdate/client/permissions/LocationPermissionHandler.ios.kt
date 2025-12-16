package app.logdate.client.permissions

import androidx.compose.runtime.*

@Composable
actual fun rememberLocationPermissionState(): LocationPermissionState {
    // iOS handles location permissions through the CLLocationManager
    // The IosLocationProvider will handle permission requests automatically
    return LocationPermissionState(
        hasPermission = true, // iOS permissions are handled at the provider level
        shouldShowRationale = false,
        permissionRequested = false,
        requestPermission = { /* No-op on iOS */ }
    )
}