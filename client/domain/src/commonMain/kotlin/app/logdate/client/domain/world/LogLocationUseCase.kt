package app.logdate.client.domain.world

import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.repository.timeline.ActivityTimelineRepository
import app.logdate.shared.model.ActivityTimelineItem
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A use case that logs the user's current location to the user's records.
 */
@OptIn(ExperimentalUuidApi::class)
class LogLocationUseCase(
    private val locationProvider: ClientLocationProvider,
    private val activityTimelineRepository: ActivityTimelineRepository,
) {

    /**
     * Logs the user's current location to the user's records.
     */
    suspend operator fun invoke() {
        val location = locationProvider.getCurrentLocation()
        val activity = ActivityTimelineItem(
            timestamp = Clock.System.now(),
            uid = Uuid.random(),
            location = location,
        )
        activityTimelineRepository.addActivity(activity)
    }
}