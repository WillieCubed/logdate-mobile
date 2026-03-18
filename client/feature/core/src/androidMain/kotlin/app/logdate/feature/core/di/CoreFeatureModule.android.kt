package app.logdate.feature.core.di

import android.app.Activity
import app.logdate.client.domain.di.accountModule
import app.logdate.client.domain.di.domainModule
import app.logdate.client.location.di.locationSettingsModule
import app.logdate.feature.core.AndroidBiometricGatekeeper
import app.logdate.feature.core.AppViewModel
import app.logdate.feature.core.BiometricGatekeeper
import app.logdate.feature.core.account.CloudAccountOnboardingViewModel
import app.logdate.feature.core.export.AndroidExportLauncher
import app.logdate.feature.core.export.ExportLauncher
import app.logdate.feature.core.export.ExportWorker
import app.logdate.feature.core.export.UserDataExportViewModel
import app.logdate.feature.core.main.HomeViewModel
import app.logdate.feature.core.profile.ui.ProfileViewModel
import app.logdate.feature.core.restore.AndroidRestoreLauncher
import app.logdate.feature.core.restore.RestoreLauncher
import app.logdate.feature.core.restore.UserDataRestoreViewModel
import app.logdate.feature.core.settings.ui.AccountSettingsViewModel
import app.logdate.feature.core.settings.ui.AdvancedSettingsViewModel
import app.logdate.feature.core.settings.ui.DangerZoneSettingsViewModel
import app.logdate.feature.core.settings.ui.DataSettingsViewModel
import app.logdate.feature.core.settings.ui.LocationSettingsViewModel
import app.logdate.feature.core.settings.ui.MemoriesSettingsViewModel
import app.logdate.feature.core.settings.ui.PrivacySettingsViewModel
import app.logdate.feature.core.settings.ui.ServerConfigurationCoordinator
import app.logdate.feature.core.settings.updates.AppUpdateController
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Feature module exposing core app screens and functionality.
 */
actual val coreFeatureModule: Module =
    module {
        // Include our domain modules - note that accountModule is included separately to avoid circular deps
        includes(domainModule)
        includes(accountModule)
        includes(devicesModule)
        includes(locationSettingsModule)
        includes(watchSettingsModule)

        // TODO: Refactor to separate auth module
        single { AndroidBiometricGatekeeper() }
        single<BiometricGatekeeper> { get<AndroidBiometricGatekeeper>() }

        // Export functionality with activity provider for file picker
        // We use lazy provider for current activity that will be set by the MainActivity
        single { ActivityProvider() }

        // Single instance exposed as both concrete type and interface
        single { AndroidExportLauncher(androidContext()) }
        single<ExportLauncher> { get<AndroidExportLauncher>() }
        workerOf(::ExportWorker)
        single { AndroidRestoreLauncher(androidContext()) }
        single<RestoreLauncher> { get<AndroidRestoreLauncher>() }

        factory { ServerConfigurationCoordinator(get(), get(), get()) }

        viewModel { AppViewModel(get(), get(), get(), get(), get()) }
        viewModel {
            AccountSettingsViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            PrivacySettingsViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            DataSettingsViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel { UserDataExportViewModel(get(), get()) }
        viewModel { UserDataRestoreViewModel(get()) }
        viewModel { AdvancedSettingsViewModel(get(), get<AppUpdateController>()) }
        viewModel {
            DangerZoneSettingsViewModel(
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            HomeViewModel(
                getStreamingTimelineUseCase = get(),
                getTimelinePageUseCase = get(),
                notesRepository = get(),
                getHomeRecommendation = get(),
            )
        }
        viewModel { CloudAccountOnboardingViewModel(get(), get(), get(), get(), get(), get()) }
        viewModel { LocationSettingsViewModel(get()) }
        viewModel { MemoriesSettingsViewModel(get()) }
        viewModel { ProfileViewModel(get(), get(), get(), get()) }
    }

// TODO: Fix this obvious code smell

/**
 * Helper class to hold a reference to the current activity.
 * This is needed for launching the file picker.
 */
class ActivityProvider {
    var currentActivity: Activity? = null
}
