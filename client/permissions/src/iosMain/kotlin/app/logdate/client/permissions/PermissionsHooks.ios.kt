@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package app.logdate.client.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.aakira.napier.Napier
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Contacts.CNContactStore
import platform.Contacts.CNEntityType
import platform.EventKit.EKEntityType
import platform.EventKit.EKEventStore
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHPhotoLibrary
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter

@Composable
actual fun rememberNotificationPermissionState(): NotificationPermissionState {
    var status by remember { mutableStateOf(PermissionStatus.UNKNOWN) }
    var permissionRequested by remember { mutableStateOf(false) }
    var refreshNonce by remember { mutableStateOf(0) }
    val center = remember { UNUserNotificationCenter.currentNotificationCenter() }

    LaunchedEffect(refreshNonce) {
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val raw = settings?.authorizationStatus ?: return@getNotificationSettingsWithCompletionHandler
            status = mapNotificationStatus(raw)
        }
    }

    return NotificationPermissionState(
        hasPermission = status == PermissionStatus.GRANTED,
        shouldShowRationale = status == PermissionStatus.PERMANENTLY_DENIED,
        permissionRequested = permissionRequested,
        requestPermission = {
            val options = UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound
            center.requestAuthorizationWithOptions(options) { _, error ->
                if (error != null) {
                    Napier.w("UNUserNotificationCenter authorization failed: ${error.localizedDescription}")
                }
                permissionRequested = true
                refreshNonce += 1
            }
        },
    )
}

@Composable
actual fun rememberHealthConnectPermissionState(): HealthConnectPermissionState =
    // Not implemented on iOS yet — needs HKHealthStore.requestAuthorization on
    // HKCategoryTypeIdentifierSleepAnalysis plus the HealthKit entitlement on the iOS target.
    // Until both land this is a hard-coded "no permission" stub; UI gated on this hook should
    // hide the entry point rather than render a request-permission button.
    HealthConnectPermissionState(
        hasPermission = false,
        permissionRequested = false,
        isRequestInFlight = false,
        requestPermission = {
            TODO("rememberHealthConnectPermissionState.requestPermission not implemented on iOS")
        },
        refreshPermissionState = {},
    )

@Composable
actual fun rememberMediaLibraryPermissionState(): MediaLibraryPermissionState {
    var status by remember {
        mutableStateOf(mapPhotosStatus(PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)))
    }
    var permissionRequested by remember { mutableStateOf(false) }
    var refreshNonce by remember { mutableStateOf(0) }

    LaunchedEffect(refreshNonce) {
        status = mapPhotosStatus(PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite))
    }

    return MediaLibraryPermissionState(
        hasPermission = status == PermissionStatus.GRANTED,
        shouldShowRationale = status == PermissionStatus.PERMANENTLY_DENIED,
        permissionRequested = permissionRequested,
        requestPermission = {
            PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite) { rawStatus ->
                status = mapPhotosStatus(rawStatus)
                permissionRequested = true
                refreshNonce += 1
            }
        },
    )
}

@Composable
actual fun rememberCalendarPermissionState(): CalendarPermissionState {
    val eventStore = remember { EKEventStore() }
    var status by remember {
        mutableStateOf(mapEventKitStatus(EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent)))
    }
    var permissionRequested by remember { mutableStateOf(false) }
    var refreshNonce by remember { mutableStateOf(0) }

    LaunchedEffect(refreshNonce) {
        status = mapEventKitStatus(EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent))
    }

    return CalendarPermissionState(
        hasPermission = status == PermissionStatus.GRANTED,
        shouldShowRationale = status == PermissionStatus.PERMANENTLY_DENIED,
        permissionRequested = permissionRequested,
        requestPermission = {
            eventStore.requestAccessToEntityType(EKEntityType.EKEntityTypeEvent) { _, error ->
                if (error != null) {
                    Napier.w("EKEventStore authorization failed: ${error.localizedDescription}")
                }
                permissionRequested = true
                refreshNonce += 1
            }
        },
    )
}

@Composable
actual fun rememberContactsPermissionState(): ContactsPermissionState {
    val contactStore = remember { CNContactStore() }
    var status by remember {
        mutableStateOf(mapContactsStatus(CNContactStore.authorizationStatusForEntityType(CNEntityType.CNEntityTypeContacts)))
    }
    var permissionRequested by remember { mutableStateOf(false) }
    var refreshNonce by remember { mutableStateOf(0) }

    LaunchedEffect(refreshNonce) {
        status = mapContactsStatus(CNContactStore.authorizationStatusForEntityType(CNEntityType.CNEntityTypeContacts))
    }

    return ContactsPermissionState(
        hasPermission = status == PermissionStatus.GRANTED,
        shouldShowRationale = status == PermissionStatus.PERMANENTLY_DENIED,
        permissionRequested = permissionRequested,
        requestPermission = {
            contactStore.requestAccessForEntityType(CNEntityType.CNEntityTypeContacts) { _, error ->
                if (error != null) {
                    Napier.w("CNContactStore authorization failed: ${error.localizedDescription}")
                }
                permissionRequested = true
                refreshNonce += 1
            }
        },
    )
}
