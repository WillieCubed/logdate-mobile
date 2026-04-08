package app.logdate.client.feature.widgets.di

import app.logdate.client.feature.widgets.AndroidMemoriesWidgetInstallController
import app.logdate.client.feature.widgets.OnThisDayWidgetRefreshWorker
import app.logdate.feature.core.settings.ui.MemoriesWidgetInstallController
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module

val widgetModule =
    module {
        single<MemoriesWidgetInstallController> {
            AndroidMemoriesWidgetInstallController(androidContext())
        }
        workerOf(::OnThisDayWidgetRefreshWorker)
    }
