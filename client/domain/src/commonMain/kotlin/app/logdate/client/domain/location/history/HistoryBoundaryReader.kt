package app.logdate.client.domain.location.history

import app.logdate.client.device.crypto.activityHistoryRecordId
import app.logdate.client.repository.location.ActivityHistoryRepository
import app.logdate.client.repository.location.HistoryRecord
import app.logdate.client.repository.location.HistoryRecordStore
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.shared.model.location.ActivityObservation
import app.logdate.shared.model.location.HistoryPayload
import app.logdate.shared.model.location.LocationObservation
import app.logdate.shared.model.location.TravelMode
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

internal data class BoundaryEvidence(
    val observations: List<LocationObservation>,
    val activities: List<ActivityObservation>,
    val activityFailed: Boolean = false,
)

/**
 * Pages only the uninterrupted component preceding a window, never assuming continuity across missing evidence.
 * A pathological uninterrupted component can still be large; truncating it would discard correction anchors.
 */
internal class HistoryBoundaryReader(
    private val raw: LocationHistoryRepository,
    private val store: HistoryRecordStore,
    private val activity: ActivityHistoryRepository?,
    private val decode: (HistoryRecord) -> HistoryPayload?,
) {
    suspend fun read(
        owner: String,
        origin: String,
        localDevice: String,
        observations: List<LocationObservation>,
        activities: List<ActivityObservation>,
        deletedSamples: Set<String>,
        deletedActivities: Set<String>,
    ): BoundaryEvidence {
        val prefix = mutableListOf<LocationObservation>()
        val allActivities = activities.toMutableList()
        var activityFailed = false
        for ((device, points) in observations.groupBy { it.deviceId }) {
            val validPoints = points.filter { it.validCoordinates() }
            if (validPoints.isEmpty()) continue
            var next = applyActivityEvidence(validPoints, allActivities).minWith(compareBy({ it.timestamp }, { it.id }))
            var cursorTime = next.timestamp
            var cursorId = next.id
            while (true) {
                val local =
                    raw.getLocationHistoryBefore(owner, device, cursorTime, cursorId, PAGE_SIZE) +
                        if (device ==
                            localDevice
                        ) {
                            raw.getLocationHistoryBefore("default_user", device, cursorTime, cursorId, PAGE_SIZE)
                        } else {
                            emptyList()
                        }
                val remote =
                    store
                        .observationsBefore(owner, origin, device, cursorTime.toEpochMilliseconds(), cursorId, PAGE_SIZE)
                        .mapNotNull { (decode(it) as? HistoryPayload.Observation)?.value }
                val page =
                    (local.map { it.toObservation(owner) } + remote)
                        .filter { it.ownerId == owner && it.deviceId == device }
                        .distinctBy { it.id }
                        .sortedWith(compareByDescending<LocationObservation> { it.timestamp }.thenByDescending { it.id })
                        .take(PAGE_SIZE)
                if (page.isEmpty()) break
                val valid = page.filter { it.id !in deletedSamples && it.validCoordinates() }
                if (valid.isNotEmpty()) {
                    val from = valid.last().timestamp - 10.minutes
                    val until = next.timestamp + 1.milliseconds
                    try {
                        val localEvents =
                            activity?.observeActivityHistoryBetween(from, until)?.first().orEmpty().map {
                                ActivityObservation(
                                    it.id,
                                    it.userId,
                                    it.deviceId,
                                    it.timestamp,
                                    it.activityType,
                                    it.transitionType,
                                    it.timeZoneId,
                                )
                            }
                        val remoteEvents =
                            store
                                .activitiesBetween(owner, origin, device, from.toEpochMilliseconds(), until.toEpochMilliseconds())
                                .mapNotNull { (decode(it) as? HistoryPayload.Activity)?.value }
                        allActivities +=
                            (localEvents + remoteEvents).filter {
                                it.ownerId == owner && it.deviceId == device && activityHistoryRecordId(it.id) !in deletedActivities
                            }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        activityFailed = true
                        Napier.w("Activity evidence before the selected day is unavailable", error)
                    }
                    val enriched = applyActivityEvidence(valid + next, allActivities)
                    next = enriched.last()
                    var reset = false
                    for (previous in enriched.dropLast(1)) {
                        if (provesBoundary(previous, next)) {
                            reset = true
                            break
                        }
                        prefix += previous
                        next = previous
                    }
                    if (reset) break
                }
                val oldest = page.last()
                cursorTime = oldest.timestamp
                cursorId = oldest.id
                if (page.size < PAGE_SIZE) break
            }
        }
        return BoundaryEvidence(observations + prefix, allActivities.distinctBy { Triple(it.ownerId, it.deviceId, it.id) }, activityFailed)
    }

    private fun provesBoundary(
        previous: LocationObservation,
        next: LocationObservation,
    ): Boolean {
        if (next.timestamp - previous.timestamp > 10.minutes) return true
        if (!previous.credible() || !next.credible()) return true
        if (previous.moving() != next.moving()) return true
        if (previous.moving()) return previous.activity != next.activity
        return geographicDistance(previous.latitude, previous.longitude, next.latitude, next.longitude) > 200
    }

    private fun LocationObservation.credible() = accuracyMeters?.let { it in 0f..100f } ?: true

    private fun LocationObservation.moving() = activity !in setOf(TravelMode.STILL, TravelMode.UNKNOWN) || (speedMetersPerSecond ?: 0f) > 1f

    private fun LocationObservation.validCoordinates() =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

    private companion object {
        const val PAGE_SIZE = 256
    }
}
