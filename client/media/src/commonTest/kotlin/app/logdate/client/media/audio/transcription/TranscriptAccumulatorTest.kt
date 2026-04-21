package app.logdate.client.media.audio.transcription

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies the behavior of [TranscriptAccumulator] in assembling full transcripts from incremental updates.
 *
 * These tests ensure that the accumulator correctly handles the transition between partial,
 * real-time updates and finalized transcript segments, while maintaining proper string
 * concatenation and timing information for [TimedUtterance]s.
 */
class TranscriptAccumulatorTest {
    @Test
    fun buildReturnsEmptyWhenNoSegmentsOrPartial() {
        val accumulator = TranscriptAccumulator()
        assertEquals("", accumulator.build())
    }

    @Test
    fun buildReturnsSingleSegment() {
        val accumulator = TranscriptAccumulator()
        accumulator.addSegment("Hello world.")
        assertEquals("Hello world.", accumulator.build())
    }

    @Test
    fun buildJoinsMultipleSegments() {
        val accumulator = TranscriptAccumulator()
        accumulator.addSegment("Hello world.")
        accumulator.addSegment("How are you?")
        assertEquals("Hello world. How are you?", accumulator.build())
    }

    @Test
    fun buildReturnsPartialWhenNoSegments() {
        val accumulator = TranscriptAccumulator()
        accumulator.setPartial("hello")
        assertEquals("hello", accumulator.build())
    }

    @Test
    fun buildAppendsPartialToSegments() {
        val accumulator = TranscriptAccumulator()
        accumulator.addSegment("Hello world.")
        accumulator.setPartial("how are")
        assertEquals("Hello world. how are", accumulator.build())
    }

    @Test
    fun addSegmentClearsPartial() {
        val accumulator = TranscriptAccumulator()
        accumulator.setPartial("hello world")
        accumulator.addSegment("Hello world.")
        assertEquals("Hello world.", accumulator.build())
    }

    @Test
    fun buildTimedTranscriptReturnsAccumulatedUtterances() {
        val accumulator = TranscriptAccumulator()
        val utterance =
            TimedUtterance(
                text = "Hello world.",
                startMs = 0,
                endMs = 1000,
                words =
                    listOf(
                        TimedWord("Hello", "hello", 0, 500),
                        TimedWord("world", "world", 500, 1000),
                    ),
            )

        accumulator.addSegment("Hello world.", utterance)

        assertEquals(listOf(utterance), accumulator.buildTimedTranscript()?.utterances)
    }

    @Test
    fun resetClearsEverything() {
        val accumulator = TranscriptAccumulator()
        accumulator.addSegment("Hello world.")
        accumulator.setPartial("how")
        accumulator.reset()
        assertEquals("", accumulator.build())
    }
}
