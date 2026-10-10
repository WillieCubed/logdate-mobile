package app.logdate.wear.recording

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import app.logdate.client.media.audio.RecordingNotificationDecorator
import app.logdate.client.media.audio.RecordingNotificationState
import app.logdate.wear.R
import app.logdate.wear.presentation.MainActivity

/**
 * The stopwatch a recording's Ongoing Activity shows, in [SystemClock.elapsedRealtime]
 * milliseconds: it reads zero at [timeZeroMillis] and stands still from [pausedAtMillis], when set.
 */
data class OngoingStopwatch(
    val timeZeroMillis: Long,
    val pausedAtMillis: Long?,
)

internal fun ongoingStopwatch(
    state: RecordingNotificationState,
    nowElapsedRealtime: Long,
): OngoingStopwatch =
    OngoingStopwatch(
        timeZeroMillis = nowElapsedRealtime - state.recordedMillis,
        pausedAtMillis = if (state.isRecording) null else nowElapsedRealtime,
    )

/**
 * Shows a recording on the watch face: an Ongoing Activity with the record glyph and a stopwatch
 * that stands still while paused. Tapping it opens the app on Today, where Stop is. Only the new
 * watch app gets one, while [enabled] reads true.
 */
class OngoingRecordingDecorator(
    private val enabled: () -> Boolean,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) : RecordingNotificationDecorator {
    override fun decorate(
        context: Context,
        builder: NotificationCompat.Builder,
        notificationId: Int,
        state: RecordingNotificationState,
    ) {
        if (!enabled()) return
        val stopwatch = ongoingStopwatch(state, elapsedRealtime())
        val pausedAt = stopwatch.pausedAtMillis
        val part =
            if (pausedAt == null) {
                Status.StopwatchPart(stopwatch.timeZeroMillis)
            } else {
                Status.StopwatchPart(stopwatch.timeZeroMillis, pausedAt)
            }
        OngoingActivity
            .Builder(context, notificationId, builder)
            .setStaticIcon(R.drawable.ic_glyph_record)
            .setTouchIntent(openToday(context))
            .setTitle(context.getString(R.string.wear_ongoing_recording_title))
            .setStatus(Status.forPart(part))
            .build()
            .apply(context)
    }

    private fun openToday(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_OPEN_TODAY,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private companion object {
        const val REQUEST_OPEN_TODAY = 4_101
    }
}
