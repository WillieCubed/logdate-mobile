package app.logdate.client.ambient

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.logdate.client.domain.recommendation.AmbientPromptTriggerContext
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AmbientPromptBootReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val scheduler: AmbientPromptScheduler by inject()

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
                runBlocking {
                    scheduler.refreshSchedules()
                }
                scheduler.enqueueImmediateEvaluation(AmbientPromptTriggerContext.PERIODIC)
            }
        }
    }
}
