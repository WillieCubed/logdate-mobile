package app.logdate.client.location.di

import app.logdate.client.location.AndroidLocationProvider
import app.logdate.client.location.ClientLocationProvider
import org.koin.core.module.Module
import org.koin.dsl.module

actual val locationModule: Module = module {
    single<ClientLocationProvider> { AndroidLocationProvider() }
}