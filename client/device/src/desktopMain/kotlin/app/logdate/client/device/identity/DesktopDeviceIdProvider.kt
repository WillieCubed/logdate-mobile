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
    private val storage: KeyValueStorage
) : DeviceIdProvider {
    
    private val DEVICE_ID_KEY = "device.id"
    
    // Initialize device ID synchronously to ensure immediate availability
    private val _deviceId: Uuid
    private val _deviceIdFlow: MutableStateFlow<Uuid>
    
    init {
        // Synchronously load or generate the ID
        _deviceId = initializeDeviceId()
        
        // Initialize flow with the loaded/generated ID
        _deviceIdFlow = MutableStateFlow(_deviceId)
    }
    
    override fun getDeviceId(): StateFlow<Uuid> = _deviceIdFlow
    
    override suspend fun refreshDeviceId() {
        val newId = Uuid.random()
        Napier.d("Refreshing device ID: ${newId.toString().take(8)}...")
        storage.putString(DEVICE_ID_KEY, newId.toString())
        _deviceIdFlow.value = newId
    }
    
    private fun initializeDeviceId(): Uuid {
        return runCatching {
            runBlocking {
                val storedId = storage.getString(DEVICE_ID_KEY)
                
                if (storedId != null) {
                    try {
                        Uuid.parse(storedId)
                    } catch (e: Exception) {
                        Napier.w("Invalid stored device ID, generating new one", e)
                        val newId = Uuid.random()
                        storage.putString(DEVICE_ID_KEY, newId.toString())
                        newId
                    }
                } else {
                    Napier.i("No device ID found, generating new one")
                    val newId = Uuid.random()
                    storage.putString(DEVICE_ID_KEY, newId.toString())
                    newId
                }
            }
        }.getOrElse {
            Napier.e("Failed to initialize device ID with KeyValueStorage", it)
            Uuid.random()
        }
    }
}