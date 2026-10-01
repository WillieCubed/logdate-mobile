package app.logdate.client.feature.widgets

import android.content.Context
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import io.github.aakira.napier.Napier

/** Publishes generated picker previews on Android 15+ while respecting launcher rate limits. */
suspend fun publishWidgetPreviews(context: Context) {
    if (Build.VERSION.SDK_INT < 35) return
    val appContext = context.applicationContext
    val preferences = appContext.getSharedPreferences("logdate_widget_previews", Context.MODE_PRIVATE)
    val now = System.currentTimeMillis()
    val appVersion = appContext.packageManager.getPackageInfo(appContext.packageName, 0).longVersionCode
    val manager = GlanceAppWidgetManager(appContext)
    for (receiver in listOf(
        OnThisDayWidgetReceiver::class,
        FixedMemoryWidgetReceiver::class,
        NewEntryWidgetReceiver::class,
    )) {
        val key = "${receiver.java.name}:$appVersion:safe-samples-v4"
        if (now - preferences.getLong(key, 0L) < 6 * 60 * 60 * 1000L) continue
        try {
            if (manager.setWidgetPreviews(receiver) == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS) {
                preferences.edit().putLong(key, now).apply()
            }
        } catch (error: Exception) {
            Napier.w("Unable to publish generated widget preview for $key", error)
        }
    }
}
