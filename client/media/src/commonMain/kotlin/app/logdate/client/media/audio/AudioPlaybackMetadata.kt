package app.logdate.client.media.audio

import kotlin.uuid.Uuid

/**
 * Optional metadata for system playback surfaces (notification, lock screen, media carousel).
 *
 * @param title Display title for the audio item.
 * @param subtitle Secondary text (e.g., date or journal name).
 * @param noteId Note identifier used for deep-link routing back into the app.
 * @param journalNames Names of journals this note belongs to, shown as the artist field.
 * @param accentColor Palette accent color as a packed ARGB Long, for tinting playback surfaces.
 * @param immersiveBackground Palette background color (packed ARGB), used for artwork generation.
 * @param gradientStart Palette gradient start color (packed ARGB), used for artwork generation.
 * @param gradientEnd Palette gradient end color (packed ARGB), used for artwork generation.
 */
data class AudioPlaybackMetadata(
    val title: String? = null,
    val subtitle: String? = null,
    val noteId: Uuid? = null,
    val journalNames: List<String> = emptyList(),
    val accentColor: Long? = null,
    val immersiveBackground: Long? = null,
    val gradientStart: Long? = null,
    val gradientEnd: Long? = null,
)
