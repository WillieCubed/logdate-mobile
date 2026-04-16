package app.logdate.client.permissions

import androidx.compose.runtime.Composable

@Composable
actual fun rememberNotificationPermissionState(): NotificationPermissionState =
    NotificationPermissionState(
        hasPermission = true,
        shouldShowRationale = false,
        permissionRequested = false,
        requestPermission = {},
    )

@Composable
actual fun rememberHealthConnectPermissionState(): HealthConnectPermissionState =
    HealthConnectPermissionState(
        hasPermission = false,
        permissionRequested = false,
        isRequestInFlight = false,
        requestPermission = {},
        refreshPermissionState = {},
    )

@Composable
actual fun rememberMediaLibraryPermissionState(): MediaLibraryPermissionState =
    MediaLibraryPermissionState(
        hasPermission = true,
        shouldShowRationale = false,
        permissionRequested = false,
        requestPermission = {},
    )

@Composable
actual fun rememberCalendarPermissionState(): CalendarPermissionState =
    CalendarPermissionState(
        hasPermission = false,
        shouldShowRationale = false,
        permissionRequested = false,
        requestPermission = {},
    )

@Composable
actual fun rememberContactsPermissionState(): ContactsPermissionState =
    ContactsPermissionState(
        hasPermission = false,
        shouldShowRationale = false,
        permissionRequested = false,
        requestPermission = {},
    )
