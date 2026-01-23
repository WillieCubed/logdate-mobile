package app.logdate.feature.editor.audio.extraction

/**
 * iOS implementation of AmplitudeExtractor.
 *
 * TODO: Implement using AVFoundation's AVAssetReader and AVAssetReaderTrackOutput
 */
class IosAmplitudeExtractor : AmplitudeExtractor {
    override suspend fun extractAmplitudes(uri: String, targetSampleCount: Int): List<Float> {
        // TODO: Implement using AVFoundation
        // Use AVAssetReader with AVAssetReaderTrackOutput to read audio samples
        // Then compute RMS and downsample similar to Android implementation
        return emptyList()
    }
}
