package app.logdate.ui.timeline

import app.logdate.ui.location.PlaceUiState
import app.logdate.ui.profiles.PersonUiState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

class TimelineDayUiStateFactoryTest {
    @Test
    fun `createTimelineDayUiState chooses media led layout when visual media is present`() {
        val state =
            createTimelineDayUiState(
                summary = "Wrapped up a photowalk and wrote down the best moments.",
                date = LocalDate(2025, 1, 15),
                people = listOf(PersonUiState(uid = Uuid.random(), name = "Alex")),
                placesVisited = listOf(PlaceUiState(id = "coffee", title = "Blue Bottle Coffee")),
                notes =
                    listOf(
                        ImageNoteUiState(
                            noteId = Uuid.random(),
                            uri = "file://photo.jpg",
                            timestamp = Instant.parse("2025-01-15T17:00:00Z"),
                        ),
                        TextNoteUiState(
                            noteId = Uuid.random(),
                            text = "Golden light on Market Street and a lot of good people watching.",
                            timestamp = Instant.parse("2025-01-15T18:30:00Z"),
                        ),
                    ),
            )

        assertEquals(TimelineDayCardLayout.MEDIA_LED, state.layout)
        assertIs<TimelineMediaSectionUiState>(state.heroSection)
        assertEquals(2, state.recap.captureCount)
        assertEquals(1, state.recap.mediaCount)
        assertEquals(1, state.recap.placeCount)
        assertEquals(1, state.recap.peopleCount)
        assertNotNull(state.supportingSummary)
    }

    @Test
    fun `createTimelineDayUiState chooses voice led layout when audio is the richest signal`() {
        val state =
            createTimelineDayUiState(
                summary = "",
                date = LocalDate(2025, 1, 16),
                people = emptyList(),
                placesVisited = emptyList(),
                notes =
                    listOf(
                        AudioNoteUiState(
                            noteId = Uuid.random(),
                            uri = "file://voice.m4a",
                            timestamp = Instant.parse("2025-01-16T09:00:00Z"),
                            duration = 45_000L,
                        ),
                        TextNoteUiState(
                            noteId = Uuid.random(),
                            text = "Left myself a short note after the walk home.",
                            timestamp = Instant.parse("2025-01-16T09:05:00Z"),
                        ),
                    ),
            )

        assertEquals(TimelineDayCardLayout.VOICE_LED, state.layout)
        assertIs<TimelineAudioSectionUiState>(state.heroSection)
        assertEquals(2, state.recap.captureCount)
        assertEquals(1, state.recap.audioCount)
        assertEquals(5, state.recap.activeSpanMinutes)
    }

    @Test
    fun `createTimelineDayUiState suppresses boilerplate summary copy`() {
        val state =
            createTimelineDayUiState(
                summary = "No summary available.",
                date = LocalDate(2025, 1, 17),
                people = emptyList(),
                placesVisited = listOf(PlaceUiState(id = "home", title = "Home")),
                notes =
                    listOf(
                        TextNoteUiState(
                            noteId = Uuid.random(),
                            text = "Quiet evening at home.",
                            timestamp = Instant.parse("2025-01-17T19:00:00Z"),
                        ),
                    ),
            )

        assertEquals(TimelineDayCardLayout.STORY_LED, state.layout)
        assertIs<TimelineTextSnippetSectionUiState>(state.heroSection)
        assertNull(state.supportingSummary)
    }
}
