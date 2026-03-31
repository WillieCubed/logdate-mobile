package app.logdate.di

import android.app.Application
import app.logdate.client.data.di.appDataModule
import app.logdate.client.device.di.deviceModule
import app.logdate.client.domain.di.accountDomainModule
import app.logdate.client.domain.di.domainModule
import app.logdate.client.domain.di.locationDomainModule
import app.logdate.client.domain.di.quotaDomainModule
import app.logdate.client.feature.widgets.di.widgetModule
import app.logdate.client.health.di.androidHealthModule
import app.logdate.client.health.di.healthModule
import app.logdate.client.intelligence.di.intelligenceModule
import app.logdate.client.location.di.locationModule
import app.logdate.client.media.di.audioModule
import app.logdate.client.networking.di.networkingModule
import app.logdate.client.sensor.di.sensorModule
import app.logdate.client.sync.AndroidPhoneAudioStreamOpener
import app.logdate.client.sync.DefaultPhoneWearSyncBridge
import app.logdate.client.sync.GooglePhoneWearTransport
import app.logdate.client.sync.PhoneAudioStreamOpener
import app.logdate.client.sync.PhoneWearSyncBridge
import app.logdate.client.sync.PhoneWearTransport
import app.logdate.client.sync.WearSyncNotificationHelper
import app.logdate.client.sync.datalayer.NoteDataMapper
import app.logdate.client.updates.PlayInAppUpdateController
import app.logdate.client.watch.AndroidWatchConnectionManager
import app.logdate.dynamic.DynamicFeatureLoader
import app.logdate.dynamic.PlayDynamicFeatureLoader
import app.logdate.feature.core.settings.ui.watch.WatchConnectionManager
import app.logdate.feature.core.settings.updates.AppUpdateController
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

actual val appModule: Module =
    module {
        // Base modules first
        includes(appDataModule)
        includes(sensorModule)
        includes(networkingModule)
        includes(deviceModule)

        // Domain modules in correct dependency order
        includes(accountDomainModule) // Account domain depends on data layers
        includes(quotaDomainModule) // Quota domain depends on sync layer
        includes(locationDomainModule) // Location domain depends on data layers
        includes(healthModule) // Common Health Connect implementation
        includes(androidHealthModule) // Android-specific Health Connect implementation
        includes(domainModule) // Main domain module with no circular deps

        // Feature modules
        includes(defaultModules)
        includes(intelligenceModule)
        includes(locationModule)
        includes(audioModule)
        includes(windowingModule)
        includes(widgetModule)

        single { NoteDataMapper() }
        single { WearSyncNotificationHelper(androidContext()) }
        single<PhoneWearTransport> { GooglePhoneWearTransport(androidContext()) }
        single<PhoneAudioStreamOpener> { AndroidPhoneAudioStreamOpener(androidContext()) }
        single<PhoneWearSyncBridge> {
            DefaultPhoneWearSyncBridge(
                notesRepository = get(),
                noteDataMapper = get(),
                transport = get(),
                audioStreamOpener = get(),
            )
        }

        single { PlayInAppUpdateController(androidContext(), get()) }
        single<AppUpdateController> { get<PlayInAppUpdateController>() }

        single<DynamicFeatureLoader> { PlayDynamicFeatureLoader(androidContext()) }

        // Watch connection manager for Wear OS settings
        single<WatchConnectionManager> { AndroidWatchConnectionManager(androidContext()) }
    }

/**
 * Initializes global Koin context with the application module.
 */
internal fun Application.initializeKoin() {
    startKoin {
        androidLogger()
        androidContext(this@initializeKoin)
        workManagerFactory()
        modules(appModule)
    }
}
