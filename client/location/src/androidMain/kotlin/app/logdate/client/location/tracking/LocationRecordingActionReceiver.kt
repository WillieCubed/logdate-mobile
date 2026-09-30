package app.logdate.client.location.tracking

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.notifications.LogDateNotificationChannelKey
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

/** Explicit notification actions change the same durable switch used in settings. */
class LocationRecordingActionReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val settings: LocationTrackingSettingsRepository by inject()
    private val manager: LocationTrackingManager by inject()
    private val dispatcher: CoroutineDispatcher by inject(named("io-dispatcher"))

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_PAUSE && intent.action != ACTION_RESUME) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + dispatcher).launch {
            try {
                val controls = LocationRecordingControls(settings)
                if (intent.action == ACTION_PAUSE) {
                    controls.pause()
                    manager.stopTracking()
                    showPausedNotification(context)
                } else {
                    controls.resume()
                    manager.startTracking()
                    NotificationManagerCompat.from(context).cancel(PAUSED_NOTIFICATION_ID)
                }
            } catch (error: Exception) {
                Napier.w("Could not change location recording", error)
            } finally {
                pending.finish()
            }
        }
    }

    @Suppress("MissingPermission")
    private fun showPausedNotification(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        launchIntent.putExtra(EXTRA_NAV_SOURCE, NAV_SOURCE_LOCATION_HISTORY)
        val open =
            PendingIntent.getActivity(
                context,
                1909,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, LogDateNotificationChannelKey.LOCATION_HISTORY.id)
                .setContentTitle("Location recording paused")
                .setContentText("Resume when you want to record your day again.")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(open)
                .addAction(android.R.drawable.ic_media_play, "Resume", actionIntent(context, ACTION_RESUME))
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setSilent(true)
                .setAutoCancel(true)
                .build()
        NotificationManagerCompat.from(context).notify(PAUSED_NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_PAUSE = "app.logdate.location.action.PAUSE_RECORDING"
        const val ACTION_RESUME = "app.logdate.location.action.RESUME_RECORDING"
        private const val PAUSED_NOTIFICATION_ID = 1907

        fun clearPausedNotification(context: Context) {
            NotificationManagerCompat.from(context).cancel(PAUSED_NOTIFICATION_ID)
        }

        fun actionIntent(
            context: Context,
            action: String,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                1908,
                Intent(context, LocationRecordingActionReceiver::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
