package app.logdate.client.domain.restore

import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.identity.DeviceIdProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.Uuid

internal const val RESTORE_TEST_OWNER_ID = "00000000-0000-4000-8000-0000000000a1"
internal val RESTORE_TEST_DEVICE_ID: Uuid = Uuid.parse("00000000-0000-4000-8000-0000000000b2")

/** The identity the restoring installation already has, so restored rows can be matched to it. */
internal class RestoreTestOwnerProvider(
    private val ownerId: String = RESTORE_TEST_OWNER_ID,
) : CanonicalOwnerProvider {
    override suspend fun getCanonicalOwnerId(): String = ownerId

    override suspend fun hasBoundOwner(): Boolean = true
}

internal class RestoreTestDeviceIdProvider(
    deviceId: Uuid = RESTORE_TEST_DEVICE_ID,
) : DeviceIdProvider {
    private val state = MutableStateFlow(deviceId)

    override fun getDeviceId(): StateFlow<Uuid> = state.asStateFlow()

    override suspend fun refreshDeviceId() = Unit
}
