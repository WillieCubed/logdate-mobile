package app.logdate.client.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

internal fun enqueueWidgetRefresh(context: Context) {
    val workManager = WorkManager.getInstance(context)
    workManager.enqueueUniqueWork(
        "logdate:widget:immediate",
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<OnThisDayWidgetRefreshWorker>().build(),
    )
    workManager.enqueueUniquePeriodicWork(
        OnThisDayWidget.UNIQUE_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<OnThisDayWidgetRefreshWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(calculateDelayUntilNextRefreshWindow(), TimeUnit.MILLISECONDS)
            .build(),
    )
}

internal fun cancelWidgetRefreshIfUnused(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val hasWidgets =
        listOf(
            OnThisDayWidgetReceiver::class.java,
            FixedMemoryWidgetReceiver::class.java,
            NewEntryWidgetReceiver::class.java,
        ).any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    if (!hasWidgets) WorkManager.getInstance(context).cancelUniqueWork(OnThisDayWidget.UNIQUE_WORK_NAME)
}
