package app.logdate.client.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import app.logdate.client.EditorActivity
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteType

/**
 * Posts notifications when notes are received from a paired Wear OS watch.
 *
 * When the watch syncs a new note to the phone, this helper creates a notification
 * that opens the editor with the note pre-loaded, allowing the user to expand on
 * their quick capture from the wrist.
 */
class WearSyncNotificationHelper(
    private val context: Context,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    /**
     * Posts a notification for a note that was just received from the paired watch.
     *
     * @param note The journal note received from the watch.
     */
    fun notifyNoteReceived(note: JournalNote) {
        val (title, body) = contentForNote(note)

        val editorIntent =
            EditorActivity.createIntent(
                context = context,
                entryId = note.uid,
            )
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                note.uid.hashCode(),
                editorIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

        notificationManager.notify(
            NOTIFICATION_ID_BASE + note.uid.hashCode(),
            notification,
        )
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Watch sync",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifications when notes sync from your watch"
                setSound(null, null)
            }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "wear_sync_channel"
        private const val NOTIFICATION_ID_BASE = 3000

        /**
         * Returns (title, body) for the notification based on note type.
         */
        fun contentForNote(note: JournalNote): Pair<String, String> =
            when (note.type) {
                NoteType.AUDIO -> "Voice note from your watch" to "Tap to expand in editor"
                NoteType.TEXT -> "Note from your watch" to (note as JournalNote.Text).content.take(80)
                NoteType.IMAGE -> "Photo from your watch" to "Tap to view"
                NoteType.VIDEO -> "Video from your watch" to "Tap to view"
                NoteType.LOCATION -> "Location from your watch" to "Tap to view"
            }
    }
}
