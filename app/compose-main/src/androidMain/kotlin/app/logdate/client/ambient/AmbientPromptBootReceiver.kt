package app.logdate.client.ambient

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.logdate.client.domain.recommendation.AmbientPromptTriggerContext
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext

class AmbientPromptBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val koin = GlobalContext.getOrNull()
        if (koin == null) {
            Napier.i("Deferring ambient prompt schedule refresh until foreground startup after ${intent.action}")
            return
        }

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            -> {
                Napier.i("Refreshing ambient prompt schedules after ${intent.action}")
                runBlocking { koin.get<AmbientPromptScheduler>().refreshSchedules() }
                koin.get<AmbientPromptScheduler>().enqueueImmediateEvaluation(AmbientPromptTriggerContext.PERIODIC)
            }
        }
    }
}
