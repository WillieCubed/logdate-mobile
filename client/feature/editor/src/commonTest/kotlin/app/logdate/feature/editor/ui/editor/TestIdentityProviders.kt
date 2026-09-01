package app.logdate.feature.editor.ui.editor

import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.identity.DeviceIdProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.Uuid

/**
 * Identity stand-ins for tests that save entries.
 *
 * Saving an entry logs a location, and a location row is owned by a canonical owner and a device.
 * These keep both stable so a test asserts on what it set up rather than on whatever identity the
 * surrounding environment happened to have.
 */
internal class TestCanonicalOwnerProvider(
    private val ownerId: String = "00000000-0000-4000-8000-000000000001",
) : CanonicalOwnerProvider {
    override suspend fun getCanonicalOwnerId(): String = ownerId

    override suspend fun hasBoundOwner(): Boolean = true
}

internal class TestDeviceIdProvider(
    deviceId: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000002"),
) : DeviceIdProvider {
    private val state = MutableStateFlow(deviceId)

    override fun getDeviceId(): StateFlow<Uuid> = state.asStateFlow()

    override suspend fun refreshDeviceId() = Unit
}
