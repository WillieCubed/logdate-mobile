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

class NewEntryWidget(
    private val previewState: OnThisDayWidgetState? = null,
    private val previewShape: PhotoWidgetShape = PhotoWidgetShape.SYSTEM,
) : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<OnThisDayWidgetState> = OnThisDayWidgetStateDefinition
    override val previewSizeMode = SizeMode.Responsive(setOf(DpSize(250.dp, 180.dp)))
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val shape = WidgetInstanceSettings(context).photoShape(appWidgetId)
        provideContent { OnThisDayWidgetContent(currentState<OnThisDayWidgetState>(), photoShape = shape) }
    }

    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val state =
            previewState ?: OnThisDayWidgetState.PhotoPrompt(
                "android.resource://${context.packageName}/drawable/widget_photo_sample",
            )
        provideContent { OnThisDayWidgetContent(state, photoShape = previewShape) }
    }
}
