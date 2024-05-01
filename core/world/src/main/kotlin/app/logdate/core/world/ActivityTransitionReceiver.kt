package app.logdate.core.world

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransitionResult
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * A processor for activity transitions.
 *
 * This notifies the app's [ActivityLocationProvider] of activity transitions.
 */
@AndroidEntryPoint
class ActivityTransitionReceiver @Inject constructor(
    private val activityProvider: ActivityLocationProvider,
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) {
            return
        }
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        for (event in result.transitionEvents) {
            // chronological sequence of events....
        }
    }

}
