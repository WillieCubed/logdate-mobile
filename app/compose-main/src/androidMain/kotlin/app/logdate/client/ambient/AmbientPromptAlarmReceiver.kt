package app.logdate.client.ambient

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.logdate.client.domain.recommendation.AmbientPromptTriggerContext
import io.github.aakira.napier.Napier

class AmbientPromptAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            AmbientPromptScheduler.ACTION_MORNING_PROMPT -> {
                Napier.d("Received morning ambient prompt alarm")
                runAmbientPromptWork { it.enqueueImmediateEvaluation(AmbientPromptTriggerContext.MORNING_SCHEDULE) }
            }

            AmbientPromptScheduler.ACTION_EVENING_PROMPT -> {
                Napier.d("Received evening ambient prompt alarm")
                runAmbientPromptWork { it.enqueueImmediateEvaluation(AmbientPromptTriggerContext.EVENING_SCHEDULE) }
            }
        }
    }
}
