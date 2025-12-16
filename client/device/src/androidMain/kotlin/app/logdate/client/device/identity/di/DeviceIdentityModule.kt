package app.logdate.client.device.identity.di

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.device.identity.DefaultDeviceIdProvider
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.device.identity.DeviceRepository
import app.logdate.client.device.identity.data.InMemoryDeviceRepository
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Android implementation of device identity module.
 */
actual val deviceIdentityModule: Module = module {
    single<DeviceIdProvider>(named("modernDeviceIdProvider")) {
        DefaultDeviceIdProvider(get<KeyValueStorage>(named("deviceKeyValueStorage")))
    }

    // Repository for device information
    single<DeviceRepository> {
        InMemoryDeviceRepository()
    }
}