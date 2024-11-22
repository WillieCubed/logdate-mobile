package app.logdate.core.status

import app.logdate.core.activitypub.LogdateServerBaseClient
import app.logdate.core.status.model.PresenceStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A domain-layer provider for observing the online status of users.
 *
 * Observers can subscribe to the online status of a user and receive updates when the user's status
 * changes.
 *
 * This implementation broadcasts the online status of users to all observers. It also sends updates
 * to the current user's personal data service when the user's status changes.
 *
 * TODO: Create LogDate API to send updates to the current user's personal data service.
 */
@Singleton
class DefaultPresenceProvider @Inject constructor(
    private val logdateServerClient: LogdateServerBaseClient,
) : PresenceProvider {

    private val _observedStatuses = MutableStateFlow<List<PresenceStatus>>(emptyList())

    /**
     * A cached value of the user's online status.
     *
     * This should only be used for testing purposes.
     */
    internal var isOnline: Boolean = false

    override val observedStatuses: Flow<List<PresenceStatus>>
        get() = _observedStatuses

    override fun setIsOnline(isOnline: Boolean) {
        this.isOnline = isOnline
    }

    override fun observeStatusForUser(userId: String): Flow<PresenceStatus> {
        // TODO: Actually observe the user's status
        _observedStatuses.value += PresenceStatus(userId, false)
        return _observedStatuses.map { statuses ->
            statuses.first { it.userId == userId }
        }
    }

    override fun stopObservingStatusForUser(userId: String) {
        _observedStatuses.value = _observedStatuses.value.filter { it.userId != userId }
    }
}