package app.logdate.client.media.audio.transcription

import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun resetClearsEverything() {
        val accumulator = TranscriptAccumulator()
        accumulator.addSegment("Hello world.")
        accumulator.setPartial("how")
        accumulator.reset()
        assertEquals("", accumulator.build())
    }
}
