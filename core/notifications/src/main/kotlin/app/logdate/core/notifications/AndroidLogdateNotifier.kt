package app.logdate.core.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.datetime.Instant
import javax.inject.Inject

/**
 * A notification manager for Android devices.
 */
class AndroidLogdateNotifier @Inject constructor(
    private val context: Context,
) : Notifier {

    private val notificationManager = NotificationManagerCompat.from(context)

    private companion object {
        private val CHANNELS = mapOf(
            "journaling_reminders" to ChannelInfo(
                id = "journaling_reminders",
                name = "Journaling Reminders",
                description = "Notifications to help you remember to journal.",
            )
        )

        private val NOTIFICATION_INTENTS = mapOf(
            "journaling_reminders" to Intent("app.logdate.action.JOURNALING_REMINDER")
        )
    }

    override fun scheduleNotification(
        notification: SystemNotification,
        channel: String,
        time: Instant,
        approximate: Boolean,
    ) {
        if (channel !in CHANNELS) {
            throw IllegalArgumentException("Unknown channel: $channel")
        }
        ensureChannelExists(CHANNELS[channel]!!)
        val androidNotification = notification.toAndroidNotification(
            channelId = channel,
            timestamp = time.toEpochMilliseconds(),
            icon = R.drawable.ic_notification_default,
        )
        // TODO: Figure out how to handle notification IDs for special notifications
        notificationManager.notify(notification.label.hashCode(), androidNotification)
    }

    override fun sendNotification(notification: SystemNotification, channel: String) {
        if (channel !in CHANNELS) {
            throw IllegalArgumentException("Unknown channel: $channel")
        }
        ensureChannelExists(CHANNELS[channel]!!)
        val androidNotification = notification.toAndroidNotification(
            channelId = channel,
            timestamp = notification.time,
            icon = R.drawable.ic_notification_default,
        )
        // TODO: Figure out how to handle notification IDs for special notifications
        notificationManager.notify(notification.label.hashCode(), androidNotification)
    }

    private fun ensureChannelExists(channelInfo: ChannelInfo) {
        val notificationChannel = NotificationChannelCompat.Builder(
            channelInfo.id,
            NotificationManagerCompat.IMPORTANCE_DEFAULT
        )
            .setName(channelInfo.name)
            .setDescription(channelInfo.description)
            .build()
        notificationManager.createNotificationChannel(notificationChannel)
    }

    /**
     * Converts a [SystemNotification] to an Android [Notification].
     *
     * @param channelId The ID of the system notification channel to use.
     * @param timestamp A time to be associated with the notification when displayed. This does not
     * need to be the time the notification is shown.
     * @param icon The icon to display in the notification. If not provided, the default icon will be used.
     * @param deleteIntent (Optional) The intent to execute when the notification is dismissed.
     */
    private fun SystemNotification.toAndroidNotification(
        channelId: String,
        @DrawableRes icon: Int = R.drawable.ic_notification_default,
        timestamp: Long? = null,
        deleteIntent: PendingIntent? = null,
    ) = Notification.Builder(context, channelId)
        .setContentTitle(this.label)
        .setContentText(this.bodyContent)
        .setSmallIcon(icon)
        .apply {
            if (timestamp != null) {
                setWhen(timestamp)
            }
        }
        .apply {
            if (actions.isNotEmpty()) {
                if (channelId !in NOTIFICATION_INTENTS) {
                    throw IllegalArgumentException("Unknown channel ID: $channelId")
                }
                val actionIntents = actions.map { action ->
                    PendingIntent.getBroadcast(
                        context,
                        action.label.hashCode(),
                        NOTIFICATION_INTENTS[channelId]
                            ?.setAction(action.id)!!,
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                }
                for (action in actions) {
                    addAction(
                        Notification.Action.Builder(
                            Icon.createWithResource(context, icon),
                            action.label,
                            actionIntents[actions.indexOf(action)]
                        ).build()
                    )
                }
            }
        }
        .apply {
            if (deleteIntent != null) {
                setDeleteIntent(deleteIntent)
            }
        }
        .build()
}

private data class ChannelInfo(
    val id: String,
    val name: String,
    val description: String,
)