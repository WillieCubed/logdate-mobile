package app.logdate.client.data.user

import android.os.Build
import app.logdate.client.database.dao.UserDevicesDao
import app.logdate.client.database.entities.UserDeviceEntity
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.repository.user.RemoteUserAccountRepository
import app.logdate.client.repository.user.devices.DeviceType
import app.logdate.client.repository.user.devices.UserDevice
import app.logdate.client.repository.user.devices.UserDeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

/**
 * The default implementation for a [UserDeviceRepository].
 *
 * This is an offline-first repository that locally stores information about the user's devices.
 */
class DefaultUserDeviceRepository(
    private val userDeviceDao: UserDevicesDao,
    private val deviceIdProvider: DeviceIdProvider,
    private val remoteUserAccountRepository: RemoteUserAccountRepository,
) : UserDeviceRepository {

    override val allDevices: Flow<List<UserDevice>>
        get() = userDeviceDao.getAllDevices().map {
            it.map(UserDeviceEntity::toUserDevice)
        }

    // TODO: Move this functionality into domain layer
    override val currentDevice: Flow<UserDevice>
        get() = combine(
            deviceIdProvider.getDeviceId(),
            remoteUserAccountRepository.currentUser,
        ) { instanceId, userAccount ->
            UserDevice(
                uid = instanceId.toString(),
                userId = userAccount?.uid ?: error("No user signed in"),
                label = Build.DEVICE,
                operatingSystem = "Android",
                version = Build.VERSION.RELEASE,
                model = Build.MODEL,
                type = DeviceType.MOBILE, // TODO: Find reliable way to determine device type
                added = Clock.System.now(),
            )
        }

    override suspend fun addDevice(
        label: String,
        operatingSystem: String,
        version: String,
        model: String,
        type: DeviceType,
    ) {
        val newDevice = generateDeviceData()
        userDeviceDao.addDevice(newDevice.toUserDeviceEntity())
    }

    override suspend fun removeDevice(deviceId: String) {
        userDeviceDao.removeDevice(deviceId)
    }

    override suspend fun updateDevice(deviceId: String) {
        userDeviceDao.updateDevice(generateDeviceData().toUserDeviceEntity())
    }

    /**
     * Generates a [UserDevice] object with the current device's data.
     *
     * @param deviceId The device ID to use. If `null`, the current device's instance ID will be used.
     *
     * @return A [UserDevice] object pre-filled with the current device's data.
     */
    private suspend fun generateDeviceData(
        deviceId: String? = null,
    ): UserDevice {
        val currentUser = remoteUserAccountRepository.currentUser.first()
            ?: throw IllegalStateException("No user signed in")
        return UserDevice(
            uid = deviceId ?: deviceIdProvider.getDeviceId().first().toString(),
            userId = currentUser.uid,
            label = Build.DEVICE,
            operatingSystem = "Android",
            version = Build.VERSION.RELEASE,
            model = Build.MODEL,
            type = DeviceType.MOBILE, // TODO: Find reliable way to determine device type
            added = Clock.System.now(),
        )
    }
}


fun UserDeviceEntity.toUserDevice() = UserDevice(
    uid = uid,
    userId = userId,
    label = label,
    operatingSystem = operatingSystem,
    version = version,
    model = model,
    type = DeviceType.valueOf(type),
    added = added,
)

fun UserDevice.toUserDeviceEntity() = UserDeviceEntity(
    uid = uid,
    userId = userId,
    label = label,
    operatingSystem = operatingSystem,
    version = version,
    model = model,
    type = type.name,
    added = added,
)