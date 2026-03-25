package app.logdate.client.feature.widgets

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition

/**
 * Glance widget that displays "on this day" memory recall data.
 *
 * State is pre-populated by [OnThisDayWidgetRefreshWorker] and persisted via
 * [OnThisDayWidgetStateDefinition]. This widget only reads the persisted state
 * and renders — no use case invocations happen during composition.
 */
class OnThisDayWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<OnThisDayWidgetState> =
        OnThisDayWidgetStateDefinition

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            val state = currentState<OnThisDayWidgetState>()
            OnThisDayWidgetContent(state)
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "on_this_day_widget_refresh"
    }
}
