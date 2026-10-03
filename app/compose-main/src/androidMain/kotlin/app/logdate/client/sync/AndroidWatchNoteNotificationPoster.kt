package app.logdate.client.sync

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import app.logdate.client.EditorActivity
import app.logdate.client.notifications.LogDateNotificationChannelKey
import kotlin.uuid.Uuid
import app.logdate.client.notifications.R as NotificationResources

/**
 * Posts a watch note's notification on the Watch sync channel. It is local-only: the note came
 * from the watch, so the watch is not alerted about its own note. Tapping it opens the note in
 * the editor.
 */
class AndroidWatchNoteNotificationPoster(
    private val context: Context,
) : WatchNoteNotificationPoster {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    override fun post(
        noteId: Uuid,
        content: WatchNoteArrivalContent,
    ) {
        val openNote =
            PendingIntent.getActivity(
                context,
                noteId.hashCode(),
                EditorActivity.createIntent(context = context, entryId = noteId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, LogDateNotificationChannelKey.WATCH_SYNC.id)
                .setSmallIcon(NotificationResources.drawable.ic_notification_watch)
                .setContentTitle(content.title)
                .setContentText(content.body)
                .setAutoCancel(true)
                .setLocalOnly(true)
                .setContentIntent(openNote)
                .build()
        notificationManager.notify(notificationId(noteId), notification)
    }

    companion object {
        private const val NOTIFICATION_ID_BASE = 3000

        /** One notification per note, so a second note doesn't replace the first. */
        fun notificationId(noteId: Uuid): Int = NOTIFICATION_ID_BASE + noteId.hashCode()
    }
}
