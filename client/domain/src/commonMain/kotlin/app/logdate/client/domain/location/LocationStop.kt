package app.logdate.client.domain.location

import app.logdate.client.repository.location.LocationCapturePipeline
import app.logdate.shared.model.Location
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A semantic stop derived from one or more consecutive location samples.
 */
data class LocationStop(
    val id: String,
    val location: Location,
    val startTime: Instant,
    val endTime: Instant,
    val sampleCount: Int,
    val maxInternalGap: Duration,
    val hasReliableDuration: Boolean,
    val evidenceKind: LocationStopEvidenceKind,
    val primaryPipeline: LocationCapturePipeline,
) {
    val duration: Duration get() = endTime - startTime
}

enum class LocationStopEvidenceKind {
    STAY,
    OBSERVATION,
}
