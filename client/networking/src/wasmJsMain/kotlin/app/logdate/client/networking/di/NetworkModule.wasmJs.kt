package app.logdate.client.networking.di

import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.networking.DefaultDataUsagePolicy
import app.logdate.client.networking.DefaultServerDiscoveryClient
import app.logdate.client.networking.DefaultServerHealthChecker
import app.logdate.client.networking.ServerDiscoveryClient
import app.logdate.client.networking.ServerHealthChecker
import app.logdate.client.networking.httpClient
import org.koin.core.module.Module
import org.koin.dsl.module

actual val networkingModule: Module =
    module {
        single { httpClient }
        single<DataUsagePolicy> { DefaultDataUsagePolicy(get()) }
        single<ServerHealthChecker> { DefaultServerHealthChecker(get()) }
        single<ServerDiscoveryClient> { DefaultServerDiscoveryClient(get()) }
    }
