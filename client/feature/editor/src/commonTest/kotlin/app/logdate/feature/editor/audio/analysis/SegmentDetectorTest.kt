package app.logdate.feature.editor.audio.analysis

import app.logdate.feature.editor.audio.model.SegmentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validates the audio segmentation algorithm used for detecting logical events in recordings.
 *
 * These tests verify [SegmentDetector]'s ability to accurately identify:
 * - Speech onsets after periods of silence
 * - Significant volume peaks
 * - Substantial pauses vs short, ignorable gaps
 * - Sorted temporal sequencing of all detected segments
 */
class SegmentDetectorTest {
    private val detector = SegmentDetector()

    @Test
    fun emptyAmplitudesReturnsEmptySegments() {
        val result = detector.detectSegments(emptyList(), 1000L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun zeroDurationReturnsEmptySegments() {
        val amplitudes = listOf(0.5f, 0.6f, 0.4f)
        val result = detector.detectSegments(amplitudes, 0L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun detectsSpeechOnsetAfterSilence() {
        // Silence followed by speech
        val amplitudes = listOf(0.05f, 0.05f, 0.05f, 0.5f, 0.6f, 0.5f)
        val result = detector.detectSegments(amplitudes, 6000L)

        val onsets = result.filter { it.type == SegmentType.SPEECH_ONSET }
        assertEquals(1, onsets.size)
        // Onset should be at index 3 (around 3000ms)
        assertTrue(onsets.first().timestampMs in 2500..3500)
    }

    @Test
    fun detectsVolumePeaks() {
        // Clear peak in the middle
        val amplitudes = listOf(0.3f, 0.5f, 0.9f, 0.5f, 0.3f)
        val result = detector.detectSegments(amplitudes, 5000L)

        val peaks = result.filter { it.type == SegmentType.VOLUME_PEAK }
        assertEquals(1, peaks.size)
        // Peak should be at index 2 (around 2000ms)
        assertTrue(peaks.first().timestampMs in 1500..2500)
    }

    @Test
    fun segmentsAreSortedByTimestamp() {
        // Multiple potential segments
        val amplitudes = List(100) { if (it % 20 == 10) 0.9f else 0.3f }
        val result = detector.detectSegments(amplitudes, 10000L)

        val timestamps = result.map { it.timestampMs }
        assertEquals(timestamps, timestamps.sorted())
    }

    @Test
    fun detectsSignificantPause() {
        // Speech, long silence, speech
        val amplitudes =
            buildList {
                repeat(10) { add(0.5f) } // Speech
                repeat(30) { add(0.05f) } // Long silence (should be detected)
                repeat(10) { add(0.5f) } // Speech
            }
        val result = detector.detectSegments(amplitudes, 5000L)

        val pauses = result.filter { it.type == SegmentType.SIGNIFICANT_PAUSE }
        assertEquals(1, pauses.size)
    }

    @Test
    fun ignoresShortPauses() {
        // Speech, short silence, speech
        val amplitudes =
            buildList {
                repeat(10) { add(0.5f) } // Speech
                repeat(3) { add(0.05f) } // Short silence (should NOT be detected)
                repeat(10) { add(0.5f) } // Speech
            }
        val result = detector.detectSegments(amplitudes, 2300L)

        val pauses = result.filter { it.type == SegmentType.SIGNIFICANT_PAUSE }
        assertEquals(0, pauses.size)
    }
}
