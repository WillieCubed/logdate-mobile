package app.logdate.feature.core.di

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.domain.watch.DefaultWatchSettingsRepository
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.WatchPreferencesRepository
import app.logdate.client.domain.watch.WatchSettingsRepository
import app.logdate.feature.core.settings.ui.watch.WatchConnectionManager
import app.logdate.feature.core.settings.ui.watch.WatchPeerSnapshotSource
import app.logdate.feature.core.settings.ui.watch.WatchSettingsViewModel
import app.logdate.feature.core.settings.ui.watch.v2.WatchCompanionPreferencesViewModel
import app.logdate.feature.core.settings.ui.watch.v2.WatchDiagnosticsViewModel
import app.logdate.feature.core.settings.ui.watch.v2.WatchReadyOfferViewModel
import app.logdate.feature.core.settings.ui.watch.v2.WatchTodayPreviewViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
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
        viewModel { WatchDiagnosticsViewModel(get(), get()) }
        viewModel { WatchTodayPreviewViewModel(get(), get()) }
        viewModel {
            app.logdate.feature.core.settings.ui.watch.v2
                .WatchRemovalViewModel(get())
        }
        viewModel { WatchReadyOfferViewModel(get<KeyValueStorage>(named("deviceKeyValueStorage")), get(), get(), get()) }
        viewModel {
            WatchSettingsViewModel(
                connectionManager = get(),
                settingsRepository = get(),
                peerSnapshots = get(),
                windowDays = get<WatchPreferencesRepository>().observe(SharedPreferenceKey.WindowDays),
            )
        }
    }
