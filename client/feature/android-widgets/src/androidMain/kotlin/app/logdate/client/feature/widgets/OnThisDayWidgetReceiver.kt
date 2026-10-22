package app.logdate.client.feature.widgets

import androidx.glance.appwidget.GlanceAppWidget
import java.util.Calendar

class OnThisDayWidgetReceiver : DurableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OnThisDayWidget()
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
