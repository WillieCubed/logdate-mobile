package app.logdate.core.status

import app.logdate.core.status.model.PresenceStatus
import kotlinx.coroutines.flow.Flow

interface PresenceProvider {

    /**
     * A flow of the online status of users that are being observed.
     */
    val observedStatuses: Flow<List<PresenceStatus>>

    /**
     * Set the online status of the current user.
     */
    fun setIsOnline(isOnline: Boolean)

    /**
     * Observe the online status of a user.
     *
     * @param userId The UID of the user to observe.
     *
     * @throws UserNotFoundException if the user is not found.
     */
    @Throws(UserNotFoundException::class)
    fun observeStatusForUser(userId: String): Flow<PresenceStatus>

    /**
     * Stop observing the online status of a user.
     *
     * If the user is not being observed, this method does nothing.
     *
     * @param userId The UID of the user to stop observing.
     */
    fun stopObservingStatusForUser(userId: String)
}
