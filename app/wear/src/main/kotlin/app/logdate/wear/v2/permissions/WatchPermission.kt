package app.logdate.wear.v2.permissions

import android.Manifest
import android.os.Build

/**
 * A runtime permission the new watch app asks for when a feature first needs it.
 *
 * @property storedName How [PermissionPromptStore] records that it was asked for. Never changes.
 */
enum class WatchPermission(
    val storedName: String,
) {
    /** Recording. Asked each time recording starts without it. */
    MICROPHONE("microphone"),

    /** A recording's Ongoing Activity and its Stop control. Asked once, on the first locked recording. */
    NOTIFICATIONS("notifications"),

    /** A mark's heart rate. Asked once, on the first mark. */
    HEART_RATE("heart_rate"),
    ;

    /** The name the system knows this permission by. */
    fun manifestName(): String =
        when (this) {
            MICROPHONE -> Manifest.permission.RECORD_AUDIO
            NOTIFICATIONS -> Manifest.permission.POST_NOTIFICATIONS
            HEART_RATE -> heartRatePermission(Build.VERSION.SDK_INT)
        }
}

/** Wear OS 6 reads the heart rate under its own health permission; earlier versions under body sensors. */
internal fun heartRatePermission(sdkInt: Int): String =
    if (sdkInt >= Build.VERSION_CODES.BAKLAVA) READ_HEART_RATE else Manifest.permission.BODY_SENSORS

private const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
