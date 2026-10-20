package app.logdate.client.ambient

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.logdate.client.domain.recommendation.AmbientPromptTriggerContext
import io.github.aakira.napier.Napier

class AmbientPromptBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            -> {
                Napier.i("Refreshing ambient prompt schedules after ${intent.action}")
                runAmbientPromptWork { scheduler ->
                    scheduler.refreshSchedules()
                    scheduler.enqueueImmediateEvaluation(AmbientPromptTriggerContext.PERIODIC)
                }
            }
        }
    }
}
