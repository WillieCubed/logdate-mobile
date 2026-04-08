package app.logdate.client.intelligence.milestones

import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import io.github.aakira.napier.Napier
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Detects when the user's primary location has shifted enough to count as a "move".
 *
 * Algorithm:
 *  1. Pull the last [LOOKBACK_DAYS] days of location history.
 *  2. Bucket samples by ISO week and compute the centroid of each bucket.
 *  3. Walk the ordered weeks looking for a transition where the new week's centroid
 *     is at least [MIN_JUMP_KM] from the previous week's centroid AND the following
 *     [STABILIZATION_WEEKS] consecutive weeks all stay within [STABILIZATION_RADIUS_KM]
 *     of that new centroid. The stabilization requirement is the noise filter that
 *     keeps a one-week vacation from looking like a move.
 *  4. The first qualifying transition becomes a [MilestoneCandidate] whose window
 *     covers one week before the jump through two weeks after the jump, so the
 *     resulting rewind has enough surrounding context to feel like a chapter rather
 *     than a single Sunday.
 *
 * The detector is intentionally conservative — false positives are worse than false
 * negatives because the user reads each milestone rewind as the system claiming "your
 * life changed", and that should be true when it shows up.
 */
class LocationChangeMilestoneDetector(
    private val locationHistoryRepository: LocationHistoryRepository,
) : MilestoneDetector {
    override suspend fun detect(now: Instant): MilestoneCandidate? {
        val zone = TimeZone.UTC
        val history =
            try {
                locationHistoryRepository.getLocationHistoryBetween(
                    startTime = now.minus(LOOKBACK_DAYS.days),
                    endTime = now,
                )
            } catch (e: Exception) {
                Napier.w("LocationChangeMilestoneDetector: failed to read location history", e)
                return null
            }
        if (history.size < MIN_SAMPLES_TO_DETECT) return null

        val weekly = bucketByWeek(history, zone)
        if (weekly.size < MIN_WEEKS_TO_DETECT) return null

        val ordered = weekly.entries.sortedBy { it.key }
        for (i in 1 until ordered.size - STABILIZATION_WEEKS) {
            val previous = ordered[i - 1].value
            val candidate = ordered[i].value
            val jumpKm = previous.distanceKmTo(candidate)
            if (jumpKm < MIN_JUMP_KM) continue
            val followUpStable =
                (1..STABILIZATION_WEEKS).all { offset ->
                    val followUp = ordered.getOrNull(i + offset)?.value ?: return@all false
                    candidate.distanceKmTo(followUp) <= STABILIZATION_RADIUS_KM
                }
            if (!followUpStable) continue

            // Build a [-1 week, +2 week] window around the jump so the rewind has the
            // before-and-after context that makes a move feel like a chapter.
            val jumpWeekStart = ordered[i].key
            val windowStartDate = jumpWeekStart.minus(7L * 1, DateTimeUnit.DAY)
            val windowEndDate = jumpWeekStart.plus(7L * STABILIZATION_WEEKS, DateTimeUnit.DAY)
            return MilestoneCandidate(
                kind = MilestoneKind.LOCATION_CHANGE,
                startTime = windowStartDate.atStartOfWeek(zone),
                endTime = windowEndDate.atStartOfWeek(zone),
                summary = "Your location shifted",
            )
        }
        return null
    }

    private fun bucketByWeek(
        history: List<LocationHistoryItem>,
        zone: TimeZone,
    ): Map<LocalDate, Centroid> {
        val buckets = mutableMapOf<LocalDate, MutableList<LocationHistoryItem>>()
        history.forEach { item ->
            val date = item.timestamp.toLocalDateTime(zone).date
            // Floor to ISO Monday so each bucket key is the week's start.
            val daysBack = (date.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal + 7) % 7
            val weekStart = date.minus(daysBack.toLong(), DateTimeUnit.DAY)
            buckets.getOrPut(weekStart) { mutableListOf() } += item
        }
        return buckets.mapValues { (_, items) -> items.centroid() }
    }

    private fun List<LocationHistoryItem>.centroid(): Centroid {
        var latSum = 0.0
        var lonSum = 0.0
        forEach {
            latSum += it.location.latitude
            lonSum += it.location.longitude
        }
        return Centroid(latitude = latSum / size, longitude = lonSum / size)
    }

    private fun LocalDate.atStartOfWeek(zone: TimeZone): Instant {
        val daysBack = (dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal + 7) % 7
        val mondayDate = minus(daysBack.toLong(), DateTimeUnit.DAY)
        return mondayDate.atStartOfDay(zone)
    }

    private fun LocalDate.atStartOfDay(zone: TimeZone): Instant = atTime(0, 0).toInstant(zone)

    private data class Centroid(
        val latitude: Double,
        val longitude: Double,
    ) {
        /**
         * Equirectangular distance approximation in kilometers — accurate enough for
         * the "did the centroid move tens of km" question this detector asks. Avoids
         * the haversine sin/cos cost for inputs that all live in the same hemisphere.
         */
        fun distanceKmTo(other: Centroid): Double {
            val avgLatRad = ((latitude + other.latitude) / 2.0) * PI / 180.0
            val xKm = (other.longitude - longitude) * KM_PER_DEGREE * cos(avgLatRad)
            val yKm = (other.latitude - latitude) * KM_PER_DEGREE
            return sqrt(xKm * xKm + yKm * yKm)
        }
    }

    private companion object {
        const val LOOKBACK_DAYS = 90
        const val MIN_SAMPLES_TO_DETECT = 14
        const val MIN_WEEKS_TO_DETECT = 4
        const val STABILIZATION_WEEKS = 2
        const val MIN_JUMP_KM = 25.0
        const val STABILIZATION_RADIUS_KM = 10.0
        const val KM_PER_DEGREE = 111.0
    }
}
