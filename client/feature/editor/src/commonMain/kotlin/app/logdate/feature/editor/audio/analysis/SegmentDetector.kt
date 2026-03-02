package app.logdate.feature.editor.audio.analysis

import app.logdate.feature.editor.audio.model.AudioSegment
import app.logdate.feature.editor.audio.model.SegmentType

/**
 * Detects significant segments in audio based on amplitude analysis.
 *
 * Segments are used for haptic feedback during scrubbing and for
 * intelligent seek points in the audio player.
 *
 * @param silenceThreshold Amplitude below which audio is considered silence (0.0-1.0)
 * @param peakThreshold Amplitude above which audio is considered a peak (0.0-1.0)
 * @param minPauseDurationMs Minimum duration of silence to be considered a significant pause
 */
class SegmentDetector(
    private val silenceThreshold: Float = 0.1f,
    private val peakThreshold: Float = 0.7f,
    private val minPauseDurationMs: Long = 500L,
) {
    /**
     * Detects segments in the given amplitude data.
     *
     * @param amplitudes Normalized amplitude values (0.0 to 1.0)
     * @param durationMs Total duration of the audio in milliseconds
     * @return List of detected segments, sorted by timestamp
     */
    fun detectSegments(
        amplitudes: List<Float>,
        durationMs: Long,
    ): List<AudioSegment> {
        if (amplitudes.isEmpty() || durationMs <= 0) return emptyList()

        val segments = mutableListOf<AudioSegment>()
        val msPerSample = durationMs.toFloat() / amplitudes.size

        detectSpeechOnsets(amplitudes, msPerSample, segments)
        detectSignificantPauses(amplitudes, msPerSample, segments)
        detectVolumePeaks(amplitudes, msPerSample, segments)

        return segments.sortedBy { it.timestampMs }
    }

    private fun detectSpeechOnsets(
        amplitudes: List<Float>,
        msPerSample: Float,
        segments: MutableList<AudioSegment>,
    ) {
        var inSilence = true
        for (i in amplitudes.indices) {
            val level = amplitudes[i]
            if (inSilence && level > silenceThreshold * 2) {
                segments.add(
                    AudioSegment(
                        timestampMs = (i * msPerSample).toLong(),
                        type = SegmentType.SPEECH_ONSET,
                    ),
                )
                inSilence = false
            } else if (!inSilence && level < silenceThreshold) {
                inSilence = true
            }
        }
    }

    private fun detectSignificantPauses(
        amplitudes: List<Float>,
        msPerSample: Float,
        segments: MutableList<AudioSegment>,
    ) {
        var silenceStartIndex: Int? = null

        for (i in amplitudes.indices) {
            val level = amplitudes[i]
            if (level < silenceThreshold) {
                if (silenceStartIndex == null) {
                    silenceStartIndex = i
                }
            } else {
                silenceStartIndex?.let { start ->
                    val silenceDuration = (i - start) * msPerSample
                    if (silenceDuration >= minPauseDurationMs) {
                        val midpoint = start + (i - start) / 2
                        segments.add(
                            AudioSegment(
                                timestampMs = (midpoint * msPerSample).toLong(),
                                type = SegmentType.SIGNIFICANT_PAUSE,
                            ),
                        )
                    }
                }
                silenceStartIndex = null
            }
        }
    }

    private fun detectVolumePeaks(
        amplitudes: List<Float>,
        msPerSample: Float,
        segments: MutableList<AudioSegment>,
    ) {
        for (i in 1 until amplitudes.size - 1) {
            val prev = amplitudes[i - 1]
            val curr = amplitudes[i]
            val next = amplitudes[i + 1]

            // Local maximum above threshold
            if (curr > peakThreshold && curr > prev && curr > next) {
                segments.add(
                    AudioSegment(
                        timestampMs = (i * msPerSample).toLong(),
                        type = SegmentType.VOLUME_PEAK,
                    ),
                )
            }
        }
    }
}
