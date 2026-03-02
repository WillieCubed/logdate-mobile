package app.logdate.client.device.identity

import app.logdate.client.datastore.KeyValueStorage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlin.uuid.Uuid

/**
 * Desktop implementation of DeviceIdProvider that stores
 * a persistent device identifier using KeyValueStorage.
 */
class DesktopDeviceIdProvider(
    private val storage: KeyValueStorage,
) : DeviceIdProvider {
    private val deviceIdKey = "device.id"

    // Initialize device ID synchronously to ensure immediate availability
    private val initialDeviceId: Uuid
    private val deviceIdFlow: MutableStateFlow<Uuid>

    init {
        // Synchronously load or generate the ID
        initialDeviceId = initializeDeviceId()

        // Initialize flow with the loaded/generated ID
        deviceIdFlow = MutableStateFlow(initialDeviceId)
    }

    override fun getDeviceId(): StateFlow<Uuid> = deviceIdFlow

    override suspend fun refreshDeviceId() {
        val newId = Uuid.random()
        Napier.d("Refreshing device ID: ${newId.toString().take(8)}...")
        storage.putString(deviceIdKey, newId.toString())
        deviceIdFlow.value = newId
    }

    private fun initializeDeviceId(): Uuid =
        runCatching {
            runBlocking {
                val storedId = storage.getString(deviceIdKey)

                if (storedId != null) {
                    try {
                        Uuid.parse(storedId)
                    } catch (e: Exception) {
                        Napier.w("Invalid stored device ID, generating new one", e)
                        val newId = Uuid.random()
                        storage.putString(deviceIdKey, newId.toString())
                        newId
                    }
                } else {
                    Napier.i("No device ID found, generating new one")
                    val newId = Uuid.random()
                    storage.putString(deviceIdKey, newId.toString())
                    newId
                }
            }
        }.getOrElse {
            Napier.e("Failed to initialize device ID with KeyValueStorage", it)
            Uuid.random()
        }
}
