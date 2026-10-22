package app.logdate.client.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

internal fun enqueueWidgetRefresh(
    context: Context,
    eventId: String = Uuid.random().toString(),
): Boolean = retainWidgetLifecycleEvent(context, widgetRefreshEvent(eventId))

internal suspend fun persistWidgetRefresh(
    context: Context,
    event: WidgetLifecycleEvent,
) {
    if (event.receiver == CaptureWidgetReceiver::class.java.simpleName) return
    val workManager = WorkManager.getInstance(context)
    val manager = AppWidgetManager.getInstance(context)
    val hasWidgets =
        listOf(OnThisDayWidgetReceiver::class.java, FixedMemoryWidgetReceiver::class.java, NewEntryWidgetReceiver::class.java)
            .any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    if (!hasWidgets && event.action in setOf(AppWidgetManager.ACTION_APPWIDGET_DELETED, AppWidgetManager.ACTION_APPWIDGET_DISABLED)) {
        workManager.cancelUniqueWork("logdate:widget:immediate").await()
        workManager.cancelUniqueWork(OnThisDayWidget.UNIQUE_WORK_NAME).await()
        return
    }
    workManager
        .enqueueUniqueWork(
            "logdate:widget:immediate",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<OnThisDayWidgetRefreshWorker>().build(),
        ).await()
    workManager
        .enqueueUniquePeriodicWork(
            OnThisDayWidget.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<OnThisDayWidgetRefreshWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(calculateDelayUntilNextRefreshWindow(), TimeUnit.MILLISECONDS)
                .build(),
        ).await()
}
