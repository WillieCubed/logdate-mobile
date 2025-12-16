package app.logdate.client.domain.di

import app.logdate.client.domain.timeline.AndroidHealthConnectRepository
import app.logdate.client.domain.timeline.DefaultHealthConnectRepository
import app.logdate.client.domain.timeline.HealthConnectRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Stub implementation of AndroidHealthConnectModule for testing.
 * 
 * This provides a default implementation that doesn't depend on Android Context.
 */
val testAndroidHealthConnectModule: Module = module {
    single<HealthConnectRepository> { DefaultHealthConnectRepository() }
}