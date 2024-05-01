package app.logdate.core.notifications

import android.net.Uri
import kotlinx.datetime.Instant

/**
 * An interface for scheduling and managing system notifications.
 */
interface Notifier {
    /**
     * Schedules a notification to be shown at a specific time.
     *
     * Implementations may not support exact scheduling and may show the notification within a few
     * minutes of the specified time.
     *
     * @param notification The notification to schedule.
     * @param channel The ID of the system notification channel to use.
     * @param time The time at which the notification should be shown.
     * @param approximate Whether the notification must be shown at the exact time or can be shown
     * within a few minutes of the specified time depending on system resources.
     */
    fun scheduleNotification(
        notification: SystemNotification,
        channel: String = "default",
        time: Instant,
        approximate: Boolean = false,
    )

    /**
     * Sends a notification immediately.
     *
     * @param notification The notification to send.
     * @param channel The ID of the system notification channel to use.
     */
    fun sendNotification(notification: SystemNotification, channel: String = "default")
}

/**
 * A notification to be shown to the user by the operating system.
 */
data class SystemNotification(
    /**
     * The title of the notification.
     */
    val label: String,
    /**
     * The content of the notification.
     */
    val bodyContent: String,
    /**
     * A relevant time for this notification in milliseconds since the Unix epoch.
     *
     * This time need not be the time the notification is shown to the user. It can be used as an
     * additional form of metadata for the notification. For example, it could be the time at which
     * the event being notified about occurred, especially if there is a noticeable delay between
     * the event and the notification.
     */
    val time: Long? = null,
    /**
     * The URI of the image to display in the notification.
     */
    val imageUri: Uri? = null,
    /**
     * Whether the notification is sensitive.
     *
     * A sensitive notification should, at minimum, not have [bodyContent] displayed on the lock
     * screen or limit the amount of information shown in the notification unless the device is
     * unlocked.
     */
    val isSensitive: Boolean = false,
    /**
     * The actions to display in the notification.
     *
     * [Notifier] implementations are responsible for handling the actions when the user interacts with them.
     */
    val actions: List<NotificationAction> = emptyList(),
)

data class NotificationAction(
    val label: String,
    val id: String,
)