package app.logdate.client.networking.di

import app.logdate.client.networking.httpClient
import org.koin.core.module.Module
import org.koin.dsl.module

actual val networkingModule: Module = module {
    single { httpClient}
}