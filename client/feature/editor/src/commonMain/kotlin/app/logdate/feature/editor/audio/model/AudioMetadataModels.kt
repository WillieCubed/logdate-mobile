package app.logdate.feature.editor.audio.model

import kotlinx.serialization.Serializable

@Serializable
data class AudioSegment(
    val timestampMs: Long,
    val type: SegmentType,
)

@Serializable
enum class SegmentType {
    SPEECH_ONSET,
    SIGNIFICANT_PAUSE,
    VOLUME_PEAK,
}

@Serializable
data class AudioPalette(
    val waveformGradientStart: Long,
    val waveformGradientEnd: Long,
    val playedFillColor: Long,
    val accentColor: Long,
    val immersiveBackground: Long,
    /** Legible text and icon color for content drawn on [immersiveBackground]. */
    val contentColor: Long = 0xFFF5F5F5,
)

enum class LocationType {
    NATURE,
    URBAN,
    HOME,
    TRANSIT,
    UNKNOWN,
}
