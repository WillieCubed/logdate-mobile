package app.logdate.client.device.identity

import app.logdate.client.device.storage.DeviceIdentityStorage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.Uuid

/**
 * Base implementation of DeviceIdProvider that uses DeviceIdentityStorage for persistence.
 * This provides common functionality across all platforms, delegating only the
 * storage operations to platform-specific implementations.
 */
class BaseDeviceIdProvider(
    private val storage: DeviceIdentityStorage,
) : DeviceIdProvider {
    private val deviceIdKey = "device.id"

    // Initialize with a default value that will be updated in init
    private val deviceIdFlow = MutableStateFlow(Uuid.random())

    init {
        // Initialize the ID - need to handle this carefully since we can't use
        // suspend functions in init, but we want the ID to be immediately available
        runCatching {
            // Use a blocking approach to get the initial value, which isn't ideal
            // but necessary to ensure the ID is immediately available
            kotlinx.coroutines.runBlocking {
                val storedId = storage.getString(deviceIdKey)

                if (storedId != null) {
                    try {
                        val uuid = Uuid.parse(storedId)
                        deviceIdFlow.value = uuid
                    } catch (e: Exception) {
                        Napier.w("Invalid stored device ID, generating new one", e)
                        val newId = Uuid.random()
                        storage.putString(deviceIdKey, newId.toString())
                        deviceIdFlow.value = newId
                    }
                } else {
                    Napier.i("No device ID found, generating new one")
                    val newId = Uuid.random()
                    storage.putString(deviceIdKey, newId.toString())
                    deviceIdFlow.value = newId
                }
            }
        }.onFailure {
            Napier.e("Failed to initialize device ID", it)
            // We already have a random UUID as default, so no action needed
        }
    }

    override fun getDeviceId(): StateFlow<Uuid> = deviceIdFlow.asStateFlow()

    override suspend fun refreshDeviceId() {
        val newId = Uuid.random()
        Napier.d("Refreshing device ID: ${newId.toString().take(8)}...")
        storage.putString(deviceIdKey, newId.toString())
        deviceIdFlow.value = newId
    }
}
