package app.logdate.client.feature.widgets.di

import app.logdate.client.feature.widgets.OnThisDayWidgetRefreshWorker
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module

val widgetModule =
    module {
        workerOf(::OnThisDayWidgetRefreshWorker)
    }
