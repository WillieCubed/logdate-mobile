package app.logdate.client.media.audio

import kotlin.uuid.Uuid

/**
 * Optional metadata for system playback surfaces (notification, lock screen, media carousel).
 *
 * @param title Display title for the audio item.
 * @param subtitle Secondary text (e.g., date or journal name).
 * @param noteId Note identifier used for deep-link routing back into the app.
 */
data class AudioPlaybackMetadata(
    val title: String? = null,
    val subtitle: String? = null,
    val noteId: Uuid? = null,
)
