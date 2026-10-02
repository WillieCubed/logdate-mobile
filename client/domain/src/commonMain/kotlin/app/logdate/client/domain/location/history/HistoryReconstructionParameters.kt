package app.logdate.client.domain.location.history

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The thresholds that turn raw location samples into visits and journeys.
 *
 * Phones report positions with error: a Wi-Fi fix indoors can land 50-150 m from where the phone
 * really is, and GPS reports a walking-pace speed while lying on a desk. These values decide how
 * much of that error is tolerated before the history shows a new place or a trip.
 */
data class HistoryReconstructionParameters(
    /** Longer than this without any sample, the history shows "Not recorded" instead of guessing. */
    val maximumGap: Duration = 10.minutes,
    /**
     * A recording that goes quiet for at most this long and resumes at the same spot continues the
     * visit instead of showing a gap. Phones that only report a fix after moving a few meters go
     * quiet exactly when someone sits still.
     */
    val maximumQuietStay: Duration = 30.minutes,
    /** How long someone has to stay in one spot before it counts as a visit. */
    val minimumStay: Duration = 5.minutes,
    /** Samples within this distance of a visit's centre belong to the visit. */
    val stayRadiusMeters: Double = 100.0,
    /** A sample's own reported accuracy widens the visit radius by at most this much. */
    val accuracyAllowanceMeters: Double = 50.0,
    /** Fixes less precise than this (or with no accuracy at all) never decide where a visit is. */
    val maximumPreciseAccuracyMeters: Float = 100f,
    /** Fixes away from a visit that span no longer than this before it resumes are drift, not a trip. */
    val excursionTolerance: Duration = 5.minutes,
    /** Two visits to the same place stay one visit when nothing in between went farther than this. */
    val neighborhoodMeters: Double = 250.0,
    /** A fix that implies travelling faster than this to get there and back is a measurement error. */
    val implausibleSpeedMetersPerSecond: Double = 70.0,
)
