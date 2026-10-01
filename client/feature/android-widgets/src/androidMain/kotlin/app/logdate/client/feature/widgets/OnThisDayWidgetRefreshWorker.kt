package app.logdate.client.feature.widgets

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.logdate.client.domain.recommendation.GetMemoryRecallUseCase
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.media.MediaManager
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.transcription.TranscriptionRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Refreshes each widget provider independently so one failing data source does
 * not prevent the other widget types from updating.
 */
class OnThisDayWidgetRefreshWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams),
    KoinComponent {
    private val getMemoryRecall: GetMemoryRecallUseCase by inject()
    private val memoriesSettingsRepository: MemoriesSettingsRepository by inject()
    private val notesRepository: JournalNotesRepository by inject()
    private val mediaManager: MediaManager by inject()
    private val transcriptionRepository: TranscriptionRepository by inject()

    override suspend fun doWork(): Result =
        try {
            val manager = GlanceAppWidgetManager(context)
            val instanceSettings = WidgetInstanceSettings(context)
            val failed =
                listOf(
                    refreshRecallWidgets(manager, instanceSettings),
                    refreshFixedMemoryWidgets(manager, instanceSettings),
                    refreshNewEntryWidgets(manager, instanceSettings),
                ).any { it }
            publishWidgetPreviews(context)
            if (failed) Result.retry() else Result.success()
        } catch (e: Exception) {
            Napier.e("Failed to refresh On This Day widget", e)
            Result.retry()
        }

    private suspend fun refreshRecallWidgets(
        manager: GlanceAppWidgetManager,
        instanceSettings: WidgetInstanceSettings,
    ): Boolean =
        try {
            val settings = memoriesSettingsRepository.getSettings()
            var failed = false
            manager.getGlanceIds(OnThisDayWidget::class.java).forEach { glanceId ->
                try {
                    val appWidgetId = manager.getAppWidgetId(glanceId)
                    instanceSettings.ensureRecallDefaults(appWidgetId, settings.recallMode, settings.widgetContentTypes)
                    val recallData =
                        getMemoryRecall(
                            aiEnabled = settings.aiRecallEnabled,
                            recallMode = instanceSettings.recallMode(appWidgetId, settings.recallMode),
                            contentTypes = instanceSettings.contentTypes(appWidgetId, settings.widgetContentTypes),
                            rotationDay =
                                Clock.System
                                    .now()
                                    .toLocalDateTime(TimeZone.currentSystemDefault())
                                    .date,
                        ).firstOrNull()
                    val widgetState =
                        recallData?.toWidgetStateWithAudio(
                            notesRepository,
                            transcriptionRepository,
                            instanceSettings.contentTypes(appWidgetId, settings.widgetContentTypes),
                        ) ?: resolveEmptyWidgetState(notesRepository)
                    updateAppWidgetState(context, OnThisDayWidgetStateDefinition, glanceId) { widgetState }
                } catch (error: Exception) {
                    failed = true
                    Napier.w("Unable to refresh a Memories widget", error)
                }
            }
            OnThisDayWidget().updateAll(context)
            failed
        } catch (error: Exception) {
            Napier.w("Unable to refresh Memories widgets", error)
            true
        }

    private suspend fun refreshFixedMemoryWidgets(
        manager: GlanceAppWidgetManager,
        instanceSettings: WidgetInstanceSettings,
    ): Boolean =
        try {
            var failed = false
            manager.getGlanceIds(FixedMemoryWidget::class.java).forEach { glanceId ->
                try {
                    val noteId = instanceSettings.chosenNoteId(manager.getAppWidgetId(glanceId))
                    val state =
                        if (noteId == null) {
                            OnThisDayWidgetState.ChooseMemory
                        } else {
                            runCatching { notesRepository.getNoteById(Uuid.parse(noteId)) }
                                .getOrNull()
                                ?.let { note ->
                                    val transcript =
                                        if (note is JournalNote.Audio) {
                                            runCatching { transcriptionRepository.getTranscription(note.uid)?.displayText() }.getOrNull()
                                        } else {
                                            null
                                        }
                                    note.toFixedWidgetState(transcript)
                                } ?: OnThisDayWidgetState.MissingMemory
                        }
                    updateAppWidgetState(context, OnThisDayWidgetStateDefinition, glanceId) { state }
                } catch (error: Exception) {
                    failed = true
                    Napier.w("Unable to refresh a pinned memory widget", error)
                }
            }
            FixedMemoryWidget().updateAll(context)
            failed
        } catch (error: Exception) {
            Napier.w("Unable to refresh pinned memory widgets", error)
            true
        }

    private suspend fun refreshNewEntryWidgets(
        manager: GlanceAppWidgetManager,
        instanceSettings: WidgetInstanceSettings,
    ): Boolean =
        try {
            val photoIds = manager.getGlanceIds(NewEntryWidget::class.java)
            var failed = false
            if (photoIds.isNotEmpty()) {
                val usePhoto = photoIds.any { instanceSettings.usePhotoPrompt(manager.getAppWidgetId(it)) }
                val photoState =
                    if (usePhoto && hasWidgetImageAccess(context)) {
                        runCatching {
                            val today =
                                Clock.System
                                    .now()
                                    .toLocalDateTime(TimeZone.currentSystemDefault())
                                    .date
                            choosePhotoPrompt(mediaManager.getRecentImages(60).first(), today)
                                ?.let { OnThisDayWidgetState.PhotoPrompt(it.uri) }
                        }.getOrNull() ?: OnThisDayWidgetState.PhotoUnavailable
                    } else {
                        OnThisDayWidgetState.PhotoUnavailable
                    }
                photoIds.forEach { glanceId ->
                    try {
                        val state =
                            if (instanceSettings.usePhotoPrompt(manager.getAppWidgetId(glanceId))) {
                                photoState
                            } else {
                                OnThisDayWidgetState.NewEntryReady
                            }
                        updateAppWidgetState(context, OnThisDayWidgetStateDefinition, glanceId) { state }
                    } catch (error: Exception) {
                        failed = true
                        Napier.w("Unable to refresh a New Entry widget", error)
                    }
                }
                NewEntryWidget().updateAll(context)
            }
            failed
        } catch (error: Exception) {
            Napier.w("Unable to refresh New Entry widgets", error)
            true
        }
}
