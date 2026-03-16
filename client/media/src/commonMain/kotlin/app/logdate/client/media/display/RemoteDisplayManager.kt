package app.logdate.client.media.display

import kotlinx.coroutines.flow.Flow

/**
 * Manages presentation of media on external displays (TVs, projectors, monitors).
 *
 * Platform implementations detect connected displays and render media content
 * full-screen on them while the phone remains a controller.
 */
interface RemoteDisplayManager {
    /**
     * Observes available external displays.
     *
     * Emits an updated list whenever displays are connected or disconnected.
     * Returns an empty list when no external displays are available.
     */
    fun observeExternalDisplays(): Flow<List<ExternalDisplay>>

    /**
     * Presents a media item on the specified external display.
     *
     * The media is shown full-screen with a black background.
     * Call [updatePresentation] to change the displayed media without dismissing.
     *
     * @param displayId The ID of the target display
     * @param mediaUri The content URI of the media to present
     * @param mimeType The MIME type of the media (e.g., "image/jpeg", "video/mp4")
     */
    fun present(
        displayId: Int,
        mediaUri: String,
        mimeType: String,
    )

    /**
     * Updates the currently presented media without dismissing the presentation.
     *
     * Used for navigating between items in presenter mode.
     * No-op if no presentation is active.
     *
     * @param mediaUri The content URI of the new media to show
     * @param mimeType The MIME type of the new media
     */
    fun updatePresentation(
        mediaUri: String,
        mimeType: String,
    )

    /**
     * Dismisses the current presentation and releases the external display.
     */
    fun dismiss()

    /**
     * Whether a presentation is currently active on any display.
     */
    fun observeIsPresenting(): Flow<Boolean>
}

/**
 * An external display capable of presenting media.
 */
data class ExternalDisplay(
    val id: Int,
    val name: String,
)
