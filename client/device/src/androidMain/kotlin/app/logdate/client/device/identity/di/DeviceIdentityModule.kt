package app.logdate.client.device.identity.di

import android.os.Build
import android.provider.Settings
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.device.AppInfoProvider
import app.logdate.client.device.identity.DefaultDeviceIdProvider
import app.logdate.client.device.identity.DefaultDeviceManager
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.device.identity.DeviceRepository
import app.logdate.client.device.identity.data.InMemoryDeviceRepository
import app.logdate.client.device.models.DevicePlatform
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Android implementation of device identity module.
 */
actual val deviceIdentityModule: Module = module {
    single<DeviceIdProvider> {
        DefaultDeviceIdProvider(get<KeyValueStorage>(named("deviceKeyValueStorage")))
    }

    // Repository for device information
    single<DeviceRepository> {
        InMemoryDeviceRepository()
    }

    single<DefaultDeviceManager> {
        val context = androidContext()
        val appInfoProvider = get<AppInfoProvider>()

        val deviceName = try {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
                ?: "${Build.MANUFACTURER} ${Build.MODEL}"
        } catch (e: Exception) {
            "${Build.MANUFACTURER} ${Build.MODEL}"
        }

        DefaultDeviceManager(
            deviceIdProvider = get(),
            deviceRepository = get(),
            initialDeviceName = deviceName,
            platform = DevicePlatform.ANDROID,
            appVersion = appInfoProvider.getAppInfo().versionName,
        )
    }
}