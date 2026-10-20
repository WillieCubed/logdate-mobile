package app.logdate.client.media.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import app.logdate.client.media.R
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid

class QuietSaveNotifier(
    private val context: Context,
) {
    fun saved(draftId: Uuid) {
        try {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL, context.getString(R.string.quiet_save_title), NotificationManager.IMPORTANCE_LOW)
            channel.setSound(null, null)
            channel.enableVibration(false)
            manager.createNotificationChannel(channel)
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
            intent.data = Uri.parse("logdate://quiet-save/$draftId")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.putExtra("ambient_prompt_target", "draft")
            intent.putExtra("ambient_prompt_draft_id", draftId.toString())
            val open =
                PendingIntent.getActivity(
                    context,
                    draftId.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification_recording)
                    .setContentTitle(context.getString(R.string.quiet_save_title))
                    .setContentText(context.getString(R.string.quiet_save_saved))
                    .setContentIntent(open)
                    .addAction(0, context.getString(R.string.quiet_save_open_draft), open)
                    .setSilent(true)
                    .setAutoCancel(true)
                    .build()
            manager.notify(draftId.toString(), NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Napier.w("Quiet Save notification unavailable; the saved draft remains available", e)
        }
    }

    companion object {
        private const val CHANNEL = "quiet_save"
        private const val NOTIFICATION_ID = 2501
    }
}
