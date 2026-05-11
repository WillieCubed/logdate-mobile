package app.logdate.client.media.audio.transcription

import app.logdate.client.repository.transcription.TranscriptDocumentStatus
import app.logdate.client.repository.transcription.TranscriptSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranscriptDocumentMappingTest {
    @Test
    fun toTranscriptDocument_preserves_timed_utterances_and_words() {
        val timedTranscript =
            TimedTranscript(
                utterances =
                    listOf(
                        TimedUtterance(
                            text = "Hello world.",
                            startMs = 100,
                            endMs = 900,
                            words =
                                listOf(
                                    TimedWord("Hello", "hello", 100, 300),
                                    TimedWord("world", "world", 400, 800),
                                ),
                        ),
                    ),
            )

        val document =
            timedTranscript.toTranscriptDocument(
                status = TranscriptDocumentStatus.FINAL,
                source = TranscriptSource.LOCAL_REFINEMENT,
            )

        assertEquals("Hello world.", document.plainText)
        assertTrue(document.isFinal)
        assertEquals("utt-0", document.segments.single().segmentId)
        assertEquals(TranscriptSource.LOCAL_REFINEMENT, document.segments.single().source)
        assertEquals(
            listOf("hello", "world"),
            document
                .segments
                .single()
                .words
                .map { it.normalizedText },
        )
    }
}
