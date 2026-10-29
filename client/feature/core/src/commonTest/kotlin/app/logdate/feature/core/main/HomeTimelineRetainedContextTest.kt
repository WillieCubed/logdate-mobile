package app.logdate.feature.core.main

import app.logdate.client.domain.timeline.Moment
import app.logdate.client.domain.timeline.MomentInferenceSource
import app.logdate.client.domain.timeline.Timeline
import app.logdate.client.domain.timeline.TimelineDay
import app.logdate.client.domain.timeline.TimelineSummaryUnavailableReason
import app.logdate.client.domain.timeline.retainAvailableTimelineSummaries
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.Person
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class HomeTimelineRetainedContextTest {
    @Test
    fun offlineRefreshPreservesPeopleResolvedFromRetainedMoments() {
        val at = Instant.parse("2026-10-28T12:00:00Z")
        val date = LocalDate(2026, 10, 28)
        val note = JournalNote.Text(content = "Lunch with Alex", creationTimestamp = at, lastUpdated = at)
        val earlier = note.copy(uid = kotlin.uuid.Uuid.random(), content = "Earlier today")
        val notes = listOf(note, earlier)
        val moment =
            Moment(
                id = note.uid,
                label = "Lunch",
                estimatedStart = at,
                estimatedEnd = at,
                sourceNotes = notes,
                textFragments = emptyList(),
                media = emptyList(),
                audio = emptyList(),
                places = emptyList(),
                people = listOf("Alex"),
                inferenceSource = MomentInferenceSource.AI_INFERRED,
            )
        val loaded =
            TimelineDay(
                at,
                at,
                "Lunch with Alex",
                date,
                entries = notes,
                people = listOf(Person(name = "Alex")),
                moments = listOf(moment),
            )
        val partial = loaded.copy(tldr = "", entries = listOf(note), people = emptyList(), moments = emptyList(), isRecentWindow = true)
        val pending = retainAvailableTimelineSummaries(Timeline(listOf(loaded)), Timeline(listOf(partial)))
        val offline =
            loaded.copy(
                tldr = "",
                people = emptyList(),
                moments = emptyList(),
                summaryUnavailableReason = TimelineSummaryUnavailableReason.NO_NETWORK,
            )
        val retained = retainAvailableTimelineSummaries(pending, Timeline(listOf(offline))).days.single()
        val ui = retained.toHomeTimelineUiState()
        assertEquals(loaded.tldr, ui.summary)
        assertEquals(notes.map { it.uid }.toSet(), ui.moments.single().sourceNoteIds)
        assertEquals(
            listOf("Alex"),
            ui.moments
                .single()
                .people
                .map { it.name },
        )
    }
}
