
package app.logdate.feature.core.main

import app.logdate.client.awareness.daylight.DaylightClassifier
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.domain.recommendation.HomeRecommendation
import app.logdate.client.domain.timeline.Moment
import app.logdate.client.domain.timeline.TimelineDay
import app.logdate.client.domain.timeline.TimelinePlaceVisit
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.Journal
import app.logdate.shared.model.Person
import app.logdate.ui.location.PlaceUiState
import app.logdate.ui.profiles.PersonUiState
import app.logdate.ui.profiles.toUiState
import app.logdate.ui.timeline.AudioNoteUiState
import app.logdate.ui.timeline.ImageNoteUiState
import app.logdate.ui.timeline.JournalBadgeUiState
import app.logdate.ui.timeline.MediaObjectUiState
import app.logdate.ui.timeline.MomentAudioUiState
import app.logdate.ui.timeline.MomentMediaUiState
import app.logdate.ui.timeline.MomentUiState
import app.logdate.ui.timeline.TextNoteUiState
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.ui.timeline.TimelineSuggestionBlock
import app.logdate.ui.timeline.VideoNoteUiState
import app.logdate.ui.timeline.createSemanticTimelineDayUiState
import app.logdate.ui.timeline.toDayEventUiState
import kotlin.uuid.Uuid

private fun TimelinePlaceVisit.toUiState(): PlaceUiState =
    PlaceUiState(
        id = id,
        title = name,
        latitude = latitude,
        longitude = longitude,
    )

internal fun TimelineDay.toHomeTimelineUiState(
    overrideNotes: List<JournalNote> = entries,
    membershipMap: Map<Uuid, List<Journal>> = emptyMap(),
): TimelineDayUiState {
    val noteUiStates = overrideNotes.toUiState(membershipMap)
    val placeUiStates = placesVisited.map { place -> place.toUiState() }
    val peopleUiStates = people.map(Person::toUiState)
    val momentUiStates = moments.toMomentUiStates(peopleUiStates)
    val eventUiStates = if (FeatureFlag.EVENTS.availableForLaunch) events.map { it.toDayEventUiState() } else emptyList()

    return createSemanticTimelineDayUiState(
        summary = tldr,
        date = date,
        moments = momentUiStates,
        people = peopleUiStates,
        notes = noteUiStates,
        placesVisited = placeUiStates,
        events = eventUiStates,
        isLoadingSummary = tldr.isEmpty(),
        isLoadingPeople = people.isEmpty() && tldr.isEmpty(),
    )
}

private fun List<Moment>.toMomentUiStates(dayPeople: List<PersonUiState>): List<MomentUiState> {
    val heroIndex =
        indices.maxByOrNull { i ->
            val m = this[i]
            m.media.size * 3 + m.textFragments.size * 2 + m.audio.size
        } ?: 0
    return mapIndexed { index, moment ->
        moment.toMomentUiState(isHero = index == heroIndex, dayPeople = dayPeople)
    }
}

private fun Moment.toMomentUiState(
    isHero: Boolean,
    dayPeople: List<PersonUiState>,
): MomentUiState {
    val timeOfDay = DaylightClassifier().classifyWithoutLocation(estimatedStart)
    val resolvedPeople =
        people.mapNotNull { name ->
            dayPeople.find { it.name.equals(name, ignoreCase = true) }
        }
    return MomentUiState(
        id = id.toString(),
        label = label,
        timeOfDay = timeOfDay,
        textSnippet = textFragments.firstOrNull()?.text,
        media = media.map { MomentMediaUiState(uri = it.uri, isVideo = it.isVideo) },
        audio =
            audio.firstOrNull()?.let {
                MomentAudioUiState(
                    uri = it.uri,
                    durationMs = it.durationMs,
                    noteId = it.sourceNoteId,
                    recordedAt = estimatedStart,
                )
            },
        places = places.map { PlaceUiState(id = it.id, title = it.name, latitude = it.latitude, longitude = it.longitude) },
        people = resolvedPeople,
        isHero = isHero,
    )
}

private fun List<JournalNote>.toUiState(membershipMap: Map<Uuid, List<Journal>> = emptyMap()): List<app.logdate.ui.timeline.NoteUiState> =
    sortedByDescending { note -> note.creationTimestamp }.map { note ->
        val noteJournals =
            membershipMap[note.uid]
                .orEmpty()
                .map { JournalBadgeUiState(journalId = it.id, title = it.title) }
        when (note) {
            is JournalNote.Text ->
                TextNoteUiState(
                    noteId = note.uid,
                    text = note.content,
                    timestamp = note.creationTimestamp,
                    journals = noteJournals,
                )
            is JournalNote.Image ->
                ImageNoteUiState(
                    noteId = note.uid,
                    uri = note.mediaRef,
                    timestamp = note.creationTimestamp,
                    caption = note.caption,
                    presentation = note.presentation,
                    journals = noteJournals,
                )
            is JournalNote.Audio ->
                AudioNoteUiState(
                    noteId = note.uid,
                    uri = note.mediaRef,
                    timestamp = note.creationTimestamp,
                    duration = note.durationMs,
                    journals = noteJournals,
                )
            is JournalNote.Video ->
                VideoNoteUiState(
                    noteId = note.uid,
                    uri = note.mediaRef,
                    timestamp = note.creationTimestamp,
                    caption = note.caption,
                    journals = noteJournals,
                )
        }
    }

internal fun HomeRecommendation.toTimelineSuggestionBlock(): TimelineSuggestionBlock? =
    when (this) {
        is HomeRecommendation.EmptyDay ->
            TimelineSuggestionBlock.EmptyDay(
                message = message,
                locationName = locationName,
            )
        is HomeRecommendation.CompleteYourDraft ->
            TimelineSuggestionBlock.CompleteDraft(
                draftId = draftId.toString(),
            )
        is HomeRecommendation.MemoryRecall ->
            TimelineSuggestionBlock.MemoryRecall(
                memoryDate = date,
                title = summary,
                people = people,
                mediaUris = mediaUris.map { uri -> MediaObjectUiState(uid = uri, uri = uri) },
                isAiGenerated = isAiGenerated,
            )
        is HomeRecommendation.UpcomingEvent ->
            TimelineSuggestionBlock.UpcomingEvent(
                eventId = eventId.toString(),
                title = title,
                startTime = startTime,
                placeName = placeName,
            )
        HomeRecommendation.None -> null
    }
