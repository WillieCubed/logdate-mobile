package app.logdate.feature.editor.audio.extraction

/**
 * Desktop/JVM implementation of AmplitudeExtractor.
 *
 * TODO: Implement using javax.sound.sampled or a library like JAVE/FFmpeg bindings
 */
class DesktopAmplitudeExtractor : AmplitudeExtractor {
    override suspend fun extractAmplitudes(uri: String, targetSampleCount: Int): List<Float> {
        // TODO: Implement using javax.sound.sampled
        // Or use FFmpeg bindings for broader format support
        return emptyList()
    }
}
