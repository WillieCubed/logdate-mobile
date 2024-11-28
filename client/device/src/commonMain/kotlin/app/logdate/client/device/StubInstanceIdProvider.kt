package app.logdate.client.device

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow


/**
 * A stub implementation of [InstanceIdProvider] that does nothing.
 */
object StubInstanceIdProvider : InstanceIdProvider {
    // TODO: Create identifier, store on device, and sync with remote server
    private val instanceId = MutableSharedFlow<String>()

    override val currentInstanceId: SharedFlow<String>
        get() = instanceId.asSharedFlow()

    override fun resetInstanceId() {
        instanceId.tryEmit("stub")
    }
}