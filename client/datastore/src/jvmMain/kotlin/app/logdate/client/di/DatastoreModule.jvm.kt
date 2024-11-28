package app.logdate.client.di

import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.datastore.createDataStore
import org.koin.core.module.Module
import org.koin.dsl.module

actual val datastoreModule: Module = module {
    single { createDataStore() }
    factory { LogdatePreferencesDataSource(get()) }
}