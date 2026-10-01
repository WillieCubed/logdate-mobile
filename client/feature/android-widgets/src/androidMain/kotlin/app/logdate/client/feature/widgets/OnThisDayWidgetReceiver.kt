package app.logdate.client.feature.widgets

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import java.util.Calendar

/**
 * Broadcast receiver for the On This Day widget.
 *
 * Schedules the shared daily refresh when placed or restored.
 */
class OnThisDayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OnThisDayWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueWidgetRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelWidgetRefreshIfUnused(context)
    }

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        super.onDeleted(context, appWidgetIds)
        val settings = WidgetInstanceSettings(context)
        appWidgetIds.forEach(settings::remove)
    }

    override fun onRestored(
        context: Context,
        oldWidgetIds: IntArray,
        newWidgetIds: IntArray,
    ) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        val settings = WidgetInstanceSettings(context)
        settings.remap(oldWidgetIds, newWidgetIds)
        enqueueWidgetRefresh(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        enqueueWidgetRefresh(context)
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
