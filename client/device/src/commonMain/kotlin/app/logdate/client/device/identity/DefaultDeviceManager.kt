package app.logdate.client.device.identity

import app.logdate.client.device.models.DeviceInfo
import app.logdate.client.device.models.DevicePlatform
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Clock
import kotlin.uuid.Uuid

/**
 * Manages device information and operations.
 *
 * This sealed class provides the core implementation while allowing platform-specific
 * data to be injected through the constructor. Platform-specific extensions should
 * only be done through composition or by extending the companion object.
 */
class DefaultDeviceManager(
    private val deviceIdProvider: DeviceIdProvider,
    private val deviceRepository: DeviceRepository,
    initialDeviceName: String, // TODO: fix code smell
    private val platform: DevicePlatform,
    private val appVersion: String,
    private val notificationTokenProvider: suspend () -> String? = { null },
) {

    private val _notificationToken = MutableStateFlow<String?>(null)
    private val _deviceInfo = MutableStateFlow(createInitialDeviceInfo(initialDeviceName))

    private fun createInitialDeviceInfo(deviceName: String): DeviceInfo {
        return DeviceInfo(
            id = deviceIdProvider.getDeviceId().value,
            name = deviceName,
            platform = platform,
            createdAt = Clock.System.now(),
            lastActive = Clock.System.now(),
            appVersion = appVersion,
            isCurrentDevice = true
        )
    }

    /**
     * Gets the current device ID.
     *
     * @return StateFlow of the device identifier
     */
    fun getDeviceId(): StateFlow<Uuid> = deviceIdProvider.getDeviceId()

    /**
     * Gets full device information for the current device.
     *
     * @return The current device's information
     */
    suspend fun getCurrentDeviceInfo(): DeviceInfo {
        // Update last active
        val currentInfo = _deviceInfo.value
        val updatedInfo = currentInfo.copy(lastActive = Clock.System.now())
        _deviceInfo.value = updatedInfo
        return updatedInfo
    }

    /**
     * Updates the device's last active timestamp.
     */
    suspend fun updateLastActive() {
        val currentInfo = _deviceInfo.value
        _deviceInfo.value = currentInfo.copy(lastActive = Clock.System.now())
    }

    /**
     * Registers this device with the cloud account.
     *
     * @return Result indicating success or failure
     */
    suspend fun registerWithCloud(): Result<Boolean> {
        return try {
            val deviceInfo = getCurrentDeviceInfo()
            val success = deviceRepository.registerDevice(
                deviceInfo = deviceInfo,
                notificationToken = _notificationToken.value
            )
            Result.success(success)
        } catch (e: Exception) {
            Napier.e("Failed to register device", e)
            Result.failure(e)
        }
    }

    /**
     * Gets all devices associated with this user account.
     *
     * @return Flow of device information objects
     */
    fun getAssociatedDevices(): Flow<List<DeviceInfo>> {
        return deviceRepository.getAssociatedDevices()
    }

    /**
     * Renames the current device.
     *
     * @param newName The new name for the device
     */
    suspend fun renameDevice(newName: String) {
        // Update local device info
        val currentInfo = _deviceInfo.value
        val updatedInfo = currentInfo.copy(name = newName)
        _deviceInfo.value = updatedInfo

        // Update in repository
        try {
            deviceRepository.updateDeviceInfo(updatedInfo)
        } catch (e: Exception) {
            Napier.e("Failed to rename device in repository", e)
        }
    }

    /**
     * Removes a device from the account.
     *
     * @param deviceId The ID of the device to remove
     * @return Result indicating success or failure
     */
    suspend fun removeDevice(deviceId: Uuid): Result<Boolean> {
        return try {
            val success = deviceRepository.removeDevice(deviceId)
            Result.success(success)
        } catch (e: Exception) {
            Napier.e("Failed to remove device", e)
            Result.failure(e)
        }
    }

    /**
     * Generates a new device ID, replacing the current one.
     * This is used for privacy resets or when a device ID is compromised.
     *
     * @return The new device ID
     */
    suspend fun refreshDeviceId(): Uuid {
        // Generate new device ID
        deviceIdProvider.refreshDeviceId()
        val newId = deviceIdProvider.getDeviceId().value

        // Update device info
        val currentInfo = _deviceInfo.value
        val updatedInfo = currentInfo.copy(
            id = newId,
            lastActive = Clock.System.now()
        )
        _deviceInfo.value = updatedInfo

        // Re-register with cloud
        try {
            deviceRepository.registerDevice(updatedInfo)
        } catch (e: Exception) {
            Napier.e("Failed to register new device ID with cloud", e)
        }

        return newId
    }

    /**
     * Gets the device notification token, if available.
     * This is used for push notifications.
     *
     * @return The notification token, or null if not available
     */
    suspend fun getNotificationToken(): String? {
        val storedToken = _notificationToken.value
        if (!storedToken.isNullOrEmpty()) {
            return storedToken
        }

        // If not stored, try to get from provider
        return notificationTokenProvider()?.also {
            _notificationToken.value = it
        }
    }

    /**
     * Updates the device notification token.
     *
     * @param token The new notification token
     */
    suspend fun updateNotificationToken(token: String) {
        _notificationToken.value = token

        // TODO: Use different coroutine scope if possible
        try {
            val deviceId = deviceIdProvider.getDeviceId().value
            deviceRepository.updateDeviceToken(deviceId, token)
        } catch (e: Exception) {
            Napier.e("Failed to update notification token in cloud", e)
        }
    }
}