package app.logdate.feature.location.timeline.ui.history

import app.logdate.client.domain.location.history.LocationHistorySnapshot
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.location.HistoryGap
import app.logdate.shared.model.location.JourneyLeg
import app.logdate.shared.model.location.LocationDayItem
import app.logdate.shared.model.location.PlaceVisit
import app.logdate.shared.model.location.SemanticPlace
import app.logdate.shared.model.location.TravelMode
import app.logdate.ui.timeline.MomentAudioUiState
import app.logdate.util.localTime
import app.logdate.util.toReadableDateShort
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal fun LocationDayItem.toHistoryUi(notes: List<JournalNote>): HistoryItemUi =
    when (this) {
        is PlaceVisit ->
            HistoryItemUi(
                id,
                HistoryItemKind.Visit,
                place?.name ?: "A place you visited",
                if (confirmedStay) historyTimeRange() else "Around ${start.localTime}",
                place?.locality.orEmpty(),
                notes.filter { it.uid.toString() in memoryIds }.map { it.toHistoryMemory() },
            )
        is JourneyLeg ->
            HistoryItemUi(
                id,
                HistoryItemKind.Journey,
                mode.humanName(),
                historyTimeRange(),
                if (end > start) "${(end - start).inWholeMinutes} min" else "Around ${start.localTime}",
            )
        is HistoryGap ->
            HistoryItemUi(
                id,
                HistoryItemKind.Gap,
                "Part of this day wasn’t recorded",
                historyTimeRange(),
                "You can add a missing visit.",
            )
    }

private fun LocationDayItem.historyTimeRange(): String {
    val zone = TimeZone.currentSystemDefault()
    return if (start.toLocalDateTime(zone).date == end.toLocalDateTime(zone).date) {
        "${start.localTime}–${end.localTime}"
    } else {
        "${start.toReadableDateShort()}, ${start.localTime}–${end.toReadableDateShort()}, ${end.localTime}"
    }
}

internal fun TravelMode.humanName(): String =
    when (this) {
        TravelMode.WALKING -> "Walked"
        TravelMode.RUNNING -> "Ran"
        TravelMode.CYCLING -> "Cycled"
        TravelMode.DRIVING -> "Drove"
        TravelMode.BUS -> "Bus"
        TravelMode.TRAIN -> "Train"
        else -> "Travelling"
    }

internal fun JournalNote.toHistoryMemory(): HistoryMemoryUi =
    when (this) {
        is JournalNote.Text -> HistoryMemoryUi(uid.toString(), content, HistoryMemoryKind.Text, creationTimestamp.toReadableDateShort())
        is JournalNote.Image ->
            HistoryMemoryUi(
                uid.toString(),
                caption.ifBlank { "A photo from this visit" },
                HistoryMemoryKind.Image,
                creationTimestamp.toReadableDateShort(),
                mediaRef,
            )
        is JournalNote.Video ->
            HistoryMemoryUi(
                uid.toString(),
                caption.ifBlank { "A video from this visit" },
                HistoryMemoryKind.Video,
                creationTimestamp.toReadableDateShort(),
                mediaRef,
            )
        is JournalNote.Audio ->
            HistoryMemoryUi(
                uid.toString(),
                "A recording from this visit",
                HistoryMemoryKind.Audio,
                creationTimestamp.toReadableDateShort(),
                audio = MomentAudioUiState(mediaRef, durationMs, noteId = uid, recordedAt = creationTimestamp),
            )
    }

internal fun LocationHistorySnapshot.visitPlaces(): List<SemanticPlace> =
    items
        .filterIsInstance<PlaceVisit>()
        .map {
            it.place ?: SemanticPlace("evidence:${it.evidenceIds.first()}", "A place you visited", it.latitude, it.longitude)
        }.distinctBy { it.id }

/** A place can have memories without evidence of a physical visit. */
internal fun LocationHistorySnapshot.collectionPlaces(): List<SemanticPlace> {
    val visited = visitPlaces()
    val linked = items.filterIsInstance<PlaceVisit>().flatMap { it.memoryIds }.toSet()
    val memoryPlaces =
        notes.filterNot { it.uid.toString() in linked }.mapNotNull { note ->
            val location = note.location ?: return@mapNotNull null
            val latitude = location.effectiveLatitude ?: return@mapNotNull null
            val longitude = location.effectiveLongitude ?: return@mapNotNull null
            val oldId = location.place?.id?.toString() ?: "memory:${note.uid}"
            val id = placeAliases[oldId] ?: oldId
            places.firstOrNull { it.id == id } ?: SemanticPlace(id, location.place?.name ?: "A place in your memories", latitude, longitude)
        }
    return (visited + memoryPlaces).distinctBy { it.id }
}

internal fun LocationHistorySnapshot.placeRows(): List<HistoryPlaceUi> =
    collectionPlaces().map { place ->
        val visits = items.filterIsInstance<PlaceVisit>().filter { (it.place?.id ?: "evidence:${it.evidenceIds.first()}") == place.id }
        val ids = visits.flatMap { it.memoryIds }.toSet()
        val allLinked = items.filterIsInstance<PlaceVisit>().flatMap { it.memoryIds }.toSet()
        val memories =
            notes
                .filter {
                    val oldId =
                        it.location
                            ?.place
                            ?.id
                            ?.toString() ?: "memory:${it.uid}"
                    it.uid.toString() in ids || (it.uid.toString() !in allLinked && (placeAliases[oldId] ?: oldId) == place.id)
                }.distinctBy { it.uid }
                .sortedByDescending { it.creationTimestamp }
        val lastVisit =
            visits.maxByOrNull { it.start }?.let {
                "Last visited ${it.start.toReadableDateShort()} · ${visits.size} ${if (visits.size == 1) "visit" else "visits"}"
            }
        val latestMemory = memories.firstOrNull()?.let { "Latest memory ${it.creationTimestamp.toReadableDateShort()}" }
        HistoryPlaceUi(
            place.id,
            place.name,
            listOfNotNull(place.locality, lastVisit, latestMemory).joinToString("\n"),
            memories.map { it.toHistoryMemory() },
        )
    }
