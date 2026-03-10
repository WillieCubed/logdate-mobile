package app.logdate.client.domain.location

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
) {
    val duration: Duration get() = endTime - startTime
}
