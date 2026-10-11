package app.logdate.wear.v2.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import app.logdate.wear.v2.today.HandoffState

/**
 * The settings section's actions, with the location request Places needs, and location access read
 * again each time the app resumes.
 */
@Composable
fun rememberWatchSettingsActions(viewModel: WatchSettingsViewModel): WatchSettingsActions {
    val locationRequest =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            viewModel.onLocationPermissionResult(results.values.any { it })
        }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return remember(viewModel, locationRequest) {
        WatchSettingsActions(
            onHaptics = viewModel::setHaptics,
            onPlaces = { enabled ->
                if (enabled &&
                    viewModel.placesNeedPermission()
                ) {
                    locationRequest.launch(LOCATION_PERMISSIONS)
                } else {
                    viewModel.setPlaces(enabled)
                }
            },
            onRetrySync = viewModel::retrySync,
            onOpenPhoneSettings = viewModel::openPhoneSettings,
        )
    }
}

/** Says "Continue on phone" once the phone opened its Watch page. */
@Composable
fun PhoneSettingsHandoffDialog(
    handoff: HandoffState,
    onDismiss: () -> Unit,
) {
    val text = OpenOnPhoneDialogDefaults.text
    val style = OpenOnPhoneDialogDefaults.curvedTextStyle
    OpenOnPhoneDialog(
        visible = handoff == HandoffState.SENT,
        onDismissRequest = onDismiss,
        curvedText = { openOnPhoneDialogCurvedText(text = text, style = style) },
    )
}

private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
