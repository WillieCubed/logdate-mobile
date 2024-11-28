package app.logdate.client.device

import com.google.firebase.installations.FirebaseInstallations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * An [InstanceIdProvider] that provides the device instance ID using Firebase Installations.
 */
class AndroidInstanceIdProvider(
    private val firebaseInstallations: FirebaseInstallations,
    // TODO: Probably just scrap the external scope and use suspend functions
    private val externalScope: CoroutineScope,
) : InstanceIdProvider {
    private val _currentInstanceId = MutableSharedFlow<String>()

    /**
     * The current device instance ID.
     */
    override val currentInstanceId: SharedFlow<String>
        get() = _currentInstanceId

    init {
        // TODO: Make sure this isn't actually code smell
        externalScope.launch {
            val id = firebaseInstallations.id.await()
            _currentInstanceId.tryEmit(id)
        }
    }

    /**
     * Resets and regenerates the current device instance ID.
     *
     * The new instance ID can be observed using [currentInstanceId].
     */
    override fun resetInstanceId() {
        externalScope.launch {
            firebaseInstallations.delete().await()
            val id = firebaseInstallations.id.await()
            _currentInstanceId.tryEmit(id)
        }
    }
}