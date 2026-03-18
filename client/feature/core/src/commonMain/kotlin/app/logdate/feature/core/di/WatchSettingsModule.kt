package app.logdate.feature.core.di

import app.logdate.client.domain.watch.DefaultWatchSettingsRepository
import app.logdate.client.domain.watch.WatchSettingsRepository
import app.logdate.feature.core.settings.ui.watch.WatchConnectionManager
import app.logdate.feature.core.settings.ui.watch.WatchSettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Dependency injection module for the watch settings feature.
 *
 * The [WatchConnectionManager] binding must be provided by the platform module
 * since it depends on platform-specific Wearable APIs.
 */
val watchSettingsModule: Module =
    module {
        single<WatchSettingsRepository> { DefaultWatchSettingsRepository(get()) }
        viewModel { WatchSettingsViewModel(get<WatchConnectionManager>(), get<WatchSettingsRepository>()) }
    }
