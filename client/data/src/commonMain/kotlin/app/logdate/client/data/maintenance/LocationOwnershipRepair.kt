package app.logdate.client.data.maintenance

import app.logdate.client.database.dao.maintenance.IntegrityDao
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.identity.DeviceIdProvider

/**
 * Hands location history back to the identity that recorded it.
 *
 * Location logging used to stamp every row with the literals `user_1` and `device_1`, so a user's
 * whole location history was owned by an account that does not exist and a device that is not
 * theirs. Rows written before that was fixed still carry the placeholders.
 *
 * Reassigning them is not a guess. The placeholder recorded nothing about whose the row was, and
 * these rows were written by this person on this device, so the canonical owner is the only
 * candidate there has ever been. Rows already carrying a real identity are left alone.
 */
class LocationOwnershipRepair(
    private val integrityDao: IntegrityDao,
    private val canonicalOwnerProvider: CanonicalOwnerProvider,
    private val deviceIdProvider: DeviceIdProvider,
) {
    /** How many location rows are still owned by the placeholder identity. */
    suspend fun count(): Int = integrityDao.countPlaceholderOwnedLocations()

    /**
     * Reassigns any placeholder-owned rows, returning how many were changed.
     *
     * Safe to run repeatedly: once nothing carries the placeholder this matches no rows.
     */
    suspend fun repair(): Int =
        integrityDao.reassignPlaceholderOwnedLocations(
            ownerId = canonicalOwnerProvider.getCanonicalOwnerId(),
            deviceId = deviceIdProvider.getDeviceId().value.toString(),
        )
}
