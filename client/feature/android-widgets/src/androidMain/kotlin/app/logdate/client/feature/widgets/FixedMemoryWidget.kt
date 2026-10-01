package app.logdate.client.feature.widgets

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import app.logdate.client.repository.journals.JournalNote
import kotlinx.datetime.LocalDate

class FixedMemoryWidget(
    private val previewState: OnThisDayWidgetState? = null,
) : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<OnThisDayWidgetState> = OnThisDayWidgetStateDefinition
    override val previewSizeMode = SizeMode.Responsive(setOf(DpSize(250.dp, 180.dp), DpSize(180.dp, 180.dp)))
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent { OnThisDayWidgetContent(currentState<OnThisDayWidgetState>(), appWidgetId) }
    }

    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val state = previewState ?: sampleFixedMemory(context)
        provideContent { OnThisDayWidgetContent(state) }
    }
}

internal fun sampleFixedMemory(context: Context): OnThisDayWidgetState.FixedMemory =
    OnThisDayWidgetState.FixedMemory(
        noteId = "00000000-0000-4000-8000-000000000000",
        dateFormatted = "September 30, 2025",
        summary = "Milo after the rain",
        thumbnailUri = "android.resource://${context.packageName}/drawable/widget_photo_sample",
    )

internal fun JournalNote.toFixedWidgetState(transcript: String? = null): OnThisDayWidgetState.FixedMemory {
    val chosen = toChosenEntryContent(transcript)
    return OnThisDayWidgetState.FixedMemory(
        noteId = chosen.noteId,
        dateFormatted = LocalDate.parse(chosen.dateIso).formatForDisplay(),
        summary = chosen.summary,
        thumbnailUri = chosen.imageUri,
        audioUri = (this as? JournalNote.Audio)?.mediaRef,
    )
}
