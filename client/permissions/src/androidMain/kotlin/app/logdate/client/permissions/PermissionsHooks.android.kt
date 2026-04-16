package app.logdate.client.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import io.github.aakira.napier.Napier

private const val HEALTH_CONNECT_PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

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
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }
    var isRequestInFlight by remember { mutableStateOf(false) }
    var refreshNonce by remember { mutableStateOf(0) }
    val sleepPermissions =
        remember {
            setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))
        }

    LaunchedEffect(context, refreshNonce) {
        val granted = readHealthConnectPermissionState(context, sleepPermissions)
        Napier.i("Health Connect permission state refreshed: granted=$granted requested=$permissionRequested")
        hasPermission = granted
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = PermissionController.createRequestPermissionResultContract(HEALTH_CONNECT_PROVIDER_PACKAGE),
        ) { granted ->
            val hasAllPermissions = granted.containsAll(sleepPermissions)
            Napier.i(
                "Health Connect permission result: granted=$hasAllPermissions grantedCount=${granted.size}",
            )
            hasPermission = hasAllPermissions
            permissionRequested = true
            isRequestInFlight = false
        }

    return HealthConnectPermissionState(
        hasPermission = hasPermission,
        permissionRequested = permissionRequested,
        isRequestInFlight = isRequestInFlight,
        requestPermission = {
            Napier.i("Launching Health Connect sleep permission request")
            isRequestInFlight = true
            permissionLauncher.launch(sleepPermissions)
        },
        refreshPermissionState = {
            Napier.i("Scheduling Health Connect permission state refresh")
            refreshNonce += 1
        },
    )
}

@Composable
actual fun rememberCalendarPermissionState(): CalendarPermissionState {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR,
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

    return CalendarPermissionState(
        hasPermission = permissionGranted,
        shouldShowRationale = shouldShowRationale,
        permissionRequested = permissionRequested,
        requestPermission = {
            permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
        },
    )
}

@Composable
actual fun rememberContactsPermissionState(): ContactsPermissionState {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS,
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

    return ContactsPermissionState(
        hasPermission = permissionGranted,
        shouldShowRationale = shouldShowRationale,
        permissionRequested = permissionRequested,
        requestPermission = {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        },
    )
}

@Composable
actual fun rememberMediaLibraryPermissionState(): MediaLibraryPermissionState {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(hasMediaLibraryPermission(context)) }
    var shouldShowRationale by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            permissionGranted = hasMediaLibraryPermission(context)
            permissionRequested = true
            if (!permissionGranted) {
                shouldShowRationale = true
            }
        }

    return MediaLibraryPermissionState(
        hasPermission = permissionGranted,
        shouldShowRationale = shouldShowRationale,
        permissionRequested = permissionRequested,
        requestPermission = {
            permissionLauncher.launch(mediaLibraryPermissions())
        },
    )
}

private fun hasMediaLibraryPermission(context: android.content.Context): Boolean {
    val hasPermission = { permission: String ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    ) {
        return true
    }

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        hasPermission(Manifest.permission.READ_MEDIA_IMAGES) || hasPermission(Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun mediaLibraryPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private suspend fun readHealthConnectPermissionState(
    context: android.content.Context,
    sleepPermissions: Set<String>,
): Boolean =
    try {
        val client = HealthConnectClient.getOrCreate(context)
        val grantedPermissions = client.permissionController.getGrantedPermissions()
        grantedPermissions.containsAll(sleepPermissions)
    } catch (e: Exception) {
        Napier.e("Failed to refresh Health Connect permission state", e)
        false
    }
