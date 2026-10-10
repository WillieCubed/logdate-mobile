package app.logdate.client.notifications

import android.app.Notification
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import androidx.core.app.NotificationCompat
import org.koin.mp.KoinPlatform

/** The app's [LiveUpdatesSwitch], or [LiveUpdatesSwitch.Off] where none is bound. */
fun currentLiveUpdatesSwitch(): LiveUpdatesSwitch = KoinPlatform.getKoinOrNull()?.getOrNull<LiveUpdatesSwitch>() ?: LiveUpdatesSwitch.Off

/**
 * Presents long-running data work as a Live Update: promoted to the status bar, with the percent in
 * the chip and a [NotificationCompat.ProgressStyle] bar split at [phaseStarts]. Does nothing when
 * [update] is not promoted, so the notification stays exactly as built.
 *
 * @param percent Progress from 0 to 100, or null while unknown.
 */
fun NotificationCompat.Builder.applyTransferLiveUpdate(
    update: TransferLiveUpdate,
    percent: Int?,
    phaseStarts: List<Int> = emptyList(),
): NotificationCompat.Builder {
    if (!update.promoted) return this
    setRequestPromotedOngoing(true)
    update.chipPercent?.let { setShortCriticalText("$it%") }
    val style =
        NotificationCompat
            .ProgressStyle()
            .setProgressSegments(progressSegmentLengths(phaseStarts).map { NotificationCompat.ProgressStyle.Segment(it) })
            .setProgressIndeterminate(percent == null)
    percent?.let { style.setProgress(it.coerceIn(0, 100)) }
    return setStyle(style)
}

/**
 * [text] marked with Android 17's semantic style for [style], so the system colors it as a success or
 * a failure. Earlier versions, and a null [style], get [text] unchanged.
 */
fun semanticStyledText(
    text: CharSequence,
    style: TransferSemanticStyle?,
): CharSequence {
    if (style == null || Build.VERSION.SDK_INT < 37) return text
    val annotation =
        Notification.createSemanticStyleAnnotation(
            when (style) {
                TransferSemanticStyle.Safe -> Notification.SEMANTIC_STYLE_SAFE
                TransferSemanticStyle.Danger -> Notification.SEMANTIC_STYLE_DANGER
            },
        )
    return SpannableString(text).apply { setSpan(annotation, 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
}
