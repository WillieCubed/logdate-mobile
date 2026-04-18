package app.logdate.wear.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.aakira.napier.Napier
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Receives AlarmManager broadcasts to post journal prompts.
 *
 * The alarm fires twice daily (morning and evening). This receiver determines
 * which prompt type to show based on the current hour and posts the notification
 * if the cooldown allows it.
 */
class WearPromptReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        val now = Clock.System.now()
        val localTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val promptType = PromptType.forHour(localTime.hour)

        if (promptType == PromptType.NONE) {
            Napier.d("Prompt receiver fired outside prompt hours (hour=${localTime.hour})")
            return
        }

        val dayEpoch = now.toEpochMilliseconds() / (24 * 60 * 60 * 1000)
        val tracker = PromptCooldownTracker()
        if (!tracker.canSend(promptType, dayEpoch)) {
            Napier.d("Skipping $promptType prompt: already sent today")
            return
        }

        Napier.d("Posting $promptType journal prompt")
        val builder = WearPromptNotificationBuilder(context)
        builder.post(promptType)
        tracker.markSent(promptType, dayEpoch)
    }
}
