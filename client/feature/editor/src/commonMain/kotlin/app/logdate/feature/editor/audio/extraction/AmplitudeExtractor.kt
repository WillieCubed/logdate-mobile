package app.logdate.feature.editor.audio.extraction

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
}
