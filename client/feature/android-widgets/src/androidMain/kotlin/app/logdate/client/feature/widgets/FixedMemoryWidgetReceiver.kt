package app.logdate.client.feature.widgets

import androidx.glance.appwidget.GlanceAppWidget

class FixedMemoryWidgetReceiver : DurableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FixedMemoryWidget()
}
