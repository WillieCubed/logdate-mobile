package app.logdate.wear.v2.mark

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.wear.LogDateWearApplication
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

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
    suspend fun mark(
        capturedAt: Instant? = null,
        timeZoneId: String? = null,
        markId: Uuid? = null,
    ): Boolean =
        try {
            if (featureFlags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)) {
                markMoment.mark(capturedAt, timeZoneId, markId)
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
class MarkMomentReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_MARK_MOMENT) return
        val request = ColdMarkWorker.request(Clock.System.now(), TimeZone.currentSystemDefault().id, Uuid.random())
        val pending = goAsync()
        (context.applicationContext as LogDateWearApplication).startupWorkScope.launch {
            try {
                WorkManager
                    .getInstance(context)
                    .enqueue(request)
                    .result
                    .get(3, TimeUnit.SECONDS)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Napier.e("The mark could not be queued", error)
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
