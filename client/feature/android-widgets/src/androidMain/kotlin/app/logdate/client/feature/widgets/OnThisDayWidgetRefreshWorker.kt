package app.logdate.client.feature.widgets

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.logdate.client.domain.recommendation.GetMemoryRecallUseCase
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.repository.journals.JournalNotesRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock

/**
 * Background worker that fetches memory recall data and updates all widget instances.
 *
 * Produces one of four states:
 * - [OnThisDayWidgetState.HasMemory] — a past entry was found
 * - [OnThisDayWidgetState.NoMemoryToday] — user has history but no match today
 * - [OnThisDayWidgetState.NewUser] — user hasn't journaled long enough
 * - [OnThisDayWidgetState.Loading] is never produced (it's only the DataStore default)
 */
class OnThisDayWidgetRefreshWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams),
    KoinComponent {
    private val getMemoryRecall: GetMemoryRecallUseCase by inject()
    private val memoriesSettingsRepository: MemoriesSettingsRepository by inject()
    private val notesRepository: JournalNotesRepository by inject()

    override suspend fun doWork(): Result =
        try {
            val settings = memoriesSettingsRepository.getSettings()

            val widgetState =
                if (!settings.contextualRecommendationsEnabled) {
                    OnThisDayWidgetState.NoMemoryToday
                } else {
                    val recallData = getMemoryRecall(aiEnabled = settings.aiRecallEnabled).firstOrNull()
                    if (recallData != null) {
                        val fallback = context.getString(R.string.widget_fallback_summary)
                        recallData.toWidgetState(fallbackSummary = fallback)
                    } else {
                        resolveEmptyState()
                    }
                }

            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(OnThisDayWidget::class.java).forEach { glanceId ->
                updateAppWidgetState(context, OnThisDayWidgetStateDefinition, glanceId) {
                    widgetState
                }
            }
            OnThisDayWidget().updateAll(context)
            Napier.d("On This Day widget refreshed: $widgetState")
            Result.success()
        } catch (e: Exception) {
            Napier.e("Failed to refresh On This Day widget", e)
            Result.retry()
        }

    /**
     * Determines whether the user is too new (no entries older than ~1 year)
     * or simply has no matching memory for today.
     */
    private suspend fun resolveEmptyState(): OnThisDayWidgetState {
        val today =
            Clock.System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
        val oneYearAgo = today.minus(1, DateTimeUnit.YEAR)
        val cutoff = oneYearAgo.atStartOfDayIn(TimeZone.currentSystemDefault())
        val hasOldEntries = notesRepository.hasNotesBefore(cutoff)
        return if (hasOldEntries) OnThisDayWidgetState.NoMemoryToday else OnThisDayWidgetState.NewUser
    }
}
