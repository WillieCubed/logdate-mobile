package app.logdate.client.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord

@Composable
actual fun rememberNotificationPermissionState(): NotificationPermissionState {
    // Notifications don't require runtime permission before Android 13
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return NotificationPermissionState(
            hasPermission = true,
            shouldShowRationale = false,
            permissionRequested = false,
            requestPermission = {},
        )
    }

    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var shouldShowRationale by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            permissionGranted = granted
            permissionRequested = true
            if (!granted) {
                shouldShowRationale = true
            }
        }

    return NotificationPermissionState(
        hasPermission = permissionGranted,
        shouldShowRationale = shouldShowRationale,
        permissionRequested = permissionRequested,
        requestPermission = {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
    )
}

@Composable
actual fun rememberHealthConnectPermissionState(): HealthConnectPermissionState {
    var completedRequestCount by remember { mutableStateOf(0) }
    val sleepPermissions =
        remember {
            setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))
        }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = PermissionController.createRequestPermissionResultContract(),
        ) {
            completedRequestCount += 1
        }

    return HealthConnectPermissionState(
        completedRequestCount = completedRequestCount,
        requestPermission = {
            permissionLauncher.launch(sleepPermissions)
        },
    )
}
