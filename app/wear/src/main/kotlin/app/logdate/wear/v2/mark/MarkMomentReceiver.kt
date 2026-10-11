package app.logdate.wear.v2.mark

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.ui.platform.LogDateHaptics
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Marks the moment for [MarkMomentReceiver]. A background app can read neither the place nor the
 * heart rate, so the mark has only its time; the phone adds the place later. A haptic confirms it.
 */
class BackgroundMarker(
    private val featureFlags: FeatureFlagStore,
    private val markMoment: MarkMomentUseCase,
    private val haptics: LogDateHaptics,
) {
    /** Returns whether a mark was saved. Nothing is marked while the new watch app is off. */
    suspend fun mark(): Boolean =
        try {
            if (featureFlags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)) {
                markMoment.mark()
                haptics.momentMarked()
                true
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Couldn't mark the moment from outside the app", e)
            haptics.actionRejected()
            false
        }
}

/**
 * Marks the moment from outside the app: a complication, a tile or a notification action fires
 * [pendingIntent]. It isn't exported, so only this app's own pending intents reach it.
 */
class MarkMomentReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val marker: BackgroundMarker by inject()
    private val scope: CoroutineScope by inject()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_MARK_MOMENT) return
        val pending = goAsync()
        scope.launch {
            try {
                marker.mark()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK_MOMENT = "app.logdate.wear.action.MARK_MOMENT"
        private const val REQUEST_MARK = 4_102

        /** What a complication, a tile or a notification action fires to mark the moment. */
        fun pendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_MARK,
                Intent(context, MarkMomentReceiver::class.java).setAction(ACTION_MARK_MOMENT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
