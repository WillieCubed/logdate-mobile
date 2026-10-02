package app.logdate.client.domain.location

import app.logdate.client.domain.location.history.ReconstructLocationDay
import app.logdate.client.domain.location.history.toObservation
import app.logdate.client.repository.location.LocationCaptureSource
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import app.logdate.shared.model.location.PlaceVisit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration

/**
 * Turns location history into the places someone stopped at, newest first.
 *
 * Stops come from the same reconstruction as the location history screen, so a drive or a noisy
 * fix never shows up as a stop here while the history shows a journey.
 */
class ObserveLocationStopsUseCase(
    private val observeLocationHistoryUseCase: ObserveLocationHistoryUseCase,
    private val reconstruct: ReconstructLocationDay = ReconstructLocationDay(),
) {
    operator fun invoke(): Flow<List<LocationStop>> = observeLocationHistoryUseCase().map(::aggregateStops)

    internal fun aggregateStops(history: List<LocationHistoryItem>): List<LocationStop> {
        val samples = history.filter { item -> item.countsTowardActivityStops() }
        val samplesById = samples.associateBy { it.sampleId }
        return reconstruct(samples.map { it.toObservation(it.userId) })
            .filterIsInstance<PlaceVisit>()
            .map { visit -> toStop(visit, visit.evidenceIds.mapNotNull(samplesById::get).sortedBy { it.timestamp }) }
            .sortedByDescending { it.endTime }
    }

    private fun toStop(
        visit: PlaceVisit,
        group: List<LocationHistoryItem>,
    ): LocationStop {
        val first = group.first()
        // Deleting a stop removes its time range, so the range covers every sample behind it.
        val startTime = minOf(visit.start, first.timestamp)
        val endTime = maxOf(visit.end, group.last().timestamp)
        val maxInternalGap =
            group
                .zipWithNext { previous, current -> current.timestamp - previous.timestamp }
                .maxOrNull() ?: Duration.ZERO
        return LocationStop(
            id = "${first.userId}:${first.deviceId}:${startTime.toEpochMilliseconds()}:${endTime.toEpochMilliseconds()}",
            location =
                Location(
                    latitude = visit.latitude,
                    longitude = visit.longitude,
                    altitude = LocationAltitude(group.map { it.location.altitude.value }.average(), AltitudeUnit.METERS),
                ),
            startTime = startTime,
            endTime = endTime,
            sampleCount = group.size,
            maxInternalGap = maxInternalGap,
            hasReliableDuration = visit.confirmedStay,
            evidenceKind = if (visit.confirmedStay) LocationStopEvidenceKind.STAY else LocationStopEvidenceKind.OBSERVATION,
            primaryPipeline = first.capturePipeline,
        )
    }

    private fun LocationHistoryItem.countsTowardActivityStops(): Boolean =
        captureSource != LocationCaptureSource.TIMELINE_REVIEW &&
            captureSource != LocationCaptureSource.JOURNAL_ENTRY
}
