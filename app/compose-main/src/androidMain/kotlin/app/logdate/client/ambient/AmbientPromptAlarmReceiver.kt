package app.logdate.client.ambient

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.logdate.client.domain.recommendation.AmbientPromptTriggerContext
import io.github.aakira.napier.Napier
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AmbientPromptAlarmReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val scheduler: AmbientPromptScheduler by inject()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            AmbientPromptScheduler.ACTION_MORNING_PROMPT -> {
                Napier.d("Received morning ambient prompt alarm")
                scheduler.enqueueImmediateEvaluation(AmbientPromptTriggerContext.MORNING_SCHEDULE)
            }

            AmbientPromptScheduler.ACTION_EVENING_PROMPT -> {
                Napier.d("Received evening ambient prompt alarm")
                scheduler.enqueueImmediateEvaluation(AmbientPromptTriggerContext.EVENING_SCHEDULE)
            }
        }
    }
}
