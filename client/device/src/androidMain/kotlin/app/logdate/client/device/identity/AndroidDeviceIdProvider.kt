package app.logdate.client.device.identity

import android.content.Context
import android.content.SharedPreferences
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.uuid.Uuid

/**
 * Android implementation of DeviceIdProvider that securely stores
 * a persistent device identifier.
 */
class AndroidDeviceIdProvider(
    private val context: Context
) : DeviceIdProvider {
    
    private val DEVICE_ID_KEY = "device_id"
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "app.logdate.device_identifiers", 
        Context.MODE_PRIVATE
    )
    
    // Initialize device ID synchronously to ensure immediate availability
    private val _deviceId: Uuid
    private val _deviceIdFlow: MutableStateFlow<Uuid>
    
    init {
        // Synchronously load or generate the ID
        val storedId = prefs.getString(DEVICE_ID_KEY, null)
        
        _deviceId = if (!storedId.isNullOrEmpty()) {
            try {
                Uuid.parse(storedId)
            } catch (e: Exception) {
                Napier.e("Invalid stored device ID, generating new one", e)
                val newId = Uuid.random()
                storeDeviceId(newId)
                newId
            }
        } else {
            Napier.i("No device ID found, generating new one")
            val newId = Uuid.random()
            storeDeviceId(newId)
            newId
        }
        
        // Initialize flow with the loaded/generated ID
        _deviceIdFlow = MutableStateFlow(_deviceId)
    }
    
    override fun getDeviceId(): StateFlow<Uuid> = _deviceIdFlow
    
    override suspend fun refreshDeviceId() {
        val newId = Uuid.random()
        Napier.d("Refreshing device ID: ${newId.toString().take(8)}...")
        storeDeviceId(newId)
        _deviceIdFlow.value = newId
    }
    
    private fun storeDeviceId(id: Uuid) {
        prefs.edit().putString(DEVICE_ID_KEY, id.toString()).apply()
    }
}