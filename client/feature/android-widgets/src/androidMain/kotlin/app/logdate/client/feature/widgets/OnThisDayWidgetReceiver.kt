package app.logdate.client.feature.widgets

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Broadcast receiver for the On This Day widget.
 *
 * Manages the daily [WorkManager] refresh schedule: the periodic job is enqueued
 * when the first widget instance is placed and cancelled when the last is removed.
 */
class OnThisDayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OnThisDayWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        schedulePeriodicRefresh(context)
        enqueueImmediateRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManager
            .getInstance(context)
            .cancelUniqueWork(OnThisDayWidget.UNIQUE_WORK_NAME)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        enqueueImmediateRefresh(context)
    }

    private fun schedulePeriodicRefresh(context: Context) {
        val refreshRequest =
            PeriodicWorkRequestBuilder<OnThisDayWidgetRefreshWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            ).setInitialDelay(
                calculateDelayUntilNextRefreshWindow(),
                TimeUnit.MILLISECONDS,
            ).setConstraints(
                Constraints.Builder().build(),
            ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            OnThisDayWidget.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            refreshRequest,
        )
    }

    private fun enqueueImmediateRefresh(context: Context) {
        val request = OneTimeWorkRequestBuilder<OnThisDayWidgetRefreshWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val IMMEDIATE_WORK_NAME = "logdate:widget:on_this_day:immediate"
    }
}

/**
 * Calculates the delay in milliseconds until the next 5:00 AM local time.
 */
internal fun calculateDelayUntilNextRefreshWindow(): Long {
    val now = Calendar.getInstance()
    val target =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 5)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
    return target.timeInMillis - now.timeInMillis
}
