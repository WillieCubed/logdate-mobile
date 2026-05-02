@file:OptIn(ExperimentalForeignApi::class)

package app.logdate.client.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.darwin.NSObject

@Composable
actual fun rememberLocationPermissionState(): LocationPermissionState {
    val scope = rememberCoroutineScope()
    val locationManager = remember { CLLocationManager() }
    var status by remember { mutableStateOf(locationManager.authorizationStatus) }
    var permissionRequested by remember { mutableStateOf(false) }

    val delegate =
        remember {
            object : NSObject(), CLLocationManagerDelegateProtocol {
                override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                    scope.launch { status = manager.authorizationStatus }
                }
            }
        }

    DisposableEffect(locationManager, delegate) {
        locationManager.delegate = delegate
        // Refresh once on entry — iOS does not always fire didChangeAuthorization on attach.
        status = locationManager.authorizationStatus
        onDispose {
            locationManager.delegate = null
        }
    }

    val granted = status == kCLAuthorizationStatusAuthorizedAlways || status == kCLAuthorizationStatusAuthorizedWhenInUse
    val rationale = status == kCLAuthorizationStatusDenied

    return LocationPermissionState(
        hasPermission = granted,
        shouldShowRationale = rationale,
        permissionRequested = permissionRequested,
        requestPermission = {
            permissionRequested = true
            if (status == kCLAuthorizationStatusNotDetermined) {
                locationManager.requestWhenInUseAuthorization()
            }
        },
    )
}
