package app.logdate.di

import android.app.Application
import app.logdate.client.ambient.AmbientPromptNotificationCoordinator
import app.logdate.client.ambient.AmbientPromptScheduler
import app.logdate.client.ambient.AmbientPromptSchedulingObserver
import app.logdate.client.ambient.AmbientPromptWorker
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
import app.logdate.client.rewind.RewindGenerationWorker
import app.logdate.client.rewind.RewindNotificationCoordinator
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
import app.logdate.client.watch.AndroidCompanionDeviceClient
import app.logdate.client.watch.AndroidWatchConnectionManager
import app.logdate.client.watch.CompanionDeviceClient
import app.logdate.client.watch.DefaultWatchAssociationRequestFactory
import app.logdate.client.watch.DefaultWatchCompanionAssociationManager
import app.logdate.client.watch.WatchAssociationRequestFactory
import app.logdate.client.watch.WatchCompanionAssociationManager
import app.logdate.dynamic.DynamicFeatureLoader
import app.logdate.dynamic.PlayDynamicFeatureLoader
import app.logdate.feature.core.settings.ui.watch.WatchConnectionManager
import app.logdate.feature.core.settings.updates.AppUpdateController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

private val watchConnectionIoDispatcherQualifier = named("watch-connection-io-dispatcher")

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

        single { AmbientPromptNotificationCoordinator(androidContext()) }
        single { AmbientPromptScheduler(androidContext(), get()) }
        single { AmbientPromptSchedulingObserver(get(), get(), get()) }
        workerOf(::AmbientPromptWorker)

        single { RewindNotificationCoordinator(androidContext()) }
        workerOf(::RewindGenerationWorker)

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

        single<CoroutineDispatcher>(watchConnectionIoDispatcherQualifier) { Dispatchers.IO }
        single<CompanionDeviceClient> { AndroidCompanionDeviceClient(androidContext()) }
        single<WatchAssociationRequestFactory> { DefaultWatchAssociationRequestFactory() }
        single<WatchCompanionAssociationManager> {
            DefaultWatchCompanionAssociationManager(
                companionDeviceClient = get(),
                applicationScope = get(),
                associationRequestFactory = get(),
            )
        }

        // Watch connection manager for Wear OS settings
        single<WatchConnectionManager> {
            AndroidWatchConnectionManager(
                context = androidContext(),
                associationManager = get(),
                ioDispatcher = get(qualifier = watchConnectionIoDispatcherQualifier),
            )
        }
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
