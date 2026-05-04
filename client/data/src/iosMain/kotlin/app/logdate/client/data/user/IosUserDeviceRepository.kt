package app.logdate.client.data.user

import app.logdate.client.repository.user.devices.DeviceType
import app.logdate.client.repository.user.devices.UserDevice
import app.logdate.client.repository.user.devices.UserDeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.UIUserInterfaceIdiomPhone
import kotlin.time.Clock

/**
 * iOS [UserDeviceRepository] backed by [UIDevice] for the current device. Until the LogDate
 * sync server enumerates a user's full device list (see §S3 backend), `allDevices` only ever
 * contains the local entry — better than the stubbed empty list since the device-management
 * UI can at least render this device's real model and OS version.
 */
class IosUserDeviceRepository : UserDeviceRepository {
    private val deviceFlow: MutableStateFlow<UserDevice> = MutableStateFlow(currentDeviceSnapshot())

    override val currentDevice: Flow<UserDevice> = deviceFlow.asStateFlow()

    override val allDevices: Flow<List<UserDevice>> = deviceFlow.map { listOf(it) }

    override suspend fun addDevice(
        label: String,
        operatingSystem: String,
        version: String,
        model: String,
        type: DeviceType,
    ) = Unit

    override suspend fun removeDevice(deviceId: String) = Unit

    override suspend fun updateDevice(deviceId: String) {
        deviceFlow.value = currentDeviceSnapshot()
    }

    private fun currentDeviceSnapshot(): UserDevice {
        val device = UIDevice.currentDevice
        val identifier = device.identifierForVendor?.UUIDString ?: "ios-unknown"
        val type =
            when (device.userInterfaceIdiom) {
                UIUserInterfaceIdiomPhone -> DeviceType.MOBILE
                UIUserInterfaceIdiomPad -> DeviceType.TABLET
                else -> DeviceType.OTHER
            }
        return UserDevice(
            uid = identifier,
            userId = "",
            label = device.name,
            operatingSystem = device.systemName,
            version = device.systemVersion,
            model = device.model,
            type = type,
            added = Clock.System.now(),
        )
    }
}
