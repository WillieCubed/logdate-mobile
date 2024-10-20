package app.logdate.core.instanceid

import app.logdate.core.di.ApplicationScope
import com.google.firebase.installations.FirebaseInstallations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * A service that provides the current device instance ID.
 *
 * This service is used to identify the current device in the context of a user account. The
 * identifier is unique to an installation of the app on a device and is used to identify the device
 * across multiple sessions.
 */
class DeviceInstanceIdProvider @Inject constructor(
    private val firebaseInstallations: FirebaseInstallations,
    @ApplicationScope private val coroutineScope: CoroutineScope,
) {
    private val _currentInstanceId = MutableSharedFlow<String>()

    /**
     * The current device instance ID.
     */
    val currentInstanceId: SharedFlow<String>
        get() = _currentInstanceId

    init {
        // TODO: Make sure this isn't actually code smell
        coroutineScope.launch {
            val id = firebaseInstallations.id.await()
            _currentInstanceId.tryEmit(id)
        }
    }

    /**
     * Resets and regenerates the current device instance ID.
     *
     * The new instance ID can be observed using [currentInstanceId].
     */
    fun resetInstanceId() {
        coroutineScope.launch {
            firebaseInstallations.delete().await()
            val id = firebaseInstallations.id.await()
            _currentInstanceId.tryEmit(id)
        }
    }
}