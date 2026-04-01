package app.logdate.client.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

data class AndroidLogDateNotificationGroupSpec(
    val key: LogDateNotificationGroupKey,
    val nameResId: Int,
)

data class AndroidLogDateNotificationChannelSpec(
    val key: LogDateNotificationChannelKey,
    val nameResId: Int,
    val descriptionResId: Int,
    val importance: Int,
    val showBadge: Boolean = true,
    val vibrationEnabled: Boolean = false,
    val soundEnabled: Boolean = false,
    val lockscreenVisibility: Int = Notification.VISIBILITY_PRIVATE,
)

object AndroidLogDateNotificationCatalog {
    val groups: List<AndroidLogDateNotificationGroupSpec> =
        listOf(
            AndroidLogDateNotificationGroupSpec(
                key = LogDateNotificationGroupKey.CAPTURE_PLAYBACK,
                nameResId = R.string.notification_group_capture_playback,
            ),
            AndroidLogDateNotificationGroupSpec(
                key = LogDateNotificationGroupKey.BACKGROUND_SERVICES,
                nameResId = R.string.notification_group_background_services,
            ),
            AndroidLogDateNotificationGroupSpec(
                key = LogDateNotificationGroupKey.CONNECTED_DEVICES,
                nameResId = R.string.notification_group_connected_devices,
            ),
            AndroidLogDateNotificationGroupSpec(
                key = LogDateNotificationGroupKey.IMPORT_EXPORT,
                nameResId = R.string.notification_group_import_export,
            ),
        )

    val phoneChannels: List<AndroidLogDateNotificationChannelSpec> =
        listOf(
            AndroidLogDateNotificationChannelSpec(
                key = LogDateNotificationChannelKey.AUDIO_RECORDING,
                nameResId = R.string.notification_channel_audio_recording_name,
                descriptionResId = R.string.notification_channel_audio_recording_description,
                importance = NotificationManager.IMPORTANCE_LOW,
                showBadge = false,
                vibrationEnabled = false,
                soundEnabled = false,
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC,
            ),
            AndroidLogDateNotificationChannelSpec(
                key = LogDateNotificationChannelKey.AUDIO_PLAYBACK,
                nameResId = R.string.notification_channel_audio_playback_name,
                descriptionResId = R.string.notification_channel_audio_playback_description,
                importance = NotificationManager.IMPORTANCE_LOW,
                showBadge = false,
                vibrationEnabled = false,
                soundEnabled = false,
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC,
            ),
            AndroidLogDateNotificationChannelSpec(
                key = LogDateNotificationChannelKey.LOCATION_HISTORY,
                nameResId = R.string.notification_channel_location_history_name,
                descriptionResId = R.string.notification_channel_location_history_description,
                importance = NotificationManager.IMPORTANCE_MIN,
                showBadge = false,
                vibrationEnabled = false,
                soundEnabled = false,
                lockscreenVisibility = Notification.VISIBILITY_SECRET,
            ),
            AndroidLogDateNotificationChannelSpec(
                key = LogDateNotificationChannelKey.WATCH_SYNC,
                nameResId = R.string.notification_channel_watch_sync_name,
                descriptionResId = R.string.notification_channel_watch_sync_description,
                importance = NotificationManager.IMPORTANCE_DEFAULT,
                showBadge = true,
                vibrationEnabled = false,
                soundEnabled = false,
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE,
            ),
            AndroidLogDateNotificationChannelSpec(
                key = LogDateNotificationChannelKey.DATA_EXPORT,
                nameResId = R.string.notification_channel_data_export_name,
                descriptionResId = R.string.notification_channel_data_export_description,
                importance = NotificationManager.IMPORTANCE_LOW,
                showBadge = false,
                vibrationEnabled = false,
                soundEnabled = false,
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE,
            ),
            AndroidLogDateNotificationChannelSpec(
                key = LogDateNotificationChannelKey.DATA_RESTORE,
                nameResId = R.string.notification_channel_data_restore_name,
                descriptionResId = R.string.notification_channel_data_restore_description,
                importance = NotificationManager.IMPORTANCE_LOW,
                showBadge = false,
                vibrationEnabled = false,
                soundEnabled = false,
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE,
            ),
        )

    fun channel(key: LogDateNotificationChannelKey): AndroidLogDateNotificationChannelSpec = phoneChannels.first { it.key == key }
}

class LogDateNotificationRegistrar(
    private val context: Context,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun registerAllPhoneChannels() {
        notificationManager.createNotificationChannelGroups(
            AndroidLogDateNotificationCatalog.groups.map { group ->
                NotificationChannelGroup(group.key.id, context.getString(group.nameResId))
            },
        )

        deleteLegacyChannels()

        notificationManager.createNotificationChannels(
            AndroidLogDateNotificationCatalog.phoneChannels.map(::buildChannel),
        )
    }

    fun registerChannel(key: LogDateNotificationChannelKey) {
        val group = AndroidLogDateNotificationCatalog.groups.first { it.key == key.groupKey }
        notificationManager.createNotificationChannelGroup(
            NotificationChannelGroup(group.key.id, context.getString(group.nameResId)),
        )
        notificationManager.createNotificationChannel(
            buildChannel(AndroidLogDateNotificationCatalog.channel(key)),
        )
    }

    private fun deleteLegacyChannels() {
        LogDateNotificationChannels.legacyChannelIds.forEach(notificationManager::deleteNotificationChannel)
    }

    private fun buildChannel(spec: AndroidLogDateNotificationChannelSpec): NotificationChannel =
        NotificationChannel(
            spec.key.id,
            context.getString(spec.nameResId),
            spec.importance,
        ).apply {
            description = context.getString(spec.descriptionResId)
            group = spec.key.groupKey.id
            setShowBadge(spec.showBadge)
            enableVibration(spec.vibrationEnabled)
            lockscreenVisibility = spec.lockscreenVisibility
            if (!spec.soundEnabled) {
                setSound(null, null)
            }
        }
}

fun Context.openAppNotificationSettings() {
    val intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    startActivity(intent)
}

fun Context.openChannelNotificationSettings(channelKey: LogDateNotificationChannelKey) {
    val intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, channelKey.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    startActivity(intent)
}
