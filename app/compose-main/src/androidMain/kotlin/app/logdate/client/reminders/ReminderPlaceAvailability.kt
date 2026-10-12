package app.logdate.client.reminders

import app.logdate.client.permissions.PermissionManager
import app.logdate.client.permissions.PermissionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** Refreshes current grants while opted in, including grants made without restarting the app. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun reminderPlaceAvailability(
    enabled: Flow<Boolean>,
    placesConsent: Flow<Boolean>,
    permissions: PermissionManager,
): Flow<Boolean> =
    combine(enabled, placesConsent) { reminders, places -> reminders && places }
        .distinctUntilChanged()
        .flatMapLatest { allowed ->
            if (!allowed) {
                flowOf(false)
            } else {
                flow {
                    while (true) {
                        emit(permissions.isPermissionGranted(PermissionType.LOCATION))
                        delay(30_000)
                    }
                }
            }
        }.distinctUntilChanged()
