package app.logdate.ui.timeline

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests utility functions designed to optimize the display and loading of timeline
 * content.
 *
 * This suite validates the logic for generating concise transcript excerpts for
 * UI previews and the strategy for pre-loading audio note IDs based on the user's
 * current scroll position in the timeline.
 */
class TimelineTranscriptionUtilsTest {
    @Test
    fun `buildTranscriptExcerpt returns first sentence when it stands on its own`() {
        val excerpt =
            buildTranscriptExcerpt(
                "Walked to the coffee shop before sunrise. Met Eli there and planned the week.",
            )

        assertEquals("Walked to the coffee shop before sunrise.", excerpt?.text)
        assertTrue(excerpt?.hasMore == true)
    }

    @Test
    fun `buildTranscriptExcerpt includes second sentence when first sentence is very short`() {
        val excerpt =
            buildTranscriptExcerpt(
                "Made it. The train doors shut right behind me and I finally relaxed.",
            )

        assertEquals(
            "Made it. The train doors shut right behind me and I finally relaxed.",
            excerpt?.text,
        )
        assertTrue(excerpt?.hasMore == false)
    }

    @Test
    fun `buildTranscriptExcerpt keeps a single long sentence intact`() {
        val transcript =
            "Spent the whole ride home recording every loose thought from the afternoon because I knew I'd forget the texture otherwise"

        val excerpt = buildTranscriptExcerpt(transcript)

        assertEquals(transcript, excerpt?.text)
        assertTrue(excerpt?.hasMore == false)
    }

    @Test
    fun `buildTranscriptExcerpt returns null for blank transcript`() {
        assertNull(buildTranscriptExcerpt("   "))
    }

    @Test
    fun `collectLazyTimelineAudioNoteIds includes visible cards and lookahead buffer`() {
        val firstVisibleAudioId = Uuid.random()
        val lookaheadAudioId = Uuid.random()
        val momentAudioId = Uuid.random()
        val items =
            listOf(
                timelineDayWithAudio(firstVisibleAudioId),
                TimelineDayUiState(summary = "Text only", date = LocalDate(2025, 3, 2)),
                timelineDayWithAudio(lookaheadAudioId),
                TimelineDayUiState(
                    summary = "Moment audio",
                    date = LocalDate(2025, 3, 4),
                    moments =
                        listOf(
                            MomentUiState(
                                id = "moment-1",
                                label = "Afternoon",
                                audio = MomentAudioUiState(uri = "file://moment.m4a", noteId = momentAudioId),
                            ),
                        ),
                ),
            )

        val visibleIds =
            collectLazyTimelineAudioNoteIds(
                items = items,
                visibleDayIndices = setOf(0),
                lookaheadCount = 2,
            )

        assertEquals(setOf(firstVisibleAudioId, lookaheadAudioId), visibleIds)
    }

    @Test
    fun `collectLazyTimelineAudioNoteIds includes all visible indices before lookahead`() {
        val visibleAudioId = Uuid.random()
        val secondVisibleAudioId = Uuid.random()
        val lookaheadMomentAudioId = Uuid.random()
        val items =
            listOf(
                TimelineDayUiState(summary = "Text only", date = LocalDate(2025, 3, 1)),
                timelineDayWithAudio(visibleAudioId).copy(date = LocalDate(2025, 3, 2)),
                timelineDayWithAudio(secondVisibleAudioId).copy(date = LocalDate(2025, 3, 3)),
                TimelineDayUiState(
                    summary = "Lookahead moment",
                    date = LocalDate(2025, 3, 4),
                    moments =
                        listOf(
                            MomentUiState(
                                id = "moment-2",
                                label = "Night",
                                audio = MomentAudioUiState(uri = "file://lookahead.m4a", noteId = lookaheadMomentAudioId),
                            ),
                        ),
                ),
            )

        val visibleIds =
            collectLazyTimelineAudioNoteIds(
                items = items,
                visibleDayIndices = setOf(1, 2),
                lookaheadCount = 1,
            )

        assertEquals(setOf(visibleAudioId, secondVisibleAudioId, lookaheadMomentAudioId), visibleIds)
    }

    private fun timelineDayWithAudio(noteId: Uuid): TimelineDayUiState =
        TimelineDayUiState(
            summary = "Audio day",
            date = LocalDate(2025, 3, 1),
            notes =
                listOf(
                    AudioNoteUiState(
                        noteId = noteId,
                        uri = "file://voice-$noteId.m4a",
                        timestamp = Instant.parse("2025-03-01T08:00:00Z"),
                        duration = 42_000L,
                    ),
                ),
        )
}
