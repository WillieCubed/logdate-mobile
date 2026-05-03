package app.logdate.client.notifications

import kotlin.time.Instant

/**
 * Cross-platform interface for scheduling local notifications. Platform implementations
 * translate each request to the appropriate system primitive (UNUserNotificationCenter on
 * iOS, NotificationManager + AlarmManager / WorkManager on Android).
 *
 * Channel selection follows [LogDateNotificationChannelKey] so iOS and Android share the
 * same logical buckets (capture nudges, draft rescue, memory recall, rewind ready).
 */
interface NotificationScheduler {
    /**
     * Schedules a notification to fire at [deliverAt]. Returns an opaque request identifier the
     * caller can later pass to [cancel] to remove a still-pending notification.
     *
     * @param channelKey Logical channel for grouping and per-channel user preferences.
     * @param title Bold-line text shown in the system tray.
     * @param body Smaller text shown beneath the title.
     * @param deliverAt Absolute instant the system should fire the notification. Times in the
     * past resolve to immediate delivery.
     * @param deepLink Optional `logdate://` URL the system should open when the user taps the
     * notification. Empty / null skips deep-link routing.
     */
    suspend fun schedule(
        channelKey: LogDateNotificationChannelKey,
        title: String,
        body: String,
        deliverAt: Instant,
        deepLink: String? = null,
    ): String

    /**
     * Cancels a previously-scheduled notification by its [requestId].
     */
    suspend fun cancel(requestId: String)

    /**
     * Cancels every pending notification on the given channel.
     */
    suspend fun cancelChannel(channelKey: LogDateNotificationChannelKey)
}
