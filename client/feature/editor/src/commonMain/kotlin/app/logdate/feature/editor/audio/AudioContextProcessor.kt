package app.logdate.feature.editor.audio

import app.logdate.feature.editor.audio.analysis.SegmentDetector
import app.logdate.feature.editor.audio.color.DaylightClassifier
import app.logdate.feature.editor.audio.color.PaletteGenerator
import app.logdate.feature.editor.audio.extraction.AmplitudeExtractor
import app.logdate.feature.editor.audio.model.AudioPalette
import app.logdate.feature.editor.audio.model.AudioSegment
import app.logdate.feature.editor.audio.model.DaylightPeriod
import app.logdate.feature.editor.audio.storage.WaveformStorage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant

/**
 * Processed audio context containing all derived data for visualization.
 *
 * @param amplitudes Normalized waveform amplitudes (0.0 to 1.0)
 * @param segments Detected audio segments for haptic feedback
 * @param daylightPeriod Time-of-day classification
 * @param palette Contextual color palette based on daylight period
 */
data class AudioContext(
    val amplitudes: List<Float>,
    val segments: List<AudioSegment>,
    val daylightPeriod: DaylightPeriod,
    val palette: AudioPalette,
)

/**
 * Processes audio files to extract context for visualization.
 *
 * Orchestrates:
 * - Waveform extraction and caching
 * - Segment detection
 * - Daylight classification
 * - Palette generation
 *
 * Results are cached using [WaveformStorage] to avoid re-processing
 * on subsequent loads.
 */
class AudioContextProcessor(
    private val amplitudeExtractor: AmplitudeExtractor,
    private val waveformStorage: WaveformStorage,
    private val segmentDetector: SegmentDetector = SegmentDetector(),
    private val daylightClassifier: DaylightClassifier = DaylightClassifier(),
    private val paletteGenerator: PaletteGenerator = PaletteGenerator(),
    private val coroutineContext: CoroutineContext = Dispatchers.Default,
) {
    /**
     * Processes an audio file and returns its context for visualization.
     *
     * If waveform data is cached, it will be loaded from storage.
     * Otherwise, the audio file is decoded and amplitudes are extracted
     * and cached for future use.
     *
     * @param audioUri URI of the audio file
     * @param durationMs Duration of the audio in milliseconds
     * @param createdAt When the audio was recorded
     * @param latitude Latitude where recorded (null if unavailable)
     * @param longitude Longitude where recorded (null if unavailable)
     * @return AudioContext with all derived data for visualization
     */
    suspend fun process(
        audioUri: String,
        durationMs: Long,
        createdAt: Instant,
        latitude: Double?,
        longitude: Double?,
    ): AudioContext =
        withContext(coroutineContext) {
            Napier.d { "Processing audio context for $audioUri" }

            val amplitudes = loadOrExtractAmplitudes(audioUri)
            val segments =
                if (amplitudes.isNotEmpty() && durationMs > 0) {
                    segmentDetector.detectSegments(amplitudes, durationMs)
                } else {
                    emptyList()
                }
            val daylightPeriod = classifyDaylightPeriod(createdAt, latitude, longitude)
            val palette = paletteGenerator.generate(daylightPeriod)

            Napier.d { "Audio context processed: ${amplitudes.size} amplitudes, ${segments.size} segments, $daylightPeriod" }

            AudioContext(
                amplitudes = amplitudes,
                segments = segments,
                daylightPeriod = daylightPeriod,
                palette = palette,
            )
        }

    /**
     * Loads cached amplitudes or extracts them from the audio file.
     */
    private suspend fun loadOrExtractAmplitudes(audioUri: String): List<Float> {
        waveformStorage.load(audioUri)?.let { cached ->
            Napier.d { "Loaded cached waveform for $audioUri" }
            return cached
        }

        Napier.d { "Extracting amplitudes from $audioUri" }
        val amplitudes = amplitudeExtractor.extractAmplitudes(audioUri)

        if (amplitudes.isNotEmpty()) {
            try {
                waveformStorage.save(audioUri, amplitudes)
                Napier.d { "Cached waveform for $audioUri" }
            } catch (e: Exception) {
                Napier.w(e) { "Failed to cache waveform for $audioUri" }
            }
        }

        return amplitudes
    }

    /**
     * Builds an initial context for immediate display while waveform extraction runs.
     *
     * Palette and daylight period are derived from the recording timestamp and location —
     * fast operations that don't require audio decoding. Amplitudes and segments are empty
     * and will be populated once [process] completes in the background.
     */
    fun buildInitialContext(
        createdAt: Instant,
        latitude: Double?,
        longitude: Double?,
    ): AudioContext {
        val daylightPeriod = classifyDaylightPeriod(createdAt, latitude, longitude)
        return AudioContext(
            amplitudes = emptyList(),
            segments = emptyList(),
            daylightPeriod = daylightPeriod,
            palette = paletteGenerator.generate(daylightPeriod),
        )
    }

    private fun classifyDaylightPeriod(
        createdAt: Instant,
        latitude: Double?,
        longitude: Double?,
    ): DaylightPeriod =
        if (latitude != null && longitude != null) {
            daylightClassifier.classify(createdAt, latitude, longitude)
        } else {
            daylightClassifier.classifyWithoutLocation(createdAt)
        }

    /**
     * Clears cached waveform data for an audio file.
     *
     * Call this when the audio file is deleted.
     */
    suspend fun clearCache(audioUri: String) {
        try {
            waveformStorage.delete(audioUri)
            Napier.d { "Cleared waveform cache for $audioUri" }
        } catch (e: Exception) {
            Napier.w(e) { "Failed to clear waveform cache for $audioUri" }
        }
    }
}
