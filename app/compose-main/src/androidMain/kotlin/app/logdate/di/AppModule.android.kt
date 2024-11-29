package app.logdate.di

import android.app.Application
import app.logdate.client.data.di.dataModule
import app.logdate.client.networking.di.networkingModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

actual val appModule: Module = module {
    includes(defaultModules)
    includes(dataModule)
    includes(networkingModule)
}

/**
 * Initializes global Koin context with the application module.
 */
internal fun Application.initializeKoin() {
    startKoin {
        androidLogger()
        androidContext(this@initializeKoin)
        modules(appModule)
    }
}
