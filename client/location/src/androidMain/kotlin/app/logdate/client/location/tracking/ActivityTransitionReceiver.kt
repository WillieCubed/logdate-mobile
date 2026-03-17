package app.logdate.client.location.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransitionResult
import io.github.aakira.napier.Napier

/**
 * Receives activity transition events from Google's Activity Recognition API
 * and forwards them to the running [ActivityAwareLocationService].
 */
class ActivityTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (!ActivityTransitionResult.hasResult(intent)) return

        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val service =
            ActivityAwareLocationService.instance ?: run {
                Napier.w("ActivityTransitionReceiver fired but no ActivityAwareLocationService is running")
                return
            }

        for (event in result.transitionEvents) {
            service.onActivityTransition(event.activityType, event.transitionType)
        }
    }
}
