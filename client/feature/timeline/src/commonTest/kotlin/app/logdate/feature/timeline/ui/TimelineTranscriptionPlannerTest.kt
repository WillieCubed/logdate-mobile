package app.logdate.feature.timeline.ui

import app.logdate.client.repository.transcription.TranscriptionData
import app.logdate.client.repository.transcription.TranscriptionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Validates the planning logic for automatic audio transcription in the timeline.
 *
 * This suite ensures that transcription requests are only generated for notes that
 * are currently visible and do not already have an active or completed transcription.
 */
class TimelineTranscriptionPlannerTest {
    @Test
    fun `autoRequestableNoteIds returns only visible notes without transcription`() {
        val missingNoteId = Uuid.random()
        val completedNoteId = Uuid.random()
        val alreadyRequestedNoteId = Uuid.random()
        val now = Instant.parse("2025-03-01T08:00:00Z")

        val autoRequestable =
            autoRequestableNoteIds(
                visibleNoteIds = setOf(missingNoteId, completedNoteId, alreadyRequestedNoteId),
                transcriptions =
                    mapOf(
                        completedNoteId to
                            TranscriptionData(
                                noteId = completedNoteId,
                                text = "Finished transcript",
                                status = TranscriptionStatus.COMPLETED,
                                created = now,
                                lastUpdated = now,
                                id = Uuid.random(),
                            ),
                    ),
                alreadyRequestedNoteIds = setOf(alreadyRequestedNoteId),
            )

        assertEquals(setOf(missingNoteId), autoRequestable)
    }
}
