package app.logdate.client.notifications

/**
 * Stable Android notification groups exposed by the phone app.
 */
enum class LogDateNotificationGroupKey(
    val id: String,
) {
    CAPTURE_PLAYBACK("logdate_capture_playback"),
    BACKGROUND_SERVICES("logdate_background_services"),
    CONNECTED_DEVICES("logdate_connected_devices"),
    IMPORT_EXPORT("logdate_import_export"),
}

/**
 * Stable Android notification channels exposed by the phone app.
 *
 * Channel IDs are part of Android's persisted user settings surface and should
 * be treated as durable API.
 */
enum class LogDateNotificationChannelKey(
    val id: String,
    val groupKey: LogDateNotificationGroupKey,
    val notificationId: Int? = null,
    val legacyChannelIds: List<String> = emptyList(),
) {
    AUDIO_RECORDING(
        id = "audio_recording_channel",
        groupKey = LogDateNotificationGroupKey.CAPTURE_PLAYBACK,
        notificationId = 1001,
    ),
    AUDIO_PLAYBACK(
        id = "audio_playback_channel",
        groupKey = LogDateNotificationGroupKey.CAPTURE_PLAYBACK,
        notificationId = 2002,
    ),
    LOCATION_HISTORY(
        id = "logdate_location_active_tracking",
        groupKey = LogDateNotificationGroupKey.BACKGROUND_SERVICES,
        notificationId = 1905,
        legacyChannelIds = listOf("logdate_location_detail_tracking"),
    ),
    WATCH_SYNC(
        id = "wear_sync_channel",
        groupKey = LogDateNotificationGroupKey.CONNECTED_DEVICES,
    ),
    DATA_EXPORT(
        id = "export_progress_channel",
        groupKey = LogDateNotificationGroupKey.IMPORT_EXPORT,
        notificationId = 1003,
    ),
    DATA_RESTORE(
        id = "restore_channel",
        groupKey = LogDateNotificationGroupKey.IMPORT_EXPORT,
        notificationId = 1002,
    ),
}

object LogDateNotificationChannels {
    val phoneChannels: List<LogDateNotificationChannelKey> =
        listOf(
            LogDateNotificationChannelKey.AUDIO_RECORDING,
            LogDateNotificationChannelKey.AUDIO_PLAYBACK,
            LogDateNotificationChannelKey.LOCATION_HISTORY,
            LogDateNotificationChannelKey.WATCH_SYNC,
            LogDateNotificationChannelKey.DATA_EXPORT,
            LogDateNotificationChannelKey.DATA_RESTORE,
        )

    val legacyChannelIds: Set<String> =
        phoneChannels
            .flatMap { it.legacyChannelIds }
            .toSet()
}
