package app.logdate.client.device.di

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.device.AndroidAccountManager
import app.logdate.client.device.AndroidAppInfoProvider
import app.logdate.client.device.AppInfoProvider
import app.logdate.client.device.BuildConfigAppInfoProvider
import app.logdate.client.device.PlatformAccountManager
import app.logdate.client.device.identity.DefaultDeviceIdProvider
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.device.identity.di.deviceIdentityModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Android-specific device instance module that complements the deviceIdentityModule.
 */
actual val deviceInstanceModule: Module = module {
    // Include the device identity module (which already provides DeviceIdProvider)
    includes(deviceIdentityModule)
    
    // Create a SupervisorJob that will be cancelled when the app is destroyed
    single<Job> { SupervisorJob() }

    // Create a CoroutineScope that uses the application dispatcher and supervisor job
    single {
        CoroutineScope(get<Job>() + Dispatchers.Default)
    }
    
    // New device ID provider using KeyValueStorage
    single(named("modernDeviceIdProvider")) {
        DefaultDeviceIdProvider(get<KeyValueStorage>(named("deviceKeyValueStorage")))
    }
    
    // Provide the Android-specific app info provider
    single<AppInfoProvider> { 
        AndroidAppInfoProvider(get())
    }
    
    // Provide the Android-specific account manager
    single<PlatformAccountManager> {
        AndroidAccountManager(get())
    }
    
    // Provide the BuildConfig-based app info provider
    single<BuildConfigAppInfoProvider> {
        BuildConfigAppInfoProvider()
    }
}