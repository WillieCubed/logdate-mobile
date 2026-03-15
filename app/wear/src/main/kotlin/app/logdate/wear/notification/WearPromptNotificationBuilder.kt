package app.logdate.wear.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.logdate.wear.R

/**
 * Builds and posts contextual journal prompt notifications on the watch.
 *
 * Morning prompts encourage the user to start their day with a mood or voice note.
 * Evening prompts invite reflection on the day.
 */
class WearPromptNotificationBuilder(
    private val context: Context,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    fun post(type: PromptType) {
        if (type == PromptType.NONE) return

        val content = PromptContent.forType(type)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        val pendingIntent = PendingIntent.getActivity(
            context,
            type.ordinal,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        notificationManager.notify(
            NOTIFICATION_ID_BASE + type.ordinal,
            notification,
        )
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.wear_prompt_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.wear_prompt_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "wear_journal_prompts"
        private const val NOTIFICATION_ID_BASE = 4000
    }
}
