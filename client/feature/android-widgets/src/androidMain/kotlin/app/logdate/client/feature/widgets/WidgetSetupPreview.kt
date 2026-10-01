@file:Suppress("ktlint:standard:function-naming")

package app.logdate.client.feature.widgets

import android.appwidget.AppWidgetProviderInfo
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.composeForPreview

/** The setup screen renders the same RemoteViews as the launcher generated preview. */
@Composable
internal fun WidgetSetupPreview(
    widget: GlanceAppWidget,
    previewKey: Any?,
) {
    val context = LocalContext.current
    var preview by remember { mutableStateOf<android.widget.RemoteViews?>(null) }
    LaunchedEffect(previewKey) {
        preview =
            runCatching {
                widget.composeForPreview(
                    context,
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                    AppWidgetProviderInfo().apply {
                        minWidth = 250
                        minHeight = 180
                    },
                )
            }.getOrNull()
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { FrameLayout(it) },
            modifier = Modifier.width(250.dp).height(180.dp),
            update = { frame ->
                frame.removeAllViews()
                preview?.let { views ->
                    runCatching { frame.addView(views.apply(context, frame)) }
                }
            },
        )
    }
}
