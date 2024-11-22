package app.logdate.feature.editor.domain

import app.logdate.core.data.timeline.ActivityTimelineRepository
import app.logdate.core.world.NewLocationProvider
import app.logdate.model.ActivityTimelineItem
import kotlinx.datetime.Clock
import javax.inject.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A use case that logs the user's current location to the user's records.
 */
@OptIn(ExperimentalUuidApi::class)
class LogLocationUseCase @Inject constructor(
    private val locationProvider: NewLocationProvider,
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