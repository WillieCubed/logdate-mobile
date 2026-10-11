package app.logdate.wear.reminders

import android.content.Context
import androidx.wear.phone.interactions.notifications.BridgingConfig
import androidx.wear.phone.interactions.notifications.BridgingManager
import io.github.aakira.napier.Napier

/** Phone notifications still appear on the watch, except a reminder the phone posts for itself. */
fun reminderBridgingConfig(context: Context): BridgingConfig =
    BridgingConfig
        .Builder(context, true)
        .addExcludedTag(WatchNudgeNotifier.TAG)
        .build()

/** Applies [reminderBridgingConfig]. A failure is logged and leaves bridging as it was. */
fun applyReminderBridging(context: Context) {
    try {
        BridgingManager.fromContext(context).setConfig(reminderBridgingConfig(context))
    } catch (e: Exception) {
        Napier.e("Couldn't stop the phone's reminders from also showing on this watch", e)
    }
}
