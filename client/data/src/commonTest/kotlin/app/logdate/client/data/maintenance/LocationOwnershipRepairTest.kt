package app.logdate.client.data.maintenance

import app.logdate.client.database.dao.maintenance.IntegrityDao
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.identity.DeviceIdProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class LocationOwnershipRepairTest {
    private val ownerId = "11111111-1111-4111-8111-111111111111"
    private val deviceId = Uuid.parse("22222222-2222-4222-8222-222222222222")

    private fun repair(dao: IntegrityDao) =
        LocationOwnershipRepair(
            integrityDao = dao,
            canonicalOwnerProvider = FixedCanonicalOwnerProvider(ownerId),
            deviceIdProvider = FixedDeviceIdProvider(deviceId),
        )

    @Test
    fun `counts the rows still owned by the placeholder`() =
        runTest {
            assertEquals(7, repair(FakeIntegrityDao(placeholderOwned = 7)).count())
        }

    @Test
    fun `reassigns them to the canonical owner and this device`() =
        runTest {
            val dao = FakeIntegrityDao(placeholderOwned = 3)

            assertEquals(3, repair(dao).repair())
            assertEquals(ownerId, dao.reassignedOwnerId)
            assertEquals(deviceId.toString(), dao.reassignedDeviceId)
        }

    /** Safe to run on every launch: once nothing carries the placeholder it matches no rows. */
    @Test
    fun `is a no-op when nothing carries the placeholder`() =
        runTest {
            val dao = FakeIntegrityDao(placeholderOwned = 0)

            assertEquals(0, repair(dao).repair())
        }

    @Test
    fun `does not touch anything when only counting`() =
        runTest {
            val dao = FakeIntegrityDao(placeholderOwned = 5)

            repair(dao).count()

            assertNull(dao.reassignedOwnerId, "counting must not rewrite rows")
        }
}

private class FixedCanonicalOwnerProvider(
    private val ownerId: String,
) : CanonicalOwnerProvider {
    override suspend fun getCanonicalOwnerId(): String = ownerId

    override suspend fun hasBoundOwner(): Boolean = true
}

private class FixedDeviceIdProvider(
    deviceId: Uuid,
) : DeviceIdProvider {
    private val state = MutableStateFlow(deviceId)

    override fun getDeviceId(): StateFlow<Uuid> = state.asStateFlow()

    override suspend fun refreshDeviceId() = Unit
}

private class FakeIntegrityDao(
    private val placeholderOwned: Int,
) : IntegrityDao {
    var reassignedOwnerId: String? = null
    var reassignedDeviceId: String? = null

    override suspend fun countPlaceholderOwnedLocations(): Int = placeholderOwned

    override suspend fun reassignPlaceholderOwnedLocations(
        ownerId: String,
        deviceId: String,
    ): Int {
        reassignedOwnerId = ownerId
        reassignedDeviceId = deviceId
        return placeholderOwned
    }

    override suspend fun countOrphanedJournalLinks(): Int = 0

    override suspend fun countOrphanedContentLinks(): Int = 0

    override suspend fun deleteOrphanedJournalLinks(): Int = 0

    override suspend fun deleteOrphanedContentLinks(): Int = 0

    override suspend fun countPendingMissingJournals(
        ownerId: String,
        serverOrigin: String,
    ): Int = 0

    override suspend fun countPendingMissingNotes(
        ownerId: String,
        serverOrigin: String,
    ): Int = 0

    override suspend fun deletePendingMissingJournals(
        ownerId: String,
        serverOrigin: String,
    ): Int = 0

    override suspend fun deletePendingMissingNotes(
        ownerId: String,
        serverOrigin: String,
    ): Int = 0
}
