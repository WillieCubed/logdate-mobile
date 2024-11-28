package app.logdate.client.device.di

import app.logdate.client.device.InstanceIdProvider
import app.logdate.client.device.StubInstanceIdProvider
import org.koin.core.module.Module
import org.koin.dsl.module

actual val deviceInstanceModule: Module = module {
    // TODO: Implement desktop-compatible devices module

    single<InstanceIdProvider> { StubInstanceIdProvider }
}