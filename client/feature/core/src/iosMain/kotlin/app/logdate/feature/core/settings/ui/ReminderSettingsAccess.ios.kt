package app.logdate.feature.core.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.logdate.client.permissions.PermissionManager
import app.logdate.client.permissions.PermissionType
import org.koin.compose.koinInject

@Composable
internal actual fun rememberReminderSettingsAccess(): ReminderSettingsAccess {
    val permissions = koinInject<PermissionManager>()
    val revision = rememberReminderAccessRevision()
    return remember(permissions, revision) {
        ReminderSettingsAccess(
            notificationsAllowed = permissions.isPermissionGranted(PermissionType.NOTIFICATION),
            locationAllowed = permissions.isPermissionGranted(PermissionType.LOCATION),
            resolveNotifications = permissions::openPermissionSettings,
            resolveLocation = permissions::openPermissionSettings,
        )
    }
}
