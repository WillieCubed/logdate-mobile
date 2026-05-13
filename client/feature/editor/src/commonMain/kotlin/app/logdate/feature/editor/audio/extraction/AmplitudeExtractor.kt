package app.logdate.feature.editor.audio.extraction

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Interface for extracting amplitude data from audio files.
 *
 * Implementations decode audio and compute RMS amplitude values
 * for visualization in the waveform component.
 */
interface AmplitudeExtractor {
    /**
     * Extracts normalized amplitude values from an audio file.
     *
     * @param uri The URI of the audio file to process
     * @param targetSampleCount The desired number of amplitude samples (default: 300)
     * @return List of normalized amplitude values (0.0 to 1.0), or empty list on failure
     */
    suspend fun extractAmplitudes(
        uri: String,
        targetSampleCount: Int = 300,
    ): List<Float>

    /**
     * Extracts amplitudes progressively, emitting intermediate snapshots while the
     * audio is still decoding. Lets the waveform fill in from the start to the end
     * instead of appearing all at once, which matters for long voice notes where
     * decode time is noticeable.
     *
     * The default implementation delegates to [extractAmplitudes] and emits the
     * full result in one go — that's correct on platforms whose decoders don't
     * expose mid-stream progress. Android overrides this to emit periodic
     * snapshots from the MediaCodec decode loop.
     *
     * @return A cold flow that emits at least one snapshot. The final emission is
     *         always the complete waveform; intermediate emissions show partial
     *         progress for visualization.
     */
    fun extractAmplitudesProgressively(
        uri: String,
        targetSampleCount: Int = 300,
    ): Flow<List<Float>> =
        flow {
            emit(extractAmplitudes(uri, targetSampleCount))
        }
}
