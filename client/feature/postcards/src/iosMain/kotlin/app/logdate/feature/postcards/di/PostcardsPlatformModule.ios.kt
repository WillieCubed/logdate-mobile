package app.logdate.feature.postcards.di

import app.logdate.feature.postcards.NoOpExportEngine
import app.logdate.feature.postcards.ui.ExportEngine
import org.koin.core.module.Module
import org.koin.dsl.module

actual val postcardsPlatformModule: Module =
    module {
        single<ExportEngine> { NoOpExportEngine() }
    }
