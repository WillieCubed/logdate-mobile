package app.logdate.di

import app.logdate.client.data.di.appDataModule
import app.logdate.client.device.di.deviceModule
import app.logdate.client.domain.di.accountDomainModule
import app.logdate.client.domain.di.domainModule
import app.logdate.client.domain.di.locationDomainModule
import app.logdate.client.domain.di.quotaDomainModule
import app.logdate.client.domain.events.EventInferenceLauncher
import app.logdate.client.domain.events.NoopEventInferenceLauncher
import app.logdate.client.location.di.locationModule
import app.logdate.client.media.di.audioModule
import app.logdate.client.networking.di.networkingModule
import app.logdate.client.sensor.di.sensorModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The main module for the application.
 *
 * This module is used to provide the dependencies for the application. Each source set will provide
 * a different implementation of this module.
 */
actual val appModule: Module =
    module {
        // Base modules first
        includes(defaultModules)
        includes(appDataModule)
        includes(networkingModule)
        includes(sensorModule)
        includes(deviceModule)

        // Domain modules in correct dependency order
        includes(accountDomainModule) // Account domain depends on data layers
        includes(quotaDomainModule) // Quota domain depends on sync layer
        includes(locationDomainModule) // Location domain depends on data layers
        includes(app.logdate.client.health.di.healthModule) // Common Health Connect implementation
        includes(app.logdate.client.health.di.jvmHealthModule) // Desktop-specific Health Connect implementation
        includes(domainModule) // Main domain module with no circular deps
        includes(locationModule)
        includes(audioModule)

        single<EventInferenceLauncher> { NoopEventInferenceLauncher }
    }
