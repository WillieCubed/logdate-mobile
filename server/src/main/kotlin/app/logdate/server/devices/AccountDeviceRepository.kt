@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.devices

import app.logdate.shared.model.RegisterDeviceRequest
import app.logdate.shared.model.RegisteredDevice
import kotlin.uuid.Uuid

interface AccountDeviceRepository {
    suspend fun list(accountId: Uuid): List<RegisteredDevice>

    suspend fun register(
        accountId: Uuid,
        deviceId: Uuid,
        request: RegisterDeviceRequest,
        now: Long,
    ): RegisteredDevice
}

class InMemoryAccountDeviceRepository : AccountDeviceRepository {
    private val devices = mutableMapOf<Pair<Uuid, Uuid>, RegisteredDevice>()

    override suspend fun list(accountId: Uuid): List<RegisteredDevice> =
        synchronized(devices) { devices.filterKeys { it.first == accountId }.values.sortedByDescending { it.lastActive } }

    override suspend fun register(
        accountId: Uuid,
        deviceId: Uuid,
        request: RegisterDeviceRequest,
        now: Long,
    ): RegisteredDevice =
        synchronized(devices) {
            val key = accountId to deviceId
            val previous = devices[key]
            RegisteredDevice(
                deviceId.toString(),
                request.name,
                request.platform,
                request.appVersion,
                previous?.createdAt ?: now,
                maxOf(previous?.lastActive ?: now, now),
            ).also { devices[key] = it }
        }
}
