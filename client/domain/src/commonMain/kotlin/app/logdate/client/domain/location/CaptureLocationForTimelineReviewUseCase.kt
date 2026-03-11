package app.logdate.client.domain.location

import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.repository.location.LocationCaptureSource

/**
 * Captures the current location when the user actively reviews their timeline, if enabled.
 */
class CaptureLocationForTimelineReviewUseCase(
    private val settingsRepository: LocationTrackingSettingsRepository,
    private val logCurrentLocationUseCase: LogCurrentLocationUseCase,
) {
    suspend operator fun invoke(
        request: LogCurrentLocationUseCase.LocationLogRequest.LogLocation =
            LogCurrentLocationUseCase.LocationLogRequest.LogLocation(
                captureSource = LocationCaptureSource.TIMELINE_REVIEW,
            ),
    ) {
        if (!settingsRepository.getSettings().autoTrackForTimelineReview) {
            return
        }

        logCurrentLocationUseCase(request)
    }
}
