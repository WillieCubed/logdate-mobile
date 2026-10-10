package app.logdate.client.media.audio

import android.content.Context
import androidx.core.app.NotificationCompat

/**
 * Adds to the recording notification before it is posted. The watch uses it to attach an Ongoing
 * Activity, so a recording shows on the watch face; the phone has none.
 */
fun interface RecordingNotificationDecorator {
    fun decorate(
        context: Context,
        builder: NotificationCompat.Builder,
        notificationId: Int,
        state: RecordingNotificationState,
    )

    /** Implemented by an app's `Application` to supply its decorator. */
    interface Provider {
        val recordingNotificationDecorator: RecordingNotificationDecorator
    }
}

/**
 * The recording a notification describes.
 *
 * @property isRecording False while paused.
 * @property startTimeMillis When the recording started, in wall-clock milliseconds.
 * @property recordedMillis How much has been recorded so far, without pauses.
 */
data class RecordingNotificationState(
    val isRecording: Boolean,
    val startTimeMillis: Long,
    val recordedMillis: Long,
)

/** The decorator [application] supplies, or null when it supplies none. */
fun recordingNotificationDecoratorOf(application: Any?): RecordingNotificationDecorator? =
    (application as? RecordingNotificationDecorator.Provider)?.recordingNotificationDecorator
