package app.logdate.client.domain.today

import app.logdate.client.domain.location.history.geographicDistance
import app.logdate.client.repository.journals.JournalNote
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/**
 * What a day contains so far, as Today, the Today tile and the phone's Watch page show it.
 *
 * @property momentCount Notes that are not mood check-ins.
 * @property placeCount Distinct places among the moments that have a location.
 * @property voiceDuration The length of every recording, added up.
 * @property latestMoment The newest moment, or null on a day without one.
 * @property feelingTag The tag of the newest mood check-in, or null.
 */
data class TodaySummary(
    val momentCount: Int,
    val placeCount: Int,
    val voiceDuration: Duration,
    val latestMoment: JournalNote?,
    val feelingTag: String?,
) {
    companion object {
        val Empty = TodaySummary(momentCount = 0, placeCount = 0, voiceDuration = Duration.ZERO, latestMoment = null, feelingTag = null)
    }
}

/**
 * Turns one day's notes into a [TodaySummary]. The caller passes only the day's notes.
 *
 * Two moments are at the same place when they name the same saved place, or when they were
 * captured within [SAME_PLACE_METERS] of each other.
 */
object TodaySummaryCalculator {
    const val SAME_PLACE_METERS = 150.0

    fun calculate(
        notes: List<JournalNote>,
        isKnownFeeling: (String) -> Boolean = { true },
    ): TodaySummary {
        val moments = notes.filterNot(MoodNoteTag::isFeeling)
        return TodaySummary(
            momentCount = moments.size,
            placeCount = countPlaces(moments),
            voiceDuration = moments.filterIsInstance<JournalNote.Audio>().sumOf { it.durationMs.coerceAtLeast(0) }.milliseconds,
            latestMoment = moments.maxByOrNull(JournalNote::creationTimestamp),
            feelingTag = MoodNoteTag.latestTag(notes, isKnownFeeling),
        )
    }

    private class PlaceCluster(
        val latitude: Double,
        val longitude: Double,
        val placeIds: MutableSet<Uuid>,
    )

    private fun countPlaces(moments: List<JournalNote>): Int {
        val clusters = mutableListOf<PlaceCluster>()
        moments.sortedBy(JournalNote::creationTimestamp).forEach { note ->
            val location = note.location ?: return@forEach
            val latitude = location.effectiveLatitude ?: return@forEach
            val longitude = location.effectiveLongitude ?: return@forEach
            val placeId = location.place?.id
            val cluster = clusters.firstOrNull { it.includes(placeId, latitude, longitude) }
            if (cluster == null) {
                clusters += PlaceCluster(latitude, longitude, listOfNotNull(placeId).toMutableSet())
                return@forEach
            }
            placeId?.let(cluster.placeIds::add)
        }
        return clusters.size
    }

    private fun PlaceCluster.includes(
        placeId: Uuid?,
        latitude: Double,
        longitude: Double,
    ): Boolean {
        if (placeId != null && placeId in placeIds) return true
        return geographicDistance(this.latitude, this.longitude, latitude, longitude) <= SAME_PLACE_METERS
    }
}
