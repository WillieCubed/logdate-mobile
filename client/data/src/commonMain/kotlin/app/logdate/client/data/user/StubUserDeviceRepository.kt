package app.logdate.client.data.user

import app.logdate.client.repository.user.devices.DeviceType
import app.logdate.client.repository.user.devices.UserDevice
import app.logdate.client.repository.user.devices.UserDeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock

/**
 * Stub implementation of [UserDeviceRepository].
 */
object StubUserDeviceRepository : UserDeviceRepository {
    override val allDevices: Flow<List<UserDevice>>
        get() = flowOf(emptyList())
    override val currentDevice: Flow<UserDevice>
        get() = flowOf(
            UserDevice(
                "stub",
                "stub",
                "stub",
                "stub",
                "",
                "",
                DeviceType.OTHER,
                Clock.System.now(),
            )
        )


    override suspend fun addDevice(
        label: String,
        operatingSystem: String,
        version: String,
        model: String,
        type: DeviceType,
    ) {
        // no-op
    }

    override suspend fun removeDevice(deviceId: String) {
        // no-op
    }

    override suspend fun updateDevice(deviceId: String) {
        // no-op
    }
}
