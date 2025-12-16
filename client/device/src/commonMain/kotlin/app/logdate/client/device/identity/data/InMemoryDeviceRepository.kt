package app.logdate.client.device.identity.data

import app.logdate.client.device.identity.DeviceRepository
import app.logdate.client.device.models.DeviceInfo
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Clock
import kotlin.uuid.Uuid

/**
 * Stub implementation of DeviceRepository for testing and offline operation.
 * This implementation stores devices in memory and provides basic functionality
 * without connecting to a cloud service.
 *
 * TODO: Perhaps consolidate with other repositories for consistency.
 */
class InMemoryDeviceRepository : DeviceRepository {

    private val devices = mutableMapOf<Uuid, DeviceInfo>()
    private val devicesFlow = MutableStateFlow<List<DeviceInfo>>(emptyList())
    
    override suspend fun registerDevice(
        deviceInfo: DeviceInfo,
        notificationToken: String?
    ): Boolean {
        Napier.d("Registering device: ${deviceInfo.id}")
        
        devices[deviceInfo.id] = deviceInfo
        devicesFlow.value = devices.values.toList()
        
        return true
    }
    
    override fun getAssociatedDevices(): Flow<List<DeviceInfo>> {
        return devicesFlow
    }
    
    override suspend fun updateDeviceInfo(deviceInfo: DeviceInfo): Boolean {
        Napier.d("Updating device: ${deviceInfo.id}")
        
        // Update or add the device
        devices[deviceInfo.id] = deviceInfo.copy(
            lastActive = Clock.System.now()
        )
        
        // Update the flow
        devicesFlow.value = devices.values.toList()
        
        return true
    }
    
    override suspend fun updateDeviceToken(deviceId: Uuid, token: String): Boolean {
        Napier.d("Updating token for device: $deviceId")
        
        // Get the device
        val device = devices[deviceId] ?: return false
        
        // Update the token (would be handled differently in a real implementation)
        devices[deviceId] = device.copy(
            lastActive = Clock.System.now()
        )
        
        // Update the flow
        devicesFlow.value = devices.values.toList()
        
        return true
    }
    
    override suspend fun removeDevice(deviceId: Uuid): Boolean {
        Napier.d("Removing device: $deviceId")
        
        // Remove the device
        devices.remove(deviceId)
        
        // Update the flow
        devicesFlow.value = devices.values.toList()
        
        return true
    }
}