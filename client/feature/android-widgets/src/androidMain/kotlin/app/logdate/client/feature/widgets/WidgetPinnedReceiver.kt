package app.logdate.client.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.aakira.napier.Napier

/**
 * Starts widget configuration after a launcher confirms the pin request.
 */
class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val appWidgetId =
            intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Napier.w("Pinned widget callback did not include an appWidgetId")
            return
        }

        val configIntent =
            Intent(context, OnThisDayWidgetConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(configIntent)
    }
}
