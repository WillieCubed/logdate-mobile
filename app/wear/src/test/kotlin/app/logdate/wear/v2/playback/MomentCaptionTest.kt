package app.logdate.wear.v2.playback

import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.repository.transcription.TranscriptDocumentStatus
import app.logdate.client.repository.transcription.TranscriptSegment
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MomentCaptionTest {
    private val timed =
        TranscriptDocument(
            status = TranscriptDocumentStatus.FINAL,
            segments =
                listOf(
                    TranscriptSegment(segmentId = "a", text = "Walked to the lake", startMs = 0L, endMs = 4_000L),
                    TranscriptSegment(segmentId = "b", text = "Saw two herons", startMs = 5_000L, endMs = 9_000L),
                ),
        )

    @Test
    fun `the line spoken at the position is the caption`() {
        assertEquals("Walked to the lake", captionAt(voiceNote(transcript = timed), 2_000L))
        assertEquals("Saw two herons", captionAt(voiceNote(transcript = timed), 6_000L))
    }

    @Test
    fun `a line stays up for a moment after it ends`() {
        assertEquals("Walked to the lake", captionAt(voiceNote(transcript = timed), 4_800L))
    }

    @Test
    fun `nothing shows in a long pause`() {
        assertNull(captionAt(voiceNote(transcript = timed), 12_000L))
    }

    @Test
    fun `an untimed transcript has no captions but is the whole transcript`() {
        val untimed = TranscriptDocument.fromPlainText("A quiet morning")

        assertNull(captionAt(voiceNote(transcript = untimed), 1_000L))
        assertEquals("A quiet morning", fullTranscript(voiceNote(transcript = untimed)))
    }

    @Test
    fun `the plain transcription is used without a transcript document`() {
        assertEquals("Older text", fullTranscript(voiceNote(transcription = "Older text")))
    }

    @Test
    fun `a note without words has no transcript`() {
        assertNull(fullTranscript(voiceNote()))
    }
}
