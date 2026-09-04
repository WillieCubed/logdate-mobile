package app.logdate.ui.timeline

import app.logdate.ui.location.PlaceUiState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests [createSemanticTimelineDayUiState], the sole builder behind the timeline.
 *
 * Two behaviours matter here. The accent treatment must follow what a day actually held, so a
 * run of days reads as varied rather than uniform. And the builder must always produce at least
 * one moment, because the timeline has a single renderer and a day that yields no moments would
 * otherwise render nothing at all.
 */
class TimelineDayUiStateFactoryTest {
    @Test
    fun `day with photos is media led`() {
        val state =
            createSemanticTimelineDayUiState(
                summary = "Wrapped up a photowalk and wrote down the best moments.",
                date = LocalDate(2025, 1, 15),
                moments =
                    listOf(
                        MomentUiState(
                            id = "moment-1",
                            label = "At Blue Bottle Coffee",
                            media = listOf(MomentMediaUiState(uri = "file://photo.jpg")),
                        ),
                    ),
                placesVisited = listOf(PlaceUiState(id = "coffee", title = "Blue Bottle Coffee")),
            )

        assertEquals(TimelineDayCardLayout.MEDIA_LED, state.layout)
        assertNotNull(state.supportingSummary)
    }

    @Test
    fun `day with audio and no photos is voice led`() {
        val state =
            createSemanticTimelineDayUiState(
                summary = "Talked it through on the walk home.",
                date = LocalDate(2025, 1, 16),
                moments =
                    listOf(
                        MomentUiState(
                            id = "moment-1",
                            label = "",
                            audio = MomentAudioUiState(uri = "file://voice.m4a", durationMs = 45_000L),
                        ),
                    ),
            )

        assertEquals(TimelineDayCardLayout.VOICE_LED, state.layout)
    }

    @Test
    fun `photos outrank audio when a day holds both`() {
        val state =
            createSemanticTimelineDayUiState(
                summary = "A day with both.",
                date = LocalDate(2025, 1, 16),
                moments =
                    listOf(
                        MomentUiState(
                            id = "moment-1",
                            label = "",
                            audio = MomentAudioUiState(uri = "file://voice.m4a", durationMs = 45_000L),
                        ),
                        MomentUiState(
                            id = "moment-2",
                            label = "",
                            media = listOf(MomentMediaUiState(uri = "file://photo.jpg")),
                        ),
                    ),
            )

        assertEquals(TimelineDayCardLayout.MEDIA_LED, state.layout)
    }

    @Test
    fun `day with several places and no media is place led`() {
        val state =
            createSemanticTimelineDayUiState(
                summary = "Moved around a lot.",
                date = LocalDate(2025, 1, 16),
                moments = listOf(MomentUiState(id = "moment-1", label = "", textSnippet = "Busy one.")),
                placesVisited =
                    listOf(
                        PlaceUiState(id = "home", title = "Home"),
                        PlaceUiState(id = "coffee", title = "Blue Bottle Coffee"),
                    ),
            )

        assertEquals(TimelineDayCardLayout.PLACE_LED, state.layout)
    }

    @Test
    fun `text only day is story led`() {
        val state =
            createSemanticTimelineDayUiState(
                summary = "Quiet one.",
                date = LocalDate(2025, 1, 17),
                moments = listOf(MomentUiState(id = "moment-1", label = "", textSnippet = "Quiet evening at home.")),
                placesVisited = listOf(PlaceUiState(id = "home", title = "Home")),
            )

        assertEquals(TimelineDayCardLayout.STORY_LED, state.layout)
    }

    @Test
    fun `suppresses boilerplate summary copy`() {
        val state =
            createSemanticTimelineDayUiState(
                summary = "No summary available.",
                date = LocalDate(2025, 1, 17),
                moments = listOf(MomentUiState(id = "moment-1", label = "", textSnippet = "Quiet evening at home.")),
            )

        assertNull(state.supportingSummary)
    }

    @Test
    fun `synthesizes a moment from notes when inference produced none`() {
        val audioNoteId = Uuid.random()
        val state =
            createSemanticTimelineDayUiState(
                summary = "A day inference could not describe.",
                date = LocalDate(2025, 1, 18),
                moments = emptyList(),
                notes =
                    listOf(
                        AudioNoteUiState(
                            noteId = audioNoteId,
                            uri = "file://voice.m4a",
                            timestamp = Instant.parse("2025-01-18T09:00:00Z"),
                            duration = 45_000L,
                        ),
                        TextNoteUiState(
                            noteId = Uuid.random(),
                            text = "Left myself a short note after the walk home.",
                            timestamp = Instant.parse("2025-01-18T09:05:00Z"),
                        ),
                    ),
                placesVisited = listOf(PlaceUiState(id = "home", title = "Home")),
            )

        val moment = state.moments.single()
        assertEquals("", moment.label)
        assertTrue(moment.isHero)
        assertEquals("Left myself a short note after the walk home.", moment.textSnippet)
        assertEquals(audioNoteId, moment.audio?.noteId)
        assertEquals(45_000L, moment.audio?.durationMs)
        assertEquals(listOf("home"), moment.places.map(PlaceUiState::id))
    }

    @Test
    fun `synthesized moment carries photos so the day still renders media`() {
        val state =
            createSemanticTimelineDayUiState(
                summary = "Photos only.",
                date = LocalDate(2025, 1, 18),
                moments = emptyList(),
                notes =
                    listOf(
                        ImageNoteUiState(
                            noteId = Uuid.random(),
                            uri = "file://photo.jpg",
                            timestamp = Instant.parse("2025-01-18T18:00:00Z"),
                        ),
                        VideoNoteUiState(
                            noteId = Uuid.random(),
                            uri = "file://video.mp4",
                            thumbnailUri = "file://video-thumb.jpg",
                            timestamp = Instant.parse("2025-01-18T19:00:00Z"),
                        ),
                    ),
            )

        assertEquals(TimelineDayCardLayout.MEDIA_LED, state.layout)
        assertEquals(
            listOf("file://video-thumb.jpg", "file://photo.jpg"),
            state.moments.single().media.map(MomentMediaUiState::uri),
        )
    }

    @Test
    fun `a day with nothing to show produces no moments`() {
        val state =
            createSemanticTimelineDayUiState(
                summary = "Nothing here.",
                date = LocalDate(2025, 1, 19),
                moments = emptyList(),
            )

        assertTrue(state.moments.isEmpty())
    }

    @Test
    fun `exposes visual notes as media objects`() {
        val state =
            createSemanticTimelineDayUiState(
                summary = "A day with photos and video.",
                date = LocalDate(2025, 1, 18),
                moments = emptyList(),
                notes =
                    listOf(
                        ImageNoteUiState(
                            noteId = Uuid.random(),
                            uri = "file://photo.jpg",
                            timestamp = Instant.parse("2025-01-18T18:00:00Z"),
                        ),
                        VideoNoteUiState(
                            noteId = Uuid.random(),
                            uri = "file://video.mp4",
                            thumbnailUri = "file://video-thumb.jpg",
                            timestamp = Instant.parse("2025-01-18T19:00:00Z"),
                        ),
                    ),
            )

        assertEquals(
            listOf("file://video-thumb.jpg", "file://photo.jpg"),
            state.mediaUris.map(MediaObjectUiState::uri),
        )
    }
}
