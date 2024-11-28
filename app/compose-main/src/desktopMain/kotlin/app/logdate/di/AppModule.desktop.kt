package app.logdate.di

import app.logdate.client.data.di.dataModule
import app.logdate.client.networking.di.networkingModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The main module for the application.
 *
 * This module is used to provide the dependencies for the application. Each source set will provide
 * a different implementation of this module.
 */
actual val appModule: Module = module {
    includes(defaultModules)
    includes(dataModule)
    includes(networkingModule)
}