@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.location.timeline.ui.history

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.client.location.tracking.ActivityAwareLocationService
import app.logdate.client.location.tracking.LocationTrackingManager
import org.koin.compose.koinInject

@Composable
internal actual fun rememberHistoryRecordingAccess(): HistoryRecordingAccess {
    val context = LocalContext.current
    val trackingManager = koinInject<LocationTrackingManager>()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val captureStatus by ActivityAwareLocationService.captureStatus.collectAsStateWithLifecycle()
    var revision by remember { mutableIntStateOf(0) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { revision++ }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return remember(revision, captureStatus) {
        fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        val motion = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        val settings = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }
        val access =
            when {
                !granted(Manifest.permission.ACCESS_FINE_LOCATION) && !granted(Manifest.permission.ACCESS_COARSE_LOCATION) ->
                    HistoryRecordingAccess(
                        message = "Allow location to record the places you visit. Your existing history stays available either way.",
                        actionLabel = "Allow location",
                        reducedMotion = motion,
                        resolve = {
                            request.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                            )
                        },
                    )
                context.getSystemService(LocationManager::class.java)?.isLocationEnabled != true ->
                    HistoryRecordingAccess(
                        message = "Location services are off. Turn them on to record your day.",
                        actionLabel = "Turn on location",
                        reducedMotion = motion,
                        resolve = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                    )
                !granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ->
                    HistoryRecordingAccess(
                        message =
                            "For a complete day and recovery after interruptions, choose Permissions → " +
                                "Location → Allow all the time in app settings.",
                        actionLabel = "Open location settings",
                        reducedMotion = motion,
                        resolve = settings,
                    )
                !granted(Manifest.permission.ACTIVITY_RECOGNITION) ->
                    HistoryRecordingAccess(
                        message = "Allow physical activity to help distinguish walking and travelling. You can correct activities later.",
                        actionLabel = "Allow activity",
                        reducedMotion = motion,
                        resolve = { request.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION)) },
                    )
                Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS) ->
                    HistoryRecordingAccess(
                        message = "Allow notifications so you can see when recording is running and pause it at any time.",
                        actionLabel = "Allow notifications",
                        reducedMotion = motion,
                        resolve = { request.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) },
                    )
                else ->
                    HistoryRecordingAccess(
                        true,
                        "Record visits and journeys in the background. This uses more battery and keeps a recording notification visible. You can pause at any time.",
                        "Start recording",
                        motion,
                    )
            }
        access.copy(
            openSettings = settings,
            resolve = if (access.ready) ({ trackingManager.startTracking() }) else access.resolve,
            captureStatus = captureStatus,
        )
    }
}
