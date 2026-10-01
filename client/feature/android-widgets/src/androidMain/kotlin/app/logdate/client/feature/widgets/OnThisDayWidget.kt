package app.logdate.client.feature.widgets

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Glance widget that displays "on this day" memory recall data.
 *
 * State is pre-populated by [OnThisDayWidgetRefreshWorker] and persisted via
 * [OnThisDayWidgetStateDefinition]. This widget only reads the persisted state
 * and renders — no use case invocations happen during composition.
 */
class OnThisDayWidget(
    private val previewState: OnThisDayWidgetState? = null,
) : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<OnThisDayWidgetState> =
        OnThisDayWidgetStateDefinition
    override val previewSizeMode = SizeMode.Responsive(setOf(DpSize(250.dp, 180.dp), DpSize(180.dp, 180.dp)))
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            val state = currentState<OnThisDayWidgetState>()
            OnThisDayWidgetContent(state)
        }
    }

    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val state = previewState ?: sampleRotatingMemory(context)
        provideContent {
            OnThisDayWidgetContent(state)
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "on_this_day_widget_refresh"
    }
}

internal suspend fun resolveEmptyWidgetState(notesRepository: JournalNotesRepository): OnThisDayWidgetState {
    val today =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
    val cutoff = today.minus(1, DateTimeUnit.YEAR).atStartOfDayIn(TimeZone.currentSystemDefault())
    return if (notesRepository.hasNotesBefore(cutoff)) OnThisDayWidgetState.NoMemoryToday else OnThisDayWidgetState.NewUser
}
