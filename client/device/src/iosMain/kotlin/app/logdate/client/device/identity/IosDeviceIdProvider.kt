package app.logdate.client.device.identity

import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlin.uuid.Uuid

/**
 * iOS implementation of DeviceIdProvider that securely stores
 * a persistent device identifier in the Keychain.
 */
class IosDeviceIdProvider(
    private val secureStorage: KeychainWrapper,
) : DeviceIdProvider {

    private companion object {
        private const val DEVICE_ID_KEY = "app.logdate.device.id"
    }

    // Initialize device ID synchronously to ensure immediate availability
    private val _deviceIdFlow: MutableStateFlow<Uuid>

    init {
        // Synchronously load or generate the ID
        val storedId = secureStorage.getString(DEVICE_ID_KEY)

        val deviceId = if (!storedId.isNullOrEmpty()) {
            try {
                Uuid.parse(storedId)
            } catch (e: Exception) {
                Napier.e("Invalid stored device ID, generating new one", e)
                val newId = Uuid.random()
                runBlocking { storeDeviceId(newId) }
                newId
            }
        } else {
            Napier.i("No device ID found, generating new one")
            val newId = Uuid.random()
            runBlocking { storeDeviceId(newId) }
            newId
        }

        // Initialize flow with the loaded/generated ID
        _deviceIdFlow = MutableStateFlow(deviceId)
    }

    override fun getDeviceId(): StateFlow<Uuid> = _deviceIdFlow

    override suspend fun refreshDeviceId() {
        val newId = Uuid.random()
        Napier.d("Refreshing device ID: ${newId.toString().take(8)}...")
        storeDeviceId(newId)
        _deviceIdFlow.value = newId
    }

    private suspend fun storeDeviceId(id: Uuid) {
        secureStorage.set(id.toString(), DEVICE_ID_KEY)
    }
}