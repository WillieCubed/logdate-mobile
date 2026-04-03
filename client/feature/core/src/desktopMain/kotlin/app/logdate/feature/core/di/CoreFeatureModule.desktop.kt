package app.logdate.feature.core.di

import app.logdate.client.domain.di.accountModule
import app.logdate.client.domain.di.domainModule
import app.logdate.client.location.di.locationSettingsModule
import app.logdate.feature.core.AppViewModel
import app.logdate.feature.core.BiometricGatekeeper
import app.logdate.feature.core.StubBiometricGatekeeper
import app.logdate.feature.core.account.CloudAccountOnboardingViewModel
import app.logdate.feature.core.export.DesktopExportLauncher
import app.logdate.feature.core.export.ExportLauncher
import app.logdate.feature.core.export.UserDataExportViewModel
import app.logdate.feature.core.main.HomeViewModel
import app.logdate.feature.core.profile.ui.ProfileViewModel
import app.logdate.feature.core.restore.DesktopRestoreLauncher
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
import app.logdate.feature.core.settings.ui.StreakSettingsViewModel
import app.logdate.feature.core.settings.ui.TimelineSettingsViewModel
import app.logdate.feature.core.settings.updates.AppUpdateController
import app.logdate.feature.core.settings.updates.UnsupportedAppUpdateController
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

        // TODO: Refactor to separate auth module
        single<BiometricGatekeeper> { StubBiometricGatekeeper() }
        single<AppUpdateController> { UnsupportedAppUpdateController(get()) }

        // Export functionality for desktop
        single<ExportLauncher> { DesktopExportLauncher() }
        single<RestoreLauncher> { DesktopRestoreLauncher() }
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
                get(),
                supportsSystemSearchVisibilityToggle = false,
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
        viewModel { UserDataRestoreViewModel(get(), get()) }
        viewModel { AdvancedSettingsViewModel(get(), get()) }
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
        // TODO(desktop): Wire location settings UX and platform permissions; keep settings storage available for now.
        viewModel { LocationSettingsViewModel(get()) }
        viewModel { MemoriesSettingsViewModel(get()) }
        viewModel { StreakSettingsViewModel(get(), get(), get()) }
        viewModel { TimelineSettingsViewModel(get(), get(), get()) }
        viewModel { ProfileViewModel(get(), get(), get(), get(), get()) }
    }
