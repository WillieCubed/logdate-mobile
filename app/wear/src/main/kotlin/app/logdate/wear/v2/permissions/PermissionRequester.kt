package app.logdate.wear.v2.permissions

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat

/** How a permission request ended. */
enum class PermissionResult {
    GRANTED,

    /** Refused this time; asking again shows the system prompt. */
    DENIED,

    /** Refused for good: the system won't show its prompt again, and only Settings can grant it. */
    BLOCKED,
}

/** Asks the system for a permission; the answer goes to the callback given to [rememberPermissionRequester]. */
fun interface PermissionRequester {
    fun request(permission: WatchPermission)
}

internal fun permissionResult(
    granted: Boolean,
    systemWillAskAgain: Boolean,
): PermissionResult =
    when {
        granted -> PermissionResult.GRANTED
        systemWillAskAgain -> PermissionResult.DENIED
        else -> PermissionResult.BLOCKED
    }

/**
 * A [PermissionRequester] for this screen. The permission being asked for survives the activity
 * being recreated while the system prompt is up.
 */
@Composable
fun rememberPermissionRequester(onResult: (WatchPermission, PermissionResult) -> Unit): PermissionRequester {
    val activity = LocalActivity.current
    val currentOnResult by rememberUpdatedState(onResult)
    var pending by rememberSaveable { mutableStateOf<WatchPermission?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val permission = pending ?: return@rememberLauncherForActivityResult
            pending = null
            val askAgain = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission.manifestName())
            currentOnResult(permission, permissionResult(granted, askAgain))
        }
    return remember(launcher) {
        PermissionRequester { permission ->
            pending = permission
            launcher.launch(permission.manifestName())
        }
    }
}
