package app.logdate.client.domain.location

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.NoteType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.round
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ObserveLocationMemoryPlacesUseCase(
    private val notesRepository: JournalNotesRepository,
    private val clock: Clock = Clock.System,
) {
    operator fun invoke(filter: LocationMemoryTimeFilter): Flow<List<LocationMemoryPlace>> =
        notesRepository.allNotesObserved.map { notes ->
            aggregatePlaces(
                notes = notes,
                filter = filter,
                now = clock.now(),
            )
        }

    internal fun aggregatePlaces(
        notes: List<JournalNote>,
        filter: LocationMemoryTimeFilter,
        now: Instant,
    ): List<LocationMemoryPlace> {
        val filteredNotes =
            notes
                .filter { note -> note.location?.hasLocation == true }
                .filter { note -> note.isWithin(filter, now) }

        return filteredNotes
            .groupBy { note -> note.locationGroupKey() }
            .map { (_, groupedNotes) -> groupedNotes.toLocationMemoryPlace() }
            .sortedByDescending(LocationMemoryPlace::lastVisitedAt)
    }

    private fun JournalNote.isWithin(
        filter: LocationMemoryTimeFilter,
        now: Instant,
    ): Boolean =
        when (filter) {
            LocationMemoryTimeFilter.Last30Days -> creationTimestamp >= now - 30.days
            LocationMemoryTimeFilter.Last90Days -> creationTimestamp >= now - 90.days
            LocationMemoryTimeFilter.YearToDate -> {
                val currentYear = now.toLocalDateTime(TimeZone.currentSystemDefault()).year
                creationTimestamp.toLocalDateTime(TimeZone.currentSystemDefault()).year == currentYear
            }
            LocationMemoryTimeFilter.AllTime -> true
        }

    private fun JournalNote.locationGroupKey(): String {
        val location = requireNotNull(location)
        val place = location.place
        if (place != null) {
            return "place:${place.id}"
        }

        val latitude = requireNotNull(location.effectiveLatitude)
        val longitude = requireNotNull(location.effectiveLongitude)
        return "coordinates:${roundCoordinate(latitude)},${roundCoordinate(longitude)}"
    }

    private fun List<JournalNote>.toLocationMemoryPlace(): LocationMemoryPlace {
        val firstLocation = first().location
        val semanticPlace = firstLocation?.place
        val latitude = mapNotNull { note -> note.location?.effectiveLatitude }.average()
        val longitude = mapNotNull { note -> note.location?.effectiveLongitude }.average()
        val memories = sortedByDescending(JournalNote::creationTimestamp).map { note -> note.toLocationMemory() }

        return LocationMemoryPlace(
            id = first().locationGroupKey(),
            semanticName = semanticPlace?.name,
            latitude = semanticPlace?.latitude ?: latitude,
            longitude = semanticPlace?.longitude ?: longitude,
            lastVisitedAt = memories.maxOf(LocationMemory::timestamp),
            memories = memories,
        )
    }

    private fun JournalNote.toLocationMemory(): LocationMemory {
        val location = requireNotNull(location)
        return LocationMemory(
            noteId = uid,
            timestamp = creationTimestamp,
            type = type,
            preview = previewText(),
            latitude = requireNotNull(location.effectiveLatitude),
            longitude = requireNotNull(location.effectiveLongitude),
        )
    }

    private fun JournalNote.previewText(): String =
        when (this) {
            is JournalNote.Text -> content
            is JournalNote.Image -> "Photo memory"
            is JournalNote.Audio -> "Audio memory"
            is JournalNote.Video -> "Video memory"
        }

    private fun roundCoordinate(value: Double): Double = round(value * 10_000) / 10_000
}

enum class LocationMemoryTimeFilter {
    Last30Days,
    Last90Days,
    YearToDate,
    AllTime,
}

data class LocationMemoryPlace(
    val id: String,
    val semanticName: String?,
    val latitude: Double,
    val longitude: Double,
    val lastVisitedAt: Instant,
    val memories: List<LocationMemory>,
) {
    val memoryCount: Int get() = memories.size
}

data class LocationMemory(
    val noteId: Uuid,
    val timestamp: Instant,
    val type: NoteType,
    val preview: String,
    val latitude: Double,
    val longitude: Double,
)
