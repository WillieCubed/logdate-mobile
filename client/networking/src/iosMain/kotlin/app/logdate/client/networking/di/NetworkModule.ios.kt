package app.logdate.client.networking.di

import app.logdate.client.networking.DefaultServerHealthChecker
import app.logdate.client.networking.IosNetworkAvailabilityMonitor
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.networking.ServerHealthChecker
import app.logdate.client.networking.httpClient
import org.koin.core.module.Module
import org.koin.dsl.module

actual val networkingModule: Module =
    module {
        single { httpClient }
        single<ServerHealthChecker> { DefaultServerHealthChecker(get()) }
        single<NetworkAvailabilityMonitor> { IosNetworkAvailabilityMonitor() }
    }
