package app.logdate.feature.core.di

import app.logdate.client.domain.watch.DefaultWatchSettingsRepository
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.WatchPreferencesRepository
import app.logdate.client.domain.watch.WatchSettingsRepository
import app.logdate.feature.core.settings.ui.watch.WatchConnectionManager
import app.logdate.feature.core.settings.ui.watch.WatchPeerSnapshotSource
import app.logdate.feature.core.settings.ui.watch.WatchSettingsViewModel
import app.logdate.feature.core.settings.ui.watch.v2.WatchCompanionPreferencesViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Dependency injection module for the watch settings feature.
 *
 * The [WatchConnectionManager] and [WatchPeerSnapshotSource] bindings must be provided by the
 * platform module, since they depend on platform-specific Wearable APIs.
 */
val watchSettingsModule: Module =
    module {
        single<WatchSettingsRepository> { DefaultWatchSettingsRepository(get()) }
        viewModel { WatchCompanionPreferencesViewModel(get()) }
        viewModel {
            WatchSettingsViewModel(
                connectionManager = get(),
                settingsRepository = get(),
                peerSnapshots = get(),
                windowDays = get<WatchPreferencesRepository>().observe(SharedPreferenceKey.WindowDays),
            )
        }
    }
