package app.logdate.client.feature.widgets

import androidx.glance.appwidget.GlanceAppWidget

class NewEntryWidgetReceiver : DurableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NewEntryWidget()
}
