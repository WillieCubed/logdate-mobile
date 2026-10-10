package app.logdate.wear.v2.permissions

import android.Manifest

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
    ;

    /** The name the system knows this permission by. */
    fun manifestName(): String =
        when (this) {
            MICROPHONE -> Manifest.permission.RECORD_AUDIO
            NOTIFICATIONS -> Manifest.permission.POST_NOTIFICATIONS
        }
}
