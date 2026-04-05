package app.logdate.feature.editor.audio

import app.logdate.client.awareness.daylight.DaylightClassifier
import app.logdate.client.awareness.daylight.DaylightPeriod
import kotlin.time.Instant

/**
 * Structured result from audio label resolution, carrying the data needed
 * for the UI layer to format a localized label via string resources.
 */
sealed interface AudioLabelResult {
    /** User provided a caption — use it directly. */
    data class Caption(
        val text: String,
    ) : AudioLabelResult

    /** No caption; label is derived from daylight period and optional location. */
    data class Contextual(
        val period: DaylightPeriod,
        val locationName: String?,
    ) : AudioLabelResult
}

/**
 * Resolves structured label data for an audio entry based on available context.
 *
 * Resolution priority:
 * 1. User-provided caption (if non-blank) -> [AudioLabelResult.Caption]
 * 2. Daylight period + optional location -> [AudioLabelResult.Contextual]
 *
 * The caller is responsible for formatting the result into a localized string
 * using the appropriate string resources.
 */
class AudioLabelResolver(
    private val daylightClassifier: DaylightClassifier = DaylightClassifier(),
) {
    /**
     * Resolves structured label data for an audio entry.
     *
     * @param createdAt When the audio was recorded.
     * @param caption User-provided caption, if any.
     * @param locationName Semantic place name (e.g., "Home", "Central Park"), if available.
     * @param latitude GPS latitude at recording time, if available.
     * @param longitude GPS longitude at recording time, if available.
     */
    fun resolve(
        createdAt: Instant,
        caption: String? = null,
        locationName: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
    ): AudioLabelResult {
        if (!caption.isNullOrBlank()) return AudioLabelResult.Caption(caption)

        val period = classifyPeriod(createdAt, latitude, longitude)
        return AudioLabelResult.Contextual(period, locationName?.takeIf { it.isNotBlank() })
    }

    /**
     * Classifies the daylight period for the given recording time and optional location.
     */
    fun classifyPeriod(
        createdAt: Instant,
        latitude: Double? = null,
        longitude: Double? = null,
    ): DaylightPeriod =
        if (latitude != null && longitude != null) {
            daylightClassifier.classify(createdAt, latitude, longitude)
        } else {
            daylightClassifier.classifyWithoutLocation(createdAt)
        }
}
