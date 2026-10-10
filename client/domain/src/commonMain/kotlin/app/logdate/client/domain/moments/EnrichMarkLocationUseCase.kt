package app.logdate.client.domain.moments

import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.WatchPreferencesRepository
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkedMomentRepository
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** What happened when the phone looked for a place for a mark. */
sealed interface MarkLocationResult {
    data class Filled(
        val location: MarkLocation,
    ) : MarkLocationResult

    /** The mark already had a place, or another path set one first. */
    data object AlreadyPlaced : MarkLocationResult

    /** Places are off, so the mark is left without one. */
    data object PlacesOff : MarkLocationResult

    /** No accurate, genuine sample falls within [EnrichMarkLocationUseCase.WINDOW] of the mark. */
    data object NoNearbySample : MarkLocationResult

    data object MarkNotFound : MarkLocationResult
}

/**
 * Gives a mark captured without a place the place this phone recorded around the same time.
 *
 * A mark from the watch face cannot read a location in the background, so the phone fills it in from
 * its own location history: the sample nearest in time within [WINDOW] either way, at most
 * [MAX_ACCURACY_METERS] uncertain, genuine and not mocked, with a tie in time going to the more
 * accurate sample. Nothing is filled unless places are on.
 */
class EnrichMarkLocationUseCase(
    private val marks: MarkedMomentRepository,
    private val locationHistory: LocationHistoryRepository,
    private val watchPreferences: WatchPreferencesRepository,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(markId: Uuid): MarkLocationResult {
        val mark = marks.getMark(markId) ?: return MarkLocationResult.MarkNotFound
        if (mark.location != null) return MarkLocationResult.AlreadyPlaced
        if (!watchPreferences.get(SharedPreferenceKey.CapturePlaces)) return MarkLocationResult.PlacesOff
        val sample = nearestSample(mark.capturedAt) ?: return MarkLocationResult.NoNearbySample
        val location =
            MarkLocation(
                latitude = sample.location.latitude,
                longitude = sample.location.longitude,
                accuracyMeters = sample.accuracyMeters,
            )
        if (!marks.fillLocationIfMissing(mark.id, location, clock.now())) return MarkLocationResult.AlreadyPlaced
        return MarkLocationResult.Filled(location)
    }

    private suspend fun nearestSample(capturedAt: Instant): LocationHistoryItem? =
        locationHistory
            .getLocationHistoryBetween(capturedAt - WINDOW, capturedAt + WINDOW)
            .filter { it.isGenuine && !it.isMock }
            .filter { sample -> sample.accuracyMeters?.let { it <= MAX_ACCURACY_METERS } == true }
            .filter { (it.timestamp - capturedAt).absoluteValue <= WINDOW }
            .minWithOrNull(
                compareBy<LocationHistoryItem> { (it.timestamp - capturedAt).absoluteValue }
                    .thenBy { it.accuracyMeters },
            )

    companion object {
        val WINDOW: Duration = 10.minutes
        const val MAX_ACCURACY_METERS = 100f
    }
}
